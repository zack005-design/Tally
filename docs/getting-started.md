[← All guides](README.md)

# Download and setup

Tally is an Android expense tracker for manually maintained accounts and shared bills. The current download is **version 0.2.1**, a development build for **Android 8.0 or later**, available in AI and non-AI editions. Both include the offline calculator and receipt scanner.

## Install Tally

Use the [download guide](downloads.md) to choose the AI or non-AI edition and find the correct asset.

1. Open the [published development release](https://github.com/zack005-design/Tally/releases/tag/v0.2.1-20261022) in your Android browser. Sign in to a GitHub account with access to this private repository.
2. Expand **Assets** below the release notes.
3. Tap **tally-0.2.1-ai-debug.apk** for local expense suggestions, or **tally-0.2.1-non-ai-debug.apk** for rule-based expense parsing.
4. Open the downloaded APK. If Android asks, allow installation from the app you used to open it.
5. Complete installation and open Tally.

If a chat or app's built-in browser opens the link but nothing downloads, use its **Open in browser** option and sign in there. A 404 page can mean the browser is signed out or the account lacks repository access.

Choose an **.apk** asset to install the app. **Source code (zip)** and **Source code (tar.gz)** are project files, not Android installers. The two app editions replace one another rather than installing side by side; create a backup before switching or updating.

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
