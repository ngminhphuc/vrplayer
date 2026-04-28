# Stage 4 — Sprint 4: Báo cáo

## Đã làm

### Persistence
- `SubtitlePrefsStore` mới: plain SharedPreferences (cosmetic only,
  không cần encrypt). Methods `fontSizeSp()/setFontSizeSp(Int)`,
  `verticalOffset()/setVerticalOffset(Float)`. Coerce vào range
  `[20, 56]` sp và `[-0.6, 0.4]` m.

### Native
- `subtitle_quad.{h,cpp}`: thêm `setVerticalOffset(float)`.
  `draw()` build model matrix `translate(0, sOffsetY, 0)` rồi nhân
  vào trước view trước khi tính MVP.
- `native_calls.cpp`: JNI export `nativeSetSubtitleVerticalOffset`.

### Kotlin
- `SubtitleSurfaceHost`:
  - height tăng 192 → 256 px để vừa font size 48 sp.
  - `fontSizeSp` mutableStateOf, default từ `SubtitlePrefsStore.DEFAULT_FONT_SIZE`.
  - `setFontSize(sp)` coerce + push vào Compose state.
  - `Text(fontSize = sz.value.sp)` — Compose recompose khi đổi.
- `PickerSurfaceHost`:
  - Hai state mới: `subtitleFontSize`, `subtitleVerticalOffset`.
  - Hai callback mới: `onSubtitleFontSizeChange`, `onSubtitleVerticalOffsetChange`.
  - 4 setter mới: `setSubtitleFontSize`, `setSubtitleVerticalOffset`.
- `VrPickerScreen`:
  - Tham số mới: `subtitleFontSize`, `subtitleVerticalOffset`,
    `onSubtitleFontSizeChange`, `onSubtitleVerticalOffsetChange`.
  - `SubtitleTab`: thêm row chip cỡ chữ (24/32/40/48 sp) và row chip
    vị trí dọc (-0.50/-0.25/Mặc định/+0.25 m). Bỏ đoạn placeholder
    "phiên bản render head-locked sẽ vào sprint sau" (đã có ở 4-3).
- `XrActivity.kt`:
  - `subtitlePrefs` lazy.
  - On startup: load saved font size + offset, push vào pickerHost +
    subtitleHost + native quad.
  - Wire `onSubtitleFontSizeChange` → `subtitlePrefs.setFontSizeSp` +
    `subtitleHost.setFontSize`.
  - Wire `onSubtitleVerticalOffsetChange` → `subtitlePrefs.setVerticalOffset` +
    `nativeSetSubtitleVerticalOffset`.
  - `external fun nativeSetSubtitleVerticalOffset(meters: Float)`.

### Docs
- `docs/stage4-sprint4.md` (plan).
- `docs/reports/stage4-sprint4-report.md` (file này).

## Definition of Done

- [x] Build `:app:assembleQuestDebug` SUCCESSFUL.
- [x] `ktlintCheck` SUCCESSFUL.
- [ ] User test runtime: đổi font size → text scale visibly trên quad.
- [ ] User test: đổi vertical offset → quad dịch lên / xuống.
- [ ] Persist: đóng/mở app → giá trị giữ nguyên.

## Hạn chế đã biết

1. Chỉ có 4 preset font size (24/32/40/48 sp). Chưa có slider liên tục.
2. Chỉ có 4 preset vertical offset. Chưa có drag/slider.
3. Quad geometry vẫn cố định kích thước thế giới (2.0 × 0.3 m). Font
   48 sp có thể bị clip nếu cue dài. Surface tăng 192→256 px nhưng
   chưa scale quad.
4. Chưa cấu hình được màu chữ, outline, shadow.

## Sprint kế tiếp đề xuất (4-5)

- Slider liên tục cho cỡ chữ + vị trí.
- Màu / outline / shadow.
- Auto-scan sidecar SRT trong cùng folder video (local file path).
- Scale quad theo font size để không clip.
