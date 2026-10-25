[← Back to features](../../README.md#features)

# Shared bills and repayments

Separate the money you paid from your own share of a group expense. Track multiple payers, each person's share, and the repayments that settle the bill.

<p align="center"><a href="../screenshots/readme/shared-bill.png"><img src="../screenshots/readme/shared-bill.png" width="300" alt="Shared bill payment and settlement review"></a></p>

## Choose a split that fits

| Method | Use it for |
| --- | --- |
| Equal shares | Everyone shares the cost equally. |
| Exact amounts | Each person owes a specific amount. |
| Percentages | Divide the total by agreed percentages. |
| Weights | Give participants different relative shares. |
| Items | Allocate individual bill items to participants. |

## Record and settle a bill

1. Enter the bill and add the participants.
2. Choose a split method and review each person's share.
3. Record who paid, including multiple payers when needed.
4. Review the outstanding settlements before saving.
5. Record repayments as they happen, including partial repayments.

## Example: dinner with friends

You pay ₹900 for three equal shares. Your account decreases by ₹900, your personal spending is ₹300, and friends owe you ₹600. Receiving that ₹600 settles the amount owed and restores your balance without counting it as new income.

For a step-by-step accounting example, read [Your payment and your share](payment-and-share.md), including partial repayments and the case where a friend pays first.

Tally records the bill and repayments; it does not send money between participants.

## Refund a shared bill

In app builds with **Shared refund**, open the original bill and choose **Record refund**. Enter the total returned by the merchant, review each person's refunded share, and record who actually received the cash. Suggested shares follow each person's remaining refundable share; edit them when only specific items were refunded. Both the refunded shares and cash received must add up to the refund total.

Choose a destination account for any cash you received, then use **Review refund** before **Save refund**. Only your refunded share reduces your spending. Only cash actually received by you increases your account. A refund received by a friend does not add cash to your account.

For a ₹900 bill split equally three ways, a ₹300 refund received entirely by you reduces your spending by ₹100 and increases your account by ₹300. Each friend is entitled to ₹100 of the refund. The original bill and any repayments remain in history; the refund records the balances owed back to participants. Review the net settlement suggestions, or explicitly offset opposite balances using the settlement tools, rather than recording cash that never moved.

Partial and repeated refunds cannot exceed a person's original share. To correct a shared refund, reverse it and record a new distribution. Reverse any repayments against that refund first. Active linked refunds must be reversed before correcting or reversing the original bill.

Full backups containing shared refunds use format 8 and need a build that supports shared refunds when restoring. Earlier personal-refund backups remain supported. The 0.2.3 download predates this feature.

[View bill review](../screenshots/readme/shared-bill.png) · [Everyday money →](everyday-money.md)
