# Contributing to Tally

Tally is an Android app project in active development. Contributions from collaborators should preserve its offline behaviour and the distinction between cash movements, personal spending, and shared-bill settlements.

## Report a problem

Use the [issue forms](https://github.com/zack005-design/Tally/issues/new/choose). Include the app version, AI or non-AI edition, Android version, steps to reproduce, and expected versus actual behaviour. Use fictional or redacted examples for finance-related reports.

## Propose a change

Describe the user problem and the intended behaviour before a substantial change. Keep each update focused, explain changes to stored data or accounting rules, and update the relevant [user guide](docs/README.md).

## Review expectations

- Add a focused regression test for a behavioural fix.
- Preserve integer-paise calculations, account balances, and settlement invariants.
- Cover database or backup changes with migration, rejected-input, and rollback checks.
- Check UI changes at narrow widths and large text, using visible semantics for interaction tests.
- Keep model suggestions and imports reviewable; do not introduce silent financial writes.
- Include the checks actually run and any device or platform limits in the change description.

## Repository boundaries

Keep keys, credentials, real spending exports, personal backups, generated builds, caches, and machine-specific settings out of commits. Required project assets and synthetic test fixtures belong with their source. APKs and plugin packages are distributed through [Releases](https://github.com/zack005-design/Tally/releases).

The separate LLM workspace is excluded from this repository. The specialised app command model and its research live under [ml/tally-command](ml/tally-command/COMMAND_MODEL.md).

[Project structure](docs/project.md) · [Installation and data safety](docs/installation-safety.md) · [Project instructions](AGENTS.md)
