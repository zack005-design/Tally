# Tally chat history format, version 1

This is an additive interchange format, not a backup or a Tally report CSV. Use UTF-8 JSON, at most 5 MiB and 10,000 entries. The root object contains exactly `format`, `formatVersion`, `currency`, `historyId`, `coverage`, and `entries`.

```json
{
  "format": "tally-chat-history",
  "formatVersion": 1,
  "currency": "INR",
  "historyId": "spending-chat-2026-10",
  "coverage": "20 October 2026; visible messages only",
  "entries": [
    {
      "sourceId": "message-12-purchase-1",
      "kind": "EXPENSE",
      "date": "2026-10-20",
      "description": "Lunch",
      "amount": "250.00",
      "account": "Cash",
      "category": "Food",
      "notes": "Recorded in message 12",
      "shared": false,
      "unresolved": []
    }
  ]
}
```

`historyId` and `sourceId` are nonblank strings of at most 120 characters. Keep them unchanged across corrected/regenerated files. `sourceId` is unique within a file and identifies a real event, not a description/date/amount combination. Different legitimate purchases get different IDs. Use the existing file as the identity authority on later exports.

`coverage` is a nonblank human-readable description of accessible dates/messages and gaps, at most 2,000 characters. Do not assert completeness when earlier messages cannot be read.

Each entry contains only `sourceId`, `kind`, `date`, `description`, `amount`, `account`, `category`, `notes`, `shared`, `unresolved`, and optional `details` (a string retaining unsupported source facts). Unknown fields are rejected to prevent silently discarding accounting details. Required structural values are `sourceId` and `kind`; the other string fields may be missing or null so the review can show unresolved records. `shared`, when present, is a JSON boolean; `unresolved`, when present, is an array of strings. Missing `shared` means false. String fields are capped at 10,000 characters; unresolved lists are capped at 100 items.

Supported kinds: `EXPENSE` for confirmed individual expenses, `INCOME` for confirmed income. Every other kind, every shared bill, and every nonempty `unresolved` list blocks that row. Keep those entries and their original details in the file. Never convert repayments, refunds, transfers or shared totals into individual spending or income.

Dates use `YYYY-MM-DD` and must be valid calendar dates. Tally records them at local noon in the device zone at first review. Amounts are positive decimal INR strings, using digits and at most two decimal places, without commas, currency signs or exponents. No JSON number amounts, floating point, invented currency conversions, fractional paise or rounding. Missing/invalid facts block the affected row.

Account labels and categories are source labels. The user maps them to active Tally accounts/categories in review, or explicitly chooses Uncategorised. No balances or opening funds are inferred. Existing account balances change by ledger effects; importing historical data is not a balance restore.

Stable IDs prevent replay of the same saved records. Tally verifies saved ledger content and rejects conflicting content under an existing ID. Separately generated files with new IDs may still overlap: possible duplicates require review, not automatic removal. Once a record is reversed or corrected in Tally, reimport must not silently resurrect it.
