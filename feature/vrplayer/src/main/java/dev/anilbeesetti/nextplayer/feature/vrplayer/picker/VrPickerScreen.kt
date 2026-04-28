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
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.EnvironmentMode
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.ProjectionMode
import dev.anilbeesetti.nextplayer.feature.vrplayer.playback.StereoMode
import dev.anilbeesetti.nextplayer.feature.vrplayer.smb.SmbServer

private enum class PickerTab { Local, Network, Smb, Playback, Subtitle, Settings, About }

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
    projectionMode: ProjectionMode?,
    onProjectionChange: (ProjectionMode?) -> Unit,
    stereoMode: StereoMode?,
    onStereoChange: (StereoMode?) -> Unit,
    onSnapFront: () -> Unit,
    lastPlayedPath: String?,
    smbServers: List<SmbServer> = emptyList(),
    onSmbAdd: (host: String, share: String, user: String, pass: String, domain: String?) -> Unit = { _, _, _, _, _ -> },
    onSmbRemove: (id: String) -> Unit = {},
    onSmbPlay: (server: SmbServer, path: String) -> Unit = { _, _ -> },
    environmentMode: EnvironmentMode = EnvironmentMode.BlackVoid,
    onEnvironmentChange: (EnvironmentMode) -> Unit = {},
    abPointA: Long = -1L,
    abPointB: Long = -1L,
    bookmarks: List<Long> = emptyList(),
    onSetLoopA: () -> Unit = {},
    onSetLoopB: () -> Unit = {},
    onClearLoop: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    onSeekBookmark: (Long) -> Unit = {},
    onRemoveBookmark: (Long) -> Unit = {},
    playerStatus: PlayerStatus = PlayerStatus.Idle,
    subtitleUri: String? = null,
    subtitleCue: String = "",
    subtitleFontSize: Int = SubtitlePrefsStore.DEFAULT_FONT_SIZE,
    subtitleVerticalOffset: Float = 0f,
    onPickSubtitle: () -> Unit = {},
    onClearSubtitle: () -> Unit = {},
    onSubtitleFontSizeChange: (Int) -> Unit = {},
    onSubtitleVerticalOffsetChange: (Float) -> Unit = {},
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
                StatusBanner(playerStatus)
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    PickerTab.Local -> LocalTab(entries, lastPlayedPath, onPick)
                    PickerTab.Network -> NetworkTab(urlHistory, onPickUrl, onUrlSubmit)
                    PickerTab.Smb -> SmbTab(smbServers, onSmbAdd, onSmbRemove, onSmbPlay)
                    PickerTab.Playback -> PlaybackTab(
                        abPointA,
                        abPointB,
                        bookmarks,
                        onSetLoopA,
                        onSetLoopB,
                        onClearLoop,
                        onAddBookmark,
                        onSeekBookmark,
                        onRemoveBookmark,
                    )
                    PickerTab.Subtitle -> SubtitleTab(
                        subtitleUri,
                        subtitleCue,
                        subtitleFontSize,
                        subtitleVerticalOffset,
                        onPickSubtitle,
                        onClearSubtitle,
                        onSubtitleFontSizeChange,
                        onSubtitleVerticalOffsetChange,
                    )
                    PickerTab.About -> AboutTab()
                    PickerTab.Settings -> SettingsTab(
                        sleepTimerMinutes,
                        onSleepTimerArm,
                        projectionMode,
                        onProjectionChange,
                        stereoMode,
                        onStereoChange,
                        onSnapFront,
                        environmentMode,
                        onEnvironmentChange,
                    )
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
                // playUrl() (triggered by onPickUrl) already trims the URL,
                // pushes it into the history store, and refreshes the list.
                // Calling onSubmit here too would push an untrimmed copy and
                // create a duplicate entry — see PR #5 review comment.
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
private fun SubtitleTab(
    subtitleUri: String?,
    subtitleCue: String,
    subtitleFontSize: Int,
    subtitleVerticalOffset: Float,
    onPickSubtitle: () -> Unit,
    onClearSubtitle: () -> Unit,
    onSubtitleFontSizeChange: (Int) -> Unit,
    onSubtitleVerticalOffsetChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Phụ đề", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Chọn file .srt / .vtt / .ass / .ttml ngoài để phát kèm video. Liên kết lưu vĩnh viễn theo từng path.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        if (subtitleUri == null) {
            Text("Chưa liên kết file phụ đề.", color = Color(0xFF9AA3B0), fontSize = 16.sp)
        } else {
            Text(
                "Đang dùng: ${subtitleUri.substringAfterLast('/').take(80)}",
                color = Color(0xFFB8E6FF),
                fontSize = 14.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(label = "Chọn file", selected = false, onClick = onPickSubtitle)
            TabChip(label = "Bỏ phụ đề", selected = false, onClick = onClearSubtitle)
        }
        Spacer(Modifier.height(8.dp))
        Text("Cỡ chữ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 32, 40, 48).forEach { sp ->
                TabChip(
                    label = "${sp}sp",
                    selected = subtitleFontSize == sp,
                    onClick = { onSubtitleFontSizeChange(sp) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Vị trí dọc (m)",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Negative = lower in the world (further from eye level), positive = higher.
            listOf(-0.5f, -0.25f, 0f, 0.25f).forEach { off ->
                TabChip(
                    label = if (off == 0f) "Mặc định" else "%.2f".format(off),
                    selected = kotlin.math.abs(subtitleVerticalOffset - off) < 0.01f,
                    onClick = { onSubtitleVerticalOffsetChange(off) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Cue hiện tại", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0A0E14),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = subtitleCue.ifBlank { "(không có)" },
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun StatusBanner(status: PlayerStatus) {
    val (text, color) = when (status) {
        is PlayerStatus.Idle -> return
        is PlayerStatus.Playing -> return
        is PlayerStatus.Buffering -> "Đang tải..." to Color(0xFF6BB1FF)
        is PlayerStatus.Ended -> "Đã hết file" to Color(0xFF9AA3B0)
        is PlayerStatus.Error -> "Lỗi: ${status.message}" to Color(0xFFFF6B6B)
    }
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF161A22),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 16.sp,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun AboutTab() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("VR Player", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Trình xem video VR mã nguồn mở dành cho Meta Quest. Native Kotlin + OpenXR + Media3 (ExoPlayer).",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("Tín dụng", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Dự án này là fork phái sinh của NextPlayer (anilbeesetti/nextplayer), giấy phép GNU GPLv3.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Text(
            "Theo điều kiện GPLv3, mã nguồn của VR Player được mở tại github.com/ngminhphuc/vrplayer cùng với toàn bộ thay đổi so với upstream.",
            color = Color(0xFF9AA3B0),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("Thư viện chính", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "• Khronos OpenXR Loader 1.0.34 (Apache 2.0)\n" +
                "• AndroidX Media3 / ExoPlayer (Apache 2.0)\n" +
                "• smbj 0.14.0 (Apache 2.0)\n" +
                "• AndroidX Security Crypto (Apache 2.0)\n" +
                "• Jetpack Compose / Material 3 (Apache 2.0)\n" +
                "• Timber (Apache 2.0)",
            color = Color(0xFF9AA3B0),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Toàn bộ giấy phép Apache 2.0 / GPLv3 lưu trong file LICENSE và NOTICE đi kèm APK.",
            color = Color(0xFF9AA3B0),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PlaybackTab(
    abPointA: Long,
    abPointB: Long,
    bookmarks: List<Long>,
    onSetLoopA: () -> Unit,
    onSetLoopB: () -> Unit,
    onClearLoop: () -> Unit,
    onAddBookmark: () -> Unit,
    onSeekBookmark: (Long) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("A-B loop", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Đặt điểm A rồi B; player sẽ tự seek về A khi vượt B. Chỉ áp dụng phiên hiện tại.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(
                label = if (abPointA >= 0) "A: ${formatMs(abPointA)}" else "Đặt A",
                selected = abPointA >= 0,
                onClick = onSetLoopA,
            )
            TabChip(
                label = if (abPointB >= 0) "B: ${formatMs(abPointB)}" else "Đặt B",
                selected = abPointB >= 0,
                onClick = onSetLoopB,
            )
            TabChip(label = "Xoá", selected = false, onClick = onClearLoop)
        }
        Spacer(Modifier.height(16.dp))
        Text("Bookmark", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Đánh dấu thời điểm trong file đang phát. Lưu vĩnh viễn theo từng path.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        TabChip(label = "+ Thêm bookmark tại vị trí hiện tại", selected = false, onClick = onAddBookmark)
        if (bookmarks.isEmpty()) {
            Text("Chưa có bookmark nào", color = Color(0xFF9AA3B0), fontSize = 16.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(bookmarks) { ms ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TabChip(label = formatMs(ms), selected = false) { onSeekBookmark(ms) }
                        TabChip(label = "Xoá", selected = false) { onRemoveBookmark(ms) }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun SettingsTab(
    sleepTimerMinutes: Int,
    onArm: (Int) -> Unit,
    projectionMode: ProjectionMode?,
    onProjectionChange: (ProjectionMode?) -> Unit,
    stereoMode: StereoMode?,
    onStereoChange: (StereoMode?) -> Unit,
    onSnapFront: () -> Unit,
    environmentMode: EnvironmentMode,
    onEnvironmentChange: (EnvironmentMode) -> Unit,
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
        Spacer(Modifier.height(16.dp))
        Text("Hình chiếu", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Auto = phát hiện theo tên file / metadata. Manual override khi bot đoán sai.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("Auto", projectionMode == null) { onProjectionChange(null) }
            TabChip("Cinema", projectionMode == ProjectionMode.OFF) {
                onProjectionChange(ProjectionMode.OFF)
            }
            TabChip("360°", projectionMode == ProjectionMode.EQUIRECT_360) {
                onProjectionChange(ProjectionMode.EQUIRECT_360)
            }
            TabChip("180°", projectionMode == ProjectionMode.HEMISPHERE_180) {
                onProjectionChange(ProjectionMode.HEMISPHERE_180)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Stereo 3D", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Auto = phát hiện theo tên file (_sbs/_lr/_tb/_ou). Chuyển nếu mắt thấy ngược.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("Auto", stereoMode == null) { onStereoChange(null) }
            TabChip("Mono", stereoMode == StereoMode.MONO) { onStereoChange(StereoMode.MONO) }
            TabChip("SBS L|R", stereoMode == StereoMode.SBS_LR) { onStereoChange(StereoMode.SBS_LR) }
            TabChip("SBS R|L", stereoMode == StereoMode.SBS_RL) { onStereoChange(StereoMode.SBS_RL) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("TB L/R", stereoMode == StereoMode.TB_LR) { onStereoChange(StereoMode.TB_LR) }
            TabChip("TB R/L", stereoMode == StereoMode.TB_RL) { onStereoChange(StereoMode.TB_RL) }
        }
        Spacer(Modifier.height(8.dp))
        TabChip("Snap front (recenter)", false, onSnapFront)
        Spacer(Modifier.height(16.dp))
        Text("Môi trường", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Skybox sau lưng video. 4 preset đều thuần shader, không tốn bộ nhớ texture.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EnvironmentMode.values().forEach { m ->
                TabChip(m.label, environmentMode == m) { onEnvironmentChange(m) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Auto-pause khi tháo headset luôn bật. Pinch (chạm ngón trỏ-cái) cũng kích hoạt giống nút trigger nếu thiết bị bật hand-tracking.",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun SmbTab(
    servers: List<SmbServer>,
    onAdd: (host: String, share: String, user: String, pass: String, domain: String?) -> Unit,
    onRemove: (id: String) -> Unit,
    onPlay: (server: SmbServer, path: String) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SmbServer?>(null) }
    var path by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SMB / NAS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "Thêm server, chọn từ danh sách rồi nhập đường dẫn file (vd. movies/dune.mkv).",
            color = Color(0xFF9AA3B0),
            fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmbField("Host", host, { host = it }, Modifier.weight(1f))
            SmbField("Share", share, { share = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmbField("User", user, { user = it }, Modifier.weight(1f))
            SmbField("Password", pass, { pass = it }, Modifier.weight(1f), isPassword = true)
            SmbField("Domain", domain, { domain = it }, Modifier.weight(1f))
        }
        Button(
            onClick = {
                if (host.isNotBlank() && share.isNotBlank()) {
                    onAdd(host.trim(), share.trim(), user.trim(), pass, domain.trim().takeIf { it.isNotEmpty() })
                    host = ""
                    share = ""
                    user = ""
                    pass = ""
                    domain = ""
                }
            },
            enabled = host.isNotBlank() && share.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BB1FF)),
        ) { Text("Thêm server", color = Color.Black, fontSize = 16.sp) }

        Spacer(Modifier.height(4.dp))
        if (servers.isEmpty()) {
            Text("Chưa có server nào.", color = Color(0xFF9AA3B0), fontSize = 16.sp)
        } else {
            servers.forEach { s ->
                val isSel = selected?.id == s.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(s.id) { awaitPointer { selected = s } },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) Color(0xFF1F2937) else Color(0xFF161A22),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.displayName, color = Color.White, fontSize = 16.sp)
                            Text(
                                "${s.host}/${s.share} as ${s.username.ifBlank { "guest" }}",
                                color = Color(0xFF9AA3B0),
                                fontSize = 13.sp,
                            )
                        }
                        TabChip("X", false) {
                            // Clear local selection first so the Play button
                            // disables on the same recompose; otherwise it
                            // would still hold the stale server and try to
                            // play an `smb://` URI that no longer resolves.
                            if (selected?.id == s.id) selected = null
                            onRemove(s.id)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Đã chọn: ${selected?.displayName ?: "—"}",
                color = Color(0xFF9AA3B0),
                fontSize = 14.sp,
            )
            SmbField("Path (vd. movies/dune.mkv)", path, { path = it }, Modifier.fillMaxWidth())
            Button(
                onClick = {
                    selected?.let { onPlay(it, path.trim()) }
                },
                enabled = selected != null && path.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6BB1FF)),
            ) { Text("Phát", color = Color.Black, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun SmbField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        decorationBox = { inner ->
            Column {
                Text(label, color = Color(0xFF9AA3B0), fontSize = 12.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color(0xFF161A22), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) { inner() }
            }
        },
        modifier = modifier,
    )
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
