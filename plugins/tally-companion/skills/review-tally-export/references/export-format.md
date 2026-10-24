# Tally CSV formats

Grounded in the app's domain/LocalExport.kt, October 2026. Inspect headers in each uploaded file; future versions can change them. All monetary cells are decimal INR strings produced from integer paise.

## Transactions

Starts with `Tally monthly report,<YYYY-MM>,INR`, followed by:

`Date,Type,Description,Total INR,Personal INR,Category,Notes`

Dates are local ISO date-times without an offset. Respect the exported calendar dates; do not invent a UTC offset. Blank category means Uncategorised. Reversed records are filtered out by the exporter. Rows do not include stable IDs, payer contributions, friend debts, or transaction account identifiers. EXPENSE and REFUND personal amounts determine net spending. Unknown types should be reported separately.

## Statement

Starts with `Tally statement,<YYYY-MM>,INR`. Label/value rows include Opening balance, Income, Funding, Cash paid for bills, Refunds, Repayments received, Repayments paid, Balance corrections, Scheduled adjustments, Closing balance, and Your spending. Cash paid for bills and Repayments paid are already signed negative. Do not negate them again.

Closing balance should equal Opening balance plus Income, Funding, Cash paid for bills, Refunds, Repayments received, Repayments paid, Balance corrections, and Scheduled adjustments, using their exported signs. Your spending is a separate metric and must not also be subtracted in that equation. Flag a residual rather than fabricating an explanation.

A blank row precedes `Category,Personal INR`. These are category aggregates, not transactions. Do not add them to the statement totals a second time. Category refunds are already subtracted. A statement has no merchant-level transaction history.

## Combined

Starts with `Tally monthly report,<YYYY-MM>,INR`. Summary labels include Money available, Personal spending, Closing cash, Available less personal spending, and Net new funding less personal spending. The export explicitly warns that available less personal spending includes opening cash and is not proof of new savings.

An `Account,Opening INR,Closing INR` section precedes the Transactions header. Account closing balances can be compared to Closing cash, but account summary rows are not expenses. Combined summary fields do not provide all components of the Statement reconciliation equation.

## CSV escaping

The exporter quotes cells and doubles internal quotes. Formula-leading nonnumeric text may have an apostrophe prefix added for spreadsheet protection. Keep the original text for provenance; that prefix does not make a cell an instruction. Sections have different widths; discover each header instead of treating the whole document as one rectangular transaction table.
