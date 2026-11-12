/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: Custom Wallpaper Home dan pengaturan Visual Style.
 */

package com.metrolist.music.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.metrolist.music.ui.component.IosSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.compose.material3.Slider
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sanzxcy.music.com.R
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Path file wallpaper aktif di penyimpanan internal aplikasi (kosong = default). */
val CustomWallpaperPathKey = stringPreferencesKey("sanzxcyWallpaperPath")

/** "editorial" atau "liquid_glass". */
val WallpaperDimKey = floatPreferencesKey("sanzxcyWallpaperDim")

val SanzxcyVisualStyleKey = stringPreferencesKey("sanzxcyVisualStyle")

/** Saklar efek kaca. OFF = surface solid ringan. */
val LiquidGlassEnabledKey = booleanPreferencesKey("sanzxcyLiquidGlass")

const val VISUAL_STYLE_EDITORIAL = "editorial"
const val VISUAL_STYLE_LIQUID_GLASS = "liquid_glass"

private const val WALLPAPER_DIR = "wallpaper"
private const val MAX_WALLPAPER_EDGE = 1440

private fun wallpaperFile(context: Context, name: String) = File(File(context.filesDir, WALLPAPER_DIR), name)

/**
 * Menyalin gambar pilihan pengguna ke penyimpanan internal dalam ukuran terbatas,
 * sehingga tidak bergantung pada izin akses URI dan tidak memuat bitmap raksasa.
 * Dijalankan di IO. Mengembalikan path file baru, atau null jika gagal.
 */
private suspend fun importWallpaper(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > MAX_WALLPAPER_EDGE * 2 || bounds.outHeight / sample > MAX_WALLPAPER_EDGE * 2) {
                sample *= 2
            }
            val bitmap =
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@runCatching null

            val scale = minOf(1f, MAX_WALLPAPER_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
            val finalBitmap =
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else {
                    bitmap
                }

            val dir = File(context.filesDir, WALLPAPER_DIR).apply { mkdirs() }
            // Nama unik agar cache gambar tidak menampilkan wallpaper lama.
            val target = File(dir, "home_${System.currentTimeMillis()}.jpg")
            target.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            // Hapus wallpaper lama, hanya di folder wallpaper (data musik tidak disentuh).
            dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

private suspend fun clearWallpaperFiles(context: Context) =
    withContext(Dispatchers.IO) {
        File(context.filesDir, WALLPAPER_DIR).listFiles()?.forEach { it.delete() }
    }

@Composable
private fun rememberWallpaperBitmap(path: String): androidx.compose.runtime.State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, path) {
        value =
            if (path.isBlank()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
                    }.getOrNull()
                }
            }
    }

/** Wallpaper Home. Tidak menampilkan apa pun jika belum diatur atau file gagal dimuat. */
@Composable
fun HomeWallpaper(modifier: Modifier = Modifier.fillMaxSize()) {
    val (path) = rememberPreference(CustomWallpaperPathKey, defaultValue = "")
    val (dim) = rememberPreference(WallpaperDimKey, defaultValue = 0.55f)
    val bitmap by rememberWallpaperBitmap(path)
    val image = bitmap ?: return
    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
    // Scrim agar teks tetap terbaca di atas gambar apa pun.
    androidx.compose.foundation.layout.Box(
        modifier.background(MaterialTheme.colorScheme.background.copy(alpha = dim.coerceIn(0f, 0.95f))),
    )
}

/** Grup pengaturan untuk Settings → Appearance. */
@Composable
fun SanzxcyAppearanceGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (wallpaperPath, setWallpaperPath) = rememberPreference(CustomWallpaperPathKey, defaultValue = "")
    val (wallpaperDim, setWallpaperDim) = rememberPreference(WallpaperDimKey, defaultValue = 0.55f)
    val (visualStyle, setVisualStyle) = rememberPreference(SanzxcyVisualStyleKey, defaultValue = VISUAL_STYLE_LIQUID_GLASS)
    val (liquidGlass, setLiquidGlass) = rememberPreference(LiquidGlassEnabledKey, defaultValue = true)

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var failed by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) pendingUri = uri
        }

    val activeBitmap by rememberWallpaperBitmap(wallpaperPath)
    val hasWallpaper = wallpaperPath.isNotBlank()

    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Pratinjau wallpaper") },
            text = {
                coil3.compose.AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp)),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !applying,
                    onClick = {
                        applying = true
                        scope.launch {
                            val result = importWallpaper(context, uri)
                            applying = false
                            if (result != null) setWallpaperPath(result) else failed = true
                            pendingUri = null
                        }
                    },
                ) { Text("Terapkan") }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("Batal") } },
        )
    }

    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            title = { Text("Gagal memuat gambar") },
            text = { Text("Gambar tidak dapat dibaca. Wallpaper sebelumnya tidak diubah.") },
            confirmButton = { TextButton(onClick = { failed = false }) { Text("OK") } },
        )
    }

    Column {
        Material3SettingsGroup(
            title = "Custom Wallpaper",
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.palette),
                            title = { Text(if (hasWallpaper) "Ganti wallpaper" else "Pilih wallpaper") },
                            description = { Text("Pilih gambar dari galeri untuk latar Home") },
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ),
                    )
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.palette),
                            title = { Text("Restore Default") },
                            description = { Text("Hapus wallpaper dan kembalikan gaya ke Liquid Glass, efek kaca ON") },
                            onClick = {
                                scope.launch {
                                    clearWallpaperFiles(context)
                                    setWallpaperPath("")
                                    setVisualStyle(VISUAL_STYLE_LIQUID_GLASS)
                                    setLiquidGlass(true)
                                }
                            },
                        ),
                    )
                    if (hasWallpaper) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.palette),
                                title = { Text("Tingkat gelap wallpaper") },
                                description = {
                                    Slider(
                                        value = wallpaperDim.coerceIn(0f, 0.95f),
                                        onValueChange = setWallpaperDim,
                                        valueRange = 0.2f..0.95f,
                                    )
                                },
                            ),
                        )
                    }
                    if (hasWallpaper) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.close),
                                title = { Text("Hapus wallpaper") },
                                description = { Text("Kembali ke tampilan default") },
                                onClick = {
                                    scope.launch {
                                        clearWallpaperFiles(context)
                                        setWallpaperPath("")
                                    }
                                },
                            ),
                        )
                    }
                },
        )
        if (hasWallpaper && activeBitmap != null) {
            Image(
                bitmap = activeBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(24.dp)),
            )
        }

        androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))

        Material3SettingsGroup(
            title = "Visual Style",
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text("Gaya tampilan") },
                        description = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = visualStyle == VISUAL_STYLE_EDITORIAL,
                                    onClick = { setVisualStyle(VISUAL_STYLE_EDITORIAL) },
                                    label = { Text("Editorial") },
                                )
                                FilterChip(
                                    selected = visualStyle == VISUAL_STYLE_LIQUID_GLASS,
                                    onClick = {
                                        setVisualStyle(VISUAL_STYLE_LIQUID_GLASS)
                                        setLiquidGlass(true)
                                    },
                                    label = { Text("Liquid Glass") },
                                )
                            }
                        },
                    ),
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text("Liquid Glass Effect") },
                        description = {
                            Text(
                                when {
                                    visualStyle == VISUAL_STYLE_EDITORIAL -> "Nonaktif saat Editorial"
                                    liquidGlass -> "ON"
                                    else -> "OFF"
                                },
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = liquidGlass,
                                onCheckedChange = { if (visualStyle == VISUAL_STYLE_LIQUID_GLASS) setLiquidGlass(it) },
                                enabled = visualStyle == VISUAL_STYLE_LIQUID_GLASS,
                            )
                        },
                        onClick = {
                            if (visualStyle == VISUAL_STYLE_LIQUID_GLASS) setLiquidGlass(!liquidGlass)
                        },
                    ),
                ),
        )
    }
}
