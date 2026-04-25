# Stage 3 Sprint 1 — SMB / NAS streaming (báo cáo)

> Plan: `docs/stage3-sprint1.md`
> Branch: `devin/1777125674-stage3-sprint1-smb`
> Stack: dựa trên Stage 2 Sprint 2 (#7), cần merge 3, 4, 5, 6, 7 trước khi merge sprint này.

## Mục tiêu

Cho phép user thêm 1..N server SMB/CIFS, lưu credential mã hoá, duyệt
share và phát thẳng file lên ExoPlayer (kèm cinema/360/180 + stereo
crop đã có ở các sprint trước) mà không cần copy về thiết bị.

## Đã làm

| # | Task | Trạng thái | Ghi chú |
|---|------|------------|---------|
| 1 | smbj 0.14.0 | xong | `gradle/libs.versions.toml`, dùng cho cả browse + stream. |
| 2 | androidx.security:security-crypto 1.1.0-alpha06 | xong | `EncryptedSharedPreferences` cho password. |
| 3 | `SmbServer` data class | xong | id (UUID) + host + share + user + (optional domain). |
| 4 | `SmbServerStore` | xong | JSON list trong file `vrplayer_smb`, key `pw_<id>` cho password (mã hoá AES-256-GCM). |
| 5 | `SmbBrowser` | xong | Lazy connect/auth/connectShare, `list()` + `open()` + `close()`. |
| 6 | `SmbDataSource` | xong | `BaseDataSource` of Media3, parse `smb://host/share/path`, lookup creds, đọc `SmbFile` theo offset. |
| 7 | `SmbAwareDataSource` | xong | Wrapper chọn delegate ở `open()` time: smb→SmbDataSource, còn lại→`DefaultDataSource`. |
| 8 | Wire vào `ExoPlayer.Builder.setMediaSourceFactory` | xong | XrActivity tạo factory custom; mọi MediaItem khác giữ nguyên. |
| 9 | Tab "Smb" trong picker | xong | Form thêm server (host/share/user/pass/domain), danh sách, chọn rồi nhập path tương đối, nút Phát. |
| 10 | Conflict packaging do bcprov-jdk18on duplicate META-INF | xong | `pickFirsts` cho `META-INF/versions/9/{OSGI-INF/MANIFEST.MF,module-info.class}` ở `app/build.gradle.kts`. |

## Kiến trúc

```
PickerScreen (Smb tab)
   └─> XrActivity.onSmbAdd / onSmbRemove / onSmbPlay
         ├─> SmbServerStore (EncryptedSharedPreferences)
         └─> playUrl("smb://host/share/path")
                └─> ExoPlayer (DefaultMediaSourceFactory(SmbAwareDataSource.Factory))
                       └─> SmbAwareDataSource.open(spec)
                             └─> if scheme == smb -> SmbDataSource(store)
                                          ├─> SmbServerStore.password(server.id)
                                          └─> SmbBrowser.open(remotePath) -> SmbFile.read(buf, pos, off, n)
                                          else -> DefaultDataSource (file/asset/http(s)/content)
```

Browse chiều đầy đủ (tree view trong tab) chưa làm trong sprint này;
user phải nhập path trực tiếp (vd. `movies/dune.mkv`). Sẽ bổ sung ở
sprint UX (Stage 3 Sprint 3) hoặc patch nhanh nếu user yêu cầu.

## Bảo mật

- Password **không** lưu plaintext: `EncryptedSharedPreferences` dùng
  master key của Android Keystore (`AES256_GCM`). File `vrplayer_smb`
  trên disk là blob mã hoá; key/value cũng được mã hoá riêng (AES256_SIV
  cho key, AES256_GCM cho value).
- `SmbServer.id` là UUID độc lập với host/share/user, an toàn nếu user
  thêm 2 server cùng host khác share.
- Khi `remove()` server cũng xoá luôn key `pw_<id>`.

## Cần test trên Quest

| Kịch bản | Kết quả mong đợi |
|----------|------------------|
| Thêm server LAN (samba server trên PC), share `movies` | Hiện trong list, ID lưu được sau khi đóng app. |
| Phát file 1080p H.264 (~5 GB) qua Wi-Fi 5 GHz | Stream mượt, throughput ≥25 MB/s, không buffering quá 1 lần. |
| Sai password | Picker không crash; player hiện lỗi/quay về picker. |
| Thoát app rồi mở lại | Server vẫn còn, password không bị mất, phát lại được. |
| Mất kết nối Wi-Fi giữa chừng | ExoPlayer tự retry; close + reopen DataSource đúng. |
| Phát file local sau khi vừa phát SMB | Vẫn ok, factory route về DefaultDataSource. |

## Build / lint

| Lệnh | Kết quả |
|------|---------|
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL |
| `./gradlew ktlintCheck` | OK |
| `:feature:vrplayer:externalNativeBuildDebug` | OK (sphere/stereo atomic patches từ #6/#7 đã trong stack) |

## Hạn chế / nợ kỹ thuật

- Tab Smb là form 1-level (không có tree view); user phải nhớ path.
- Không có connection pool; mỗi lần phát mới mở connection mới.
  Không vấn đề với 1 file/thời điểm, nhưng nếu chuyển nhanh giữa
  nhiều file sẽ chậm.
- Chưa có `seek()` dispatcher tối ưu — Media3 sẽ gọi `close()+open()`
  với position mới, smbj `read()` của ta nhận `position` parameter
  nên seek thực ra không tốn thêm round-trip lớn, nhưng latency phụ
  thuộc smbj negotiation.
- jcifs-ng vs smbj: chọn smbj vì Apache 2.0 + maintained tốt hơn. Nếu
  gặp issue tương thích Windows < 10, có thể fallback jcifs-ng sau.
- Test rig Docker (samba) chưa setup — sẽ thêm vào sprint UX nếu user
  cần auto test.

## Bug từ Devin Review đã fix trong stack

Khi rebase chain qua sprint này, đã đồng bộ các fix sau từ PR upstream:

- PR #5 (sprint 3): proximity `clearPausedFlag()` được gọi từ
  SleepTimer fire + manual togglePlayPause để không auto-resume sau
  khi remount (fix race với sleep timer).
- PR #6 (sprint 1 sphere): `sMode`, `sYawOffsetRad` chuyển sang
  `std::atomic` với acquire/release.
- PR #7 (sprint 2 stereo): áp dụng cùng pattern cho `Stereo::sMode`.

## Definition of Done

- [x] User thêm/xoá server, password mã hoá.
- [x] Phát smb:// qua ExoPlayer cùng factory với file local/HTTP.
- [x] Build + ktlint pass.
- [ ] Throughput ≥ 25 MB/s trên Quest 3 + NAS LAN (cần test thật).
- [ ] Auto reconnect khi mất Wi-Fi tạm thời (cần test thật, ExoPlayer
      tự retry 3 lần default).
- [ ] Tree view + thumbnail (sẽ làm ở sprint UX).
