# Zangetsu Native Android Rebuild — Phase 01 Source Audit

**Audit date:** 2026-11-12  
**Source archive:** `Zangetsu-main.zip`  
**Target:** Native Android application in Kotlin + XML Views (no Flutter/Dart runtime, no Jetpack Compose).

## 1. Source inventory

The extracted source tree contains 1,876 files. The Flutter application has 595 Dart files under `lib/`; the archive also contains 148 Android Kotlin source files under `android/app/src/main/kotlin`, plus Android XML resources and native build integration. The `assets/` directory contains 77 files: 51 PNGs, 7 SVGs, 10 TTF fonts, and supporting JS/license/config files. Across the entire multi-platform source tree there are 121 PNG files; many are duplicated platform launcher/TV artwork and should not be copied indiscriminately into the phone UI.

The app version in `pubspec.yaml` is `2.3.5+23637`. Android namespace/application ID are `com.spyou.watch_app`. Existing Android SDK values are Flutter plugin-managed, so the native rebuild must set explicit compatible SDK values after dependency/version review rather than blindly inheriting Flutter variables.

## 2. Existing app capability map

The Dart tree confirms these major product areas:

- App shell, phone/TV modes, dynamic/reorderable navigation, mode selection.
- Home/discovery, hero carousel, catalogue rows, continue watching, metadata and recommendations.
- Search, filters, source browsing and extension/provider management.
- Media detail pages, episodes, source selection and playback routing.
- Video player, subtitle/audio selection, playback controls, downloads, casting and watch-together.
- Manga reader, chapter sources/downloads, translation and reading history.
- Light-novel reader, HTML rendering, pagination and EPUB export.
- My List/library, watch/read history, subscriptions and schedule.
- AniList, MyAnimeList, Simkl and TMDB integrations.
- Profiles, authentication/pairing, cloud sync and account settings.
- Settings, appearance, themes, fonts, language, notifications and backup.
- Android TV-specific shell, focus navigation and TV player.

This is a large product migration, not a one-screen port. Native modules and third-party provider systems must be evaluated individually for compatibility and license obligations.

## 3. UI source-of-truth findings

The Dart source is authoritative. Screenshots are only visual cross-checks. No design decisions should be inferred from screenshots when a widget/layout definition exists in Dart.

### Theme tokens (`lib/core/theme/app_colors.dart`)

| Token | Original value |
|---|---|
| Background | `#0B0B0F` |
| Surface | `#16161C` |
| Surface 2 | `#20212A` |
| Primary text | `#FFFFFF` |
| Secondary text | `#A7A7B2` |
| Tertiary text | `#6E6E78` |
| Hairline | `#14FFFFFF` (white at low alpha) |
| Default accent | `#FF4D57` |

Background/surface/accent values are runtime mutable: AMOLED mode and user-selected accent must be represented as theme state, not hardcoded constants in individual screens.

### Typography (`lib/core/theme/app_text.dart`)

- Default font family is the Android/system font (`System`).
- Font sizes: large title 32sp, title 22sp, headline 17sp, body 15sp, caption 13sp, button 16sp, overline 12sp.
- The source supports user-selectable font families and CJK fallback. The ZIP includes 10 TTF files: Noto Sans, Montserrat, Nunito, Lato, Rubik, Roboto, Open Sans, Poppins, Source Sans 3 and Inter.

### Home hero (`lib/core/ui/featured_hero.dart`, `featured_carousel.dart`)

- Rounded inset cinematic artwork card; 28dp corner radius.
- Dynamic dominant-colour treatment and layered gradients over cover art.
- Centered title/logo, metadata line, Play/Read, My List and Info actions.
- Hero top reserve is `max(90dp, statusBarInset + 58dp)`.
- Carousel is swipeable, auto-advances every 5 seconds, with a 750ms page transition and 6dp progress indicators.
- Artwork is fetched/cached from metadata/source URLs; local artwork is not a substitute for those runtime remote covers.

### Navigation shell (`lib/features/shell/root_shell.dart`)

- Uses an `IndexedStack` to preserve tab state/scroll position.
- Default phone shell supports user-configured visible/reordered tabs.
- Floating dock supports at least two styles: liquid glass and floating dock.
- Z-mode/content-mode selector can appear above the dock; modes include Anime, Movie/TV, Manga and Novel (subject to source state/configuration).
- Tab transition: incoming page fades from 0.4 to 1.0 and translates upward 20dp over 200ms using ease-out-cubic.
- Dock hide/show transition is 240ms; dock can collapse in response to scroll.
- Root back handling uses a double-back-to-exit interval of 2 seconds.

The native XML implementation must reproduce the chosen nav style, glass/tint/blur settings, dynamic tab configuration and mode-bar interactions. A static bottom bar would not be behaviorally equivalent.

## 4. Image/font asset policy

Use the original ZIP's app-relevant assets. Do not introduce generic placeholders or unrelated internet imagery.

Migration targets:

- Android launcher variants: preserve the existing mipmap assets and icon variants.
- Brand assets: `assets/brand/` and `assets/icon/` logos/wordmarks/previews.
- Navigation icons: `assets/icons/lucide/` SVG/PNG pairs; use the matching source asset for each glyph.
- Provider icons: `assets/icon/providers/`.
- Fonts: preserve the bundled TTF files and expose the same user-selectable families.
- Android-specific artwork already in `android/app/src/main/res/` should be retained where applicable.

The archive also contains iOS/tvOS icon-stack PNGs and duplicate launcher variants. These are not all phone UI assets; include only those referenced by the Android UI/branding. Build an asset manifest mapping each migrated file to its Dart call-site/resource ID so unused artwork is not copied blindly. For remote media posters/backdrops, preserve the original URL/cache behavior rather than replacing them with static images.

## 5. Existing native Android code

There are 148 Kotlin files in the existing Android app module. They include native player activities/bridges, download/torrent engines, CloudStream and Mihon/An​​iyomi extension infrastructure, casting, subtitles, file/provider helpers and other platform integrations. These files currently coexist with a Flutter host and some depend on Flutter channels or Flutter lifecycle assumptions.

**Migration rule:** inspect and classify each Kotlin file as (A) reusable standalone Android implementation, (B) needs a thin native API refactor, or (C) Flutter-coupled and must be rewritten. Do not copy the entire old Android module into a fresh native app without dependency and lifecycle review.

## 6. Proposed native architecture

- Kotlin + XML layouts / ViewBinding; no Compose.
- Single-activity navigation host with Navigation Component and Fragments for major phone destinations.
- MVVM + repository/data layers; Coroutines + Flow.
- Hilt for dependency injection.
- Retrofit + OkHttp for API/network operations.
- Room for structured local data; DataStore for preferences; secure storage for credentials/tokens.
- Coil for image loading/caching.
- AndroidX Media3 ExoPlayer for playback, with a deliberate adapter layer for the original player/source contracts.
- WorkManager for durable background work where appropriate.
- TV-specific layouts/focus behavior handled as a separate UI surface, not forced into phone XML layouts.

Dependency versions, minSdk, targetSdk and Gradle/AGP/Kotlin compatibility will be fixed in Phase 02 after checking the Android toolchain and native library constraints.

## 7. Main migration risks

1. Provider/extension runtime: CloudStream, Mihon, Aniyomi, Stremio and JavaScript extractors have distinct contracts and native dependencies.
2. Playback parity: source resolution, headers/cookies, HLS rewriting, subtitle tracks, audio tracks, PiP, external players and download handoff need explicit parity tests.
3. Persistence migration: Hive/local preferences and existing user data need a documented import/migration path if compatibility with installed app data is expected.
4. Integrations: AniList/MAL/Simkl OAuth, Supabase/Appwrite/Firebase services, profile sync and notifications must not lose token security or redirect behavior.
5. Android TV: remote focus and TV layouts are a separate acceptance surface.
6. Licensing: the source tree contains multiple license/NOTICE files (including GPL-3.0, Apache-2.0 and MPL-2.0 material). Every reused native module and bundled asset must retain its applicable notices and license terms. Review root `LICENSE` and `NOTICE.md` before redistribution.

## 8. Phase 01 exit criteria

- [x] Confirm original archive and extracted source are present.
- [x] Inventory Dart, Kotlin, XML and app asset files.
- [x] Read original theme tokens and typography scale.
- [x] Inspect home hero and navigation-shell behavior.
- [x] Establish image/font migration policy.
- [x] Establish native architecture and migration risk list.
- [ ] Build full screen-by-screen Dart-to-native mapping (continued as implementation planning in Phase 02/03).

**Phase 01 result:** Source audit completed. This is an audit/architecture baseline only; no claim is made that the native app has been implemented or built yet.
