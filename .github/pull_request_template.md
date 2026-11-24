## Summary

<!-- What changes for the user, and why. -->

## Checks run

- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug`
- [ ] Both editions build (`-PtallyAi=true` and `-PtallyAi=false`)
- [ ] Device or emulator tests, if UI, database or backup code changed

<!-- List what you actually ran and anything you could not test. -->

## Data safety

- [ ] No change to stored data, backups or money calculations, **or** the change is covered by migration and restore tests
- [ ] No real personal data, keys or builds in the diff
- [ ] User guides and `CHANGELOG.md` updated where behaviour changed
