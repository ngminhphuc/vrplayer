#include "stereo.h"

#include <atomic>

namespace vrplayer {

namespace {
// Cross-thread: written from JNI (UI thread) via Stereo::setMode, read
// from native render thread in Stereo::uvScaleOffset / Stereo::mode.
std::atomic<Stereo::Mode> sMode{Stereo::Mode::Mono};
}  // namespace

void Stereo::setMode(Mode m) { sMode.store(m, std::memory_order_release); }
Stereo::Mode Stereo::mode() { return sMode.load(std::memory_order_acquire); }

void Stereo::uvScaleOffset(int eyeIndex, float out[4]) {
    // Default: identity (full texture for both eyes).
    out[0] = 1.f;  out[1] = 1.f;  out[2] = 0.f;  out[3] = 0.f;

    bool isLeft = (eyeIndex == 0);
    switch (sMode.load(std::memory_order_acquire)) {
        case Mode::Mono:
            return;
        case Mode::SbsLeftRight:
            out[0] = 0.5f;
            out[2] = isLeft ? 0.f : 0.5f;
            return;
        case Mode::SbsRightLeft:
            out[0] = 0.5f;
            out[2] = isLeft ? 0.5f : 0.f;
            return;
        case Mode::TbLeftRight:
            out[1] = 0.5f;
            // GL UV origin is bottom-left. ExoPlayer texMatrix already
            // applies a Y-flip so "top half" of the original frame ends up
            // in v ∈ [0, 0.5] after composition. The left eye is the top
            // half of the source → v in [0, 0.5] post-flip.
            out[3] = isLeft ? 0.f : 0.5f;
            return;
        case Mode::TbRightLeft:
            out[1] = 0.5f;
            out[3] = isLeft ? 0.5f : 0.f;
            return;
    }
}

}  // namespace vrplayer
