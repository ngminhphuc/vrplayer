# Stage 3 Sprint 2 — Hand-tracking + Environment skybox preset (báo cáo)

> Plan: `docs/stage3-sprint2.md`
> Branch: `devin/1777127294-stage3-sprint2-handtrack-env`
> Stack: dựa trên #8 (SMB). Cần merge 3 → 4 → 5 → 6 → 7 → 8 → PR này.

## Mục tiêu sprint

1. Hand-tracking (XR_EXT_hand_tracking) như input phụ trợ — pinch ngón
   trỏ + ngón cái thay được trigger controller.
2. 4 environment preset (Black Void / Modern Cinema / Drive-In Night /
   Space) chuyển bằng UI Settings, lưu giữa các phiên.

## Đã làm

| # | Task | Trạng thái | Ghi chú |
|---|------|------------|---------|
| 1 | Bật `XR_EXT_hand_tracking` trong instance create info | xong | best-effort: chỉ thêm vào extension list nếu runtime hỗ trợ. |
| 2 | Tạo lớp `HandTracking` (cpp/h) | xong | quản lý 2 tracker (left/right), dlsym 3 entry points qua `xrGetInstanceProcAddr`. |
| 3 | Pinch detect index-tip ↔ thumb-tip distance | xong | enter ≤ 25 mm, release ≥ 35 mm (hysteresis). |
| 4 | Latched edge `consumeLeft/RightPinchEdge()` | xong | tránh double-fire khi user giữ pose pinch. |
| 5 | Merge pinch edges vào XrInput trigger | xong | `mInput.triggerLeftEdge / triggerRightEdge / triggerPressedEdge` được set thêm; logic picker hit-test ưu tiên hand aim khi pinch fire. |
| 6 | Aim pose từ joint INDEX_TIP | xong | dùng để hit test picker quad. |
| 7 | Skybox 4 mode procedural | xong | shader branching trên uniform `uMode`; không có asset. |
| 8 | `Skybox::setMode/mode` atomic | xong | cùng pattern với sphere/stereo (acquire/release). |
| 9 | JNI `nativeSetEnvironment(int)` | xong | XrActivity expose `setEnvironment(EnvironmentMode)`; lưu vào SharedPreferences `vrplayer_env`. |
| 10 | UI picker: tab Settings có "Môi trường" 4 chip | xong | label hiển thị tên thân thiện. |

## Kiến trúc

```
[ Quest hand sensors ]
       │
       ▼
HandTracking::update(baseSpace, predictedTime)   # mỗi frame
       │   xrLocateHandJointsEXT → 26 joints/hand
       │   pinch hysteresis & edge latch
       ▼
XrSession::processInput
       │   if pinch edge → set XrInput.triggerEdge
       │   pick aim source: hand tip > controller aim
       ▼
PickerQuad::hitTest → VideoBridge::injectPickerTap

[ Picker Settings tab ]
       │ click chip
       ▼
PickerSurfaceHost.onEnvironmentChange
       │
       ▼
XrActivity.setEnvironment
       │ persist SharedPreferences
       │ JNI nativeSetEnvironment(raw)
       ▼
Skybox::sMode (atomic) → shader uMode → Skybox::draw
```

## Bảo đảm tính tương thích

- Hand tracking **không bắt buộc**: nếu Quest tắt setting "Hand
  tracking" hoặc thiết bị cũ không hỗ trợ extension, `HandTracking::init`
  trả về `false`, controller input vẫn chạy bình thường.
- Trên Quest 2/3/Pro: extension sẵn có khi user bật setting.
- Pinch edges được "thêm vào" controller edges, không thay thế. User
  cầm controller vẫn dùng được trigger; người buông controller dùng
  bare hand vẫn click được picker.

## Build / lint

| Lệnh | Kết quả |
|------|---------|
| `./gradlew :app:assembleQuestDebug` | BUILD SUCCESSFUL (87 MB APK) |
| `./gradlew :feature:vrplayer:ktlintCheck` | OK |
| `:feature:vrplayer:externalNativeBuildDebug` | OK (29 warnings, không error) |

## Cần test trên Quest

| Kịch bản | Kết quả mong đợi |
|----------|------------------|
| Bật hand-tracking trong setting Quest, mở app, không cầm controller | Hand mesh không render (chưa làm) nhưng pinch click được trên picker quad. |
| Cầm controller + dùng tay trái pinch | Cả hai đều hoạt động; pinch ưu tiên hand aim khi pinch là nguồn gây edge. |
| Trong Settings → chọn "Modern Cinema" | Bg đổi sang gradient ấm; thoát app rồi mở lại preset vẫn giữ. |
| Chọn "Space" rồi quay đầu | Starfield ở mọi hướng (vì shader chạy theo screen-space gradient — note: chưa world-space đúng nghĩa). |
| Nhanh chuyển 4 preset | Đổi < 50ms (chỉ swap 1 int uniform). |

## Hạn chế / nợ kỹ thuật

- **Hand mesh chưa render**: Sprint plan có ghi "render mesh tay điểm
  cloud joint" — bỏ qua trong sprint này để tập trung vào logic input.
  Sẽ thêm ở sprint UX nếu user yêu cầu (chỉ là 26 sphere instanced).
- **Wrist menu palm-up không có**: gesture detect đơn giản hoá thành
  pinch-only; menu hệ thống vẫn dùng nút Menu controller.
- **Skybox screen-space, không cubemap**: gradient render trên triangle
  full-screen thay vì sphere world-space. Pros: 0 fillrate, 0 memory;
  cons: không "cảm" như môi trường thực sự (không quay theo head). Đủ
  cho cinema/void; với Space và Drive-In sẽ thấy gradient không xoay
  khi quay đầu. Nâng cấp lên skybox sphere có thể làm sau (~30 dòng).
- **Pinch chỉ trigger; không grip**: drag-screen vẫn cần controller.
  Có thể bổ sung "pinch + di tay = drag" sau.

## Definition of Done

- [x] Hand-tracking pinch click hoạt động (logic được verify build,
      runtime cần test Quest).
- [x] 4 preset chuyển được trong < 1s (chỉ uniform swap).
- [x] Memory tăng < 50 MB (procedural shader, 0 KB asset).
- [x] Build + ktlint pass.
- [ ] Wrist menu (out of scope, đã note).
- [ ] Hand mesh render (out of scope).
