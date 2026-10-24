# Model ladder results — 24 October 2026

## Decision

**No candidate is promoted.** The 100K candidate leads this experiment on the expanded synthetic set, but accepts two incorrect drafts on the legacy 2,000-case set and has slightly lower correct coverage than the incumbent. The 500K model loses substantial coverage. The 1M model loses coverage and accepts more incorrect drafts. Increasing parameter count did not make this app's assistant reliably better.

The bundled app model remains **53,142 parameters**. No APK, app asset, Kotlin runtime or release was changed by this experiment. Larger research models are not evidence of a general chatbot or unrestricted calculation ability.

## Controlled runs

Every expanded run uses the same 60,000 training / 2,000 development / 2,000 test examples, seed 2027, 5,000 updates and batch 96. Random initialization, no pretrained weights, no private records and no external teacher-model calls. A 54K compact control separates some effects of the expanded vocabulary/data from capacity. The incumbent uses its original corpus; its comparison is a Python rerun using current research guards, not shipped Android timing/behavior.

| Run | Actual parameters | Embedding / hidden | Threshold | Export bytes |
| --- | ---: | --- | ---: | ---: |
| Incumbent | 53,142 | 48 /64 | 0.8 | 214,695 |
| Expanded compact control | 54,198 | 48 /64 | 0.8 | 219,100 |
| 100K | 99,684 | 64 /91 | 0.8 | 401,044 |
| 500K | 500,724 | 128 /223 | 0.8 | 2,005,204 |
| 1M | 998,382 | 128 /342 | 0.9 | 3,995,836 |

The first 100K, 500K and 1M runs failed the development-only gate. Diagnostics showed confidently selected command filler (`entry`) and truncated descriptions. A stricter boundary guard was added using development cases only, before challenge/test evaluation, while keeping the same threshold gate: ≥20 accepted drafts and zero accepted development errors. The retained 500K/1M checkpoints were recalibrated without retraining. The initial 100K attempt predated rejected-checkpoint preservation, so it was rerun with the same seed, dimensions and exact frozen corpus; that retained run is reported here. Original rejected500K/1M evidence remains local. No threshold was chosen from test/challenge results.

## Accuracy, refusal and coverage

Correct-positive coverage counts both errors and refusals as unsuccessful positive drafts. Accepted errors are wrong returned drafts; they are more serious than asking for review. Expanded held-out coverage is not comparable to the incumbent's own-corpus test, so that incumbent cell is deliberately blank. Shared legacy results are directly comparable under current Python research guards.

| Run | Expanded correct positives /1,573 | Expanded accepted errors | Legacy correct positives /1,563 | Legacy accepted errors | New capabilities /12 | Legacy challenge /16 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Incumbent | — | — | 1,559 (99.74%) | 0 | 0 | 13 |
| Expanded compact control | 1,488 (94.60%) | 0 | 1,437 (91.94%) | 0 | 11 | 13 |
| 100K | 1,570 (99.81%) | 0 | 1,555 (99.49%) | 2 | 11 | 13 |
| 500K | 1,498 (95.23%) | 0 | 1,486 (95.07%) | 0 | 10 | 13 |
| 1M | 1,409 (89.57%) | 5 | 1,473 (94.24%) | 9 | 10 | 13 |

All five runs refused all 12 new-challenge negative cases and all 24 existing safety negatives, with no accepted error on those two small challenge/safety sets. All returned 5/6 supported safety positives. Negative refusals are substantially enforced by deterministic guards; these are not claims that the neural weights independently learned complete safety behavior. The new challenge includes bounded account typos, Latin-script Hinglish phrases, negation, plans, mixed accounts and multiple amounts.

## Runtime/export verification

Portable Python round-trip comparison checks all draft fields and confidence on 54 cases per run: **270 parity cases total**, all passed. Fourteen Python unit tests and Python compilation checks passed. No Android runtime or physical-device inference benchmark was performed for these candidates.

| Run | Warmed Python CPU median ms | p95 ms |
| --- | ---: | ---: |
| Incumbent | 2.184 | 8.285 |
| Expanded compact control | 1.831 | 4.443 |
| 100K | 1.756 | 2.558 |
| 500K | 2.545 | 7.601 |
| 1M | 3.533 | 9.066 |

Measurements use 20 warm-up calls then 200 timed calls to `Lunch 180 GPay`, four CPU threads. Concurrent machine load and implementation effects vary; this is a sanity check, not proof that 100K is faster on a phone. CUDA training used the local RTX 3050 laptop GPU.

## Provenance and limitations

- Frozen expanded dataset SHA-256: `14429cde126afdb67fd02b4950f97ac6133e82dd01ea749b537e060696eb0891`.
- New 24-case challenge SHA-256: `a7c3b6147c33f4212c6591dd522af358370365ed7c723eccb7993454a40e49e9`; no exact challenge text occurs in training.
- Templates are disjoint across expanded train/dev/test; vocabulary and authorship are shared. Synthetic testing is not independent real-person language evidence.
- These results are now selection/regression evidence. Further tuning must not relabel them as untouched new tests.
- Expanded and wider candidates remain blocked from automatic app export until separately verified Android aliases/guards, parity and runtime support exist.
- Checkpoints, generated datasets and full prediction reports remain in ignored local `runs/`; they are not uploaded. The separate `tools/tally-lm` workspace remains excluded.

See [protocol and reproduction](MODEL_LADDER.md) and the [currently bundled model](COMMAND_MODEL.md).
