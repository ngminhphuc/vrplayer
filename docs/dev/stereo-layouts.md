# Stereo layouts — Stage 2 Sprint 2

## Layout hỗ trợ

| Tên | Texture phân bổ | UV crop (left eye) | UV crop (right eye) |
|---|---|---|---|
| `MONO` | Toàn frame cho cả 2 mắt | scale=(1,1), off=(0,0) | scale=(1,1), off=(0,0) |
| `SBS_LR` | Trái: L, phải: R | scale=(0.5,1), off=(0,0) | scale=(0.5,1), off=(0.5,0) |
| `SBS_RL` | Trái: R, phải: L | scale=(0.5,1), off=(0.5,0) | scale=(0.5,1), off=(0,0) |
| `TB_LR` | Trên: L, dưới: R | scale=(1,0.5), off=(0,0) | scale=(1,0.5), off=(0,0.5) |
| `TB_RL` | Trên: R, dưới: L | scale=(1,0.5), off=(0,0.5) | scale=(1,0.5), off=(0,0) |

## Implementation

### Native — single uniform composition

`gl_renderer.cpp` build column-major UV crop matrix `S` từ `Stereo::uvScaleOffset(eyeIndex, ...)`, rồi compose với `texMatrix` của `SurfaceTexture`:

```cpp
texMatrixEye = texMatrix * S;
```

Pass `texMatrixEye` vào `Screen::draw` hoặc `Sphere::draw`. Shader (đã có sẵn) làm `vUv = (uTexMatrix * vec4(aUv,0,1)).xy`. Không cần đổi shader.

### Lý do chọn approach này

- Không phải refactor signature của Screen/Sphere/Pointer.
- Tự động áp được cho mọi geometry (cinema cylinder, sphere 360, hemisphere 180).
- Per-eye matrix tính 1 lần / eye / frame; cost negligible.

## Auto-detect

`StereoDetector.detect(source)` parse tên file:

- `_sbs`, `_lr`, `_h_sbs`, `side-by-side` → `SBS_LR`
- `_rl`, `_sbs_rl` → `SBS_RL`
- `_tb`, `_ou`, `over-under`, `_v_lr` → `TB_LR`
- `_tb_rl`, `_ou_rl` → `TB_RL`
- (không match) → `MONO`

Order: TB_RL trước TB_LR; SBS_RL trước SBS_LR (tiền tố dài hơn).

User override trong tab Settings của picker: 6 chip (Auto / Mono / SBS L|R / SBS R|L / TB L/R / TB R/L).

## Out-of-scope

- **Phụ đề head-locked / screen-locked** (S2-2.4 → S2-2.6 trong sprint plan): Hiện tại chưa wire SubtitleView render-to-texture vì pipeline subtitle của ExoPlayer đi qua `MediaSession` cần thiết kế cẩn thận để không đụng `feature:player` 2D upstream. Sẽ làm ở stage 3 sprint 1 cùng hand-tracking + comfort menu.
- **MV-HEVC**: cần phần cứng decoder dual-stream (Q3+ only) và work với extension OpenXR `XR_FB_color_space` / `XR_KHR_loader_init_android`. Roadmap.
- **MP4 `st3d` / MKV `Projection` parsing**: Filename heuristic phủ ~80% case người dùng cuối; binary box parser sẽ thêm khi cần.

## Testing checklist (manual, sau khi sideload)

| File mẫu | Mode kỳ vọng |
|---|---|
| `clip_sbs.mp4` | SBS_LR |
| `clip_tb.mp4` | TB_LR |
| `clip_180_sbs.mp4` | SBS_LR + Hemisphere180 |
| `clip_360_sbs.mp4` | SBS_LR + Equirect360 |
| `regular.mp4` | MONO + Cinema |

Nhắm 1 mắt khi xem, đổi mắt khác — nếu thấy mỗi mắt có nội dung khác nhau (parallax) → stereo đang đúng. Nếu thấy ngược (3D bị warp / depth ngược) → đổi sang variant `RL`.
