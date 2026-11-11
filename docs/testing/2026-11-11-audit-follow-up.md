# Audit follow-up verification — 11 November 2026

These changes apply to current source builds. The published 0.2.3 APKs have not been replaced, and this update does not create a release.

## Behavior

- Receipt recognition rejects text longer than 10,000 characters with an actionable message. It does not return a successful scan with a silently truncated tail. Existing file, storage, schema, and backup limits remain unchanged.
- Original receipts have a full-screen zoom/pan preview with zoom/reset controls, accessible movement actions, and PDF page navigation. The preview retains the surrounding font scale.
- Receipt replacement retains the current bytes, text, item corrections, and category if selection is canceled or reading fails. Successful replacement updates bytes/text/duplicate status together, clears old corrections, and closes old text/item/category editors. Editing is disabled while scanning.
- Parsed receipt suggestions are reused while the text is unchanged. Recognition quality warnings appear once and follow the draft.
- Merchant detection skips common registration, invoice, and contact headers and stops before purchases/summary rows rather than selecting a footer as the merchant.
- Diary, local settings, and planning changes update independently of transaction hydration. Financial-table changes still load balances, debts, reports, and full transaction history in one database transaction. Route entry retains refresh behavior for date-relative reports and monthly review. This is not transaction pagination or a claim of measured speedup on large personal histories.
- Shared refunds require an explicit cash recipient or per-person received amounts. Matching received amounts to refunded shares is an explicit action. A changed total invalidates an old multi-person cash distribution until it is corrected and reviewed. Saving remains a separate action.

## Checks

- Full JVM suite: 231 tests, zero failures.
- Debug app and Android test APK assembly: passed.
- Android lint: zero issues.
- Final focused Android regression run: 44 tests passed, zero failures (131 seconds).
- Documentation links: checked locally before publication.

The device checks use the existing `medium_phone` Android 16 / API 36 emulator and a separate `com.smartexpense.auditcheck` application identity. Fixtures are synthetic, databases are disposable, and the installed Tally application is not replaced.

New coverage includes over-limit embedded PDF text, receipt replacement success/failure and stale editor state, zoom/reset controls, canceled replacement, diary refresh without transaction hydration, financial reversal refresh, monthly-review route entry, and explicit/multiple refund recipients. UI cases include narrow 320dp layouts and large text; captured zoom previews are inspected locally and are not uploaded as source artifacts.

Existing coverage exercises receipt OCR (including faint/tilted and long photos), receipt editing/restoration, receipt persistence and backup, receipt PDF sharing, bill/draft switching, shared-refund accounting and backup, everyday-money accounting, report export, and backup rejection/restore.

## Limits

Real camera/picker applications, manufacturer-specific behavior, TalkBack interaction on hardware, API 26 devices, landscape, and production signing were not verified. Gesture math and accessibility movement actions received source review; automated preview interactions exercise the zoom/reset buttons. The focused device set is not the entire application's instrumentation suite.
