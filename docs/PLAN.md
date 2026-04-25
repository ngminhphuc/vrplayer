# Kế hoạch tổng thể — NextPlayer XR (VR cho Meta Quest)

> Tài liệu chốt phương án triển khai. Mọi sprint cụ thể nằm tại các file `docs/stage{N}-sprint{M}.md`.

## 1. Quyết định đã chốt

| Hạng mục | Lựa chọn |
|---|---|
| Mục tiêu phân phối | **Sideload / SideQuest → App Lab → Meta Horizon Store**. Loại bỏ mọi tính năng / chuỗi văn bản / link không phù hợp với điều khoản Store của Meta. |
| Nền tảng | **100% Meta Quest** (Quest 2 / 3 / 3S / Pro). Không duy trì build mobile song song trong giai đoạn 1. |
| Nguồn nội dung | **Video local** (storage / SAF) + **streaming online** (HTTP/HTTPS, HLS, DASH, RTSP) + **SMB/CIFS**. |
| Multi-user / Watch Together | **Không** trong giai đoạn 1. |
| Đồ hoạ môi trường | **Rạp ảo đơn giản**: sphere skybox + curved/flat quad cho màn ảo. Không cần asset 3D phức tạp. |
| Engine | **Native Kotlin + OpenXR** (Chiến lược A). Render bằng Vulkan (ưu tiên) hoặc OpenGL ES 3.2 fallback. |

### Lưu ý pháp lý
- Repo gốc (`anilbeesetti/nextplayer`) là **GPLv3**. Mọi binary phái sinh, kể cả khi lên Horizon Store, vẫn phải public source. Việc "bỏ điều khoản NextPlayer" được hiểu là:
  - Rebrand toàn bộ tên / icon / package / chuỗi UI để tránh nhầm lẫn với app gốc.
  - Loại bỏ link tới Google Play / F-Droid / IzzyOnDroid / Weblate trong app & store listing.
  - Giữ nguyên file `LICENSE` (GPLv3) và ghi credit về upstream trong `docs/CREDITS.md`.
- Kiểm tra điều khoản phân phối GPL của Meta Horizon Store trước khi submit (App Lab thường dễ hơn, Store cần xác nhận chính sách hiện hành).

## 2. Định danh sản phẩm (đề xuất)

- App ID: `dev.ngminhphuc.nextplayer.xr` (sửa lại sau).
- Display name: tạm `NextPlayer XR` — chốt sau khi rebrand.
- Min SDK: **29** (Quest 2 base). Target SDK: **32** trở lên (yêu cầu của Meta Store).
- ABI duy nhất: **`arm64-v8a`**.

## 3. Kiến trúc kỹ thuật

### 3.1 Module mới
- `feature:vrplayer` — Android Library + JNI.
  - Chứa `XrActivity` (kế thừa `NativeActivity` hoặc Kotlin Activity gọi OpenXR loader).
  - Renderer Vulkan/GLES qua native code (`src/main/cpp`).
  - Bridge ExoPlayer ↔ `SurfaceTexture` ↔ `GL_TEXTURE_EXTERNAL_OES` (sampler `samplerExternalOES`).
- `feature:vrui` — Compose UI cho world-space panel (picker, settings, controls), render vào `Surface` rồi map sang quad layer OpenXR.

### 3.2 Module tái sử dụng
- `core:common`, `core:data`, `core:database`, `core:datastore`, `core:domain`, `core:media`, `core:model`, `core:ui`.
- `feature:videopicker`, `feature:settings` — port logic, **viết lại UI cho world-space**.
- `feature:player` — giữ ExoPlayer pipeline, track selection, playlist, gesture sẽ được map sang controller input.

### 3.3 Build flavor
- Chỉ giữ flavor `quest` trong giai đoạn 1. Manifest VR:
  - `<uses-feature android:name="android.hardware.vr.headtracking" android:required="true" />`
  - `<category android:name="com.oculus.intent.category.VR" />`
  - `<meta-data android:name="com.oculus.supportedDevices" android:value="quest2|quest3|questpro" />`
  - `<meta-data android:name="com.oculus.handtracking.frequency" android:value="HIGH" />` (khi bổ sung hand-tracking).

### 3.4 Phân phối
- CI build APK ký bằng key Quest, upload thủ công lên SideQuest (sprint đầu) → tự động hoá qua `ovr-platform-util` khi vào App Lab.

## 4. Roadmap theo sprint

> Ước lượng 1 sprint = ~2 tuần làm việc của 1 dev fulltime. Có thể compress nếu nhiều người.

| Stage | Sprint | Mục tiêu chính | File |
|---|---|---|---|
| 0. Spike | 1 | POC: OpenXR loader + Vulkan + ExoPlayer texture quad trắng | `stage0-sprint1.md` |
| 1. MVP Cinema | 1 | Curved cinema screen + controller input + recenter + comfort 72/90 Hz | `stage1-sprint1.md` |
| 1. MVP Cinema | 2 | World-space picker (port Compose) + SAF + resume position | `stage1-sprint2.md` |
| 1. MVP Cinema | 3 | Streaming HTTP/HLS/DASH/RTSP + manifest VR + flavor quest + sideload README | `stage1-sprint3.md` |
| 2. VR Formats | 1 | 360° equirect + 180° hemisphere + free look + reset view | `stage2-sprint1.md` |
| 2. VR Formats | 2 | Stereo SBS / TB shader + auto-detect + phụ đề head-locked | `stage2-sprint2.md` |
| 3. Network & UX | 1 | SMB/CIFS browse & stream + lưu credentials | `stage3-sprint1.md` |
| 3. Network & UX | 2 | Hand-tracking + environment skybox preset | `stage3-sprint2.md` |
| 3. Network & UX | 3 | PiP-on-wrist + A-B loop + thumbnail seek + polish + App Lab submit | `stage3-sprint3.md` |

## 5. Định nghĩa "Done" chung cho mọi sprint

1. Tất cả file mới / sửa **pass `./gradlew ktlintCheck`** & build `assembleQuestDebug`.
2. APK cài được trên Quest 2 & 3 qua `adb install`, app vào được VR mode (không bị render 2D Android phone).
3. **Không drop frame** > 5% trong 60s benchmark trên Quest 2 (72 Hz) và Quest 3 (90 Hz).
4. Update `CHANGELOG.md` và file `stage{N}-sprint{M}.md` (đánh dấu task hoàn thành).
5. Có ít nhất 1 video demo (`docs/media/`) cho mỗi sprint kết thúc tính năng người dùng nhìn thấy.

## 6. Rủi ro chính cần theo dõi

- MediaCodec hardware decode không hỗ trợ MV-HEVC trên Q2 → fallback FFmpeg (nextlib) software → đo nhiệt + pin.
- 8K@60 H.265 đẩy tới giới hạn decoder Q3 → cần test sớm trong stage 2.
- Compose render-to-Surface có thể khiến text mờ nếu không bật quad layer compositor → cân nhắc subsampling / mipmap.
- ASW (Application SpaceWarp) cần engine hỗ trợ motion vectors — sẽ cân nhắc ở stage 3.
- Ký APK & policy GPL trên Horizon Store: kiểm tra trước khi submit App Lab.

## 7. Việc cần xác nhận thêm sau giai đoạn 1

- Có cần thêm Jellyfin/Plex client không?
- Voice command / hand-tracking ưu tiên cái nào?
- Có cần MV-HEVC stereo native từ camera Insta360/Apple iPhone Pro không?
