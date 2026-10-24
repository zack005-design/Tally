# FIn unfinished work

Updated 24 October 2026. This is the current task ledger; the untracked October 10 implementation checklist is historical and contains superseded statements.

## Execution scope

Continue the existing offline Kotlin/Room/Compose app. Preserve exact integer-paise accounting, explicit draft confirmation, encrypted backups, existing signing identity, private distribution and other chats' files. The user requested starting every unfinished task, then prioritized broad phone compatibility, especially Xiaomi 17T. They have no physical test phone, production signing key or Play account available.

## Tasks

- [x] Reduce reminder reconciliation overhead. Disabled/denied reminders avoid ledger reads; enabled reminders load scheduling inputs without rebuilding balances, debts or reports. Query-tracing regressions failed before the fix and pass afterward, including reversed occurrence IDs and archived accounts.
- [ ] Extend compatibility verification to Android 8/API26 as well as modern Android. Cover compact/large-text layouts, navigation, restore, model execution and reminders with isolated test data.
- [ ] Check Xiaomi 17T-relevant behavior: Android 16, tall high-density display, gestures/keyboard, large text, interrupted/background work and offline inference. An emulator cannot prove HyperOS background delivery, camera behavior, biometric hardware or 120 Hz frame performance.
- [ ] Implement dedicated shared-bill refund distribution. Started by tracing repository, debt, UI and backup contracts and writing a local design. Exact participant shares, actual cash recipients, partial/repeated refunds, existing repayments, reversal, idempotency and backup validation must ship together. Existing personal refunds continue rejecting shared bills until that complete path is verified.
- [x] Resume a bounded separate local LLM research run. All 15 research tests passed; a fresh seed-43 run completed 1,200 steps on synthetic data. It matched 11/32 held-out synthetic commands and 7/8 historical commands. This is insufficient for app integration. The app model remains unchanged; the research workspace and all artifacts remain ignored. Broader independent evaluation is still future work.
- [ ] Check the latest GitHub Actions run and resolve reproducible failures. Publish completed verified source changes individually on main.
- [x] Review the two pre-existing untracked files, `IMPLEMENTATION.md` and `ui-redesign.html`. Their local references resolve, but their no-OCR/no-model and mint/graphite design descriptions are historical. Preserve the originals and keep them outside this publication; the release notes and current guides describe the shipped app.
- [ ] Physical-phone testing: unavailable until a device is supplied.
- [ ] Production signing and store distribution: unavailable without the user's signing identity and store account. Continue private development distribution; do not create a replacement signing identity or claim store readiness.

## Existing completed work

Private development release 0.2.3 includes history import, encrypted-backup safeguards, app-lock/motion changes, receipt OCR, a specialized local expense-command model and expanded deterministic calculator tools. Release evidence records 173 JVM tests per edition, zero lint errors and two calculator emulator checks. Those historical results are not fresh verification of subsequent changes.

## Current source verification

The reminder update passed the shared checkout's 183 JVM tests, debug assembly, Android lint (zero errors; existing warnings), and 13 targeted API36 instrumentation tests for reminder efficiency/delivery, exports, populated migrations, restore safety, model execution and distribution assets. Tests used the disposable `com.smartexpense.safety` identity and synthetic data. Independent review found no material issue. The 0.2.3 download assets have not been replaced by these source changes.

## Device reference

[Xiaomi's India specifications](https://www.mi.com/in/product/xiaomi-17t/specs/) list Android 16/HyperOS 3, 1268 × 2756 pixels and up to 120 Hz. Android display size and font settings determine logical layout width; do not hard-code this physical resolution as a dp size or force a refresh rate.
