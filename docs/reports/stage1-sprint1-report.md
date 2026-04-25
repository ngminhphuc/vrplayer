# Stage 1 — Sprint 1 · Báo cáo

> Trạng thái: **Code-complete trên CI Linux. Cần validate trên Quest hardware.**
> PR: cập nhật khi tạo.
> Branch: `devin/<ts>-stage1-sprint1-cinema`.

## Tóm tắt

Sprint này biến quad spike của Stage 0 thành **một rạp ảo dùng được**: cylindrical screen + skybox + OpenXR action set đầy đủ + laser pointer + grip-drag + recenter, gắn với pipeline ExoPlayer hiện có qua JNI. Build `assembleQuestDebug` xanh, `ktlintCheck` pass, không có warning C++ nào leo lên thành error.

## Đã làm

### Hình học
- `feature/vrplayer/src/main/cpp/screen.{h,cpp}` — cylindrical screen mesh (radius/arc/height parametric, 48×12 segments). Hiện đang chiếu một strip cong qua model matrix (chord + rotation), đủ cho 120° FoV. Khi đo trên Quest cần tinh chỉnh thêm trong vertex shader để khớp đúng cylinder.
- `skybox.{h,cpp}` — fullscreen-quad gradient indigo→đen (Black Void), depth-write off.
- `pointer.{h,cpp}` — laser ray 1.5 m từ controller aim pose.

### Input
- `input.{h,cpp}` — `XrActionSet vrplayer` với 5 actions (`trigger`, `grip`, `thumbstick`, `menu`, `aim_pose`), suggested binding cho Oculus Touch.
- Edge-detection cho trigger/menu, hai action space (left/right aim).

### Mapping → ExoPlayer
- `xr_session.cpp::processInput`: trigger → `togglePlayPause`, thumb X → `seekDelta(±10 000)` rate-limit 250 ms, thumb Y → `volumeDelta`.
- `XrActivity.kt`: bốn JNI sink mới (`togglePlayPause`, `seekDelta`, `volumeDelta`, `persistScreenTransform`) chạy trên main thread qua `runOnUiThread`.
- `video_bridge.{h,cpp}`: 4 JNI helper, attach JavaVM đúng cách.

### Tương tác
- Grip-drag (`processInput`): mỗi frame đo delta giữa current aim pose và pose lúc grip-press; cập nhật yaw/yOffset/zOffset; release → persist `SharedPreferences("vrplayer_screen")`.
- Recenter: tap menu → destroy + recreate `XR_REFERENCE_SPACE_TYPE_LOCAL`.
- `XrSession::applyScreenTransform` để Kotlin gọi vào trong tương lai (DataStore wiring lùi sang Sprint 2).

### Hiệu năng
- `xr_session.cpp::createInstance`: enumerate extension và best-effort enable `XR_FB_foveation`, `XR_FB_foveation_configuration`, `XR_FB_swapchain_update_state`, `XR_FB_display_refresh_rate`. Code path để áp FFR level 2 thực tế cần kiểm tra trên Quest (Linux CI không có Meta runtime để probe).
- `gl_renderer.cpp` refactor: skybox → screen → pointers, depth-test bật, depth-write off cho skybox.

### Docs
- `docs/dev/input-mapping.md` — bảng action / binding / effect.
- `docs/reports/stage1-sprint1-report.md` (file này).

## Build & lint

| Lệnh | Kết quả |
|---|---|
| `./gradlew :feature:vrplayer:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL (~1 phút) |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL |
| C++ warnings | 28 (`missing-field-initializers` từ struct OpenXR, không block) |

## Definition of Done — đối chiếu

| Tiêu chí | Trạng thái |
|---|---|
| Vào rạp ảo cong, video phát ổn định 60 phút | **CHƯA TEST** trên Quest. Code path đã wire. |
| Trigger pause/play, thumbstick tua/volume, recenter | Code có; phải bấm thử trên thiết bị thật. |
| Grip-drag dời/scale màn, persist sau khi đóng-mở app | Code có (SharedPreferences). DataStore proper sẽ ở Sprint 2. |
| Frame time ≤ 13.8 ms Q2, ≤ 11 ms Q3, không spike > 20 ms | Cần `OVRMetricsTool` trên Quest. |
| Ktlint pass, build `assembleQuestDebug` xanh | **DONE** |

## Không làm trong sprint này (đẩy sang sau)

- DataStore (proto) thay cho SharedPreferences — Sprint 2 đi cùng picker.
- World-space overlay menu (lúc tap menu) — Sprint 3 với Compose-in-XR.
- Curving thật trong vertex shader (hiện dùng chord approximation) — sẽ refactor khi đo méo trên Quest.
- Validate FFR level 2 trên thiết bị — sẽ làm trước khi nộp Stage 1 review.

## Rủi ro đã thấy

1. **Cylinder approximation**: model-matrix chord chỉ đúng khi user nhìn thẳng vào tâm. Khi quay mạnh đầu, edge của screen sẽ lệch so với cylinder thật. Acceptable cho rạp 120°; nếu user phàn nàn, refactor sang vertex-shader trig.
2. **Grip-drag dùng yaw từ delta-x của hand**: dễ vọt khi user vung tay. Có thể cần damping hoặc pivot quanh tâm screen ở sprint sau.
3. **Recenter qua destroy/create reference space**: rủi ro 1 frame trống. Nếu thấy flash trên Quest, chuyển sang `XR_FB_local_floor` hoặc dùng pose offset thuần.

## File đã đụng

```
feature/vrplayer/src/main/cpp/{screen,skybox,pointer,input,math_util}.{h,cpp}
feature/vrplayer/src/main/cpp/{xr_session,gl_renderer,video_bridge}.{h,cpp}
feature/vrplayer/src/main/cpp/CMakeLists.txt
feature/vrplayer/src/main/java/.../XrActivity.kt
docs/dev/input-mapping.md
docs/reports/stage1-sprint1-report.md
```
