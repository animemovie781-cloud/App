# Phase 02 — Native Android Foundation (in progress)

- Kotlin + XML Views and ViewBinding; no Flutter runtime or Compose.
- Namespace/application ID: com.spyou.watch_app.
- JDK 17, compile/target SDK 35, min SDK 23 (provisional pending dependency/device compatibility verification).
- Native shell with Home, Search, My List and Settings tab entry points.
- Theme tokens match original Dart AppColors defaults.
- Original brand mark, provider icons and ten bundled font files are included in the local project archive.
- Media3 ExoPlayer, Navigation, Lifecycle, Coroutines, Coil and Material dependencies declared.

This phase is not yet build-verified: the current execution environment does not have Gradle installed. Android Studio/Gradle sync and an emulator/device build are required before claiming compile success. Feature modules and original source parity are still pending.
