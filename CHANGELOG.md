# Changelog

All notable changes to this project are documented here.

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
