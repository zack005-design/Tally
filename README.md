<p align="center"><img src="app/src/main/res/drawable-nodpi/launcher_wallet.png" width="80" alt="Tally wallet icon"></p>

# Tally

**Know what you can spend. Know who owes you.**

Tally is a native Android app for tracking personal spending, Wallet and GPay balances, and shared bills with friends. It keeps your financial records on your device and calculates money in integer paise, so splits and repayments reconcile exactly.

Built with **Kotlin · Jetpack Compose · Room · Hilt**. Supports **Android 8.0+**, with **INR** as the current currency. The Android app is currently labelled **Smart Expense Tracker**.

> **Development snapshot · 11 October 2026.** This repository contains the current Android source, including features still under development. It is not a production release. The verification section distinguishes automated checks from broader device validation.

## What you can do

| Area | Features |
| --- | --- |
| Money | Separate Wallet and GPay accounts, opening balances, top-ups, adjustments, transfers and account history |
| Spending | Expenses, income, personal refunds, editable entries, duplication and reversals |
| Shared bills | Multiple payers; equal, weighted, percentage, fixed and itemized splits; shared charges and discounts |
| Friends | Saved groups, repeat-group entry, outstanding balances, partial repayments and settlement history |
| Calendar | Month and week views, daily spending patterns, selected-day entries and shared-bill context |
| Insights | Your personal share of spending, category totals, comparisons and monthly budgets |
| Entry assistance | Offline text parsing, editable drafts, live calculations and projected payment-source balances |
| Planning | A what-if money planner for costs, savings allocations and daily spending allowances |
| Daily diary | Date-based notes alongside spending entries; notes do not move money |
| Monthly review | Opening cash, income, funding, personal spending and closing balances across tracked accounts |
| GPay settings | Manual opening/current balances and optional monthly resets, disabled by default |
| Local profile | Optional name/avatar and a welcome flow |
| Backups | Local file export, optional password encryption and a validated restore preview |

### Shared bills that reflect your actual cash

For a ₹900 dinner split equally between you and two friends:

- If you pay the full bill, your selected account decreases by ₹900, your spending is ₹300, and your friends collectively owe you ₹600.
- If each person pays ₹300, your account decreases by ₹300 and no repayment is needed.
- If you pay ₹200 from Wallet and ₹700 from GPay, Tally records both sources and keeps your personal share separate from the amount you paid.

Money owed by friends stays separate from spendable cash until a repayment is recorded. Transfers and repayments do not inflate spending totals.

## A closer look

<p align="center"><img src="docs/screenshots/calendar-week.png" width="300" alt="Calendar week view with personal spending and shared-bill details"></p>

Screenshot uses fictional emulator data. Tally separates a shared bill’s full amount from your share.

## First launch

1. Open **Set up Wallet & GPay** and enter the balances you actually have, or reuse existing accounts.
2. Add an expense and choose its payment source. Review the projected balance and save it.
3. Create friends or a group, then use **Split** for a shared bill and **Repayment** when money changes hands.
4. Use the calendar and daily diary to review spending; use the monthly review for your cash breakdown.
5. Export a local backup before making significant changes to your records.

GPay resets are **off by default**. Optional reset settings choose the target amount, day and starting month. A reset is a local ledger adjustment when the app processes a due date; it does not receive funds from a bank. With resets disabled, recorded balances carry forward normally.

## Local by design

- No sign-in, server, bank connection, GPay integration or synchronization.
- The app manifest requests no Internet permission.
- GPay is a manually tracked account; entries do not move money through Google Pay.
- Android cloud backup is disabled. Export a local backup when you need a copy of your records.
- Text entry uses local parsing rules. Review the draft and explicitly save it; this is not a general-purpose AI assistant.

Encrypted backups require the password used to create them. There is no password recovery. Restoring a backup replaces the current dataset after confirmation. Local files can contain sensitive financial information, so store them carefully.

## Build and run

### Requirements

- Android Studio and a Gradle-compatible JDK. Verification uses Android Studio's bundled JDK 25; the app targets Java 17 bytecode.
- Android SDK Platform 36 and the build tools required by the Android Gradle plugin.
- An Android 8.0/API 26 or newer device or emulator.

Clone the repository and open the project in Android Studio:

```sh
git clone https://github.com/zack005-design/Tally.git
cd Tally
```

Let Android Studio configure the local SDK path, or create an untracked `local.properties` containing your SDK location. Gradle downloads dependencies on the first build; the installed app works offline.

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```sh
./gradlew :app:assembleDebug
```

Install the generated APK from `app/build/outputs/apk/debug/app-debug.apk`, or run the app from Android Studio. A debug APK is signed with a development key; store signing and release distribution are not configured.

## Verification

Run JVM tests, build and Android lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Start an emulator or connect a device, then run the Android tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Use `./gradlew` for the same tasks on macOS or Linux. Tests cover exact money calculations, split rounding, parsing, repayment routing, financial reports, persistence, corrections, migration, encrypted backups and adaptive Compose layouts.

The isolated publication snapshot on 11 October 2026 passed **75 JVM tests**, debug APK assembly, Android test APK assembly and Android lint with zero errors. The current snapshot’s device tests were compiled but were not executed on a device as part of publication. Earlier focused runs verified Wallet, calendar, funding, migration, diary and backup flows on an API 36 emulator. Physical devices and API 26 have not yet been verified.

## Project structure

```text
app/
  src/main/java/com/smartexpense/
    domain/       Money, splits, debt routing, reports and parsing
    data/         Room database, repository, preferences and backups
    ui/           Compose screens, view model and design tokens
  src/main/res/   Icons, typography and Android resources
  src/test/       JVM tests
  src/androidTest/  Repository, migration and Compose device tests
  schemas/        Versioned Room schemas used by migration tests
gradle/wrapper/  Gradle wrapper for reproducible tooling
```

Compose screens render view-model state and reviewed entry drafts. The repository owns ledger writes and transaction boundaries. Room persists accounts, transactions, payments, shares and obligations; domain functions perform calculations independently of the UI. Exported Room schema files are intentional source assets.

## Current limits and work in progress

- Saved group defaults, recurring-entry templates and savings goals are included as work in progress. Their latest changes have not completed full device validation. Recurring templates prepare entries for review rather than silently charging an account; goals represent planned allocations rather than additional cash deductions.
- Shared-bill refunds require correcting the original bill. The dedicated refund flow supports personal expenses.
- Linked repayments must be reversed before correcting a settled bill.
- Receipt recognition, local bill-image/PDF import and expanded local profile features are not completed in this checkout.
- There is no released production build or store distribution. Broader device testing remains pending.

## Contributing

Open an issue with a reproducible example or propose a focused change. For accounting changes, add tests that reconcile cash movements, personal spending and outstanding debts. Include migration and backup tests when changing persisted data. Use fictional fixtures in tests and screenshots.

The bundled Plus Jakarta Sans font is distributed under the [SIL Open Font License](docs/licenses/PlusJakartaSans-OFL.txt). The launcher artwork was generated for this project. No project-wide open-source license has been selected.

Keep build outputs, APKs, local SDK paths, logs, signing keys, personal financial files and internal working notes out of commits. Include required Gradle wrapper files, Room schemas and runtime resources.

Questions or bugs? [Open an issue](https://github.com/zack005-design/Tally/issues).
