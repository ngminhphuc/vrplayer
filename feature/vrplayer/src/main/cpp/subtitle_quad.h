#pragma once

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <cstdint>

namespace vrplayer {

/**
 * Head-locked subtitle panel — a 2.0 m × 0.3 m flat quad floating
 * at Y=0.6 m, Z=-3.0 m (just below the cinema cylinder, ~3 m in
 * front of the user). Samples an external OES texture filled by
 * the Compose subtitle host on the Kotlin side. No hit-testing
 * (subtitles are passive output).
 */
class SubtitleQuad {
public:
    static bool init();
    static void shutdown();

    static void setVisible(bool visible);
    static bool visible();

    /** User-controlled vertical offset in meters. Added to the quad's
     *  default Y position so people with large fonts can push the
     *  subtitle further down out of the cinema screen. */
    static void setVerticalOffset(float offsetMeters);

    /** Render with the subtitle's external OES texture id + transform. */
    static void draw(uint32_t externalOesTexId, const float* proj,
                     const float* view, const float* texMatrix);
};

}  // namespace vrplayer
