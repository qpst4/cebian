# Changelog

All notable changes to SlideIndex are documented in this file.

## [1.1.0] - 2026-07-10

### Added
- Lightweight unit tests for message filters/swipes, shake action resolution, quick-launcher layout, and app repository helpers.
- Debug performance overlay panel (FPS / jank) when the layout debug monitor is enabled.
- MIT open-source license.
- Additional instrumentation smoke checks for app wiring.

### Changed
- Extracted `resolveShakeAction` as a testable pure function.
- `PerformanceMonitor` now exposes the latest FPS/jank snapshot for the debug overlay.
- Tightened R8 keep rules for Jetpack Compose.
- Incremented `versionCode` to 2.

### Quality
- ProGuard Compose rules no longer keep the entire `androidx.compose.runtime` and `ui.platform` packages.
