# AI and non-AI APKs

Both editions share `com.smartexpense`, a version code, database schema and signing certificate. They update one another; they do not install side by side. Both include the deterministic calculator, rule-based parser, and existing ML Kit receipt OCR. “Non-AI” means the custom expense-command model is absent; it does not mean every existing third-party component is machine-learning-free.

Build the AI edition (default):

```sh
./gradlew -PtallyAi=true :app:assembleDebug :app:testDebugUnitTest
```

Build the non-AI edition:

```sh
./gradlew -PtallyAi=false :app:assembleDebug :app:testDebugUnitTest
```

Copy each APK before building the other: both commands write `app/build/outputs/apk/debug/app-debug.apk`. With `tools/command-build.init.gradle`, outputs are redirected beneath `.build-cache/command-output/app/`.

AI assets live in `app/src/localAi/assets/` and are added to the main asset source set only when `tallyAi=true`. `BuildConfig.LOCAL_AI_ENABLED` also disables model loading in the non-AI edition. Invalid property values fail configuration. Instrumentation `DistributionVariantTest` verifies that model asset presence matches the flag; the model-specific instrumentation test is skipped in non-AI builds.
