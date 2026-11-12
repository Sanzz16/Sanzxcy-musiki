/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: layar loading (screen.mp4), mode maintenance, dan pemberitahuan update
 * yang semuanya dikendalikan dari owner.html.
 */

package com.metrolist.music.community

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sanzxcy.music.com.BuildConfig
import com.sanzxcy.music.com.R
import com.metrolist.music.utils.Updater

object AppStatusHolder {
    val status = MutableStateFlow<AppStatus?>(null)
    suspend fun refresh() {
        CommunityApi.appStatus()?.let { status.value = it }
    }
}

/** Dipasang sekali di MainActivity. Menampilkan: splash → maintenance (menutup semua) → dialog update. */
@Composable
fun SanzxcyOverlays() {
    val status by AppStatusHolder.status.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var dismissedUpdate by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                AppStatusHolder.refresh()
                delay(if (AppStatusHolder.status.value?.maintenanceEnabled == true) 20_000 else 120_000)
            }
        }
    }

    Box(Modifier.fillMaxSize().zIndex(1000f)) {
        val s = status
        val updateKey = s?.let { "${it.updateVersionCode}:${it.updateVersionName}:${it.updateUrl}" }.orEmpty()
        val newerOwnerUpdate = s != null && (
            s.updateVersionCode > BuildConfig.VERSION_CODE.toLong() ||
                (s.updateVersionName.isNotBlank() && Updater.compareVersions(s.updateVersionName, BuildConfig.BASE_VERSION_NAME) > 0)
            )
        if (s != null && s.maintenanceEnabled) MaintenanceScreen(s)

        if (s != null && s.updateEnabled && newerOwnerUpdate && dismissedUpdate != updateKey && !s.maintenanceEnabled && splashDone) {
            UpdateDialog(s, onDismiss = { dismissedUpdate = updateKey })
        }

        AnimatedVisibility(visible = !splashDone, exit = fadeOut(tween(450)), enter = fadeIn(tween(0))) {
            SplashScreen(onFinished = { splashDone = true })
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val resId = remember { context.resources.getIdentifier("screen", "raw", context.packageName) }
    var finished by remember { mutableStateOf(false) }

    // Batas aman: intro splash dibatasi 2 detik agar pengguna cepat masuk aplikasi.
    LaunchedEffect(Unit) { delay(2000); if (!finished) { finished = true; onFinished() } }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        if (resId != 0) {
            SplashVideo(resId = resId, onEnded = { if (!finished) { finished = true; onFinished() } })
        } else {
            LaunchedEffect(Unit) { delay(2000); if (!finished) { finished = true; onFinished() } }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_launcher_static_foreground), null, Modifier.size(120.dp))
                Text("Sanzxcy Music", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SplashVideo(resId: Int, onEnded: () -> Unit) {
    val context = LocalContext.current
    val player =
        remember(resId) {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                volume = 0f
                videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
        }
    androidx.compose.runtime.DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == androidx.media3.common.Player.STATE_ENDED) onEnded() }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { onEnded() }
        }
        player.addListener(l)
        // Pasang listener sebelum prepare agar frame/end event pertama tidak terlewat.
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$resId")))
        player.prepare()
        player.playWhenReady = true
        onDispose { player.removeListener(l); player.release() }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> android.view.TextureView(ctx).also { player.setVideoTextureView(it) } },
    )
}

@Composable
private fun MaintenanceScreen(s: AppStatus) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_static_foreground), null, Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black))
            Spacer(Modifier.height(24.dp))
            Text("Sedang Maintenance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                s.maintenanceMessage.ifBlank { "Kami sedang melakukan peningkatan. Aplikasi akan kembali normal sebentar lagi." },
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
            )
            if (s.maintenanceEta.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp)) {
                    Text("Perkiraan selesai: ${s.maintenanceEta}", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(28.dp))
            OutlinedButton(enabled = !checking, onClick = { scope.launch { checking = true; AppStatusHolder.refresh(); checking = false } }, modifier = Modifier.height(48.dp)) {
                Text(if (checking) "Memeriksa…" else "Periksa lagi")
            }
        }
    }
}

private enum class UpdateDownloadState { IDLE, DOWNLOADING, COMPLETE, FAILED }

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val MANUAL_DOWNLOAD_URL = "https://sanzxcy-music.xo.je/?i=1#beranda"

private data class DownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val mimeType: String?,
)

private fun updateApkFile(context: android.content.Context, versionCode: Long): File? {
    val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
    return File(File(downloads, "MetrolistUpdate"), "update-$versionCode.apk")
}

private fun validHttpsDownloadUrl(value: String): Uri? {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    return uri.takeIf {
        it.scheme.equals("https", ignoreCase = true) &&
            !it.host.isNullOrBlank() && it.userInfo == null
    }
}

@Composable
private fun UpdateDialog(s: AppStatus, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val manager = remember(context) {
        context.getSystemService(DownloadManager::class.java) ?: error("DownloadManager unavailable")
    }
    var savedState by rememberSaveable(s.updateUrl, s.updateVersionCode) {
        mutableStateOf(UpdateDownloadState.IDLE.name)
    }
    var downloadId by rememberSaveable(s.updateUrl, s.updateVersionCode) { mutableStateOf<Long?>(null) }
    var downloadedBytes by rememberSaveable(s.updateUrl, s.updateVersionCode) { mutableStateOf(0L) }
    var totalBytes by rememberSaveable(s.updateUrl, s.updateVersionCode) { mutableStateOf(0L) }
    var errorText by remember(s.updateUrl, s.updateVersionCode) { mutableStateOf("") }
    var selectedDownloadUrl by rememberSaveable(s.updateUrl, s.updateVersionCode) { mutableStateOf(s.updateUrl.ifBlank { "https://sanzxcy-music.xo.je/Sanzxcy-Music-arm64-v8a.apk" }) }
    var showDownloadOptions by remember { mutableStateOf(false) }
    var showPermissionExplanation by remember(s.updateUrl, s.updateVersionCode) { mutableStateOf(false) }
    var installPermissionAvailable by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = runCatching { UpdateDownloadState.valueOf(savedState) }.getOrDefault(UpdateDownloadState.IDLE)
    val apkFile = remember(context, s.updateVersionCode) { updateApkFile(context, s.updateVersionCode) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installPermissionAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    context.packageManager.canRequestPackageInstalls()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(downloadId, state) {
        val observedId = downloadId ?: return@LaunchedEffect
        if (state != UpdateDownloadState.DOWNLOADING) return@LaunchedEffect
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                val cursor = manager.query(DownloadManager.Query().setFilterById(observedId)) ?: return@withContext null
                cursor.use {
                    if (!it.moveToFirst()) return@use null
                    val mimeIndex = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                    DownloadSnapshot(
                        status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        downloadedBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        mimeType = if (mimeIndex >= 0) it.getString(mimeIndex) else null,
                    )
                }
            }
            if (downloadId != observedId) return@LaunchedEffect
            if (snapshot == null) {
                errorText = resources.getString(R.string.update_download_failed)
                savedState = UpdateDownloadState.FAILED.name
                return@LaunchedEffect
            }
            downloadedBytes = snapshot.downloadedBytes.coerceAtLeast(0L)
            totalBytes = snapshot.totalBytes.coerceAtLeast(0L)
            when (snapshot.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val isValidApk = withContext(Dispatchers.IO) {
                        val file = apkFile
                        val mime = snapshot.mimeType?.substringBefore(';')?.trim()?.lowercase()
                        val acceptableMime = mime in setOf(
                            APK_MIME_TYPE,
                            "application/octet-stream",
                            "application/zip",
                            "application/x-android-package-archive",
                        )
                        if (file == null || !file.isFile || file.length() <= 0L || !acceptableMime) {
                            false
                        } else {
                            @Suppress("DEPRECATION")
                            val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                            packageInfo != null && packageInfo.packageName == context.packageName &&
                                packageInfo.versionCode > BuildConfig.VERSION_CODE
                        }
                    }
                    if (downloadId != observedId) return@LaunchedEffect
                    if (isValidApk) {
                        savedState = UpdateDownloadState.COMPLETE.name
                        errorText = ""
                    } else {
                        apkFile?.delete()
                        errorText = resources.getString(R.string.update_apk_invalid)
                        savedState = UpdateDownloadState.FAILED.name
                    }
                    return@LaunchedEffect
                }
                DownloadManager.STATUS_FAILED -> {
                    errorText = resources.getString(R.string.update_download_failed)
                    savedState = UpdateDownloadState.FAILED.name
                    return@LaunchedEffect
                }
            }
            delay(600)
        }
    }

    fun cancelAndDeleteDownload() {
        downloadId?.let { manager.remove(it) }
        downloadId = null
        apkFile?.delete()
        downloadedBytes = 0L
        totalBytes = 0L
        errorText = ""
        savedState = UpdateDownloadState.IDLE.name
    }

    fun dismiss() {
        cancelAndDeleteDownload()
        onDismiss()
    }

    fun startDownload() {
        if (selectedDownloadUrl == MANUAL_DOWNLOAD_URL) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MANUAL_DOWNLOAD_URL)))
            }.onFailure {
                errorText = "Website unduhan tidak dapat dibuka."
                savedState = UpdateDownloadState.FAILED.name
            }
            return
        }
        val uri = validHttpsDownloadUrl(selectedDownloadUrl)
        if (uri == null) {
            errorText = resources.getString(R.string.update_url_invalid)
            savedState = UpdateDownloadState.FAILED.name
            return
        }
        val file = apkFile
        if (file == null) {
            errorText = resources.getString(R.string.update_download_failed)
            savedState = UpdateDownloadState.FAILED.name
            return
        }
        runCatching {
            downloadId?.let { manager.remove(it) }
            file.parentFile?.mkdirs()
            file.delete()
            val request = DownloadManager.Request(uri)
                .setTitle(resources.getString(R.string.update_download_notification_title))
                .setDescription(resources.getString(R.string.update_download_notification_description))
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "MetrolistUpdate/${file.name}",
                )
            manager.enqueue(request)
        }.onSuccess { id ->
            downloadId = id
            downloadedBytes = 0L
            totalBytes = 0L
            errorText = ""
            savedState = UpdateDownloadState.DOWNLOADING.name
        }.onFailure {
            errorText = resources.getString(R.string.update_download_failed)
            savedState = UpdateDownloadState.FAILED.name
        }
    }

    fun installApk() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            installPermissionAvailable = false
            showPermissionExplanation = true
            return
        }
        val file = apkFile
        if (file == null || !file.isFile) {
            errorText = resources.getString(R.string.update_apk_invalid)
            savedState = UpdateDownloadState.FAILED.name
            return
        }
        runCatching {
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            errorText = resources.getString(R.string.update_installer_unavailable)
        }
    }

    AlertDialog(
        onDismissRequest = { dismiss() },
        icon = {
            Image(
                painter = painterResource(R.drawable.ic_launcher_static_foreground),
                contentDescription = null,
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
            )
        },
        title = { Text(stringResource(R.string.update_dialog_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.update_latest_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        Text(
                            s.updateVersionName.ifBlank { stringResource(R.string.update_version_fallback, s.updateVersionCode) },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    s.updateMessage.ifBlank { stringResource(R.string.update_default_message) },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box {
                    OutlinedButton(onClick = { showDownloadOptions = true }) {
                        Text(
                            when (selectedDownloadUrl) {
                                "https://sanzxcy-music.xo.je/Sanzxcy-Music-arm64-v8a.apk" -> "Arm64-v8a stabil"
                                "https://sanzxcy-music.xo.je/Sanzxcy-Music-arm32-v7a.apk" -> "Arm32-v7a stabil"
                                MANUAL_DOWNLOAD_URL -> "Full release stabil (64/32 bit) · website"
                                else -> "APK dari server update"
                            },
                        )
                    }
                    DropdownMenu(expanded = showDownloadOptions, onDismissRequest = { showDownloadOptions = false }) {
                        listOf(
                            "Arm64-v8a stabil" to "https://sanzxcy-music.xo.je/Sanzxcy-Music-arm64-v8a.apk",
                            "Arm32-v7a stabil" to "https://sanzxcy-music.xo.je/Sanzxcy-Music-arm32-v7a.apk",
                            "Full release stabil (64/32 bit) · website" to MANUAL_DOWNLOAD_URL,
                        ).forEach { (label, url) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { selectedDownloadUrl = url; showDownloadOptions = false },
                            )
                        }
                    }
                }
                if (selectedDownloadUrl.isBlank()) {
                    Text(stringResource(R.string.update_link_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                if (state == UpdateDownloadState.DOWNLOADING) {
                    if (totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(
                                R.string.update_progress_details,
                                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100),
                                android.text.format.Formatter.formatShortFileSize(context, downloadedBytes),
                                android.text.format.Formatter.formatShortFileSize(context, totalBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.update_download_starting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state == UpdateDownloadState.COMPLETE) {
                    Text(stringResource(R.string.update_download_complete), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                if (errorText.isNotBlank()) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                if (showPermissionExplanation) {
                    Text(
                        stringResource(if (installPermissionAvailable) R.string.update_install_permission_enabled else R.string.update_install_permission_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    if (!installPermissionAvailable) {
                        OutlinedButton(onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }.onFailure {
                                errorText = resources.getString(R.string.update_installer_unavailable)
                            }
                        }) { Text(stringResource(R.string.update_open_install_settings)) }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                UpdateDownloadState.IDLE -> Button(enabled = selectedDownloadUrl.isNotBlank(), onClick = { startDownload() }) {
                    Text(stringResource(R.string.update_download_button))
                }
                UpdateDownloadState.DOWNLOADING -> Button(onClick = { cancelAndDeleteDownload() }) {
                    Text(stringResource(R.string.update_cancel_download))
                }
                UpdateDownloadState.COMPLETE -> Button(onClick = { installApk() }) {
                    Text(stringResource(R.string.update_install_button))
                }
                UpdateDownloadState.FAILED -> Button(enabled = selectedDownloadUrl.isNotBlank(), onClick = { startDownload() }) {
                    Text(stringResource(R.string.update_retry_button))
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MANUAL_DOWNLOAD_URL)))
                    }.onFailure {
                        errorText = "Website unduhan tidak dapat dibuka."
                    }
                }) { Text("Buka website") }
                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.update_later_button)) }
            }
        },
    )
}
