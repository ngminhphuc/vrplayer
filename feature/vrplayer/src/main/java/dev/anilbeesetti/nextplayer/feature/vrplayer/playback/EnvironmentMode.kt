package dev.anilbeesetti.nextplayer.feature.vrplayer.playback

/**
 * Procedural environment / skybox preset rendered behind the video.
 * Mirrors `vrplayer::Skybox::Mode` in `cpp/skybox.h`. Plain shader
 * branching, no external assets.
 */
enum class EnvironmentMode(val raw: Int, val label: String) {
    BlackVoid(0, "Black Void"),
    ModernCinema(1, "Modern Cinema"),
    DriveIn(2, "Drive-In Night"),
    Space(3, "Space"),
    ;

    companion object {
        fun fromRaw(raw: Int): EnvironmentMode = values().firstOrNull { it.raw == raw } ?: BlackVoid
    }
}
