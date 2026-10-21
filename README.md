<p align="center">
  <img src="docs/screenshots/ios-polish/launcher.png" width="80" alt="Tally logo">
</p>

# Tally

An offline Android app for personal expenses, account balances, and shared bills.

**Android 8.0+ · Kotlin · Jetpack Compose · INR**

[Download APK](https://github.com/zack005-design/Tally/releases/download/v0.1.0-ios-polish-20261021/tally-ios-polish-debug.apk) · [Releases](https://github.com/zack005-design/Tally/releases) · [Report an issue](https://github.com/zack005-design/Tally/issues)

## Features

- Track expenses, income, refunds, and Wallet or GPay balances.
- Split bills, manage group payments, and record repayments.
- Scan receipts with on-device text recognition.
- Review spending, set budgets, and export CSV or PDF reports.
- Protect access with app lock and create encrypted backups.

Records stay on your device. Accounts are maintained manually; Tally does not connect to banks or Google Pay.

## Preview

<p align="center">
  <img src="docs/screenshots/home.png" width="240" alt="Account overview">
  &nbsp;
  <img src="docs/screenshots/all-screens/shared-review.png" width="240" alt="Shared bill review">
</p>

## Build

Requires Android SDK 36 and the configured JetBrains JDK 25 toolchain.

Clone the repository, open it in Android Studio, and run:

    ./gradlew :app:assembleDebug

On Windows, use:

    .\gradlew.bat :app:assembleDebug

The APK is generated at app/build/outputs/apk/debug/app-debug.apk.

## Verification

Run unit tests and Android lint:

    ./gradlew :app:testDebugUnitTest :app:lintDebug

See the [verification report](docs/verification/ios-polish-report.md) for recorded results and testing limitations.

The downloadable APK is a development build.
