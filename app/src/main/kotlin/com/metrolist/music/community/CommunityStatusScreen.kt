package com.metrolist.music.community

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sanzxcy.music.com.R
import coil3.compose.AsyncImage
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATUS_PHOTO_LIMIT = 3 * 1024 * 1024

@Composable
fun CommunityStatusScreen(state: CommunityState) {
    val token = state.token ?: return
    val me = state.me ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val loadErrorText = stringResource(R.string.community_status_load_error)
    val createdNotice = stringResource(R.string.community_status_created)
    val deletedNotice = stringResource(R.string.community_status_deleted)
    val deleteError = stringResource(R.string.community_status_delete_error)
    var statuses by remember(token) { mutableStateOf<List<CommunityStatus>>(emptyList()) }
    var loading by remember(token) { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf("") }
    var showComposer by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<CommunityStatus?>(null) }
    var notice by remember { mutableStateOf("") }

    suspend fun refresh() {
        val response = CommunityApi.statuses(token)
        if (response.ok) {
            val array = response.json.optJSONArray("statuses")
            statuses = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                array?.optJSONObject(index)?.let(::parseCommunityStatus)
            }.filter { it.expiresAt > System.currentTimeMillis() }
            error = ""
        } else {
            error = response.error.ifBlank { loadErrorText }
            state.handleAuthFailure(response)
        }
        loading = false
        refreshing = false
    }

    LaunchedEffect(token) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                refresh()
                delay(60_000)
            }
        }
    }
    LaunchedEffect(notice) {
        if (notice.isNotBlank()) { delay(3_500); notice = "" }
    }

    fun openStatus(status: CommunityStatus) {
        opened = status
        scope.launch {
            val result = CommunityApi.viewStatus(token, status.id)
            if (result.ok) {
                val updated = status.copy(views = result.json.optInt("views", status.views))
                opened = updated
                statuses = statuses.map { if (it.id == updated.id) updated else it }
            }
        }
    }

    if (showComposer) {
        StatusComposer(
            token = token,
            onDismiss = { showComposer = false },
            onCreated = { created ->
                statuses = (listOf(created) + statuses).distinctBy { it.id }.take(100)
                showComposer = false
                notice = createdNotice
            },
            onError = { notice = it },
        )
    }
    opened?.let { status ->
        StatusViewer(
            status = status,
            isMine = status.from == me.username,
            onDismiss = { opened = null },
            onDelete = {
                scope.launch {
                    val result = CommunityApi.deleteStatus(token, status.id)
                    if (result.ok) {
                        statuses = statuses.filterNot { it.id == status.id }
                        opened = null
                        notice = deletedNotice
                    } else notice = result.error.ifBlank { deleteError }
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.community_status_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.community_status_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                scope.launch { refreshing = true; refresh() }
            }, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.community_status_refresh))
            }
        }
        val heroShape = RoundedCornerShape(28.dp)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            shape = heroShape,
            modifier = Modifier.fillMaxWidth().glassBorder(heroShape, LocalLiquidGlass.current),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { showComposer = true }, contentAlignment = Alignment.Center) {
                    if (me.avatarUrl.isNotBlank()) AsyncImage(me.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(me.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Box(Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text("+", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.community_status_share_moment), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.community_status_expiry_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { showComposer = true }) { Text(stringResource(R.string.community_status_create)) }
            }
        }
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { scope.launch { loading = true; refresh() } }) { Text(stringResource(R.string.community_status_retry)) }
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (statuses.isEmpty() && error.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.community_status_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.community_status_empty_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(statuses, key = { it.id }) { status -> StatusFeedCard(status, isMine = status.from == me.username, onClick = { openStatus(status) }) }
        }
    }
}

@Composable
private fun StatusFeedCard(status: CommunityStatus, isMine: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val author = status.author
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
        shape = shape,
        modifier = Modifier.fillMaxWidth().glassBorder(shape, LocalLiquidGlass.current).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = status.photoUrl,
                contentDescription = stringResource(R.string.community_status_photo_by, status.displayName),
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(86.dp).height(112.dp).clip(RoundedCornerShape(19.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 13.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(author?.avatarUrl.orEmpty(), status.displayName, 34.dp, fallbackUrls = author?.avatarUrls.orEmpty())
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text(status.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${status.from}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Text(status.caption.ifBlank { stringResource(R.string.community_status_photo_placeholder) }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val remaining = timeRemaining(status.expiresAt)
                Text(
                    if (isMine) stringResource(R.string.community_status_views_remaining, status.views, remaining)
                    else stringResource(R.string.community_status_remaining, remaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun StatusComposer(token: String, onDismiss: () -> Unit, onCreated: (CommunityStatus) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preparingPhoto = stringResource(R.string.community_status_preparing_photo)
    val uploadingPhoto = stringResource(R.string.community_status_uploading_photo)
    val photoReadError = stringResource(R.string.community_status_photo_read_error)
    val photoTooLargeError = stringResource(R.string.community_status_photo_too_large)
    val photoUploadError = stringResource(R.string.community_status_photo_upload_error)
    val photoUrlMissingError = stringResource(R.string.community_status_photo_url_missing)
    val photoReady = stringResource(R.string.community_status_photo_ready)
    val photoProcessingError = stringResource(R.string.community_status_photo_processing_error)
    val incompleteResponseError = stringResource(R.string.community_status_response_incomplete)
    val createError = stringResource(R.string.community_status_create_error)
    var caption by remember { mutableStateOf("") }
    var localPhoto by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            localPhoto = uri
            photoUrl = ""
            scope.launch {
                busy = true
                message = preparingPhoto
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val bitmap: Bitmap = decodeBitmap(context, uri, 1800) ?: error(photoReadError)
                        try { compressJpeg(bitmap, STATUS_PHOTO_LIMIT) } finally { bitmap.recycle() }
                    }
                    require(bytes.isNotEmpty() && bytes.size <= STATUS_PHOTO_LIMIT) { photoTooLargeError }
                    message = uploadingPhoto
                    val uploaded = CommunityApi.upload(token, "statusPhoto", "image/jpeg", bytes)
                    if (!uploaded.ok) error(uploaded.error.ifBlank { photoUploadError })
                    photoUrl = uploaded.json.optString("url")
                    if (photoUrl.isBlank()) error(photoUrlMissingError)
                    message = photoReady
                } catch (e: Exception) {
                    photoUrl = ""
                    message = e.message ?: photoProcessingError
                } finally { busy = false }
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(30.dp)
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = shape,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.community_status_create_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.community_status_create_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                localPhoto?.let { uri ->
                    Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        AsyncImage(model = photoUrl.ifBlank { uri }, contentDescription = stringResource(R.string.community_status_photo_preview), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (localPhoto == null) R.string.community_status_choose_photo else R.string.community_status_change_photo))
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(240) },
                    label = { Text(stringResource(R.string.community_status_caption_optional)) },
                    supportingText = { Text(stringResource(R.string.community_status_caption_counter, caption.length)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(19.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = if (photoUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  ${stringResource(R.string.community_status_processing_photo)}", style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.community_status_cancel)) }
                    FilledTonalButton(
                        enabled = !busy && photoUrl.isNotBlank(),
                        onClick = {
                            busy = true
                            scope.launch {
                                val result = CommunityApi.createStatus(token, photoUrl, caption.trim())
                                busy = false
                                if (result.ok) {
                                    val obj = result.json.optJSONObject("status")
                                    if (obj != null) onCreated(parseCommunityStatus(obj)) else onError(incompleteResponseError)
                                } else {
                                    onError(result.error.ifBlank { createError })
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.community_status_share)) }
                }
            }
        }
    }
}

@Composable
private fun StatusViewer(status: CommunityStatus, isMine: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = status.photoUrl,
                contentDescription = status.caption.ifBlank { stringResource(R.string.community_status_photo_placeholder) },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.46f)).padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("‹", color = Color.White, fontSize = 28.sp) }
                Column(Modifier.weight(1f)) {
                    Text(status.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(stringResource(R.string.community_status_time_remaining, timeRemaining(status.expiresAt)), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.labelSmall)
                }
                if (isMine) Text(stringResource(R.string.community_status_view_count, status.views), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            if (status.caption.isNotBlank() || isMine) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(status.caption, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (isMine) TextButton(onClick = onDelete) { Text(stringResource(R.string.community_status_delete), color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun timeRemaining(expiresAt: Long): String {
    val minutes = ((expiresAt - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    val context = LocalContext.current
    return if (minutes >= 60) {
        context.getString(R.string.community_status_time_hours_minutes, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.community_status_time_minutes, minutes)
    }
}
