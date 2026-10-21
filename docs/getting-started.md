[← All guides](README.md)

# Download and setup

Tally is an Android expense tracker for manually maintained accounts and shared bills. The current published download is **version 0.1.0**, a development build for **Android 8.0 or later**.

## Install Tally

1. Open the [published development release](https://github.com/zack005-design/Tally/releases/tag/v0.1.0-ios-polish-20261021).
2. Download **tally-ios-polish-debug.apk** to your Android device.
3. Open the downloaded APK. If Android asks, allow installation from the app you used to open it.
4. Complete installation and open Tally.

The release also includes two plugin ZIP files. They are separate Codex plugins, not Android installers. The APK filename contains “ios-polish” to identify its visual update; the app runs on Android.

## Set up your records

- Add the accounts you use and enter their starting balances.
- Record a small expense and check its account, category, amount, and date.
- For a group bill, review both who paid and each person's share.
- Create a full backup when you need a restorable copy of your records.

Wallet and GPay balances are maintained manually. Tally does not connect to your bank or import Google Pay activity automatically.

## Before updating an existing installation

Create a [full backup](features/reports-and-backups.md#create-a-restorable-backup) first. CSV and PDF reports cannot restore the database. If Android rejects an update, keep the existing installation and note the error before removing anything.

## Release scope

This is a development APK, not a production store release. INR is the core currency; foreign bills use manually entered exchange rates. Recorded test results and device-testing limits are in the [verification report](verification/ios-polish-report.md).

[Everyday money →](features/everyday-money.md) · [All feature guides](README.md)
