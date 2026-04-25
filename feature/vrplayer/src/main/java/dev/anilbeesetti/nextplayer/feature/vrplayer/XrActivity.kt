package dev.anilbeesetti.nextplayer.feature.vrplayer

import android.app.NativeActivity
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

/**
 * Stage 0 — Sprint 1 spike entry point.
 *
 * Inherits from [NativeActivity] so the OpenXR / GLES render loop runs in C++ via
 * `android_main()` (see `src/main/cpp/main.cpp`). The Kotlin side owns the
 * ExoPlayer instance and provides a [Surface] that the native side reads from
 * as a `GL_TEXTURE_EXTERNAL_OES` sampler.
 *
 * This is intentionally minimal: no UI, no picker, no settings. The goal is to
 * prove the pipeline `ExoPlayer → SurfaceTexture → GL external texture → quad
 * inside an OpenXR session` end-to-end on a Meta Quest device.
 *
 * NOT YET VERIFIED ON DEVICE — see `docs/stage0-sprint1.md` for the verification
 * checklist that requires real Quest hardware.
 */
class XrActivity : NativeActivity() {

    private var player: ExoPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.tag(TAG).i("XrActivity#onCreate")
    }

    override fun onResume() {
        super.onResume()
        ensurePlayer()
        // Loop the bundled sample (a real picker arrives in Stage 1 Sprint 2).
        val sample = "asset:///sample/spike0_sample.mp4"
        player?.run {
            setMediaItem(MediaItem.fromUri(sample))
            prepare()
            playWhenReady = true
        }
    }

    override fun onPause() {
        player?.playWhenReady = false
        super.onPause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun ensurePlayer() {
        if (player != null) return
        val exo = ExoPlayer.Builder(this).build()
        player = exo
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        videoSurface?.release()
        videoSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    /**
     * Called from native code (`Java_…_acquireVideoSurface`) once the GLES
     * context exists and an external OES texture id has been allocated. The
     * native side passes its own texture id; we wrap it in a [SurfaceTexture]
     * and return a [Surface] that ExoPlayer pushes frames to.
     */
    @Suppress("unused") // called from JNI
    fun acquireVideoSurface(textureId: Int): Surface {
        val st = SurfaceTexture(textureId).apply {
            // Sized at first frame; ExoPlayer adjusts via setDefaultBufferSize when known.
            setDefaultBufferSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
            setOnFrameAvailableListener {
                // Native render thread polls hasNewFrame() instead of using a callback;
                // keeping this listener present silences "no listener" warnings.
            }
        }
        val surface = Surface(st)
        surfaceTexture = st
        videoSurface = surface
        player?.setVideoSurface(surface)
        Timber.tag(TAG).i("acquireVideoSurface(textureId=%d) ok", textureId)
        return surface
    }

    /**
     * Called from the native render thread once per frame to pull the latest
     * decoded frame into the bound external OES texture. Must run on the GL
     * thread that owns the texture.
     */
    @Suppress("unused") // called from JNI
    fun updateTexImage() {
        surfaceTexture?.updateTexImage()
    }

    /**
     * Native side queries this to compute the texture-coord transform matrix
     * (handles vertical flip / NPOT / crop applied by MediaCodec).
     */
    @Suppress("unused") // called from JNI
    fun getTransformMatrix(out: FloatArray) {
        surfaceTexture?.getTransformMatrix(out)
    }

    companion object {
        private const val TAG = "VrPlayer/XrActivity"
        private const val DEFAULT_WIDTH = 1920
        private const val DEFAULT_HEIGHT = 1080

        init {
            // Loaded by NativeActivity via android.app.lib_name meta-data, but
            // we keep an explicit System.loadLibrary as a safety net for cases
            // where android_main() needs JNI symbols before NativeActivity
            // finishes initialising.
            System.loadLibrary("vrplayer")
        }
    }
}
