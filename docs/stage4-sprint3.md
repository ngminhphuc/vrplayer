# Stage 4 — Sprint 3: Head-locked subtitle quad

## Mục tiêu

Render subtitle text trong VR space — không chỉ trong tab picker (sprint 4-2)
mà thực sự trên một quad nổi dưới cinema screen, luôn nhìn rõ khi xem phim.

## Phạm vi

- `subtitle_quad.{h,cpp}` — flat quad 2.0 × 0.3 m, Y=0.6, Z=-3.0, alpha-blend.
- `SubtitleSurfaceHost` Kotlin — Compose-to-Surface pattern song hành với
  `PickerSurfaceHost`, render duy nhất `Text(cue)` trên nền trong suốt.
- Bridge JNI:
  - `acquireSubtitleSurface(int)`, `updateSubtitleTexImage(): Boolean`,
    `getSubtitleTransformMatrix(float[16])`, `subtitleWidth(): Int`,
    `subtitleHeight(): Int` (Kotlin → C++).
  - `nativeSetSubtitleVisible(boolean)` (C++ → Kotlin); ẩn quad khi cue rỗng.
- `Player.Listener.onCues` đẩy text vào `SubtitleSurfaceHost` + flip native
  visibility. Wire vào `resetPerFileState` + `setExternalSubtitle` để clear.
- Render pass đặt sau picker (cùng frame) trong `GlRenderer::renderEye`.

## Definition of Done

- Build `:app:assembleQuestDebug` + `ktlintCheck` SUCCESSFUL.
- Quad render đúng vị trí khi cue text non-empty, biến mất khi rỗng.
- Alpha-blend pixel-perfect: chữ trắng nổi, không có background đen.
- Người dùng test runtime trên Quest: link SRT → cue hiện trên quad
  ngay cả khi đóng picker.

## Hạn chế đã biết

- Vị trí quad cố định (Y=0.6, Z=-3.0) — chưa tuỳ chỉnh được.
- Style ASS/SSA bị strip về plain text.
- Font size cố định 32 sp — chưa scale theo distance/preference.
- Chưa auto-scan sidecar `.srt` cùng tên video (deferred).

## Sprint kế tiếp (4-4)

- Subtitle styling preference (size, color, position).
- Auto-scan sidecar khi chọn video.
- Multi-line wrap proper (hiện tại fontSize cố định, dài quá thì cắt).
