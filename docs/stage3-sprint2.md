# Stage 3 — Sprint 2 · Hand-tracking + Environment skybox preset

> Mục tiêu: thêm **hand-tracking** thay thế / song song với controller, và 3–4 **rạp ảo skybox** đẹp mà nhẹ.

## Phạm vi

### In-scope
- Bật **OpenXR `XR_EXT_hand_tracking`**.
- Gesture cơ bản:
  - Pinch index-thumb: click (thay trigger).
  - Pinch giữ + di tay: drag (thay grip).
  - Pinch ngón giữa-thumb: secondary click (mở menu).
  - Lật bàn tay (palm-up) hiện wrist menu nhỏ (timer, volume, pause).
- Ưu tiên controller nếu cả 2 cùng track (theo guideline Meta).
- 4 environment preset:
  1. **Black Void** (mặc định, 0 cost).
  2. **Modern Cinema** (room nhỏ, dim light, baked cubemap 1024).
  3. **Drive-in night** (sky gradient + ground plane).
  4. **Space** (starfield cubemap).
- Picker chọn preset trong Settings panel.
- Asset cubemap nén ASTC 4×4 để giảm bộ nhớ.

### Out-of-scope
- Avatar / seat tùy chỉnh.
- Watch Together.
- Voice command.

## Deliverables

1. APK hand-tracking + 4 preset.
2. Asset cubemap (~10 MB tổng) + script bake.
3. Doc `docs/dev/hand-tracking.md`, `docs/dev/environments.md`.
4. Demo video.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S3-2.1 | Bật `XR_EXT_hand_tracking` + locate joint mỗi frame | 1d |
| S3-2.2 | Pinch detect (distance index tip-thumb tip < 2 cm) | 0.5d |
| S3-2.3 | Render mesh tay đơn giản (point cloud joint hoặc skinned mesh từ Meta sample) | 1d |
| S3-2.4 | Map gesture → action set hiện có; ưu tiên controller khi cả 2 active | 1d |
| S3-2.5 | Wrist menu UI quad nhỏ gắn vào joint cổ tay khi palm-up | 1d |
| S3-2.6 | Skybox cubemap loader (KTX2 ASTC) | 1d |
| S3-2.7 | Bake 3 environment trong Blender → cubemap 1024 → KTX2 | 1.5d |
| S3-2.8 | Setting picker chọn preset, lưu vào DataStore | 0.5d |
| S3-2.9 | Đo memory footprint, bật/tắt environment runtime | 0.5d |
| S3-2.10 | Demo + doc | 0.5d |

## Definition of Done

- [ ] Hand-tracking cơ bản hoạt động: pinch để play/pause, drag panel.
- [ ] Wrist menu hiện khi lật tay, ẩn khi úp.
- [ ] 4 preset chuyển được trong < 1s.
- [ ] Memory tăng không quá 50 MB cho preset nặng nhất.
- [ ] Frame time vẫn ≤ ngưỡng comfort (xem PLAN §5).
- [ ] Ktlint, build pass.
