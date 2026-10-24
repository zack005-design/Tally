[← All guides](../README.md)

# Offline calculator

Open **More → Money & planning → Calculate** to work out totals, discounts, equal splits, and conversions without changing your records. The calculator is included in both editions.

## Try a calculation

Type a request, choose an example, or use the guided percentage, split, and unit-conversion fields. Review the interpreted expression as well as the result.

| Task | Example |
| --- | --- |
| Add costs | `120 plus 85 plus 40` |
| Add tax | `add 5% tax to 450` |
| Apply a discount | `take 10% off 500` |
| Split a bill | `split 100 between 3 people` |
| Convert units | `convert 2.5 km to m` |
| Convert currencies with your rate | `convert 10 usd to inr at 83.5` |
| Solve a supported equation | `2*x + 3 = 11` |
| Add a tip and show total | `tip 10% on 500` |
| Recover the pre-tax amount | `remove 18% tax from 1180` |
| Compare a change | `percentage change from 80 to 100` |
| Summarise numbers | `average 10, 20, 30` |
| Find a median | `median 9, 1, 4, 2` |
| Population statistics | `population variance 2, 4, 6` |
| Simplify a ratio | `simplify ratio 12:18:24` |
| Simple interest | `simple interest on 1000 at 10% per year for 2 years` |
| Compound interest | `compound interest on 1000 at 12% per year for 1 year compounded monthly` |

An equal ₹100 split among three people produces ₹33.34, ₹33.33, and ₹33.33, preserving the original total.

Tip results show the total including the tip. Reverse tax divides the tax-inclusive total by `1 + rate/100`; it differs from taking a discount. Interest results show **interest earned**, with the final amount in the explanation. Compound forecasts assume a fixed annual nominal rate, no fees, deposits or withdrawals; choose yearly, quarterly or monthly compounding. These are arithmetic scenarios, not bank quotes.

Statistics also support `sum`, `mean`, `minimum`, `maximum`, and `population standard deviation`. Use up to 100 plain numbers, separated by a comma **and space**, or semicolons. Write `1000`, not `1,000`, in a statistics list. Ratios accept up to 20 nonnegative integer terms. Sample variance is currently unsupported.

## How it works

The calculator uses a deterministic language adapter and bounded decimal engine, separate from the learned expense-command model. It does not execute user code or contact remote AI services.

Decimal arithmetic is exact where representable within the engine's limits. Repeating division is rounded; transcendental functions and equation results may be approximate. The interface labels approximate results.

## Limits

Currency conversion requires a rate you supply; there are no downloaded exchange rates. Unsupported wording, incompatible units, missing participants, and ambiguous percentage requests need clarification.

Tip, reverse-tax and interest tools accept rates from 0% to 100%. Interest durations are limited to 100 years and 1,200 compounding periods; a compound duration must contain a whole number of periods. Financial tool results are unrounded decimal calculations and are never saved as money entries. Repeating divisions and longer compound forecasts may be approximate.

The calculator cannot read receipts, answer ledger-history questions, solve arbitrary symbolic mathematics, or save transactions. Use expense entry separately to record a purchase.

[Full capabilities](../../ml/tally-command/CALCULATOR.md) · [Recorded verification](../../ml/tally-command/CALCULATOR_VERIFICATION.md) · [Expense suggestions](on-device-ai.md)
