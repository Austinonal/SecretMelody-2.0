package com.secretmelody

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Danger = Color(0xFFFF6B6B)

/** What the phone currently allows for background playback, and what happened the last time the app was swiped away. */
data class BackgroundStatus(val batteryUnrestricted: Boolean, val notificationsOn: Boolean, val swipe: SwipeResult)

/** Progress of picking files from the phone. */
class FileImportState {
    var text by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
}

/** One link import: the field, the download progress and what came out of it. */
class LinkImportState {
    var url by mutableStateOf("")
    var running by mutableStateOf(false)
    var done by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var resultId by mutableStateOf<Long?>(null)
    var format by mutableStateOf(YtFormat.MP3)
    var percent by mutableStateOf<Float?>(null) // null = a plain file download, shown in MB
    // YouTube preview, editable before converting. previewId says which video it belongs to.
    var previewId by mutableStateOf<String?>(null)
    var previewLoading by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var thumb by mutableStateOf<Bitmap?>(null)
    var cover by mutableStateOf<Bitmap?>(null)
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ", tint = Color.White) }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

/** A tappable settings line, at least 64dp tall so it is easy to hit. */
@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (enabled) Accent else Muted) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
fun SettingsScreen(
    files: FileImportState,
    storageSummary: String,
    background: BackgroundStatus,
    onPickFiles: () -> Unit,
    onOpenLink: () -> Unit,
    onOpenStorage: () -> Unit,
    onBattery: () -> Unit,
    onAppSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "ตั้งค่า", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
        SectionLabel("นำเข้าเพลง")
        SettingRow(Icons.Default.FolderOpen, "เลือกไฟล์จากเครื่อง", "เลือกได้หลายไฟล์พร้อมกัน จาก Drive, แฟลชไดรฟ์ หรือโฟลเดอร์ในเครื่อง", enabled = !files.busy, onClick = onPickFiles)
        SettingRow(Icons.Default.Link, "นำเข้าจากลิงก์", "วางลิงก์ไฟล์เสียง (.mp3, .m4a) หรือลิงก์ YouTube ที่คุณมีสิทธิ์ใช้", onClick = onOpenLink)
        files.text?.let { text ->
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface2)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (files.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(text, color = Color.White, fontSize = 14.sp)
            }
        }
        SectionLabel("จัดการ")
        SettingRow(Icons.Default.Storage, "พื้นที่ใช้งาน", storageSummary, onClick = onOpenStorage)
        SectionLabel("เล่นเพลงเบื้องหลัง")
        SettingRow(
            Icons.Default.GraphicEq, "ผลการปัดแอปทิ้งครั้งล่าสุด",
            when (background.swipe) {
                SwipeResult.SURVIVED -> "เพลงเล่นต่อได้หลังปัดแอปทิ้ง ✓"
                SwipeResult.KILLED -> "ระบบของมือถือปิดตัวเล่นเพลงหลังปัดแอปทิ้ง (ไม่ใช่เพลงจบเอง) ลองสองข้อล่างนี้"
                SwipeResult.STOPPED_NORMALLY -> "เพลงหยุดเองหลังปัดแอป (กดหยุดหรือเพลงจบ) ไม่ใช่ปัญหาของระบบ"
                SwipeResult.NONE -> "ยังไม่มีข้อมูล: เล่นเพลง ปัดแอปทิ้ง รอครึ่งนาที แล้วเปิดแอปใหม่"
            },
            onClick = {},
        )
        SettingRow(
            Icons.Default.BatteryChargingFull, "ไม่จำกัดการใช้แบตเตอรี่",
            if (background.batteryUnrestricted) "อนุญาตแล้ว ✓" else "ยังไม่อนุญาต แตะเพื่อขออนุญาต",
            onClick = onBattery,
        )
        SettingRow(
            Icons.Default.Notifications, "การแจ้งเตือนและการทำงานเบื้องหลัง",
            if (background.notificationsOn) "เปิดอยู่ ✓ แตะเพื่อเปิดตั้งค่าแอป (ล็อกแอปในหน้ารวมแอป, อนุญาตทำงานเบื้องหลัง)"
            else "ปิดอยู่ ✗ เปิดการแจ้งเตือนของแอปนี้ ระบบจะได้ไม่ปิดตัวเล่นเพลง",
            onClick = onAppSettings,
        )
        SettingRow(
            Icons.Default.Lock, "วิธีตั้งค่าให้เพลงไม่หยุดเมื่อปัดแอปทิ้ง",
            "ขั้นตอนสำหรับ Oppo / Realme / OnePlus (ColorOS) และมือถือยี่ห้ออื่น",
            onClick = onOpenGuide,
        )
        SectionLabel("สำรองข้อมูล")
        SettingRow(
            Icons.Default.Upload, "ส่งออกไฟล์สำรอง",
            "เพลย์ลิสต์ เพลงที่ถูกใจ ชื่อและปกที่แก้ไข เป็นไฟล์ .zip (ไม่รวมไฟล์เพลง) เก็บไว้ใน Drive หรือส่งเข้าแชทได้",
            onClick = onExport,
        )
        SettingRow(Icons.Default.Download, "กู้คืนจากไฟล์สำรอง", "ใช้เมื่อลงแอปใหม่หรือย้ายมือถือ", onClick = onImport)
        SectionLabel("เกี่ยวกับ")
        Text(
            "SecretMelody เวอร์ชัน ${BuildConfig.VERSION_NAME}\nเล่นเพลงจากไฟล์ในเครื่องของคุณ ข้อมูลเพลย์ลิสต์และที่แก้ไขเก็บในเครื่องเท่านั้น",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** One numbered step of the background-playback guide, with an optional shortcut button. */
@Composable
private fun GuideStep(n: Int, title: String, body: String, done: Boolean? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (done == true) Accent else Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (done == true) "✓" else "$n", color = if (done == true) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            if (action != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp).heightIn(min = 44.dp)) { Text(action) }
            }
        }
    }
}

@Composable
fun BackgroundGuideScreen(
    status: BackgroundStatus,
    onBack: () -> Unit,
    onBattery: () -> Unit,
    onNotifications: () -> Unit,
    onAutoStart: () -> Unit,
    onAppSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("ให้เพลงไม่หยุดเมื่อปัดแอปทิ้ง", onBack)
        Text(
            "มือถือบางยี่ห้อ เช่น Oppo, Realme, OnePlus (ColorOS) ปิดแอปทั้งตัวเมื่อปัดออกจากหน้ารวมแอป " +
                "แอปห้ามเองไม่ได้ ต้องตั้งค่าที่มือถือครั้งเดียวตามนี้",
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        GuideStep(
            1, "ล็อกแอปในหน้ารวมแอป (ได้ผลที่สุด)",
            "เปิดหน้ารวมแอป แตะ ⋮ หรือกดค้างที่การ์ด SecretMelody แล้วเลือก \"ล็อก\" (รูปกุญแจ) แอปที่ล็อกจะไม่ถูกปิดเมื่อกดล้างหรือปัด",
        )
        GuideStep(
            2, "ไม่จำกัดการใช้แบตเตอรี่",
            "ให้ระบบไม่ลดการทำงานของแอปตอนปิดหน้าจอ",
            done = status.batteryUnrestricted, action = if (status.batteryUnrestricted) null else "ขออนุญาต", onAction = onBattery,
        )
        GuideStep(
            3, "เปิดการแจ้งเตือน",
            "แถบควบคุมเพลงในแถบแจ้งเตือนบอกระบบว่าแอปกำลังเล่นเพลง",
            done = status.notificationsOn, action = if (status.notificationsOn) null else "เปิดการแจ้งเตือน", onAction = onNotifications,
        )
        GuideStep(
            4, "อนุญาตเริ่มต้นอัตโนมัติ / ทำงานเบื้องหลัง",
            "ใน ColorOS อยู่ที่ ตั้งค่า > แอป > การจัดการแอป > SecretMelody > การใช้แบตเตอรี่ " +
                "เปิด \"อนุญาตกิจกรรมเบื้องหน้า/เบื้องหลัง\" และ \"อนุญาตเริ่มอัตโนมัติ\" (ชื่อเมนูต่างกันตามรุ่น)",
            action = "เปิดหน้าตั้งค่านี้", onAction = onAutoStart,
        )
        GuideStep(
            5, "ถ้าเพลงยังหยุดอยู่",
            "เปิดแอปใหม่ เพลงและตำแหน่งที่ฟังค้างไว้จะรออยู่ที่แถบด้านล่าง กดเล่นต่อได้ทันที " +
                "หรือกดเล่นที่หูฟังบลูทูธ / แถบควบคุมเพลงของระบบ",
            action = "เปิดตั้งค่าแอป", onAction = onAppSettings,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class) // SegmentedButton
@Composable
fun LinkImportScreen(
    state: LinkImportState,
    imported: Track?,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    onPlay: (Track) -> Unit,
    onEdit: (Track) -> Unit,
    onAdjustCover: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val videoId = youtubeId(state.url)
    LaunchedEffect(videoId) {
        // Keep edits made before leaving to crop; refetch only if a fetch was cut off by leaving the screen.
        if (videoId == null || (videoId == state.previewId && !state.previewLoading)) return@LaunchedEffect
        state.previewId = videoId
        state.title = ""; state.artist = ""; state.thumb = null; state.cover = null
        state.previewLoading = true
        val p = withContext(Dispatchers.IO) { runCatching { youtubePreview(videoId) }.getOrNull() }
        if (state.previewId == videoId) {
            if (p != null) {
                state.title = p.title
                state.artist = p.artist
                state.thumb = p.thumb
                state.cover = p.thumb?.let { withContext(Dispatchers.Default) { centerSquare(it) } }
            }
            state.previewLoading = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("นำเข้าจากลิงก์", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.url,
                onValueChange = { state.url = it; state.error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ลิงก์ไฟล์เสียง") },
                placeholder = { Text("https://…/เพลง.mp3") },
                singleLine = true,
                enabled = !state.running,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(
                        onClick = { clipboard.getText()?.text?.let { state.url = it.trim(); state.error = null } },
                        enabled = !state.running,
                    ) { Icon(Icons.Default.ContentPaste, "วางจากคลิปบอร์ด") }
                },
                supportingText = { Text(state.error ?: "ลิงก์ไฟล์เสียงตรงๆ หรือลิงก์ YouTube ขึ้นต้นด้วย https://") },
            )
            val youtube = videoId != null
            if (youtube && state.previewLoading) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Text("กำลังโหลดชื่อเพลงและปก…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            } else if (youtube && state.previewId == videoId && (state.title.isNotEmpty() || state.cover != null)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp),
                ) {
                    if (state.format == YtFormat.MP3) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)).background(Bg)
                                    .clickable(enabled = !state.running && state.thumb != null, onClick = onAdjustCover),
                                contentAlignment = Alignment.Center,
                            ) {
                                val c = state.cover
                                if (c != null) Image(c.asImageBitmap(), "ปกเพลง", Modifier.fillMaxSize())
                                else Icon(Icons.Default.MusicNote, null, tint = Muted)
                            }
                            if (state.thumb != null) {
                                TextButton(onClick = onAdjustCover, enabled = !state.running) { Text("ปรับตำแหน่งปก", fontSize = 12.sp) }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            state.title, { state.title = it }, label = { Text("ชื่อเพลง") },
                            enabled = !state.running, modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.format == YtFormat.MP3) {
                            OutlinedTextField(
                                state.artist, { state.artist = it }, label = { Text("ศิลปิน") }, singleLine = true,
                                enabled = !state.running, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            if (youtube) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    YtFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = state.format == f,
                            onClick = { state.format = f },
                            shape = SegmentedButtonDefaults.itemShape(i, YtFormat.entries.size),
                            enabled = !state.running,
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Accent, activeContentColor = Color.Black),
                        ) { Text(f.name) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.running) {
                val percent = state.percent
                val fraction = when {
                    percent != null -> percent.coerceIn(0f, 1f)
                    state.total > 0 -> (state.done.toFloat() / state.total).coerceIn(0f, 1f)
                    else -> null
                }
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth(), color = Accent)
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                Text(
                    when {
                        percent != null -> "กำลังแปลง ${(percent * 100).toInt()}%"
                        state.total > 0 -> "กำลังโหลด ${formatSize(state.done)} / ${formatSize(state.total)}"
                        else -> "กำลังโหลด ${formatSize(state.done)}"
                    },
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("ยกเลิก") }
            } else {
                Button(
                    onClick = onImport,
                    enabled = state.url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                ) { Text(if (youtube) "แปลง" else "นำเข้า", fontWeight = FontWeight.Bold) }
            }
            imported?.let { t ->
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp),
                ) {
                    Text("นำเข้าแล้ว", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(t, Modifier.size(56.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onEdit(t) }, Modifier.weight(1f)) { Text("แก้ไขข้อมูล") }
                        Button(
                            onClick = { onPlay(t) }, Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                        ) { Text("เล่น", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Text(
                "ใช้ได้กับไฟล์ที่คุณมีสิทธิ์ เช่น เพลงของคุณเอง ลิงก์แชร์จาก Google Drive ของคุณ หรือเพลงที่เจ้าของอนุญาตให้ดาวน์โหลด" +
                    "\nลิงก์ YouTube แปลงเป็น MP3 (ไม่เกิน 240 นาที) หรือ MP4 360p (ไม่เกิน 30 นาที) ได้ ส่วน TikTok, Spotify และหน้าเว็บทั่วไปยังใช้ไม่ได้",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
fun StorageScreen(tracks: List<Track>, freeBytes: Long, onBack: () -> Unit, onDelete: (List<Track>) -> Unit) {
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirm by remember { mutableStateOf(false) }
    val sorted = remember(tracks, sort) {
        if (sort == 0) tracks.sortedByDescending { it.sizeBytes } else tracks.sortedByDescending { it.dateAdded }
    }
    val chosen = remember(tracks, selected) { tracks.filter { it.id in selected } }
    val chosenBytes = chosen.sumOf { it.sizeBytes }
    Column(Modifier.fillMaxSize()) {
        PageHeader("พื้นที่ใช้งาน", onBack)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).background(Surface2).padding(16.dp),
        ) {
            Text(formatSize(tracks.sumOf { it.sizeBytes }), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${tracks.size} เพลงในเครื่อง", color = Muted, fontSize = 14.sp)
            Text("พื้นที่ว่างในเครื่อง ${formatSize(freeBytes)}", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ขนาดใหญ่สุด", "เพิ่มล่าสุด").forEachIndexed { i, label ->
                FilterChip(
                    selected = sort == i, onClick = { sort = i }, label = { Text(label) }, border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Surface2, labelColor = Color.White,
                        selectedContainerColor = Accent, selectedLabelColor = Color.Black,
                    ),
                )
            }
        }
        if (tracks.isEmpty()) {
            Text("ยังไม่มีเพลงในเครื่อง", color = Muted, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(sorted, key = { it.id }) { t ->
                val on = t.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { selected = if (on) selected - t.id else selected + t.id }
                        .padding(start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = on, onCheckedChange = { selected = if (on) selected - t.id else selected + t.id },
                        colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.Black, uncheckedColor = Muted),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatSize(t.sizeBytes), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        if (chosen.isNotEmpty()) {
            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = Color.Black),
            ) { Text("ลบ ${chosen.size} เพลง • ${formatSize(chosenBytes)}", fontWeight = FontWeight.Bold) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("ลบ ${chosen.size} เพลงออกจากเครื่อง?") },
            text = { Text("ไฟล์เพลงจะถูกลบถาวร ได้พื้นที่คืน ${formatSize(chosenBytes)} เพลงที่อยู่ในเพลย์ลิสต์จะหายไปด้วย") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onDelete(chosen); selected = emptySet() }) { Text("ลบ", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("ยกเลิก") } },
        )
    }
}

/** Edit a song's title, artist and cover. Changes are saved on "บันทึก"; the cover applies at once. */
@Composable
fun EditTrackDialog(
    track: Track,
    hasCustomCover: Boolean,
    onSave: (title: String, artist: String) -> Unit,
    onSearchCover: () -> Unit,
    onPickCover: () -> Unit,
    onResetCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("แก้ไขข้อมูลเพลง") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track, Modifier.size(88.dp), px = 300)
                    Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = onSearchCover) { Text("ค้นหาปกออนไลน์") }
                        OutlinedButton(onClick = onPickCover) { Text("เลือกรูปจากเครื่อง") }
                        if (hasCustomCover) TextButton(onClick = onResetCover) { Text("ใช้ปกเดิม") }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("ชื่อเพลง") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("ศิลปิน") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("แก้เฉพาะที่แสดงในแอป ไฟล์เพลงต้นฉบับไม่ถูกแก้", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist); onDismiss() }, enabled = title.isNotBlank()) { Text("บันทึก") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } },
    )
}
