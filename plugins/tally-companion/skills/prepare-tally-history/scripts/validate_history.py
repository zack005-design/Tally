"""Validate the Tally history interchange structure; report unresolved rows separately."""
import datetime
import json
import re
import sys
from pathlib import Path

MAX_BYTES = 5 * 1024 * 1024
MAX_PAISE = 9223372036854775807
ROOT = {"format", "formatVersion", "currency", "historyId", "coverage", "entries"}
FIELDS = {"sourceId", "kind", "date", "description", "amount", "account", "category", "notes", "shared", "unresolved", "details"}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Repeated field: {key}")
        result[key] = value
    return result


def validate(path):
    with Path(path).open("rb") as source:
        raw = source.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        raise ValueError("History exceeds 5 MiB")
    data = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=unique_object)
    if not isinstance(data, dict) or set(data) != ROOT:
        raise ValueError("Wrong root fields")
    if data["format"] != "tally-chat-history" or type(data["formatVersion"]) is not int or data["formatVersion"] != 1 or data["currency"] != "INR":
        raise ValueError("Expected tally-chat-history version 1, INR")
    for key, limit in (("historyId", 120), ("coverage", 2000)):
        if not isinstance(data[key], str) or not data[key].strip() or len(data[key]) > limit:
            raise ValueError(f"Invalid {key}")
    rows = data["entries"]
    if not isinstance(rows, list) or not 1 <= len(rows) <= 10000:
        raise ValueError("Expected 1–10,000 entries")
    seen, supported, unresolved, expenses, income = set(), 0, 0, 0, 0
    for row in rows:
        if not isinstance(row, dict) or not set(row) <= FIELDS:
            raise ValueError("Unknown entry fields")
        for key, limit in (("sourceId", 120), ("kind", 80)):
            if not isinstance(row.get(key), str) or not row[key].strip() or len(row[key]) > limit:
                raise ValueError(f"Invalid {key}")
        if row["sourceId"] in seen:
            raise ValueError("Repeated sourceId")
        seen.add(row["sourceId"])
        for key in FIELDS - {"shared", "unresolved"}:
            value = row.get(key)
            if value is not None and (not isinstance(value, str) or len(value) > 10000):
                raise ValueError(f"{key} must be a string or null")
        if "shared" in row and type(row["shared"]) is not bool:
            raise ValueError("shared must be boolean")
        reasons = row.get("unresolved", [])
        if not isinstance(reasons, list) or len(reasons) > 100 or any(not isinstance(x, str) or not x.strip() or len(x) > 10000 for x in reasons):
            raise ValueError("Invalid unresolved reasons")
        valid_date = False
        if isinstance(row.get("date"), str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", row["date"], flags=re.ASCII):
            try:
                datetime.date.fromisoformat(row["date"])
                valid_date = True
            except ValueError:
                pass
        amount = row.get("amount") or ""
        paise = None
        if re.fullmatch(r"\d+(\.\d{1,2})?", amount, flags=re.ASCII) and len(amount) <= 24:
            whole, _, fraction = amount.partition(".")
            paise = int(whole) * 100 + int((fraction + "00")[:2])
        ready = row["kind"] in ("EXPENSE", "INCOME") and not row.get("shared", False) and not reasons and valid_date and paise is not None and 0 < paise <= MAX_PAISE and bool((row.get("description") or "").strip()) and bool((row.get("account") or "").strip())
        if ready:
            supported += 1
            if row["kind"] == "EXPENSE":
                expenses += paise
            else:
                income += paise
        else:
            unresolved += 1
    return {"records": len(rows), "supported_before_account_mapping": supported, "unresolved": unresolved, "expense_paise": expenses, "income_paise": income, "coverage": data["coverage"]}


if __name__ == "__main__":
    try:
        print(json.dumps(validate(sys.argv[1]), ensure_ascii=False))
    except (ValueError, OSError, IndexError, RecursionError) as error:
        print(f"Invalid Tally history: {error}", file=sys.stderr)
        sys.exit(1)
