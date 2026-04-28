#pragma once

#include <cstdint>

namespace vrplayer {

/**
 * Procedural skybox / environment renderer. Drawn first each frame with
 * depth-write off so video meshes composite on top. Modes are
 * generated entirely in-shader so we don't ship cubemap assets.
 */
class Skybox {
public:
    enum class Mode : uint8_t {
        BlackVoid = 0,    // soft indigo gradient
        ModernCinema = 1, // dim warm/blue room ambience
        DriveIn = 2,      // night sky + faint ground horizon
        Space = 3,        // procedural starfield
    };

    static bool init();
    static void shutdown();
    static void draw();

    /** Called from JNI thread. Cross-thread safe (atomic). */
    static void setMode(Mode m);
    static Mode mode();
};

}  // namespace vrplayer
