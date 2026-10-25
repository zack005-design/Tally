<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases"><img src="docs/assets/readme-hero.svg" width="100%" alt="Tally — Clarity for every rupee. Personal spending, shared bills, and a clearer picture of your money."></a>
</p>

<h1 align="center">Tally</h1>

<p align="center">
  <strong>Personal finance. Shared expenses. On your device.</strong><br>
  Track balances, understand your share of every bill, and turn everyday expenses into clear records.
</p>

<p align="center">
  <a href="docs/downloads.md"><img src="docs/assets/download-android.svg" height="48" alt="Open Tally Android downloads"></a>
</p>

<p align="center">
  <a href="#screens">Explore the app</a>
  &nbsp;·&nbsp;
  <a href="#features">Features</a>
  &nbsp;·&nbsp;
  <a href="#on-device-ai">AI</a>
  &nbsp;·&nbsp;
  <a href="docs/README.md">Guides</a>
  &nbsp;·&nbsp;
  <a href="#privacy">Privacy</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/zack005-design/Tally/releases">Releases</a>
</p>

## Download

**Android 8.0+ · Version 0.2.3 · Development prerelease**

| Edition | Expense entry | Included in both |
| --- | --- | --- |
| [AI edition](docs/downloads.md#android-ai-edition) | Local model suggestions with rules fallback | Accounts, shared bills, receipt OCR, calculator, reports, and backups |
| [Non-AI edition](docs/downloads.md#android-non-ai-edition) | Rule-based parsing without the custom model | The same finance features and receipt OCR |

Download the APK from **Assets** on the [release page](https://github.com/zack005-design/Tally/releases/tag/v0.2.3-20261024). Both editions share an app identity and replace one another. [Installation and update guide](docs/getting-started.md).

## Screens

<table align="center" width="100%">
  <tr>
    <th align="center" width="33%"><a href="docs/screenshots/readme/overview.png">Your money at a glance</a></th>
    <th align="center" width="33%"><a href="docs/screenshots/readme/shared-bill.png">A clear view of every split</a></th>
    <th align="center" width="33%"><a href="docs/screenshots/readme/calendar.png">Spending, day by day</a></th>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%"><a href="docs/screenshots/readme/overview.png"><img src="docs/screenshots/readme/overview.png" width="240" alt="Overview showing available funds, Wallet and GPay balances, and recent transactions"></a></td>
    <td align="center" valign="top" width="33%"><a href="docs/screenshots/readme/shared-bill.png"><img src="docs/screenshots/readme/shared-bill.png" width="240" alt="Shared-bill review showing payments, personal shares, and outstanding settlements"></a></td>
    <td align="center" valign="top" width="33%"><a href="docs/screenshots/readme/calendar.png"><img src="docs/screenshots/readme/calendar.png" width="240" alt="Weekly calendar showing the selected day's spending and transactions"></a></td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%"><sub>Balances, recent activity, and quick actions.</sub></td>
    <td align="center" valign="top" width="33%"><sub>What you paid, your share, and who owes whom.</sub></td>
    <td align="center" valign="top" width="33%"><sub>Daily totals with the transactions behind them.</sub></td>
  </tr>
</table>

*App captures use fictional data and show the 0.1.0 core finance interface. Open a screen at full size, or [browse the complete gallery](docs/screenshots/all-screens/README.md). Newer AI and calculator workflows are documented in their guides.*

## Features

<table align="center" width="100%">
  <tr>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/on-device-ai.md">On-device expense suggestions</a></h3>
      <p>Turn a short English expense command into an editable draft. The AI edition suggests the description, INR amount, and account locally. Review every field before saving.</p>
    </td>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/calculator.md">Offline calculator</a></h3>
      <p>Work out totals, percentages, splits, statistics, ratios, tax, tips, and interest. Convert units or use your own exchange rate. Results stay separate from your ledger.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/everyday-money.md">Everyday money</a></h3>
      <p>Track Wallet, GPay, and other account balances. Record expenses, income, and personal refunds; add funds, transfer money, and reconcile your balances.</p>
    </td>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/shared-bills.md">Shared bills and repayments</a></h3>
      <p>Split equally or by amounts, percentages, weights, or items. Handle multiple payers, track what friends owe, and record partial repayments.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/receipts.md">Receipts, without retyping</a></h3>
      <p>Import a photo or PDF and review text recognized on your device. Confirm the items, amounts, and payment details before saving.</p>
    </td>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/spending-and-planning.md">Spending and planning</a></h3>
      <p>Explore transactions by day and category. Set monthly budgets, plan allowances, create recurring drafts, and track savings goals.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/reports-and-backups.md">Reports and backups</a></h3>
      <p>Export monthly CSV or PDF reports for review. Create a separate, password-encrypted full backup when you need a restorable copy of your data.</p>
    </td>
    <td width="50%" align="left" valign="top">
      <h3><a href="docs/features/quick-entry.md">Quicker daily entry</a></h3>
      <p>Reuse favourite expenses or repeat a recent purchase. Reach entry through the home-screen widget or launcher shortcut, with explicit confirmation before saving.</p>
    </td>
  </tr>
</table>

## On-device AI

The released AI edition uses a specialised expense-command model to prepare drafts from short English input. It runs on the phone, without an API key or cloud inference.

> **Example:** `coffee 80 cash` suggests the description, ₹80 amount, and cash-account hint. You review the resolved account and every field, then choose **Save**.

The model has **53,142 parameters**, with a **roughly 210 KiB** weight bundle trained from random weights on synthetic commands. It selects text and amount spans from your input; exact decimal code converts money to integer paise. Low-confidence or unsupported commands fall back to rules or require manual correction.

| Component | Role | Where it runs |
| --- | --- | --- |
| Expense-command model | Suggests single-INR personal-expense drafts in the AI edition | On your device |
| Receipt OCR | Recognises text from imported receipts in both editions | On your device |
| Calculator | Uses deterministic rules and decimal arithmetic | On your device |
| Tally Companion | Reviews exports or prepares a chat-history import file you explicitly share | Separately in Codex |

The command model is not a conversational assistant and does not answer ledger-history questions. It never saves a transaction by itself. Research models in the repository do not automatically replace the model in a published APK.

[AI usage and limits](docs/features/on-device-ai.md) · [Deployed model design](ml/tally-command/COMMAND_MODEL.md) · [Recorded evaluation](ml/tally-command/VERIFICATION_V3.md) · [Model research](ml/tally-command/MODEL_LADDER.md)

## Your payment and your share

See what you paid, what you personally spent, and what remains to be settled.

For a **₹900 dinner split equally among three people**, when you pay the whole amount:

<table align="center" width="100%">
  <tr>
    <td align="center" width="33%" valign="top"><h3><a href="docs/features/payment-and-share.md#what-you-paid">₹900</a></h3>Leaves your account</td>
    <td align="center" width="33%" valign="top"><h3><a href="docs/features/payment-and-share.md#your-personal-share">₹300</a></h3>Your personal spending</td>
    <td align="center" width="33%" valign="top"><h3><a href="docs/features/payment-and-share.md#what-remains-to-be-settled">₹600</a></h3>Owed to you by friends</td>
  </tr>
</table>

Repayments reduce what is owed without becoming new income. Transfers between your own accounts stay separate from spending.

[Explore payments, shares, and settlements →](docs/features/payment-and-share.md)

## Privacy

**Your records stay on your device.** Tally has no Internet permission. Receipt recognition and expense suggestions run locally; calculator results do not change your records.

- **No bank connection.** Wallet and GPay are balances you maintain manually; Tally does not access banks, Google Pay, SMS messages, or payment credentials.
- **Optional app lock.** Restrict access with supported device credentials or biometric authentication.
- **Encrypted backups.** Export a password-protected full backup. Automatic Android cloud backup is disabled.
- **Sharing is your choice.** CSV and PDF reports are for reading and analysis, not database restoration. You choose when and where to export them.

[Read about privacy and app lock →](docs/features/privacy.md)

## Tally Companion

Tally Companion 0.2.0 connects two explicit workflows: review a Transactions CSV exported from Tally, or prepare spending history from a ChatGPT conversation for import into the app. Imported entries are previewed and require confirmation; unsupported records remain flagged for manual handling.

[Download Tally Companion](docs/downloads.md#tally-companion) &nbsp;·&nbsp; [Download Tally Toolkit](docs/downloads.md#tally-toolkit)

The plugins run separately in Codex. Tally does not send records automatically. [Export review](docs/features/companion.md) · [Chat-history import](docs/features/chat-history-import.md).

## Release information

Tally is in active development. The published 0.2.3 APKs are debug-signed test builds, with **173 JVM tests passed per edition**, zero lint errors, and recorded API36 emulator checks. Those published builds include lint warnings; physical-phone and minimum-API verification are pending.

INR is the core currency, and foreign bills use manually entered exchange rates. Keep a full encrypted backup before updating or switching editions. [Verification and limits](docs/releases/0.2.3.md) · [Installation safety](docs/installation-safety.md) · [Changelog](CHANGELOG.md).

## Project

Built with Kotlin and Jetpack Compose, with Room for local records and a repository-based data layer. Finance calculations stay separate from UI and Android services; user actions explicitly confirm changes to the ledger.

Current source targets Android 17 (API 37) and uses AGP 9's built-in Kotlin support. Source updates are listed under [Unreleased](CHANGELOG.md#unreleased); they are newer than the downloadable 0.2.3 builds.

[Source guide](docs/project.md) · [User documentation](docs/README.md) · [Contributing](CONTRIBUTING.md) · [Report a bug](https://github.com/zack005-design/Tally/issues/new/choose)

<p align="center">
  <a href="https://github.com/zack005-design/Tally/releases/tag/v0.2.3-20261024">Current release</a>
  &nbsp;·&nbsp;
  <a href="docs/screenshots/all-screens/README.md">Screen gallery</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/zack005-design/Tally/issues">Feedback and issues</a>
</p>
