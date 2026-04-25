#pragma once

namespace vrplayer {

/**
 * Black Void skybox — fullscreen-quad post effect rendered first with depth
 * write off. Stage 1 ships a near-pure-black gradient; future stages replace
 * with environment textures (hangar, theatre).
 */
class Skybox {
public:
    static bool init();
    static void shutdown();
    static void draw();
};

}  // namespace vrplayer
