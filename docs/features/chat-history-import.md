# Import spending from a ChatGPT chat

Use **Tally Companion 0.2.0 or newer** with the updated Android app. This is a file transfer: the plugin prepares records and Tally imports them after your review. The plugin does not connect directly to your phone or retrieve other chats automatically.

1. Open the chat holding your spending history and select Tally Companion.
2. Ask: **“Prepare my spending history for Tally. Preserve corrections and legitimate repeat purchases, and flag missing details.”**
3. Review the coverage and unresolved counts. Download the generated `tally-history.json`. If older messages are unavailable, provide those records before requesting a complete export.
4. In Tally, open **Tools → History & reports → Import from ChatGPT**, then choose the file.
5. Map source account/category labels to existing active accounts/categories. Choose Uncategorised explicitly if appropriate. Correct missing source labels in the file by asking the plugin to regenerate it using the same IDs.
6. Inspect every entry and possible duplicate. Select the supported records, then tap **Preview balance effects**. Review the actual account balances and excluded count. Nothing is saved by this preview.
7. Acknowledge the review and tap **Confirm import**. The result reports added, already recorded and excluded entries. If accounts/history changed, preview again first.

Confirmed individual INR expenses and income are supported. Shared bills, refunds, repayments, transfers, adjustments and unclear entries remain visible but cannot be imported through this screen. Resolve them using Tally's existing workflows; they are not silently converted or omitted from the prepared file.

Importing old spending changes balances and can trigger historical GPay reset recalculation. It does not infer opening funds or restore a previous balance. Existing records are preserved. Reopening the same file verifies saved details and skips matching source IDs; changed content under the same ID is blocked. Newly assigned IDs can overlap existing records, so keep the original file and review possible duplicates. Records reversed or corrected in Tally are not silently resurrected.

Files are UTF-8 JSON, at most 5 MiB and 10,000 entries. See [format details](chat-history-format.md). Date-only entries use local noon. Keep the file on your device; Tally does not upload it. If the app process closes before saving, select the file and review again. A cancelled file picker preserves the current review.
