[← Back to Tally](../README.md) · [User guides](README.md)

# Project structure

Tally is a native Android application written in Kotlin with Jetpack Compose. It keeps its ledger in a local Room database, with finance calculations separated from Android UI and services.

| Area | Responsibility |
| --- | --- |
| [UI](../app/src/main/java/com/smartexpense/ui) | Compose screens, navigation, draft review, and presentation. |
| [Domain](../app/src/main/java/com/smartexpense/domain) | Exact money arithmetic, splits, settlements, parsing, calculator logic, and validation. |
| [Data](../app/src/main/java/com/smartexpense/data) | Repository operations, Room persistence, backup/restore, receipt recognition, and platform services. |
| [Database schemas](../app/schemas/com.smartexpense.data.TrackerDatabase) | Historical Room schemas used to verify upgrades. |
| [JVM tests](../app/src/test) | Domain rules, parsing, arithmetic, and pure logic regressions. |
| [Android tests](../app/src/androidTest) | Database behaviour, migrations, platform integration, and Compose interactions. |
| [Command model](../ml/tally-command/COMMAND_MODEL.md) | Deployed model design, synthetic training tools, evaluation, and clearly labelled research. |
| [Tally Companion](../plugins/tally-companion) | Export review and preparation of reviewed chat-history import files. |

## Behavioural boundaries

**Money:** Integer paise represent financial amounts. A payment, a personal share, a repayment, and a transfer have distinct accounting meanings.

**Persistence:** Repository operations own ledger changes. Restore and import flows validate their inputs and use transactional writes; a preview is not a saved transaction.

**Assistance:** Expense parsing produces editable drafts. Receipt OCR recognises text locally. The calculator uses deterministic arithmetic. Companion workflows operate separately in Codex and require explicit file sharing.

**Privacy:** The Android app has no Internet permission. App lock controls access without encrypting the database. Full backups use authenticated encryption; report exports are readable files.

## Verification and distribution

[Android checks](../.github/workflows/android.yml) cover JVM tests, lint, and APK compilation for both editions, plus a disposable-emulator job for upgrade and restore safety. The [current release report](releases/0.2.3.md) records published-build verification and its limits.

Source changes on `main` can be newer than the latest APK. Read the release notes and versioned verification report to identify what a download contains. Both current APK editions are development builds. Signing and update compatibility are documented in [installation safety](installation-safety.md).

[Changelog](../CHANGELOG.md) · [Contribution guide](../CONTRIBUTING.md) · [Downloads](downloads.md)
