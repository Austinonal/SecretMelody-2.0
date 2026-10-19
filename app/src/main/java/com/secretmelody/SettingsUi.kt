package com.secretmelody

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
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
    // Spotify link: the song it names and the YouTube recordings that match it (null = not searched yet).
    var spotify by mutableStateOf<SpotifyTrack?>(null)
    var searching by mutableStateOf(false)
    var candidates by mutableStateOf<List<YtCandidate>?>(null)
    var chosen by mutableStateOf<String?>(null) // YouTube video id to convert
    var searchError by mutableStateOf<String?>(null)
    // Spotify album / playlist / artist: its songs, which ones to convert, and how the batch is going.
    var listFor by mutableStateOf<String?>(null)
    var list by mutableStateOf<SpotifyList?>(null)
    var listError by mutableStateOf<String?>(null)
    var picked by mutableStateOf(setOf<String>())
    var batchIndex by mutableIntStateOf(0)
    var batchTotal by mutableIntStateOf(0) // 0 = not converting a batch
    var batchTitle by mutableStateOf("")
    var batchResult by mutableStateOf<String?>(null)
}

/** Which preview a link gets: the YouTube id, "sp:" + the Spotify id, or null for a plain file link. */
fun previewKey(url: String): String? = youtubeId(url) ?: spotifyTrackId(url)?.let { "sp:$it" }

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
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    checked: Boolean? = null, // set = an on/off row with a switch, toggled by tapping anywhere on it
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (checked == null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() }),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (enabled) AccentText else Muted) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, color = Color.White, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (checked == null) Icon(Icons.Default.ChevronRight, null, tint = Muted)
        else Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
        )
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
    audio: AudioSettings = AudioSettings(),
    onAudio: (AudioSettings) -> Unit = {},
    onOpenEq: () -> Unit = {},
    statsSummary: String = "",
    onOpenStats: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "ตั้งค่า", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
        SectionLabel("นำเข้าเพลง")
        SettingRow(Icons.Default.FolderOpen, "เลือกไฟล์จากเครื่อง", "เลือกได้หลายไฟล์พร้อมกัน จาก Drive, แฟลชไดรฟ์ หรือโฟลเดอร์ในเครื่อง", enabled = !files.busy, onClick = onPickFiles)
        SettingRow(Icons.Default.Link, "นำเข้าจากลิงก์", "วางลิงก์ไฟล์เสียง (.mp3, .m4a), ลิงก์ YouTube หรือเพลงใน Spotify ที่คุณมีสิทธิ์ใช้", onClick = onOpenLink)
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
        SectionLabel("เสียง")
        SettingRow(
            Icons.Default.GraphicEq, "ข้ามช่วงเงียบ", "ตัดช่วงเงียบตอนต้น ตอนท้าย และระหว่างเพลงออกขณะเล่น",
            checked = audio.skipSilence, onClick = { onAudio(audio.copy(skipSilence = !audio.skipSilence)) },
        )
        SettingRow(
            Icons.AutoMirrored.Filled.VolumeUp, "ปรับเสียงให้ดังใกล้กัน", "เพลงที่อัดมาเบาจะดังขึ้น เพลงที่ดังจะนุ่มลง ไม่ต้องคอยปรับเสียงเอง",
            checked = audio.evenVolume, onClick = { onAudio(audio.copy(evenVolume = !audio.evenVolume)) },
        )
        SettingRow(Icons.Default.Equalizer, "อีควอไลเซอร์", if (audio.eqOn) "เปิดอยู่" else "ปิดอยู่", onClick = onOpenEq)
        SectionLabel("จัดการ")
        SettingRow(Icons.Default.Leaderboard, "สถิติการฟัง", statsSummary, onClick = onOpenStats)
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
            Text(if (done == true) "✓" else "$n", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current
    val videoId = youtubeId(state.url)
    val spotifyId = spotifyTrackId(state.url)
    val linkKey = previewKey(state.url)
    val listPath = spotifyListPath(state.url)
    val shortLink = spotifyShortLink(state.url)
    LaunchedEffect(shortLink) {
        if (shortLink == null) return@LaunchedEffect
        val real = withContext(Dispatchers.IO) { runCatching { resolveSpotifyShortLink(shortLink) }.getOrNull() }
        if (spotifyShortLink(state.url) != shortLink) return@LaunchedEffect // the field changed meanwhile
        if (real != null) state.url = real // the track / list effects take it from here
        else state.error = "เปิดลิงก์สั้นของ Spotify ไม่ได้ ลองคัดลอกลิงก์จากแอป Spotify ใหม่"
    }
    LaunchedEffect(listPath) {
        if (listPath == null || (listPath == state.listFor && state.list != null)) return@LaunchedEffect
        state.listFor = listPath
        state.list = null; state.listError = null; state.batchResult = null
        val l = withContext(Dispatchers.IO) { runCatching { spotifyList(listPath) }.getOrNull() }
        if (state.listFor != listPath) return@LaunchedEffect
        when {
            l == null -> state.listError = "อ่านรายชื่อเพลงจาก Spotify ไม่ได้ ตรวจสอบอินเทอร์เน็ตหรือลิงก์"
            l.items.isEmpty() -> state.listError = "ไม่พบเพลงในลิงก์นี้"
            else -> {
                state.list = l
                state.picked = l.items.map { it.id }.toSet()
            }
        }
    }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(linkKey, retry) {
        if (linkKey == null) return@LaunchedEffect
        // Keep edits made before leaving to crop; refetch only if a fetch was cut off by leaving the screen.
        if (linkKey != state.previewId || state.previewLoading) {
            state.previewId = linkKey
            state.title = ""; state.artist = ""; state.thumb = null; state.cover = null
            state.spotify = null; state.candidates = null; state.chosen = null; state.searchError = null
            state.previewLoading = true
            val p = withContext(Dispatchers.IO) {
                runCatching {
                    if (videoId != null) youtubePreview(videoId)
                    else spotifyTrack(spotifyId!!).let { t ->
                        state.spotify = t
                        YtPreview(t.title, t.artists.joinToString(", "), t.coverUrl?.let { runCatching { downloadBitmap(it) }.getOrNull() })
                    }
                }.getOrNull()
            }
            if (state.previewId != linkKey) return@LaunchedEffect
            if (p != null) {
                state.title = p.title
                state.artist = p.artist
                state.thumb = p.thumb
                state.cover = p.thumb?.let { withContext(Dispatchers.Default) { centerSquare(it) } }
            } else if (spotifyId != null) state.searchError = "อ่านข้อมูลเพลงจาก Spotify ไม่ได้ ตรวจสอบอินเทอร์เน็ตหรือลิงก์"
            state.previewLoading = false
        }
        val track = state.spotify
        if (spotifyId != null && track != null && state.candidates == null) {
            state.searching = true
            try {
                val found = runInterruptible(Dispatchers.IO) { findOnYoutube(context, track) }
                if (state.previewId == linkKey) {
                    state.candidates = found
                    state.chosen = found.firstOrNull()?.id
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.searchError = (e as? ImportException)?.message ?: "ค้นหาเพลงบน YouTube ไม่ได้: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                state.searching = false // left mid-search: candidates stay null, so coming back searches again
            }
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
                label = { Text("ลิงก์เพลง") },
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
                supportingText = { Text(state.error ?: "ลิงก์ไฟล์เสียงตรงๆ, YouTube หรือเพลงใน Spotify ขึ้นต้นด้วย https://") },
            )
            val youtube = videoId != null
            val mp3 = spotifyId != null || state.format == YtFormat.MP3 // Spotify songs always become MP3
            if (linkKey != null && state.previewLoading) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Text("กำลังโหลดชื่อเพลงและปก…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            } else if (linkKey != null && state.previewId == linkKey && (state.title.isNotEmpty() || state.cover != null)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp),
                ) {
                    if (mp3) {
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
                        if (mp3) {
                            OutlinedTextField(
                                state.artist, { state.artist = it }, label = { Text("ศิลปิน") }, singleLine = true,
                                enabled = !state.running, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            if (shortLink != null && state.error == null) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Text("กำลังเปิดลิงก์ Spotify…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            if (spotifyId != null) SpotifyMatches(state, onRetry = { state.searchError = null; retry++ })
            state.batchResult?.let { Text(it, color = AccentText, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (youtube) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    YtFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = state.format == f,
                            onClick = { state.format = f },
                            shape = SegmentedButtonDefaults.itemShape(i, YtFormat.entries.size),
                            enabled = !state.running,
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Accent, activeContentColor = Color.White),
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
                        percent != null && state.batchTotal > 0 ->
                            "เพลงที่ ${state.batchIndex + 1}/${state.batchTotal}: ${state.batchTitle} · กำลังแปลง ${(percent * 100).toInt()}%"
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
                    enabled = state.url.isNotBlank() && shortLink == null && (spotifyId == null || state.chosen != null) &&
                        (listPath == null || (state.listFor == listPath && state.list != null && state.picked.isNotEmpty())),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                ) {
                    Text(
                        when {
                            listPath != null -> "แปลง ${state.picked.size} เพลง"
                            linkKey != null -> "แปลง"
                            else -> "นำเข้า"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Below the button and progress, so a 50-song list doesn't push them off screen.
            if (listPath != null) SpotifyListPicker(state, listPath)
            imported?.let { t ->
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp),
                ) {
                    Text("นำเข้าแล้ว", color = AccentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                        ) { Text("เล่น", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Text(
                "ใช้ได้กับไฟล์ที่คุณมีสิทธิ์ เช่น เพลงของคุณเอง ลิงก์แชร์จาก Google Drive ของคุณ หรือเพลงที่เจ้าของอนุญาตให้ดาวน์โหลด" +
                    "\nลิงก์ YouTube แปลงเป็น MP3 (ไม่เกิน 240 นาที) หรือ MP4 360p (ไม่เกิน 30 นาที) ได้" +
                    "\nลิงก์เพลงจาก Spotify: แอปหาเพลงเดียวกันบน YouTube ให้เลือก แล้วแปลงเป็น MP3 (เสียงมาจาก YouTube ไม่ใช่ Spotify)" +
                    "\nลิงก์อัลบั้ม เพลย์ลิสต์ (50 เพลงแรก) หรือศิลปิน (10 เพลงยอดนิยม) ของ Spotify: ติ๊กเพลงที่ต้องการแล้วแปลงทีละเพลงให้" +
                    "\nTikTok และหน้าเว็บทั่วไปยังใช้ไม่ได้",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
        }
    }
}

/** The songs of a Spotify album / playlist / artist, all ticked to start with. */
@Composable
private fun SpotifyListPicker(state: LinkImportState, path: String) {
    val list = state.list
    when {
        state.listError != null -> Text(state.listError!!, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        list == null || state.listFor != path -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
            Text("กำลังโหลดรายชื่อเพลง…", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
        else -> Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(list.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${list.items.size} เพลง" + if (path.startsWith("artist/")) " ยอดนิยม" else "",
                        color = Muted, fontSize = 13.sp,
                    )
                }
                val all = state.picked.size == list.items.size
                TextButton(onClick = { state.picked = if (all) emptySet() else list.items.map { it.id }.toSet() }, enabled = !state.running) {
                    Text(if (all) "ไม่เลือกเลย" else "เลือกทั้งหมด")
                }
            }
            list.items.forEach { item ->
                val on = item.id in state.picked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !state.running) { state.picked = if (on) state.picked - item.id else state.picked + item.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null, enabled = !state.running)
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(item.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.artists, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (path.startsWith("playlist/") && list.items.size >= 50) {
                Text("Spotify ให้ดูได้แค่ 50 เพลงแรกของเพลย์ลิสต์", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** The YouTube recordings found for a Spotify song, best first; the user can pick another before converting. */
@Composable
private fun SpotifyMatches(state: LinkImportState, onRetry: () -> Unit) {
    val found = state.candidates
    when {
        state.searching -> Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
            Text(
                "กำลังหาเพลงนี้บน YouTube… (ครั้งแรกแอปต้องเตรียมตัวแปลงก่อน อาจนานเป็นนาที)",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp),
            )
        }
        state.searchError != null -> Column(Modifier.padding(vertical = 8.dp)) {
            Text(state.searchError!!, color = Danger, fontSize = 13.sp)
            if (state.spotify != null) OutlinedButton(onClick = onRetry, Modifier.padding(top = 4.dp)) { Text("ลองใหม่") }
        }
        found != null && found.isEmpty() -> Text(
            "ไม่เจอเพลงนี้บน YouTube ที่ชื่อ ศิลปิน และความยาวตรงกัน ลองหาคลิปเองแล้ววางลิงก์ YouTube แทน",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        found != null -> Column(Modifier.padding(vertical = 4.dp)) {
            Text("เลือกคลิปที่จะใช้เสียง (เรียงจากตรงที่สุด)", color = Muted, fontSize = 13.sp)
            found.take(3).forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !state.running) { state.chosen = c.id }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.chosen == c.id, onClick = { state.chosen = c.id }, enabled = !state.running)
                    Column(Modifier.weight(1f)) {
                        Text(c.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(c.channel.ifBlank { null }, c.durationSec?.let { formatTime((it * 1000).toLong()) }).joinToString(" · "),
                            color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The phone's own equalizer: on/off, its presets, and one slider per band. */
@Composable
fun EqScreen(audio: AudioSettings, info: EqInfo?, onAudio: (AudioSettings) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("อีควอไลเซอร์", onBack)
        if (info == null || info.bandHz.isEmpty()) {
            Text("มือถือเครื่องนี้ไม่มีอีควอไลเซอร์ให้แอปใช้", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
            return@Column
        }
        // Edited here, sent when a slider is let go: a message per drag frame would flood the player.
        var levels by remember(audio.eqLevels) { mutableStateOf(audio.eqLevels.take(info.bandHz.size).ifEmpty { info.bandHz.map { 0 } }) }
        val commit = { l: List<Int> -> onAudio(audio.copy(eqOn = true, eqLevels = l)) }
        SettingRow(
            Icons.Default.Equalizer, "ใช้อีควอไลเซอร์", "ปรับเสียงทุ้ม กลาง แหลม ให้ถูกหูหรือเข้ากับหูฟัง",
            checked = audio.eqOn, onClick = { onAudio(audio.copy(eqOn = !audio.eqOn, eqLevels = levels)) },
        )
        if (info.presets.isNotEmpty()) {
            SectionLabel("แบบสำเร็จรูป")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(info.presets) { (name, preset) ->
                    FilterChip(
                        selected = audio.eqOn && levels == preset,
                        onClick = { levels = preset; commit(preset) },
                        label = { Text(name) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.White),
                    )
                }
            }
        }
        SectionLabel("ปรับเอง")
        info.bandHz.forEachIndexed { band, hz ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatHz(hz), color = Color.White, fontSize = 14.sp, modifier = Modifier.width(64.dp))
                Slider(
                    value = levels.getOrElse(band) { 0 }.toFloat(),
                    onValueChange = { v -> levels = levels.toMutableList().also { it[band] = v.toInt() } },
                    onValueChangeFinished = { commit(levels) },
                    valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "ความถี่ ${formatHz(hz)}" },
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                )
                Text(
                    "%+.0f dB".format(levels.getOrElse(band) { 0 } / 100f), color = Muted, fontSize = 13.sp,
                    modifier = Modifier.width(56.dp), textAlign = TextAlign.End,
                )
            }
        }
        TextButton(
            onClick = { levels = info.bandHz.map { 0 }; commit(levels) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).heightIn(min = 48.dp),
        ) { Text("รีเซ็ตเป็นเสียงปกติ") }
    }
}

/** Listening time, plays, most played songs and artists, counted by the player (30 s of listening = one play). */
@Composable
fun StatsScreen(summary: ListeningSummary, onBack: () -> Unit, onPlay: (Track) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("สถิติการฟัง", onBack)
        if (summary.plays == 0 && summary.seconds == 0L) {
            Text(
                "ยังไม่มีข้อมูล ฟังเพลงสักพักแล้วกลับมาดูอีกครั้ง\nเพลงที่ฟังเกิน 30 วินาทีนับเป็นการฟัง 1 ครั้ง",
                color = Muted, fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("เวลาที่ฟังทั้งหมด" to formatListening(summary.seconds), "จำนวนครั้งที่ฟัง" to "${summary.plays} ครั้ง").forEach { (label, value) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Surface2).padding(16.dp)) {
                    Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(label, color = Muted, fontSize = 13.sp)
                }
            }
        }
        SectionLabel("เพลงที่ฟังบ่อยที่สุด")
        summary.songs.forEachIndexed { i, (t, stat) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onPlay(t) }.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Muted, fontSize = 15.sp, modifier = Modifier.width(28.dp))
                Artwork(t, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${stat.plays} ครั้ง", color = AccentText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (summary.artists.isNotEmpty()) {
            SectionLabel("ศิลปินที่ฟังนานที่สุด")
            summary.artists.forEach { (name, sec) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatListening(sec), color = Muted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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
                        selectedContainerColor = Accent, selectedLabelColor = Color.White,
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
                        colors = CheckboxDefaults.colors(checkedColor = Accent, checkmarkColor = Color.White, uncheckedColor = Muted),
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
