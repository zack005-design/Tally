<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases"><img src="docs/assets/readme-hero.svg" width="100%" alt="Tally — Clarity for every rupee. Personal spending, shared bills, and a clearer picture of your money."></a>
</p>

<h1 align="center">Tally</h1>

<p align="center">
  <strong>Your everyday money, clearly accounted for.</strong><br>
  An offline Android app for personal spending, account balances, and shared expenses.
</p>

<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases/download/v0.1.0-ios-polish-20261021/tally-ios-polish-debug.apk"><img src="docs/assets/download-android.svg" height="48" alt="Download Tally for Android"></a>
</p>

<p align="center">
  <a href="#screens">Explore the app</a>
  &nbsp;·&nbsp;
  <a href="#features">Features</a>
  &nbsp;·&nbsp;
  <a href="#privacy">Privacy</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/zack005-design/Tally/releases">Releases</a>
</p>

## Screens

<table>
  <tr>
    <th><a href="docs/screenshots/readme/overview.png">Your money at a glance</a></th>
    <th><a href="docs/screenshots/readme/shared-bill.png">A clear view of every split</a></th>
    <th><a href="docs/screenshots/readme/calendar.png">Spending, day by day</a></th>
  </tr>
  <tr>
    <td align="center"><a href="docs/screenshots/readme/overview.png"><img src="docs/screenshots/readme/overview.png" width="240" alt="Overview showing available funds, Wallet and GPay balances, and recent transactions"></a></td>
    <td align="center"><a href="docs/screenshots/readme/shared-bill.png"><img src="docs/screenshots/readme/shared-bill.png" width="240" alt="Shared-bill review showing payments, personal shares, and outstanding settlements"></a></td>
    <td align="center"><a href="docs/screenshots/readme/calendar.png"><img src="docs/screenshots/readme/calendar.png" width="240" alt="Weekly calendar showing the selected day's spending and transactions"></a></td>
  </tr>
  <tr>
    <td align="center"><sub>Balances, recent activity, and quick actions.</sub></td>
    <td align="center"><sub>What you paid, your share, and who owes whom.</sub></td>
    <td align="center"><sub>Daily totals with the transactions behind them.</sub></td>
  </tr>
</table>

*Real app captures with fictional data. Click any screen to open it at full size, or [browse the complete gallery](docs/screenshots/all-screens/README.md).*

## Features

<table>
  <tr>
    <td width="50%" valign="top">
      <h3><a href="docs/screenshots/all-screens/accounts.png">Everyday money</a></h3>
      <p>Track Wallet, GPay, and other account balances. Record expenses, income, and personal refunds; add funds, transfer money, and reconcile your balances.</p>
    </td>
    <td width="50%" valign="top">
      <h3><a href="docs/screenshots/readme/shared-bill.png">Shared bills and repayments</a></h3>
      <p>Split equally or by amounts, percentages, weights, or items. Handle multiple payers, track what friends owe, and record partial repayments.</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3><a href="docs/screenshots/all-screens/receipt-review.png">Receipts, without retyping</a></h3>
      <p>Import a photo or PDF and review text recognized on your device. Confirm the items, amounts, and payment details before saving.</p>
    </td>
    <td valign="top">
      <h3><a href="docs/screenshots/all-screens/budgets.png">Spending and planning</a></h3>
      <p>Explore transactions by day and category. Set monthly budgets, plan allowances, create recurring drafts, and track savings goals.</p>
    </td>
  </tr>
  <tr>
    <td valign="top">
      <h3><a href="docs/screenshots/all-screens/export.png">Reports and backups</a></h3>
      <p>Export monthly CSV or PDF reports for review. Create a separate, password-encrypted full backup when you need a restorable copy of your data.</p>
    </td>
    <td valign="top">
      <h3><a href="docs/screenshots/all-screens/favourites.png">Quicker daily entry</a></h3>
      <p>Reuse favourite expenses or repeat a recent purchase. Reach entry through the home-screen widget or launcher shortcut, with explicit confirmation before saving.</p>
    </td>
  </tr>
</table>

## Your payment and your share

Paying the bill does not mean spending the entire bill yourself.

For a **₹900 dinner split equally among three people**, when you pay the whole amount:

<table>
  <tr>
    <td align="center" width="33%"><h3>₹900</h3>Leaves your account</td>
    <td align="center" width="33%"><h3>₹300</h3>Your personal spending</td>
    <td align="center" width="33%"><h3>₹600</h3>Owed to you by friends</td>
  </tr>
</table>

Repayments restore your balance and reduce the debt without becoming new income. Transfers between your own accounts stay separate from spending. Integer-paise calculations keep payments and shares exact.

## Privacy

**Your records stay on your device.** Tally has no Internet permission, and receipt recognition uses a bundled on-device model.

- **No bank connection.** Wallet and GPay are balances you maintain manually; Tally does not access banks, Google Pay, SMS messages, or payment credentials.
- **Optional app lock.** Restrict access with supported device credentials or biometric authentication.
- **Encrypted backups.** Export a password-protected full backup. Automatic Android cloud backup is disabled.
- **Sharing is your choice.** CSV and PDF reports are for reading and analysis, not database restoration. You choose when and where to export them.

## Tally Companion

Review an exported Transactions CSV with your custom Codex plugins. The [export screen](docs/screenshots/ios-polish/companion-export.png) guides you through preparing the file; you explicitly attach it in Codex.

[Download Tally Companion](https://github.com/zack005-design/Tally/releases/download/v0.1.0-ios-polish-20261021/tally-companion-0.1.0.zip) &nbsp;·&nbsp; [Download Tally Toolkit](https://github.com/zack005-design/Tally/releases/download/v0.1.0-ios-polish-20261021/tally-toolkit-0.1.0.zip)

These plugins run separately in Codex. Tally does not send your records automatically.

## Release information

**Android 8.0+ · Version 0.1.0 · Development build**

INR is the core currency. Foreign bills can use a manually entered exchange rate. See the [verification report](docs/verification/ios-polish-report.md) for recorded test results and device-testing limits.

<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases">Latest release</a>
  &nbsp;·&nbsp;
  <a href="docs/screenshots/all-screens/README.md">Screen gallery</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/zack005-design/Tally/issues">Feedback and issues</a>
</p>
