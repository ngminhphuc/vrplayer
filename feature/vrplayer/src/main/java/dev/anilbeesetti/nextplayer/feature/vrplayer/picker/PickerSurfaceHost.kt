package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.ComposeView
import timber.log.Timber

/**
 * Renders [VrPickerScreen] into an off-screen [Surface] backed by a
 * [SurfaceTexture] that the native side samples as `GL_TEXTURE_EXTERNAL_OES`.
 *
 * This is intentionally rough — proper Compose-to-Surface support landed in
 * the Compose 1.6 OffscreenComposition APIs but is gated behind feature flags.
 * For Sprint 2 we use the approach of laying the [ComposeView] inside an
 * invisible [WindowManager] window and capturing it; Sprint 3 will move to
 * a proper `XrCompositionLayerQuad` rendered directly from a Compose-Owned
 * surface.
 */
class PickerSurfaceHost(
    private val context: Context,
    val width: Int = 1024,
    val height: Int = 720,
) {
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var composeView: ComposeView? = null
    private val entries: SnapshotStateList<VideoEntry> = mutableListOf<VideoEntry>().toMutableStateList()
    private val urlHistory: SnapshotStateList<String> = mutableListOf<String>().toMutableStateList()
    private val lastPlayed = mutableStateOf<String?>(null)
    private val sleepMinutes = mutableStateOf(0)
    private val projectionMode =
        mutableStateOf<dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProjectionMode?>(null)
    private val stereoMode =
        mutableStateOf<dev.anilbeesetti.nextplayer.feature.vrplayer.playback.StereoMode?>(null)
    private val smbServers = mutableListOf<dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbServer>().toMutableStateList()
    private val environmentMode =
        mutableStateOf(dev.anilbeesetti.nextplayer.feature.vrplayer.playback.EnvironmentMode.BlackVoid)
    private val abPointA = mutableStateOf(-1L)
    private val abPointB = mutableStateOf(-1L)
    private val bookmarks: SnapshotStateList<Long> = mutableListOf<Long>().toMutableStateList()
    private val playerStatus = mutableStateOf<PlayerStatus>(PlayerStatus.Idle)
    private val subtitleUri = mutableStateOf<String?>(null)
    private val subtitleCue = mutableStateOf("")
    private val subtitleFontSize = mutableStateOf(SubtitlePrefsStore.DEFAULT_FONT_SIZE)
    private val subtitleVerticalOffset = mutableStateOf(0f)

    var onPick: ((VideoEntry) -> Unit)? = null
    var onPickUrl: ((String) -> Unit)? = null
    var onUrlSubmit: ((String) -> Unit)? = null
    var onSleepTimerArm: ((Int) -> Unit)? = null
    var onProjectionChange: ((dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProjectionMode?) -> Unit)? = null
    var onStereoChange: ((dev.anilbeesetti.nextplayer.feature.vrplayer.playback.StereoMode?) -> Unit)? = null
    var onSnapFront: (() -> Unit)? = null
    var onEnvironmentChange: ((dev.anilbeesetti.nextplayer.feature.vrplayer.playback.EnvironmentMode) -> Unit)? = null
    var onSmbAdd: ((host: String, share: String, user: String, pass: String, domain: String?) -> Unit)? = null
    var onSmbRemove: ((id: String) -> Unit)? = null
    var onSmbPlay: ((server: dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbServer, path: String) -> Unit)? = null
    var onSetLoopA: (() -> Unit)? = null
    var onSetLoopB: (() -> Unit)? = null
    var onClearLoop: (() -> Unit)? = null
    var onAddBookmark: (() -> Unit)? = null
    var onSeekBookmark: ((Long) -> Unit)? = null
    var onRemoveBookmark: ((Long) -> Unit)? = null
    var onPickSubtitle: (() -> Unit)? = null
    var onClearSubtitle: (() -> Unit)? = null
    var onSubtitleFontSizeChange: ((Int) -> Unit)? = null
    var onSubtitleVerticalOffsetChange: ((Float) -> Unit)? = null

    /** Native side calls this when its OES texture is allocated. */
    fun acquirePickerSurface(textureId: Int): Surface {
        val st = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
        }
        val s = Surface(st)
        surfaceTexture = st
        surface = s
        // attachComposeView phải chạy trên main thread vì WindowManager.addView
        // và ComposeView setContent đều require UI thread. Native gọi từ render
        // thread nên cần post sang. SurfaceTexture/Surface đã được tạo trên
        // GL thread (yêu cầu của OES texture binding) nên không cần post chúng.
        Handler(Looper.getMainLooper()).post { attachComposeView() }
        return s
    }

    fun releaseSurface() {
        // releaseSurface có thể được gọi từ bất kỳ thread nào (lifecycle hook,
        // shutdown, ...). WindowManager.removeView phải trên main thread.
        Handler(Looper.getMainLooper()).post {
            composeView?.let { v ->
                runCatching {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeViewImmediate(v)
                }
            }
            composeView = null
        }
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
    }

    fun setEntries(list: List<VideoEntry>) {
        entries.clear()
        entries.addAll(list)
    }

    fun setLastPlayed(path: String?) {
        lastPlayed.value = path
    }

    fun setUrlHistory(list: List<String>) {
        urlHistory.clear()
        urlHistory.addAll(list)
    }

    fun setSleepMinutes(min: Int) {
        sleepMinutes.value = min
    }

    fun setProjectionMode(mode: dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProjectionMode?) {
        projectionMode.value = mode
    }

    fun setStereoMode(mode: dev.anilbeesetti.nextplayer.feature.vrplayer.playback.StereoMode?) {
        stereoMode.value = mode
    }

    fun setSmbServers(list: List<dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbServer>) {
        smbServers.clear()
        smbServers.addAll(list)
    }

    fun setEnvironmentMode(mode: dev.anilbeesetti.nextplayer.feature.vrplayer.playback.EnvironmentMode) {
        environmentMode.value = mode
    }

    fun setAbLoop(a: Long, b: Long) {
        abPointA.value = a
        abPointB.value = b
    }

    fun setBookmarks(list: List<Long>) {
        bookmarks.clear()
        bookmarks.addAll(list)
    }

    fun setPlayerStatus(status: PlayerStatus) {
        playerStatus.value = status
    }

    fun setSubtitleUri(uri: String?) {
        subtitleUri.value = uri
    }

    fun setSubtitleCue(text: String) {
        subtitleCue.value = text
    }

    fun setSubtitleFontSize(sp: Int) {
        subtitleFontSize.value = sp
    }

    fun setSubtitleVerticalOffset(offset: Float) {
        subtitleVerticalOffset.value = offset
    }

    fun updateTexImage(): Boolean {
        return runCatching {
            surfaceTexture?.updateTexImage()
            true
        }.getOrDefault(false)
    }

    fun getTransformMatrix(out: FloatArray) {
        surfaceTexture?.getTransformMatrix(out)
    }

    /** Inject a controller-pointer click. Coordinates are in [0..1] u/v space
     *  computed by native ray-vs-quad intersect; we scale to [width]/[height]. */
    fun injectTap(u: Float, v: Float) {
        val view = composeView ?: return
        val now = SystemClock.uptimeMillis()
        val x = (u.coerceIn(0f, 1f)) * width
        val y = (v.coerceIn(0f, 1f)) * height
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 8, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun attachComposeView() {
        if (composeView != null) return
        val cv = ComposeView(context).apply {
            setContent {
                VrPickerScreen(
                    entries = entries,
                    onPick = { picked -> onPick?.invoke(picked) },
                    onPickUrl = { u -> onPickUrl?.invoke(u) },
                    urlHistory = urlHistory,
                    onUrlSubmit = { u -> onUrlSubmit?.invoke(u) },
                    sleepTimerMinutes = sleepMinutes.value,
                    onSleepTimerArm = { m -> onSleepTimerArm?.invoke(m) },
                    projectionMode = projectionMode.value,
                    onProjectionChange = { m ->
                        projectionMode.value = m
                        onProjectionChange?.invoke(m)
                    },
                    stereoMode = stereoMode.value,
                    onStereoChange = { m ->
                        stereoMode.value = m
                        onStereoChange?.invoke(m)
                    },
                    onSnapFront = { onSnapFront?.invoke() },
                    lastPlayedPath = lastPlayed.value,
                    smbServers = smbServers,
                    onSmbAdd = { h, sh, u, p, d -> onSmbAdd?.invoke(h, sh, u, p, d) },
                    onSmbRemove = { id -> onSmbRemove?.invoke(id) },
                    onSmbPlay = { srv, path -> onSmbPlay?.invoke(srv, path) },
                    environmentMode = environmentMode.value,
                    onEnvironmentChange = { m ->
                        environmentMode.value = m
                        onEnvironmentChange?.invoke(m)
                    },
                    abPointA = abPointA.value,
                    abPointB = abPointB.value,
                    bookmarks = bookmarks,
                    onSetLoopA = { onSetLoopA?.invoke() },
                    onSetLoopB = { onSetLoopB?.invoke() },
                    onClearLoop = { onClearLoop?.invoke() },
                    onAddBookmark = { onAddBookmark?.invoke() },
                    onSeekBookmark = { ms -> onSeekBookmark?.invoke(ms) },
                    onRemoveBookmark = { ms -> onRemoveBookmark?.invoke(ms) },
                    playerStatus = playerStatus.value,
                    subtitleUri = subtitleUri.value,
                    subtitleCue = subtitleCue.value,
                    subtitleFontSize = subtitleFontSize.value,
                    subtitleVerticalOffset = subtitleVerticalOffset.value,
                    onPickSubtitle = { onPickSubtitle?.invoke() },
                    onClearSubtitle = { onClearSubtitle?.invoke() },
                    onSubtitleFontSizeChange = { sp ->
                        subtitleFontSize.value = sp
                        onSubtitleFontSizeChange?.invoke(sp)
                    },
                    onSubtitleVerticalOffsetChange = { off ->
                        subtitleVerticalOffset.value = off
                        onSubtitleVerticalOffsetChange?.invoke(off)
                    },
                )
            }
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }
        // Add to a hidden WindowManager window so the Compose runtime has a
        // ViewTreeLifecycleOwner / SavedStateRegistry. Quest VR doesn't show
        // 2D windows in headset, but the WindowManager still backs our view.
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        try {
            wm.addView(cv, params)
        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "addView failed; picker will not render")
        }
        composeView = cv
        // Best-effort: schedule periodic PixelCopy from cv to the surface canvas.
        cv.post(::pumpFrame)
    }

    private fun pumpFrame() {
        val cv = composeView ?: return
        val s = surface ?: return
        runCatching {
            val canvas = s.lockCanvas(null)
            canvas.drawColor(0xFF0F1115.toInt())
            cv.draw(canvas)
            s.unlockCanvasAndPost(canvas)
        }.onFailure {
            // PixelCopy unavailable on Quest in some configs; fall back silently.
        }
        cv.postDelayed(::pumpFrame, FRAME_INTERVAL_MS)
    }

    companion object {
        private const val TAG = "VrPlayer/PickerHost"
        private const val FRAME_INTERVAL_MS = 33L // ~30 FPS for UI is plenty

        @Suppress("unused")
        private fun keepImport() = PixelCopy.SUCCESS
    }
}
