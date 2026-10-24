# Calculator verification — 22 October 2026

- Full JVM suite: 149 tests, zero failures. Eight calculator test methods cover arithmetic precedence and decimal exactness, natural wording, percentages, bill splits, conversions, equations, unsupported requests, and review regressions.
- Android emulator API 36: `CalculatorScreenTest`, two tests passed. Verified typed `0.1 + 0.2` → `0.3`, clearing a previous result on edit, and guided ₹100/3 splitting at 320dp width with 200% font scale.
- App and instrumentation APKs built successfully. The first parallel build attempt hit a Windows file lock; serial builds succeeded. Initial emulator instrumentation hit a startup ANR during heavy build load; rerun passed both tests.
- Final combined serial JVM/build/lint run succeeded. Android lint reported zero errors; existing project warnings remain.
- Independent read-only review identified number-word ambiguity, overflow, malformed comma grouping, input truncation, quadratic cancellation, and equation rounding issues. These were corrected and regression coverage added; follow-up review found no further blocking issue.

These checks cover supported patterns, not unrestricted human language or all mathematics. See CALCULATOR.md for scope and numerical limits.

## Capability expansion — 24 October 2026

Verified in an isolated clean-HEAD source snapshot with only this chat's calculator changes overlaid, preserving other chats' unfinished data/UI/build changes. Added seven capability test methods, including known-answer statistics, ratios, percentage changes, fractional tips/tax/interest, explicit compounding, invalid-input bounds, large-offset variance, zero-duration large-principal forecasts, and tiny positive rates. The initial new capability tests failed before implementation; numerical regressions were reproduced before fixing them. Full JVM suite: 156 tests, zero failures/errors/skips. Debug APK assembly succeeded. Read-only review identified the zero-period compound rounding issue; the growth-increment fix and regression tests were rechecked with no further important finding.

Optimization is structural: cached language patterns/vocabulary, bounded single-pass sums and exact variance numerators, integer GCD for ratios, and logarithmic growth-increment exponentiation for compound forecasts. No trained-model parameters changed.

Final source-matched verification reran the full JVM suite, debug assembly and Android lint after the word-percent normalization change. All tasks succeeded; lint reported zero errors. Existing project warnings remain.

Desktop JVM sanity timing: 100 warm-up calls and 300 measured calls per input, one process, against the compiled app classes with the cached Kotlin runtime. Concurrent build load was present; these are not Android or battery benchmarks and are not a before/after speedup claim.

| Input | Median ms | p95 ms |
| --- | ---: | ---: |
| `average 10, 20, 30` | 0.055 | 0.426 |
| `median 9, 1, 4, 2` | 0.029 | 0.159 |
| `population variance 2, 4, 6` | 0.046 | 0.180 |
| `simplify ratio 12:18:24` | 0.020 | 0.045 |
| `tip ten percent on five hundred` | 0.036 | 0.076 |
| `remove 18% tax from 1180` | 0.015 | 0.025 |
| Monthly compound interest, 1,200 periods | 0.058 | 0.164 |

This batch changes source; the previously published APKs do not acquire the new tools automatically. No new emulator/physical-device or release-APK verification was performed for this batch.
