# Stage 2 — Sprint 1 · 360° / 180° + Free look-around

> Mục tiêu: hỗ trợ **video immersive 360° equirectangular** và **180° hemisphere** monoscopic, tự do quay đầu nhìn quanh.

## Phạm vi

### In-scope
- Mesh **sphere** (radius lớn ~50 m, inverted normal) cho 360.
- Mesh **hemisphere** cho VR180.
- Shader: equirectangular UV mapping (yaw/pitch), inverted face culling.
- Toggle view mode (cinema flat / 360 / 180) từ control bar (panel quad từ Stage 1).
- Free look (head pose tự nhiên — đã có sẵn từ OpenXR).
- "Snap front" / reset view vào cùng nút recenter.
- Auto-detect 360 / 180 nếu file có metadata `spherical-video` (mp4 box `sv3d`/`mshp`) hoặc tên file gợi ý (`*_360`, `*_180`).

### Out-of-scope
- Stereoscopic SBS/TB (sprint 2).
- Fisheye / EAC / cubemap (P1, sau).

## Deliverables

1. APK với 3 view mode hoạt động.
2. Sample 360 mp4 (5 MB) + sample 180 mp4 trong assets test.
3. Doc `docs/dev/projection-shaders.md`.
4. Demo video: cinema → 360 → 180 → recenter.

## Task chi tiết

| ID | Task | Ước lượng |
|---|---|---|
| S2-1.1 | Sphere mesh generator (segments, rings) | 0.5d |
| S2-1.2 | Hemisphere mesh (cắt sphere theo yaw -90°→+90°) | 0.5d |
| S2-1.3 | Shader equirectangular cho external OES texture | 1d |
| S2-1.4 | UI toggle view mode trong control bar | 0.5d |
| S2-1.5 | Parser metadata `sv3d`/`prhd`/`st3d` từ MP4 (qua `MediaExtractor`) | 1.5d |
| S2-1.6 | Heuristic tên file → projection guess | 0.5d |
| S2-1.7 | "Snap front" recenter cho sphere mode (zero yaw) | 0.5d |
| S2-1.8 | Test với clip thực: YouTube 360 sample, Insta360 sample | 1d |
| S2-1.9 | Tối ưu: đổi resolution texture lên ≥ 4Kx2K mà không drop frame Q3 | 1d |
| S2-1.10 | Demo + doc | 0.5d |

## Definition of Done

- [ ] Phát file 360 8K equirect trên Q3 ở 60 fps không drop.
- [ ] Phát file 180 hemisphere mượt; vùng ngoài 180° không có texture rò.
- [ ] Toggle giữa 3 mode mượt, recenter snap front đúng.
- [ ] Auto-detect đúng cho ≥ 80% sample test (10 clip).
- [ ] Ktlint, build pass.

## Rủi ro

- 8K@60 H.265 trên Q2 có thể vượt giới hạn decoder → fallback giảm xuống 5.7K hoặc software decode (`nextlib`).
- Equirectangular pole distortion cần shader latitude correction → kiểm tra mắt thường.
