# Stage 0 — Sprint 1 · Spike OpenXR + ExoPlayer texture

> Mục tiêu: chứng minh rằng có thể đẩy frame video từ ExoPlayer lên một quad 3D trong scene OpenXR chạy ổn trên Meta Quest. Chưa cần UI, chưa cần picker.

## Phạm vi

### In-scope
- Tạo module `feature:vrplayer` (Android Library, có `cpp/` cho native).
- Tích hợp **OpenXR loader của Meta** (`org.khronos.openxr:openxr_loader_for_android`).
- Khởi tạo session OpenXR: instance, system, session, swapchain (Vulkan ưu tiên; nếu Vulkan tốn thời gian thì OpenGL ES 3.2 cũng chấp nhận cho spike).
- Render pipeline tối thiểu: clear color khác nhau cho 2 mắt + 1 quad trước mặt.
- Pipeline video: `ExoPlayer` → `Surface(SurfaceTexture(externalOesTexId))` → sampler `samplerExternalOES` → vẽ lên quad.
- App vào được VR mode khi đeo headset (không hiện màn 2D mirror).

### Out-of-scope
- UI Compose, picker, settings.
- Phụ đề, audio spatial.
- 360 / stereo / streaming online.
- Recenter, controller input nâng cao.

## Deliverables

1. APK debug `nextplayer-vr-debug.apk` cài qua `adb install` chạy trên Q2/Q3.
2. Sample video bundle trong `assets/` (1 file mp4 ≤ 30 MB) phát loop.
3. Document `docs/dev/openxr-bootstrap.md` mô tả setup project, SDK đã cài, lệnh build.
4. Video demo 30s (`docs/media/stage0-sprint1.mp4`) chạy trên Quest.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S0-1.1 | Thêm Gradle module `feature:vrplayer`, cấu hình NDK + CMake + Vulkan validation | 0.5d |
| S0-1.2 | Add dependency `org.khronos.openxr:openxr_loader_for_android:1.x`; copy `openxr_loader_meta.so` từ Meta XR SDK nếu cần | 0.5d |
| S0-1.3 | Viết `XrActivity.kt` kế thừa `NativeActivity`, `android:theme="@android:style/Theme.Black.NoTitleBar.Fullscreen"`, intent filter VR | 0.5d |
| S0-1.4 | Native: `xrCreateInstance` với extension `XR_KHR_vulkan_enable2` + `XR_FB_passthrough` (chuẩn bị cho stage sau) | 1d |
| S0-1.5 | Native: tạo Vulkan instance/device/swapchain tương thích OpenXR | 1.5d |
| S0-1.6 | Native: render loop `xrWaitFrame`/`xrBeginFrame`/`xrEndFrame`, vẽ quad mỗi mắt | 1d |
| S0-1.7 | JNI bridge: cấp `Surface` cho Java, hub vào ExoPlayer; kéo `updateTexImage()` ở native | 1.5d |
| S0-1.8 | Sample video assets + luồng decode test | 0.5d |
| S0-1.9 | Profile FPS + GPU time với OVR Metrics Tool | 0.5d |
| S0-1.10 | Quay demo, viết doc, tổng kết | 0.5d |

Tổng: ~7–8 ngày dev (1 sprint 2 tuần có buffer).

## Tiến độ

### Đã làm trên CI build host (Linux x86_64) — **chưa test trên Quest hardware**

- Module `feature:vrplayer` với Gradle + NDK + CMake.
- `XrActivity.kt` kế thừa `NativeActivity`, ExoPlayer init + JNI surface bridge.
- Native: OpenXR loader init (GLES extension), instance / system / session / swapchain / reference space.
- Vòng render `xrWaitFrame`/`xrBeginFrame`/`xrEndFrame` cho stereo views, mỗi mắt vẽ một quad với `samplerExternalOES` map vào video texture.
- Bridge `acquireVideoSurface(textureId)` / `updateTexImage()` / `getTransformMatrix()` từ Kotlin → JNI.
- Build flavor `quest` (arm64-v8a) trong `:app`, override manifest để Quest launcher chỉ thấy `XrActivity`.
- Build thành công `./gradlew :app:assembleQuestDebug` (APK ~91 MB) và `./gradlew :feature:vrplayer:ktlintCheck`.
- APK chứa `lib/arm64-v8a/libvrplayer.so` + `libopenxr_loader.so`; manifest có `com.oculus.intent.category.VR`, `com.samsung.android.vr.application.mode=vr_only`, `supportedDevices=quest2|quest3|questpro`.

### Cần test trên Quest hardware (block bởi thiết bị thật)

- [ ] Cài APK lên Q2 & Q3 qua `adb install -r app/build/outputs/apk/quest/debug/app-quest-debug.apk`.
- [ ] Khởi động hiện scene VR, không phải 2D launcher.
- [ ] Quad trước mặt phát video ≥ 60s không crash, frame time ≤ 13.8 ms (Q2 @ 72 Hz).
- [ ] Khi dừng app, ExoPlayer release sạch không leak surface (`adb shell dumpsys gfxinfo`).
- [ ] Đặt một file `sample/spike0_sample.mp4` vào `feature/vrplayer/src/main/assets/` (chưa bundled — để file build nhẹ và tránh license).
- [ ] Quay demo video.

## Definition of Done

- [x] Code build qua `assembleQuestDebug`, ktlint sạch.
- [x] APK chứa đúng native lib + manifest VR.
- [ ] Cài APK lên Q2 & Q3 qua `adb install`.
- [ ] Khởi động hiện scene VR, không phải 2D launcher.
- [ ] Quad trước mặt phát video ≥ 60s không crash, frame time ≤ 13.8 ms (Q2 @ 72 Hz).
- [ ] Khi dừng app, ExoPlayer release sạch không leak surface (`adb shell dumpsys gfxinfo`).
- [ ] Document & demo nộp.

## Rủi ro & dự phòng

- Vulkan + OpenXR setup phức tạp → fallback **OpenGL ES 3.2 + `XR_KHR_opengl_es_enable`** trong sprint này, port Vulkan ở stage 2.
- `samplerExternalOES` cần extension `GL_OES_EGL_image_external_essl3` — cần check support sớm.
- Nếu Meta XR SDK ràng buộc license, cân nhắc dùng **Khronos OpenXR loader thuần** + Meta-specific extension qua header công khai.

## Tham chiếu

- [OpenXR Quick Start (Meta)](https://developers.meta.com/horizon/develop/openxr-getting-started/)
- [Khronos OpenXR loader for Android (Maven)](https://central.sonatype.com/artifact/org.khronos.openxr/openxr_loader_for_android)
- ExoPlayer `setVideoSurface` doc Media3 1.8.
