# Privacy policy — VR Player

VR Player là phần mềm mã nguồn mở (GPLv3). Tài liệu này tuyên bố
toàn bộ dữ liệu mà ứng dụng đọc, ghi, hoặc gửi đi.

Áp dụng cho tất cả bản phát hành trên nhánh `main` và App Lab.

## 1. Dữ liệu chúng tôi thu thập

**Không thu thập gì.** Cụ thể:

- Không có analytics (Firebase, Crashlytics, Mixpanel, Amplitude, ...).
- Không có ad SDK.
- Không có tracking SDK.
- Không có server backend của VR Player.
- Không có account / login.
- Không gửi log đi đâu cả. Log chỉ ghi vào logcat của thiết bị.

## 2. Dữ liệu lưu trên thiết bị

Toàn bộ dưới đây nằm trong sandbox `dev.ngminhphuc.vrplayer.quest.*`,
chỉ ứng dụng VR Player có quyền đọc, không sync ra ngoài.

| Dữ liệu | Lưu ở đâu | Mục đích |
| --- | --- | --- |
| Vị trí phát dở | `SharedPreferences vrplayer_resume` | Resume file đang xem khi mở lại. |
| Lịch sử URL | `SharedPreferences vrplayer_url_history` | Gợi ý URL streaming đã dùng. |
| Server SMB / NAS | `EncryptedSharedPreferences vrplayer_smb` (AES-256-GCM, key trong Android Keystore) | Lưu host/share/user/password để kết nối lại. |
| Bookmark | `SharedPreferences vrplayer_bookmarks` | Đánh dấu thời điểm trong file. |
| Tuỳ chọn môi trường | `SharedPreferences vrplayer_env` | Skybox preset. |

User có thể xoá toàn bộ dữ liệu trên qua **Settings → Apps → VR
Player Debug → Storage → Clear data** ở Quest, hoặc gỡ cài đặt app.

## 3. Quyền (permissions) ứng dụng yêu cầu

| Quyền | Tại sao | Khi nào dùng |
| --- | --- | --- |
| `READ_EXTERNAL_STORAGE` (Android ≤ 12) | Quét file video local | Khi mở picker tab "Local". |
| `READ_MEDIA_VIDEO` (Android 13+) | Quét file video local | Như trên. |
| `INTERNET` | Phát URL streaming + SMB | Khi user nhập URL hoặc thêm SMB server. |
| `ACCESS_NETWORK_STATE` | Báo lỗi rõ ràng khi mất Wi-Fi | Trong khi streaming. |
| `oculus.permission.HAND_TRACKING` (optional) | Pinch gesture | Khi user đã bật hand-tracking trong Quest Settings. |

Ứng dụng KHÔNG yêu cầu các quyền: vị trí, máy ảnh, micro, danh bạ,
SMS, Bluetooth, ghi storage.

## 4. Trẻ em

Ứng dụng không hướng tới trẻ em dưới 13 tuổi. Không thu thập dữ liệu
cá nhân của bất kỳ ai.

## 5. Thay đổi tài liệu

Mọi thay đổi sẽ được commit vào `docs/PRIVACY.md` của repo và đính
trong từng release notes.

## 6. Liên hệ

- Issue tracker: https://github.com/ngminhphuc/vrplayer/issues
- Mã nguồn: https://github.com/ngminhphuc/vrplayer

Cập nhật lần cuối: theo commit chạm file này (`git log docs/PRIVACY.md`).
