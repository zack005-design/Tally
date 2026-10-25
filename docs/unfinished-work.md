# FIn unfinished work

Updated 25 October 2026. This is the current task ledger; the untracked October 10 implementation checklist is historical and contains superseded statements.

## Execution scope

Continue the existing offline Kotlin/Room/Compose app. Preserve exact integer-paise accounting, explicit draft confirmation, encrypted backups, existing signing identity, private distribution and other chats' files. The user requested starting every unfinished task, then prioritized broad phone compatibility, especially Xiaomi 17T. They have no physical test phone, production signing key or Play account available.

## Tasks

- [x] Reduce reminder reconciliation overhead. Disabled/denied reminders avoid ledger reads; enabled reminders load scheduling inputs without rebuilding balances, debts or reports. Query-tracing regressions failed before the fix and pass afterward, including reversed occurrence IDs and archived accounts.
- [ ] Complete remote compatibility verification on Android 8/API26, API35 and API36. The expanded CI matrix now runs migration, restore, reminder, model, distribution and layout checks with per-API reports. All 37 targeted device/layout checks passed locally on API36; API26/API35 results remain pending CI.
- [ ] Check Xiaomi 17T-relevant behavior: Android 16, tall high-density display, gestures/keyboard, large text, interrupted/background work and offline inference. An emulator cannot prove HyperOS background delivery, camera behavior, biometric hardware or 120 Hz frame performance.
- [x] Implement and verify dedicated shared-bill refund distribution. The flow separates refunded personal shares from actual cash recipients, caps partial/repeated refunds per person, preserves original bills and repayments, supports idempotent retries and dependency-aware reversal, and validates format-8 backups. The explicit review/save form survives state restoration and remains usable at 320dp/200% text. Source verification passed; private APK release packaging is separate.
- [x] Resume a bounded separate local LLM research run. All 15 research tests passed; a fresh seed-43 run completed 1,200 steps on synthetic data. It matched 11/32 held-out synthetic commands and 7/8 historical commands. This is insufficient for app integration. That research run did not change the app model; its workspace and artifacts remain ignored. Later model updates are described in the [on-device AI guide](features/on-device-ai.md). Broader independent evaluation is still future work.
- [ ] Check the latest GitHub Actions run and resolve reproducible failures. Publish completed verified source changes individually on main.
- [x] Review the two pre-existing untracked files, `IMPLEMENTATION.md` and `ui-redesign.html`. Their local references resolve, but their no-OCR/no-model and mint/graphite design descriptions are historical. Preserve the originals and keep them outside this publication; the release notes and current guides describe the shipped app.
- [ ] Physical-phone testing: unavailable until a device is supplied.
- [ ] Production signing and store distribution: unavailable without the user's signing identity and store account. Continue private development distribution; do not create a replacement signing identity or claim store readiness.

## Existing completed work

Private development release 0.2.3 includes history import, encrypted-backup safeguards, app-lock/motion changes, receipt OCR, a specialized local expense-command model and expanded deterministic calculator tools. Release evidence records 173 JVM tests per edition, zero lint errors and two calculator emulator checks. Those historical results are not fresh verification of subsequent changes.

## Current source verification

The reminder update passed the shared checkout's 183 JVM tests, debug assembly, Android lint (zero errors; existing warnings), and 13 targeted API36 instrumentation tests for reminder efficiency/delivery, exports, populated migrations, restore safety, model execution and distribution assets. Tests used the disposable `com.smartexpense.safety` identity and synthetic data. Independent review found no material issue. The 0.2.3 download assets have not been replaced by these source changes.

All 24 layout scenarios also passed on API36: widths 320/360/393/412/440/600dp, each at font scales 1.0/1.3/1.5/2.0. Checks exercise navigation, balances, entry review/save actions, shared-bill review and history filters. Wider scenarios now fit their full logical width into the test display; stale navigation/filter selectors were updated to the current app. These are logical layout checks, not a physical Xiaomi 17T benchmark.

The published 0.2.3 AI APK passed `zipalign -c -P 16 4`; its ARM64 and x86_64 native libraries have at least 16 KB load alignment. This is structural packaging evidence, not proof of firmware behavior or 120 Hz frame performance.

## Device reference

[Xiaomi's India specifications](https://www.mi.com/in/product/xiaomi-17t/specs/) list Android 16/HyperOS 3, 1268 × 2756 pixels and up to 120 Hz. Android display size and font settings determine logical layout width; do not hard-code this physical resolution as a dp size or force a refresh rate.

## Shared-refund source verification — 25 October

An isolated archive of commit `45603df` plus only the refund-owned changes passed 190 JVM tests, final app/test APK assembly and Android lint with zero errors. The final APK passed 21 API36 instrumentation cases covering shared-refund saving/retries/caps, dependent reversal, encrypted-backup round trips and malformed-backup rejection, migration/correction/repository regressions, restored form state and cash-recipient labels at 320dp/200% and 412dp/100%. Both detail-label regressions failed on the prior implementation and passed after the correction. Independent read-only review found no material issue.

Tests used only synthetic data, a separate application ID (`com.smartexpense.refund`) and an owned read-only emulator. Other chats' uncommitted source/assets were excluded. Shared refunds reuse Room schema 5; backups containing their participant metadata use format 8, while older formats 1–7 remain supported. [Shared-bill instructions](features/shared-bills.md) describe cash versus personal benefit, explicit offsets and reversal-based corrections. Existing release APKs were not replaced by this source update. API26/API35 and final combined-main CI remain subject to actual remote results; the earlier compatibility run was cancelled by a later push.
