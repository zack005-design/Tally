import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("validator", HERE / "validate_history.py")
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)


class HistoryValidatorTest(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads((HERE.parent / "references" / "synthetic-history.json").read_text(encoding="utf-8"))

    def check(self, payload):
        with tempfile.TemporaryDirectory(dir=Path.cwd()) as directory:
            path = Path(directory) / "history.json"
            path.write_text(payload if isinstance(payload, str) else json.dumps(payload), encoding="utf-8")
            return VALIDATOR.validate(path)

    def test_correction_repeat_and_unsupported_events_keep_expected_counts(self):
        result = self.check(self.fixture)
        self.assertEqual(5, result["records"])
        self.assertEqual(2, result["supported_before_account_mapping"])
        self.assertEqual(3, result["unresolved"])
        self.assertEqual(48000, result["expense_paise"])
        self.assertIn("Earlier messages inaccessible", result["coverage"])

    def test_numeric_amount_duplicate_source_and_unknown_fields_reject(self):
        for mutation in ("numeric", "duplicate", "unknown", "version"):
            with self.subTest(mutation=mutation):
                data = json.loads(json.dumps(self.fixture))
                if mutation == "numeric":
                    data["entries"][0]["amount"] = 240
                elif mutation == "duplicate":
                    data["entries"][1]["sourceId"] = data["entries"][0]["sourceId"]
                elif mutation == "unknown":
                    data["entries"][0]["personalShare"] = "80.00"
                else:
                    data["formatVersion"] = True
                with self.assertRaises(ValueError):
                    self.check(data)

    def test_repeated_json_field_and_oversized_input_reject(self):
        with self.assertRaises(ValueError):
            self.check('{"format":"a","format":"b"}')
        with self.assertRaises(ValueError):
            self.check(" " * (VALIDATOR.MAX_BYTES + 1))

    def test_fractional_paise_bad_date_and_overflow_stay_unresolved(self):
        for field, value in (("amount", "1.001"), ("amount", "92233720368547758.08"), ("date", "2026-02-29")):
            with self.subTest(field=field, value=value):
                data = json.loads(json.dumps(self.fixture))
                data["entries"][0][field] = value
                result = self.check(data)
                self.assertEqual(1, result["supported_before_account_mapping"])
                self.assertEqual(4, result["unresolved"])


if __name__ == "__main__":
    unittest.main()
