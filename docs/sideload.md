# Cài đặt VR Player trên Meta Quest (sideload)

> **Audience**: Tester / sideload user. Không phải hướng dẫn nộp App Lab — xem `docs/stage3-sprint3.md` cho việc đó.

## Yêu cầu

- Meta Quest 2 / 3 / Pro chạy Horizon OS ≥ 49.
- Tài khoản Meta đã bật **Developer Mode** ([hướng dẫn của Meta](https://developer.oculus.com/documentation/native/android/mobile-device-setup/)).
- Máy tính có ADB:
  - Windows: `Meta Quest Developer Hub` hoặc `platform-tools` của Android SDK.
  - macOS / Linux: `brew install android-platform-tools` hoặc tải từ Google.
- Cáp USB-C dữ liệu (không phải dây sạc thuần).

## Bước 1 — Bật Developer Mode

1. Cài app **Meta Quest** trên điện thoại.
2. Vào _Devices → Headset Settings → Developer Mode → On_.
3. Tạo "Developer Organization" trên https://developer.oculus.com/manage/organizations/create/ nếu chưa có.

## Bước 2 — Kết nối ADB

```bash
# Linux/macOS
adb devices
# Trong headset xuất hiện popup "Allow USB debugging" → tick "Always allow" → OK
adb devices
```

Nếu chưa thấy device, kiểm tra rule udev (Linux):
```bash
sudo bash -c 'echo "SUBSYSTEM==\"usb\", ATTR{idVendor}==\"2833\", MODE=\"0666\"" > /etc/udev/rules.d/51-oculus.rules'
sudo udevadm control --reload && sudo udevadm trigger
```

## Bước 3 — Cài APK

Tải APK debug từ **Releases** của repo (`vrplayer-quest-debug.apk`) hoặc tự build:

```bash
git clone https://github.com/ngminhphuc/vrplayer.git
cd vrplayer
./gradlew :app:assembleQuestDebug
adb install -r app/build/outputs/apk/quest/debug/app-quest-debug.apk
```

## Bước 4 — Khởi chạy

1. Đeo headset → vào **App Library**.
2. Đổi filter **Apps** → **Unknown Sources**.
3. Chọn **VR Player Debug**.
4. Cấp quyền `READ_MEDIA_VIDEO` khi được hỏi (Quest hiển thị system overlay).

## Bước 5 — Đưa video vào headset

Có 3 cách:

| Cách | Thao tác |
|---|---|
| ADB push | `adb push myvideo.mp4 /sdcard/Movies/` |
| MTP | Cắm cáp, bấm "Allow data access" trên headset → kéo file vào folder `Movies/` |
| Streaming URL | Mở app → tab **Network** → dán link HLS / DASH / RTSP → "Phát URL" |

## Mapping điều khiển (Quest Touch)

| Phím | Chức năng |
|---|---|
| Trigger (`R/L`) | Play/pause; khi picker mở: chọn item dưới ray |
| Grip (`R/L`) | Giữ + di chuyển tay → kéo màn rạp; thả → lưu vị trí |
| Thumbstick X | Tua ±10 giây (rate-limit 250 ms) |
| Thumbstick Y | Tăng/giảm âm lượng ExoPlayer |
| Menu | Tap: bật/tắt picker. Khi picker đang mở → bấm lần 2 để đóng + recenter view. |
| Volume rocker | Tăng/giảm âm lượng ExoPlayer (cùng action với thumbstick Y) |

## Sleep timer

Tab **Settings** trong picker → chọn **15m / 30m / 60m / Off**. Khi hết giờ, app pause player tự động.

## Auto-pause khi tháo headset

Cảm biến proximity của Quest sẽ tự pause player khi user tháo headset, và resume khi đeo lại. Không cần cấu hình.

## Troubleshooting

| Triệu chứng | Cách xử lý |
|---|---|
| App vào VR mode rồi đứng đen | `adb logcat -s VrPlayer/XrActivity AndroidRuntime *:F` — gửi log lên Issues. |
| Picker không hiện text, chỉ thấy quad đen | Kiểm tra log `picker will not render` — Quest VR có thể chặn `WindowManager.addView`; mình sẽ chuyển sang fallback `PixelCopy` ở patch sau. |
| `adb install` báo `INSTALL_FAILED_OLDER_SDK` | Quest 2 đôi khi vẫn ở Android 10 — đảm bảo `minSdk = 29` (mặc định OK). |
| Frame drop khi tua trong file 4K | Disable HDR trong cài đặt video; FFR validate sẽ tới sprint sau. |

---

# Install VR Player on Meta Quest (sideload, English)

Same steps in English:

1. Enable Developer Mode in the Meta Quest mobile app (Settings → Headset → Developer Mode → On). Create a Developer Organization if you don't have one.
2. Connect via USB-C, accept "Allow USB debugging" inside the headset.
3. `adb install -r app-quest-debug.apk`.
4. In headset: App Library → filter to "Unknown Sources" → "VR Player Debug".
5. Grant `READ_MEDIA_VIDEO`. Push videos to `/sdcard/Movies/` or paste a streaming URL on the **Network** tab.

Controls match the table above. Sleep timer + auto-pause on un-mount work out of the box.
