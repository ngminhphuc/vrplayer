# Stage 1 — Sprint 3 · Báo cáo

> Trạng thái: **Code-complete trên CI Linux. Cần test trên Quest hardware.**
> PR: cập nhật khi tạo (stack trên Sprint 2).
> Branch: `devin/<ts>-stage1-sprint3-streaming`.

## Tóm tắt

Sprint này hoàn tất phần MVP: phát URL streaming (HLS / DASH / HTTP), sleep timer, proximity auto-pause, volume hardware key, và doc sideload tiếng Việt + EN.

## Đã làm

### Streaming + URL history
- `picker/UrlHistoryStore.kt` — danh sách URL gần nhất (`SharedPreferences("vrplayer_urls")`), bounded 32 entry, dùng `\u0001` làm separator để cho phép URL có khoảng trắng / `;`.
- Tab **Network** trong `VrPickerScreen.kt`: `BasicTextField` (Quest virtual keyboard sẽ tự bật khi focus), button "Phát URL", LazyColumn lịch sử click-to-replay.
- `XrActivity.playUrl(url)` — `setMediaItem(MediaItem.fromUri)`, dùng lại resumeStore cho cả URL (key = path string).

### Sleep timer
- `playback/SleepTimer.kt` — `Handler.postDelayed`, single-shot, cancelable. Khi fire: `playWhenReady = false` + reset UI badge.
- Tab **Settings** trong picker: chọn 0 (off) / 15 / 30 / 60 phút.

### Proximity auto-pause
- `playback/ProximityAutoPause.kt` — `SensorManager.TYPE_PROXIMITY`. Khi rời cảm biến (tháo headset) → `playWhenReady = false`. Đeo lại → resume nếu chính nó pause. Cờ `pausedByProximity` tránh double-resume khi user pause thủ công trước đó.
- Wired vào `XrActivity.onResume/onPause`.

### Volume hardware key
- `XrActivity.onKeyDown` chặn `KEYCODE_VOLUME_UP/DOWN`, gọi `volumeDelta(±0.1)` (đã có sẵn từ Sprint 1).

### Picker tabs
- `VrPickerScreen.kt` refactor: enum `PickerTab { Local, Network, Settings }`, header có tab chip row.
- `PickerSurfaceHost`: thêm state `urlHistory`, `sleepMinutes`, callback `onPickUrl`, `onUrlSubmit`, `onSleepTimerArm`.

### Sideload + docs
- `docs/sideload.md` — tiếng Việt + English, từ Developer Mode → ADB → install → controls → troubleshooting.
- `docs/reports/stage1-sprint3-report.md` (file này).

## Build & lint

| Lệnh | Kết quả |
|---|---|
| `./gradlew :feature:vrplayer:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL (sau `ktlintFormat` cho 2 violation) |

## Definition of Done — đối chiếu

| Tiêu chí | Trạng thái |
|---|---|
| Phát URL HLS test trên Quest | **CHƯA TEST**. Code path đã wire qua ExoPlayer source factory mặc định. |
| APK cài qua SideQuest hoạt động | Build green; cài đặt user-side (xem `docs/sideload.md`). |
| Manifest VR pass `OVRDeviceCheck` | Manifest đã đầy đủ từ Stage 0 + permissions Sprint 2. |
| Sleep timer 15/30/60, auto-pause | **DONE** (code), cần test thực. |
| Volume hardware key | **DONE** (code). |
| Loại bỏ link Google Play / F-Droid / Izzy | Đã làm trong Stage 0 Sprint 1 (rebrand). |
| README sideload | **DONE** (`docs/sideload.md`). |

## Không làm trong sprint này

- Bàn phím ảo riêng cho VR (xài system Quest IME) — đẩy sang Stage 3 nếu user phàn nàn.
- Room schema cho URL history — `SharedPreferences` đủ cho 32 entry; không cần Room.
- Splash icon Quest 1024 × 1024 — phụ thuộc design asset; gắn vào Stage 3 Sprint 3 lúc submit Store.
- Demo video — chưa có thiết bị để quay.
- App Lab submission — Stage 3 Sprint 3.

## Rủi ro đã thấy

1. **Quest virtual keyboard trong VR**: `BasicTextField` dùng default IME; trên Quest nên tự gọi system overlay. Nếu không, fallback Stage 3: built-in laser-pointer keyboard component.
2. **Proximity sensor**: Quest 3 đã đổi mounting detection sang internal flag — nếu `TYPE_PROXIMITY` không có, listener `onSensorChanged` không bao giờ gọi → app vẫn chạy bình thường. Đã `null-check` `sensor`.
3. **`KeyEvent` xuyên `NativeActivity`**: Quest có thể consume volume keys ở system level trước khi tới app. Phải verify.

## File đã đụng

```
feature/vrplayer/src/main/java/dev/.../picker/{VrPickerScreen,PickerSurfaceHost,UrlHistoryStore}.kt
feature/vrplayer/src/main/java/dev/.../playback/{SleepTimer,ProximityAutoPause}.kt
feature/vrplayer/src/main/java/dev/.../XrActivity.kt
docs/sideload.md
docs/reports/stage1-sprint3-report.md
```
