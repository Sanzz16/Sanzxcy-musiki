package com.metrolist.music.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanzxcy.music.com.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FeedbackHistoryEntry(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Composable
fun CommunityFeedbackHistory(token: String) {
    val scope = rememberCoroutineScope()
    var entries by remember(token) { mutableStateOf<List<FeedbackHistoryEntry>>(emptyList()) }
    var loading by remember(token) { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }

    suspend fun load(isRefresh: Boolean) {
        if (isRefresh) refreshing = true else loading = true
        error = null
        val result = CommunityApi.myFeedback(token)
        if (result.ok) {
            val array = result.json.optJSONArray("feedback")
            entries = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val item = array?.optJSONObject(index) ?: return@mapNotNull null
                FeedbackHistoryEntry(
                    id = item.optString("id", index.toString()),
                    type = item.optString("type", "bug"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    status = item.optString("status", "new"),
                    createdAt = normalizeEpochMillis(item.optLong("createdAt")),
                    updatedAt = normalizeEpochMillis(item.optLong("updatedAt", item.optLong("createdAt"))),
                )
            }
        } else {
            error = result.error.ifBlank { "" }
        }
        loading = false
        refreshing = false
    }

    LaunchedEffect(token) { load(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.community_feedback_history_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.community_feedback_history_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { scope.launch { load(true) } }, enabled = !refreshing) {
                if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.community_feedback_history_refresh))
            }
        }
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val errorMessage = error.orEmpty()
                    Text(
                        if (errorMessage.isBlank()) stringResource(R.string.community_feedback_history_load_error) else errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = { scope.launch { load(false) } }) { Text(stringResource(R.string.community_feedback_history_retry)) }
                }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            entries.isEmpty() && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.community_feedback_history_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.community_feedback_history_empty_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            entries.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { entry -> FeedbackHistoryCard(entry) }
            }
        }
    }
}

@Composable
private fun FeedbackHistoryCard(entry: FeedbackHistoryEntry) {
    val (statusString, statusColor, statusContent) = when (entry.status) {
        "processing" -> Triple(R.string.community_feedback_status_processing, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        "done" -> Triple(R.string.community_feedback_status_done, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        "archived" -> Triple(R.string.community_feedback_status_archived, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(R.string.community_feedback_status_new, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (entry.type == "feature") R.string.community_feedback_type_feature else R.string.community_feedback_type_bug),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Surface(color = statusColor, shape = RoundedCornerShape(50)) {
                    Text(stringResource(statusString), style = MaterialTheme.typography.labelMedium, color = statusContent, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val time = if (entry.createdAt > 0L) formatFeedbackTimestamp(entry.createdAt) else stringResource(R.string.community_feedback_history_time_unknown)
            Text(stringResource(R.string.community_feedback_history_sent_at, time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.updatedAt > entry.createdAt && entry.updatedAt > 0L) {
                Text(
                    stringResource(R.string.community_feedback_history_updated_at, formatFeedbackTimestamp(entry.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatFeedbackTimestamp(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
