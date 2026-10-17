package com.secretmelody

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Type a song name, see covers found online (with song, artist, album to tell same names apart), pick one. */
@Composable
fun CoverSearchScreen(initialQuery: String, onBack: () -> Unit, onPick: (CoverResult) -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<CoverResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val search: () -> Unit = {
        if (query.isNotBlank()) {
            loading = true
            error = null
            scope.launch {
                try {
                    results = withContext(Dispatchers.IO) { searchCovers(query) }
                } catch (e: Exception) {
                    error = "ค้นหาไม่ได้ ตรวจสอบอินเทอร์เน็ตแล้วลองใหม่"
                } finally {
                    loading = false
                }
            }
        }
    }
    LaunchedEffect(Unit) { search() }
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ", tint = Color.White) }
            Text("ค้นหาปกเพลง", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("ชื่อเพลง และชื่อศิลปิน (ถ้ารู้)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            trailingIcon = { IconButton(onClick = search) { Icon(Icons.Default.Search, "ค้นหา") } },
        )
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            error != null -> Text(error!!, color = Color(0xFFFF6B6B), modifier = Modifier.padding(16.dp))
            results?.isEmpty() == true -> Text(
                "ไม่พบปกของ \"$query\" ลองพิมพ์ชื่อสั้นลง หรือเพิ่มชื่อศิลปิน",
                color = Muted, modifier = Modifier.padding(16.dp),
            )
            results != null -> {
                Text(
                    "แตะปกที่ใช่ แล้วปรับตำแหน่งในขั้นถัดไป", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(results!!) { r -> CoverResultCard(r) { onPick(r) } }
                }
            }
        }
    }
}

@Composable
private fun CoverResultCard(r: CoverResult, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, r.thumbUrl) {
        value = withContext(Dispatchers.IO) { runCatching { downloadBitmap(r.thumbUrl)?.asImageBitmap() }.getOrNull() }
    }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Surface2), contentAlignment = Alignment.Center) {
            val b = image
            if (b != null) Image(b, contentDescription = "ปกอัลบั้ม ${r.album}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.MusicNote, null, tint = Muted)
        }
        Text(r.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(r.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(r.album, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Drag to move, pinch to zoom; what is inside the square becomes the cover. */
@Composable
fun CropScreen(source: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    var scale by remember(source) { mutableFloatStateOf(1f) }
    var offX by remember(source) { mutableFloatStateOf(0f) }
    var offY by remember(source) { mutableFloatStateOf(0f) }
    val viewDp = (LocalConfigurationWidthDp() - 32).coerceAtMost(420).dp
    val view = with(LocalDensity.current) { viewDp.toPx() }
    val rect = cropRect(source.width, source.height, view, scale, offX, offY)
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "ยกเลิก", tint = Color.White) }
            Text("ปรับตำแหน่งปก", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("ลากเพื่อเลื่อน • ใช้สองนิ้วถ่างเพื่อซูม", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .size(viewDp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .pointerInput(source) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val (mx, my) = maxOffset(source.width, source.height, view, scale)
                        offX = (offX + pan.x).coerceIn(-mx, mx)
                        offY = (offY + pan.y).coerceIn(-my, my)
                    }
                },
        ) {
            drawImage(
                image,
                srcOffset = IntOffset(rect.left, rect.top),
                srcSize = IntSize(rect.size, rect.size),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }
        Spacer(Modifier.height(12.dp))
        Slider(
            value = scale, onValueChange = {
                scale = it
                val (mx, my) = maxOffset(source.width, source.height, view, scale)
                offX = offX.coerceIn(-mx, mx)
                offY = offY.coerceIn(-my, my)
            },
            valueRange = 1f..5f,
            modifier = Modifier.width(viewDp),
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        )
        Text("ซูม", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { scale = 1f; offX = 0f; offY = 0f }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text("รีเซ็ต") }
            Button(
                onClick = { onDone(cropBitmap(source, rect)) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Text("ใช้รูปนี้", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LocalConfigurationWidthDp(): Int = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

/** Sleep timer choices. -1 means "at the end of this song", 0 turns the timer off. */
@Composable
fun SleepTimerDialog(active: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ตั้งเวลาปิดเพลง") },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { m ->
                    Text(
                        "$m นาที", fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(m); onDismiss() }.padding(vertical = 14.dp),
                    )
                }
                Text(
                    "จบเพลงนี้", fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(-1); onDismiss() }.padding(vertical = 14.dp),
                )
                if (active) {
                    Text(
                        "ปิดตัวตั้งเวลา", fontSize = 16.sp, color = Color(0xFFFF6B6B),
                        modifier = Modifier.fillMaxWidth().clickable { onPick(0); onDismiss() }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
    )
}
