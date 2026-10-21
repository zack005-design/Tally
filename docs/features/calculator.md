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

An equal ₹100 split among three people produces ₹33.34, ₹33.33, and ₹33.33, preserving the original total.

## How it works

The calculator uses a deterministic language adapter and bounded decimal engine, separate from the learned expense-command model. It does not execute user code or contact remote AI services.

Decimal arithmetic is exact where representable within the engine's limits. Repeating division is rounded; transcendental functions and equation results may be approximate. The interface labels approximate results.

## Limits

Currency conversion requires a rate you supply; there are no downloaded exchange rates. Unsupported wording, incompatible units, missing participants, and ambiguous percentage requests need clarification.

The calculator cannot read receipts, answer ledger-history questions, solve arbitrary symbolic mathematics, or save transactions. Use expense entry separately to record a purchase.

[Full capabilities](../../ml/tally-command/CALCULATOR.md) · [Recorded verification](../../ml/tally-command/CALCULATOR_VERIFICATION.md) · [Expense suggestions](on-device-ai.md)
