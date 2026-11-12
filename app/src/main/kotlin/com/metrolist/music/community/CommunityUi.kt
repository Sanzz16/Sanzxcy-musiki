/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Komponen UI bersama untuk Komunitas: avatar, title kustom, badge role, banner foto/video.
 */

package com.metrolist.music.community

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

fun parseHexColor(hex: String, fallback: Color = Color(0xFF9ED7CB)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

val TitleIcons = listOf("none", "star", "crown", "music", "heart", "fire", "bolt", "diamond", "shield", "leaf")
val TitleShapes = listOf("pill", "round", "tag", "outline")

fun titleIconSymbol(icon: String): String =
    when (icon) {
        "star" -> "★"
        "crown" -> "♛"
        "music" -> "♪"
        "heart" -> "♥"
        "fire" -> "🔥"
        "bolt" -> "⚡"
        "diamond" -> "◆"
        "shield" -> "🛡"
        "leaf" -> "🍃"
        else -> ""
    }

fun titleShapeOf(shape: String): Shape =
    when (shape) {
        "round" -> RoundedCornerShape(6.dp)
        "tag" -> RoundedCornerShape(topStart = 12.dp, topEnd = 3.dp, bottomEnd = 12.dp, bottomStart = 3.dp)
        else -> CircleShape
    }

/** Title kustom milik pengguna (nama, warna, bentuk, logo). */
@Composable
fun TitleChip(title: TitleDto, modifier: Modifier = Modifier) {
    val color = parseHexColor(title.color)
    val shape = titleShapeOf(title.shape)
    val outline = title.shape == "outline"
    val symbol = titleIconSymbol(title.icon)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(if (outline) Color.Transparent else color.copy(alpha = 0.22f))
                .border(BorderStroke(1.dp, color.copy(alpha = if (outline) 0.9f else 0.45f)), shape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol.isNotEmpty()) {
            Text(symbol, fontSize = 9.sp, color = color)
            Spacer(Modifier.width(3.dp))
        }
        Text(title.text, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** Badge resmi dari role server (tidak bisa dipalsukan lewat title/nama). */
@Composable
fun RoleBadge(role: String, modifier: Modifier = Modifier) {
    val (label, color) =
        when (role) {
            "owner" -> "OWNER" to Color(0xFFFFC857)
            "admin" -> "ADMIN" to Color(0xFFFF6B6B)
            "staff" -> "STAFF" to Color(0xFF6BB8FF)
            else -> return
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50.dp))
                .background(color.copy(alpha = 0.18f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.78f)), RoundedCornerShape(50.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = 0.65.sp)
    }
}

@Composable
fun UserAvatar(
    url: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackUrls: List<String> = emptyList(),
) {
    val imageUrls = remember(url, fallbackUrls) { (listOf(url) + fallbackUrls).filter { it.isNotBlank() }.distinct() }
    val imageIndex = remember(imageUrls) { androidx.compose.runtime.mutableStateOf(0) }
    val imageFailed = remember(imageUrls) { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (imageIndex.value < imageUrls.size && !imageFailed.value) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrls[imageIndex.value]).build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (imageIndex.value < imageUrls.lastIndex) imageIndex.value++ else imageFailed.value = true
                },
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

/** Banner profil: foto atau video yang berputar tanpa suara. */
@Composable
fun BannerMedia(
    url: String,
    type: String,
    modifier: Modifier = Modifier,
    fallbackUrls: List<String> = emptyList(),
) {
    val imageUrls = remember(url, fallbackUrls) { (listOf(url) + fallbackUrls).filter { it.isNotBlank() }.distinct() }
    val imageIndex = remember(imageUrls) { androidx.compose.runtime.mutableStateOf(0) }
    val imageFailed = remember(imageUrls) { androidx.compose.runtime.mutableStateOf(false) }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (imageUrls.isEmpty()) return@Box
        if (type == "video") LoopingVideo(uri = Uri.parse(imageUrls.first()), modifier = Modifier.fillMaxSize())
        else if (!imageFailed.value) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUrls[imageIndex.value]).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (imageIndex.value < imageUrls.lastIndex) imageIndex.value++ else imageFailed.value = true
                },
            )
        } else {
            Text("Banner tidak dapat dimuat", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Video loop senyap berbasis ExoPlayer + TextureView (crop penuh). Dijeda saat halaman tidak aktif. */
@OptIn(UnstableApi::class)
@Composable
fun LoopingVideo(
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player =
        remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                repeatMode = Player.REPEAT_MODE_ALL
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
        }
    DisposableEffect(player, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> player.play()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(modifier = modifier, factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } })
}

fun formatClock(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))

fun formatDate(ts: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("id", "ID")).format(java.util.Date(ts))
