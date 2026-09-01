# Changelog

All notable changes to this project are documented here.

## [4.0] - 2026-09-01

### Added

- A real-time English and Cantonese news home page backed entirely by fixed RSS sources and deterministic local filtering.
- Multi-label news classification, cached feed fallback, in-app original-article reading, single-language playback, and news bookmarking.
- An optional ignored build-time seed channel for shipping an existing settings snapshot and Room article database inside a private APK.

### Changed

- Replaced the bottom navigation's Create destination with News; AI generation and pasted articles now open from the News header.
- Split fixed-source discovery into reusable feed refresh and article-loading operations while preserving the AI material workflow.
- News articles use a relaxed code-only body cleaner and never invoke a material-generation model.

### Security

- Embedded settings and databases remain outside Git through `signing/embedded-assets`; public source pushes never include runtime API keys or personal article data.

## [3.5] - 2026-09-01

### Added

- Article-level speed controls backed by global English, Cantonese, and Mandarin preferences.
- Direct IELTS 1.0–9.0 material-level selection for both English and Cantonese generation.
- Concurrent bilingual audio preloading for the current sentence and the following two sentences.
- A non-destructive Room v3-to-v4 migration that preserves materials, drafts, sources, and playback progress.

### Changed

- Unified all default speech speeds at 0.8x and migrated existing installations once to the new defaults.
- Replaced the legacy easy, target, and challenge creation choices with the exact IELTS listening level.
- Refreshed the navigation, reader, material, settings, voice, provider, theme, and launcher presentation.
- Updated generated material metadata and model guidance to record and display the selected listening level.

### Fixed

- Removed the avoidable network pause between a target-language sentence and its Mandarin translation.

## [3.4] - 2026-08-29

### Added

- Project architecture, release, verification, and agent working guides.
- Fast and full PowerShell verification entry points.
- A guarded version tagging and push workflow.

### Changed

- Reduced large UI and material-feature responsibilities while preserving existing behavior and interfaces.
- Prepared signing configuration for safe public source control and unsigned fresh-clone development.
- Replaced hard-coded runtime version text with the Gradle-generated app version.
- Reduced build overhead by using local icon resources and Gradle build caching.

### Security

- Keystore files, credentials, machine-local SDK configuration, and generated artifacts are excluded from source control.

The Room schema, stored material formats, application ID, and model request/response protocol are unchanged in this release.
