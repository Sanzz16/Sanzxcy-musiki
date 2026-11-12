@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: pembuat stiker kustom (pilih gambar, crop/zoom persegi, tambah teks, unggah).
 */

package com.metrolist.music.community

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerAwareWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private class TextOverlay(text: String, color: String, size: Float, x: Float, y: Float) {
    var text by mutableStateOf(text)
    var color by mutableStateOf(color)
    var size by mutableFloatStateOf(size) // dalam piksel ekspor (512)
    var x by mutableFloatStateOf(x) // pecahan 0..1 (kiri-atas)
    var y by mutableFloatStateOf(y)
}

private val StickerColors = listOf("#FFFFFF", "#000000", "#FFC857", "#FF6B6B", "#6BB8FF", "#7EE787", "#C792EA", "#FF9E64")
private const val EXPORT = 512

@Composable
fun StickerMakerScreen(
    state: CommunityState,
    onBack: () -> Unit,
) {
    val token = state.token ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val insets = LocalPlayerAwareWindowInsets.current

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val overlays = remember { mutableStateListOf<TextOverlay>() }
    var selected by remember { mutableStateOf(-1) }
    var side by remember { mutableFloatStateOf(1f) }
    var addDialog by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 1600) }
            if (bmp == null) message = "Gambar tidak bisa dibaca." else { bitmapToCrop = bmp; message = null }
        }
    }

    bitmapToCrop?.let { selectedBitmap ->
        CropBitmapDialog(
            bitmap = selectedBitmap,
            aspectRatio = 1f,
            isAvatar = true,
            fixedAspectRatio = true,
            title = "Pangkas gambar stiker (1:1)",
            onDismiss = { bitmapToCrop = null },
            onConfirm = { cropped ->
                bitmapToCrop = null
                bitmap = cropped
                zoom = 1f
                pan = Offset.Zero
                overlays.clear()
                selected = -1
            },
        )
    }

    /** Skala dasar agar gambar menutup penuh area persegi (center-crop). */
    fun baseScale(b: Bitmap, s: Float) = max(s / b.width, s / b.height)
    fun clampPan(b: Bitmap, s: Float, z: Float, p: Offset): Offset {
        val total = baseScale(b, s) * z
        val maxX = max(0f, (b.width * total - s) / 2f)
        val maxY = max(0f, (b.height * total - s) / 2f)
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Tambah teks") },
            text = { OutlinedTextField(newText, { newText = it.take(40) }, singleLine = true, label = { Text("Teks stiker") }, shape = RoundedCornerShape(16.dp)) },
            confirmButton = {
                TextButton(enabled = newText.isNotBlank(), onClick = {
                    overlays.add(TextOverlay(newText.trim(), "#FFFFFF", 56f, 0.12f, 0.72f)); selected = overlays.lastIndex
                    newText = ""; addDialog = false
                }) { Text("Tambah") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Batal") } },
        )
    }

    fun exportAndUpload() {
        val b = bitmap ?: return
        scope.launch {
            busy = true; message = null
            val result = withContext(Dispatchers.Default) {
                val out = Bitmap.createBitmap(EXPORT, EXPORT, Bitmap.Config.ARGB_8888)
                val c = AndroidCanvas(out)
                val k = EXPORT / side // piksel pratinjau -> piksel ekspor
                val total = baseScale(b, side) * zoom
                val p = clampPan(b, side, zoom, pan)
                val w = b.width * total
                val h = b.height * total
                val left = (side - w) / 2f + p.x
                val top = (side - h) / 2f + p.y
                val src = android.graphics.Rect(0, 0, b.width, b.height)
                val dst = android.graphics.RectF(left * k, top * k, (left + w) * k, (top + h) * k)
                c.drawBitmap(b, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                overlays.forEach { o ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.parseColor(o.color)
                        textSize = o.size
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        setShadowLayer(o.size * 0.12f, o.size * 0.04f, o.size * 0.04f, android.graphics.Color.BLACK)
                    }
                    c.drawText(o.text, o.x * EXPORT, o.y * EXPORT - paint.ascent(), paint)
                }
                compressSticker(out, 650 * 1024)
            }
            val up = CommunityApi.upload(token, "sticker", result.second, result.first)
            if (!up.ok) { message = up.error; busy = false; return@launch }
            val add = CommunityApi.addSticker(token, up.json.getString("url"))
            busy = false
            if (add.ok) onBack() else message = add.error
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(insets.only(WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Kembali") }
            Text("Buat stiker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        Box(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .onSizeChanged { side = it.width.toFloat() }
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                            val b = bitmap ?: return@detectTransformGestures
                            zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            pan = clampPan(b, side, zoom, pan + gesturePan)
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b == null) {
                Text("Pilih gambar untuk mulai", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val total = baseScale(b, side) * zoom
                    val p = clampPan(b, side, zoom, pan)
                    val w = b.width * total
                    val h = b.height * total
                    drawImage(
                        image = b.asImageBitmap(),
                        dstOffset = IntOffset(((side - w) / 2f + p.x).roundToInt(), ((side - h) / 2f + p.y).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    )
                }
                overlays.forEachIndexed { index, o ->
                    val px = with(density) { (o.size * side / EXPORT).toSp() }
                    Text(
                        text = o.text,
                        style = TextStyle(fontSize = px, fontWeight = FontWeight.Bold, color = parseHexColor(o.color), shadow = Shadow(Color.Black, Offset(3f, 3f), 8f)),
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .offset { IntOffset((o.x * side).roundToInt(), (o.y * side).roundToInt()) }
                                .then(if (selected == index) Modifier.border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp)) else Modifier)
                                .clickable { selected = index }
                                .pointerInput(index) {
                                    detectDragGestures(onDragStart = { selected = index }) { change, drag ->
                                        change.consume()
                                        o.x = (o.x + drag.x / side).coerceIn(0f, 0.95f)
                                        o.y = (o.y + drag.y / side).coerceIn(0f, 0.95f)
                                    }
                                },
                    )
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Geser dan cubit gambar untuk crop. Seret teks untuk memindahkannya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(if (bitmap == null) "Pilih gambar" else "Ganti gambar")
                }
                OutlinedButton(enabled = bitmap != null, onClick = { addDialog = true }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Tambah teks") }
            }
            val sel = overlays.getOrNull(selected)
            if (sel != null) {
                Text("Warna teks", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StickerColors.forEach { hex ->
                        Box(Modifier.size(32.dp).clip(CircleShape).background(parseHexColor(hex)).border(if (sel.color == hex) 3.dp else 1.dp, if (sel.color == hex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f), CircleShape).clickable { sel.color = hex })
                    }
                }
                Text("Ukuran teks", style = MaterialTheme.typography.labelLarge)
                Slider(value = sel.size, onValueChange = { sel.size = it }, valueRange = 24f..110f)
                OutlinedButton(onClick = { overlays.removeAt(selected); selected = -1 }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Hapus teks ini", color = MaterialTheme.colorScheme.error) }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = bitmap != null && !busy, onClick = { exportAndUpload() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan ke stikerku") }
        }
    }
}
