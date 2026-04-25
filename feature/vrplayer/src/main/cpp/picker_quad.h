#pragma once

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <cstdint>

#include <openxr/openxr.h>

namespace vrplayer {

/**
 * World-space picker panel — a 1.2 m × 0.8 m flat quad floating ~1.4 m in
 * front of the user, sampling an external OES texture filled by the Compose
 * UI host on the Kotlin side.
 *
 * Stage 1 Sprint 2 keeps this in-scene (depth-tested) for simplicity. Stage
 * 1 Sprint 3 will promote it to an `XrCompositionLayerQuad` for sharper
 * sampling.
 */
class PickerQuad {
public:
    static bool init();
    static void shutdown();

    static void setVisible(bool visible);
    static bool visible();

    /** Render with the picker's external OES texture id + transform. */
    static void draw(uint32_t externalOesTexId, const float* proj,
                     const float* view, const float* texMatrix);

    /** Project a controller aim ray onto the picker plane. Returns true if
     *  the ray hits the quad and writes [u, v] in [0, 1]. */
    static bool hitTest(const XrPosef& aim, float* outU, float* outV);
};

}  // namespace vrplayer
