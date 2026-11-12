@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: profil publik (sheet) dan editor profil (foto, banner foto/video, nama, bio, title kustom).
 */

package com.metrolist.music.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.theme.LocalLiquidGlass
import com.sanzxcy.music.com.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.floor

// ───────────────────────── Util gambar ─────────────────────────

internal fun decodeBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = min(1f, maxEdge.toFloat() / max(w, h))
                dec.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                dec.isMutableRequired = true
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true })
            }
        }
    }.getOrNull()

internal fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
    val side = min(src.width, src.height)
    val x = (src.width - side) / 2
    val y = (src.height - side) / 2
    val cropped = Bitmap.createBitmap(src, x, y, side, side)
    return if (side == size) cropped else Bitmap.createScaledBitmap(cropped, size, size, true)
}

/** Kompres sampai <= maxBytes: turunkan kualitas JPEG, lalu perkecil ukuran bila perlu. */
internal fun compressJpeg(bitmap: Bitmap, maxBytes: Int): ByteArray {
    var bmp = bitmap
    var quality = 88
    while (true) {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (out.size() <= maxBytes || (quality <= 40 && min(bmp.width, bmp.height) < 200)) return out.toByteArray()
        if (quality > 45) quality -= 10
        else bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.8f).toInt(), (bmp.height * 0.8f).toInt(), true)
    }
}

internal fun uploadedUrls(response: JSONObject): List<String> =
    response.optJSONArray("urls")?.let { array ->
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.distinct()
    }?.ifEmpty { null } ?: listOf(response.optString("url")).filter { it.isNotBlank() }

private fun uploadSuccessText(response: JSONObject, label: String): String {
    val copies = response.optInt("replicaCount", 1)
    val target = response.optInt("replicaTarget", 2)
    return if (copies < target) "$label ($copies/$target salinan; Supabase belum aktif atau gagal)." else "$label ($copies salinan)."
}

/** Sticker/PNG: pakai PNG; jika terlalu besar, WEBP. Mengembalikan (bytes, contentType). */
internal fun compressSticker(bitmap: Bitmap, maxBytes: Int): Pair<ByteArray, String> {
    val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    if (png.size <= maxBytes) return png to "image/png"
    var q = 90
    while (true) {
        @Suppress("DEPRECATION")
        val webp = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.WEBP, q, it) }.toByteArray()
        if (webp.size <= maxBytes || q <= 30) return webp to "image/webp"
        q -= 15
    }
}

private suspend fun readBytes(context: Context, uri: Uri, limit: Int): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > limit) return@use null
                }
                out.toByteArray()
            }
        }.getOrNull()
    }

@Composable
internal fun CropBitmapDialog(
    bitmap: Bitmap,
    aspectRatio: Float,
    isAvatar: Boolean,
    fixedAspectRatio: Boolean = false,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val ratios = if (isAvatar) listOf(stringResource(R.string.community_crop_ratio_1_1) to 1f)
    else if (fixedAspectRatio) listOf(stringResource(R.string.community_crop_ratio_16_9) to aspectRatio)
    else listOf(
        stringResource(R.string.community_crop_ratio_banner_16_6) to 16f / 6f,
        stringResource(R.string.community_crop_ratio_16_9) to 16f / 9f,
        stringResource(R.string.community_crop_ratio_4_3) to 4f / 3f,
        stringResource(R.string.community_crop_ratio_1_1) to 1f,
    )
    var selectedRatio by remember(bitmap, aspectRatio) { mutableStateOf(aspectRatio) }
    var rotation by remember(bitmap) { mutableStateOf(0) }
    var flipHorizontal by remember(bitmap) { mutableStateOf(false) }
    var zoom by remember(bitmap, selectedRatio, rotation, flipHorizontal) { mutableStateOf(1f) }
    var pan by remember(bitmap, selectedRatio, rotation, flipHorizontal) { mutableStateOf(Offset.Zero) }
    var viewport by remember(bitmap) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val displayedBitmap = remember(bitmap, rotation, flipHorizontal) {
        var transformed = bitmap
        if (rotation != 0) {
            transformed = Bitmap.createBitmap(
                transformed, 0, 0, transformed.width, transformed.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true,
            )
        }
        if (flipHorizontal) {
            transformed = Bitmap.createBitmap(
                transformed, 0, 0, transformed.width, transformed.height,
                Matrix().apply { setScale(-1f, 1f) }, true,
            )
        }
        transformed
    }
    val image = remember(displayedBitmap) { displayedBitmap.asImageBitmap() }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.community_crop_gesture_hint), style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .aspectRatio(selectedRatio)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .onSizeChanged { viewport = it }
                        .pointerInput(displayedBitmap, viewport, selectedRatio) {
                            detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                val centerX = size.width / 2f
                                val centerY = size.height / 2f
                                val proposedPanX = centroid.x - centerX - (centroid.x - centerX - pan.x) * zoomChange + panChange.x
                                val proposedPanY = centroid.y - centerY - (centroid.y - centerY - pan.y) * zoomChange + panChange.y
                                val geometry = calculateProfileCropGeometry(
                                    displayedBitmap.width, displayedBitmap.height,
                                    size.width.toFloat(), size.height.toFloat(), nextZoom,
                                    proposedPanX, proposedPanY,
                                )
                                pan = Offset(geometry.panX, geometry.panY)
                                zoom = nextZoom
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val geometry = calculateProfileCropGeometry(
                            displayedBitmap.width, displayedBitmap.height,
                            size.width, size.height, zoom, pan.x, pan.y,
                        )
                        drawImage(
                            image = image,
                            dstOffset = IntOffset(geometry.imageLeft.toInt(), geometry.imageTop.toInt()),
                            dstSize = IntSize(geometry.imageWidth.toInt(), geometry.imageHeight.toInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                    }
                }
                if (!fixedAspectRatio) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ratios.forEach { (label, ratio) ->
                            FilterChip(
                                selected = selectedRatio == ratio,
                                onClick = { selectedRatio = ratio },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rotation = (rotation + 90) % 360 }) { Text(stringResource(R.string.community_crop_rotate_90)) }
                    OutlinedButton(onClick = { flipHorizontal = !flipHorizontal }) {
                        Text(stringResource(if (flipHorizontal) R.string.community_crop_flip_horizontal_active else R.string.community_crop_flip_horizontal))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = viewport.width > 0 && viewport.height > 0,
                onClick = {
                    val geometry = calculateProfileCropGeometry(
                        displayedBitmap.width, displayedBitmap.height,
                        viewport.width.toFloat(), viewport.height.toFloat(), zoom, pan.x, pan.y,
                    )
                    val left = floor(geometry.sourceLeft).toInt().coerceIn(0, displayedBitmap.width - 1)
                    val top = floor(geometry.sourceTop).toInt().coerceIn(0, displayedBitmap.height - 1)
                    val right = ceil(geometry.sourceRight).toInt().coerceIn(left + 1, displayedBitmap.width)
                    val bottom = ceil(geometry.sourceBottom).toInt().coerceIn(top + 1, displayedBitmap.height)
                    onConfirm(Bitmap.createBitmap(displayedBitmap, left, top, right - left, bottom - top))
                },
            ) { Text(stringResource(R.string.community_crop_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.community_crop_cancel)) } },
    )
}

// ───────────────────────── Profil publik ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    username: String,
    state: CommunityState,
    onDismiss: () -> Unit,
    onMessage: (String, UserDto) -> Unit,
) {
    val token = state.token ?: return
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<UserDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(username) {
        val r = CommunityApi.profile(token, username)
        if (r.ok) user = parseUser(r.json.getJSONObject("user")) else error = r.error
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val u = user
        when {
            u != null -> Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Box(Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp))) {
                    BannerMedia(u.bannerUrl, u.bannerType, Modifier.fillMaxSize(), fallbackUrls = u.bannerUrls)
                }
                UserAvatar(
                    u.avatarUrl, u.displayName, 84.dp,
                    Modifier.padding(start = 32.dp).offset(y = (-42).dp).border(4.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                    fallbackUrls = u.avatarUrls,
                )
                Column(Modifier.padding(horizontal = 24.dp).offset(y = (-34).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        RoleBadge(u.role, Modifier.padding(start = 8.dp))
                    }
                    Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    u.title?.let { TitleChip(it, Modifier.padding(top = 8.dp)) }
                    if (u.bio.isNotBlank()) Text(u.bio, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    Text("Bergabung ${formatDate(u.joinedAt)}", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (u.username != state.me?.username) {
                        Button(
                            onClick = { scope.launch { val r = CommunityApi.dmRoom(token, u.username); if (r.ok) onMessage(r.json.getString("room"), u) } },
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(50.dp),
                        ) { Text("Kirim pesan") }
                    }
                }
            }
            error != null -> Text(error!!, modifier = Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            else -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

// ───────────────────────── Editor profil ─────────────────────────

private val TitlePalette = listOf("#9ED7CB", "#FFC857", "#FF6B6B", "#6BB8FF", "#C792EA", "#FF9E64", "#7EE787", "#F78FB3", "#FFFFFF", "#B0BEC5")

@Composable
fun EditProfileScreen(
    state: CommunityState,
    onBack: () -> Unit,
    onStickerMaker: () -> Unit,
    onChangePassword: () -> Unit,
) {
    val token = state.token ?: return
    val me = state.me ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val insets = LocalPlayerAwareWindowInsets.current

    var name by remember(me.username) { mutableStateOf(me.displayName) }
    var bio by remember(me.username) { mutableStateOf(me.bio) }
    var recoveryEmail by remember(me.username) { mutableStateOf(me.email) }
    var recoveryCode by remember(me.username) { mutableStateOf("") }
    var emailCodeSent by remember(me.username) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errorDialog by remember { mutableStateOf<String?>(null) }
    var avatarToCrop by remember { mutableStateOf<Bitmap?>(null) }
    var bannerToCrop by remember { mutableStateOf<Bitmap?>(null) }

    var tText by remember(me.title) { mutableStateOf(me.title?.text.orEmpty()) }
    var tColor by remember(me.title) { mutableStateOf(me.title?.color ?: "#9ED7CB") }
    var tShape by remember(me.title) { mutableStateOf(me.title?.shape ?: "pill") }
    var tIcon by remember(me.title) { mutableStateOf(me.title?.icon ?: "none") }

    fun applyResult(r: ApiResult, okText: String) {
        if (r.ok) {
            val user = r.json.optJSONObject("user")
            if (user == null) message = "Respons profil dari server tidak valid."
            else { state.updateMe(parseUser(user)); message = okText }
        } else if (r.code == "reserved_title" || r.code == "reserved_name") errorDialog = r.error
        else message = r.error
    }

    suspend fun pushProfile(body: JSONObject, okText: String) {
        busy = true
        try {
            val result = CommunityApi.updateProfile(token, body)
            if (result.ok) {
                val writtenJson = result.json.optJSONObject("user")
                if (writtenJson == null) {
                    message = "Respons profil dari server tidak valid."
                } else {
                    val written = parseUser(writtenJson)
                    state.updateMe(written)
                    val readBack = CommunityApi.profile(token, written.username)
                    val confirmedJson = readBack.json.optJSONObject("user")
                    if (readBack.ok && confirmedJson != null) {
                        val confirmed = parseUser(confirmedJson)
                        state.updateMe(confirmed)
                        val avatarMismatch = body.has("avatarUrl") && confirmed.avatarUrl != body.optString("avatarUrl", "")
                        val bannerMismatch = body.has("bannerUrl") && confirmed.bannerUrl != body.optString("bannerUrl", "")
                        message = if (avatarMismatch || bannerMismatch) {
                            "Server belum mengembalikan foto/banner terbaru. Periksa koneksi server dan konfigurasi storage, lalu coba lagi."
                        } else {
                            okText
                        }
                    } else {
                        message = "$okText, tetapi profil belum dapat diverifikasi ulang. Buka kembali profil untuk memuat data terbaru."
                    }
                }
            } else {
                applyResult(result, okText)
            }
        } catch (e: Exception) {
            message = e.message?.takeIf(String::isNotBlank) ?: "Profil gagal disimpan. Coba lagi."
        } finally {
            busy = false
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; message = null
            avatarToCrop = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 1600) }
            if (avatarToCrop == null) message = "Gambar tidak bisa dibaca."
            busy = false
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = null
            try {
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (mime.startsWith("video/")) {
                    val bytes = readBytes(context, uri, 3 * 1024 * 1024)
                    if (bytes == null) message = "Video terlalu besar. Maksimal 3 MB (pendekkan/kompres dulu)."
                    else if (mime != "video/mp4") message = "Gunakan video berformat MP4."
                    else {
                        val up = CommunityApi.upload(token, "banner", "video/mp4", bytes)
                        if (up.ok) {
                            val urls = uploadedUrls(up.json)
                            if (urls.isNotEmpty()) pushProfile(JSONObject().put("bannerUrl", urls.first()).put("bannerUrls", JSONArray(urls)).put("bannerType", "video"), uploadSuccessText(up.json, "Banner video diperbarui"))
                            else message = "Upload selesai tetapi URL video tidak diterima."
                        } else message = up.error
                    }
                } else {
                    bannerToCrop = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 1920) }
                    if (bannerToCrop == null) message = "Gambar tidak bisa dibaca."
                }
            } catch (e: Exception) {
                message = e.message?.takeIf(String::isNotBlank) ?: "Media tidak dapat dibaca atau diunggah."
            } finally {
                busy = false
            }
        }
    }

    avatarToCrop?.let { bitmap ->
        CropBitmapDialog(
            bitmap = bitmap,
            aspectRatio = 1f,
            isAvatar = true,
            title = "Atur crop foto profil",
            onDismiss = { avatarToCrop = null },
            onConfirm = { cropped ->
                avatarToCrop = null
                scope.launch {
                    busy = true
                    message = null
                    try {
                        val bytes = withContext(Dispatchers.IO) { compressJpeg(cropped, 420 * 1024) }
                        val up = CommunityApi.upload(token, "avatar", "image/jpeg", bytes)
                        if (up.ok) {
                            val urls = uploadedUrls(up.json)
                            if (urls.isNotEmpty()) pushProfile(JSONObject().put("avatarUrl", urls.first()).put("avatarUrls", JSONArray(urls)), uploadSuccessText(up.json, "Foto profil diperbarui"))
                            else message = "Upload selesai tetapi URL gambar tidak diterima."
                        } else message = up.error
                    } catch (e: Exception) {
                        message = e.message?.takeIf(String::isNotBlank) ?: "Foto profil gagal diunggah."
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }

    bannerToCrop?.let { bitmap ->
        CropBitmapDialog(
            bitmap = bitmap,
            aspectRatio = 16f / 6f,
            isAvatar = false,
            title = "Atur crop banner",
            onDismiss = { bannerToCrop = null },
            onConfirm = { cropped ->
                bannerToCrop = null
                scope.launch {
                    busy = true
                    message = null
                    try {
                        val bytes = withContext(Dispatchers.IO) { compressJpeg(cropped, 2 * 1024 * 1024) }
                        val up = CommunityApi.upload(token, "banner", "image/jpeg", bytes)
                        if (up.ok) {
                            val urls = uploadedUrls(up.json)
                            if (urls.isNotEmpty()) pushProfile(JSONObject().put("bannerUrl", urls.first()).put("bannerUrls", JSONArray(urls)).put("bannerType", "image"), uploadSuccessText(up.json, "Banner diperbarui"))
                            else message = "Upload selesai tetapi URL gambar tidak diterima."
                        } else message = up.error
                    } catch (e: Exception) {
                        message = e.message?.takeIf(String::isNotBlank) ?: "Banner gagal diunggah."
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }

    errorDialog?.let {
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text("Ditolak") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { errorDialog = null }) { Text("Mengerti") } },
        )
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(insets.only(WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Kembali") }
            Text("Profil saya", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))) {
                BannerMedia(me.bannerUrl, me.bannerType, Modifier.fillMaxSize(), fallbackUrls = me.bannerUrls)
            }
            Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallPill("Ganti banner") { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
                if (me.bannerUrl.isNotBlank()) SmallPill("Hapus") { scope.launch { pushProfile(JSONObject().put("bannerUrl", ""), "Banner dihapus") } }
            }
        }
        Row(Modifier.padding(horizontal = 32.dp).offset(y = (-36).dp), verticalAlignment = Alignment.Bottom) {
            UserAvatar(me.avatarUrl, me.displayName, 84.dp, Modifier.border(4.dp, MaterialTheme.colorScheme.background, CircleShape).clickable {
                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, fallbackUrls = me.avatarUrls)
            Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${me.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RoleBadge(me.role, Modifier.padding(start = 6.dp))
                }
                Text("Bergabung ${formatDate(me.joinedAt)} • ketuk foto untuk mengganti", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SmallPill("Ubah foto profil") {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-24).dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(24) }, label = { Text("Nama tampilan") }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(160) }, label = { Text("Deskripsi (${bio.length}/160)") }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(enabled = !busy, onClick = { scope.launch { pushProfile(JSONObject().put("displayName", name).put("bio", bio), "Profil disimpan") } }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Simpan nama & deskripsi") }

            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().glassBorder(RoundedCornerShape(24.dp), LocalLiquidGlass.current)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Email Gmail (opsional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Verifikasi email agar bisa login memakai alamat email dan menerima kode reset password. Akun tetap bisa dipakai dengan username tanpa email.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(recoveryEmail, { recoveryEmail = it.trim().lowercase().take(254) }, label = { Text("Alamat email") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    if (me.emailVerified && recoveryEmail.equals(me.email, true)) {
                        Text("Email terverifikasi — bisa dipakai untuk login dan pemulihan password.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (emailCodeSent) {
                        OutlinedTextField(recoveryCode, { recoveryCode = it.filter(Char::isDigit).take(6) }, label = { Text("Kode verifikasi 6 digit") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && recoveryEmail.contains('@'), onClick = {
                            scope.launch {
                                busy = true; message = null
                                val r = CommunityApi.requestEmailCode(token, recoveryEmail)
                                if (r.ok && r.json.optBoolean("verified")) {
                                    val updated = parseUser(r.json.getJSONObject("user"))
                                    state.updateMe(updated); recoveryEmail = updated.email
                                    message = "Email ini sudah terverifikasi."
                                } else if (r.ok) { emailCodeSent = true; message = "Kode verifikasi dikirim. Periksa Inbox/Spam." } else message = r.error
                                busy = false
                            }
                        }, modifier = Modifier.weight(1f).height(46.dp)) { Text("Kirim kode") }
                        if (emailCodeSent) {
                            OutlinedButton(enabled = !busy && recoveryCode.length == 6, onClick = {
                                scope.launch {
                                    busy = true; message = null
                                    val r = CommunityApi.verifyEmailCode(token, recoveryEmail, recoveryCode)
                                    if (r.ok) {
                                        val updated = parseUser(r.json.getJSONObject("user"))
                                        state.updateMe(updated); recoveryEmail = updated.email; recoveryCode = ""; emailCodeSent = false
                                        message = "Email terverifikasi. Kamu bisa login menggunakan Gmail atau username."
                                    } else message = r.error
                                    busy = false
                                }
                            }, modifier = Modifier.height(46.dp)) { Text("Verifikasi") }
                        }
                    }
                }
            }

            // ── Title kustom ──
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().glassBorder(RoundedCornerShape(24.dp), LocalLiquidGlass.current)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Title kustom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Muncul di samping namamu di Global Chat. Kata Owner/Admin/Staff dilarang untuk pengguna biasa, dengan font apa pun.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(tText, { tText = it.take(16) }, label = { Text("Teks title (maks 16)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Text("Warna", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TitlePalette.forEach { hex ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(parseHexColor(hex))
                                    .border(if (tColor.equals(hex, true)) 3.dp else 1.dp, if (tColor.equals(hex, true)) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.4f), CircleShape)
                                    .clickable { tColor = hex },
                            )
                        }
                    }
                    OutlinedTextField(tColor, { v -> tColor = v.take(7).let { if (it.startsWith("#")) it else "#$it" } }, label = { Text("Hex warna") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    Text("Bentuk", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TitleShapes.forEach { s -> FilterChip(selected = tShape == s, onClick = { tShape = s }, label = { Text(when (s) { "pill" -> "Kapsul"; "round" -> "Kotak bulat"; "tag" -> "Tag"; else -> "Garis" }) }) }
                    }
                    Text("Logo", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TitleIcons.forEach { i -> FilterChip(selected = tIcon == i, onClick = { tIcon = i }, label = { Text(if (i == "none") "Tanpa" else titleIconSymbol(i)) }) }
                    }
                    if (tText.isNotBlank()) {
                        Text("Pratinjau", style = MaterialTheme.typography.labelLarge)
                        TitleChip(TitleDto(tText, tColor, tShape, tIcon))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && tText.isNotBlank(), onClick = {
                            scope.launch { pushProfile(JSONObject().put("title", JSONObject().put("text", tText.trim()).put("color", tColor).put("shape", tShape).put("icon", tIcon)), "Title disimpan") }
                        }, modifier = Modifier.weight(1f).height(46.dp)) { Text("Simpan title") }
                        OutlinedButton(enabled = !busy && me.title != null, onClick = {
                            scope.launch { pushProfile(JSONObject().put("title", JSONObject.NULL), "Title dihapus"); tText = "" }
                        }, modifier = Modifier.height(46.dp)) { Text("Hapus") }
                    }
                }
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            OutlinedButton(onClick = onStickerMaker, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Buat stiker kustom") }
            OutlinedButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Ganti password") }
            OutlinedButton(onClick = { scope.launch { state.logout(); onBack() } }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Keluar akun", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Black.copy(alpha = 0.55f), shape = CircleShape) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
