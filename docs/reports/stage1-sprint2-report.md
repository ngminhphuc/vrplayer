# Stage 1 — Sprint 2 · Báo cáo

> Trạng thái: **Code-complete trên CI Linux. Cần test trên Quest.**
> PR: cập nhật khi tạo (stack trên PR Sprint 1).
> Branch: `devin/<ts>-stage1-sprint2-picker` (rebased onto Sprint 1).

## Tóm tắt

Sprint này thêm **picker world-space** vào VR Player: panel 1.2 × 0.8 m phía trước, render Compose UI vào `Surface`, ray-cast trigger để chọn file, resume position cho từng file. Build sạch, ktlint pass.

## Đã làm

### Compose picker UI
- `picker/VrPickerScreen.kt` — `MaterialTheme(darkColorScheme)` + `LazyColumn` với row 88 dp, font 22 sp+, contrast cao trên Black Void.
- `picker/VideoEntry.kt`, `picker/MediaStoreScanner.kt` — query `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`, sort theo `DATE_ADDED DESC`, không dùng Room (giữ feature module nhẹ).
- `picker/ResumeStore.kt` — SharedPreferences cho `<path → positionMs>` và `_last_played`.
- `picker/PickerSurfaceHost.kt` — `ComposeView` + invisible `WindowManager` window, lock canvas vẽ vào `Surface(SurfaceTexture)`, inject `MotionEvent` từ controller hit-test.

### Native quad layer
- `picker_quad.{h,cpp}` — flat quad `1.2 × 0.8 m @ z = -1.4 m`, `samplerExternalOES`, ray-vs-plane hit-test trả `(u, v)`.
- `gl_renderer.cpp::renderEye` — vẽ picker giữa screen và pointer, depth-test on.
- `xr_session.cpp::processInput` — `menu tap`: toggle picker (lần đầu sẽ acquire surface). `trigger`: nếu picker mở và ray hit → inject tap + đóng picker, ngược lại → toggle play/pause.

### XrActivity rewire
- `onResume`: scan MediaStore (IO dispatcher), `lastPlayedPath()` → seek resume, fallback bundled sample.
- `playEntry`: load resume cho file đó, set `_last_played`, `runOnUiThread { player.setMediaItem }`.
- 5 s ticker (`mainScope`): persist `currentPosition`. `onPause` flush sync.
- 7 JNI sink mới cho native: `acquirePickerSurface`, `pickerWidth`, `pickerHeight`, `updatePickerTexImage`, `getPickerTransformMatrix`, `injectPickerTap`.

### Build infra
- `feature/vrplayer/build.gradle.kts`: bật `compose = true`, plugin `composeCompiler`, dependencies `androidx.compose.bom`, `compose.ui`, `material3`, `iconsExtended`.
- `AndroidManifest.xml`: `READ_MEDIA_VIDEO` (API 33+) + legacy `READ_EXTERNAL_STORAGE` (API ≤ 32).

### Docs
- `docs/dev/world-space-ui.md` — kiến trúc Compose-to-Surface, ray-cast, resume.
- `docs/reports/stage1-sprint2-report.md` (file này).

## Build & lint

| Lệnh | Kết quả |
|---|---|
| `./gradlew :feature:vrplayer:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL (sau `ktlintFormat` cho 4 violation tự sửa) |

## Definition of Done — đối chiếu

| Tiêu chí | Trạng thái |
|---|---|
| Mở app → panel picker hiện trước mặt sharp | **CHƯA TEST** trên Quest. Code đã wire (1024 × 720 surface, render 30 FPS). |
| Click file → đóng picker → màn ảo phát file đó | Code path đầy đủ; cần đo lag thực giữa `injectPickerTap` và `setMediaItem`. |
| Đóng app, mở lại → file tiếp tục từ vị trí cũ | Test path ổn (SharedPreferences); sai số tối đa 5 s + delta `onPause` flush. |
| Permissions được prompt | **CẦN BỔ SUNG**: Sprint 2 chỉ khai báo manifest, chưa runtime-prompt. Sẽ thêm `requestPermissions` vào `onResume` ở Sprint 3 trước khi mở app lần đầu. |
| Ktlint, build, test pass | **DONE** (build/lint). Test units chưa thêm — picker UI chưa có test infra. |

## Không làm trong sprint này

- Permission runtime prompt — đẩy sang Sprint 3 (combined với SAF / streaming URL flow).
- SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`) — Sprint 3.
- Thumbnail render từ ExoPlayer 1 frame — Sprint 3 (cần render-to-bitmap pipeline).
- DataStore proto migration — Sprint 3.
- `XrCompositionLayerQuad` thật cho picker — Sprint 3.
- Animation panel mở/đóng — Sprint 3 (sẽ smooth scale/fade khi visible toggle).

## Rủi ro đã thấy

1. **`Surface.lockCanvas` cho Compose** không phải API chính thức cho ComposeView; trên một số version Compose có thể trả `Canvas` không hỗ trợ hardware. Nếu Quest crash, sẽ chuyển sang `PixelCopy.request(View, Bitmap, ...)` rồi `glTexImage2D` upload — chậm hơn nhưng ổn định.
2. **`WindowManager.addView` trên VR**: Quest VR runtime có thể không cho phép thêm window 2D. Đã wrap `try/catch`; nếu fail, picker sẽ là quad đen — log `addView failed; picker will not render`. Plan B: dùng `ViewRootImpl` thủ công.
3. **MediaStore + Quest filesystem**: Quest user thường copy file vào `Movies/` hoặc `Download/`. Chưa biết MediaStore có index folder bên ngoài hay không; nếu list rỗng, fallback SAF folder picker ở Sprint 3.

## File đã đụng

```
feature/vrplayer/build.gradle.kts
feature/vrplayer/src/main/AndroidManifest.xml
feature/vrplayer/src/main/cpp/{picker_quad,gl_renderer,xr_session,video_bridge}.{h,cpp}
feature/vrplayer/src/main/cpp/CMakeLists.txt
feature/vrplayer/src/main/java/dev/.../XrActivity.kt
feature/vrplayer/src/main/java/dev/.../picker/{VideoEntry,MediaStoreScanner,ResumeStore,VrPickerScreen,PickerSurfaceHost}.kt
docs/dev/world-space-ui.md
docs/reports/stage1-sprint2-report.md
```
