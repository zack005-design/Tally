# Tally icon, motion and layout verification

Date: 21 October 2026

## Changes

- Added a blue gradient launcher icon with a white tally mark, adaptive layers and an Android 13+ monochrome layer. Reused the mark in the Overview wordmark.
- Added short screen push/back transitions, tab color transitions, switch track animation, and an expanding Companion card. Compose animations use Android's animator duration scale.
- Fixed compact account rows by measuring the label and balance before deciding whether to stack them.
- Fixed navigation labels splitting at 320dp and 200% text size by giving tabs full-width rows when they cannot fit side by side.
- Added a Tally Companion card to Export report. It prepares Transactions/CSV options and explains an explicit file handoff to the user's custom plugin. Preparing the export does not change the ledger.
- Updated the existing shared-bill navigation test to use the pinned Back button rather than searching for it inside the scrollable receipt.

## Custom plugin scope

The authenticated personal plugin inventory returned `tally-toolkit` and `tally-companion`, both version 0.1.0. Their packages contain skills and references, rather than an Android executable or server. Tally Toolkit guided this work. The in-app integration is a manual CSV handoff to the plugins in Codex; it does not embed an AI runtime or upload data. Existing on-device storage and network permissions are unchanged.

## Evidence

Portable copies of the unit-test summary, complete device results, reduced-motion results, lint report and APK signature result are in [ios-polish-evidence](ios-polish-evidence/). Current preview images are in [screenshots/ios-polish](../screenshots/ios-polish/).

- JVM suite: 132 tests, zero failures. See `artifacts/ios-polish-verification.log` and `.build-cache/exact-reference/app/test-results/testDebugUnitTest/`.
- Android lint: zero errors, 32 warnings, seven hints. See `artifacts/ios-polish-build-lint-final.log` and `.build-cache/exact-reference/app/reports/lint-results-debug.txt`.
- Account-row regression first failed with the label squeezed to three lines, then passed after the measured layout change. See `artifacts/ios-polish-red.log` and `artifacts/ios-polish-guide-red.log`.
- Large-text navigation regression first failed because Overview occupied two lines. See `artifacts/ios-polish-nav-red.log`.
- Independent read-only code review found no actionable issues, including a second review of the stacked navigation layout.
- Final complete emulator run: **41 tests passed**, zero failures. See `artifacts/ios-polish-device-complete.log` (286.884 seconds).
- Reduced-motion check: **four tests passed** with the emulator animator duration scale set to zero, covering tab navigation, the expandable guide and persisted shared-bill navigation. The original setting was restored. See `artifacts/ios-polish-reduced-motion.log`.
- Normal-package debug APK assembly passed. See `artifacts/ios-polish-production-build.log`.
- APK signature verification passed (v2, one signer). Package identity: `com.smartexpense`, version 0.1.0 / 1, minimum API 26, target API 36. See `artifacts/ios-polish-apk-verification.log`.
- Installable APK: `dist/tally-ios-polish-debug.apk` (61,005,490 bytes).
- APK SHA-256: `7FAC2DA2C7C8D15065F93A3FA4AAD0F204E8E0B751EE4206896A605A1A3C9962`.
- Installed the exact delivered APK on the emulator and launched `com.smartexpense/.MainActivity` successfully. Visually checked its Overview screen in `artifacts/ios-polish-previews/production-launch.png`; the crash log was empty. An emulator System UI stall overlay was dismissed before this final visual check; it was not a Tally crash.

## Device test scope

The selected suites cover the new icon/Companion flow, Overview and setup, compact and double-text layouts, secondary screens, search/history, shared screen controls, real shared-bill persistence/navigation, and screen previews:

`IosPolishTest`, `LightUiPhaseOneTest`, `UiLayoutAuditTest`, `SharedScreenQualityTest`, `StudioNavigationTest`, `AllScreensPreviewTest`, `SecondaryLayoutAuditTest`, `TransactionHistoryPolishTest`.

Tests use an isolated `com.smartexpense.reference` installation and disposable in-memory databases. Screenshots are collected under `artifacts/ios-polish-previews/` and `artifacts/ios-polish-gallery/`.

## Limits

Verification uses the API 36 medium-phone emulator. Physical devices, API 26, real camera/biometric hardware and store signing were not tested in this task. This is a debug APK. The selected device suites do not represent every historical instrumentation test in the repository.

