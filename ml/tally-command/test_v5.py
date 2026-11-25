import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent


class V5CorpusTests(unittest.TestCase):
    """Imported last by discovery: command_v5_data applies the v5 runtime policy to command_data."""

    def test_splits_are_disjoint_deterministic_and_cover_new_accounts(self):
        from command_v5_data import make_corpus_v5
        from command_v4_data import signature
        corpus = make_corpus_v5(3000, 200, 200, seed=41017)
        self.assertEqual(corpus, make_corpus_v5(3000, 200, 200, seed=41017))
        for a, b in ((0, 1), (0, 2), (1, 2)):
            self.assertFalse({r['text'].lower() for r in corpus[a]} & {r['text'].lower() for r in corpus[b]})
            self.assertFalse({signature(r['template']) for r in corpus[a] if r['intent']}
                             & {signature(r['template']) for r in corpus[b] if r['intent']})
        accounts = {r['draft']['account'] for r in corpus[0] if r['draft']}
        self.assertTrue({'GPAY', 'WALLET', 'PHONEPE', 'PAYTM'} <= accounts)

    def test_v5_policy_accepts_new_boundaries_but_keeps_conflict_checks(self):
        import command_v5_data  # noqa: F401
        from command_data import decode_spans, tokenize
        text = 'momos cash 120'
        self.assertEqual(decode_spans(text, tokenize(text), [1, 3, 2])['account'], 'WALLET')
        text = 'petrol 1200 phone pe'
        self.assertEqual(decode_spans(text, tokenize(text), [1, 2, 3, 3])['account'], 'PHONEPE')
        text = 'biryani 280 upi or cash'
        self.assertIsNone(decode_spans(text, tokenize(text), [1, 2, 3, 0, 0]))

    def test_frozen_v5_challenge_is_balanced_and_absent_from_training(self):
        from command_v5_data import make_corpus_v5
        rows = json.loads((ROOT / 'challenge_v5.json').read_text(encoding='utf-8'))
        self.assertEqual(sum(r['draft'] is not None for r in rows), 12)
        self.assertEqual(len(rows), 24)
        train = {r['text'].lower() for r in make_corpus_v5(20000, 10, 10, seed=41017)[0]}
        self.assertFalse(train & {r['text'].lower() for r in rows})


if __name__ == '__main__':
    unittest.main()
