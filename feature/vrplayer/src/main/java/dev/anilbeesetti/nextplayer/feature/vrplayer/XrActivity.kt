package dev.anilbeesetti.nextplayer.feature.vrplayer

import android.app.NativeActivity
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.MediaStoreScanner
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.PickerSurfaceHost
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.ResumeStore
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.UrlHistoryStore
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.VideoEntry
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProximityAutoPause
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resumeStore by lazy { ResumeStore(this) }
    private val urlStore by lazy { UrlHistoryStore(this) }
    private val pickerHost by lazy { PickerSurfaceHost(this) }
    private val sleepTimer by lazy {
        SleepTimer {
            player?.playWhenReady = false
            pickerHost.setSleepMinutes(0)
            // Sleep timer pause is intentional. If proximity later sees a
            // remount it must NOT auto-resume — clear our "we paused it"
            // flag so the player stays paused until the user re-arms or
            // hits play.
            proximity.clearPausedFlag()
        }
    }
    private val proximity by lazy {
        ProximityAutoPause(
            context = this,
            isPlaying = { player?.playWhenReady == true },
            pausePlayer = { player?.playWhenReady = false },
            resumePlayer = { player?.playWhenReady = true },
        )
    }
    private var resumeWriterJob: Job? = null
    private var currentPath: String? = null

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
        configurePicker()
        loadInitialMedia()
        startResumeWriter()
        proximity.start()
    }

    override fun onPause() {
        proximity.stop()
        player?.playWhenReady = false
        currentPath?.let { resumeStore.save(it, player?.currentPosition ?: 0L) }
        super.onPause()
    }

    private fun configurePicker() {
        pickerHost.onPick = { entry -> playEntry(entry) }
        pickerHost.onPickUrl = { url -> playUrl(url) }
        pickerHost.onUrlSubmit = { url ->
            urlStore.push(url)
            pickerHost.setUrlHistory(urlStore.list())
        }
        pickerHost.onSleepTimerArm = { minutes ->
            sleepTimer.arm(minutes)
            pickerHost.setSleepMinutes(sleepTimer.armedMinutes)
        }
        pickerHost.setUrlHistory(urlStore.list())
        pickerHost.setSleepMinutes(sleepTimer.armedMinutes)
        mainScope.launch {
            val list = MediaStoreScanner.scan(this@XrActivity)
            pickerHost.setEntries(list)
            pickerHost.setLastPlayed(resumeStore.lastPlayedPath())
        }
    }

    private fun playUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        currentPath = trimmed
        resumeStore.setLastPlayed(trimmed)
        urlStore.push(trimmed)
        pickerHost.setUrlHistory(urlStore.list())
        runOnUiThread {
            player?.run {
                setMediaItem(MediaItem.fromUri(trimmed))
                prepare()
                seekTo(resumeStore.load(trimmed))
                playWhenReady = true
            }
        }
        Timber.tag(TAG).i("play url: %s", trimmed)
    }

    /** Pick the most recent file the user played; fall back to the bundled
     *  sample so the headset always has *something* on screen. */
    private fun loadInitialMedia() {
        val resumePath = resumeStore.lastPlayedPath()
        val resumePos = resumePath?.let { resumeStore.load(it) } ?: 0L
        val uri = resumePath ?: "asset:///sample/spike0_sample.mp4"
        currentPath = resumePath
        player?.run {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            seekTo(resumePos)
            playWhenReady = true
        }
    }

    private fun playEntry(entry: VideoEntry) {
        val resume = resumeStore.load(entry.path)
        currentPath = entry.path
        resumeStore.setLastPlayed(entry.path)
        runOnUiThread {
            player?.run {
                setMediaItem(MediaItem.fromUri(entry.path))
                prepare()
                seekTo(resume)
                playWhenReady = true
            }
        }
        Timber.tag(TAG).i("play: %s @ %d", entry.path, resume)
    }

    private fun startResumeWriter() {
        resumeWriterJob?.cancel()
        resumeWriterJob = mainScope.launch {
            while (isActive) {
                delay(5_000L)
                val p = currentPath ?: continue
                val pos = player?.currentPosition ?: continue
                resumeStore.save(p, pos)
            }
        }
    }

    override fun onDestroy() {
        sleepTimer.cancel()
        resumeWriterJob?.cancel()
        mainScope.cancel()
        pickerHost.releaseSurface()
        releasePlayer()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Quest mirrors hardware volume rocker to KEYCODE_VOLUME_UP/DOWN even
        // in VR mode; we hook them so the user doesn't have to surface the
        // system volume HUD just to nudge a video.
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeDelta(VOLUME_KEY_STEP)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeDelta(-VOLUME_KEY_STEP)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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

    /** JNI: trigger pressed — toggle play/pause. Marshalled to main thread. */
    @Suppress("unused")
    fun togglePlayPause() {
        runOnUiThread {
            player?.let {
                it.playWhenReady = !it.playWhenReady
                if (!it.playWhenReady) {
                    // Manual pause: clear proximity's "we paused it" flag
                    // so a subsequent remount doesn't auto-resume against
                    // the user's intent.
                    proximity.clearPausedFlag()
                }
            }
        }
    }

    /** JNI: thumbstick X past threshold — seek by [deltaMs]. */
    @Suppress("unused")
    fun seekDelta(deltaMs: Int) {
        runOnUiThread {
            player?.let {
                val target = (it.currentPosition + deltaMs).coerceAtLeast(0L)
                it.seekTo(target)
            }
        }
    }

    /** JNI: thumbstick Y — change ExoPlayer volume by [delta] (clamped 0..1). */
    @Suppress("unused")
    fun volumeDelta(delta: Float) {
        runOnUiThread {
            player?.let {
                val v = (it.volume + delta).coerceIn(0f, 1f)
                it.volume = v
            }
        }
    }

    /** JNI: persist screen transform from grip-drag. Stored via SharedPreferences
     *  (DataStore wiring lands in Stage 1 Sprint 2 alongside the picker). */
    @Suppress("unused")
    fun persistScreenTransform(
        radius: Float,
        arc: Float,
        height: Float,
        yaw: Float,
        yOffset: Float,
        zOffset: Float,
    ) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_RADIUS, radius)
            .putFloat(KEY_ARC, arc)
            .putFloat(KEY_HEIGHT, height)
            .putFloat(KEY_YAW, yaw)
            .putFloat(KEY_Y_OFFSET, yOffset)
            .putFloat(KEY_Z_OFFSET, zOffset)
            .apply()
    }

    /** JNI: load persisted transform during native init.
     *  Returns 6 floats: radius, arc, height, yaw, yOffset, zOffset.
     *  Matches the native [XrSession::applyScreenTransform] signature. */
    @Suppress("unused")
    fun loadScreenTransform(): FloatArray {
        val sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return floatArrayOf(
            sp.getFloat(KEY_RADIUS, 3f),
            sp.getFloat(KEY_ARC, 2.0944f),
            sp.getFloat(KEY_HEIGHT, 1.6f),
            sp.getFloat(KEY_YAW, 0f),
            sp.getFloat(KEY_Y_OFFSET, 1.5f),
            sp.getFloat(KEY_Z_OFFSET, 0f),
        )
    }

    /** Suppress AudioManager unused-import lint — reserved for Stage 1 Sprint 2
     *  global volume integration. */
    @Suppress("unused")
    private fun audioStream(): Int = AudioManager.STREAM_MUSIC

    // ---- Picker surface bridge (JNI) ----

    @Suppress("unused")
    fun acquirePickerSurface(textureId: Int): Surface = pickerHost.acquirePickerSurface(textureId)

    @Suppress("unused")
    fun pickerWidth(): Int = pickerHost.width

    @Suppress("unused")
    fun pickerHeight(): Int = pickerHost.height

    @Suppress("unused")
    fun updatePickerTexImage(): Boolean = pickerHost.updateTexImage()

    @Suppress("unused")
    fun getPickerTransformMatrix(out: FloatArray) = pickerHost.getTransformMatrix(out)

    /** Native side: laser-pointer ray vs picker-quad intersect → (u,v). */
    @Suppress("unused")
    fun injectPickerTap(u: Float, v: Float) {
        runOnUiThread { pickerHost.injectTap(u, v) }
    }

    companion object {
        private const val TAG = "VrPlayer/XrActivity"
        private const val DEFAULT_WIDTH = 1920
        private const val DEFAULT_HEIGHT = 1080
        private const val VOLUME_KEY_STEP = 0.1f
        private const val PREFS = "vrplayer_screen"
        private const val KEY_RADIUS = "radius"
        private const val KEY_ARC = "arc"
        private const val KEY_HEIGHT = "height"
        private const val KEY_YAW = "yaw"
        private const val KEY_Y_OFFSET = "y_off"
        private const val KEY_Z_OFFSET = "z_off"

        init {
            // Loaded by NativeActivity via android.app.lib_name meta-data, but
            // we keep an explicit System.loadLibrary as a safety net for cases
            // where android_main() needs JNI symbols before NativeActivity
            // finishes initialising.
            System.loadLibrary("vrplayer")
        }
    }
}
