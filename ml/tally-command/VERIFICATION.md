# Verification — 21 October 2026

**Historical v2 results. Current v3 verification is in
[VERIFICATION_V3.md](VERIFICATION_V3.md).**

The bundled app model was trained locally from random weights. It is a compact
expense-command span model, not a conversational LLM. Parameters: 50,694.
Portable bundle: 204,403 bytes. Its SHA-256 manifest is packaged beside it in
`app/src/main/assets/tally-command.json`.

Completed checks:

- Python command-model suite: **6 tests passed**. The retained v1/v2 research
  folder's combined suite also passed 15 tests.
- Android JVM suite: **137 tests passed, zero failures/errors**, including
  all-field accepted/rejected parity on **138 Python/Kotlin fixture commands**.
- `assembleDebug`, `lintDebug`, and `assembleDebugAndroidTest`: **passed**.
- Android API **36** read-only `medium_phone` emulator:
  `LocalCommandModelTest.bundledModelImprovesDraftsAndRunsLocally`: **passed**.
- Exact trained-model bytes inside the built APK match the source asset.

Emulator measurements from 100 warm inferences of “Lunch 180 GPay”:

| Measurement | Observed |
| --- | --- |
| Median inference | 1.0158 ms |
| 95th percentile | 1.6019 ms |
| Complete correct drafts on 8 historical commands, old rules | 2 / 8 |
| Complete correct drafts on the same commands, model-assisted parser | 8 / 8 |

The test uses fictional phrases and model assets only, never the financial
database. Timings exclude model loading and app startup. Emulator/laptop results
do not establish physical-phone battery use, memory use or latency. No physical
phone was connected. The instrumentation test covers the parser and portable
runtime; it does not exercise the full editable-form UI lifecycle.

Synthetic evaluation remains limited: on 1,000 reused generator checks the
deployment policy accepted 199 correct drafts and deferred the remainder,
including 591 supported-but-uncertain positives. All 25 negative cases in the
30-case targeted check were refused by the neural model. The rules fallback is
separate and can still return incomplete drafts. These figures are not a claim
of 100% real-world accuracy; user review and explicit Save remain required.

Reproducible training/export code is in `ml/tally-command`. Earlier experiments
and their datasets/checkpoints remain local under the repository's intentionally
ignored `tools/tally-lm/runs` directory. The final source was tested and used to
re-export the exact bundled model from that frozen run. Full research iteration
history is not represented as an independent unseen benchmark.

Read-only review findings about conflicting multiword accounts, cross-runtime
parity and stale error messages were addressed. Unknown vocabulary is refused
before neural suggestion acceptance; no pretrained model service is involved.
