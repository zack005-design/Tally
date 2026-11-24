# Changelog

User-facing changes in the published development releases. Downloadable APKs are prereleases, not production store builds.

## Unreleased

- Change the app tint from pine green to blue (#0A66D8) across every screen, and use the redesign's dark greys (#1C1C1E page, #2C2C2E cards) instead of black in the dark theme. Money in and switches stay green.
- Rebuild first launch: **Welcome to Tally**, **How Tally works**, your starting balances, and an optional step to turn on the daily reminder and app lock. You can also restore a backup straight from the welcome screen. Summary then opens on a **You're all set** card with a three-step setup list.
- Fit the narrowest phones: compact phones at their largest display size, and narrow split-screen windows, now scale far enough that no screen is laid out narrower than the 320 dp it is tested at.
- In landscape, keep content and the side rail clear of the camera cutout or punch-hole.
- Show the whole week in the date picker on narrow phones instead of hiding the weekend behind a sideways scroll.
- In **Spending**, each category's icon now matches its colour in the distribution bar, so the list works as the chart's legend. Previously the icons cycled through three unrelated colours.

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
