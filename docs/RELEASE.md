# Release guide

## Version source of truth

Read `versionName` and `versionCode` from `app/build.gradle.kts`. The matching changelog heading and annotated `v<version>` tag are release metadata; they do not belong in runtime source or general documentation.

## One-time signing setup

Copy `signing/keystore.properties.example` to the ignored file `signing/keystore.properties`, then point `storeFile` at the original update JKS and provide its passwords and alias. Keep the JKS and credentials outside Git and maintain a separate secure backup.

Without the private signing configuration, unit tests, Lint, and `assembleDebug` use the normal debug signing path. A release build intentionally fails without the stable update signing material. When the stable configuration is present, the Debug APK also uses it so it can be tested as an in-place update.

## Prepare a version

1. Increase `versionCode` for every distributed APK. Set the desired `versionName` in `app/build.gradle.kts`.
2. Add the matching entry to `CHANGELOG.md`; do not change the database or model protocol merely for a release.
3. Run `./scripts/verify-full.ps1`. With an authorized `adb` device, this also runs the isolated device-test suite.
4. For an in-place update, compare the APK certificate with the original keystore before distribution:

   ```powershell
   apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
   keytool -list -v -keystore <path-to-original-jks> -alias <key-alias>
   ```

5. Confirm `git status --short` is empty and no secret or generated file is tracked.

## Tag and push

From `main`, run:

```powershell
./scripts/publish-version.ps1 -Version <version> -Push
```

The script verifies the clean worktree, Gradle and changelog versions, local/remote tag availability, target `origin`, and the full test suite. It then creates the annotated `v<version>` tag and, only with `-Push`, pushes `main` followed by that tag.

This workflow does not create a GitHub Release or upload an APK. After pushing, verify that remote `main` and the tag resolve to the intended commit.

## Recovery rules

- If verification fails, fix the cause and rerun; no tag is created before verification succeeds.
- If `main` pushes but the tag push fails, keep the local annotated tag and push `refs/tags/v<version>` after fixing remote access.
- Never delete or recreate a published version tag to point at different code. Prepare a new version instead.
- Never uninstall the existing app when testing an update; install the same-signed APK over it so stored keys and materials remain intact.
