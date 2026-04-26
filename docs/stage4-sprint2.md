# Stage 4 Sprint 2 — External subtitle parsing + per-file linking

## Mục tiêu

Cho phép user gắn file phụ đề ngoài (SRT/VTT/ASS/TTML) vào video
đang phát, ExoPlayer parse cue, tab "Phụ đề" hiển thị cue text.

## Scope

**IN**:
- `SubtitleStore` SharedPreferences map path → subtitle URI.
- `MediaItem.SubtitleConfiguration` qua MIME-type theo extension.
- `Player.Listener.onCues` collect cue + push vào picker.
- Tab "Phụ đề" trong picker: nút Chọn file (SAF), Bỏ phụ đề,
  preview cue hiện tại.
- SAF qua `ACTION_OPEN_DOCUMENT` + `takePersistableUriPermission`.

**OUT** (sprint 4-3):
- Render subtitle head-locked qua native quad riêng. Hiện cue chỉ
  hiển thị trong tab Picker — user phải mở picker mới thấy cue.

## Deliverables

- `feature/vrplayer/.../picker/SubtitleStore.kt`.
- `feature/vrplayer/.../picker/PickerSurfaceHost.kt`: state +
  callback bridge.
- `feature/vrplayer/.../picker/VrPickerScreen.kt`: SubtitleTab.
- `feature/vrplayer/.../XrActivity.kt`:
  - `buildMediaItem(uri)` helper thay `MediaItem.fromUri`.
  - `Player.Listener.onCues` map cue → host.
  - `launchSubtitlePicker()` SAF + onActivityResult.
  - `setExternalSubtitle(uri?)` công khai cho picker callback.
  - `resetPerFileState` đồng bộ subtitleUri/cue.
- `docs/reports/stage4-sprint2-report.md`.

## DoD

- Build `:app:assembleQuestDebug` pass.
- `ktlintCheck` pass.
- ExoPlayer load được file SRT (test runtime do user).
- Đổi file → tab Phụ đề hiển thị URI tương ứng (hoặc trống).
- Gắn rồi xoá phụ đề → cue clear, file không còn linked.

## Risk

- **NativeActivity onActivityResult**: `NativeActivity` extends
  `Activity`, không phải `ComponentActivity`. Phải dùng
  `startActivityForResult` + `onActivityResult` truyền thống thay
  cho `registerForActivityResult`. Đã làm đúng.
- **SAF persistable URI**: nếu user xoá quyền truy cập file qua
  Settings, `setMediaItem` sẽ throw IOException và banner sẽ hiển
  thị error. Đây là behavior bình thường.
- **Cue hiển thị trong picker**: user phải mở picker để thấy.
  Sprint sau (4-3) sẽ render cue head-locked riêng.

## Sprint kế (4-3)

- Subtitle head-locked native quad: thêm `subtitle_quad.{h,cpp}` +
  `SubtitleSurfaceHost` Kotlin + JNI bridge mirror picker.
- Subtitle styling: font size, position offset, color/outline.
