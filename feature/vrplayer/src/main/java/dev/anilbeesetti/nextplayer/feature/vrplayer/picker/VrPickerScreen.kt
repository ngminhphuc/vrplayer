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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class PickerTab { Local, Network, Settings }

/**
 * World-space picker UI — designed to render into a 1.2 m × 0.8 m quad in
 * front of the user. Tabbed layout (Local / Network / Settings) chosen so
 * controller users only have to point + click; no keyboard required for
 * the common case.
 */
@Composable
fun VrPickerScreen(
    entries: List<VideoEntry>,
    onPick: (VideoEntry) -> Unit,
    onPickUrl: (String) -> Unit,
    urlHistory: List<String>,
    onUrlSubmit: (String) -> Unit,
    sleepTimerMinutes: Int,
    onSleepTimerArm: (Int) -> Unit,
    lastPlayedPath: String?,
) {
    var tab by remember { mutableStateOf(PickerTab.Local) }
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
                Header(entries.size, tab) { tab = it }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    PickerTab.Local -> LocalTab(entries, lastPlayedPath, onPick)
                    PickerTab.Network -> NetworkTab(urlHistory, onPickUrl, onUrlSubmit)
                    PickerTab.Settings -> SettingsTab(sleepTimerMinutes, onSleepTimerArm)
                }
            }
        }
    }
}

@Composable
private fun Header(total: Int, current: PickerTab, onSelect: (PickerTab) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "VR Player",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "$total local",
                color = Color(0xFF9AA3B0),
                fontSize = 18.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerTab.values().forEach { t ->
                TabChip(t.name, selected = t == current) { onSelect(t) }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .pointerInput(label) { awaitPointer { onClick() } },
        color = if (selected) Color(0xFF1F2937) else Color(0xFF161A22),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun LocalTab(
    entries: List<VideoEntry>,
    lastPlayedPath: String?,
    onPick: (VideoEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState("Chưa có video nào.\nCấp READ_MEDIA_VIDEO hoặc copy file vào Movies/.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                resumeMark = entry.path == lastPlayedPath,
                onPick = onPick,
            )
        }
    }
}

@Composable
private fun NetworkTab(
    history: List<String>,
    onPickUrl: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Column {
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF161A22), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSubmit(url)
                onPickUrl(url)
                url = ""
            },
            enabled = url.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BB1FF)),
        ) {
            Text("Phát URL", color = Color.Black, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Lịch sử", color = Color(0xFF9AA3B0), fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        if (history.isEmpty()) {
            Text(
                "Chưa có URL nào.",
                color = Color(0xFF666F80),
                fontSize = 16.sp,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(history, key = { it }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .pointerInput(entry) {
                                awaitPointer { onPickUrl(entry) }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(entry, color = Color.White, fontSize = 18.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    sleepTimerMinutes: Int,
    onArm: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sleep timer", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        if (sleepTimerMinutes > 0) {
            Text(
                "Đang đếm $sleepTimerMinutes phút",
                color = Color(0xFF6BB1FF),
                fontSize = 18.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 60).forEach { min ->
                TabChip(
                    label = if (min == 0) "Off" else "${min}m",
                    selected = sleepTimerMinutes == min,
                    onClick = { onArm(min) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Auto-pause khi tháo headset luôn bật.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF9AA3B0), fontSize = 22.sp)
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
