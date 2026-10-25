[← All guides](../README.md)

# On-device expense suggestions

The AI edition includes specialised expense-command models trained from random weights. They identify the description, amount, and account in short English and supported Latin-script Hinglish commands, producing editable drafts on your device. The expanded 1M capability described here is in the latest source/build; older release APKs retain their previously bundled model until updated.

## Use an expense command

1. Type a short personal expense with one INR amount and an account, such as `coffee 80 cash`.
2. Use the text-interpretation action to prepare the draft.
3. Review the description, amount, account, category, and date; correct any mistakes.
4. Explicitly save when the entry is correct.

Account hints must match an active account. Interpretation does not create transactions or change balances by itself.

New examples include `Paid 49 for coffee using gpy` and `maine chai ke liye cash se 30 diye`. Account spelling support is an explicit allowlist: `gpy`, `gpaay`, `gooogle pay`, `wallett`, and `walllet`. This does not correct arbitrary spellings. `kal`, negation and planned payments require manual clarification; unfamiliar Hinglish wording may also defer.

## What runs locally

The extension has **999,150 parameters** and a **3,998,956-byte** portable bundle (about 3.81 MiB). A **53,142-parameter** compatibility model keeps already-supported English drafts fast and stable. It is checked first; the extension handles its abstentions. Combined learned parameters: **1,052,292**. Both run locally and are omitted from the non-AI APK.

The models select spans from your input; exact decimal code converts the selected amount to integer paise rather than generating a new number. An uncertain new-language command leaves amount/account incomplete and asks for manual confirmation.

This is a specialised expense parser, not a conversational LLM. It does not answer questions about your ledger history. No cloud inference, login, API key, or pretrained model weights are required.

## Scope and fallback

The learned path handles sufficiently scored, single-INR personal expenses. Shared bills, repayments, transfers, and other entry types retain their rules-based handling. The lightweight preview also uses rules.

Unsupported or uncertain phrases fall back to rules or require manual correction. Negation, plans, questions, conflicting accounts, multiple amounts, and foreign currencies are checked before accepting personal-expense suggestions. These checks cannot establish understanding of every sentence.

If the bundle cannot load, rules remain available. The non-AI edition omits the learned model. Always review the draft in either edition.

## Recorded evaluation

The frozen combined system correctly accepted 2,447 of 2,451 supported fresh synthetic examples and all 1,563 supported legacy examples, with no wrong returned drafts in those sets. It handled 11/12 new capability examples (one deferred), while retaining 14/16 supported legacy challenge cases. These assistant-authored checks are not a real-user accuracy guarantee; older suites are regression evidence and the model score is not a calibrated probability.

[Current model and verification](../../ml/tally-command/IMPROVED_1M.md) · [Compatibility model](../../ml/tally-command/COMMAND_MODEL.md) · [Offline calculator](calculator.md)
