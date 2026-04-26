# Stage 4 — Sprint 4: Subtitle preferences

## Mục tiêu

Cho người dùng tuỳ chỉnh cỡ chữ phụ đề và vị trí dọc của quad — hai
thông số ảnh hưởng nhiều nhất tới mức độ thoải mái khi xem dài.

## Phạm vi

- `SubtitlePrefsStore` — plain SharedPreferences, lưu font size (sp)
  và vertical offset (m).
- `SubtitleSurfaceHost.setFontSize(sp)` — Compose state cho text size.
- `SubtitleQuad::setVerticalOffset(m)` — translate Y model matrix.
- `nativeSetSubtitleVerticalOffset(meters)` JNI export.
- UI tab Phụ đề: hai dãy chip thêm — `24sp / 32sp / 40sp / 48sp` và
  `-0.50 / -0.25 / Mặc định / +0.25` mét.
- Persist + restore khi launch.

## Definition of Done

- Build `:app:assembleQuestDebug` + `ktlintCheck` SUCCESSFUL.
- Đổi font size trong picker → next cue render với cỡ mới.
- Đổi vertical offset → quad dịch lên / xuống tương ứng.
- Đóng app, mở lại → giá trị giữ nguyên.

## Hạn chế đã biết (deferred)

- Chưa cấu hình màu chữ / outline / shadow.
- Chưa scale theo distance.
- Chưa auto-scan sidecar SRT cùng tên video.
