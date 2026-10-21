[← Back to Tally](../../README.md#your-payment-and-your-share)

# Your payment and your share

The amount that leaves your account and the amount you personally spend can be different. Tally keeps payments, personal shares, and outstanding settlements distinct so a shared bill does not inflate your spending.

## One bill, three views

Imagine a ₹900 dinner shared equally by you and two friends. You pay the entire bill.

| View | Amount | Meaning |
| --- | --- | --- |
| Your payment | ₹900 | Money that leaves your selected account. |
| Your personal share | ₹300 | The part of dinner that counts as your spending. |
| Owed to you | ₹600 | The other two shares you paid on your friends' behalf. |

## What you paid

Payments describe the actual money movement. Paying ₹900 reduces your account by ₹900 even though only ₹300 belongs to your personal share.

For a bill with multiple payers, record each person's payment. Tally compares those payments with the assigned shares to determine the outstanding settlements.

## Your personal share

Your share describes your part of the bill. An equal split among three people assigns ₹300 of a ₹900 total to each participant. Other split methods let you assign exact amounts, percentages, weights, or individual items.

If a friend pays the entire bill instead, your ₹300 share still belongs to you, but your account has not paid it yet. You owe your friend until you record the repayment.

## What remains to be settled

In the original example, each friend owes you ₹300. Record repayments as they happen, including partial amounts.

| Event | Change in your account | Still owed to you | Your dinner spending |
| --- | --- | --- | --- |
| You pay the bill | −₹900 | ₹600 | ₹300 |
| One friend repays ₹300 | +₹300 | ₹300 | ₹300 |
| The second friend repays ₹100 | +₹100 | ₹200 | ₹300 |
| The remaining ₹200 is repaid | +₹200 | ₹0 | ₹300 |

The repayments return money you advanced. They are not new income and do not change your original personal share. After settlement, your net cash outflow is ₹300.

## Transfers and refunds

- **Transfer:** Moving ₹500 between your own GPay and Wallet accounts changes their balances without creating income or spending.
- **Personal refund:** Money returned for a purchase is recorded as a refund rather than a shared-bill repayment.
- **Repayment:** Money sent or received to settle a shared bill reduces an outstanding amount.

Tally calculates amounts in integer paise to avoid floating-point drift. Review the allocated shares and payment total before saving, particularly when a bill cannot divide evenly.

## Review it in the app

Check the bill's participants, shares, payments, and outstanding settlements before recording it. Use the repayment flow when someone settles an amount. Tally records these movements; it does not transfer money for you.

[View shared-bill review](../screenshots/readme/shared-bill.png) · [Shared-bill guide](shared-bills.md) · [Account guide](everyday-money.md)
