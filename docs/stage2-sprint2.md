# Stage 2 — Sprint 2 · Stereoscopic + Auto-detect + Phụ đề head-locked

> Mục tiêu: hỗ trợ **video 3D Side-by-Side / Top-Bottom** trên cả flat & spherical projection, **phụ đề trong VR** không gây chóng mặt.

## Phạm vi

### In-scope
- Stereo layout: SBS-LR, SBS-RL, TB-LR, TB-RL, mono.
- Áp dụng được cho cả cinema flat, 360 sphere, 180 hemisphere → **tổ hợp 3 projection × 5 stereo**.
- Render 2 mắt: chia UV trong shader (left half cho mắt trái, right half cho mắt phải, v.v.).
- Auto-detect stereo qua:
  - MP4 `st3d` box.
  - MKV `Projection` element.
  - Tên file (`*_LR_180`, `*_TB`, `*_SBS`).
- Manual override trong control bar.
- **Phụ đề head-locked** (cố định trước mặt) hoặc **screen-locked** (gắn vào màn ảo).
- Render phụ đề:
  - Nguồn: pipeline subtitle hiện có của ExoPlayer (SRT/ASS/VTT).
  - Vẽ vào SubtitleView → texture → quad layer riêng (sharp text).
  - Tham số: distance 1.0–3.0 m, vertical offset, kích thước, opacity.

### Out-of-scope
- 3D depth subtitle (P1, sau).
- MV-HEVC.
- Translation.

## Deliverables

1. APK hỗ trợ stereo + phụ đề head-locked.
2. 5 clip test mỗi tổ hợp projection × stereo.
3. Doc `docs/dev/stereo-layouts.md` + `docs/dev/subtitle-rendering.md`.
4. Demo video.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S2-2.1 | Shader stereo: chia UV theo `eyeIndex` uniform | 1d |
| S2-2.2 | UI manual stereo selector trong control bar | 0.5d |
| S2-2.3 | Auto-detect: parser `st3d`, MKV Projection, regex tên file | 1.5d |
| S2-2.4 | Subtitle render-to-texture: bridge `SubtitleView` → `Bitmap` → `Surface` | 1d |
| S2-2.5 | Quad layer riêng cho subtitle (sharp), config head-locked vs screen-locked | 1d |
| S2-2.6 | Setting panel: distance, height, scale, opacity | 0.5d |
| S2-2.7 | Test 10 clip stereo (SBS/TB, 180/360/cinema), kiểm tra mắt mỏi | 1d |
| S2-2.8 | Edge case: file SSA có override style + animation | 0.5d |
| S2-2.9 | Demo + doc | 0.5d |

## Definition of Done

- [ ] Phát đúng 3D trên Q3 (parallax cảm nhận được, không ghost).
- [ ] Phụ đề SRT/ASS hiển thị sharp, không jitter, vẫn theo timing.
- [ ] Auto-detect đúng ≥ 80% clip test.
- [ ] Trong stereo mode, phụ đề không bị chia 2 (render đúng cho cả 2 mắt).
- [ ] Ktlint, build pass; demo nộp.
