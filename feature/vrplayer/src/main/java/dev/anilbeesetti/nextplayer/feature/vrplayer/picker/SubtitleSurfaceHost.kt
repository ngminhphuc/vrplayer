package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber

/**
 * Tiny Compose-to-Surface host dedicated to subtitle text. Mirrors the
 * pattern in [PickerSurfaceHost] (hidden WindowManager view + periodic
 * PixelCopy via lockCanvas) but renders just one centered text line.
 *
 * Background is fully transparent so the native [SubtitleQuad] can
 * alpha-blend the cue over the cinema screen.
 *
 * Visibility is toggled from native (via XrActivity#nativeSetSubtitleVisible)
 * based on whether the current cue is non-empty.
 */
class SubtitleSurfaceHost(
    private val context: Context,
    val width: Int = 1024,
    val height: Int = 192,
) {
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var composeView: ComposeView? = null
    private val cueText = mutableStateOf("")

    fun acquireSubtitleSurface(textureId: Int): Surface {
        val st = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
        }
        val s = Surface(st)
        surfaceTexture = st
        surface = s
        Handler(Looper.getMainLooper()).post { attachComposeView() }
        return s
    }

    fun releaseSurface() {
        Handler(Looper.getMainLooper()).post {
            composeView?.let { v ->
                runCatching {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(v)
                }
            }
            composeView = null
        }
        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
    }

    fun setCue(text: String) {
        cueText.value = text
    }

    fun cue(): String = cueText.value

    fun updateTexImage(): Boolean {
        return runCatching {
            surfaceTexture?.updateTexImage()
            true
        }.getOrDefault(false)
    }

    fun getTransformMatrix(out: FloatArray) {
        surfaceTexture?.getTransformMatrix(out)
    }

    private fun attachComposeView() {
        if (composeView != null) return
        val cv = ComposeView(context).apply {
            setContent {
                val t = remember { cueText }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t.value,
                        color = ComposeColor.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }
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
            Timber.tag(TAG).w(t, "subtitle addView failed")
        }
        composeView = cv
        cv.post(::pumpFrame)
    }

    private fun pumpFrame() {
        val cv = composeView ?: return
        val s = surface ?: return
        runCatching {
            val canvas = s.lockCanvas(null)
            // Clear with full transparency so the SubtitleQuad fragment
            // shader gets proper alpha-zero pixels everywhere except
            // where the cue text is drawn.
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            cv.draw(canvas)
            s.unlockCanvasAndPost(canvas)
        }.onFailure {
            // Same caveat as PickerSurfaceHost — PixelCopy may be
            // unavailable on some Quest configurations. Fall back silently.
        }
        cv.postDelayed(::pumpFrame, FRAME_INTERVAL_MS)
    }

    companion object {
        private const val TAG = "VrPlayer/SubtitleHost"
        private const val FRAME_INTERVAL_MS = 33L
    }
}
