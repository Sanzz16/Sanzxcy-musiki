package com.metrolist.music.community

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotModerationSettingsScreen(navController: NavController) {
    val state = rememberCommunityState()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var verifiedOwner by remember { mutableStateOf(false) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(BotModerationSettings()) }
    var blockedWordsText by remember { mutableStateOf("") }
    var spamMuteInput by remember { mutableStateOf("") }
    var warningMuteInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var userQuery by remember { mutableStateOf("") }
    var archivedUsers by remember { mutableStateOf(false) }
    var usersLoading by remember { mutableStateOf(false) }
    var userMessage by remember { mutableStateOf<String?>(null) }
    var temporaryPassword by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<OwnerDialog?>(null) }
    var section by remember { mutableStateOf(ModerationSection.BOT) }

    suspend fun reloadUsers() {
        val token = state.token ?: return
        usersLoading = true
        val result = CommunityApi.ownerUsers(token, userQuery.trim(), archivedUsers)
        usersLoading = false
        if (result.ok) {
            users = result.json.optJSONArray("users")?.let { a -> (0 until a.length()).map { parseUser(a.getJSONObject(it)) } }.orEmpty()
            userMessage = null
        } else {
            userMessage = result.error.ifBlank { "Daftar user tidak dapat dimuat." }
            state.handleAuthFailure(result)
        }
    }

    LaunchedEffect(state) {
        loading = true
        state.restore()
        val token = state.token
        if (token.isNullOrBlank() || state.me?.role != "owner") {
            error = "Akses hanya tersedia untuk akun owner yang terautentikasi."
            loading = false
            return@LaunchedEffect
        }
        val roleCheck = CommunityApi.me(token)
        val verifiedUser = if (roleCheck.ok) runCatching { parseUser(roleCheck.json.getJSONObject("user")) }.getOrNull() else null
        if (verifiedUser?.role != "owner") {
            if (!roleCheck.ok) state.handleAuthFailure(roleCheck)
            error = "Akses owner tidak dapat diverifikasi oleh server."
            loading = false
            return@LaunchedEffect
        }
        state.updateMe(verifiedUser)
        verifiedOwner = true
        val response = CommunityApi.botModerationSettings(token)
        if (response.ok) {
            val parsed = parseBotModerationSettings(response.json.optJSONObject("settings"))
            if (parsed != null) {
                settings = parsed
                blockedWordsText = parsed.extraBlockedWords.joinToString("\n")
                spamMuteInput = parsed.spamMuteSeconds.toString()
                warningMuteInput = parsed.warningMuteMinutes.toString()
                settingsLoaded = true
            } else error = "Format pengaturan dari server tidak valid."
        } else {
            error = response.error.ifBlank { "Pengaturan moderasi tidak dapat dimuat." }
            state.handleAuthFailure(response)
        }
        if (verifiedOwner && settingsLoaded) reloadUsers()
        loading = false
    }

    fun save() {
        val token = state.token ?: return
        val words = blockedWordsText.split(',', '\n', '\r').map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val sanitized = settings.copy(
            extraBlockedWords = words,
            spamMuteSeconds = (spamMuteInput.toIntOrNull() ?: settings.spamMuteSeconds).coerceIn(10, 86400),
            warningMuteMinutes = (warningMuteInput.toIntOrNull() ?: settings.warningMuteMinutes).coerceIn(1, 43200),
            cooldownSeconds = settings.cooldownSeconds.coerceIn(5, 300),
            warningLimit = settings.warningLimit.coerceIn(1, 10),
            noticeSeconds = settings.noticeSeconds.coerceIn(1, 60),
        )
        scope.launch {
            saving = true; error = null; savedMessage = null
            val result = CommunityApi.setBotModerationSettings(token, sanitized)
            saving = false
            if (result.ok) {
                settings = parseBotModerationSettings(result.json.optJSONObject("settings")) ?: sanitized
                settingsLoaded = true; blockedWordsText = settings.extraBlockedWords.joinToString("\n")
                spamMuteInput = settings.spamMuteSeconds.toString(); warningMuteInput = settings.warningMuteMinutes.toString()
                savedMessage = "Pengaturan moderasi berhasil disimpan."
            } else { error = result.error.ifBlank { "Pengaturan gagal disimpan." }; state.handleAuthFailure(result) }
        }
    }

    fun perform(action: String, user: UserDto, extra: JSONObject = JSONObject(), success: String) {
        val token = state.token ?: return
        scope.launch {
            val result = CommunityApi.ownerUserAction(token, action, user.username, extra)
            if (result.ok) { userMessage = success; reloadUsers() }
            else { userMessage = result.error.ifBlank { "Aksi gagal." }; state.handleAuthFailure(result) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Moderasi Bot") }, navigationIcon = { TextButton(onClick = navController::navigateUp) { Text("‹ Kembali") } })
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Text("Memeriksa akses owner dan memuat pengaturan…", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else if (!verifiedOwner || !settingsLoaded) {
                SettingsMessageCard(error ?: "Akses owner tidak tersedia.", isError = true)
            } else {
                ModerationNavbar(section) { section = it }
                if (section == ModerationSection.BOT) {
                Text("Atur filter otomatis dan penanganan pelanggaran Global Chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsCard {
                    Text("Filter pesan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ModerationSwitch("Bahasa kasar", settings.rudeEnabled) { settings = settings.copy(rudeEnabled = it) }
                    HorizontalDivider(); ModerationSwitch("Spam berulang", settings.spamEnabled) { settings = settings.copy(spamEnabled = it) }
                    HorizontalDivider(); ModerationSwitch("Tautan", settings.linksEnabled) { settings = settings.copy(linksEnabled = it) }
                    HorizontalDivider(); ModerationSwitch("Promosi dan spam promosi", settings.promotionEnabled) { settings = settings.copy(promotionEnabled = it) }
                    Text("Filter SARA berat selalu aktif dan tidak dapat dimatikan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                SettingsCard {
                    Text("Kata tambahan yang diblokir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Pisahkan kata atau frasa dengan koma atau baris baru.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = blockedWordsText, onValueChange = { blockedWordsText = it.take(4000) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, shape = RoundedCornerShape(18.dp), label = { Text("Kata/frasa terlarang") })
                }
                SettingsCard {
                    Text("Jeda dan ambang sanksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SliderSetting("Cooldown kirim", "${settings.cooldownSeconds} detik", settings.cooldownSeconds.toFloat(), 5f..300f, 58) { settings = settings.copy(cooldownSeconds = it.roundToInt().coerceIn(5, 300)) }
                    NumberSetting("Mute spam", spamMuteInput, "detik (10–86400)") { spamMuteInput = it; it.toIntOrNull()?.let { v -> settings = settings.copy(spamMuteSeconds = v.coerceIn(10, 86400)) } }
                    SliderSetting("Batas peringatan", "${settings.warningLimit} peringatan", settings.warningLimit.toFloat(), 1f..10f, 8) { settings = settings.copy(warningLimit = it.roundToInt().coerceIn(1, 10)) }
                    NumberSetting("Mute setelah batas peringatan", warningMuteInput, "menit (1–43200)") { warningMuteInput = it; it.toIntOrNull()?.let { v -> settings = settings.copy(warningMuteMinutes = v.coerceIn(1, 43200)) } }
                    SliderSetting("Lama notice bot", "${settings.noticeSeconds} detik", settings.noticeSeconds.toFloat(), 1f..60f, 58) { settings = settings.copy(noticeSeconds = it.roundToInt().coerceIn(1, 60)) }
                }
                savedMessage?.let { SettingsMessageCard(it, false) }; error?.let { SettingsMessageCard(it, true) }
                Button(onClick = ::save, enabled = !saving, modifier = Modifier.fillMaxWidth().height(50.dp)) { if (saving) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text("Simpan pengaturan bot") }

                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Daftar pengguna", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("Kelola user seperti di owner.html", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { archivedUsers = !archivedUsers; scope.launch { reloadUsers() } }) { Text(if (archivedUsers) "Aktif" else "Arsip") }
                    }
                    OutlinedTextField(userQuery, { userQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cari username atau IP") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { scope.launch { reloadUsers() } }) { Text("Cari") }; if (!archivedUsers) TextButton(onClick = { dialog = OwnerDialog.Create }) { Text("Buat akun") } }
                    userMessage?.let { SettingsMessageCard(it, userMessage!!.contains("gagal", true) || userMessage!!.contains("tidak", true)) }
                    if (usersLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    users.forEach { user -> OwnerUserCard(user, onAction = { action ->
                        when (action) {
                            "reset" -> dialog = OwnerDialog.Confirm(user, "reset")
                            "delete" -> dialog = OwnerDialog.Confirm(user, "delete")
                            "role" -> dialog = OwnerDialog.EditRole(user)
                            "title" -> dialog = OwnerDialog.EditTitle(user)
                            "ban" -> perform("user.ban", user, success = "Akun @${user.username} diblokir")
                            "unban" -> perform("user.unban", user, success = "Blokir @${user.username} dibuka")
                            "mute" -> perform("user.mute", user, JSONObject().put("minutes", 7 * 24 * 60), "@${user.username} di-mute 7 hari")
                            "unmute" -> perform("user.unmute", user, success = "Mute @${user.username} dibuka")
                            "warns" -> perform("user.warns.reset", user, success = "Peringatan @${user.username} direset")
                            "ip" -> perform("ip.ban", user, JSONObject().put("ip", user.lastIp), "IP ${user.lastIp} diblokir")
                            "archive" -> perform("user.archive", user, success = "@${user.username} diarsipkan")
                            "unarchive" -> perform("user.unarchive", user, success = "@${user.username} dipulihkan")
                        }
                    }) }
                    if (users.isEmpty() && !usersLoading) Text(if (archivedUsers) "Belum ada user di arsip." else "Belum ada user.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                } else {
                    ModerationExtraPages(section, state.token.orEmpty()) { result -> scope.launch { state.handleAuthFailure(result) } }
                }
            }
        }
    }

    when (val d = dialog) {
        OwnerDialog.Create -> OwnerCreateDialog(onDismiss = { dialog = null }, onCreate = { username, role -> dialog = null; scope.launch { val r = CommunityApi.ownerCreateUser(state.token ?: return@launch, username, role); userMessage = if (r.ok) "Akun @$username dibuat." else r.error; if (r.ok) temporaryPassword = r.json.optString("tempPassword").takeIf(String::isNotBlank); reloadUsers() } })
        is OwnerDialog.Confirm -> if (d.action == "delete") OwnerDeleteDialog(d.user, onDismiss = { dialog = null }) { dialog = null; perform("user.delete", d.user, success = "Akun @${d.user.username} dihapus") } else AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Reset password?") }, text = { Text("Password lama @${d.user.username} dicabut. Password sementara hanya ditampilkan sekali.") }, confirmButton = { Button(onClick = { dialog = null; val token = state.token ?: return@Button; scope.launch { val r = CommunityApi.ownerUserAction(token, "user.resetPassword", d.user.username); if (r.ok) { temporaryPassword = r.json.optString("tempPassword").takeIf(String::isNotBlank); userMessage = "Password @${d.user.username} berhasil direset." } else userMessage = r.error; reloadUsers() } }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } })
        is OwnerDialog.EditRole -> OwnerInputDialog("Edit role @${d.user.username}", d.user.role, "user, staff, admin, owner", { dialog = null }) { value -> dialog = null; perform("user.setRole", d.user, JSONObject().put("role", value.trim()), "Role @${d.user.username} diperbarui") }
        is OwnerDialog.EditTitle -> OwnerInputDialog("Edit title @${d.user.username}", d.user.title?.text.orEmpty(), "Title komunitas", { dialog = null }) { value -> dialog = null; val title = if (value.isBlank()) JSONObject.NULL else JSONObject().put("text", value.take(16)).put("color", "#9ED7CB").put("shape", "pill").put("icon", "star"); perform("user.setTitle", d.user, JSONObject().put("title", title), "Title @${d.user.username} diperbarui") }
        null -> Unit
    }
    temporaryPassword?.let { password -> AlertDialog(onDismissRequest = { temporaryPassword = null }, title = { Text("Password sementara — salin sekarang") }, text = { Text(password, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }, confirmButton = { TextButton(onClick = { temporaryPassword = null }) { Text("Selesai") } }) }
}

private sealed interface OwnerDialog { data object Create : OwnerDialog; data class Confirm(val user: UserDto, val action: String) : OwnerDialog; data class EditRole(val user: UserDto) : OwnerDialog; data class EditTitle(val user: UserDto) : OwnerDialog }

@Composable private fun OwnerUserCard(user: UserDto, onAction: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${user.displayName}  @${user.username}", fontWeight = FontWeight.SemiBold)
            Text("Role: ${user.role}${if (user.banned) " · Diblokir" else ""}${if (user.mustChange) " · Wajib ganti password" else ""}", style = MaterialTheme.typography.bodySmall)
            Text("Peringatan: ${user.warns}${if (user.lastIp.isNotBlank()) " · IP ${user.lastIp}" else ""}${if (user.joinedAt > 0) " · Bergabung ${user.joinedAt}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onAction("role") }) { Text("Role") }; TextButton(onClick = { onAction("title") }) { Text("Title") }; TextButton(onClick = { onAction("reset") }) { Text("Reset password") }
                if (user.lastIp.isNotBlank()) TextButton(onClick = { onAction("ip") }) { Text("Blokir IP") }
                TextButton(onClick = { onAction(if (user.banned) "unban" else "ban") }) { Text(if (user.banned) "Buka blokir" else "Blokir") }; TextButton(onClick = { onAction("mute") }) { Text("Mute") }; TextButton(onClick = { onAction("warns") }) { Text("Reset peringatan") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { onAction(if (user.archivedAt > 0) "unarchive" else "archive") }) { Text(if (user.archivedAt > 0) "Pulihkan" else "Arsipkan") }; TextButton(onClick = { onAction("delete") }) { Text("Hapus") } }
        }
    }
}

@Composable private fun OwnerDeleteDialog(user: UserDto, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var confirmation by remember(user.username) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus akun @${user.username}?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Data profil dan akses akun akan dihapus. Ketik username dengan tepat untuk melanjutkan.")
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Username") }, singleLine = true)
        } },
        confirmButton = { Button(onClick = onConfirm, enabled = confirmation == user.username) { Text("Hapus akun") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable private fun OwnerCreateDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }; var role by remember { mutableStateOf("user") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Buat akun komunitas") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(username, { username = it.lowercase().take(20) }, label = { Text("Username") }, singleLine = true); OutlinedTextField(role, { role = it.lowercase().take(5) }, label = { Text("Role: user/staff/admin/owner") }, singleLine = true) } }, confirmButton = { Button(onClick = { if (username.matches(Regex("[a-z0-9_.]{3,20}"))) onCreate(username, role) }) { Text("Buat") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable private fun OwnerInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var value by remember { mutableStateOf(initial) }; AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) }, confirmButton = { Button(onClick = { onConfirm(value) }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }) }

@Composable private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
@Composable private fun ModerationSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange) } }
@Composable private fun SliderSetting(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) { Column { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }; Slider(value, onValueChange, valueRange = range, steps = steps) } }
@Composable private fun NumberSetting(label: String, value: String, hint: String, onValueChange: (String) -> Unit) { OutlinedTextField(value, { onValueChange(it.filter(Char::isDigit).take(6)) }, Modifier.fillMaxWidth(), label = { Text(label) }, supportingText = { Text(hint) }, singleLine = true, shape = RoundedCornerShape(18.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
@Composable private fun SettingsMessageCard(message: String, isError: Boolean) { Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Text(message, color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(14.dp)) } }
