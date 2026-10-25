# Changelog

User-facing changes in the published development releases. Downloadable APKs are prereleases, not production store builds.

## Unreleased

- Improve window-aware sheet sizing, Compose state handling, and large-text layout checks.
- Simplify expense widget shortcuts while preserving category icons, readable labels, and touch targets.
- Use AndroidX receipt metadata handling and support themed launcher icons across adaptive-icon devices.
- Update Android libraries and target Android 17; migrate the build to AGP 9's supported Kotlin and DSL configuration.
- Refresh Compose test APIs and Android test-runner compatibility, with Android 17 included in CI.

## [0.2.3](https://github.com/zack005-design/Tally/releases/tag/v0.2.3-20261024)

- Combined the completed finance, import, backup, and UI updates with expanded offline calculator tools.
- Added calculator statistics, ratios, percentage changes, tips, reverse tax, and interest arithmetic.
- Published source-matched AI and non-AI APKs, Tally Companion 0.2.0, and checksums.

[Verification and known limits](docs/releases/0.2.3.md)

## [0.2.2](https://github.com/zack005-design/Tally/releases/tag/v0.2.2-20261024)

- Added reviewed ChatGPT-history import with explicit preview and confirmation.
- Required password-protected authenticated encryption for new backups while retaining legacy imports.
- Added migration and restore safety checks, refined app-lock layout, and updated motion and press feedback.
- Packaged the completed safety/UI snapshot before the subsequent calculator expansion.

## [0.2.1](https://github.com/zack005-design/Tally/releases/tag/v0.2.1-20261022)

- Added separate AI and non-AI APKs with a shared app identity and signing certificate.
- Verified model inclusion/exclusion while retaining the calculator and receipt scanner in both editions.

## [0.2.0](https://github.com/zack005-design/Tally/releases/tag/v0.2.0-ai-20261022)

- Introduced bundled expense-command suggestions and an offline calculator.
- Kept suggestions editable, with explicit saving and existing rules-based handling for other transaction types.

## [0.1.0 visual update](https://github.com/zack005-design/Tally/releases/tag/v0.1.0-ios-polish-20261021)

- Added the Tally launcher mark, screen transitions, and compact/large-text layout refinements.
- Published the first Tally Companion and Tally Toolkit packages alongside the Android development APK.
