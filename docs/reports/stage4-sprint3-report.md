# Stage 4 — Sprint 3: Báo cáo

## Đã làm

### Native quad
- `feature/vrplayer/src/main/cpp/subtitle_quad.{h,cpp}` (mới): flat quad
  2.0 × 0.3 m, Y=0.6, Z=-3.0 (dưới cinema, gần user 3 m), shader OES
  external + alpha blend `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA`,
  `glDepthMask(GL_FALSE)` để không che video.
- `CMakeLists.txt`: thêm `subtitle_quad.cpp` vào sources.
- `gl_renderer.cpp`: init / shutdown / render pass mới sau picker
  (chỉ render khi `SubtitleQuad::visible() && subtitleTextureId != 0`).

### JNI bridge (`video_bridge.{h,cpp}`)
- 5 jmethodID mới: `sAcquireSubtitle`, `sUpdateSubtitle`,
  `sGetSubtitleTexMat`, `sSubtitleWidth`, `sSubtitleHeight`.
- `sSubtitleTexId` static lưu OES texture id.
- 5 wrapper: `requestSubtitleSurface()`, `updateSubtitleTexImage()`,
  `getSubtitleTransformMatrix()`, `subtitleWidth()`, `subtitleHeight()`.

### `native_calls.cpp`
- Thêm `nativeSetSubtitleVisible(boolean)` JNI export. Khi visible
  lần đầu, gọi `requestSubtitleSurface()` mirror cách picker khởi tạo.

### Kotlin
- `SubtitleSurfaceHost.kt` (mới): mini Compose-to-Surface host,
  1024 × 192 px, render duy nhất `Text(cue)` căn giữa, font 32 sp white.
  `Color.TRANSPARENT, PorterDuff.Mode.CLEAR` để alpha-blend hoạt động.
  Pump frame mỗi 33 ms.
- `XrActivity.kt`:
  - `subtitleHost` lazy.
  - 5 method JNI-callable: `acquireSubtitleSurface`,
    `subtitleWidth/Height`, `updateSubtitleTexImage`,
    `getSubtitleTransformMatrix`.
  - `nativeSetSubtitleVisible(boolean)` external.
  - `onCues` listener (đã có ở 4-2): bổ sung
    `subtitleHost.setCue(joined)` + `nativeSetSubtitleVisible(joined.isNotEmpty())`.
  - `resetPerFileState` + `setExternalSubtitle`: clear cue + ẩn quad.
  - `onDestroy` gọi `subtitleHost.releaseSurface()`.

### Docs
- `docs/stage4-sprint3.md` (plan).
- `docs/reports/stage4-sprint3-report.md` (file này).

## Definition of Done

- [x] Build `:app:assembleQuestDebug` SUCCESSFUL (CMake compile cả 5 native
      file mới + Kotlin).
- [x] `ktlintCheck` SUCCESSFUL — không violation.
- [ ] User test runtime trên Quest: pick SRT, xác nhận cue hiện trên quad
      khi đóng picker, biến mất giữa các cue.
- [ ] User confirm alpha-blend trong/đẹp, không có viền tối quanh chữ.

## Hạn chế đã biết

1. Vị trí quad cố định (Y=0.6, Z=-3.0). Người dùng không config được vị trí.
2. Font size cố định 32 sp. Không scale theo distance.
3. Style ASS/SSA bị strip về plain text qua `Cue.text.toString()`.
4. Multi-cue rendering: nếu cue group có nhiều cue cùng lúc, join với `\n`
   nhưng quad chỉ cao 0.3 m — text dài có thể bị cắt.
5. Chưa auto-scan sidecar `.srt` cùng tên video (`movie.mp4` → `movie.srt`).
   Vẫn cần SAF picker thủ công từ 4-2.

## Sprint kế tiếp đề xuất (4-4)

- Subtitle preferences (size, color, vertical offset).
- Auto-scan sidecar SRT/VTT khi chọn video file.
- Multi-line wrap với `BasicText` autoSize hoặc Compose `TextLayout`
  measure-then-scale.
- Dynamic Y position theo cinema mode (cinema/360°/180° khác nhau).
