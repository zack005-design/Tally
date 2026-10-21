# Calculator verification — 22 October 2026

- Full JVM suite: 149 tests, zero failures. Eight calculator test methods cover arithmetic precedence and decimal exactness, natural wording, percentages, bill splits, conversions, equations, unsupported requests, and review regressions.
- Android emulator API 36: `CalculatorScreenTest`, two tests passed. Verified typed `0.1 + 0.2` → `0.3`, clearing a previous result on edit, and guided ₹100/3 splitting at 320dp width with 200% font scale.
- App and instrumentation APKs built successfully. The first parallel build attempt hit a Windows file lock; serial builds succeeded. Initial emulator instrumentation hit a startup ANR during heavy build load; rerun passed both tests.
- Final combined serial JVM/build/lint run succeeded. Android lint reported zero errors; existing project warnings remain.
- Independent read-only review identified number-word ambiguity, overflow, malformed comma grouping, input truncation, quadratic cancellation, and equation rounding issues. These were corrected and regression coverage added; follow-up review found no further blocking issue.

These checks cover supported patterns, not unrestricted human language or all mathematics. See CALCULATOR.md for scope and numerical limits.
