# Changelog

User-facing changes in the published development releases. Downloadable APKs are prereleases, not production store builds.

## Unreleased

- Do one calculation inside an expense command: `coffee 40 + 25 cash`, `chai 3x15 cash`, `2 chai at 15 each cash`, `lunch 450 + 5% gst gpay` or `shoes 2000 - 10% cash`. Results are exact to the paisa and shown for checking; ranges, division and signed amounts are still refused. [Do the maths in a command](docs/features/on-device-ai.md#do-the-maths-in-a-command)
- Ask spending questions in expense entry, such as `how much did I spend on food this month?` or `total spent on chai last week`, answered from your own records without saving anything. [Ask about your spending](docs/features/on-device-ai.md#ask-about-your-spending)
- Calculator: add or remove GST, loan EMI with total interest, and sample variance and standard deviation. [Calculator](docs/features/calculator.md)
- Treat `upi`, `phonepe` and `paytm` as payment accounts when checking a command for conflicting accounts.
- Update the on-device command model to v5 (AI edition): more English and Hinglish ways of writing an expense, about 40 more everyday items, and `upi`, `phonepe` and `paytm` as payment accounts. No wrong drafts in any recorded check. [On-device suggestions](docs/features/on-device-ai.md) · [Model report](ml/tally-command/IMPROVED_V5.md)
- Suggest a category on the phone when the rules cannot tell, from a new 40,970-parameter model trained from scratch on about 460 everyday terms (AI edition). It stays silent when unsure and never picks Other. [On-device suggestions](docs/features/on-device-ai.md#category-suggestions)
- Fill a missing category and payment account from your own saved entries with the same description, when at least two recent entries agree. Accounts you type always win. [Learns from your own entries](docs/features/on-device-ai.md#learns-from-your-own-entries)
- Understand more dates and amounts in commands: `this morning`, `tonight`, `earlier today`, `two weeks ago`, `on 21 Oct` without a year, and Indian `₹500/-` prices. [English logging shortcuts](docs/features/on-device-ai.md#english-logging-shortcuts)

## [0.2.6](https://github.com/zack005-design/Tally/releases/tag/v0.2.6-20261124)

- Change the app tint from pine green to blue (#0A66D8) across every screen, and use the redesign's dark greys (#1C1C1E page, #2C2C2E cards) instead of black in the dark theme. Money in and switches stay green.
- Rebuild first launch: **Welcome to Tally**, **How Tally works**, your starting balances, and an optional step to turn on the daily reminder and app lock. You can also restore a backup straight from the welcome screen. Summary then opens on a **You're all set** card with a three-step setup list.
- Fit the narrowest phones: compact phones at their largest display size, and narrow split-screen windows, now scale far enough that no screen is laid out narrower than the 320 dp it is tested at.
- In landscape, keep content and the side rail clear of the camera cutout or punch-hole.
- Show the whole week in the date picker on narrow phones instead of hiding the weekend behind a sideways scroll.

[Verification and known limits](docs/releases/0.2.6.md)

## [0.2.5](https://github.com/zack005-design/Tally/releases/tag/v0.2.5-20261123)

- Add a dark theme. Tally follows your phone's dark theme setting by default; choose Light or Dark under **Settings → Appearance**. [Appearance guide](docs/features/appearance.md)
- Add a yearly summary under **Tools → History & reports**: total spending and income, a like-for-like comparison with last year, month-by-month spending and top categories. [Spending and planning](docs/features/spending-and-planning.md#yearly-summary)
- Add tags: label any transaction from its detail screen, filter Transactions by tag, and see spending per tag in the yearly summary. Tags never change money and are kept in encrypted backups; backups with tags need this version or later to restore. [Tag transactions](docs/features/everyday-money.md#tag-transactions)
- Document how to create a release key, build and verify a signed APK, and move a phone from the debug build. [Build a signed release](docs/release-signing.md)
- Search transactions by amount: type `80` or `₹1,250` in **Transactions** to find entries with that total or share. [Find a transaction](docs/features/everyday-money.md#find-a-transaction)
- Transaction reports (CSV and PDF) now include the accounts used and the tags on each transaction. [Reports and backups](docs/features/reports-and-backups.md#export-a-report)
- Adapt navigation to the screen: landscape and other short screens use a side rail so content keeps the full height, and with large text the add button stays on the tab row whenever it fits.
- Redesign the app in an iOS style: a system-grey background, white grouped lists, solid colour icon squares, contact-style avatars, green switches, the Inter typeface and pine green as the app tint, with a matching black dark theme.
- Replace the tab bar with a floating bar for **Summary**, **Activity**, **Spending** and **Split**, plus a round search button. Adding an expense, scanning a bill and transfers now start from the **Summary** card.
- Rebuild **Summary** with available-to-spend, today against your daily limit, the month's budget, what friends owe and recent entries, plus a short setup list on first launch.
- Fit every phone width: phones narrower than 360 dp, or set to a large display size, scale the whole layout down to fit instead of cutting content off, while your text-size setting still applies. Tablets and foldables keep a phone-width column.
- Bring the remaining screens into the new style: balance setup, budget progress bars, the spending trend chart, the calendar and calculator now use the pine-green tint and standard card and field shapes instead of the old blue and purple.

[Verification and known limits](docs/releases/0.2.5.md)

## [0.2.4](https://github.com/zack005-design/Tally/releases/tag/v0.2.4-20261115)

- Show the Friends owe you card in the same rounded format as other home amounts. The full device test suite now passes on Android 16, including real device-PIN unlock and relock.
- Share receipt PDF now produces a complete itemised receipt (merchant, date and time, category, paid-from account, items, taxes, total, payments and shares) with the original scan attached.
- Name the friend in repayment descriptions, move Reminders under Notifications in Settings, and avoid a crash when a refund's original expense disappears while editing.
- Add optional budget alerts: a local notification when monthly spending reaches 80% or 100% of a budget. Off by default; no new permissions or network access.
- Improve window-aware sheet sizing, Compose state handling, and large-text layout checks.
- Simplify expense widget shortcuts while preserving category icons, readable labels, and touch targets.
- Use AndroidX receipt metadata handling and support themed launcher icons across adaptive-icon devices.
- Update Android libraries and target Android 17; migrate the build to AGP 9's supported Kotlin and DSL configuration.
- Refresh Compose test APIs and Android test-runner compatibility, with Android 17 included in CI.

[Verification and known limits](docs/releases/0.2.4.md)

## [0.2.3](https://github.com/zack005-design/Tally/releases/tag/v0.2.3-20261024)

- Combined the completed finance, import, backup, and UI updates with expanded offline calculator tools.
- Added calculator statistics, ratios, percentage changes, tips, reverse tax, and interest arithmetic.
- Published source-matched AI and non-AI APKs, Tally Companion 0.2.0, and checksums.

[Verification and known limits](docs/releases/0.2.3.md)

## [0.2.2](https://github.com/zack005-design/Tally/releases/tag/v0.2.2-20261024)

- Added reviewed ChatGPT-history import with explicit preview and confirmation.
- Required password-protected authenticated encryption for new backups while retaining legacy imports.
- Added migration and restore safety checks, refined app-lock layout, and updated motion and press feedback.
- Packaged the completed safety/UI snapshot before the subsequent calculator expansion.

## [0.2.1](https://github.com/zack005-design/Tally/releases/tag/v0.2.1-20261022)

- Added separate AI and non-AI APKs with a shared app identity and signing certificate.
- Verified model inclusion/exclusion while retaining the calculator and receipt scanner in both editions.

## [0.2.0](https://github.com/zack005-design/Tally/releases/tag/v0.2.0-ai-20261022)

- Introduced bundled expense-command suggestions and an offline calculator.
- Kept suggestions editable, with explicit saving and existing rules-based handling for other transaction types.

## [0.1.0 visual update](https://github.com/zack005-design/Tally/releases/tag/v0.1.0-ios-polish-20261021)

- Added the Tally launcher mark, screen transitions, and compact/large-text layout refinements.
- Published the first Tally Companion and Tally Toolkit packages alongside the Android development APK.
