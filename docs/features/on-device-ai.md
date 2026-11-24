[← All guides](../README.md)

# On-device expense suggestions

The AI edition includes specialised models trained from random weights on your phone. Two of them identify the description, amount and account in short English and supported Latin-script Hinglish commands; a third suggests a category. They produce editable drafts on your device. The capabilities described here are in the latest source/build; older release APKs keep their previously bundled models until updated.

## Use an expense command

1. Type a short personal expense with one INR amount and an account, such as `coffee 80 cash`.
2. Use the text-interpretation action to prepare the draft.
3. Review the description, amount, account, category, and date; correct any mistakes.
4. Explicitly save when the entry is correct.

Amounts also accept Hindi (`४५.५०`), Arabic (`٤٥٫٥٠`) and full-width (`４５．５０`) decimal digits, including supported grouped amounts. Pasted Unicode spaces are standardized before interpretation. Hidden direction/control characters, superscript/fraction numerals and non-INR currency symbols require manual correction rather than a guessed amount. This input layer works in both editions; it does not add Hindi-script language understanding.

Account hints must match an active account. Interpretation does not create transactions or change balances by itself.

Examples include `Paid 49 for coffee using gpy`, `maine chai ke liye cash se 30 diye`, `momos cash 120`, `sabzi mein 85 gaye cash se` and `Paid 60 for chai using phonepe`.

Accounts are an explicit allowlist. `upi` uses your UPI (GPay) account. `phonepe`, `phone pe` and `paytm` fill the account only if you have an active account with exactly that name, such as **PhonePe**; otherwise choose the account yourself. Spellings `gpy`, `gpaay`, `gooogle pay`, `wallett` and `walllet` are also recognised. Other spellings are not corrected. `kal`, negation and planned payments require manual clarification; unfamiliar Hinglish wording may also defer.

## English logging shortcuts

Type a personal expense with an amount and payment account. For example:

| Input | Draft to review |
| --- | --- |
| `I paid 1.5k for groceries using cash yesterday` | Groceries, ₹1,500, Wallet, yesterday |
| `coffee 45 cash two days ago` | Coffee, ₹45, Wallet, two calendar days ago |
| `coffee 45 cash last Monday` | Coffee, ₹45, Wallet, the previous Monday |
| `coffee 45 cash on 21 October 2026` | Coffee, ₹45, Wallet, 21 October 2026 |
| `coffee 45 cash this morning` | Coffee, ₹45, Wallet, today |
| `coffee 45 cash two weeks ago` | Coffee, ₹45, Wallet, 14 days ago |
| `coffee 45 cash on 21 Oct` | Coffee, ₹45, Wallet, 21 October this year |
| `coffee ₹80/- cash` | Coffee, ₹80, Wallet |

Supported date phrases are `today`, `this morning`, `this afternoon`, `this evening`, `tonight`, `earlier today`, `yesterday`, `day before yesterday`, 1–365 `days ago` and 1–52 `weeks ago` (or `a` and the words one through seven), and `last` followed by a full weekday name. “Last Monday” means the strictly previous Monday, including when today is Monday. Explicit dates accept ISO `2026-10-21`, `on 21 October 2026`, `on 21 Oct 2026`, or `on October 21, 2026`. A day and month without a year, such as `on 21 Oct`, means this calendar year; if that date is still ahead, Tally asks you to correct it rather than guessing last year. Relative dates use your device's local calendar day.

The Indian price notation `/-` after an amount (`₹500/-`, `Rs. 1,250/-`) is read as exactly that amount. Amount shorthand accepts `k`/`thousand` for ×1,000 and `lakh`/`lac` for ×100,000, with at most two decimal places before the suffix: `1.25k` means ₹1,250. `INR1.5k`, `Rs1k`, and `1.5k rupees` are also supported. Arithmetic uses exact integer paise and rejects overflow; grouped/repeated multipliers such as `1,000 thousand` or `1k thousand` require correction. These conveniences work in both editions; learned sentence/description recognition remains an AI-edition feature.

Future or conflicting dates and recognised ambiguous phrases such as `last night`, `in two days`, or `on 10/11/2026` require manual date correction. Negation, foreign currencies and several separate amounts still require review; write one calculation instead, as described below. There is no scheduling or automatic saving, and these examples do not imply understanding of every English sentence.

## Do the maths in a command

In both editions you can write one calculation where the amount goes. Tally works it out exactly, puts the result in the draft and shows the sum so you can check it.

| Input | Draft amount |
| --- | --- |
| `coffee 40 + 25 cash` | ₹65 |
| `chai 3x15 cash` or `chai 3 x 15 cash` | ₹45 |
| `2 chai at 15 each cash` | ₹30, description chai |
| `lunch 450 + 5% gst gpay` | ₹472.50 |
| `shoes 2000 - 10% cash` | ₹1,800 |

You can add and multiply up to 10 positive amounts, or add or take off one percentage from 0.01% to 100% (optionally followed by `gst`, `tax`, `vat`, `tip`, `service charge`, `discount` or `off`). A result that falls between paise is rounded to the nearest paisa, and the note says so. Use one calculation per expense. Ranges such as `200-250`, division, signed amounts and two separate calculations are never guessed; Tally asks you to enter the amount instead.

## Ask about your spending

Type a spending question in the same box and Tally answers from your own records instead of preparing a draft:

- `how much did I spend on food this month?` (a category)
- `total spent on chai last week` (words in the description; plurals match)
- `how much did I spend yesterday`, `… in October`, `… in the last 7 days`, `… this year`
- `how much did I earn this month`

Totals use your own share of shared bills and subtract refunds, like the reports. Periods are today, yesterday, this or last week (Monday to Sunday), this or last month, this or last year, the last 1–366 days, or a month name (the most recent one that has started). The answer is read-only: nothing is saved or changed. Other questions, such as account balances, are not answered here.

## Category suggestions

In the AI edition, when the rules cannot tell the category, a second small model suggests one from the description, for example `netflix` → Subscriptions, `petrol` → Transport or `pani puri` → Food. It knows about 460 everyday terms and recognises them with extra words, plurals and small typos (`extra masala dosa`, `petrol for home`). It suggests only Tally's built-in categories (never Other) and stays silent when it is unsure, including for words it has not learned. A suggested category is marked in the draft so you can change it before saving.

## Learns from your own entries

In both editions, Tally also looks at your own saved personal expenses with the same description. When at least two of your recent entries agree, for example `chai` always filed under Food and paid from Wallet, the draft uses that category, and that account if you did not name one. Accounts you type always win, and mixed or one-off habits produce no guess. This is a lookup on your phone, not a trained model: nothing leaves the device and nothing is saved until you confirm.

## What runs locally

The command extension has **1,002,068 parameters** and a **4,011,202-byte** portable bundle (about 3.83 MiB). A **53,142-parameter** compatibility model keeps already-supported English drafts fast and stable. It is checked first; the extension handles its abstentions. The category suggester has **40,970 parameters** (164,022 bytes). All three run locally and are omitted from the non-AI APK.

The models select spans from your input; exact decimal code converts the selected amount to integer paise rather than generating a new number. An uncertain new-language command leaves amount/account incomplete and asks for manual confirmation.

This is a specialised expense parser, not a conversational LLM. Spending questions are answered by exact code over your records, not by a model. No cloud inference, login, API key, or pretrained model weights are required.

## Scope and fallback

The learned path handles sufficiently scored, single-INR personal expenses. Shared bills, repayments, transfers, and other entry types retain their rules-based handling. The lightweight preview also uses rules.

Unsupported or uncertain phrases fall back to rules or require manual correction. Negation, plans, questions, conflicting accounts, multiple amounts, and foreign currencies are checked before accepting personal-expense suggestions. These checks cannot establish understanding of every sentence.

If the bundle cannot load, rules remain available. The non-AI edition omits the learned model. Always review the draft in either edition.

## Recorded evaluation

The combined command system accepted 2,105 of 2,384 new-phrasing examples, all 1,547 legacy examples and 11 of 12 cases in a frozen new challenge, with no wrong returned drafts and no unsupported command accepted in any set. Compared with the previous version, that is 1,575 more correct drafts on the new phrasing and no loss elsewhere. The category suggester accepted 266 held-out variants of known terms with no wrong category and ignored every unknown test word; for words it has never seen, its guesses are unreliable, so it usually stays silent. These assistant-authored checks are not a real-user accuracy guarantee, and model scores are not calibrated probabilities.

[Current models and verification](../../ml/tally-command/IMPROVED_V5.md) · [Previous extension](../../ml/tally-command/IMPROVED_1M.md) · [Compatibility model](../../ml/tally-command/COMMAND_MODEL.md) · [Offline calculator](calculator.md)
