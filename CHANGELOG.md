# Changelog

VR Player. All notable changes to this fork are documented per sprint
of the roadmap (`docs/`).

The base project is NextPlayer ([anilbeesetti/nextplayer](https://github.com/anilbeesetti/nextplayer))
licensed under GPLv3. This fork inherits the same license. See
`LICENSE`.

## [Unreleased] — Stage 3 wrap-up

Stage 3 is the final stage of the original 8-sprint roadmap. After it
merges to `main`, the next milestone is App Lab submission (signing,
metadata, Horizon Store entitlements).

### Added
- **A-B loop**: per-session state machine with 250 ms watcher in the
  resume-writer loop. (`feature/vrplayer/.../playback/ABLoop.kt`).
- **Bookmarks**: per-file JSON-backed list in SharedPreferences
  `vrplayer_bookmarks`. Persists across runs.
  (`feature/vrplayer/.../picker/BookmarkStore.kt`).
- **Playback tab in world-space picker**: set A/B/Clear, add/remove/
  seek bookmark.
- **Hand-tracking**: optional `XR_EXT_hand_tracking`; pinch (thumb-tip
  vs index-tip < 25 mm) acts as a controller trigger when available.
- **Environment presets**: 4 procedural skyboxes (BlackVoid /
  ModernCinema / DriveIn / Space). Stored in atomic `<int>` shared
  with render thread; thread-safe across UI ↔ render thread.

### Changed
- Resume writer now ticks every 250 ms (was 5 s). Same throughput for
  resume save (5 s aggregate); A-B loop check is the new tenant of
  the loop.
- `SmbDataSource.open()`: accept upper-case `SMB://` URIs as well
  (ignore-case scheme check), throw `IOException` instead of
  `IllegalArgumentException` so Media3 can recover.
- `SmbBrowser.ensureShare()`: build connection/session/share locally
  before assigning to fields, with cleanup on failure — no more
  Connection leak per ExoPlayer retry on auth fail.
- SmbTab Compose UI clears local `selected` state when the chosen
  server is removed.

### Fixed
- `SmbDataSource.getUri()` returned an interpolated browser
  `toString()` (e.g. `smb://browser@1a2b3c`); now returns the
  caller-supplied URI from `open()`.

## Stage 2 — Format expansion

### Added
- 360° equirect projection (whole-sphere mesh, mono shader).
- 180° hemisphere projection (front-half mesh, mono shader).
- Auto-detect from filename / metadata; manual override in picker.
- Stereo SBS (L|R, R|L) + TB (L/R, R/L) via per-eye UV crop in the
  fragment shader, atomic `<int>` mode shared with render thread.
- Stereo auto-detect from filename hints (`_sbs`, `_lr`, `_tb`,
  `_ou`).

## Stage 1 — Cinema MVP

### Added
- Curved cinema screen (180° cylinder, 9 m radius, 16:9 quad).
- Quest Touch input: trigger pause/play, joystick seek, B menu, grip
  to drag screen, recenter snap-front.
- World-space picker (Compose-to-Surface) with Local / Network / SMB
  / Settings tabs.
- Per-file resume position via `SharedPreferences vrplayer_resume`.
- Sleep timer (15/30/60 m), proximity auto-pause when headset is
  removed.
- URL streaming (HTTP / HTTPS / RTSP via Media3).
- Sideload guide (`docs/sideload.md`).

## Stage 0 — Spike

### Added
- Module `feature:vrplayer` (NDK + CMake + OpenXR loader 1.0.34).
- `XrActivity` Kotlin bridging to native via JNI.
- ExoPlayer → `SurfaceTexture` → `GL_TEXTURE_EXTERNAL_OES` → quad.
- Build flavor `quest` (arm64-v8a only, minSdk 29, targetSdk 32).
- Application ID `dev.ngminhphuc.vrplayer` (rebrand from NextPlayer).
- Manifest with `com.oculus.intent.category.VR`,
  `com.oculus.supportedDevices=quest2|quest3|questpro`,
  `vr.headtracking`.

## Notes

- Audio sample mp4 not bundled; the spike falls back to a silent
  black quad until the user picks a file from local storage / URL /
  SMB.
- Hand-tracking is best-effort; runtime checks `XR_EXT_hand_tracking`
  and disables silently on devices/users without it.
- This fork removes Google Play / F-Droid / IzzyOnDroid links from
  README and UI strings (per the project goal of sideload-only
  distribution until App Lab). NextPlayer attribution is preserved
  per GPLv3.
