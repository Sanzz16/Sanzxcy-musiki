package com.metrolist.music.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.theme.LocalLiquidGlass
import com.sanzxcy.music.com.R
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private data class CommunityAnnouncement(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val updatedBy: String,
    val deletedAt: Long,
) {
    companion object {
        fun fromJson(value: JSONObject) = CommunityAnnouncement(
            id = value.optString("id"),
            title = value.optString("title"),
            description = value.optString("description"),
            createdAt = value.optLong("createdAt"),
            updatedAt = value.optLong("updatedAt", value.optLong("createdAt")),
            updatedBy = value.optString("updatedBy", value.optString("createdBy")),
            deletedAt = value.optLong("deletedAt"),
        )
    }
}

private data class AnnouncementRevision(
    val action: String,
    val title: String,
    val description: String,
    val by: String,
    val at: Long,
) {
    companion object {
        fun fromJson(value: JSONObject) = AnnouncementRevision(
            action = value.optString("action"),
            title = value.optString("title"),
            description = value.optString("description"),
            by = value.optString("by"),
            at = value.optLong("at"),
        )
    }
}

@Composable
fun CommunityAnnouncementsScreen(state: CommunityState, onBack: () -> Unit) {
    val token = state.token ?: return
    val isOwner = state.me?.role == "owner"
    val loadErrorFallback = stringResource(R.string.community_announcement_load_error)
    val publishErrorFallback = stringResource(R.string.community_announcement_publish_error)
    val deleteErrorFallback = stringResource(R.string.community_announcement_delete_error)
    val scope = rememberCoroutineScope()
    var announcements by remember { mutableStateOf<List<CommunityAnnouncement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var editorVisible by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CommunityAnnouncement?>(null) }
    var historyTitle by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<AnnouncementRevision>>(emptyList()) }
    var showingHistory by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = ""
            val result = if (isOwner) CommunityApi.ownerAnnouncements(token) else CommunityApi.announcements(token)
            loading = false
            if (result.ok) {
                val values = result.json.optJSONArray("announcements")
                announcements = if (values == null) emptyList() else (0 until values.length())
                    .mapNotNull { values.optJSONObject(it)?.let(CommunityAnnouncement::fromJson) }
            } else error = result.error.ifBlank { loadErrorFallback }
        }
    }

    fun openEditor(item: CommunityAnnouncement? = null) {
        editingId = item?.id
        title = item?.title.orEmpty()
        description = item?.description.orEmpty()
        formError = ""
        editorVisible = true
    }

    fun loadHistory(item: CommunityAnnouncement) {
        scope.launch {
            val result = CommunityApi.ownerAnnouncementHistory(token, item.id)
            if (result.ok) {
                val values = result.json.optJSONArray("history")
                history = if (values == null) emptyList() else (0 until values.length())
                    .mapNotNull { values.optJSONObject(it)?.let(AnnouncementRevision::fromJson) }
                historyTitle = item.title
                showingHistory = true
            }
        }
    }

    LaunchedEffect(token, isOwner) { refresh() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.community_announcement_back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.community_announcements_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.community_announcements_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { refresh() }) { Text(stringResource(R.string.community_announcement_refresh)) }
        }

        if (isOwner) {
            Button(onClick = { openEditor() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(20.dp)) {
                Text(stringResource(R.string.community_announcement_create))
            }
        }

        when {
            loading && announcements.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            error.isNotBlank() && announcements.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refresh() }) { Text(stringResource(R.string.community_announcement_retry)) }
            }
            announcements.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.community_announcement_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
                items(announcements, key = { it.id }) { item ->
                    AnnouncementCard(
                        item = item,
                        isOwner = isOwner,
                        onEdit = { openEditor(item) },
                        onHistory = { loadHistory(item) },
                        onDelete = { deleteTarget = item },
                    )
                }
            }
        }
    }

    if (editorVisible) {
        Dialog(onDismissRequest = { editorVisible = false; editingId = null }) {
            val shape = RoundedCornerShape(30.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).glassBorder(shape, LocalLiquidGlass.current),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (LocalLiquidGlass.current) 0.9f else 1f),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(if (editingId == null) R.string.community_announcement_create_title else R.string.community_announcement_edit_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(title, { title = it.take(100) }, label = { Text(stringResource(R.string.community_announcement_title_label)) }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it.take(2000) }, label = { Text(stringResource(R.string.community_announcement_description_label)) }, minLines = 4, maxLines = 8, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                    if (formError.isNotBlank()) Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editorVisible = false; editingId = null }) { Text(stringResource(R.string.community_announcement_cancel)) }
                        Button(enabled = !saving && title.isNotBlank() && description.isNotBlank(), onClick = {
                            scope.launch {
                                saving = true
                                val result = CommunityApi.saveAnnouncement(token, editingId, title.trim(), description.trim())
                                saving = false
                                if (result.ok) {
                                    editorVisible = false
                                    editingId = null
                                    refresh()
                                } else formError = result.error.ifBlank { publishErrorFallback }
                            }
                        }, shape = RoundedCornerShape(18.dp)) { Text(stringResource(R.string.community_announcement_save)) }
                    }
                }
            }
        }
    }

    deleteTarget?.let { item ->
        Dialog(onDismissRequest = { deleteTarget = null }) {
            val shape = RoundedCornerShape(28.dp)
            Surface(Modifier.fillMaxWidth().widthIn(max = 480.dp).glassBorder(shape, LocalLiquidGlass.current), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.community_announcement_delete_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.community_announcement_delete_confirm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.community_announcement_cancel)) }
                        TextButton(onClick = {
                            scope.launch {
                                val result = CommunityApi.deleteAnnouncement(token, item.id)
                                deleteTarget = null
                                if (result.ok) refresh() else error = result.error.ifBlank { deleteErrorFallback }
                            }
                        }) { Text(stringResource(R.string.community_announcement_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showingHistory) {
        Dialog(onDismissRequest = { showingHistory = false }) {
            val shape = RoundedCornerShape(28.dp)
            Surface(Modifier.fillMaxWidth().widthIn(max = 560.dp).glassBorder(shape, LocalLiquidGlass.current), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.community_announcement_history_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(historyTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (history.isEmpty()) Text(stringResource(R.string.community_announcement_history_empty))
                    else Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.forEach { revision ->
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    val actionLabel = when (revision.action) {
                                        "create" -> stringResource(R.string.community_announcement_revision_created)
                                        "edit" -> stringResource(R.string.community_announcement_revision_edited)
                                        else -> stringResource(R.string.community_announcement_revision_deleted)
                                    }
                                    Text(stringResource(R.string.community_announcement_revision_meta, actionLabel, revision.by, formatAnnouncementDate(revision.at)), style = MaterialTheme.typography.labelMedium)
                                    Text(revision.title, fontWeight = FontWeight.SemiBold)
                                    Text(revision.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showingHistory = false }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.community_announcement_close)) }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(
    item: CommunityAnnouncement,
    isOwner: Boolean,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).glassBorder(shape, LocalLiquidGlass.current),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (LocalLiquidGlass.current) 0.86f else 1f),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isOwner && item.deletedAt > 0) Text(stringResource(R.string.community_announcement_deleted), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.community_announcement_by, item.updatedBy.ifBlank { "owner" }, formatAnnouncementDate(item.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isOwner) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.deletedAt == 0L) TextButton(onClick = onEdit) { Text(stringResource(R.string.community_announcement_edit)) }
                    TextButton(onClick = onHistory) { Text(stringResource(R.string.community_announcement_history)) }
                    if (item.deletedAt == 0L) TextButton(onClick = onDelete) { Text(stringResource(R.string.community_announcement_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

private fun formatAnnouncementDate(timestamp: Long): String =
    if (timestamp <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
