[← All guides](README.md)

# Download and setup

Tally is an Android expense tracker for manually maintained accounts and shared bills. The current published AI download is **version 0.2.0**, a development build for **Android 8.0 or later** with local expense suggestions and an offline calculator.

## Install Tally

1. Open the [published development release](https://github.com/zack005-design/Tally/releases/tag/v0.2.0-ai-20261022).
2. Download **tally-0.2.0-ai-debug.apk** to your Android device.
3. Open the downloaded APK. If Android asks, allow installation from the app you used to open it.
4. Complete installation and open Tally.

The expense-command model is bundled in the AI APK and requires no separate download. The [Companion plugin ZIPs](features/companion.md) remain available from the earlier release; they are separate Codex plugins, not Android installers.

## Set up your records

- Add the accounts you use and enter their starting balances.
- Record a small expense and check its account, category, amount, and date.
- For a group bill, review both who paid and each person's share.
- Create a full backup when you need a restorable copy of your records.

Wallet and GPay balances are maintained manually. Tally does not connect to your bank or import Google Pay activity automatically.

## Before updating an existing installation

Create a [full backup](features/reports-and-backups.md#create-a-restorable-backup) first. CSV and PDF reports cannot restore the database. If Android rejects an update, keep the existing installation and note the error before removing anything.

## Release scope

This is a development APK, not a production store release. INR is the core currency; foreign bills use manually entered exchange rates. Read the [expense-suggestion guide](features/on-device-ai.md) and [calculator guide](features/calculator.md) for capabilities and recorded checks. The [earlier visual-update report](verification/ios-polish-report.md) covers UI testing and device limits.

[Everyday money →](features/everyday-money.md) · [All feature guides](README.md)
