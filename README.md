<p align="center">
  <img src="docs/screenshots/ios-polish/launcher.png" width="88" alt="Tally app logo">
</p>

<h1 align="center">Tally</h1>

<p align="center">
  Personal spending and shared expenses, clearly accounted for.
</p>

<p align="center">
  <strong>Android 8.0+ · Offline · INR</strong>
</p>

<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases/download/v0.1.0-ios-polish-20261021/tally-ios-polish-debug.apk">Download for Android</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/zack005-design/Tally/releases">Releases</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/zack005-design/Tally/issues">Report an issue</a>
</p>

Tally brings account balances, everyday spending, and shared bills into one place. Keep track of the money available to you, understand your share of each expense, and see who still needs to settle up.

## Inside the app

<table>
  <tr>
    <th>Overview</th>
    <th>Shared bills</th>
    <th>Spending calendar</th>
  </tr>
  <tr>
    <td><img src="docs/screenshots/readme/overview.png" width="240" alt="Overview showing available funds, Wallet and GPay balances, and recent spending"></td>
    <td><img src="docs/screenshots/readme/shared-bill.png" width="240" alt="Shared-bill review separating the amount paid, personal share, and outstanding settlements"></td>
    <td><img src="docs/screenshots/readme/calendar.png" width="240" alt="Weekly spending calendar with a selected day and its transactions"></td>
  </tr>
</table>

*Actual app screens with fictional test data. [View more screens](docs/screenshots/all-screens/).*

## Everyday money, organized

| Feature | What it helps you do |
| --- | --- |
| **Accounts and balances** | Track Wallet, GPay, and other accounts; add funds, move money between accounts, and check recorded balances. |
| **Expenses and income** | Record purchases, income, and personal refunds. Use favourite templates or repeat a recent expense to reduce entry time. |
| **Shared bills** | Split expenses equally or by amounts, percentages, weights, or individual items. Record multiple payers and shared charges. |
| **Friends and repayments** | See outstanding balances, record partial repayments, and keep a history of settlements and group expenses. |
| **Receipts** | Import a receipt photo or PDF, review text recognized on the device, and confirm the details before saving. |
| **Spending and planning** | Explore transactions by day or category, set monthly budgets, plan allowances, and track savings goals. |
| **Reports and reminders** | Export monthly CSV or PDF reports, create recurring expense drafts, and enable optional local reminders. |

A home-screen widget and launcher shortcut make expense entry easier to reach. Entries are saved only after you confirm them.

## Shared expenses that reflect your actual money

What you pay and what you spend are not always the same. Tally tracks both.

If you pay a **₹900 dinner bill** shared equally by three people, your account decreases by ₹900, your personal spending is ₹300, and your friends owe you ₹600. Their repayments restore your account balance and reduce the outstanding debt without being counted as new income.

Transfers between your own accounts stay separate from spending. Calculations use integer paise so payments and shares reconcile exactly.

## Private by design

Your financial records and receipt attachments stay on your device. Tally has no Internet permission, and receipt recognition uses a bundled on-device model.

- **Manual accounts:** Wallet and GPay are balances you maintain. Tally does not access banks, Google Pay, SMS messages, or payment credentials.
- **Optional app lock:** Use your device's supported biometric or credential authentication to restrict access.
- **Encrypted backups:** Export a password-protected full backup for restoration. Automatic Android cloud backup is disabled.

CSV and PDF reports are separate from backups: they are for reading and analysis, not for restoring the database. You choose when and where to export or share a file.

## Reports and Tally Companion

The export screen includes a guide for reviewing a Transactions CSV with **Tally Companion** or **Tally Toolkit**, available in the [release downloads](https://github.com/zack005-design/Tally/releases/tag/v0.1.0-ios-polish-20261021).

These are separate Codex plugins. You explicitly attach the exported CSV for review; Tally does not send your records automatically.

## Availability

The current download is an Android **0.1.0 development build**, with INR as its core currency. Foreign bills can be converted using a manually entered exchange rate.

Recorded verification covers unit tests, selected Android emulator flows, accessibility-related layouts, and reduced-motion behavior. See the [verification report](docs/verification/ios-polish-report.md) for results and device-testing limits.

Found a problem or have a suggestion? [Open an issue](https://github.com/zack005-design/Tally/issues) with the app version and a short description of what happened.
