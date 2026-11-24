[← Back to source guide](project.md)

# Build a signed release

Downloadable Tally APKs are currently debug-signed test builds. This guide sets up your own release key and builds a signed APK on your computer. The key never leaves your computer and is never uploaded to GitHub.

Commands are for PowerShell on Windows, run from the project folder.

## Before you start

Every future update must be signed with the same key. **If you lose the key file or its passwords, you can never publish an update that installs over your existing app.** Keep at least two copies, for example one in a password manager and one on a USB drive kept somewhere safe.

You need Android Studio (it includes `keytool`) and the Android SDK.

## 1. Create the key once

```powershell
New-Item -ItemType Directory -Force "$HOME\tally-signing" | Out-Null
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore "$HOME\tally-signing\tally-release.jks" -alias tally `
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` asks for a keystore password and your name details. Use a long, unique password and store it with the key file. Keep the folder outside the project; the repository also ignores `*.jks` files.

## 2. Record the certificate fingerprint

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore "$HOME\tally-signing\tally-release.jks" -alias tally
```

Copy the `SHA256:` line and keep it with your key. The verification step checks every APK against it.

## 3. Build the signed APK

Set the signing values for the current PowerShell window only. The passwords are typed in hidden fields and are not saved in your command history.

```powershell
$env:TALLY_KEYSTORE_FILE = "$HOME\tally-signing\tally-release.jks"
$env:TALLY_KEY_ALIAS = "tally"
$p = Read-Host "Keystore password" -AsSecureString
$env:TALLY_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new("", $p).Password
$p = Read-Host "Key password (press Enter if it is the same)" -AsSecureString
$key = [System.Net.NetworkCredential]::new("", $p).Password
$env:TALLY_KEY_PASSWORD = if ($key) { $key } else { $env:TALLY_KEYSTORE_PASSWORD }

.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

The APK is written to `app\build\outputs\apk\release\app-release.apk`. Build the non-AI edition by adding `-PtallyAi=false`; copy the first APK somewhere else before building the second, because both use the same output path.

If any signing value is missing, the build stops. It never falls back to the debug key.

## 4. Verify the APK

```powershell
.\tools\verify-apk.ps1 -Apk app\build\outputs\apk\release\app-release.apk `
  -ExpectedCertificateSha256 <SHA256 from step 2> -ExpectedVersionCode <versionCode in app\build.gradle.kts>
```

The script checks the signature and certificate, the package name and version, that the build is not debuggable, that there is no internet permission, and that Android cloud backup is off. It writes a `.sha256` file next to the APK only when every check passes.

Close the PowerShell window when you are done so the passwords are cleared.

## 5. Move your phone to the signed build (once)

Android only installs an update signed with the same key as the installed app. Your phone currently has a debug-signed build, so the first signed build cannot install over it. Move across once:

1. In Tally, open **Backup & restore** and export an encrypted backup. Keep the backup file and its password.
2. Copy the backup off the phone as well, so you have a second copy.
3. Uninstall the debug build. **This deletes the records stored in the app**, so do not skip steps 1 and 2.
4. Install the signed APK.
5. Open **Backup & restore**, restore your backup, and check your balances and recent transactions.

After this, every signed update installs over the previous one and keeps your data.

## Publish development builds from GitHub

The **Android checks** workflow publishes a development prerelease when you run it manually (**Actions → Android checks → Run workflow** on `main`). To keep downloads installable over earlier releases, it signs both APKs with the existing development key, which it reads from the repository secret `TALLY_DEBUG_KEYSTORE_BASE64`.

To create or replace that secret on the Windows computer that holds the key:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.android\debug.keystore")) | Set-Clipboard
```

Then open the repository on GitHub, go to **Settings → Secrets and variables → Actions**, edit `TALLY_DEBUG_KEYSTORE_BASE64` (or add it), paste the clipboard as the value and save. Paste only that text: not the file path and not the keystore file itself. Run the workflow again afterwards.

The workflow refuses to publish if the APKs are signed with any other certificate than SHA-256 `7ddc9c94…b8b865`.

Each release is tagged `v<version>-<date>`, so a version can be published once per day. Running the workflow again for a version that already has a release that day stops with an error naming the tag. Bump the version first. The **checks** badge in the README only reports runs from pushes to `main`, so a refused manual release does not mark the checks as failing.

## Related

- [Installation safety](installation-safety.md) explains update and data-safety rules.
- [Android app signing](https://developer.android.com/studio/publish/app-signing) is Google's reference for keys and signing.
