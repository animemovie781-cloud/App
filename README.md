# Zangetsu — Native Android Rebuild

Native Android migration of the original Zangetsu Flutter application.

- Language: Kotlin
- UI: Android XML Views + ViewBinding (no Flutter runtime, no Compose)
- Application ID: com.spyou.watch_app
- Original source of truth: Zangetsu Dart UI and Android assets

## Status
Phase 01 source audit: docs/PHASE_01_SOURCE_AUDIT.md
Phase 02 started: native Gradle project, manifest, theme tokens, initial app shell, Home layout and original brand/font/provider assets scaffolded.

This is an incremental migration scaffold, not a feature-complete replacement. Search, provider integrations, playback, catalogue data, manga/novel, account sync and other screens remain to be ported.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle, then run the app configuration.
