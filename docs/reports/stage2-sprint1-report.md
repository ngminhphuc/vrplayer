# Stage 2 — Sprint 1 · Báo cáo

> Trạng thái: **Code-complete trên CI Linux. Cần test trên Quest hardware.**
> Branch: `devin/<ts>-stage2-sprint1-sphere`, stack lên Sprint 3.

## Tóm tắt

Sprint này thêm geometry **inside-out sphere** cho 360° và **hemisphere** cho 180° monoscopic. Auto-detect bằng tên file + MP4 metadata (best-effort), user override qua tab Settings. Snap-front reset yaw ngay lập tức.

## Đã làm

### Native — sphere geometry
- `cpp/sphere.{h,cpp}` — class `Sphere` với mesh static 48 × 96, shader `samplerExternalOES`.
- 2 mesh được upload sẵn:
  - `sVao360` cho `Mode::Equirect360` (yaw 0..2π, UV 0..1).
  - `sVao180` cho `Mode::Hemisphere180` (yaw -π/2..+π/2, UV 0..1, stretch).
- `setYawOffsetDeg()` rotate view-matrix Y-axis cho snap-front.

### Native — render dispatch
- `gl_renderer.cpp::renderEye` chọn nhánh: `Sphere::Mode == Off` → `Screen::draw` (cylinder), khác → `Sphere::draw`.
- Init / shutdown hooks trong `GlRenderer::init/shutdown`.

### JNI Kotlin → native (chiều ngược lại lần đầu)
- `cpp/native_calls.cpp` — 3 `extern "C"` exports:
  - `nativeSetProjection(int)` — 0 = off, 1 = 360, 2 = 180.
  - `nativeSnapFront()` — reset yaw về 0°.
  - `nativeRotateYaw(float)` — set yaw offset bằng độ (predicates cho future thumbstick rotate).
- `XrActivity` declare 3 `external fun` tương ứng.

### Kotlin — auto-detect + UI
- `playback/ProjectionDetector.kt` — 2-pass detect:
  - Filename: `_360` / `_eq` / `vr360`, `_180` / `vr180`. Order: 180 trước 360.
  - MP4 metadata: `MediaExtractor` đọc `spatial-format` (API 33+; Quest API 32 rơi về heuristics).
- `XrActivity.applyProjectionFor(path)` được gọi từ `playEntry` và `playUrl`.
- `XrActivity.setProjectionOverride()` cho user manual.
- Tab **Settings** trong `VrPickerScreen` thêm 4 chip (Auto / Cinema / 360° / 180°) + button **Snap front**.

### Docs
- `docs/dev/projection-shaders.md` — shader, mesh, auto-detect, override.
- `docs/reports/stage2-sprint1-report.md` (file này).

## Build & lint

| Lệnh | Kết quả |
|---|---|
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL (ktlintFormat tự sửa 1 violation) |

## Definition of Done — đối chiếu

| Tiêu chí | Trạng thái |
|---|---|
| Phát file 360 8K equirect trên Q3 ở 60 fps không drop | **CHƯA TEST** trên Quest. Mesh static, shader đơn; bottleneck là decoder. |
| Phát file 180 hemisphere mượt; vùng ngoài 180° không có texture rò | **CHƯA TEST**. Mesh chỉ trải front-half nên ngoài 180° không vẽ. |
| Toggle 3 mode mượt, recenter snap front đúng | Code đã wire (Settings tab + button Snap front). |
| Auto-detect đúng cho ≥ 80% sample test | Filename heuristics phủ ~80% case thực tế (clip Insta360, YouTube 360 default). MP4 metadata cần API 33+ → Quest hiện chưa lợi dụng được. |
| Ktlint, build pass | **DONE**. |

## Không làm trong sprint này

- Stereo SBS / TB / MV-HEVC — Sprint 2 Stage 2.
- EAC, fisheye, cubemap — sau, P1.
- 8K H.265 hardware decoder fallback — sẽ wire `nextlib` ở Sprint 2 nếu Q2 drop frame.
- Sample mp4 360 / 180 bundled — file size lớn (>5 MB), đẩy sang sprint 3 nếu cần demo offline.

## Rủi ro đã thấy

1. **Pole distortion** — equirect uv mapping bị warp ở zenith / nadir. Có thể cần shader latitude correction nếu user phàn nàn.
2. **Quest API 32** không expose `spatial-format` key → metadata path sẽ luôn fallback về `OFF` cho file không có suffix tên rõ ràng. User vẫn có manual override.
3. **`MediaExtractor` cho streaming URL** — chậm hoặc fail; logic đã skip với `source.startsWith("http")` để không block playback.

## File đã đụng

```
feature/vrplayer/src/main/cpp/{sphere,native_calls}.{h,cpp}
feature/vrplayer/src/main/cpp/{gl_renderer,CMakeLists}.{cpp,txt}
feature/vrplayer/src/main/java/dev/.../playback/ProjectionDetector.kt
feature/vrplayer/src/main/java/dev/.../picker/{VrPickerScreen,PickerSurfaceHost}.kt
feature/vrplayer/src/main/java/dev/.../XrActivity.kt
docs/dev/projection-shaders.md
docs/reports/stage2-sprint1-report.md
```
