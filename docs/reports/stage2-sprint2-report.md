# Stage 2 — Sprint 2 · Báo cáo

> Trạng thái: **Code-complete trên CI Linux. Cần test stereo trên Quest hardware.**
> Branch: `devin/<ts>-stage2-sprint2-stereo`, stack lên Sprint 2.1 (sphere).

## Tóm tắt

Sprint này thêm hỗ trợ **stereoscopic 3D** (SBS / TB) trên cả cinema cylinder và sphere 360/180. UV crop per-eye được compose vào `texMatrix` ngay trong gl_renderer — không refactor signature của Screen / Sphere.

## Đã làm

### Native — UV crop per eye
- `cpp/stereo.{h,cpp}` — class `Stereo` với enum 5 mode (`Mono`, `SbsLeftRight`, `SbsRightLeft`, `TbLeftRight`, `TbRightLeft`), API `uvScaleOffset(eyeIndex, out[4])`.
- `cpp/gl_renderer.cpp::renderEye` nhận thêm `int eyeIndex`, build `stereoCrop` matrix từ scale/offset, compose `texMatrix * stereoCrop` rồi pass vào `Screen::draw` hoặc `Sphere::draw`. Mono = identity → behavior cũ.
- `cpp/native_calls.cpp` thêm export `nativeSetStereo(int)`.

### Kotlin — auto-detect + override
- `playback/StereoMode.kt` — enum `StereoMode` (Mono/SBS_LR/SBS_RL/TB_LR/TB_RL) + `StereoDetector.detect(source)` parse filename theo pattern `_sbs/_lr/_tb/_ou/...`. Order ưu tiên: TB_RL trước TB_LR, SBS_RL trước SBS_LR.
- `XrActivity.applyStereoFor(path)` được gọi từ `applyProjectionFor` (chain cùng projection detect) → mỗi lần đổi media tự áp stereo.
- `XrActivity.setStereoOverride(mode)` cho user manual choice.
- Tab Settings của picker thêm 2 row chip: row 1 (Auto / Mono / SBS L|R / SBS R|L) + row 2 (TB L/R / TB R/L).

### Docs
- `docs/dev/stereo-layouts.md` — UV math, auto-detect, testing checklist.
- `docs/reports/stage2-sprint2-report.md` (file này).

## Build & lint

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL |

## Definition of Done — đối chiếu

| Tiêu chí | Trạng thái |
|---|---|
| Phát đúng 3D trên Q3 (parallax cảm nhận được, không ghost) | **CHƯA TEST** (cần Quest + clip stereo). UV math đối chiếu manual: SBS_LR left=`(0.5,1)|(0,0)` → đúng. |
| Phụ đề SRT/ASS hiển thị sharp, không jitter | **DEFERRED** sang stage 3 sprint 1 (kèm comfort menu). Xem out-of-scope. |
| Auto-detect đúng ≥ 80% clip test | Heuristics phủ tên file phổ biến (`_sbs`, `_tb`, `_ou`, `side-by-side`). Binary `st3d` box parser deferred. |
| Trong stereo mode, phụ đề không bị chia 2 | DEFERRED cùng phụ đề. |
| Ktlint, build pass | **DONE**. |

## Không làm trong sprint này

- **Phụ đề head-locked/screen-locked** — pushed sang Stage 3 Sprint 1. Lý do: pipeline subtitle ExoPlayer đi qua `MediaSession` + `SubtitleView`, render-to-texture cần riêng 1 layer OpenXR + life-cycle cẩn thận. Không hợp scope với UV crop.
- **MV-HEVC** dual-stream — cần extension OpenXR `XR_KHR_video_capture` (chưa stable trên Quest).
- **MP4 `st3d` / MKV Projection** binary parsing — filename heuristics đủ cho 80%+ case. Fallback override luôn còn.
- **Stereo subtitle convergence depth** — sau, P2.

## Rủi ro đã thấy

1. **Y-flip trong texMatrix có thể đảo ngược TB top/bottom** — ExoPlayer's `getTransformMatrix` đã apply Y-flip, nên TB_LR offset cho left eye dùng `0.5` cho v thay vì `0`. Đã ghi chú trong `stereo.cpp` nhưng cần kiểm tra trên hardware. Nếu hiển thị bị đảo, swap TB_LR ↔ TB_RL.
2. **Stereo trên picker** — picker quad cũng đi qua `texMatrixEye` chứ không, hiện không. Picker UI là Compose mono, không cần stereo crop. Đã giữ picker dùng `texMatrix` gốc qua `getPickerTransformMatrix`.
3. **Convergence / IPD** — stereo render dùng `XrView::pose` từ runtime nên IPD đã được Quest config sẵn. Không cần expose thêm slider.

## File đã đụng

```
feature/vrplayer/src/main/cpp/{stereo,native_calls,gl_renderer,CMakeLists}.{cpp,h,txt}
feature/vrplayer/src/main/cpp/{gl_renderer.h,xr_session.cpp}  (renderEye signature)
feature/vrplayer/src/main/java/dev/.../playback/StereoMode.kt
feature/vrplayer/src/main/java/dev/.../picker/{VrPickerScreen,PickerSurfaceHost}.kt
feature/vrplayer/src/main/java/dev/.../XrActivity.kt
docs/dev/stereo-layouts.md
docs/reports/stage2-sprint2-report.md
```
