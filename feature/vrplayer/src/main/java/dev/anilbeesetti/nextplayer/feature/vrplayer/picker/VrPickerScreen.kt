package dev.anilbeesetti.nextplayer.feature.vrplayer.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * World-space picker UI — designed to render into a 1.2 m × 0.8 m quad in
 * front of the user. Layout choices:
 *  - large fonts (>= 22 sp) so 1024 × 720 px composition reads at 1.4 m.
 *  - dark theme with high contrast — sits on top of the Black Void skybox.
 *  - rows are 88 dp tall (controller-pointer comfortable).
 */
@Composable
fun VrPickerScreen(
    entries: List<VideoEntry>,
    onPick: (VideoEntry) -> Unit,
    lastPlayedPath: String?,
) {
    MaterialTheme(colorScheme = vrColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F1115),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Header(entries.size)
                Spacer(Modifier.height(12.dp))
                if (entries.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            EntryRow(
                                entry = entry,
                                resumeMark = entry.path == lastPlayedPath,
                                onPick = onPick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "VR Player — Local",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = "$total video",
            color = Color(0xFF9AA3B0),
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Chưa có video nào.\nCấp quyền READ_MEDIA_VIDEO hoặc copy file vào Movies/.",
            color = Color(0xFF9AA3B0),
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun EntryRow(
    entry: VideoEntry,
    resumeMark: Boolean,
    onPick: (VideoEntry) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .pointerInput(entry.id) {
                awaitPointer { onPick(entry) }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (resumeMark) Color(0xFF1F2937) else Color(0xFF161A22),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${entry.durationMs / 1000}s · ${entry.sizeBytes / (1024 * 1024)} MB" +
                        if (resumeMark) " · resume" else "",
                    color = Color(0xFF9AA3B0),
                    fontSize = 16.sp,
                )
            }
        }
    }
}

private val vrColorScheme = darkColorScheme(
    primary = Color(0xFF6BB1FF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF161A22),
)

/** Rough wrapper for "first tap-down or click" so we don't need foundation
 *  gesture dependencies. */
private suspend inline fun androidx.compose.ui.input.pointer.PointerInputScope.awaitPointer(
    crossinline onTap: () -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.any { it.pressed }
            if (pressed) {
                onTap()
                event.changes.forEach { it.consume() }
            }
        }
    }
}
