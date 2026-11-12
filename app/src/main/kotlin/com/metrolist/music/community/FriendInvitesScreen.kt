package com.metrolist.music.community

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

@Composable
fun FriendInvitesScreen(state: CommunityState, onBack: () -> Unit) {
    val token = state.token ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invites by remember { mutableStateOf<List<FriendInvite>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    suspend fun refresh() {
        val result = CommunityApi.invites(token)
        loading = false
        if (result.ok) {
            invites = result.json.optJSONArray("invites").toInviteList()
            error = ""
        } else error = result.error.ifBlank { "Undangan belum dapat dimuat." }
    }

    LaunchedEffect(token) {
        while (true) {
            refresh()
            delay(20_000)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Kembali") }
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text("Undangan teman", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Terima atau tolak undangan yang masuk.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(enabled = !loading, onClick = { scope.launch { loading = true; refresh() } }) { Text("Muat ulang") }
        }
        Spacer(Modifier.size(8.dp))
        if (loading && invites.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(18.dp))
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        if (!loading && invites.isEmpty() && error.isBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Belum ada undangan", fontWeight = FontWeight.SemiBold)
                    Text("Undangan baru akan muncul di sini dan diperbarui otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(invites, key = { it.id }) { invite ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape, modifier = Modifier.size(42.dp)) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Text(invite.fromDisplayName.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(invite.fromDisplayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("@${invite.from} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(invite.createdAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(
                                when (invite.status) { "pending" -> "Menunggu"; "accepted" -> "Diterima"; "declined" -> "Ditolak"; else -> invite.status },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (invite.status == "pending") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (invite.status == "pending") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { scope.launch { resolve(invite, false, token, { invites = it }, context) } },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Tolak") }
                                Button(
                                    onClick = { scope.launch { resolve(invite, true, token, { invites = it }, context) } },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Terima") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun resolve(
    invite: FriendInvite,
    accept: Boolean,
    token: String,
    update: (List<FriendInvite>) -> Unit,
    context: android.content.Context,
) {
    val result = if (accept) CommunityApi.acceptInvite(token, invite.id) else CommunityApi.declineInvite(token, invite.id)
    val updated = result.json.optJSONObject("invite")
    if (result.ok && updated != null) {
        val replacement = parseFriendInvite(updated)
        // Keep the status visible in the inbox while removing its action controls.
        val current = CommunityApi.invites(token).let { response ->
            if (response.ok) response.json.optJSONArray("invites").toInviteList() else emptyList()
        }
        update(if (current.isEmpty()) listOf(replacement) else current)
        Toast.makeText(context, if (accept) "Undangan diterima" else "Undangan ditolak", Toast.LENGTH_SHORT).show()
    } else Toast.makeText(context, result.error.ifBlank { "Tindakan tidak dapat diproses" }, Toast.LENGTH_SHORT).show()
}

private fun JSONArray?.toInviteList(): List<FriendInvite> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.let(::parseFriendInvite) }
}
