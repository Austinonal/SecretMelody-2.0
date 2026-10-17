package com.secretmelody

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import androidx.activity.result.IntentSenderRequest
import android.app.NotificationManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import kotlin.random.Random

val Accent = Color(0xFFA77BFF)
val Bg = Color(0xFF121212)
val Surface2 = Color(0xFF242424)
val Muted = Color(0xFFB3B3B3)
private val Tile = Color(0xFF2A2A2A)
private val TileColors = listOf(
    Color(0xFF8E3FD6), Color(0xFFD6336C), Color(0xFF1E7A5A), Color(0xFF2F5BD3),
    Color(0xFFC2521B), Color(0xFF4F6B7F), Color(0xFF8A2F5E), Color(0xFF4A7A2F),
)

/** A stable colour per name, used for browse tiles and as the fallback when a song has no artwork. */
fun colorFor(key: String): Color = TileColors[Math.floorMod(key.hashCode(), TileColors.size)]

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun has(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) = context.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

/** Asks the system to stop limiting this app's battery use; falls back to the general battery settings page. */
private fun requestUnrestrictedBattery(context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    val intent = if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    }
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

/**
 * Opens the phone maker's auto-start page if this phone has one we know of, else the app's own settings page.
 * shortcut: covers ColorOS/OxygenOS screens known to us; other brands and future ColorOS versions fall back to app settings.
 */
private fun openAutoStartSettings(context: Context) {
    val known = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
    )
    val opened = known.any { (pkg, cls) ->
        runCatching { context.startActivity(Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
    if (!opened) openAppSettings(context)
}

private fun greeting() = when (LocalTime.now().hour) {
    in 5..11 -> "สวัสดีตอนเช้า"
    in 12..16 -> "สวัสดีตอนบ่าย"
    in 17..21 -> "สวัสดีตอนเย็น"
    else -> "ราตรีสวัสดิ์"
}

@Composable
fun App(player: Player?, startTick: Int, incoming: List<Uri> = emptyList(), onIncomingHandled: () -> Unit = {}) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(has(context, audioPermission())) }
    LaunchedEffect(startTick) { granted = has(context, audioPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (!granted) launcher.launch(audioPermission()) }
    LaunchedEffect(granted) {
        // Without it the playback notification is hidden, and some phones then treat the service as killable.
        if (granted && Build.VERSION.SDK_INT >= 33 && !has(context, Manifest.permission.POST_NOTIFICATIONS)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (!granted) {
        PermissionScreen(onAsk = { launcher.launch(audioPermission()) })
        return
    }

    val library = remember { Library(File(context.filesDir, "library.json")) }
    val edits = remember { Edits(File(context.filesDir, "edits.json"), File(context.filesDir, "covers")) }
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) } // bump after importing or deleting files
    val tracks by produceState<List<Track>?>(null, startTick, reload) {
        value = withContext(Dispatchers.IO) { loadTracks(context) }
    }
    var settingsPage by rememberSaveable { mutableStateOf("") } // "", "link", "storage" or "guide"
    val fileImport = remember { FileImportState() }
    val linkImport = remember { LinkImportState() }
    var linkJob by remember { mutableStateOf<Job?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var coverKey by rememberSaveable { mutableStateOf<String?>(null) } // song whose cover is being changed
    var coverStage by rememberSaveable { mutableStateOf("") } // "", "search" or "crop"
    var coverQuery by rememberSaveable { mutableStateOf("") }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var sleepAt by rememberSaveable { mutableLongStateOf(0L) } // when the sleep timer pauses (ms), -1 = end of song, 0 = off
    var showSleep by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<TrackGroup?>(null) } // album or artist
    var openedPlaylist by rememberSaveable { mutableStateOf<Long?>(null) } // playlist id or LIKED_ID
    var addTarget by remember { mutableStateOf<Track?>(null) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDelete by remember { mutableStateOf<Playlist?>(null) }
    val homeState = rememberLazyListState()
    val ui = player?.let { rememberPlayerUi(it) }
    val rawList = tracks.orEmpty()
    val list = remember(rawList, edits.map) { rawList.withEdits(edits.map) }
    val byId = remember(list) { list.associateBy { it.id } }
    val byKey = remember(list) { list.associateBy { it.key } }
    LaunchedEffect(rawList) {
        refreshing = false
        if (rawList.isNotEmpty()) { // data from versions that remembered songs by MediaStore id
            val keyFor = rawList.associate { it.id to it.key }
            library.migrate { keyFor[it] }
            edits.migrate { keyFor[it] }
        }
    }
    val albums = remember(list) { list.albums() }
    val artists = remember(list) { list.artists() }
    val current = ui?.mediaId?.toLongOrNull()?.let { byId[it] }
    val recentIds by produceState(emptyList<String>(), startTick, ui?.mediaId) {
        delay(500) // let the playback process write the play first
        value = withContext(Dispatchers.IO) { readRecent(context.filesDir) }
    }
    val recent = remember(recentIds, byId) { recentIds.mapNotNull { it.toLongOrNull()?.let(byId::get) }.take(9) }

    val liked = TrackGroup(
        "เพลงที่ถูกใจ", "เพลย์ลิสต์ • ${library.liked.size} เพลง",
        library.liked.mapNotNull { byKey[it] }, isArtist = false, playlistId = LIKED_ID,
    )
    val playlistGroups = library.playlists.map { p ->
        TrackGroup(p.name, "เพลย์ลิสต์ • ${p.trackIds.size} เพลง", p.trackIds.mapNotNull { byKey[it] }, false, p.id)
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    val onPlay: (List<Track>, Int) -> Unit = { l, i -> player?.playList(l, i) }
    val onShuffle: (List<Track>) -> Unit = { l ->
        if (l.isNotEmpty() && player != null) {
            player.shuffleModeEnabled = true
            player.playList(l, Random.nextInt(l.size))
        }
    }
    val onOpen: (TrackGroup) -> Unit = { g ->
        if (g.playlistId != null) {
            openedPlaylist = g.playlistId
            opened = null
        } else {
            opened = g
            openedPlaylist = null
        }
        showNowPlaying = false
    }
    val newPlaylist: (Track?) -> Unit = { t ->
        nameDialog = NameDialog("ตั้งชื่อเพลย์ลิสต์", "เพลย์ลิสต์ของฉัน #${library.playlists.size + 1}", "สร้าง") { name ->
            val p = library.create(name)
            if (t != null) {
                library.add(p.id, t.key)
                toast("เพิ่มลง \"${p.name}\" แล้ว")
            } else {
                openedPlaylist = p.id
                opened = null
            }
        }
    }
    val actions = TrackActions(
        playNext = { t ->
            player?.playNext(t)
            toast("จะเล่น \"${t.title}\" ต่อจากเพลงนี้")
        },
        isLiked = { library.isLiked(it.key) },
        toggleLike = { t ->
            library.toggleLike(t.key)
            toast(if (library.isLiked(t.key)) "เพิ่มลงเพลงที่ถูกใจแล้ว" else "ลบออกจากเพลงที่ถูกใจแล้ว")
        },
        addToPlaylist = { addTarget = it },
        goToAlbum = { t -> albums.find { g -> g.tracks.any { it.id == t.id } }?.let(onOpen) },
        goToArtist = { t -> artists.find { it.title == t.artist }?.let(onOpen) },
        edit = { editingId = it.id },
    )

    val runImport: (List<Uri>) -> Unit = { uris ->
        fileImport.busy = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    importFiles(context, uris) { done, total -> fileImport.text = "กำลังนำเข้า ${minOf(done + 1, total)}/$total…" }
                }
                reload++
                fileImport.text = buildString {
                    append("นำเข้า ${r.added.size} เพลงแล้ว")
                    if (r.skipped > 0) append(" • ข้าม ${r.skipped} เพลงที่มีอยู่แล้ว")
                    if (r.notAudio > 0) append(" • ${r.notAudio} ไฟล์ไม่ใช่เสียง")
                    if (r.failed > 0) append(" • ${r.failed} ไฟล์นำเข้าไม่สำเร็จ")
                }
                toast(fileImport.text.orEmpty())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fileImport.text = "นำเข้าไม่สำเร็จ: ${e.message ?: "ไม่ทราบสาเหตุ"}"
            } finally {
                fileImport.busy = false
            }
        }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) runImport(uris)
    }
    LaunchedEffect(incoming) {
        if (incoming.isNotEmpty()) {
            toast("กำลังนำเข้า ${incoming.size} ไฟล์…")
            runImport(incoming)
            onIncomingHandled()
        }
    }
    val startLinkImport: () -> Unit = {
        linkImport.running = true
        linkImport.error = null
        linkImport.resultId = null
        linkImport.done = 0
        linkImport.total = 0
        linkJob = scope.launch {
            try {
                val id = runInterruptible(Dispatchers.IO) {
                    downloadAudio(context, linkImport.url) { done, total ->
                        linkImport.done = done
                        linkImport.total = total
                    }
                }
                reload++
                linkImport.resultId = id
                linkImport.url = ""
            } catch (e: CancellationException) {
                linkImport.error = "ยกเลิกแล้ว"
            } catch (e: ImportException) {
                linkImport.error = e.message
            } catch (e: Exception) {
                linkImport.error = "นำเข้าไม่สำเร็จ: ${e.message ?: "ไม่ทราบสาเหตุ"}"
            } finally {
                linkImport.running = false
            }
        }
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && coverKey != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeImage(context, uri) }
                if (bmp == null) toast("เปิดรูปนี้ไม่ได้") else {
                    cropSource = bmp
                    coverStage = "crop"
                }
            }
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { writeBackup(it, library, edits) } }.isSuccess
            }
            toast(if (ok) "บันทึกไฟล์สำรองแล้ว" else "บันทึกไฟล์สำรองไม่สำเร็จ")
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                pendingRestore = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { readBackup(it, edits.coversDir) }
                }
            } catch (e: ImportException) {
                toast(e.message.orEmpty())
            } catch (e: Exception) {
                toast("อ่านไฟล์สำรองไม่ได้")
            }
        }
    }
    val setSleep: (Int) -> Unit = { m ->
        (player as? MediaController)?.sendCustomCommand(SessionCommand(CMD_SLEEP, Bundle.EMPTY), Bundle().apply { putInt("minutes", m) })
        sleepAt = when {
            m > 0 -> System.currentTimeMillis() + m * 60_000L
            m == -1 -> -1L
            else -> 0L
        }
        toast(when { m > 0 -> "เพลงจะหยุดใน $m นาที"; m == -1 -> "เพลงจะหยุดเมื่อจบเพลงนี้"; else -> "ปิดตัวตั้งเวลาแล้ว" })
    }
    LaunchedEffect(ui?.showPlay) { if (ui?.showPlay == true && sleepAt == -1L) sleepAt = 0L } // "end of song" fired
    val deleteResult = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { reload++ }
    val deleteTracks: (List<Track>) -> Unit = { gone ->
        val ids = gone.map { it.id.toString() }.toSet()
        player?.let { p -> for (i in p.mediaItemCount - 1 downTo 0) if (p.getMediaItemAt(i).mediaId in ids) p.removeMediaItem(i) }
        if (Build.VERSION.SDK_INT >= 30) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, gone.map { it.uri })
            deleteResult.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            scope.launch {
                val failed = withContext(Dispatchers.IO) {
                    gone.count { runCatching { context.contentResolver.delete(it.uri, null, null) }.getOrDefault(0) == 0 }
                }
                reload++
                if (failed > 0) toast("ลบ $failed เพลงไม่ได้ ให้ลบจากตัวจัดการไฟล์แทน")
            }
        }
    }

    // Dialogs float above whichever screen is showing.
    editingId?.let { id ->
        byId[id]?.let { t ->
            EditTrackDialog(
                track = t,
                hasCustomCover = t.coverPath != null,
                onSave = { title, artist -> edits.setText(t.key, title, artist) },
                onSearchCover = {
                    coverKey = t.key
                    coverQuery = listOf(t.title, t.artist.takeUnless { it == "ไม่ทราบศิลปิน" }.orEmpty()).joinToString(" ").trim()
                    editingId = null
                    coverStage = "search"
                },
                onPickCover = {
                    coverKey = t.key
                    editingId = null
                    pickCover.launch("image/*")
                },
                onResetCover = { edits.resetCover(t.key) },
                onDismiss = { editingId = null },
            )
        }
        if (byId[id] == null) LaunchedEffect(id) { editingId = null } // the song is gone (deleted)
    }
    addTarget?.let { t ->
        AddToPlaylistDialog(
            playlists = library.playlists,
            onPick = { p ->
                toast(if (library.add(p.id, t.key)) "เพิ่มลง \"${p.name}\" แล้ว" else "เพลงนี้อยู่ใน \"${p.name}\" แล้ว")
                addTarget = null
            },
            onNew = {
                addTarget = null
                newPlaylist(t)
            },
            onDismiss = { addTarget = null },
        )
    }
    nameDialog?.let { NameDialogView(it, onDismiss = { nameDialog = null }) }
    confirmDelete?.let { p ->
        ConfirmDeleteDialog(
            p,
            onConfirm = {
                library.delete(p.id)
                if (openedPlaylist == p.id) openedPlaylist = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    pendingRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("กู้คืนจากไฟล์สำรอง?") },
            text = { Text("เพลย์ลิสต์ เพลงที่ถูกใจ และชื่อ/ปกที่แก้ไข ในเครื่องนี้จะถูกแทนที่ด้วยข้อมูลในไฟล์ ไฟล์เพลงไม่ถูกแตะ") },
            confirmButton = {
                TextButton(onClick = {
                    library.restore(b.library)
                    edits.restore(b.edits)
                    pendingRestore = null
                    toast("กู้คืนแล้ว")
                }) { Text("กู้คืน") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("ยกเลิก") } },
        )
    }
    if (showSleep) SleepTimerDialog(active = sleepAt != 0L, onPick = setSleep, onDismiss = { showSleep = false })

    val key = coverKey
    if (coverStage == "search" && key != null) {
        BackHandler { coverStage = "" }
        CoverSearchScreen(coverQuery, onBack = { coverStage = "" }, onPick = { r ->
            toast("กำลังโหลดรูป…")
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { runCatching { downloadBitmap(r.bigUrl) }.getOrNull() }
                if (bmp == null) toast("โหลดรูปไม่ได้ ลองรูปอื่น") else {
                    cropSource = bmp
                    coverStage = "crop"
                }
            }
        })
        return
    }
    val source = cropSource
    if (coverStage == "crop") {
        if (source == null || key == null) {
            LaunchedEffect(Unit) { coverStage = "" } // app was restarted mid-crop; the picture is gone
        } else {
            BackHandler { coverStage = ""; cropSource = null }
            CropScreen(source, onCancel = { coverStage = ""; cropSource = null }, onDone = { cropped ->
                scope.launch {
                    withContext(Dispatchers.IO) { edits.setCover(key, cropped) }
                    toast("เปลี่ยนปกแล้ว")
                }
                coverStage = ""
                cropSource = null
            })
            return
        }
    }

    if (player != null && ui != null && current != null) {
        if (showQueue) {
            BackHandler { showQueue = false }
            QueueScreen(player, ui, byId, onClose = { showQueue = false })
            return
        }
        if (showNowPlaying) {
            BackHandler { showNowPlaying = false }
            NowPlaying(
                current, ui, player,
                liked = library.isLiked(current.key),
                onLike = { actions.toggleLike(current) },
                onQueue = { showQueue = true },
                sleepAt = sleepAt,
                onSleep = { showSleep = true },
                onClose = { showNowPlaying = false },
            )
            return
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            val playlistGroup = when (openedPlaylist) {
                null -> null
                LIKED_ID -> liked
                else -> playlistGroups.find { it.playlistId == openedPlaylist }
            }
            // Re-read every time the app comes back to the front, since the user changes these in system settings.
            val bgStatus = remember(startTick, tab) {
                BackgroundStatus(
                    batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                        .isIgnoringBatteryOptimizations(context.packageName),
                    notificationsOn = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
                    swipe = swipeResult(PlayLog.read(context.filesDir), System.currentTimeMillis()),
                )
            }
            val album = opened
            when {
                tracks == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
                playlistGroup != null -> {
                    BackHandler { openedPlaylist = null }
                    val playlist = library.playlists.find { it.id == playlistGroup.playlistId }
                    CollectionScreen(
                        playlistGroup, current?.id, onBack = { openedPlaylist = null }, onPlay, onShuffle, actions,
                        onRemove = { t -> library.removeTrack(playlistGroup.playlistId!!, t.key) },
                        onMove = if (playlist != null) { t, by -> library.moveTrack(playlist.id, t.key, by) } else null,
                        onRename = if (playlist != null) {
                            {
                                nameDialog = NameDialog("เปลี่ยนชื่อเพลย์ลิสต์", playlist.name, "บันทึก") {
                                    library.rename(playlist.id, it)
                                }
                            }
                        } else null,
                        onDelete = if (playlist != null) { { confirmDelete = playlist } } else null,
                        onFindSongs = {
                            openedPlaylist = null
                            tab = 1
                        },
                    )
                }
                album != null -> {
                    BackHandler { opened = null }
                    CollectionScreen(album, current?.id, onBack = { opened = null }, onPlay, onShuffle, actions)
                }
                tab == 3 && settingsPage == "link" -> {
                    BackHandler { settingsPage = "" }
                    LinkImportScreen(
                        linkImport, imported = linkImport.resultId?.let { byId[it] },
                        onBack = { settingsPage = "" },
                        onImport = startLinkImport,
                        onCancel = { linkJob?.cancel() },
                        onPlay = { onPlay(listOf(it), 0) },
                        onEdit = { editingId = it.id },
                    )
                }
                tab == 3 && settingsPage == "storage" -> {
                    BackHandler { settingsPage = "" }
                    val free = remember(reload) { runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(0L) }
                    StorageScreen(list, free, onBack = { settingsPage = "" }, onDelete = deleteTracks)
                }
                tab == 3 && settingsPage == "guide" -> {
                    BackHandler { settingsPage = "" }
                    BackgroundGuideScreen(
                        bgStatus,
                        onBack = { settingsPage = "" },
                        onBattery = { requestUnrestrictedBattery(context) },
                        onNotifications = { openNotificationSettings(context) },
                        onAutoStart = { openAutoStartSettings(context) },
                        onAppSettings = { openAppSettings(context) },
                    )
                }
                tab == 3 -> {
                    SettingsScreen(
                        fileImport,
                        storageSummary = "${list.size} เพลง • ${formatSize(list.sumOf { it.sizeBytes })}",
                        background = bgStatus,
                        onPickFiles = { pickAudio.launch(arrayOf("audio/*", "application/ogg")) },
                        onOpenLink = { settingsPage = "link" },
                        onOpenStorage = { settingsPage = "storage" },
                        onBattery = { requestUnrestrictedBattery(context) },
                        onAppSettings = { openAppSettings(context) },
                        onOpenGuide = { settingsPage = "guide" },
                        onExport = { exportBackup.launch("secretmelody-backup-${java.time.LocalDate.now()}.zip") },
                        onImport = { importBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                    )
                }
                list.isEmpty() && tab != 2 -> EmptyLibrary()
                tab == 0 -> HomeScreen(
                    list, current?.id, homeState, recent,
                    refreshing = refreshing,
                    onRefresh = { refreshing = true; reload++ },
                    shortcuts = (listOfNotNull(liked.takeIf { it.tracks.isNotEmpty() }) + playlistGroups + albums).take(6),
                    albums, artists, onPlay, onShuffle, actions, onOpen,
                )
                tab == 1 -> SearchScreen(list, artists, current?.id, query, { query = it }, onPlay, actions, onOpen)
                else -> LibraryScreen(liked, playlistGroups, artists, albums, onOpen, onCreate = { newPlaylist(null) })
            }
        }
        if (player != null && ui != null && current != null) {
            MiniPlayer(
                current, ui, player,
                liked = library.isLiked(current.key),
                onLike = { actions.toggleLike(current) },
                onOpen = { showNowPlaying = true },
            )
        }
        NavigationBar(containerColor = Bg, tonalElevation = 0.dp) {
            val colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White, selectedTextColor = Color.White,
                unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Color.Transparent,
            )
            val goTo: (Int) -> Unit = {
                tab = it
                opened = null
                openedPlaylist = null
                settingsPage = ""
            }
            NavigationBarItem(
                selected = tab == 0, onClick = { goTo(0) }, colors = colors,
                icon = { Icon(Icons.Default.Home, null) }, label = { Text("หน้าหลัก") },
            )
            NavigationBarItem(
                selected = tab == 1, onClick = { goTo(1) }, colors = colors,
                icon = { Icon(Icons.Default.Search, null) }, label = { Text("ค้นหา") },
            )
            NavigationBarItem(
                selected = tab == 2, onClick = { goTo(2) }, colors = colors,
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }, label = { Text("คอลเลกชัน") },
            )
            NavigationBarItem(
                selected = tab == 3, onClick = { goTo(3) }, colors = colors,
                icon = { Icon(Icons.Default.Settings, null) }, label = { Text("ตั้งค่า") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    tracks: List<Track>,
    currentId: Long?,
    state: LazyListState,
    recent: List<Track>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    shortcuts: List<TrackGroup>,
    albums: List<TrackGroup>,
    artists: List<TrackGroup>,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    actions: TrackActions,
    onOpen: (TrackGroup) -> Unit,
) {
    val newest = remember(tracks) { tracks.sortedByDescending { it.dateAdded }.take(9) }
    // Pull down to re-read the phone's music (after copying new files in).
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                greeting(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            )
        }
        if (shortcuts.size > 1) item { QuickGrid(shortcuts, onOpen) }
        if (recent.isNotEmpty()) {
            item { SectionTitle("เล่นล่าสุด", overline = "ฟังต่อ") }
            item { TrackPages(recent, currentId, onPlay = { i -> onPlay(recent, i) }, actions) }
        }
        item { SectionTitle("เพิ่มล่าสุด", overline = if (recent.isEmpty()) "เริ่มฟัง" else null) }
        item { TrackPages(newest, currentId, onPlay = { i -> onPlay(newest, i) }, actions) }
        if (artists.size > 1) {
            item { SectionTitle("ศิลปินของคุณ") }
            item { CoverRow(artists, onOpen) }
        }
        if (albums.size > 1) {
            item { SectionTitle("อัลบั้ม") }
            item { CoverRow(albums, onOpen) }
        }
        item {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("เพลงทั้งหมด", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${tracks.size} เพลง", color = Muted, fontSize = 13.sp)
                }
                PlayButton(onClick = { onShuffle(tracks) }, icon = Icons.Default.Shuffle, label = "สุ่มเล่นทั้งหมด")
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, t.id == currentId, onClick = { onPlay(tracks, i) }, actions)
        }
    }
    }
}

@Composable
private fun SectionTitle(title: String, overline: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)) {
        if (overline != null) Text(overline, color = Muted, fontSize = 13.sp)
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** Two-column shortcuts to Liked Songs, playlists and recent albums, like the top of Spotify's Home. */
@Composable
private fun QuickGrid(groups: List<TrackGroup>, onOpen: (TrackGroup) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Tile)
                            .clickable { onOpen(g) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupCover(g, Modifier.size(56.dp))
                        Text(
                            g.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Pages of three songs that swipe sideways, with the next page peeking in. */
@Composable
private fun TrackPages(songs: List<Track>, currentId: Long?, onPlay: (Int) -> Unit, actions: TrackActions) {
    val pages = songs.chunked(3)
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val pageWidth = if (pages.size > 1) screen - 40.dp else screen
    LazyRow {
        items(pages) { page ->
            Column(Modifier.width(pageWidth)) {
                page.forEach { t ->
                    TrackRow(t, t.id == currentId, onClick = { onPlay(songs.indexOf(t)) }, actions)
                }
            }
        }
    }
}

@Composable
private fun CoverRow(groups: List<TrackGroup>, onOpen: (TrackGroup) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(groups) { g ->
            Column(Modifier.width(140.dp).clickable { onOpen(g) }) {
                GroupCover(g, Modifier.size(140.dp), px = 400)
                Spacer(Modifier.height(8.dp))
                Text(
                    g.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = if (g.isArtist) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    g.subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = if (g.isArtist) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Album, artist, playlist or Liked Songs page. Playlist-only controls appear when their callbacks are set:
 * [onRemove] for Liked Songs and playlists, [onMove]/[onRename]/[onDelete] for your own playlists.
 */
@Composable
private fun CollectionScreen(
    g: TrackGroup,
    currentId: Long?,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    actions: TrackActions,
    onRemove: ((Track) -> Unit)? = null,
    onMove: ((Track, Int) -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onFindSongs: (() -> Unit)? = null,
) {
    var editing by remember(g.playlistId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val color = g.tracks.firstOrNull()?.let { rememberArtColor(it) } ?: Surface2
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color, Bg), endY = 1200f)),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (editing) {
                    TextButton(onClick = { editing = false }) {
                        Text("เสร็จ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (onMove != null || onRename != null || onDelete != null) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "ตัวเลือกเพลย์ลิสต์", tint = Color.White)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (onMove != null && g.tracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("จัดลำดับ / ลบเพลง") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menu = false; editing = true },
                                )
                            }
                            if (onRename != null) {
                                DropdownMenuItem(
                                    text = { Text("เปลี่ยนชื่อ") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                    onClick = { menu = false; onRename() },
                                )
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("ลบเพลย์ลิสต์") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { menu = false; onDelete() },
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GroupCover(g, Modifier.size(220.dp).shadow(16.dp, RoundedCornerShape(6.dp)), px = 700)
            }
        }
        item {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        g.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            g.isArtist -> "${g.tracks.size} เพลง"
                            g.playlistId != null -> g.subtitle
                            else -> "${g.subtitle} • ${g.tracks.size} เพลง"
                        },
                        color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (g.tracks.isNotEmpty() && !editing) {
                    IconButton(onClick = { onShuffle(g.tracks) }) {
                        Icon(Icons.Default.Shuffle, "สุ่มเล่น", tint = Muted, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    PlayButton(onClick = { onPlay(g.tracks, 0) }, icon = Icons.Default.PlayArrow, label = "เล่น")
                }
            }
        }
        if (g.tracks.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (g.playlistId == LIKED_ID) "กดรูปหัวใจที่เพลงไหนก็ได้ แล้วเพลงจะมาอยู่ที่นี่"
                        else "ยังไม่มีเพลง กด ⋮ ที่เพลงไหนก็ได้ แล้วเลือก \"เพิ่มลงเพลย์ลิสต์\"",
                        color = Muted, textAlign = TextAlign.Center,
                    )
                    if (onFindSongs != null) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onFindSongs,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                        ) { Text("หาเพลง", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        itemsIndexed(g.tracks, key = { _, t -> t.id }) { i, t ->
            if (editing) {
                TrackRow(t, t.id == currentId, onClick = {}, actions) {
                    // Buttons instead of drag-to-reorder: one tap each, and easy to hit.
                    IconButton(onClick = { onMove?.invoke(t, -1) }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, "เลื่อนขึ้น", tint = if (i > 0) Color.White else Surface2)
                    }
                    IconButton(onClick = { onMove?.invoke(t, 1) }, enabled = i < g.tracks.lastIndex) {
                        Icon(
                            Icons.Default.KeyboardArrowDown, "เลื่อนลง",
                            tint = if (i < g.tracks.lastIndex) Color.White else Surface2,
                        )
                    }
                    IconButton(onClick = { onRemove?.invoke(t) }) {
                        Icon(Icons.Default.RemoveCircleOutline, "ลบออกจากเพลย์ลิสต์", tint = Color(0xFFFF6B6B))
                    }
                }
            } else {
                TrackRow(
                    t, t.id == currentId, onClick = { onPlay(g.tracks, i) }, actions,
                    extra = if (onRemove != null) {
                        listOf((if (g.playlistId == LIKED_ID) "ลบออกจากเพลงที่ถูกใจ" else "ลบออกจากเพลย์ลิสต์") to { onRemove(t) })
                    } else emptyList(),
                )
            }
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).clip(CircleShape).background(Accent),
    ) {
        Icon(icon, label, tint = Color.Black, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SearchScreen(
    tracks: List<Track>,
    artists: List<TrackGroup>,
    currentId: Long?,
    query: String,
    onQuery: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    actions: TrackActions,
    onOpen: (TrackGroup) -> Unit,
) {
    val results = remember(tracks, query) { tracks.search(query) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "ค้นหา", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
        )
        // Stays put while the results scroll, like the original.
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Color.Black)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("คุณอยากฟังอะไร", color = Color.DarkGray, fontSize = 16.sp)
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "ล้าง", tint = Color.Black)
                }
            }
        }
        when {
            query.isBlank() -> {
                Text(
                    "เรียกดูตามศิลปิน", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(artists) { g -> BrowseTile(g, onOpen) }
                }
            }
            results.isEmpty() -> Text(
                "ไม่พบ \"$query\"", color = Muted, modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(results, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, t.id == currentId, onClick = { onPlay(results, i) }, actions)
                }
            }
        }
    }
}

/** Colour tile with the name top-left and a tilted cover poking out bottom-right. */
@Composable
private fun BrowseTile(g: TrackGroup, onOpen: (TrackGroup) -> Unit) {
    Box(
        Modifier
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colorFor(g.title))
            .clickable { onOpen(g) },
    ) {
        Text(
            g.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp).fillMaxWidth(0.62f),
        )
        Artwork(
            g.tracks[0],
            Modifier
                .size(68.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 6.dp)
                .rotate(25f),
        )
    }
}

/**
 * One song. The ⋮ menu holds the per-song actions plus any [extra] items;
 * passing [trailing] swaps the menu for custom buttons (playlist edit mode).
 */
@Composable
private fun TrackRow(
    t: Track,
    playing: Boolean,
    onClick: () -> Unit,
    actions: TrackActions,
    extra: List<Pair<String, () -> Unit>> = emptyList(),
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val liked = actions.isLiked(t)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(t, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Icon(
                        Icons.Default.GraphicEq, "กำลังเล่น", tint = Accent,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp),
                    )
                }
                Text(
                    t.title, color = if (playing) Accent else Color.White, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(t.artist, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            trailing()
        } else {
            if (liked) Icon(Icons.Default.Favorite, "ถูกใจแล้ว", tint = Accent, modifier = Modifier.size(18.dp))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "ตัวเลือกของ ${t.title}", tint = Muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(if (liked) "ลบออกจากเพลงที่ถูกใจ" else "เพิ่มลงเพลงที่ถูกใจ",
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder) { menu = false; actions.toggleLike(t) }
                    MenuItem("เพิ่มลงเพลย์ลิสต์", Icons.AutoMirrored.Filled.PlaylistAdd) { menu = false; actions.addToPlaylist(t) }
                    MenuItem("เล่นต่อจากเพลงนี้", Icons.AutoMirrored.Filled.QueueMusic) { menu = false; actions.playNext(t) }
                    MenuItem("ไปที่อัลบั้ม", Icons.Default.Album) { menu = false; actions.goToAlbum(t) }
                    MenuItem("ไปที่ศิลปิน", Icons.Default.Person) { menu = false; actions.goToArtist(t) }
                    MenuItem("แก้ไขข้อมูลเพลง", Icons.Default.Edit) { menu = false; actions.edit(t) }
                    extra.forEach { (label, run) ->
                        MenuItem(label, Icons.Default.RemoveCircleOutline) { menu = false; run() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun LikeButton(liked: Boolean, onLike: () -> Unit, size: Int = 24) {
    IconButton(onClick = onLike) {
        Icon(
            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (liked) "ลบออกจากเพลงที่ถูกใจ" else "เพิ่มลงเพลงที่ถูกใจ",
            tint = if (liked) Accent else Color.White,
            modifier = Modifier.size(size.dp),
        )
    }
}

@Composable
private fun MiniPlayer(t: Track, ui: PlayerUi, player: Player, liked: Boolean, onLike: () -> Unit, onOpen: () -> Unit) {
    val (pos, dur) = rememberProgress(player)
    val total = if (dur > 0) dur else t.durationMs
    Column(
        Modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rememberArtColor(t))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(t, Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    t.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            LikeButton(liked, onLike)
            IconButton(onClick = { player.togglePlay() }) {
                Icon(
                    if (ui.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (ui.showPlay) "เล่น" else "หยุดชั่วคราว",
                    tint = Color.White, modifier = Modifier.size(30.dp),
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (total > 0) (pos.toFloat() / total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(2.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(
    t: Track,
    ui: PlayerUi,
    player: Player,
    liked: Boolean,
    onLike: () -> Unit,
    onQueue: () -> Unit,
    sleepAt: Long,
    onSleep: () -> Unit,
    onClose: () -> Unit,
) {
    val (pos, dur) = rememberProgress(player)
    val total = if (dur > 0) dur else t.durationMs
    var drag by remember { mutableStateOf<Float?>(null) }
    val max = total.coerceAtLeast(1L).toFloat()
    val shown = drag ?: pos.coerceAtMost(total).toFloat()
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(rememberArtColor(t), Bg)))
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, "ปิด", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("กำลังเล่นจากอัลบั้ม", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Text(
                    t.album.ifBlank { "เพลงของคุณ" }, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.weight(1f))
        val cover = RoundedCornerShape(8.dp)
        Artwork(t, Modifier.fillMaxWidth().aspectRatio(1f).shadow(20.dp, cover), px = 1000, shape = cover)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(t.artist, color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LikeButton(liked, onLike, size = 28)
        }
        Spacer(Modifier.height(12.dp))
        Slider(
            value = shown,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { player.seekTo(it.toLong()) }
                drag = null
            },
            valueRange = 0f..max,
            thumb = { Box(Modifier.size(12.dp).background(Color.White, CircleShape)) },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxWidth((shown / max).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shown.toLong()), color = Muted, fontSize = 12.sp)
            Text(formatTime(total), color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { player.shuffleModeEnabled = !ui.shuffle }) {
                Icon(Icons.Default.Shuffle, if (ui.shuffle) "ปิดการสุ่ม" else "สุ่ม", tint = if (ui.shuffle) Accent else Color.White)
            }
            IconButton(onClick = { player.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "เพลงก่อนหน้า", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(
                onClick = { player.togglePlay() },
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White),
            ) {
                Icon(
                    if (ui.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                    if (ui.showPlay) "เล่น" else "หยุดชั่วคราว",
                    tint = Color.Black, modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { player.seekToNext() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "เพลงถัดไป", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = {
                player.repeatMode = when (ui.repeat) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }) {
                Icon(
                    if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "เล่นซ้ำ",
                    tint = if (ui.repeat == Player.REPEAT_MODE_OFF) Color.White else Accent,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            // Recomposes with the progress poll, so the countdown ticks without its own timer.
            val left = if (sleepAt > 0) sleepAt - System.currentTimeMillis() else 0L
            val label = when {
                sleepAt == -1L -> "จบเพลงนี้"
                left > 0 -> formatTime(left)
                else -> null
            }
            IconButton(onClick = onSleep) {
                Icon(Icons.Default.Bedtime, "ตั้งเวลาปิดเพลง", tint = if (label != null) Accent else Color.White)
            }
            if (label != null) Text(label, color = Accent, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "คิว", tint = Color.White)
            }
        }
    }
}

/** A dark colour picked from the song's artwork, so white text stays readable on it. */
@Composable
private fun rememberArtColor(t: Track): Color {
    val context = LocalContext.current
    val fallback = lerp(colorFor(t.album + t.artist), Color.Black, 0.3f)
    val picked by produceState(fallback, t.id, t.coverPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = loadArt(context, t, 64) ?: return@runCatching null
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch
                swatch?.let { lerp(Color(it.rgb), Color.Black, 0.3f) }
            }.getOrNull() ?: fallback
        }
    }
    return animateColorAsState(picked, label = "artColor").value
}

@Composable
fun Artwork(t: Track, modifier: Modifier, px: Int = 200, shape: Shape = RoundedCornerShape(4.dp)) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, t.id, t.coverPath, px) { value = loadArtImage(context, t, px) }
    Box(modifier.clip(shape).background(Surface2), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.MusicNote, null, tint = Muted)
        }
    }
}

@Composable
private fun PermissionScreen(onAsk: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "SecretMelody ต้องขอสิทธิ์อ่านไฟล์เพลงในเครื่องของคุณ", color = Color.White,
            textAlign = TextAlign.Center, fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAsk, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) {
            Text("อนุญาต", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }) {
            Text("ถ้ากดแล้วไม่ขึ้น: เปิดหน้าตั้งค่าแอป", color = Muted)
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Muted, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "ยังไม่พบเพลงในเครื่อง\nคัดลอกไฟล์ mp3 หรือ m4a ลงโฟลเดอร์ Music แล้วเปิดแอปใหม่",
            color = Muted, textAlign = TextAlign.Center,
        )
    }
}
