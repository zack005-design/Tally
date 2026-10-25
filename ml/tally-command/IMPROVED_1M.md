# Improved 1M expense-command extension

The current app source integrates a **999,150-parameter** bidirectional GRU trained from random weights, plus the existing **53,142-parameter** compatibility model. Total learned parameters: **1,052,292**. Compatibility predictions are preferred; the extension handles abstentions, protecting established behavior while extending coverage. Neither model is a conversational LLM or a math engine.

## Capabilities and limits

- Extract description, exact INR amount and explicit account from broader English structures and bounded Latin-script Hinglish: `maine chai ke liye cash se 30 diye`.
- Recognise five explicit account spellings: `gpy`, `gpaay`, `gooogle pay`, `wallett`, `walllet`. This is an allowlist, not general fuzzy account matching.
- Reject negation, future/planned payments, conflicting accounts, multiple amounts and unsupported currencies before accepting a personal-expense draft. `kal` is conservatively ambiguous.
- Defer incomplete or uncertain new-language input with explicit manual-confirmation warnings. Suggestions never save financial records. Non-expense/shared-bill paths retain their existing rules.

Only one expense/INR amount is supported by the learned path. Arbitrary Hindi, speech, receipt interpretation, ledger questions and unrestricted calculations are outside this model. The calculator and receipt scanner remain separate tools.

## Training and selection

`command_v4_data.py` expands structure coverage to 120,000 synthetic training examples, 2,000 development examples and 3,000 new held-out examples. Previous ladder templates/scores are explicitly regression/training-structure material, not untouched new evidence. Fresh positive train/dev/test templates are disjoint even after tokenizer normalization. No private records, pretrained weights or external teacher calls are used.

`train_improved.py` trains from scratch with seed 29173, batch 128, AdamW, a lower cosine learning-rate schedule and bounded token masking. Every 1,000 updates it selects a threshold using development data only, requiring ≥20 accepted drafts and zero accepted errors. Checkpoints are ranked by correct development coverage. **Step 6,000 of 8,000** was selected, rather than blindly shipping the final checkpoint. The new held-out set is evaluated only after selection freezes.

Extension dimensions: vocabulary 207, embedding 128, hidden 342 per direction. Threshold 0.8 is an uncalibrated score. Portable bundle: 3,998,956 bytes; SHA-256 `dcf3212c92f1c042adc41137d243e019d7f27a2409d6c6b91b36776638b2da1e`. Dataset SHA-256 `2d1af960664411069838fdac824111e1df49bfafddb7893d0d4931c13fcbe10d`.

## Frozen combined-system evaluation

| Set | Supported | Correctly accepted | Wrong returned drafts | Unsupported falsely accepted |
| --- | ---: | ---: | ---: | ---: |
| Fresh held-out templates | 2,451 | 2,447 | 0 | 0/549 |
| Legacy regression corpus | 1,563 | 1,563 | 0 | 0/437 |
| Legacy challenge | 16 | 14 | 0 | 0/16 |
| New Hinglish/typo challenge | 12 | 11 | 0 | 0/12 |
| Safety regression cases | 6 | 5 | 0 | 0/24 |

Legacy compatibility predictions are evaluated with their original runtime policy, distinct from stricter extension guards. Deterministic checks contribute heavily to unsafe-input refusal. These synthetic results are not independent real-user language accuracy or a universal safety guarantee. Further tuning must treat these sets as inspected selection/regression evidence.

## Android runtime

The bounded loader permits at most 512 hidden units and 1.5M parameters per model, validates every tensor shape and finite weight, and rejects trailing data. Extension policy adds the same account aliases, negation and complete-description checks as Python. Compatibility policy is preserved separately.

Per-inference recurrent gate buffers and preallocated output vectors are reused locally, with row offsets hoisted from inner loops. Weights remain immutable, so concurrent parsing does not share mutable inference buffers. Lazy model loading and parsing run on `Dispatchers.Default`. Missing/corrupt extension assets retain compatibility/rules behavior; both bundles are excluded from non-AI builds.

Cross-runtime fixtures contain 386 independent Python-computed examples, covering all draft fields, model routing and score tolerance. Existing 220 compatibility fixtures remain unchanged. JVM, build, lint and packaged Android checks are recorded after completion below.

## Verified integration

- 19 Python tests passed, including normalized split separation, scalar/batched inference agreement, precise checkpoint scoring and binding of both exported model checksums.
- 192 JVM tests passed, including all 386 extension/composite parity cases and the existing 220 compatibility cases, bounded-loader rejection and explicit clarification behavior.
- AI debug APK and instrumentation APK built successfully. Android lint reported zero errors; existing project warnings remain.
- Four API 36 emulator instrumentation tests passed: packaged extension phrases/unsafe-input refusals, distribution asset checks, compatibility behavior, and background draft handling without automatic ledger writes.
- Thirty warmed packaged Hinglish parses after five warm-ups measured **36.45 ms median /80.24 ms p95** on the emulator while concurrent builds were active. This is not a physical-phone/battery benchmark.
- Non-AI debug and instrumentation APKs built successfully; direct ZIP inspection confirmed neither model bundle nor its metadata is packaged. The resumed non-AI runtime check is unverified: Android recorded startup ANRs before tests began, and subsequent isolated emulator boots stalled, including retries with 4 GB RAM and software rendering. No app-test assertion failure was recorded, and these attempts are not counted as passes.
- No physical-device or production-release verification was performed. Published APK releases are unchanged by this source integration.

## Reproduce

```sh
python -m unittest test_command test_command_model test_v4
python train_improved.py --run-dir runs/improved-1m --steps 8000
python evaluate_improved.py --run-dir runs/improved-1m --output-dir runs/improved-1m-evaluation
python export_extension.py --run-dir runs/improved-1m --evaluation-dir runs/improved-1m-evaluation
```

Run these commands from `ml/tally-command/` with PyTorch installed. Composite evaluation also requires the locally retained legacy `runs/command-v3/dataset.json` from the original training run; it is deliberately excluded from Git.

The composite evaluation must pass before export, and both bundled model checksums must match the evaluation report. App parity/device checks are separate mandatory integration checks. Runs, checkpoints, generated corpora and full predictions stay in ignored local `runs/`; only source, required app assets and synthetic test fixtures are published. Prior rejected ladder results remain in [the initial report](MODEL_LADDER_RESULTS.md).
