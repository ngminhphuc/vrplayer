package dev.anilbeesetti.nextplayer.feature.vrplayer.playback

import java.io.File

/**
 * Stereoscopic layout of a video source. The native renderer applies a
 * UV crop per eye when sampling the external OES texture; we only need to
 * tell it which layout the source uses.
 */
enum class StereoMode(val raw: Int) {
    MONO(0),
    SBS_LR(1),
    SBS_RL(2),
    TB_LR(3),
    TB_RL(4),
}

object StereoDetector {

    private val sbsLrPatterns = listOf("_sbs", "_lr", "side-by-side", "_h_sbs")
    private val sbsRlPatterns = listOf("_rl", "_sbs_rl")
    private val tbLrPatterns = listOf("_tb", "_ou", "over-under", "_v_lr")
    private val tbRlPatterns = listOf("_tb_rl", "_ou_rl")

    fun detect(source: String): StereoMode {
        val name = File(source).nameWithoutExtension.lowercase()
        if (tbRlPatterns.any { name.contains(it) }) return StereoMode.TB_RL
        if (tbLrPatterns.any { name.contains(it) }) return StereoMode.TB_LR
        if (sbsRlPatterns.any { name.contains(it) }) return StereoMode.SBS_RL
        if (sbsLrPatterns.any { name.contains(it) }) return StereoMode.SBS_LR
        return StereoMode.MONO
    }
}
