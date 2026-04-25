#pragma once

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <openxr/openxr.h>

namespace vrplayer {

/** Renders a thin laser line emitted from a controller aim pose. */
class Pointer {
public:
    static bool init();
    static void shutdown();

    /** Draws a 1.5 m ray segment from `aim`. The visualization is intentionally
     *  cheap — a unit-length line buffer transformed by the controller pose. */
    static void draw(const XrPosef& aim, const float* proj, const float* view);
};

}  // namespace vrplayer
