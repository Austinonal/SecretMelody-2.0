package com.secretmelody

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

/** What the track menu can do, wired once in App. */
class TrackActions(
    val playNext: (Track) -> Unit,
    val isLiked: (Track) -> Boolean,
    val toggleLike: (Track) -> Unit,
    val addToPlaylist: (Track) -> Unit,
    val goToAlbum: (Track) -> Unit,
    val goToArtist: (Track) -> Unit,
    val edit: (Track) -> Unit,
)

/** Playlist or album cover; Liked Songs gets its own heart tile, an empty playlist a placeholder. */
@Composable
fun GroupCover(g: TrackGroup, modifier: Modifier, px: Int = 300) {
    val shape = if (g.isArtist) CircleShape else RoundedCornerShape(4.dp)
    when {
        g.playlistId == LIKED_ID -> Box(
            modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF6E1530), Accent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.4f))
        }
        g.tracks.isEmpty() -> Box(modifier.clip(shape).background(Surface2), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Muted, modifier = Modifier.fillMaxSize(0.4f))
        }
        else -> Artwork(g.tracks[0], modifier, px = px, shape = shape)
    }
}

private fun librarySubtitle(g: TrackGroup) = when {
    g.isArtist -> "ศิลปิน"
    g.playlistId != null -> g.subtitle
    else -> "อัลบั้ม • ${g.subtitle}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    liked: TrackGroup,
    playlists: List<TrackGroup>,
    artists: List<TrackGroup>,
    albums: List<TrackGroup>,
    onOpen: (TrackGroup) -> Unit,
    onCreate: () -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("ทั้งหมด", "เพลย์ลิสต์", "ศิลปิน", "อัลบั้ม")
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "คอลเลกชันของคุณ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCreate) {
                Icon(Icons.Default.Add, "สร้างเพลย์ลิสต์", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { i, label ->
                FilterChip(
                    selected = filter == i,
                    onClick = { filter = if (filter == i) 0 else i },
                    label = { Text(label) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Surface2, labelColor = Color.White,
                        selectedContainerColor = Accent, selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (filter == 0 || filter == 1) {
                item { LibraryRow(liked, onOpen) }
                items(playlists) { LibraryRow(it, onOpen) }
                if (playlists.isEmpty() && filter == 1) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ยังไม่มีเพลย์ลิสต์", color = Muted)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onCreate,
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                            ) { Text("สร้างเพลย์ลิสต์", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            if (filter == 0 || filter == 2) items(artists) { LibraryRow(it, onOpen) }
            if (filter == 0 || filter == 3) items(albums) { LibraryRow(it, onOpen) }
        }
    }
}

@Composable
private fun LibraryRow(g: TrackGroup, onOpen: (TrackGroup) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(g) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupCover(g, Modifier.size(64.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(g.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(librarySubtitle(g), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun QueueScreen(player: Player, ui: PlayerUi, byId: Map<Long, Track>, onClose: () -> Unit) {
    val upNext = remember(ui.version) { player.upNextIndices() }
    val current = ui.mediaId?.toLongOrNull()?.let { byId[it] }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, "ปิด", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Text(
                "คิว", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { QueueHeading("กำลังเล่น") }
            if (current != null) {
                item { QueueRow(current.title, current.artist, current, playing = true, onClick = {}) {} }
            }
            item {
                QueueHeading("ถัดไป", note = if (ui.shuffle) "ปิดการสุ่มเพื่อจัดลำดับ" else null)
            }
            if (upNext.isEmpty()) {
                item { Text("ไม่มีเพลงต่อจากนี้", color = Muted, modifier = Modifier.padding(16.dp)) }
            }
            itemsIndexed(upNext) { pos, index ->
                val meta = player.getMediaItemAt(index)
                val t = meta.mediaId.toLongOrNull()?.let { byId[it] }
                QueueRow(
                    title = meta.mediaMetadata.title?.toString() ?: t?.title ?: "",
                    artist = meta.mediaMetadata.artist?.toString() ?: t?.artist ?: "",
                    track = t,
                    playing = false,
                    onClick = { player.seekTo(index, 0L) },
                ) {
                    // Buttons instead of drag-to-reorder: one tap each, and nothing to aim at while scrolling.
                    if (!ui.shuffle && pos > 0) {
                        IconButton(onClick = { player.moveMediaItem(index, index - 1) }) {
                            Icon(Icons.Default.KeyboardArrowUp, "เลื่อนขึ้น", tint = Muted)
                        }
                    }
                    IconButton(onClick = { player.removeMediaItem(index) }) {
                        Icon(Icons.Default.Close, "ลบออกจากคิว", tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHeading(text: String, note: String? = null) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (note != null) Text(note, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    track: Track?,
    playing: Boolean,
    onClick: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (track != null) {
            Artwork(track, Modifier.size(48.dp))
        } else {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Surface2))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title, color = if (playing) AccentText else Color.White, fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        buttons()
    }
}

/** Shared by "new playlist" and "rename". */
data class NameDialog(val title: String, val initial: String, val confirm: String, val onDone: (String) -> Unit)

@Composable
fun NameDialogView(d: NameDialog, onDismiss: () -> Unit) {
    var text by remember(d) { mutableStateOf(d.initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(d.title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("ชื่อเพลย์ลิสต์") },
            )
        },
        confirmButton = {
            TextButton(onClick = { d.onDone(text); onDismiss() }, enabled = text.isNotBlank()) { Text(d.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("เพิ่มลงเพลย์ลิสต์") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onNew).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(12.dp))
                        Text("สร้างเพลย์ลิสต์ใหม่", fontWeight = FontWeight.Bold)
                    }
                }
                items(playlists) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.trackIds.size} เพลง", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
    )
}

@Composable
fun ConfirmDeleteDialog(p: Playlist, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ลบเพลย์ลิสต์?") },
        text = { Text("\"${p.name}\" จะถูกลบ ไฟล์เพลงในเครื่องไม่หาย") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("ลบ", color = Color(0xFFFF6B6B)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}
