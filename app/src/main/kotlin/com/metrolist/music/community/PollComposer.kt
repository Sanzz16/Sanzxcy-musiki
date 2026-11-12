package com.metrolist.music.community

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PollComposer(token: String, onDismiss: () -> Unit, onCreated: (ChatMessage) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var imageUrl by remember { mutableStateOf("") }
    var localImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            localImage = uri
            scope.launch {
                busy = true
                error = ""
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val bitmap = decodeBitmap(context, uri, 1600) ?: throw IllegalArgumentException("Gambar tidak dapat dibaca")
                        compressJpeg(bitmap, 2 * 1024 * 1024).also { bitmap.recycle() }
                    }
                    val uploaded = CommunityApi.upload(token, "pollImage", "image/jpeg", bytes)
                    if (uploaded.ok) imageUrl = uploaded.json.optString("url") else { imageUrl = ""; error = uploaded.error }
                } catch (e: Exception) {
                    imageUrl = ""
                    error = e.message ?: "Gambar gagal diunggah."
                } finally { busy = false }
            }
        }
    }
    val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Buat polling") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Polling hanya dapat dibuat Owner di Global Chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(question, { question = it.take(200) }, label = { Text("Pertanyaan") }, singleLine = false, maxLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                options.forEachIndexed { index, option ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { value -> options = options.toMutableList().also { it[index] = value.take(100) } },
                            label = { Text("Pilihan ${index + 1}") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                        )
                        if (options.size > 2) TextButton(onClick = { options = options.filterIndexed { i, _ -> i != index } }) { Text("×") }
                    }
                }
                if (options.size < 10) TextButton(onClick = { options = options + "" }, enabled = !busy) { Text("+ Tambah pilihan") }
                localImage?.let { selectedImage -> AsyncImage(model = imageUrl.takeIf { it.isNotBlank() } ?: selectedImage, contentDescription = "Gambar poll", modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).padding(top = 2.dp)) }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (imageUrl.isBlank()) "Tambahkan gambar" else "Ganti gambar") }
                if (localImage != null) TextButton(onClick = { localImage = null; imageUrl = "" }, enabled = !busy) { Text("Hapus gambar") }
                if (busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp); Text("  Mengunggah…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } },
        confirmButton = {
            Button(enabled = !busy && question.isNotBlank() && cleanOptions.size >= 2 && cleanOptions.size <= 10, onClick = {
                scope.launch {
                    busy = true
                    val result = CommunityApi.createPoll(token, question.trim(), cleanOptions, imageUrl)
                    busy = false
                    val message = result.json.optJSONObject("message")?.let(::parseMessage)
                    if (result.ok && message != null) onCreated(message) else error = result.error.ifBlank { "Poll gagal dibuat." }
                }
            }) { Text(if (busy) "Memproses…" else "Buat poll") }
        },
    )
}
