#pragma once

#include <cstdint>

namespace vrplayer {

/** A flat 2 m × 1.13 m (16:9) cinema screen placed 3 m in front of the user. */
class Quad {
public:
    static bool init();
    static void shutdown();
    static void draw(uint32_t externalOesTexId, const float* proj,
                     const float* view, const float* texMatrix);
};

}  // namespace vrplayer
