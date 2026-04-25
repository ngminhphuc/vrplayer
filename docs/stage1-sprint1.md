# Stage 1 — Sprint 1 · Rạp ảo cong + Controller input

> Mục tiêu: nâng quad demo của Stage 0 thành **một rạp chiếu ảo** dùng được, có controller pointer, recenter, và ổn định 72/90 Hz.

## Phạm vi

### In-scope
- Mesh rạp chiếu **curved screen** (cylindrical hoặc flat-quad, scale + curvature điều chỉnh).
- Skybox sphere màu tối / đen (Black Void) làm môi trường mặc định.
- **OpenXR action set** cơ bản: trigger, grip, thumbstick, A/B, menu.
  - Trigger: play / pause.
  - Thumbstick X: tua ±10s.
  - Thumbstick Y: chỉnh âm lượng.
  - Grip + drag: cầm và di chuyển màn ảo.
  - Menu: mở overlay control bar (placeholder, render thật ở sprint sau).
- Recenter (long-press menu hoặc nút riêng).
- Quay đầu free-look (head pose từ OpenXR view).
- Comfort: lock 72 Hz Q2, 90 Hz Q3 mặc định; dynamic FFR (Fixed Foveated Rendering) bật mức 2.

### Out-of-scope
- Picker, Compose UI, phụ đề.
- 360 / stereoscopic.
- Hand tracking.

## Deliverables

1. APK chạy trên Q2/Q3, vào VR là thấy rạp ảo + video sample loop.
2. Doc `docs/dev/input-mapping.md` liệt kê action set + binding.
3. Video demo 1 phút.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S1-1.1 | Tạo cylindrical screen mesh (radius, fov, height tham số), vertex/UV gen | 1d |
| S1-1.2 | Skybox cube/sphere render đầu tiên (single color shader) | 0.5d |
| S1-1.3 | OpenXR action set: tạo `xrCreateActionSet`, `xrCreateAction`, suggested binding cho Quest Touch | 1d |
| S1-1.4 | Map action → ExoPlayer command (play/pause/seek/volume) qua JNI | 1d |
| S1-1.5 | Laser pointer ray render từ controller; intersect logic chuẩn bị cho UI sau | 1d |
| S1-1.6 | Grip-drag: di chuyển/scale màn ảo, lưu transform vào DataStore | 1d |
| S1-1.7 | Recenter: gọi `xrLocateSpace` với reference space mới + lưu offset | 0.5d |
| S1-1.8 | Bật FFR mức 2, vsync 72/90, đo `OVRMetricsTool` | 0.5d |
| S1-1.9 | Test 60 phút phát video không drop frame; viết test note | 0.5d |
| S1-1.10 | Document + demo | 0.5d |

## Definition of Done

- [ ] App vào rạp ảo cong, video phát ổn định 60 phút.
- [ ] Trigger pause/play, thumbstick tua/volume, recenter hoạt động.
- [ ] Có thể grip-drag để dời màn; vị trí/kích cỡ giữ nguyên sau khi đóng-mở app.
- [ ] Frame time trung bình ≤ 13.8 ms Q2, ≤ 11 ms Q3, không có spike > 20 ms trong demo run.
- [ ] Ktlint pass, build `assembleQuestDebug` xanh.
