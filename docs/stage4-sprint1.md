# Stage 4 Sprint 1 — Polish + App Lab prep

> Ngoài 8-sprint roadmap ban đầu. User yêu cầu "tiếp tục" sau khi
> Stage 3 hoàn thành. Sprint này dồn các việc cần làm trước khi nộp
> App Lab + nâng cao trải nghiệm phản hồi cho user.

## Mục tiêu

- About / credits screen với credit GPL + NextPlayer.
- Trạng thái player (buffering / error / ended) hiển thị banner
  trong picker thay cho màn đen lặng lẽ.
- Privacy policy doc (App Lab requirement).
- GitHub Actions CI build + ktlint, upload APK artifact 14 ngày.

## Scope

**IN**:
- Tab "About" trong picker (read-only).
- `PlayerStatus` sealed interface + listener trong `XrActivity`.
- `StatusBanner` Composable trong picker hiển thị buffering/error.
- `docs/PRIVACY.md` boilerplate.
- `.github/workflows/build.yml` CI workflow.

**OUT** (để dành sprint kế):
- Subtitle external (`.srt`/`.vtt`) head-locked — cần native quad
  mới + shader mới + parser. Quá lớn cho 1 sprint.
- App icon thiết kế riêng.
- ProGuard config cho release build.

## Deliverables

- `feature/vrplayer/.../picker/PlayerStatus.kt` mới.
- `feature/vrplayer/.../picker/VrPickerScreen.kt`: tab About +
  StatusBanner.
- `feature/vrplayer/.../picker/PickerSurfaceHost.kt`: bridge
  playerStatus state.
- `feature/vrplayer/.../XrActivity.kt`: `Player.Listener` map sang
  `PlayerStatus`, gọi `pickerHost.setPlayerStatus`.
- `docs/PRIVACY.md`.
- `.github/workflows/build.yml`.
- `docs/reports/stage4-sprint1-report.md`.

## DoD

- Build `:app:assembleQuestDebug` pass.
- `ktlintCheck` pass.
- Picker render được tab About không crash.
- ExoPlayer error → Banner hiển thị error code + message thay vì
  bặt vô âm tín.
- CI workflow pass trên GitHub khi merge (chờ user verify).

## Rủi ro

- StatusBanner overflow nếu error message dài. Đã test với
  `errorCodeName + message` ngắn; nếu dài có thể cần truncate.
- CI `setup-android@v3` thay đổi flag SDK packages. Đã chốt phiên
  bản `34.0.0 build-tools` + `ndk;25.2.9519653` khớp `app/build.gradle.kts`.
