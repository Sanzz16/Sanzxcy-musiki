package com.metrolist.music.community

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class ModerationSection(val label: String) {
    BOT("Bot"), OVERVIEW("Ringkasan"), IP("Keamanan IP"), MAINTENANCE("Maintenance"), MAINTENANCE_HISTORY("Histori maintenance"), CHAT("Chat"), UPDATE("Update aplikasi"), CHANGELOG("Changelog"), ROOMS("Room Control"), FEEDBACK("Masukan"),
}

@Composable
fun ModerationNavbar(selected: ModerationSection, onSelect: (ModerationSection) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModerationSection.entries.forEach { section ->
            if (section == selected) Button(onClick = { onSelect(section) }, content = { Text(section.label) })
            else OutlinedButton(onClick = { onSelect(section) }, content = { Text(section.label) })
        }
    }
}

@Composable
fun ModerationExtraPages(section: ModerationSection, token: String, onAuthFailure: (ApiResult) -> Unit) {
    when (section) {
        ModerationSection.OVERVIEW -> OwnerOverviewPage(token, onAuthFailure)
        ModerationSection.IP -> IpAdminPage(token, onAuthFailure)
        ModerationSection.MAINTENANCE -> MaintenanceAdminPage(token, onAuthFailure)
        ModerationSection.MAINTENANCE_HISTORY -> MaintenanceHistoryAdminPage(token, onAuthFailure)
        ModerationSection.CHAT -> ChatRetentionAdminPage(token, onAuthFailure)
        ModerationSection.UPDATE -> UpdateAdminPage(token, onAuthFailure)
        ModerationSection.CHANGELOG -> ChangelogAdminPage(token, onAuthFailure)
        ModerationSection.ROOMS -> RoomsAdminPage(token, onAuthFailure)
        ModerationSection.FEEDBACK -> FeedbackAdminPage(token, onAuthFailure)
        ModerationSection.BOT -> Unit
    }
}

@Composable
private fun AdminCard(title: String, description: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun IpAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope(); var ips by remember { mutableStateOf<List<String>>(emptyList()) }; var input by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var lookup by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(true) }
    fun reload() { scope.launch { loading = true; val r = CommunityApi.ownerRead(token, "ip.list"); loading = false; if (r.ok) ips = r.json.optJSONArray("ips")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() else { message = r.error; onAuthFailure(r) } } }
    LaunchedEffect(Unit) { reload() }
    AdminCard("Keamanan IP", "Blokir atau buka blokir alamat IP komunitas.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true, label = { Text("Alamat IP") }); Button(onClick = { val ip = input.trim(); if (ip.isNotBlank()) scope.launch { val r = CommunityApi.ownerWrite(token, "ip.ban", JSONObject().put("ip", ip)); message = if (r.ok) "IP diblokir." else r.error; reload() } }) { Text("Blokir") } }
        OutlinedButton(onClick = { val ip = input.trim(); if (ip.isNotBlank()) scope.launch { val r = CommunityApi.ownerRead(token, "ip.lookup", mapOf("ip" to ip)); if (r.ok) { val names = r.json.optJSONArray("usernames")?.let { a -> (0 until a.length()).joinToString { "@${a.optString(it)}" } }.orEmpty(); lookup = (if (r.json.optBoolean("banned")) "IP diblokir" else "IP tidak diblokir") + ". Akun terkait: " + names.ifBlank { "tidak ada" } } else { lookup = r.error; onAuthFailure(r) } } }) { Text("Periksa IP") }
        if (lookup.isNotBlank()) Text(lookup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = ::reload) { Text("Muat ulang") }; if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        ips.forEach { ip -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(ip, Modifier.weight(1f)); TextButton(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "ip.unban", JSONObject().put("ip", ip)); message = if (r.ok) "Blokir IP dibuka." else r.error; reload() } }) { Text("Buka blokir") } } }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MaintenanceAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope(); var enabled by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }; var eta by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val r = CommunityApi.ownerRead(token, "status.get"); if (r.ok) { val m = r.json.optJSONObject("status")?.optJSONObject("maintenance"); enabled = m?.optBoolean("enabled") ?: false; message = m?.optString("message").orEmpty(); eta = m?.optString("eta").orEmpty() } else onAuthFailure(r) }
    AdminCard("Maintenance", "Atur pemberitahuan pemeliharaan aplikasi.") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Aktifkan mode maintenance", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        OutlinedTextField(message, { message = it.take(800) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Deskripsi") })
        OutlinedTextField(eta, { eta = it.take(120) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Perkiraan selesai") })
        Button(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "maintenance.set", JSONObject().put("enabled", enabled).put("message", message).put("eta", eta)); notice = if (r.ok) "Pengaturan maintenance disimpan." else r.error; if (!r.ok) onAuthFailure(r) } }) { Text("Simpan") }
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChatRetentionAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var globalMinutes by remember { mutableStateOf("1440") }
    var dmMinutes by remember { mutableStateOf("1440") }
    var notice by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val r = CommunityApi.ownerRead(token, "chat.retention.get")
        if (r.ok) { val settings = r.json.optJSONObject("settings"); globalMinutes = settings?.optInt("globalMinutes", 1440)?.toString() ?: "1440"; dmMinutes = settings?.optInt("dmMinutes", 1440)?.toString() ?: "1440" }
        else { notice = r.error; onAuthFailure(r) }
    }
    AdminCard("Retensi chat otomatis", "Atur usia maksimum pesan komunitas sebelum dibersihkan otomatis. Rentang 1 menit hingga 30 hari.") {
        OutlinedTextField(globalMinutes, { globalMinutes = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Global Chat (menit)") })
        OutlinedTextField(dmMinutes, { dmMinutes = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Pesan pribadi (menit)") })
        Button(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "chat.retention.set", JSONObject().put("globalMinutes", globalMinutes.toIntOrNull() ?: 1440).put("dmMinutes", dmMinutes.toIntOrNull() ?: 1440)); notice = if (r.ok) "Retensi chat disimpan." else r.error; if (!r.ok) onAuthFailure(r) } }) { Text("Simpan") }
        Text("Contoh: 60 = 1 jam, 1440 = 1 hari. Perintah /clearall di Global Chat hanya dapat digunakan owner.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun UpdateAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope(); var enabled by remember { mutableStateOf(false) }; var version by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val r = CommunityApi.ownerRead(token, "status.get"); if (r.ok) { val u = r.json.optJSONObject("status")?.optJSONObject("update"); enabled = u?.optBoolean("enabled") ?: false; version = u?.optString("versionName").orEmpty(); code = u?.optInt("versionCode", 0)?.toString().orEmpty(); message = u?.optString("message").orEmpty(); url = u?.optString("downloadUrl").orEmpty() } else onAuthFailure(r) }
    AdminCard("Update aplikasi", "Kelola notifikasi versi terbaru dan link APK.") {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Tampilkan notifikasi update", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        OutlinedTextField(version, { version = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Nama versi") })
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(8) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Version code") })
        OutlinedTextField(message, { message = it.take(1500) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Deskripsi update") })
        OutlinedTextField(url, { url = it.take(500) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Link APK HTTPS") })
        Button(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "update.set", JSONObject().put("enabled", enabled).put("versionName", version).put("versionCode", code.toIntOrNull() ?: 0).put("message", message).put("downloadUrl", url)); notice = if (r.ok) "Pengaturan update disimpan." else r.error; if (!r.ok) onAuthFailure(r) } }) { Text("Simpan") }
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChangelogAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope(); var markdown by remember { mutableStateOf("") }; var notice by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val r = CommunityApi.ownerRead(token, "status.get"); if (r.ok) markdown = r.json.optJSONObject("changelog")?.optString("md").orEmpty() else onAuthFailure(r) }
    AdminCard("Changelog", "Tulis catatan rilis dalam format Markdown.") {
        OutlinedTextField(markdown, { markdown = it.take(60000) }, Modifier.fillMaxWidth(), minLines = 12, label = { Text("Isi changelog") })
        OutlinedButton(onClick = { scope.launch { val r = CommunityApi.remoteChangelog(); if (r.ok) { markdown = r.json.optString("md"); notice = "Changelog dimuat dari GitHub." } else { notice = r.error; onAuthFailure(r) } } }) { Text("Muat dari GitHub") }
        Button(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "changelog.set", JSONObject().put("md", markdown)); notice = if (r.ok) "Changelog disimpan." else r.error; if (!r.ok) onAuthFailure(r) } }) { Text("Simpan changelog") }
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
}


@Composable
private fun OwnerOverviewPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    fun reload() {
        scope.launch {
            loading = true
            val result = CommunityApi.ownerRead(token, "stats")
            loading = false
            if (result.ok) { data = result.json; message = "" }
            else { message = result.error; onAuthFailure(result) }
        }
    }
    LaunchedEffect(Unit) { reload() }
    AdminCard("Ringkasan komunitas", "Statistik akun, keamanan IP, dan aktivitas terbaru.") {
        OutlinedButton(onClick = ::reload) { Text("Muat ulang") }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        data?.let { d ->
            Text("Akun aktif: ${d.optInt("users")}   ·   Arsip: ${d.optInt("archivedUsers")}   ·   IP diblokir: ${d.optInt("bannedIps")}", style = MaterialTheme.typography.titleSmall)
            Text("Log aktivitas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val audit = d.optJSONArray("audit")
            if (audit == null || audit.length() == 0) Text("Belum ada aktivitas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else (0 until audit.length()).forEach { index ->
                val row = audit.optJSONObject(index) ?: JSONObject()
                Text("${row.optString("by", "owner")} · ${row.optString("msg")}\n${row.optLong("ts")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (index < audit.length() - 1) HorizontalDivider()
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MaintenanceHistoryAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var snapshots by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var notice by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var deletingId by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var eta by remember { mutableStateOf("") }
    fun reload() {
        scope.launch {
            loading = true
            val r = CommunityApi.ownerRead(token, "maintenance.history.list")
            loading = false
            if (r.ok) snapshots = r.json.optJSONArray("snapshots")?.let { a -> (0 until a.length()).mapNotNull(a::optJSONObject) } ?: emptyList()
            else { notice = r.error; onAuthFailure(r) }
        }
    }
    LaunchedEffect(Unit) { reload() }
    AdminCard("Histori maintenance", "Snapshot tersimpan dapat diedit, diterapkan, atau dihapus.") {
        OutlinedButton(onClick = ::reload) { Text("Muat ulang") }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        snapshots.forEach { item ->
            val id = item.optString("id")
            Text(if (item.optBoolean("enabled")) "Maintenance aktif" else "Maintenance nonaktif", fontWeight = FontWeight.SemiBold)
            Text(item.optString("message").ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            Text("ETA: ${item.optString("eta").ifBlank { "—" }} · ${item.optLong("createdAt")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { enabled = item.optBoolean("enabled"); description = item.optString("message"); eta = item.optString("eta"); editing = item }) { Text("Edit") }
                TextButton(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "maintenance.history.apply", JSONObject().put("id", id)); notice = if (r.ok) "Snapshot diterapkan." else r.error; if (!r.ok) onAuthFailure(r) } }) { Text("Terapkan") }
                TextButton(onClick = { deletingId = id }) { Text("Hapus") }
            }
            HorizontalDivider()
        }
        if (snapshots.isEmpty() && !loading) Text("Belum ada snapshot maintenance.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
    editing?.let { item ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit snapshot maintenance") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Maintenance aktif", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
                OutlinedTextField(description, { description = it.take(800) }, label = { Text("Deskripsi") }, minLines = 3)
                OutlinedTextField(eta, { eta = it.take(120) }, label = { Text("Perkiraan selesai") }, singleLine = true)
            } },
            confirmButton = { Button(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "maintenance.history.update", JSONObject().put("id", item.optString("id")).put("enabled", enabled).put("message", description).put("eta", eta)); notice = if (r.ok) "Snapshot diperbarui." else r.error; if (!r.ok) onAuthFailure(r); editing = null; reload() } }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Batal") } },
        )
    }
    if (deletingId.isNotBlank()) AlertDialog(
        onDismissRequest = { deletingId = "" }, title = { Text("Hapus snapshot?") }, text = { Text("Snapshot ini akan disembunyikan dari histori.") },
        confirmButton = { Button(onClick = { val id = deletingId; deletingId = ""; scope.launch { val r = CommunityApi.ownerWrite(token, "maintenance.history.delete", JSONObject().put("id", id)); notice = if (r.ok) "Snapshot dihapus." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { deletingId = "" }) { Text("Batal") } },
    )
}

@Composable
private fun RoomsAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var closedAll by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf("") }
    var editRoom by remember { mutableStateOf<JSONObject?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftHost by remember { mutableStateOf("") }
    var closeCode by remember { mutableStateOf("") }
    var confirmCloseAll by remember { mutableStateOf(false) }
    fun reload() {
        scope.launch {
            loading = true
            val r = CommunityApi.ownerRead(token, "rooms.list")
            loading = false
            if (r.ok) { rooms = r.json.optJSONArray("rooms")?.let { a -> (0 until a.length()).mapNotNull(a::optJSONObject) } ?: emptyList(); closedAll = r.json.optBoolean("closedAll") }
            else { notice = r.error; onAuthFailure(r) }
        }
    }
    LaunchedEffect(Unit) { reload() }
    AdminCard("Room Control", "Kelola metadata room. Room WebSocket eksternal mungkin perlu dihentikan dari server transportnya.") {
        Text(if (closedAll) "Pembuatan room ditutup." else "Pembuatan room aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = ::reload) { Text("Muat ulang") }
            if (closedAll) Button(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "rooms.openAll"); notice = if (r.ok) "Pembuatan room dibuka." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Buka semua") }
            else Button(onClick = { confirmCloseAll = true }) { Text("Tutup semua") }
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        rooms.forEach { room ->
            val code = room.optString("code")
            Text(room.optString("roomName").ifBlank { "Ruang dengar bersama" }, fontWeight = FontWeight.SemiBold)
            Text("${room.optString("visibility")} · kode $code · host @${room.optString("host")} · ${room.optInt("members")} anggota", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { draftName = room.optString("roomName"); draftHost = room.optString("hostName"); editRoom = room }) { Text("Ubah nama") }
                TextButton(onClick = { scope.launch { val next = if (room.optString("visibility") == "public") "private" else "public"; val r = CommunityApi.ownerWrite(token, "rooms.update", JSONObject().put("code", code).put("visibility", next)); notice = if (r.ok) "Visibilitas room diperbarui." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text(if (room.optString("visibility") == "public") "Jadikan privat" else "Jadikan publik") }
                TextButton(onClick = { closeCode = code }) { Text("Tutup room") }
            }
            HorizontalDivider()
        }
        if (rooms.isEmpty() && !loading) Text("Tidak ada room aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
    editRoom?.let { room -> AlertDialog(
        onDismissRequest = { editRoom = null }, title = { Text("Ubah informasi room") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(draftName, { draftName = it.take(60) }, label = { Text("Nama room") }); OutlinedTextField(draftHost, { draftHost = it.take(80) }, label = { Text("Nama tampilan host") }) } },
        confirmButton = { Button(onClick = { val code = room.optString("code"); editRoom = null; scope.launch { val r = CommunityApi.ownerWrite(token, "rooms.update", JSONObject().put("code", code).put("roomName", draftName).put("hostName", draftHost)); notice = if (r.ok) "Room diperbarui." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = { editRoom = null }) { Text("Batal") } },
    ) }
    if (closeCode.isNotBlank()) AlertDialog(
        onDismissRequest = { closeCode = "" }, title = { Text("Tutup room $closeCode?") }, text = { Text("Kode undangan room tidak akan aktif lagi.") },
        confirmButton = { Button(onClick = { val code = closeCode; closeCode = ""; scope.launch { val r = CommunityApi.ownerWrite(token, "rooms.close", JSONObject().put("code", code)); notice = if (r.ok) "Room ditutup." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Tutup") } },
        dismissButton = { TextButton(onClick = { closeCode = "" }) { Text("Batal") } },
    )
    if (confirmCloseAll) AlertDialog(
        onDismissRequest = { confirmCloseAll = false }, title = { Text("Tutup semua room?") }, text = { Text("Semua metadata room di server ini akan dihapus dan room baru ditolak sampai dibuka kembali.") },
        confirmButton = { Button(onClick = { confirmCloseAll = false; scope.launch { val r = CommunityApi.ownerWrite(token, "rooms.closeAll"); notice = if (r.ok) "Semua room ditutup." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Tutup semua") } },
        dismissButton = { TextButton(onClick = { confirmCloseAll = false }) { Text("Batal") } },
    )
}

@Composable
private fun FeedbackAdminPage(token: String, onAuthFailure: (ApiResult) -> Unit) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var feedback by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf("") }
    fun reload() {
        scope.launch {
            loading = true
            val r = CommunityApi.ownerRead(token, "feedback.list")
            loading = false
            if (r.ok) feedback = r.json.optJSONArray("feedback")?.let { a -> (0 until a.length()).mapNotNull(a::optJSONObject) } ?: emptyList()
            else { notice = r.error; onAuthFailure(r) }
        }
    }
    LaunchedEffect(Unit) { reload() }
    AdminCard("Masukan komunitas", "Kelola laporan bug dan permintaan fitur. Lampiran URL publik tak-tercantum; jangan membagikan data sensitif.") {
        OutlinedButton(onClick = ::reload) { Text("Muat ulang") }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        feedback.forEach { item ->
            val id = item.optString("id")
            Text(item.optString("title").ifBlank { "Laporan komunitas" }, fontWeight = FontWeight.SemiBold)
            Text("${if (item.optString("type") == "bug") "Bug" else "Fitur"} · @${item.optString("username")} · ${item.optString("status")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.optString("description"), style = MaterialTheme.typography.bodySmall)
            item.optJSONObject("attachment")?.let { attachment ->
                val url = attachment.optString("url")
                if (url.startsWith("https://")) TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Buka lampiran (${attachment.optString("contentType")})") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "feedback.update", JSONObject().put("id", id).put("status", "processing")); notice = if (r.ok) "Laporan ditandai diproses." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Diproses") }
                TextButton(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "feedback.update", JSONObject().put("id", id).put("status", "done")); notice = if (r.ok) "Laporan ditandai selesai." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Selesai") }
                if (item.optString("status") != "archived") TextButton(onClick = { scope.launch { val r = CommunityApi.ownerWrite(token, "feedback.archive", JSONObject().put("id", id)); notice = if (r.ok) "Laporan diarsipkan." else r.error; if (!r.ok) onAuthFailure(r); reload() } }) { Text("Arsipkan") }
            }
            HorizontalDivider()
        }
        if (feedback.isEmpty() && !loading) Text("Belum ada laporan komunitas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.primary)
    }
}
