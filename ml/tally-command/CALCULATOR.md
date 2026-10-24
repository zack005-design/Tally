# Offline calculator

Open More → Money & planning → Calculate. Type a request, tap an example, or choose guided percentage, split, or unit conversion fields. This tool does not save or edit ledger records.

The scratch-trained expense model is unchanged. Calculator requests use a deterministic language adapter and a bounded decimal expression engine, with no pretrained weights, remote inference, user-code execution, or exchange-rate downloads.

Supported examples:

- `what is 120 plus 85 plus 40`
- `one hundred twenty plus thirty`
- `1.5 lakh + 25 thousand`
- `add 5% tax to 450`, `take 10% off 500`
- `split 100 between 3 people` (₹33.34, ₹33.33, ₹33.33)
- `convert 2.5 km to m`, `32 fahrenheit to celsius`
- `convert 10 usd to inr at 83.5` (rate supplied by the user)
- `sqrt(144)`, `5!`, `sin_deg(30)`, `sin_rad(pi/2)`
- `2*x + 3 = 11`, `x^2 - 5*x + 6 = 0`
- `average 10, 20, 30`, `median 9, 1, 4, 2`, `population variance 2, 4, 6`
- `simplify ratio 12:18:24`, `percentage change from 80 to 100`
- `tip 10% on 500` (total including tip), `remove 18% tax from 1180` (pre-tax amount)
- `simple interest on 1000 at 10% per year for 2 years`
- `compound interest on 1000 at 12% per year for 1 year compounded monthly`

Extended tools use anchored patterns compiled once, with reused spoken-number vocabulary and patterns. Statistics accept at most 100 plain decimal values separated by comma-space or semicolon; variance uses an exact numerator before division to avoid rounded-mean cancellation. Ratios use integer GCD with at most 20 terms of at most 100 digits. Financial rates are bounded to 0–100%, forecasts to 100 years and 1,200 whole compounding periods. Compound forecasts assume fixed nominal annual rates and no payments or fees. Interest is the returned value; final balance appears in the explanation. Financial tool outputs are unrounded arithmetic, separate from ledger rounding. No model weights or parameter counts change with these tools.

The UI shows the interpreted expression and labels approximate results. Decimal arithmetic is exact when representable within resource bounds; repeating division uses 34 significant digits, transcendental functions use floating point with 15 significant output digits. Equation results are conservatively labelled approximate. Nonterminating coefficient division in equations is refused to avoid changing solution sets through rounding.

This is not unrestricted natural-language mathematics. Unsupported wording, missing split participants, incompatible units, missing exchange rates, ambiguous percentage changes, and angle functions without units ask for clarification. It cannot answer ledger-history questions, solve arbitrary symbolic mathematics, or read images or speech.

Inputs are limited to 1,000 characters, expressions to 256 tokens, nesting to 32 parsing levels, factorials to 100, and integer exponents to magnitude 100. Decimal size checks constrain intermediate work. Equal bill splits use the existing integer-paisa largest-remainder implementation and conserve the original amount.
