# App command model v3

The app now has a small model trained from scratch specifically for expense
drafts. It identifies amount, description and account spans in one pass instead
of generating JSON one character at a time. Exact decimal code converts the
selected amount to integer paise, so the model cannot invent another number.
This is a specialized language-understanding model, not a conversational LLM.

All embeddings, bidirectional GRU and classifier weights start randomly.
No pretrained model/tokenizer, model-response distillation, private records or
cloud service is used. The synthetic training generator and its English
templates were written with the coding assistant in this session.

## Measured artifacts

- 53,142 parameters; 214,695-byte portable float32 bundle, about 210 KiB.
- About 105 times smaller than the first 22,602,518-byte checkpoint. The
  architectures, tasks and serialization differ, so this is a deployed-artifact
  size comparison rather than an equal-capacity compression benchmark.
- Trained from random weights on 60,000 generated commands for 5,000 updates,
  using a cosine learning-rate schedule. The training loop took 55.72 seconds.
- 8/8 historical sample commands correct, versus the first model's 6/8.
- A 32-phrase challenge was frozen and scored with v2 before changing training.
  V3 correctly accepted 14/16 supported commands, versus v2's 2/16. Both refused
  all 16 unsupported commands. The two remaining supported phrases defer.
- On 2,000 disjoint synthetic template checks, v3 correctly accepted 1,559 of
  1,563 supported commands (99.74%) and refused all 437 unsupported commands.
  All accepted drafts in this set were correct; four supported commands deferred.
- On the 30-case targeted policy checks, all 24 unsupported cases were refused;
  five of six positives were accepted correctly. Unknown descriptions are now
  eligible only in constrained spans, so the old unknown-merchant refusal case
  intentionally has a positive label. One such positive still defers.

The acceptance threshold was chosen on development data, not the test split.
Previously inspected v2 templates are explicitly training material in v3. The
v3 train/dev/test structures are disjoint, but share generator vocabulary and
domain assumptions. Challenge sentences were authored with the coding assistant,
not independently collected from real users. These are controlled checks.
A high neural score is not a calibrated probability of correctness.

## Android integration

`ScratchCommandModel` implements the exported GRU directly in Kotlin. There
is no added native AI framework or dependency and no network permission.
The bundled weights load once, on a background dispatcher. Suggestions are
used by the explicit text-interpretation action. The lightweight live preview
continues using existing rules to avoid model work on every keystroke.

Only complete, sufficiently scored, single-INR-expense suggestions are accepted.
Unknown words must be confined to a contiguous description after for/on/at/towards,
or a strict compact description + amount + account command. Unknown command words
are refused. Amounts/descriptions are copied from input spans. INR/₹/Rs. notation,
Indian comma grouping, and g-pay/googlepay account aliases are included.
Missing, unfamiliar and unsupported inputs defer to the rules parser;
the new model does not replace refund, transfer, repayment or shared-bill logic.
Both paths apply personal-expense review checks for negation/questions/plans,
malformed or multiple amounts, conflicting accounts and foreign currencies.
An unsafe expense suggestion has its amount cleared, even when the neural model
is unavailable. Valid ISO dates remain supported by the rules fallback.
These checks cannot prove understanding of every sentence. Every result remains
editable and requires the existing explicit Save action.

Async results are ignored if the request was superseded or any draft field
changed. Account hints are resolved against current active accounts by the
existing resolver. A missing/corrupt bundle logs a non-financial diagnostic and
uses the rules parser. No records, splits or balances are written by inference.

The Python/JVM parity fixture contains 220 accepted/rejected commands. JVM
tests compare every accepted field and confidence tolerance as well as refusals.
The model instrumentation test uses fictional phrases and model math. A separate
draft-flow test uses a disposable in-memory database to check manual edits,
superseded requests, new-draft navigation, and that parsing never saves records.
Device results are documented in VERIFICATION_V3.md; laptop or
emulator timings do not establish physical-phone performance.

## Reproduce

From the repository root in PowerShell, create an isolated Python 3.12
environment and install the training software (no pretrained weights):

```powershell
uv venv --python 3.12 ml/tally-command/.venv
uv pip install --python ml/tally-command/.venv/Scripts/python.exe -r ml/tally-command/requirements-lock.txt
& ml/tally-command/.venv/Scripts/python.exe -m unittest discover -s ml/tally-command -p 'test_*.py'
& ml/tally-command/.venv/Scripts/python.exe ml/tally-command/train_command.py --steps 5000 --run-dir ml/tally-command/runs/command-v3
& ml/tally-command/.venv/Scripts/python.exe ml/tally-command/evaluate_command.py
```

Choose a fresh training folder if the run already exists. The evaluation/export
script defaults to `command-v3`; select another explicitly with `--run-dir`
when reviewing another model. It writes the app bundle, provenance
manifest, parity fixture and compact `COMMAND_RESULTS.json`. Full datasets,
predictions and checkpoints remain in Git-ignored `runs/` folders.

Run Android checks with the checked-in wrapper and installed JDK/SDK:

```powershell
./gradlew.bat -I tools/command-build.init.gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
./gradlew.bat -I tools/command-build.init.gradle :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.smartexpense.LocalCommandModelTest'
```

Remaining work for broader language support is independently collected test
phrasing, richer open-vocabulary supervision, more languages and actual-phone
battery/memory/latency testing. Bigger model size alone does not establish a
better expense-entry experience.
