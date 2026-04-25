package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
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
    private val lastPlayed = mutableStateOf<String?>(null)

    var onPick: ((VideoEntry) -> Unit)? = null

    /** Native side calls this when its OES texture is allocated. */
    fun acquirePickerSurface(textureId: Int): Surface {
        val st = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
        }
        val s = Surface(st)
        surfaceTexture = st
        surface = s
        attachComposeView()
        return s
    }

    fun releaseSurface() {
        composeView?.let { v ->
            // ComposeView được attach qua WindowManager.addView, parent là
            // ViewRootImpl (không phải ViewGroup), nên phải remove qua WM.
            runCatching {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(v)
            }
        }
        composeView = null
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
                    lastPlayedPath = lastPlayed.value,
                    onPick = { picked -> onPick?.invoke(picked) },
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
