#pragma once

#include <cstdint>

namespace vrplayer {

/**
 * Cylindrical cinema screen — a slice of a vertical cylinder with samplerExternalOES.
 * Centered on the user (or the user-defined anchor); curvature, height, and
 * horizontal arc are configurable per-frame so the user can grip-drag-rescale.
 *
 * In the local reference space, the screen sits at +/- {radius * sin(arc/2)}
 * along x, and -radius along z (so it's in front of the user, curving toward them).
 */
class Screen {
public:
    static bool init();
    static void shutdown();

    static void setTransform(float radius, float arcRadians, float height,
                             float yaw, float pitch, float yOffset, float zOffset);

    static void draw(uint32_t externalOesTexId, const float* proj,
                     const float* view, const float* texMatrix);

    /** Default values used until the user grip-drags. ~120° wrap, 1.6 m tall, 3 m away. */
    static void resetTransform();
};

}  // namespace vrplayer
