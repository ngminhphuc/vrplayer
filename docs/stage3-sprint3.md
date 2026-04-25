# Stage 3 — Sprint 3 · PiP-on-wrist + A-B loop + Thumbnail seek + App Lab submit

> Mục tiêu: nốt các tính năng polish và **submit App Lab** (hoặc release sideload v1.0).

## Phạm vi

### In-scope
- **PiP-on-wrist**: thumbnail mini của video hiện tại gắn vào cổ tay khi lật tay (tái dùng wrist menu Sprint 2).
- **A-B loop**: chọn 2 timestamp, lặp đoạn.
- **Thumbnail seekbar**: khi tua, hiện preview frame (sinh khi load video, lưu cache 1 ảnh / 5s).
- **Bookmark đa mục**: thêm/xoá nhiều bookmark mỗi file.
- Polish:
  - Loading spinner world-space.
  - Error toast 3D (panel quad nhỏ).
  - Settings dark/light theme cho Compose panel.
  - About screen với credit upstream.
- **Distribution**:
  - Cấu hình ký APK release với keystore của bạn (lưu trong CI secret).
  - `ovr-platform-util` upload App Lab (hoặc tải lên SideQuest).
  - Submit form Meta App Lab (mô tả, screenshot 360 panorama, video preview ≤ 2 phút).
  - Tạo `release/v1.0.0` tag + GitHub Release.

### Out-of-scope
- Voice command.
- MV-HEVC / Ambisonic / Watch Together (giai đoạn sau).

## Deliverables

1. APK release v1.0.0 ký bằng key chính.
2. Listing App Lab (asset 1024×1024 icon, 5 screenshots, 1 video preview, mô tả ≤ 4000 ký tự).
3. Doc `docs/release.md` quy trình ký + submit.
4. CHANGELOG bản v1.0.0 đầy đủ.
5. Demo video tổng thể.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S3-3.1 | PiP-on-wrist quad layer thứ 2, hiện khi lật tay | 1d |
| S3-3.2 | A-B loop UI (2 nút trên control bar) + ExoPlayer logic | 0.5d |
| S3-3.3 | Sinh thumbnail mỗi 5s khi load file (background coroutine) | 1d |
| S3-3.4 | UI thumbnail bay theo seek bar khi pointer hover | 0.5d |
| S3-3.5 | Bookmark đa mục: schema Room mới + UI list | 0.5d |
| S3-3.6 | Settings theme + About screen | 0.5d |
| S3-3.7 | Loading/error UX 3D | 0.5d |
| S3-3.8 | Cấu hình `signingConfigs.release` + GitHub Action build & sign | 1d |
| S3-3.9 | `ovr-platform-util upload-quest-build` script | 0.5d |
| S3-3.10 | Asset listing: icon 1024, 5 screenshots, video preview | 1d |
| S3-3.11 | Submit App Lab + theo dõi review | 1d |
| S3-3.12 | CHANGELOG, tag, release notes | 0.5d |

## Definition of Done

- [ ] PiP-on-wrist hoạt động khi xem video, không drop frame.
- [ ] A-B loop chính xác, off bằng 1 click.
- [ ] Thumbnail seek mượt cho file ≤ 2h.
- [ ] APK release ký, OTA upload App Lab thành công.
- [ ] App Lab listing pass review hoặc nếu reject, đã trả lời feedback.
- [ ] GitHub Release v1.0.0 có APK đính kèm.
- [ ] Tài liệu sideload + release đầy đủ.
