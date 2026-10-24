[← Tally guides](README.md)

# Android phone compatibility

Tally supports Android 8.0/API26 and later. Screens use Android's logical display size and system font settings. Display resolution alone does not determine how much content fits. The app keeps its ledger and expense-command inference offline and requires review before saving a transaction.

## Xiaomi 17T

[Xiaomi's India specifications](https://www.mi.com/in/product/xiaomi-17t/specs/) list Android 16/HyperOS 3, a 1268 × 2756 display and up to 120 Hz. Keep Android's display-size and text-size settings at the sizes you prefer. Tally should adapt rather than require smaller text or a particular screen resolution. It does not force a refresh rate, keep a background service alive, or require unrestricted battery access to maintain balances.

Allow notifications if you want recurring-entry or backup reminders. Android and manufacturer battery restrictions can delay background notifications. Opening Tally reconciles reminder schedules; recurring templates still require explicit review and saving. A delayed notification does not create or delete a transaction. If reminders are late, check Tally's notification permission/channel and the phone's per-app battery/background settings. Menu names vary by HyperOS version; change those settings only if you need more prompt reminders.

Biometric unlock uses Android's available authentication methods. Receipt scanning uses the local file picker and bundled OCR; check the recognized amount and account. Hardware authentication, the Xiaomi camera/file-provider path and HyperOS background behavior require a real-phone check.

## Verification boundaries

The compatibility workflow targets disposable emulators on API26, API35 and API36 for migration, backup, reminders, model, distribution and layout checks. A configured CI job is not a passing result. Local device runs and their actual results are recorded in the [task ledger](unfinished-work.md).

Layout checks cover compact widths and large system text. An Android emulator can exercise Android16 and alternative display sizes but does not reproduce Xiaomi firmware, thermal limits or the physical display's refresh behavior. Smooth 120 Hz rendering, battery consumption and camera/biometric reliability remain unmeasured until tested on hardware. No universal every-phone performance guarantee is implied.

## Moving or updating phones

Use the [private development downloads](downloads.md). Keep an encrypted full backup and its password before moving phones. Updates must preserve the installed signing identity; do not uninstall or clear storage to resolve an update failure. See [installation safety](installation-safety.md).
