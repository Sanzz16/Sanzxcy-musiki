package com.metrolist.music.community

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.sanzxcy.music.com.R
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_FINAL_MEDIA = 2 * 1024 * 1024
private const val MAX_INPUT_VIDEO = 10 * 1024 * 1024

private data class FeedbackAttachment(val uri: Uri, val type: String, val name: String, val bytes: ByteArray)

@Composable
fun CommunityFeedbackForm(
    token: String,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("bug") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<FeedbackAttachment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var allowTextOnlyRetry by remember { mutableStateOf(false) }

    fun submit(includeAttachment: Boolean) {
        if (title.isBlank() || description.isBlank()) { message = resources.getString(R.string.community_feedback_required_fields); return }
        val media = if (includeAttachment) attachment else null
        if (!includeAttachment) attachment = null
        allowTextOnlyRetry = false
        busy = true
        message = resources.getString(R.string.community_feedback_sending)
        scope.launch {
            val result = CommunityApi.submitFeedback(token, type, title.trim(), description.trim(), media?.type, media?.bytes)
            busy = false
            if (result.ok) onSubmitted() else {
                message = result.error.ifBlank { resources.getString(R.string.community_feedback_send_failed) }
                allowTextOnlyRetry = media != null
            }
        }
    }

    fun select(uri: Uri?, expectedVideo: Boolean) {
        if (uri == null) return
        scope.launch {
            busy = true
            message = resources.getString(if (expectedVideo) R.string.community_feedback_preparing_video else R.string.community_feedback_reading_photo)
            try {
                val selected = withContext(Dispatchers.IO) { readAttachment(context, uri, expectedVideo) }
                attachment = selected
                allowTextOnlyRetry = false
                message = if (selected.type == MimeTypes.VIDEO_MP4 && selected.bytes.size < querySize(context, uri)) {
                    resources.getString(R.string.community_feedback_video_compressed, selected.bytes.size / 1024)
                } else resources.getString(R.string.community_feedback_attachment_ready, selected.bytes.size / 1024)
            } catch (e: Exception) {
                attachment = null
                message = e.message ?: resources.getString(R.string.community_feedback_attachment_error)
            } finally {
                busy = false
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { select(it, false) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { select(it, true) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.community_feedback_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "bug", onClick = { type = "bug" }, enabled = !busy, label = { Text(stringResource(R.string.community_feedback_bug)) }, leadingIcon = if (type == "bug") ({ Text("✓") }) else null)
            FilterChip(selected = type == "feature", onClick = { type = "feature" }, enabled = !busy, label = { Text(stringResource(R.string.community_feedback_feature)) }, leadingIcon = if (type == "feature") ({ Text("✓") }) else null)
        }
        OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.community_feedback_title_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it.take(2000) }, label = { Text(stringResource(R.string.community_feedback_description_label)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.community_feedback_attachment_privacy), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { photoPicker.launch("image/*") }, enabled = !busy) { Text(stringResource(R.string.community_feedback_pick_photo)) }
            OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !busy) { Text(stringResource(R.string.community_feedback_pick_video)) }
        }
        attachment?.let { media ->
            if (media.type.startsWith("image/")) {
                AsyncImage(model = media.uri, contentDescription = stringResource(R.string.community_feedback_photo_preview), modifier = Modifier.fillMaxWidth().size(220.dp), contentScale = ContentScale.Fit)
            } else {
                AndroidView(
                    factory = { viewContext -> VideoView(viewContext).apply { setVideoURI(media.uri); setMediaController(MediaController(viewContext)) } },
                    modifier = Modifier.fillMaxWidth().size(220.dp),
                )
            }
            Text(stringResource(R.string.community_feedback_attachment_name, media.name, media.bytes.size / 1024))
            OutlinedButton(onClick = { attachment = null; allowTextOnlyRetry = false; message = resources.getString(R.string.community_feedback_attachment_removed) }, enabled = !busy) { Text(stringResource(R.string.community_feedback_remove_attachment)) }
        }
        if (busy) CircularProgressIndicator()
        if (message.isNotBlank()) Text(message)
        if (allowTextOnlyRetry && attachment != null && !busy) {
            TextButton(onClick = { submit(includeAttachment = false) }) { Text(stringResource(R.string.community_feedback_retry_text_only)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.community_feedback_cancel)) }
            Button(
                onClick = { submit(includeAttachment = true) }, enabled = !busy && title.isNotBlank() && description.isNotBlank(),
            ) { Text(stringResource(R.string.community_feedback_send)) }
        }
    }
}

private fun querySize(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
}.getOrDefault(0L)

private suspend fun readAttachment(context: Context, uri: Uri, video: Boolean): FeedbackAttachment {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)?.lowercase() ?: throw IOException(context.getString(R.string.community_feedback_unknown_type))
    val allowed = if (video) mime.startsWith("video/") else mime in setOf("image/jpeg", "image/png", "image/webp")
    if (!allowed) throw IOException(context.getString(if (video) R.string.community_feedback_video_type_invalid else R.string.community_feedback_photo_type_invalid))
    val size = querySize(context, uri)
    if (video && size > MAX_INPUT_VIDEO) throw IOException(context.getString(R.string.community_feedback_video_input_too_large))
    if (!video && size > MAX_FINAL_MEDIA) throw IOException(context.getString(R.string.community_feedback_photo_too_large))
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: context.getString(R.string.community_feedback_default_attachment_name)
    val source = resolver.openInputStream(uri)?.use {
        it.readBytesBounded(
            if (video) MAX_INPUT_VIDEO else MAX_FINAL_MEDIA,
            context.getString(R.string.community_feedback_file_too_large),
        )
    } ?: throw IOException(context.getString(R.string.community_feedback_file_read_failed))
    if (source.isEmpty()) throw IOException(context.getString(R.string.community_feedback_file_empty))
    return if (video && (source.size > MAX_FINAL_MEDIA || mime != "video/mp4")) {
        val reduced = compressVideo(context, uri)
        if (reduced.size > MAX_FINAL_MEDIA) throw IOException(context.getString(R.string.community_feedback_video_still_too_large))
        FeedbackAttachment(uri, "video/mp4", name, reduced)
    } else FeedbackAttachment(uri, if (video) "video/mp4" else mime, name, source)
}

private fun java.io.InputStream.readBytesBounded(limit: Int, tooLargeMessage: String): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (out.size() + count > limit) throw IOException(tooLargeMessage)
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

@OptIn(UnstableApi::class)
private suspend fun compressVideo(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.Main) {
    val durationMs = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(context, uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
        finally { retriever.release() }
    }
    if (durationMs <= 0) throw IOException(context.getString(R.string.community_feedback_video_duration_unavailable))
    val audioBits = 96_000
    val targetTotalBits = (1.45 * 1024 * 1024 * 8 * 1000 / durationMs).toInt()
    val videoBits = (targetTotalBits - audioBits).coerceIn(96_000, 2_500_000)
    val output = File(context.cacheDir, "feedback-${System.nanoTime()}.mp4")
    try {
        suspendCancellableCoroutine { continuation ->
            val encoder = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBits).build())
                .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(audioBits).build())
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoder)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                        if (continuation.isActive) {
                            if (output.length() > MAX_FINAL_MEDIA) continuation.resumeWithException(IOException(context.getString(R.string.community_feedback_video_still_too_large)))
                            else continuation.resume(output.readBytes())
                        }
                    }
                    override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: androidx.media3.transformer.ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(IOException(context.getString(R.string.community_feedback_video_compression_failed)))
                    }
                }).build()
            continuation.invokeOnCancellation { transformer.cancel() }
            transformer.start(EditedMediaItem.Builder(MediaItem.fromUri(uri)).build(), output.absolutePath)
        }
    } finally { output.delete() }
}
