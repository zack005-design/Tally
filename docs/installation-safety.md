# Safe installation, updates and data

## Installation and updates

Install APKs only from the project's trusted release channel. A checksum detects a damaged or substituted file only when compared with a checksum from a trusted source. It is not a malware scan or a substitute for checking the signing certificate.

An in-place update must keep `com.smartexpense`, a compatible signing certificate and a nondecreasing version code. This source update uses version 0.2.2, code 4. Both AI options retain the same app identity and database. Never resolve an update failure by telling users to uninstall or clear storage: those operations remove local records.

The previously downloaded 0.2.1 APK in this workspace is signed by an Android Debug certificate, SHA-256 `7ddc9c94f93053522c6048694ff19436bc1333a18654d10be26d646e32b8b865`. This is a development identity, not the trusted production identity. Do not silently replace it with a new signing key and claim existing users can update. A production transition needs either a tested supported signing transition or an explicit migration: export an encrypted backup from the old app, verify it in a separate test installation, and retain that backup before any user chooses to replace the installation. Never uninstall on the user's behalf.

The release verifier rejects debug certificates even when their fingerprint is supplied. Existing debug installations therefore need a deliberate transition before production distribution. No signing key is generated or changed by this patch.

## Release configuration

Set `TALLY_KEYSTORE_FILE`, `TALLY_KEYSTORE_PASSWORD`, `TALLY_KEY_ALIAS` and `TALLY_KEY_PASSWORD` in the build environment. Keep the keystore and passwords outside the repository, and keep an independently recoverable secure copy of the signing identity. Release packaging fails without this configuration; it does not fall back to the debug identity.

Build with `gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease`. Then run:

```powershell
.\tools\verify-apk.ps1 -Apk app/build/outputs/apk/release/app-release.apk -ExpectedCertificateSha256 <trusted-certificate-sha256> -ExpectedVersionCode 4 -SdkRoot <android-sdk>
```

Obtain the expected certificate fingerprint independently from the maintained signing identity, not from an untrusted APK. The script verifies the signature, trusted certificate, package, version, non-debuggable status, absence of Internet permission and disabled automatic Android backup. It writes an adjacent `.sha256` only after all checks pass. Distribution remains a separate, explicit step; no artifacts are uploaded by this tool.

## User data protection

New full backups require a password of at least eight characters containing a non-whitespace character. Use a long, unique passphrase. Backups use the existing authenticated AES-256-GCM format; keep the password separately because it cannot be recovered. Older plaintext and password-encrypted backups remain importable, including those with shorter passwords. Readable CSV/PDF reports remain unencrypted and should be shared deliberately.

Restore validates before replacing records, and performs replacement inside a database transaction. Failed validation, authentication or insertion must leave existing records intact. Upgrade tests cover each supported database schema (1–4) to schema 5. CI runs populated migration, restore authentication, tampering and transaction rollback tests in a disposable emulator.

Run the device safety checks locally with `gradlew.bat -I tools/data-safety-build.init.gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartexpense.InstallationDataSafetyTest,com.smartexpense.BackupTest --max-workers=2`. The init script uses separate build outputs and the test app identity `com.smartexpense.safety` to avoid touching an installed Tally database.

Records remain in Android's private app storage. Automatic Android cloud backup and device transfer are disabled; moving phones requires an explicit backup. App lock controls access but does not encrypt the database. This patch does not add database encryption, malware scanning, remote recovery or a guarantee against a compromised/unlocked device. Keep encrypted backups before updates and before replacing records.

References: [Android app signing](https://developer.android.com/studio/publish/app-signing), [APK signature verification](https://developer.android.com/tools/apksigner), [Room migration testing](https://developer.android.com/training/data-storage/room/migrating-db-versions#test).
