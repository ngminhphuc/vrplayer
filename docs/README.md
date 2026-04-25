# NextPlayer XR — Tài liệu kế hoạch chuyển đổi VR

Thư mục này chứa kế hoạch chuyển NextPlayer (Android video player) thành **trình xem VR cho Meta Quest** (Quest 2 / 3 / 3S / Pro).

## Cấu trúc

| File | Nội dung |
|---|---|
| [`PLAN.md`](./PLAN.md) | Kế hoạch tổng thể, quyết định kiến trúc, lộ trình |
| [`stage0-sprint1.md`](./stage0-sprint1.md) | Spike: OpenXR + ExoPlayer texture POC |
| [`stage1-sprint1.md`](./stage1-sprint1.md) | MVP Cinema: rạp ảo + controller |
| [`stage1-sprint2.md`](./stage1-sprint2.md) | MVP Cinema: world-space picker + SAF + resume |
| [`stage1-sprint3.md`](./stage1-sprint3.md) | MVP Cinema: streaming + manifest VR + sideload |
| [`stage2-sprint1.md`](./stage2-sprint1.md) | VR Formats: 360 / 180 mono + free look |
| [`stage2-sprint2.md`](./stage2-sprint2.md) | VR Formats: stereo SBS/TB + phụ đề head-locked |
| [`stage3-sprint1.md`](./stage3-sprint1.md) | Network & UX: SMB / CIFS |
| [`stage3-sprint2.md`](./stage3-sprint2.md) | Network & UX: hand-tracking + environment preset |
| [`stage3-sprint3.md`](./stage3-sprint3.md) | Network & UX: PiP-on-wrist + A-B loop + App Lab submit |

## Tóm tắt quyết định

- **Engine**: native Kotlin + OpenXR (Chiến lược A).
- **Nền tảng**: 100% Meta Quest. Không dual-build mobile ở giai đoạn 1.
- **Phân phối**: Sideload → App Lab → Horizon Store. Loại bỏ link tới các store khác.
- **Nội dung**: video local + streaming online + SMB.
- **Đồ hoạ**: rạp ảo đơn giản (sphere skybox + curved/flat quad).
- **Multi-user**: chưa làm trong giai đoạn 1.

Xem [`PLAN.md`](./PLAN.md) để biết chi tiết kiến trúc, ràng buộc giấy phép GPLv3, và roadmap.
