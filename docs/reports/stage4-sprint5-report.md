# Stage 4 — Sprint 5: Báo cáo

## Đã làm

### Logic
- `XrActivity.autoScanSidecar(videoUri): String?`:
  - Parse URI; chỉ chấp nhận scheme null (path raw) hoặc `file://`.
  - Lấy parent dir → check `canRead()` để bypass thư mục restricted.
  - Lấy `nameWithoutExtension` của video.
  - Loop qua `srt`, `vtt`, `ass`, `ssa`, `ttml`. File đầu tiên tồn tại
    + readable → return `Uri.fromFile(...)`.

### Resolution order
- `buildMediaItem(uri)` lấy explicit từ SubtitleStore trước; fallback
  autoScanSidecar; nếu null không add subtitle config.
- `resetPerFileState(path)` push vào Subtitle tab cùng order, để UI
  cho thấy auto-detect đã pick được sidecar.

### Docs
- `docs/stage4-sprint5.md` (plan).
- `docs/reports/stage4-sprint5-report.md` (file này).

## Definition of Done

- [x] Build `:app:assembleQuestDebug` SUCCESSFUL.
- [x] `ktlintCheck` SUCCESSFUL.
- [ ] User test: drop `movie.srt` cạnh `movie.mp4`, mở video → cue
      render trên quad ngay không cần pick.
- [ ] User test: SAF override → ưu tiên file user chọn.
- [ ] User test: "Bỏ phụ đề" + mở lại → auto-detect lại.

## Hạn chế đã biết

1. Chỉ match exact basename. `movie.en.srt` không khớp với `movie.mp4`.
2. SAF content URI bị skip — phải dùng SAF picker thủ công.
3. SMB URI bị skip — phải dùng SAF picker thủ công.
4. Lần lượt extension; không có ưu tiên theo ngôn ngữ.
5. `autoScanSidecar` chạy trên main thread (gọi từ buildMediaItem).
   I/O check chỉ là `File.exists()` — fast trên local FS, có thể chậm
   nhẹ với external SD card. Nếu là vấn đề có thể move sang IO dispatcher.

## Sprint kế tiếp đề xuất (4-6)

- Auto-scan SAF tree URIs (yêu cầu user grant `OPEN_DOCUMENT_TREE`
  cho thư mục video).
- Auto-scan SMB share (enumerate parent qua smbj).
- Match `movie.<lang>.srt` patterns + ưu tiên theo system locale.
- Slider liên tục cho cỡ chữ + offset.
- Cấu hình màu chữ / outline.
