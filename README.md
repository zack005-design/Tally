<a href="https://github.com/zack005-design/Tally/releases/latest"><img src="docs/assets/readme-hero.png" width="100%" alt="Tally: Every rupee, accounted for. Track spending, split bills with friends and keep itemised receipts. Everything stays on your phone."></a>

<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases/latest"><img src="https://img.shields.io/github/v/release/zack005-design/Tally?label=release&color=0A66FF&style=flat-square" alt="Latest release"></a>
  <a href="https://github.com/zack005-design/Tally/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/zack005-design/Tally/android.yml?branch=main&label=checks&style=flat-square" alt="Android checks"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-13213F?logo=android&logoColor=white&style=flat-square" alt="Android 8.0 or later">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-13213F?logo=kotlin&logoColor=white&style=flat-square" alt="Kotlin and Jetpack Compose">
</p>

<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases/latest"><img src="docs/assets/download-android.svg" height="60" alt="Download Tally for Android"></a>
</p>

<p align="center">
  <a href="docs/getting-started.md">Installation guide</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href="docs/README.md">User guides</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href="docs/screenshots/all-screens/README.md">Screen gallery</a>&nbsp;&nbsp;|&nbsp;&nbsp;<a href="CHANGELOG.md">Changelog</a>
</p>

<br>

Tally is a personal finance app for Android that works entirely offline. It records your spending across accounts, splits shared bills fairly, and turns paper or PDF receipts into itemised records. There is no sign-up, no bank connection and no internet permission, so your financial data never leaves your phone.

<img src="docs/assets/readme-showcase.png" width="100%" alt="Four Tally screens: monthly spending by day and category, choosing a split method for a ₹900 bill, reviewing settlements before saving, and monthly budgets with progress bars.">

## Features

<table>
  <tr>
    <td width="50%" valign="top">
      <h4><a href="docs/features/everyday-money.md">Accounts and transactions</a></h4>
      Keep balances for a bank account, cash, GPay and more. Record expenses, income and refunds, move money between accounts, reconcile balances and tag transactions with your own labels.
    </td>
    <td width="50%" valign="top">
      <h4><a href="docs/features/shared-bills.md">Shared bills</a></h4>
      Split equally or by amount, percentage, shares or item. Handle several payers, partial repayments and running balances with each friend.
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h4><a href="docs/features/receipts.md">Receipts</a></h4>
      Scan a photo or PDF, check the recognised items and save. Share a complete itemised receipt PDF with the original scan attached.
    </td>
    <td valign="top">
      <h4><a href="docs/features/spending-and-planning.md">Spending and planning</a></h4>
      Daily and category views, a yearly summary, monthly budgets with optional alerts, allowances, recurring drafts and savings goals.
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h4><a href="docs/features/quick-entry.md">Quick entry</a></h4>
      Type <code>coffee 80 cash</code>, repeat a recent purchase, use favourites or the home-screen widget. You confirm every entry before it is saved.
    </td>
    <td valign="top">
      <h4><a href="docs/features/on-device-ai.md">On-device suggestions</a></h4>
      In the AI edition, a small model running on your phone turns short commands into editable drafts. It never saves anything by itself.
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h4><a href="docs/features/reports-and-backups.md">Reports and backups</a></h4>
      Export monthly reports as CSV or PDF, and create password-encrypted backups you can restore on any phone.
    </td>
    <td valign="top">
      <h4><a href="docs/features/calculator.md">Calculator</a></h4>
      Splits, tax, tips, interest, statistics and conversions, kept separate from your records.
    </td>
  </tr>
</table>

## Shared bills, done fairly

Tally keeps what you paid separate from what you spent, so lending money to friends never inflates your spending and repayments never look like income.

<img src="docs/assets/readme-split.png" width="100%" alt="A ₹900 dinner paid by you and split three ways: ₹300 counts as your spending and ₹600 is owed to you by Rahul and Ananya.">

Transfers between your own accounts are never counted as spending either.

## Privacy

| Protection | How it works |
| --- | --- |
| **Offline by design** | The app has no internet permission. Receipt recognition, suggestions and calculations all run on the device. |
| **No bank access** | Tally never reads your bank, payment apps, SMS messages or card details. You keep the balances yourself. |
| **App lock** | Optionally lock Tally with your device PIN, pattern, password or biometrics. |
| **Encrypted backups** | Backups are protected by your password. Automatic Android cloud backup is turned off. |
| **You decide what to share** | Reports and receipts are exported only when you ask. Shared receipts leave out private notes and account balances. |

## Download

The current version is **0.2.4**, for Android 8.0 and later. Tally comes in two editions with the same finance and receipt features:

| Edition | How quick entry works | APK |
| --- | --- | --- |
| **AI** | A local model suggests drafts, with rules as a fallback | [tally-0.2.4-ai-debug.apk](https://github.com/zack005-design/Tally/releases/download/v0.2.4-20261115/tally-0.2.4-ai-debug.apk) |
| **Non-AI** | Rule-based parsing only | [tally-0.2.4-non-ai-debug.apk](https://github.com/zack005-design/Tally/releases/download/v0.2.4-20261115/tally-0.2.4-non-ai-debug.apk) |

Both editions share one app ID, so you can switch between them without losing data. Make an encrypted backup before updating. See the [installation guide](docs/getting-started.md) for step-by-step instructions.

> [!NOTE]
> Tally is in active development. These APKs are debug-signed test builds and are not published on the Play Store.

## Building from source

Tally is written in Kotlin with Jetpack Compose, Room and Hilt, and targets Android 17 (API 37).

```bash
./gradlew :app:assembleDebug                      # AI edition
./gradlew :app:assembleDebug -PtallyAi=false      # Non-AI edition
./gradlew :app:testDebugUnitTest :app:lintDebug   # Unit tests and lint
```

[Contributing](CONTRIBUTING.md)&nbsp;&nbsp;|&nbsp;&nbsp;[Report an issue](https://github.com/zack005-design/Tally/issues/new/choose)
