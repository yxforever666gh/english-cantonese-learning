# Project working guide

## Purpose

- This is a single-module Android app for English and Cantonese reading, material generation, and sentence-by-sentence playback.
- Keep changes narrow. Read the relevant feature files and tests; never scan generated `build/` content for implementation context.

## Architecture map

- `ui/` contains Compose screens and view models. Material creation and playback live under `ui/material/`.
- `data/repository/` coordinates saved materials, drafts, progress, sources, and model generation.
- `data/local/` is the Room database; `data/network/` contains the Responses-compatible material gateway.
- `speech/` owns MiniMax voice discovery, synthesis, playback, and audio caching.
- `AppContainer.kt` is the application composition root. Keep construction and dependency wiring there.

## Safety invariants

- Never change the application ID or stable signing key for an in-place update.
- Never commit JKS files, API keys, `signing/keystore.properties`, `local.properties`, or generated APKs.
- Never add destructive Room migration fallback. Schema changes require a version bump, an explicit migration, and an exported schema.
- Instrumented tests must continue to target the isolated `.uitest` application ID; they must not uninstall or clear the user's app.
- Preserve stored JSON fields, old drafts, playback progress, and service preference migrations unless a migration is explicitly requested.
- Do not change model prompts or request/response schemas as part of an unrelated refactor.

## Verification

- Fast check: `./scripts/verify-fast.ps1`.
- Full check before release: `./scripts/verify-full.ps1`.
- Run focused tests while iterating; run the full check after dependency, database, signing, or release changes.
- Device tests are run by the full script only when `adb` reports at least one authorized device.

## Release

- Read the current `versionName` and `versionCode` from `app/build.gradle.kts`.
- Every distributed APK needs a higher `versionCode`; keep `versionName`, `CHANGELOG.md`, and the Git tag aligned.
- Follow `docs/RELEASE.md`. The original update keystore must be backed up outside this public repository.
