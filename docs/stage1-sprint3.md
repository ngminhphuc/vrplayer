# Stage 1 — Sprint 3 · Streaming online + Manifest VR + Sideload-ready

> Mục tiêu: hoàn tất **MVP** với khả năng phát URL streaming, build flavor `quest` chuẩn manifest, có hướng dẫn sideload.

## Phạm vi

### In-scope
- Phát URL streaming: HTTP/HTTPS, HLS, DASH, RTSP — tái dùng cấu hình ExoPlayer hiện có.
- World-space input nhập URL: bàn phím ảo Quest hoặc panel input Compose; lưu lịch sử URL.
- Build flavor `quest` chính thức:
  - Chỉ ABI `arm64-v8a`.
  - Manifest VR (xem PLAN §3.3).
  - `targetSdk = 32`, `minSdk = 29`.
  - Loại bỏ link Google Play / F-Droid / IzzyOnDroid khỏi UI và `AboutScreen`.
- Splash/launch icon Quest (1024×1024 transparent) + thumbnail Store.
- Sleep timer + auto-pause khi tháo headset (proximity sensor `SensorManager.TYPE_PROXIMITY` của Quest).
- Volume control map vào hardware key Quest.
- README sideload hướng dẫn `adb install`, SideQuest.

### Out-of-scope
- App Lab submission (stage 3 sprint 3).
- 360 / stereo (stage 2).

## Deliverables

1. APK release-debug ký bằng key debug, sẵn sàng sideload.
2. `docs/sideload.md` hướng dẫn người dùng cuối.
3. `CHANGELOG.md` cho v0.1.0-mvp.
4. Video demo: phát URL HLS test stream + phát file local + sleep timer.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S1-3.1 | URL input panel (Compose) với keyboard ảo hoặc hardware Bluetooth | 1d |
| S1-3.2 | Tab "Network" trong picker, lưu lịch sử URL trong Room | 0.5d |
| S1-3.3 | Cấu hình ExoPlayer source factory cho HLS/DASH/RTSP đã có; verify trong VR | 0.5d |
| S1-3.4 | Build flavor `quest`: tách `app/build.gradle.kts` productFlavors | 1d |
| S1-3.5 | Manifest VR + intent-filter `com.oculus.intent.category.VR` | 0.5d |
| S1-3.6 | Loại bỏ string / asset link tới các store ngoài; rebrand splash | 1d |
| S1-3.7 | Sleep timer + auto-pause proximity | 0.5d |
| S1-3.8 | Volume hardware key Quest (`KEYCODE_VOLUME_UP/DOWN`) → ExoPlayer volume | 0.5d |
| S1-3.9 | `docs/sideload.md` viết tiếng Việt + EN | 0.5d |
| S1-3.10 | CHANGELOG, demo, ktlint | 0.5d |

## Definition of Done

- [ ] Phát thành công URL HLS test (ví dụ `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`) trên Quest.
- [ ] APK cài qua SideQuest hoạt động, không có nút launch trong launcher 2D mà nằm trong "Unknown Sources" của Quest.
- [ ] Manifest VR pass `OVRDeviceCheck`.
- [ ] Sleep timer 15/30/60 phút, auto-pause khi tháo headset.
- [ ] Không còn link tới Google Play/F-Droid/IzzyOnDroid trong UI.
- [ ] README sideload có ảnh / GIF.
