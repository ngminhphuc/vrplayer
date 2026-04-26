package dev.anilbeesetti.nextplayer.feature.vrplayer

import android.app.NativeActivity
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.BookmarkStore
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.MediaStoreScanner
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.PickerSurfaceHost
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.PlayerStatus
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.ResumeStore
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.SubtitleStore
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.UrlHistoryStore
import dev.anilbeesetti.nextplayer.feature.vrplayer.picker.VideoEntry
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ABLoop
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.EnvironmentMode
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProjectionDetector
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProjectionMode
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProximityAutoPause
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.SleepTimer
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.StereoDetector
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.StereoMode
import dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbServer
import dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbServerStore
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
    private val smbStore by lazy { SmbServerStore(this) }
    private val bookmarkStore by lazy { BookmarkStore(this) }
    private val subtitleStore by lazy { SubtitleStore(this) }
    private var currentCue = ""
    private val abLoop = ABLoop()
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
    private var projectionOverride: ProjectionMode? = null
    private var stereoOverride: StereoMode? = null
    private val envPrefs by lazy { getSharedPreferences("vrplayer_env", MODE_PRIVATE) }
    private var environmentMode: EnvironmentMode = EnvironmentMode.BlackVoid

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
        pickerHost.onProjectionChange = { mode -> setProjectionOverride(mode) }
        pickerHost.onStereoChange = { mode -> setStereoOverride(mode) }
        pickerHost.onSnapFront = { snapFront() }
        pickerHost.onEnvironmentChange = { mode -> setEnvironment(mode) }
        pickerHost.onSmbAdd = { host, share, user, pass, domain ->
            smbStore.add(
                SmbServer(id = "", host = host, share = share, username = user, domain = domain),
                pass,
            )
            pickerHost.setSmbServers(smbStore.list())
        }
        pickerHost.onSmbRemove = { id ->
            smbStore.remove(id)
            pickerHost.setSmbServers(smbStore.list())
        }
        pickerHost.onSmbPlay = { server, path ->
            val cleanPath = path.trim('/', '\\').replace('\\', '/')
            // Use Uri.Builder so reserved chars in filenames (#, ?, %, space)
            // get percent-encoded. Raw "smb://host/share/movie #2.mkv" would
            // be parsed by Uri.parse as fragment "2.mkv" and SmbDataSource
            // would then try to open the wrong (truncated) path.
            val builder = android.net.Uri.Builder()
                .scheme("smb")
                .authority(server.host)
                .appendPath(server.share)
            cleanPath.split('/').filter { it.isNotEmpty() }.forEach { builder.appendPath(it) }
            playUrl(builder.build().toString())
        }
        pickerHost.onSetLoopA = { setLoopA() }
        pickerHost.onSetLoopB = { setLoopB() }
        pickerHost.onClearLoop = { clearLoop() }
        pickerHost.onAddBookmark = { addBookmark() }
        pickerHost.onSeekBookmark = { ms -> seekToBookmark(ms) }
        pickerHost.onRemoveBookmark = { ms -> removeBookmark(ms) }
        pickerHost.onPickSubtitle = { launchSubtitlePicker() }
        pickerHost.onClearSubtitle = { setExternalSubtitle(null) }
        pickerHost.setProjectionMode(projectionOverride)
        pickerHost.setStereoMode(stereoOverride)
        // Restore environment preference and push to native immediately so
        // first frame after launch is in the user's chosen ambience.
        environmentMode = EnvironmentMode.fromRaw(envPrefs.getInt(KEY_ENV, 0))
        nativeSetEnvironment(environmentMode.raw)
        pickerHost.setEnvironmentMode(environmentMode)
        pickerHost.setUrlHistory(urlStore.list())
        pickerHost.setSleepMinutes(sleepTimer.armedMinutes)
        pickerHost.setSmbServers(smbStore.list())
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
        resetPerFileState(trimmed)
        applyProjectionFor(trimmed)
        runOnUiThread {
            player?.run {
                setMediaItem(buildMediaItem(trimmed))
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
            setMediaItem(buildMediaItem(uri))
            prepare()
            seekTo(resumePos)
            playWhenReady = true
        }
        // Resume must keep its last projection. Without this, a 360°/180°
        // file restored from resumeStore would silently fall back to the
        // cinema cylinder until the user re-picked a file. Also push the
        // saved bookmarks for this path into the picker so the Playback
        // tab is populated on first open instead of waiting for a re-pick.
        resumePath?.let {
            resetPerFileState(it)
            applyProjectionFor(it)
        }
    }

    private fun playEntry(entry: VideoEntry) {
        val resume = resumeStore.load(entry.path)
        currentPath = entry.path
        resumeStore.setLastPlayed(entry.path)
        resetPerFileState(entry.path)
        applyProjectionFor(entry.path)
        runOnUiThread {
            player?.run {
                setMediaItem(buildMediaItem(entry.path))
                prepare()
                seekTo(resume)
                playWhenReady = true
            }
        }
        Timber.tag(TAG).i("play: %s @ %d", entry.path, resume)
    }

    /**
     * Decides between cinema / 360 / 180 for the given source. The user's
     * manual override (set via [setProjectionOverride]) wins; otherwise
     * we delegate to [ProjectionDetector].
     */
    private fun applyProjectionFor(path: String) {
        val mode = projectionOverride ?: ProjectionDetector.detect(path)
        nativeSetProjection(mode.raw)
        if (mode != ProjectionMode.OFF) nativeSnapFront()
        applyStereoFor(path)
    }

    private fun applyStereoFor(path: String) {
        val s = stereoOverride ?: StereoDetector.detect(path)
        nativeSetStereo(s.raw)
    }

    fun setProjectionOverride(mode: ProjectionMode?) {
        projectionOverride = mode
        currentPath?.let { applyProjectionFor(it) }
    }

    fun setStereoOverride(mode: StereoMode?) {
        stereoOverride = mode
        currentPath?.let { applyStereoFor(it) }
    }

    fun snapFront() {
        nativeSnapFront()
    }

    fun setEnvironment(mode: EnvironmentMode) {
        environmentMode = mode
        nativeSetEnvironment(mode.raw)
        envPrefs.edit().putInt(KEY_ENV, mode.raw).apply()
        pickerHost.setEnvironmentMode(mode)
    }

    private external fun nativeSetProjection(mode: Int)
    private external fun nativeSnapFront()
    private external fun nativeRotateYaw(degrees: Float)
    private external fun nativeSetStereo(mode: Int)
    private external fun nativeSetEnvironment(mode: Int)

    private fun startResumeWriter() {
        resumeWriterJob?.cancel()
        resumeWriterJob = mainScope.launch {
            // 5s for resume save (low frequency, ok if we miss the last
            // half-second). 250 ms tick for A-B loop watcher so the seek
            // back is responsive without spinning the CPU.
            var sinceLastSave = 0L
            while (isActive) {
                delay(250L)
                sinceLastSave += 250L
                val p = currentPath ?: continue
                val pos = player?.currentPosition ?: continue
                // Capture the loop target so the resume-save below uses the
                // post-seek position. Without this, a tick that both saves
                // and triggers the loop persists a position past B; on next
                // launch the player would resume past the loop and skip A.
                val seeked = abLoop.seekTargetIfPastB(pos)?.also { target ->
                    player?.seekTo(target)
                }
                if (sinceLastSave >= 5_000L) {
                    resumeStore.save(p, seeked ?: pos)
                    sinceLastSave = 0L
                }
            }
        }
    }

    fun setLoopA() {
        val pos = player?.currentPosition ?: return
        abLoop.setA(pos)
        pickerHost.setAbLoop(abLoop.pointA, abLoop.pointB)
    }

    fun setLoopB() {
        val pos = player?.currentPosition ?: return
        abLoop.setB(pos)
        pickerHost.setAbLoop(abLoop.pointA, abLoop.pointB)
    }

    fun clearLoop() {
        abLoop.clear()
        pickerHost.setAbLoop(-1L, -1L)
    }

    fun addBookmark() {
        val key = currentPath ?: return
        val pos = player?.currentPosition ?: return
        bookmarkStore.add(key, pos)
        pickerHost.setBookmarks(bookmarkPositions(key))
    }

    fun removeBookmark(positionMs: Long) {
        val key = currentPath ?: return
        bookmarkStore.remove(key, positionMs)
        pickerHost.setBookmarks(bookmarkPositions(key))
    }

    fun seekToBookmark(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    private fun bookmarkPositions(key: String): List<Long> =
        bookmarkStore.list(key).map { it.positionMs }

    /** Reset session-only A-B loop, then refresh persisted bookmarks for
     *  the new path so the picker shows the right list immediately. */
    private fun resetPerFileState(path: String) {
        abLoop.clear()
        pickerHost.setAbLoop(-1L, -1L)
        pickerHost.setBookmarks(bookmarkPositions(path))
        // Push the persisted subtitle association into the picker so the
        // Subtitle tab shows whether a sidecar is linked for this file.
        pickerHost.setSubtitleUri(subtitleStore.get(path))
        pickerHost.setSubtitleCue("")
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

    /** Build a MediaItem with optional sidecar subtitle from SubtitleStore.
     *  Use this everywhere instead of MediaItem.fromUri so any caller
     *  benefits from per-file external SRT/VTT side-loading. */
    private fun buildMediaItem(uri: String): MediaItem {
        val sub = subtitleStore.get(uri)
        if (sub.isNullOrBlank()) return MediaItem.fromUri(uri)
        val ext = sub.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
            "srt", "subrip" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
            "ssa", "ass" -> androidx.media3.common.MimeTypes.TEXT_SSA
            "ttml", "xml", "dfxp" -> androidx.media3.common.MimeTypes.APPLICATION_TTML
            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        }
        val cfg = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub))
            .setMimeType(mime)
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .build()
        return MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(listOf(cfg))
            .build()
    }

    /** Open Storage Access Framework picker so the user can browse for an
     *  external SRT/VTT/ASS/TTML next to the video. We use SAF + content://
     *  URIs so we don't need MANAGE_EXTERNAL_STORAGE on Android 13+. */
    private fun launchSubtitlePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(
                android.content.Intent.EXTRA_MIME_TYPES,
                arrayOf("text/*", "application/x-subrip", "application/ttml+xml"),
            )
        runCatching { startActivityForResult(intent, REQ_PICK_SUBTITLE) }
            .onFailure { Timber.tag(TAG).e(it, "subtitle picker launch failed") }
    }

    @Deprecated("NativeActivity inherits this; use SAF result here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_SUBTITLE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            // Persist URI permission so SAF still resolves it after process
            // restart; without this the user has to re-pick on every launch.
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            setExternalSubtitle(uri.toString())
        }
    }

    fun setExternalSubtitle(subtitleUri: String?) {
        val key = currentPath ?: return
        subtitleStore.set(key, subtitleUri)
        // Push fresh state into the picker. Without this the Subtitle tab
        // would show stale data because resetPerFileState only fires on
        // file change, not on add/remove of subtitle for the same file.
        pickerHost.setSubtitleUri(subtitleUri)
        pickerHost.setSubtitleCue("")
        // Preserve the user's pause/play intent. setExternalSubtitle is a
        // mid-playback swap of the same file, not a fresh start, so forcing
        // playWhenReady=true would resume a paused video the moment the
        // user picks or clears a sidecar.
        val wasPlaying = player?.playWhenReady ?: true
        val pos = player?.currentPosition ?: 0L
        runOnUiThread {
            player?.run {
                setMediaItem(buildMediaItem(key))
                prepare()
                seekTo(pos)
                playWhenReady = wasPlaying
            }
        }
    }

    private fun ensurePlayer() {
        if (player != null) return
        // Custom DataSource.Factory routes smb:// URIs through SmbDataSource
        // (smbj-backed) and falls through to DefaultDataSource for
        // file://, content://, http(s)://, asset:// — keeping ExoPlayer's
        // built-in semantics for the common path.
        val store = smbStore
        val defaultFactory = DefaultDataSource.Factory(this)
        val factory = DataSource.Factory {
            dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbAwareDataSource(
                store,
                defaultFactory.createDataSource(),
            )
        }
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
        // Surface playback state and errors to the picker so the user
        // sees a buffering spinner / readable error message instead of
        // a silently-paused black quad. We keep this listener tiny and
        // delegate the heavy work to PlayerStatus mapping.
        exo.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                pickerHost.setPlayerStatus(
                    when (state) {
                        androidx.media3.common.Player.STATE_BUFFERING -> PlayerStatus.Buffering
                        androidx.media3.common.Player.STATE_READY -> PlayerStatus.Playing
                        androidx.media3.common.Player.STATE_ENDED -> PlayerStatus.Ended
                        else -> PlayerStatus.Idle
                    },
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                pickerHost.setPlayerStatus(PlayerStatus.Error(error.errorCodeName + ": " + (error.message ?: "")))
            }

            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                // Concatenate all cues at this timestamp; ExoPlayer hands us
                // one CueGroup per timed-text update, so this string is the
                // text the user should see right now (empty == clear).
                val joined = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }
                currentCue = joined
                pickerHost.setSubtitleCue(joined)
            }
        })
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
        private const val KEY_ENV = "env_mode"
        private const val REQ_PICK_SUBTITLE = 0x5172

        init {
            // Loaded by NativeActivity via android.app.lib_name meta-data, but
            // we keep an explicit System.loadLibrary as a safety net for cases
            // where android_main() needs JNI symbols before NativeActivity
            // finishes initialising.
            System.loadLibrary("vrplayer")
        }
    }
}
