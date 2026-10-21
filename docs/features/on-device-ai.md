[← All guides](../README.md)

# On-device expense suggestions

The AI edition includes a small expense-command model trained from random weights. It identifies the description, amount, and account in a short English expense command and suggests an editable draft on your device.

## Use an expense command

1. Type a short personal expense with one INR amount and an account, such as `coffee 80 cash`.
2. Use the text-interpretation action to prepare the draft.
3. Review the description, amount, account, category, and date; correct any mistakes.
4. Explicitly save when the entry is correct.

Account hints must match an active account. Interpretation does not create transactions or change balances by itself.

## What runs locally

The model has **53,142 parameters** and a **roughly 210 KiB** weight bundle. It selects spans from your input; exact decimal code converts the selected amount to integer paise rather than generating a new number.

This is a specialised expense parser, not a conversational LLM. It does not answer questions about your ledger history. No cloud inference, login, API key, or pretrained model weights are required.

## Scope and fallback

The learned path handles sufficiently scored, single-INR personal expenses. Shared bills, repayments, transfers, and other entry types retain their rules-based handling. The lightweight preview also uses rules.

Unsupported or uncertain phrases fall back to rules or require manual correction. Negation, plans, questions, conflicting accounts, multiple amounts, and foreign currencies are checked before accepting personal-expense suggestions. These checks cannot establish understanding of every sentence.

If the bundle cannot load, rules remain available. The non-AI edition omits the learned model. Always review the draft in either edition.

## Recorded evaluation

In the 32-phrase controlled challenge, the model correctly accepted 14 of 16 supported commands and refused all 16 unsupported commands. Two supported phrases deferred. These assistant-authored checks are not a real-user accuracy guarantee, and the model score is not a calibrated probability.

[Model design](../../ml/tally-command/COMMAND_MODEL.md) · [Evaluation details](../../ml/tally-command/VERIFICATION_V3.md) · [Offline calculator](calculator.md)
