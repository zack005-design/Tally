# Tally's tiny reasoning model

A **4,843,776-parameter** decoder-only transformer, trained from random weights on this project's own synthetic questions. No pretrained weights, no external teacher model and no private records were used. It runs on the phone in the AI edition (`tally-assistant.bin`, 19.4 MB) through a plain Kotlin implementation, with no native AI runtime and no network.

It is a small language model that **plans**, not a chatbot. It reads a money question and writes a short step program:

```
did i spend more on food this month than last month
→ spend this_month cat CAT1 ; spend last_month cat CAT1 ; compare $1 $2 ; answer
```

Tally's exact code then runs each step over local records and shows the steps as "How I worked it out". **The model never produces a number**: amounts, totals, percentages and dates all come from saved entries or from figures the user typed.

## How a question is handled

1. **Anonymise** (`assistant_data.anonymise`, mirrored by `AssistantPlanner.anonymise`): the user's category and account names become `CAT1…`, `ACC1…`; amounts and counts become `NUM1…`; other unfamiliar words become `WORD1…`. The model only ever sees the shape of the question.
2. **Plan**: greedy decoding with a key/value cache, at most 40 tokens.
3. **Check** (`assistant_validate.py`, mirrored by `AssistantValidator.kt`):
   - grammar of every step, at most four steps;
   - every name and number in the question is used by the plan, and nothing else;
   - the time periods named in the question are exactly the ones in the plan;
   - meaning cues: percentage wording needs a share or what-if step, comparison wording needs a compare step, and a help topic must be the single best match for the question's topic words;
   - the first step's confidence (lowest chosen-token probability) must reach that step's threshold.
4. **Run** the steps with exact code (`AssistantPlanner.Executor`). Any failure means no answer; an `unknown` plan is a refusal.

If the model declines, Tally falls back to the exact spending-question rules and then to normal entry handling.

## Steps it can plan

`spend`, `income`, `count`, `top`, `largest`, `average` over a period (today … last year, a month name, the last N days) with an optional category, account or description; `compare` and `share` of two totals; `budget` left; month-end `forecast`; `saveplan` (amount in N months) and `savetime` (months to reach an amount); `whatif` (cut a category by a percentage); `help` on eight app topics; `unknown`.

## Training and selection

`assistant_data.py` generates 60,000 training, 1,500 development and 1,500 test questions (seed 7331) from 13 intent families, English and some Latin-script Hinglish. Templates are split by position, so development and test phrasings never appear in training. Training (`train_assistant.py`) uses AdamW, cosine schedule, dropout 0.1, and training-only augmentation (synonym swaps, word dropout, filler words). Width 256, 6 layers, 8 heads, feed-forward 1,024, context 96, vocabulary 313. Development exact-match was checked every 1,000 steps; **step 6,000 of 8,000** was selected. CPU training took 1,719 seconds.

A first run without dropout or augmentation plateaued near 55% development exact-match and was discarded. Dataset SHA-256 (first 16 hex) `9af437823bf0e340`; bundle SHA-256 `3308ca8324f86d9f57a56a4ca83bcb9d947acc8efe41548993eb4052e0efcf0d`.

## Results

`export_assistant.py` chooses one threshold per first step on development data only: the lowest with zero wrong answers for that step (floor 0.5). A step never predicted on development data gets 1.01 and is never answered. The held-out test is then scored once, and any wrong answer blocks export.

| Set | Questions | Exact plan | Answered | Wrong answers | Declined |
| --- | ---: | ---: | ---: | ---: | ---: |
| Development | 1,479 | 1,029 (69.6%) | 902 | 0 | 577 |
| Held-out test | 1,500 | 1,210 (80.7%) | 830 | 0 | 670 |

21 development rows were excluded as label noise. Their template used the word "cash", which is also an account name, so the expected plan ignored a placeholder the question contained.

Held-out answers by question type: spending 363/363, budget 70/70, compare categories 75/75, count 63/63, forecast 75/75, income 68/68, saving plans 54/137, help 33/92, compare periods 24/138, share 5/75. Averages, largest expense, top categories, what-if and saving timelines were never answered on this test set: their thresholds are 1.01 or very high because development data gave no clean evidence. Those questions fall back to the exact rules where possible.

**These are synthetic, assistant-authored phrasings, not real-user accuracy.** Wording far from the templates is usually declined. The validator and meaning cues were developed after looking at development and held-out errors, so the held-out set is now inspected evidence; a fresh set is needed for an untouched measurement.

## Verification

- 4 Python tests (`test_assistant.py`).
- Kotlin matches Python exactly on 200 frozen questions (program tokens, and confidence within 0.002), on 8 anonymiser cases and on 603 validator cases.
- Executor tests cover comparisons, budgets, shares, saving plans, what-if and rejection of invalid plans.
- An end-to-end JVM test runs the bundled model on records.
- About 60 ms per question on the build machine's CPU with the cache. Phone speed and battery use have not been measured.

## Reproduce

```sh
python -m unittest test_assistant
python train_assistant.py --run-dir runs/assistant-v1 --steps 8000
python export_assistant.py --run-dir runs/assistant-v1
python write_validate_fixture.py runs/assistant-v1
```

Runs, checkpoints and generated datasets stay in the ignored `runs/` folder. This folder is separate from the excluded `tools/tally-lm/` workspace.
