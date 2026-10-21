# V3 verification — 21 October 2026

The app bundle contains a 53,142-parameter expense-command span model trained
from random weights on 60,000 locally generated examples for 5,000 updates.
No pretrained weights/tokenizer, external teacher-model service or personal
financial records were used. Synthetic templates were authored with the coding
assistant. Bundle size: **214,695 bytes**, approximately **210 KiB**.

## Accuracy and coverage

The 32-phrase challenge was frozen and evaluated with v2 before v3 training:

| Check | V2 | V3 |
| --- | --- | --- |
| Correct accepted drafts among 16 supported challenge phrases | 2 | 14 |
| Incorrect accepted drafts among those phrases | 0 | 0 |
| Unsupported challenge phrases refused, out of 16 | 16 | 16 |
| Original eight regression phrases correctly parsed | 8 | 8 |

Two supported challenge phrases still defer. These are assistant-authored
controlled phrases, not independently collected real-user language. The v3
synthetic test uses structures disjoint from its training/development templates;
it shares vocabulary and generator assumptions. Of 1,563 supported test commands,
1,559 produced exact correct drafts and four deferred. All 437 unsupported
commands were refused. Threshold selection used development examples only.
These figures do not establish 99.74% accuracy on arbitrary language.

The 30-case policy set accepted five of six positives correctly and refused all
24 negatives. Its old unknown-merchant case intentionally became a positive
under v3's constrained open-description policy; that particular phrase still
defers. Results and full frozen baseline are preserved alongside the sources.

## Final verification

- **7 Python tests passed** for generation, exact decimal amounts, unknown-span
  policy, padding invariance and portable export/loading.
- **141 JVM tests passed**, zero failures/errors. These include **220**
  accepted/rejected Python/Kotlin parity cases and the new input-review tests.
- **Debug app APK, debug test APK and lint passed**, with zero lint errors.
- **2 Android API 36 emulator tests passed** against the final rebuilt APK:
  the portable model/legacy-parser comparison and actual ViewModel draft flow.
- Bundled model bytes inside the final APK match the source asset and its
  SHA-256 manifest.

The actual ViewModel test verifies that background parsing cannot overwrite a
manual edit, a superseding request wins, a new draft stays empty, negation clears
the suggested amount, and the no-parser-service constructor uses guarded rules.
It checks record counts before/after parsing. Only a disposable in-memory Room
database and fictional text are used; real financial data is never opened.
This is not a visual UI test or physical-phone test.

## Android timing

On the read-only `medium_phone` API 36 emulator, 100 warm inferences of
“Lunch 180 GPay” after 20 warmups measured:

| Condition | Median | 95th percentile |
| --- | --- | --- |
| Earlier run overlapping active Gradle work | 109.3709 ms | 500.7026 ms |
| Final run after the build completed | 1.3078 ms | 2.0372 ms |

These are observed wall-clock results, excluding loading/startup. The variation
is recorded rather than hidden; no system trace proves its sole cause. Neither
run establishes physical-phone latency, battery use or memory use. No physical
phone was connected. Inference and model loading remain off the UI thread.

## Review and fixes

Read-only review found that the rules parser assigns a `self` payer even to
ordinary personal “I paid…” entries. Treating every contribution map as shared
bypassed amount/currency/account checks and the learned path. A regression was
observed failing, then fixed: only genuine shared participants/split information
exempt an expense from personal-input checks. Ordinary self-paid entries now use
the model and the same checks as other personal expenses.

Additional regressions cover future intent, negation, malformed decimal/grouped
amounts, multiple accounts/amounts, foreign currency, valid ISO dates and sentence
punctuation. Existing affirmative income, transfer and shared-bill behavior is
preserved. Unsafe personal suggestions have their amount cleared before review,
including when the model or parser service is unavailable.

No parsing path saves financial records. Every draft remains editable and needs
explicit Save. Ambiguous language beyond the tested cases still requires review.
V3 remains a specialized English/INR command model, not a conversational LLM.

See `COMMAND_MODEL.md` for reproduction and `COMMAND_RESULTS.json` for compact
metrics. Checkpoints, datasets and full prediction reports remain in ignored
`ml/tally-command/runs/command-v3/`.
