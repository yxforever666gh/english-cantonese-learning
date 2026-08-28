# Architecture

## Overview

The project is a single Android `app` module using Kotlin, Java, Jetpack Compose, Room, OkHttp, and MiniMax speech services. `LearningApplication` creates `AppContainer`, the composition root used by the activity and Compose UI.

The main data flow is:

```text
Compose screen -> ViewModel -> repository/controller -> Room, HTTP source, model, or speech gateway
```

## Subsystems

- **UI:** `ui/` contains navigation, shared editorial components, the reader, settings, and smart-material screens. View models expose UI state and delegate persistence, generation, and playback work.
- **Materials:** `DefaultMaterialRepository` coordinates fixed article sources, failover model generation, saved materials, generation drafts, and playback progress. Network requests use the Responses-compatible gateway and local response validation.
- **Persistence:** Room stores materials, drafts, and playback progress in `listening-materials.db`. Exported schemas are committed under `app/schemas/`.
- **Configuration:** service configuration and user preferences live in app-private preferences. API keys must never be embedded in source, logs, fixtures, or Git history.
- **Speech:** speech controllers coordinate MiniMax synthesis and the bounded local MP3 cache. Cache identity includes the text, language, speed, model, and voice.

## Compatibility boundaries

- Application ID: `com.example.englishcantoneselearning`.
- Minimum Android version: API 26.
- Database version: 3, with explicit migrations `1 -> 2` and `2 -> 3`.
- Device-test target: the `uiTest` build type with the `.uitest` application ID suffix.
- App version source of truth: `app/build.gradle.kts`.

Refactors should preserve public view-model and repository behavior, persisted JSON shapes, Room schemas, request/response protocols, and preference migrations. Split responsibilities behind existing interfaces instead of widening APIs.

## Change navigation

- UI or interaction changes: start in the relevant screen and view-model package.
- Material generation changes: start in `data/network/`, then validate repository and parser tests.
- Storage changes: start in `data/local/`; add a migration, exported schema, and device migration test.
- Speech changes: start in `speech/`; verify cache-key and gateway tests.
- Dependency wiring or lifecycle changes: start in `AppContainer.kt` and `LearningApplication.kt`.

Generated directories such as `.gradle/`, root `build/`, and `app/build/` are never architectural sources of truth.
