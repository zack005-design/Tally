# Changelog

User-facing changes in the published development releases. Downloadable APKs are prereleases, not production store builds.

## Unreleased

Nothing yet.

## [0.2.7](https://github.com/zack005-design/Tally/releases)

- Publish one APK, the AI edition, instead of separate AI and non-AI downloads. It includes everything the non-AI edition had, and falls back to the same rules if a model cannot load. It installs over either earlier edition and keeps your records.
- New app logo: a **T** whose stem turns into a tick, on the blue tile. Choose from ten home-screen icons in **Settings → Appearance → App icon**, including the original tally marks as **Classic**. Each has a themed-icon version for Android 13 and later. [Choose an app icon](docs/features/appearance.md#choose-an-app-icon)
- Animate closing: sheets fade out, the bottom bar slides back in, and pages slide over each other iOS-style, including while you swipe back.
- Keep the Activity filter button on screen with large text, and add a Search key to search fields.
- Read a bill total that text recognition split with a space, when the items add up to it.
- Add **Ask Tally**, a 4.9M-parameter reasoning model trained from scratch that runs on the phone (AI edition, about 5 MB). It plans the steps for questions like `how much did I spend on food this month?`, `what was my biggest expense this week`, `average daily spending last month` or `what if I cut food by 20%`, follows up on `what about last month`, and Tally's exact code carries them out and shows how it worked it out. It never invents numbers and declines when unsure; comparisons, shares and savings plans are often declined for now. [Ask Tally](docs/features/on-device-ai.md#ask-tally-tiny-reasoning-model) · [Model report](ml/tally-assistant/README.md)
- Train a personal category model on the phone from your own entries, including categories you created, so variants like `evening chai` follow your usual choice. It stays in memory, needs a few entries per category and stays silent when unsure. [Learns from your own entries](docs/features/on-device-ai.md#learns-from-your-own-entries)
- Search transactions in plain words: `coffee over 100 last month`, `food in october by gpay`, `refunds under 500`. Tally shows what it understood. [Find a transaction](docs/features/everyday-money.md#find-a-transaction)
- Add **Smart insights** under Tools → Money & planning: a month-end spending forecast, alerts for unusually high categories and large entries, detected repeating payments with next dates and monthly cost, one-tap budget suggestions from your history, and duplicate warnings, all worked out on the phone. [Smart insights](docs/features/spending-and-planning.md#smart-insights)
- Add a **Net worth** screen under Tools → Money & planning: account balances plus what friends owe you, minus what you owe, with each account and a six-month month-end trend. [Net worth](docs/features/spending-and-planning.md#net-worth)
- Enter several expenses in one line, such as `chai 20 cash, samosa 30 cash; bus 15 upi`. Each becomes its own draft (Expense 1 of 3, …) that you review and save in turn. [Several expenses at once](docs/features/on-device-ai.md#several-expenses-at-once)
- Convert a foreign amount in a command with your own rate: `lunch 12 usd at 83.5 cash` becomes ₹1,002 with the original amount and rate in the notes. [Foreign currency in a command](docs/features/on-device-ai.md#foreign-currency-in-a-command)
- Do one calculation inside an expense command: `coffee 40 + 25 cash`, `chai 3x15 cash`, `2 chai at 15 each cash`, `lunch 450 + 5% gst gpay` or `shoes 2000 - 10% cash`. Results are exact to the paisa and shown for checking; ranges, division and signed amounts are still refused. [Do the maths in a command](docs/features/on-device-ai.md#do-the-maths-in-a-command)
- Ask spending questions in expense entry, such as `how much did I spend on food this month?` or `total spent on chai last week`, answered from your own records without saving anything. [Ask about your spending](docs/features/on-device-ai.md#ask-about-your-spending)
- Calculator: add or remove GST, loan EMI with total interest, and sample variance and standard deviation. [Calculator](docs/features/calculator.md)
- Treat `upi`, `phonepe` and `paytm` as payment accounts when checking a command for conflicting accounts.
- Update the on-device command model to v5 (AI edition): more English and Hinglish ways of writing an expense, about 40 more everyday items, and `upi`, `phonepe` and `paytm` as payment accounts. No wrong drafts in any recorded check. [On-device suggestions](docs/features/on-device-ai.md) · [Model report](ml/tally-command/IMPROVED_V5.md)
- Suggest a category on the phone when the rules cannot tell, from a new 40,970-parameter model trained from scratch on about 460 everyday terms (AI edition). It stays silent when unsure and never picks Other. [On-device suggestions](docs/features/on-device-ai.md#category-suggestions)
- Fill a missing category and payment account from your own saved entries with the same description, when at least two recent entries agree. Accounts you type always win. [Learns from your own entries](docs/features/on-device-ai.md#learns-from-your-own-entries)
- Understand more dates and amounts in commands: `this morning`, `tonight`, `earlier today`, `two weeks ago`, `on 21 Oct` without a year, and Indian `₹500/-` prices. [English logging shortcuts](docs/features/on-device-ai.md#english-logging-shortcuts)
- Read standard 12 MP phone photos at full resolution. They were halved before text recognition, which could leave receipt print too small to recognise and produce wrong items and amounts.
- Accept bill photos in any format the phone can open, including HEIC/HEIF, AVIF, GIF and BMP, not only JPEG, PNG and WebP.
- Enlarge small images, such as photos forwarded through messaging apps, before reading them, and read receipts photographed sideways or upside down without rotation information.
- Sharpen and re-read photos with mild blur from hand shake or soft focus. Tally says when it used the sharpened read.
- Accept bill photos larger than 6 MB. Photos larger than 6 MB were rejected before any text was read; photos up to 40 MB now work and are saved as a compact JPEG copy.
- Pick up bill amounts written as `50/-` or `50/=`, or followed by a tax-code letter or `*` (such as `56.00 A`). These rows were previously skipped, leaving the item list and total empty.
- Begin the UI redesign: refreshed colour, shape and dark-mode tokens, a reworked **Summary**, updated **Split**, **Budgets** and **Accounts** screens, and a one-page keypad for adding a personal expense.
- Tidy **Tools**: each group page is now split into short, labelled sections (for example Record, Accounts and Plan under **Money & planning**) with its own icon on every row, instead of one long list that repeated the same icon. Favourites and the home-screen widget share one entry instead of three.
- In **Spending**, each category's icon now matches its colour in the distribution bar, so the list works as the chart's legend. Previously the icons cycled through three unrelated colours.
- Tapping your avatar on **Summary** now opens **Settings** instead of the long Tools list. Settings groups your profile, appearance, app lock, reminders, accounts, categories, merchant rules, favourites, calculator, backup, export and chat import. [Appearance guide](docs/features/appearance.md)
- **Spending** now lists View transactions, Budgets, Smart insights, Savings goals, Recurring, Money planner, Calendar & diary, Monthly review and Year in review in one grouped list, with Spending details to show or hide the monthly breakdown, replacing the stacked buttons. Set budgets from **Budgets → Edit budgets**. [Spending and planning](docs/features/spending-and-planning.md)

[Verification and known limits](docs/releases/0.2.7.md)

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
