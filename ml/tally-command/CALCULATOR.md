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

The UI shows the interpreted expression and labels approximate results. Decimal arithmetic is exact when representable within resource bounds; repeating division uses 34 significant digits, transcendental functions use floating point with 15 significant output digits. Equation results are conservatively labelled approximate. Nonterminating coefficient division in equations is refused to avoid changing solution sets through rounding.

This is not unrestricted natural-language mathematics. Unsupported wording, missing split participants, incompatible units, missing exchange rates, ambiguous percentage changes, and angle functions without units ask for clarification. It cannot answer ledger-history questions, solve arbitrary symbolic mathematics, or read images or speech.

Inputs are limited to 1,000 characters, expressions to 256 tokens, nesting to 32 parsing levels, factorials to 100, and integer exponents to magnitude 100. Decimal size checks constrain intermediate work. Equal bill splits use the existing integer-paisa largest-remainder implementation and conserve the original amount.
