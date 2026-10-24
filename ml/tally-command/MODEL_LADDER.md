# Scratch model ladder: 100K → 500K → 1M

These are research candidates for Tally's specialised expense-command parser, not conversational LLMs. Training starts with random weights; no pretrained weights or private expense records are used. The existing app model remains installed until a candidate passes a separate promotion review and Android verification.

**Completed experiment:** [measured results and non-promotion decision](MODEL_LADDER_RESULTS.md). All three requested sizes were trained; none met the complete non-regression requirement.

## New capability data

The expanded synthetic corpus adds bounded Latin-script Hinglish expense forms and a small explicit typo allowlist: `gpy`, `gpaay`, `gooogle pay`, `wallett`, `walllet`. Examples include `maine chai pe 25 wallet se kharch kiye`. This is neither arbitrary Hindi understanding nor general typo correction. Description, exact INR amount and account remain copied/extracted fields; every draft requires review before saving.

Hindi negation/planning terms (`nahi`, `nahin`, `mat`, `kal`, `karunga`, `karungi`, `karenge`, `dunga`, `dungi`) are conservatively rejected by research decoding. `kal` is ambiguous between yesterday and tomorrow. Development/test templates are disjoint from training; all still share synthetic vocabulary. The new 24-case challenge was authored before ladder training and is never training material. Old tests/challenges are regression sets, not new independent evidence.

## Protocol

- Sizes target 100,000 / 500,000 / 1,000,000 parameters. The nearest GRU width is chosen without unused padding parameters. Actual counts are recorded.
- Same expanded 60,000/ 2,000/ 2,000 train/development/test corpus, seed 2027, 5,000 updates, batch 96, AdamW schedule and regularisation per stage.
- Development-only threshold selection requires at least 20 accepted drafts and zero accepted development errors. Failure preserves research checkpoint/report and refuses portable/app export. A rejected run is not deployable.
- Development diagnostics prompted a stricter description-boundary check: filler words and incomplete noun spans are refused. This guard was revised using development rows before inspecting test/challenge results. `recalibrate_ladder.py INPUT OUTPUT` reapplies the unchanged precision gate to development data only. Original weights and the frozen corpus remain unchanged. Use `--dataset runs/ladder-500k/dataset.json` to reuse that corpus for controlled reruns.
- Evaluation compares incumbent, expanded held-out set, legacy 2,000-case test, legacy 32-case challenge, new 24-case challenge, and safety cases. Guardrails contribute to refusal scores.
- Python portable parity, model size and warmed CPU latency are measured. Loss and parameter count alone do not determine promotion. Old/new-corpus differences confound size and data; expanded candidates share data.
- Python supports larger research widths. Android currently supports at most 128 hidden units; app export explicitly refuses wider candidates and all expanded candidates until Android aliases/guards and parity are separately verified. Incumbent comparisons use current Python research guards, not the shipped Android implementation.

## Reproduce locally

Use a compatible Python/PyTorch environment and `requirements-lock.txt`. From this directory:

```sh
python -m unittest test_command test_command_model
python train_command.py --expanded --target-parameters 100000 --steps 5000 --run-dir runs/ladder-100k
python train_command.py --expanded --target-parameters 500000 --steps 5000 --run-dir runs/ladder-500k
python train_command.py --expanded --target-parameters 1000000 --steps 5000 --run-dir runs/ladder-1m
python compare_ladder.py runs/command-v3 runs/ladder-100k runs/ladder-500k runs/ladder-1m --output runs/ladder-comparison.json
```

Fresh run/output directories prevent overwrites. Comparison expects locally retained `runs/command-v3` checkpoint/dataset; reproduce it with `python train_command.py --steps 5000 --run-dir runs/command-v3` if absent. Runs, checkpoints, generated datasets and caches remain ignored/local. Only source, synthetic challenge fixtures and useful compact documentation are published.

## Tool verification

Fourteen Python tests cover target sizes, larger portable round trips, malformed exports, development-gate rejection, frozen-corpus provenance, complete description boundaries, exact amounts, bounded aliases, negation, deterministic corpora and disjoint splits. Python compilation checks are included. CUDA is available on the local RTX 3050 laptop GPU. Training/evaluation results and promotion decisions are documented separately when verified.
