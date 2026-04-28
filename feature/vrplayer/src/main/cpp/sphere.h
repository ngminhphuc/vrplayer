#pragma once

#include <cstdint>

namespace vrplayer {

/**
 * Inside-out sphere for 360° equirectangular video. Default radius is 50 m
 * — large enough that the user feels "inside" the sphere without seeing
 * obvious tessellation. Hemisphere mode draws only the front +Z half (yaw
 * −90° → +90°).
 */
class Sphere {
public:
    static bool init();
    static void shutdown();

    enum class Mode {
        // Off — render path is the cinema cylinder.
        Off,
        // Full equirect 360°×180° dome.
        Equirect360,
        // Front-half hemisphere (180°×180°) — VR180 monoscopic.
        Hemisphere180,
    };

    static void setMode(Mode m);
    static Mode mode();

    /** [degrees] yaw rotation around Y, used by snap-front recenter. */
    static void setYawOffsetDeg(float deg);

    static void draw(uint32_t externalOesTexId, const float* proj,
                     const float* view, const float* texMatrix);
};

}  // namespace vrplayer
