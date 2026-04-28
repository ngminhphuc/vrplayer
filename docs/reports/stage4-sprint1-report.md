# Stage 4 Sprint 1 — Polish + App Lab prep — Report

PR: https://github.com/ngminhphuc/vrplayer/pull/11 (TBD on push)
Branch: `devin/1777216130-stage4-sprint1-polish-applab`
Stack: PR #8 → #9 → #10 → #11.

## Mục tiêu

Sprint phụ ngoài roadmap 8-sprint ban đầu, sau khi user duyệt
"tiếp tục". Tập trung vào ba việc bắt buộc trước App Lab:
attribution dialog, phản hồi player UX, và CI/privacy template.

## Đã làm

### `PlayerStatus` + StatusBanner
- Sealed interface `PlayerStatus { Idle / Buffering / Playing /
  Ended / Error(message) }` thay vì để picker đoán state.
- `XrActivity` gắn `Player.Listener` lên ExoPlayer:
  - `onPlaybackStateChanged` → map sang Buffering / Playing / Ended /
    Idle.
  - `onPlayerError` → `Error("CODE: message")` với
    `error.errorCodeName` và `error.message`.
- `PickerSurfaceHost.setPlayerStatus(status)` push state qua
  `mutableStateOf<PlayerStatus>` chia sẻ với Compose.
- `StatusBanner` Composable hiển thị banner trên đầu picker khi
  buffering / ended / error. Idle/Playing không banner để picker
  trông sạch.

### Tab "About" trong picker
- Tab thứ 6 (`PickerTab.About`).
- Nội dung:
  - Tiêu đề + một câu mô tả ngắn.
  - **Tín dụng**: ghi rõ là fork phái sinh của
    `anilbeesetti/nextplayer` GPLv3, link mã nguồn.
  - Danh sách thư viện chính + giấy phép (OpenXR, Media3, smbj,
    Security-Crypto, Compose, Timber).
  - Ghi chú LICENSE/NOTICE đi kèm APK.

### `docs/PRIVACY.md`
- Tuyên bố không thu thập gì (no analytics / ad / tracking SDK /
  backend / login).
- Liệt kê 5 SharedPreferences app dùng + permission cụ thể với lý
  do từng cái.
- Hướng dẫn user clear data / uninstall.
- App Lab review yêu cầu URL privacy policy; có thể publish file
  này qua GitHub Pages của repo.

### `.github/workflows/build.yml`
- Chạy `ktlintCheck` + `:app:assembleQuestDebug` trên Ubuntu CI
  với JDK 17, Android SDK 32 + build-tools 34.0.0 + NDK
  25.2.9519653 + CMake 3.22.1 (chốt theo `app/build.gradle.kts`).
- Cache Gradle / Android cache giữa các run.
- Upload APK artifact (retention 14 ngày) để bạn tải về test
  trực tiếp từ tab "Actions" của GitHub.
- Chạy trên `push:main` và mọi PR → tự động chặn merge khi build
  hoặc lint fail.

## Files thay đổi

| File | Đổi | Lý do |
| --- | --- | --- |
| `feature/vrplayer/.../picker/PlayerStatus.kt` | + new | Sealed UI status. |
| `feature/vrplayer/.../XrActivity.kt` | thêm `Player.Listener` | Map state/error → PlayerStatus. |
| `feature/vrplayer/.../picker/PickerSurfaceHost.kt` | thêm field + setter | Bridge state vào Compose. |
| `feature/vrplayer/.../picker/VrPickerScreen.kt` | tab About + StatusBanner | UI. |
| `docs/PRIVACY.md` | + new | App Lab requirement. |
| `.github/workflows/build.yml` | + new | CI gate cho mọi PR. |
| `docs/stage4-sprint1.md` | + new | Sprint plan. |
| `docs/reports/stage4-sprint1-report.md` | + new | File này. |

## DoD

- [x] `:app:assembleQuestDebug` BUILD SUCCESSFUL.
- [x] `ktlintCheck` pass.
- [x] PR #8 case-insensitive host/share fix đã rebase vào.
- [ ] User verify CI workflow chạy đúng khi merge.
- [ ] User test runtime trên Quest sau khi merge stack.

## Risk / Open

- **Privacy policy URL**: file MD này cần chuyển thành web URL
  cho App Lab metadata. Cách đơn giản: bật GitHub Pages → expose
  `docs/PRIVACY.md` qua Jekyll mặc định. Sẽ đưa vào sprint 4-2 nếu
  bạn duyệt.
- **CI lần đầu chạy**: Có thể fail nếu Android SDK packages thay
  đổi default. Đã chốt phiên bản nhưng cần verify run đầu tiên.
- **StatusBanner overflow**: Nếu error message của ExoPlayer dài
  (network stack trace), banner có thể chiếm nhiều chiều cao.
  Hiện chưa giới hạn — sẽ truncate trong sprint sau nếu cần.

## Sprint sau (đề xuất Stage 4 Sprint 2)

- Subtitle ngoài (`.srt` / `.vtt`) head-locked: cần native quad +
  shader text mới. Chưa làm trong sprint này.
- App icon thiết kế riêng + splash screen VR.
- ProGuard config cho release build (App Lab yêu cầu APK obfuscated
  để giảm size).
- Privacy policy hosting qua GitHub Pages.
