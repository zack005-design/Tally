# Command model v5 (1M extension)

The current app source replaces the v4 extension with a **1,002,068-parameter** bidirectional GRU trained from random weights. The **53,142-parameter** compatibility model is unchanged and is still checked first; the extension handles its abstentions. Combined learned parameters: **1,055,210**. Neither model is a conversational LLM or a math engine. The [v4 report](IMPROVED_1M.md) describes the previous extension and the app runtime, which v5 keeps.

## What is new

- **Accounts:** `upi` maps to the app's UPI slot (GPay). `phonepe`, `phone pe` and `paytm` are returned under their own names and resolve only to an active account with that exact name; otherwise the account stays empty for you to choose. This is an explicit allowlist, not fuzzy matching.
- **English phrasing:** account before amount (`momos cash 120`, `wallet: vada pav 30`), `worth`, `ordered … on gpay`, `paid … to …`, `just paid …`, `expense: …, …, …` and similar structures.
- **Hinglish phrasing:** `mein … gaye`, `… liya`, `… khareeda … mein`, `… ka bill … bhara`, `… ke … se`.
- **Descriptions:** about 40 more everyday items such as Swiggy/Zomato orders, petrol, sabzi, doodh, momos and movie tickets (115 in total).
- **Guards:** account words may end a description; Hinglish particles (`mein`, `ka`, `ke`, `ki`, `se`, `pe`, `par`) can never start one.

Negation, plans, questions, multiple amounts, conflicting accounts, shared bills and foreign currencies are still refused before a personal-expense draft is accepted.

## Training and selection

`command_v5_data.py` generates 140,000 training, 2,000 development and 3,000 held-out examples (seed 41017). All v4 development and test structures are now training material; the v5 development and test structures are new and disjoint after tokenizer normalisation. `train_improved.py --recipe improved-v5` uses the v4 recipe unchanged: batch 128, AdamW, cosine schedule, bounded token masking, and development-only checkpoint selection requiring at least 20 accepted drafts and zero accepted errors. **Step 3,000 of 8,000** was selected. Training ran on four CPU threads in 1,535 seconds (shared with other jobs); no GPU was used.

Vocabulary 268, embedding 128, hidden 341 per direction. Threshold 0.8 is an uncalibrated score. Portable bundle: 4,011,202 bytes; SHA-256 `19f0373969769ffa1d30d3376801faa4b45f95eff7ebd9f7935fd5f619a9698c`. Dataset SHA-256 `f364c9e11e7896dc1b8ec00bf956b6931eadfe4f6f8879d6db633f0bb820c359`.

### A guard change after seeing held-out results

The first held-out score accepted 245 wrong drafts, all from one new Hinglish structure (`{amount} mein {description} khareeda {account} se`): the model included `mein` in the description. The development set had saturated at 100% and could not catch this. Hinglish particles were then forbidden from starting a description, in Python and Kotlin, without retraining. The held-out set below is therefore **inspected regression evidence**, not an untouched test. The frozen v5 challenge was written before any v5 result and was scored only once, after this change.

## Composite evaluation

Compatibility model first, extension on abstention, scored by `evaluate_improved.py` against both the compatibility model alone and the currently shipped v4 system under the same guards. The gate requires zero accepted errors, no regression against either, at least 10/12 on the v4 capability challenge and at least 9/12 on the v5 challenge.

| Set | Supported | v4 system | v5 system | Wrong returned drafts | Unsupported accepted |
| --- | ---: | ---: | ---: | ---: | ---: |
| v5 held-out templates (inspected) | 2,384 | 530 | 2,105 | 0 | 0/616 |
| Legacy regression corpus (regenerated) | 1,547 | 1,547 | 1,547 | 0 | 0/453 |
| Legacy challenge | 16 | 14 | 14 | 0 | 0/16 |
| v4 Hinglish/typo challenge | 12 | 11 | 11 | 0 | 0/12 |
| Safety regression cases | 6 | 5 | 5 | 0 | 0/24 |
| **v5 challenge (frozen)** | 12 | 0 | 11 | 0 | 0/12 |

The one v5 challenge miss, `petrol 1,200 paytm`, defers for manual confirmation rather than returning a wrong draft.

The original v3 legacy corpus was not in this checkout, so it was regenerated with `make_corpus(seed=2027)` from the current generator. Generator vocabulary has grown since v3, so this set has 1,547 supported rows instead of the original 1,563; it is legacy-style regression evidence, not the original file.

These synthetic results are not independent real-user language accuracy or a universal safety guarantee. Deterministic checks contribute heavily to refusals.

## App integration and verification

`ScratchCommandModel` mirrors the v5 aliases and description boundaries for the extension; the compatibility policy is unchanged. The command parser's "confirm manually" warning now also covers the new account and Hinglish words. The cross-runtime fixture has 410 Python-computed cases covering every draft field, model routing and score tolerance; the existing 220 compatibility fixtures are unchanged.

Verified in this update: 25 Python tests; all JVM unit tests and Android lint (zero errors) in both editions; AI and non-AI debug APKs and the instrumentation APK build; the non-AI APK contains no model files. No emulator, physical-device or latency measurement was run for v5, and published APK releases are unchanged.

## Reproduce

From `ml/tally-command/` with PyTorch 2.6 installed (CPU or CUDA):

```sh
python -m unittest discover -s . -p 'test_*.py'
python -c "import json; from command_data import make_corpus; t,d,e=make_corpus(seed=2027); import pathlib; pathlib.Path('runs/command-v3').mkdir(parents=True, exist_ok=True); pathlib.Path('runs/command-v3/dataset.json').write_text(json.dumps({'train':t,'dev':d,'test':e}, ensure_ascii=False))"
python train_improved.py --recipe improved-v5 --run-dir runs/improved-v5 --steps 8000
python evaluate_improved.py --run-dir runs/improved-v5 --output-dir runs/improved-v5-evaluation
python export_extension.py --run-dir runs/improved-v5 --evaluation-dir runs/improved-v5-evaluation
```

Run, checkpoint, corpus and prediction files stay in the ignored `runs/` folder.

# Category suggester v1

A separate **40,970-parameter** model suggests a spending category when the rules cannot tell. Each description is split into lowercase words, their character 3–5-grams and adjacent word pairs, hashed with 32-bit FNV-1a into 4,096 rows of 10 scores (one per built-in category except Other), summed and divided by the square root of the feature count. It was trained from random weights by `train_category.py` (seed 5309) on an assistant-authored lexicon of 462 terms in `category_data.py`, with random filler words and nonsense words trained towards a uniform score so unfamiliar text abstains.

The threshold (0.8245) is the lowest development score with zero accepted errors. Bundle: 164,022 bytes; SHA-256 `87a400106441fbe373b9c6d1c1d63d875cf965d20f46573524435022d242e7e0`.

| Set | Known-term variants | Accepted | Wrong | Unknown words accepted |
| --- | ---: | ---: | ---: | ---: |
| Development (new modifiers and suffixes) | 462 | 304 | 0 | 0/36 |
| Test (other modifiers, typos, `A and B` phrases) | 502 | 266 | 0 | 0/36 |
| Lexicon terms themselves | 462 | 458 | 0 | — |
| Diagnostic: completely unseen words | 46 | 14 | 4 | — |

The model recognises variants of terms it knows; it does **not** know the meaning of new words, and the diagnostic shows unseen-word guesses are unreliable. In the app it fills only a missing category of a personal expense: the user's own matching history (below) comes first, then the rules' category, then this model. Kotlin `CategoryModel` reproduces the Python scores on 1,046 parity cases.

## Personal history

`PersonalSuggestions.kt` is not a trained model. For a new draft it looks at up to 30 recent personal expenses with the same description (ignoring case and spacing). If at least two agree and the leading choice covers two thirds of them, the draft uses that category, and that account when none was typed. It runs in both editions, only on the phone.
