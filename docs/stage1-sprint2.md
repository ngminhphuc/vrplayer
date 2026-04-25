# Stage 1 — Sprint 2 · World-space picker + SAF + resume

> Mục tiêu: cho phép người dùng **chọn file local** trong VR, phát, và tiếp tục từ vị trí cũ.

## Phạm vi

### In-scope
- Module `feature:vrui`: Compose render vào `Surface` → map vào **OpenXR quad layer** (sharper than texture-on-mesh).
- Port `feature:videopicker` Compose code thành `VrPickerScreen` chạy trong panel world-space (~1.4 m trước mặt, 1.2×0.8 m).
- Laser pointer + click trigger để chọn item.
- Storage Access Framework permissions:
  - `READ_MEDIA_VIDEO` (Android 13+ / Quest 3 trở đi).
  - SAF `Intent.ACTION_OPEN_DOCUMENT_TREE` (cấp folder lần đầu).
- Sử dụng module `core:media` đang có để index folder (Room DB).
- Resume: ghi `currentPosition` vào `core:datastore` mỗi 5s, đọc khi mở lại.
- Bookmark đơn giản (1 mục/file).

### Out-of-scope
- Streaming online (sprint 3).
- SMB (stage 3).
- Phụ đề (stage 2).

## Deliverables

1. Picker world-space: tree view + folder view + file view như app gốc.
2. Resume position hoạt động.
3. Doc `docs/dev/world-space-ui.md`.
4. Video demo: mở picker → chọn file → phát → thoát → mở lại tiếp tục.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S1-2.1 | Tạo `feature:vrui` với `ComposeView` render-to-Surface + dirty rect tracking | 1.5d |
| S1-2.2 | Bridge: `Surface` từ Kotlin → native → `XrCompositionLayerQuad` | 1d |
| S1-2.3 | Laser pointer hit-test → emit MotionEvent vào ComposeView | 1d |
| S1-2.4 | Port `MediaPickerScreen` Compose, layout phù hợp panel 1.2×0.8 m | 1.5d |
| S1-2.5 | SAF flow: yêu cầu cấp folder lần đầu, lưu URI persistent | 0.5d |
| S1-2.6 | Index file qua `core:media`, hiển thị thumbnail (sinh từ ExoPlayer 1 frame) | 1d |
| S1-2.7 | Resume position + bookmark trong DataStore | 0.5d |
| S1-2.8 | Animation panel mở/đóng, recenter panel theo head pose | 0.5d |
| S1-2.9 | Test gõ + click, Ktlint, demo | 0.5d |

## Definition of Done

- [ ] Mở app → panel picker hiện trước mặt sharp, không mờ.
- [ ] Click file → đóng picker → màn ảo phát file đó.
- [ ] Đóng app, mở lại → file được tiếp tục từ vị trí cũ (sai số ≤ 2s).
- [ ] Permissions được prompt trong môi trường VR (system overlay) hoặc fallback Quest passthrough.
- [ ] Ktlint, build, test pass.

## Rủi ro

- Compose render-to-Surface trên Quest có thể tốn CPU → bật `LocalDensity` thấp & hạn chế recomposition.
- SAF prompt từ system có thể buộc thoát VR → kiểm tra Quest UX, có thể cần dialog system Quest mới.
