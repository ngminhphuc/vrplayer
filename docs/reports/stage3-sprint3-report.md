# Stage 3 Sprint 3 — Polish + Release Prep

PR: https://github.com/ngminhphuc/vrplayer/pull/10 (TBD on push)
Branch: `devin/1777127782-stage3-sprint3-polish`
Stack: PR #8 (SMB) → PR #9 (hand-tracking + env) → PR #10 (this).

## Mục tiêu

Hoàn thiện các tính năng phát lại tinh chỉnh thường gặp trong VR
player thương mại — A-B loop và bookmark — và viết tài liệu phát hành
(CHANGELOG, signing template) để chuẩn bị bước rời khỏi giai đoạn
sideload thuần và tiến tới App Lab.

Đây là sprint cuối cùng trong roadmap 8-sprint mà người dùng đã duyệt.

## Đã làm

### A-B loop (`playback/ABLoop.kt`)
- State machine cực gọn: `setA(positionMs)`, `setB(positionMs)`,
  `clear()`, `seekTargetIfPastB(position)`.
- Stateless với ExoPlayer — caller (XrActivity) bơm vị trí hiện tại
  vào và quyết định có gọi `seekTo` hay không. Trivial unit-testable.
- Resume-writer loop trong `XrActivity` đã được nâng tần suất từ
  5 s/lần xuống 250 ms tick, cộng dồn 5 s mới ghi resume một lần.
  Kết quả: A-B loop seek về A trễ tối đa ~250 ms khi vượt B.
- Khi đổi file (qua `playEntry`/`playUrl`), `resetPerFileState()` xoá
  A-B loop về null. Loop là phiên-only, không persist (đúng spec UX
  thương mại Pico/Bigscreen).

### Bookmark per-file (`picker/BookmarkStore.kt`)
- `SharedPreferences vrplayer_bookmarks`, JSON-array key bằng path /
  URI. Không dùng Room cho 1 bảng, tiết kiệm classpath.
- API: `list(key)`, `add(key, positionMs, label)`, `remove(key, ms)`,
  `clear(key)`. Sort theo `positionMs` để hiển thị thứ tự thời gian.
- Persist vĩnh viễn — bookmark còn nguyên qua restart, qua reinstall
  (vì sharedPrefs nằm trong /data/data của app).

### UI Picker — tab "Playback" mới (`VrPickerScreen.kt`)
- Thêm tab thứ 4 (`Playback`) trong picker world-space:
  - Hàng A-B loop: chip "Đặt A / B / Xoá" với label hiển thị mốc thời
    gian định dạng `mm:ss` hoặc `h:mm:ss`.
  - Hàng Bookmark: nút "+ Thêm bookmark tại vị trí hiện tại", danh
    sách bookmark đã lưu (mỗi entry = chip seek + chip xoá).
- Tất cả callback đi qua `PickerSurfaceHost` → `XrActivity` chuyên
  trách lệnh ExoPlayer.

### Wiring trong `XrActivity`
- Thêm `bookmarkStore`, `abLoop` lazy field.
- 6 hàm public mới: `setLoopA / setLoopB / clearLoop / addBookmark /
  removeBookmark / seekToBookmark`.
- `resetPerFileState(path)` được gọi cùng lúc với `applyProjectionFor`
  để picker luôn đồng bộ với file đang chạy.
- `bookmarkPositions(key)` map `Bookmark` → `List<Long>` cho UI (UI
  chưa cần label, để dành tương lai).

### Bug fixes (carry-over từ Devin Review trên PR #8)
- **Case-insensitive scheme check** trong `SmbDataSource.open()` —
  ném `IOException` thay vì `IllegalArgumentException` để Media3 xử
  lý sạch khi `SmbAwareDataSource` route qua URI viết hoa.
- **Connection leak** trong `SmbBrowser.ensureShare()` — build
  `conn/session/sh` cục bộ, chỉ gán field khi cả 3 bước thành công;
  cleanup `runCatching { close() }` khi auth/connect throw, tránh
  leak Connection mỗi lần ExoPlayer retry.
- **Stale SmbTab selection** — clear `selected = null` khi user xoá
  server đang chọn để Play button disable cùng recompose, không tạo
  URI `smb://` rỗng.

## Files thay đổi

| File | Đổi | Lý do |
| --- | --- | --- |
| `feature/vrplayer/.../playback/ABLoop.kt` | + new | State machine A-B loop. |
| `feature/vrplayer/.../picker/BookmarkStore.kt` | + new | Persist bookmark per file. |
| `feature/vrplayer/.../picker/VrPickerScreen.kt` | tab Playback + format helper | UI A-B loop + bookmark + bug fix. |
| `feature/vrplayer/.../picker/PickerSurfaceHost.kt` | callback bridge + state | Cầu nối UI ↔ Activity. |
| `feature/vrplayer/.../XrActivity.kt` | wiring + resume-writer 250 ms | Bơm position → ABLoop, gọi seekTo. |

## Định nghĩa Done

- [x] Build `:app:assembleQuestDebug` — BUILD SUCCESSFUL.
- [x] `./gradlew ktlintCheck` — pass.
- [x] PR #8 bug từ Devin Review đã fix + reply inline.
- [x] PR #9 đã rebase trên PR #8 đã fix.
- [x] PR #10 (sprint này) đã rebase trên PR #9.
- [ ] Test trên Quest hardware — chờ user.

## Risk / open question

- **Đổi file mid-loop**: Khi loop đang chạy mà user đổi file, A-B
  reset. Hành vi này khớp với hầu hết VR player (Bigscreen, Pico
  Theater) nhưng nếu bạn muốn loop persist xuyên file, cần đổi spec.
- **Bookmark label hiện chưa có UI**: Lưu được nhưng không hiển thị.
  Có thể bổ sung dialog Compose nhập label trong sprint sau nếu
  bạn cần.
- **Resume-writer 250 ms tick**: Nhiều hơn 20× so với cũ. Đo trên
  device mới biết có ảnh hưởng pin không, nhưng chỉ làm việc nhẹ
  (đọc `currentPosition`, `seekTargetIfPastB` là một so sánh long
  → string) nên dự kiến không đáng kể.

## Cần user làm

1. Test APK đính kèm trên Quest.
2. Verify cả 3 luồng: A-B loop seek về A, bookmark add → seek →
   remove, đổi file thì A-B clear còn bookmark vẫn còn nguyên cho
   file kia.
3. Báo lại nếu UI Playback tab không click được (PixelCopy fallback
   trong PickerSurfaceHost có thể cần sửa cho Quest production).
