# Stage 3 — Sprint 1 · SMB / CIFS browse & stream

> Mục tiêu: cho phép **truy cập media trên server SMB/NAS** trực tiếp từ Quest, lưu credentials an toàn.

## Phạm vi

### In-scope
- Tích hợp thư viện SMB client cho Android (`jcifs-ng` hoặc `smbj` — chọn theo license, ưu tiên `smbj` Apache 2.0).
- UI thêm/xoá server: hostname, share, username, password, domain (option).
- Lưu credential vào **EncryptedSharedPreferences** hoặc Android Keystore.
- Browse cây thư mục SMB trong picker world-space (sprint Stage 1-2 đã port).
- Stream file qua `SmbDataSource` cho ExoPlayer (thực hiện qua `DataSource.Factory` custom).
- Fallback: nếu file ≤ 200 MB cho phép cache local; lớn hơn stream trực tiếp.
- Hỗ trợ SMBv2 và SMBv3 (mặc định v3).

### Out-of-scope
- Jellyfin / Plex (P1, ngoài giai đoạn 1).
- WebDAV / FTP (sau).
- Discovery NetBIOS (P1).

## Deliverables

1. APK với tab "Network → SMB" trong picker.
2. Doc `docs/dev/smb-integration.md`.
3. Test plan với NAS giả lập (Samba server Docker `dperson/samba`).
4. Demo: thêm server → browse → phát file 1080p H.264 từ NAS.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S3-1.1 | Thêm dep `smbj` + integration với `core:data` Repository | 1d |
| S3-1.2 | Schema Room mới: `SmbServerEntity` (id, host, share, user, encryptedPwd, domain, lastUsed) | 0.5d |
| S3-1.3 | UI Compose form thêm/sửa server (panel world-space) | 1d |
| S3-1.4 | EncryptedSharedPreferences cho password | 0.5d |
| S3-1.5 | `SmbDataSource implements DataSource` cho ExoPlayer | 1.5d |
| S3-1.6 | Browse async: list folders/files với pagination + thumbnail nếu có | 1d |
| S3-1.7 | Cache thumbnail vào disk `/data/data/.../cache/smb` | 0.5d |
| S3-1.8 | Test rig: docker compose Samba với 5 file test | 0.5d |
| S3-1.9 | Test trên Quest (cùng Wi-Fi 5 GHz) — đo throughput | 1d |
| S3-1.10 | Doc + demo | 0.5d |

## Definition of Done

- [ ] Thêm 1 server, browse share, phát 1 file 1080p H.264 mượt qua Wi-Fi 5 GHz.
- [ ] Reconnect tự động khi mất kết nối tạm thời (~5s).
- [ ] Password không xuất hiện trong logcat / không lưu plaintext.
- [ ] Throughput ≥ 25 MB/s trên LAN gigabit.
- [ ] Ktlint, build pass.

## Rủi ro

- Một số NAS chạy SMBv1 → cần config bật/tắt protocol tối thiểu.
- ExoPlayer không thân thiện với non-seekable random access → cần implement đúng `open/read/close` của `DataSource`.
