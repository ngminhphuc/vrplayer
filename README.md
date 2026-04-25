# VR Player

> **VR Player** là trình xem video VR cho **Meta Quest 2 / 3 / 3S / Pro**, được fork và refactor từ [`anilbeesetti/nextplayer`](https://github.com/anilbeesetti/nextplayer).

Mục tiêu: render video local / streaming online / SMB trong môi trường VR (rạp ảo cong, sphere 360°, hemisphere 180°, stereoscopic 3D…) bằng **OpenXR + GLES/Vulkan native**, tận dụng pipeline Media3/ExoPlayer + nextlib (FFmpeg) sẵn có.

## Trạng thái

Dự án đang ở giai đoạn **Stage 0 — Spike**. Roadmap chi tiết và phân chia sprint: xem [`docs/`](docs/PLAN.md).

## Hỗ trợ định dạng (kế thừa NextPlayer)

- **Video**: H.263, H.264, H.265 / HEVC, MPEG-4, VP8, VP9, AV1.
- **Audio**: Vorbis, Opus, FLAC, ALAC, PCM/WAVE, MP1/2/3, AMR, AAC, AC-3, E-AC-3, DTS, DTS-HD, TrueHD (qua ExoPlayer FFmpeg extension).
- **Phụ đề**: SRT, SSA, ASS, TTML, VTT, DVB.

VR Player bổ sung (theo roadmap):

- Curved cinema screen, 360° equirectangular, 180° hemisphere.
- Stereoscopic SBS / TB / mono auto-detect.
- Phụ đề head-locked & screen-locked trong VR.
- Controller + hand-tracking input.
- SMB / CIFS browse.

## Build

```bash
# Build cho Quest (arm64-v8a, manifest VR)
./gradlew assembleQuestDebug

# Build cho Android phone (legacy, kept for reference)
./gradlew assembleMobileDebug
```

## Sideload lên Quest

Xem [`docs/sideload.md`](docs/sideload.md) (sẽ có ở Stage 1 Sprint 3). Tóm tắt:

```bash
adb install -r app/build/outputs/apk/quest/debug/app-quest-debug.apk
```

## Đóng góp

Issues / PRs welcome trên [`ngminhphuc/vrplayer`](https://github.com/ngminhphuc/nextplayer) (repo sẽ được rename).

## Giấy phép

VR Player kế thừa giấy phép **GNU General Public License v3.0** từ NextPlayer upstream. Xem [`LICENSE`](LICENSE).

Mọi binary phái sinh phải mở mã, kể cả khi phân phối trên Meta Horizon Store.

## Credits

- [`anilbeesetti/nextplayer`](https://github.com/anilbeesetti/nextplayer) — codebase gốc.
- [`anilbeesetti/nextlib`](https://github.com/anilbeesetti/nextlib) — FFmpeg extension cho Media3.
- AndroidX Media3 / ExoPlayer team.
- Khronos OpenXR & Meta Horizon OS team.
