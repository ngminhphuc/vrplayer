# Stage 4 — Sprint 5: Auto-scan sidecar subtitle

## Mục tiêu

Tự động phát hiện file phụ đề cùng tên cùng folder với video local —
không bắt người dùng phải SAF picker mỗi lần mở phim mới có sub.

## Phạm vi

- `XrActivity.autoScanSidecar(videoUri)` — tìm file `.srt`, `.vtt`,
  `.ass`, `.ssa`, `.ttml` cùng basename trong cùng folder.
- Áp dụng chỉ cho `file://` và path tuyệt đối local. SAF
  (`content://`) và SMB (`smb://`) bỏ qua vì không có
  enumerate-parent rẻ tiền.
- Resolution order: SubtitleStore (user explicit) → autoScanSidecar →
  null.
- `resetPerFileState`: hiển thị URI auto-detect lên Subtitle tab để
  user thấy confirm.

## Definition of Done

- Build `:app:assembleQuestDebug` + `ktlintCheck` SUCCESSFUL.
- Đặt `movie.srt` cạnh `movie.mp4`, mở video → tab Phụ đề hiển thị
  "Đang dùng: movie.srt", cue render trên quad.
- User SAF picker chọn file khác → override auto-detect.
- "Bỏ phụ đề" → set null vào store, lần mở sau auto-detect lại.

## Hạn chế đã biết

- Chỉ scan cùng tên (`movie.mp4` ↔ `movie.srt`). Không match
  `movie.en.srt`, `movie [English].srt`, etc.
- Không scan SAF tree URIs.
- Không scan SMB shares.
- Match đầu tiên thắng — không ưu tiên ngôn ngữ.
