# Stage 4 Sprint 2 — External subtitle parsing — Report

PR: https://github.com/ngminhphuc/vrplayer/pull/12 (TBD on push)
Branch: `devin/1777217016-stage4-sprint2-subtitles`
Stack: PR #8 → #9 → #10 → #11 → #12.

## Đã làm

### `SubtitleStore`
- SharedPreferences plain (`vrplayer_subtitles`), key = video path,
  value = subtitle URI string.
- API: `get(key)`, `set(key, uri)`, `clear(key)`. Phù hợp với pattern
  của `BookmarkStore` / `ResumeStore`.

### `XrActivity` integration
- `buildMediaItem(uri)` helper thay tất cả `MediaItem.fromUri(...)`
  trong `playUrl`, `playEntry`, `loadInitialMedia`. Khi store có
  subtitle cho path này, builder sẽ gắn `SubtitleConfiguration` với
  MIME đoán theo extension (`vtt` / `srt` / `ass` / `ttml`).
- `Player.Listener.onCues` join cue text → push vào
  `pickerHost.setSubtitleCue`. Cue rỗng = clear.
- `launchSubtitlePicker()` mở SAF `ACTION_OPEN_DOCUMENT` với MIME
  filter (`text/*`, `application/x-subrip`, `application/ttml+xml`).
  `takePersistableUriPermission` để quyền sống qua restart process.
- `onActivityResult` xử lý kết quả SAF, gọi `setExternalSubtitle`
  → `subtitleStore.set` + `setMediaItem` lại + `prepare()` + seek
  về vị trí cũ → ExoPlayer apply subtitle ngay.
- `resetPerFileState` push subtitle URI tương ứng + clear cue khi
  đổi file.

### Picker UI
- Tab thứ 7 "Phụ đề" trong `VrPickerScreen`.
- Nội dung: "Chọn file" (gọi SAF) / "Bỏ phụ đề" / hiển thị URI đang
  link (truncate 80 ký tự) / preview cue hiện tại trong khung tối.

## Files

| File | Đổi | Lý do |
| --- | --- | --- |
| `feature/vrplayer/.../picker/SubtitleStore.kt` | + new | Persist link path → subtitle. |
| `feature/vrplayer/.../picker/PickerSurfaceHost.kt` | state + callback | Bridge. |
| `feature/vrplayer/.../picker/VrPickerScreen.kt` | SubtitleTab | UI. |
| `feature/vrplayer/.../XrActivity.kt` | buildMediaItem, listener, SAF | Wiring. |

## DoD

- [x] Build `:app:assembleQuestDebug` SUCCESSFUL.
- [x] `ktlintCheck` pass.
- [ ] User test runtime: pick một file `.srt` cùng tên video, xem
      cue trong tab Phụ đề.
- [ ] User test SAF popup hoạt động trong VR mode (Quest hỗ trợ
      file picker dialog 2D).

## Hạn chế đã biết

- **Cue chỉ trong tab picker**: muốn xem khi đang xem video user
  phải mở picker bằng nút B. Sprint 4-3 sẽ thêm native quad riêng
  cho subtitle floating dưới cinema screen.
- **SAF UI 2D**: Storage Access Framework dialog có thể không
  thân thiện trong VR (menu Quest đôi khi che). Có thể workaround
  sau bằng cách scan thư mục cùng video tự động tìm sidecar.
- **Encoding**: Mặc định Media3 đoán encoding theo BOM hoặc
  `UTF-8`. File SRT Windows-1252 có thể hiển thị mojibake.
- **Style**: ASS hỗ trợ style nâng cao (color, position, font);
  Media3 strip về plain text. Đủ với SRT thông thường.

## Sprint sau

- 4-3: subtitle quad head-locked riêng (native + Compose surface).
- 4-3: auto-scan sidecar (`movie.srt` cùng `movie.mp4`).
- 4-3: subtitle styling controls (offset, size, outline).
