# Projection shaders — Stage 2 Sprint 1

## Hình chiếu hỗ trợ

| Mode | Geometry | Coverage | UV mapping |
|---|---|---|---|
| `OFF` (Cinema) | Cylinder ~3 m radius, 120° arc | Front-only | `u = arcParam`, `v = vertical` |
| `EQUIRECT_360` | Inside-out sphere, r = 50 m | 360° × 180° | `u = (yaw + π) / 2π`, `v = (pitch + π/2) / π` |
| `HEMISPHERE_180` | Inside-out front half | 180° × 180° | UV stretched to fill front hemisphere |

Mesh: 48 rings × 96 segments (=9 217 verts, 27 648 indices). Inverted winding so that the inside faces the user.

## Shader (sphere.cpp)

```glsl
// Vertex
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
uniform mat4 uMvp;
uniform mat4 uTexMatrix;
out vec2 vUv;
void main() {
    vUv = (uTexMatrix * vec4(aUv, 0.0, 1.0)).xy;
    gl_Position = uMvp * vec4(aPos, 1.0);
}

// Fragment
#extension GL_OES_EGL_image_external_essl3 : require
in vec2 vUv;
uniform samplerExternalOES uTex;
out vec4 fragColor;
void main() {
    fragColor = texture(uTex, vUv);
}
```

`uTexMatrix` đến từ `SurfaceTexture.getTransformMatrix` (đã apply Y-flip cho ExoPlayer).

## Auto-detect

`ProjectionDetector.detect(source)` (Kotlin) chạy 2 pass:

1. **Filename heuristics** — tên file chứa `_360` / `_eq` / `vr360` → `EQUIRECT_360`. `_180` / `vr180` → `HEMISPHERE_180`. Order: 180 trước 360 vì `vr180_eq` mang nghĩa half-sphere chứ không phải full.
2. **MP4 metadata** — `MediaExtractor` đọc track video, kiểm tra `spatial-format` key (API 33+). Quest hiện tại API 32 nên thường rơi về heuristics; structure đã có sẵn để bật khi Meta nâng base SDK.

User có thể override trong tab **Settings** của picker (Auto / Cinema / 360° / 180°). Override applied ngay trên file đang phát.

## Snap front

Mode 360 cho phép xoay đầu tự do. Khi user tap **Snap front (recenter)**, `Sphere::setYawOffsetDeg(0)` được gọi → reset rotation về thẳng trước. Implementation thực tế là rotate view matrix theo Y axis trước khi nhân với projection.

## Hiệu năng

- 8K@60 H.265: Q3 đủ băng thông decoder, Q2 dự kiến cần fallback xuống 5.7K hoặc software (`nextlib` từ NextPlayer).
- Cylinder vs sphere: sphere đắt hơn ~10× về vertex count, nhưng mesh static + GL VAO nên overhead negligible. Bottleneck thực tế là decoder + pixel push qua `samplerExternalOES`.

## Out-of-scope (stage 2 sprint 2)

- Stereo SBS / TB
- EAC, fisheye, cubemap
- Hi-resolution dual-source streams (left+right cùng track)
