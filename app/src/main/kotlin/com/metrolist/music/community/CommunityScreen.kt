/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: tab Komunitas (daftar/login, Global Chat, pesan pribadi, profil, dengar bersama).
 */

package com.metrolist.music.community

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items as gridItems
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.theme.LocalLiquidGlass
import com.sanzxcy.music.com.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalCommunity = compositionLocalOf<CommunityState> { error("CommunityState belum tersedia") }

private sealed interface Page {
    data object Main : Page
    data object EditProfile : Page
    data object StickerMaker : Page
    data object Invites : Page
    data object Announcements : Page
    data class Dm(val room: String, val peer: UserDto) : Page
}

private val RULES_ALLOWED =
    listOf(
        "Tanya jawab.",
        "Lapor bug atau share tips yang relevan dengan ekosistem Sanzxcy.",
        "Diskusi teknis dalam konteks penggunaan Sanzxcy.",
    )

private val RULES_FORBIDDEN =
    listOf(
        "Bahasa kasar, hinaan, atau pelecehan.",
        "Promo jualan: open order, ready stock, dm/wa me, jual apk, dll.",
        "Telegram / tele / tili / TikTok / IG / igoy, jualan, atau pindah chat.",
        "Bypass (spasi, skip huruf, salah tulis, leetspeak) tetap kena filter otomatis.",
        "Menulis nomor HP / WA.",
        "Promosi APK / brand / merk lain (WA mod, competitor, dll).",
        "Source code / script / payload / command / snippet programming (termasuk code block).",
        "Konten off-topic: pulsa/kuota, judi, crypto, rekrut tim, spam iklan.",
        "Menghina agama, rasis, dan sejenisnya akan dikenakan sanksi berupa ban permanen untuk fitur komunitas saja.",
    )

@Composable
fun CommunityRoot(onOpenListenTogether: () -> Unit = {}, initialListenTab: Boolean = false) {
    val state = rememberCommunityState()
    LaunchedEffect(Unit) { state.restore() }
    CompositionLocalProvider(LocalCommunity provides state) {
        val insets = LocalPlayerAwareWindowInsets.current
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            when {
                !state.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !state.loggedIn && state.hasSavedSession -> SessionRestoreGate(state)
                !state.loggedIn -> AuthGate(state)
                else -> CommunityHome(state, onOpenListenTogether, initialListenTab)
            }
        }
    }
}

@Composable
private fun SessionRestoreGate(state: CommunityState) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_static_foreground), null, Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black))
        Spacer(Modifier.height(18.dp))
        Text("Sesi komunitas tersimpan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            state.restoreError ?: "Profil belum bisa dimuat. Sesi kamu tetap tersimpan; coba lagi saat koneksi tersedia.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(enabled = !checking, onClick = {
            scope.launch { checking = true; state.restore(); checking = false }
        }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Coba pulihkan sesi")
        }
        TextButton(onClick = { scope.launch { state.logout() } }) { Text("Keluar dan masuk dengan akun lain") }
    }
}

// ───────────────────────── Daftar / Login ─────────────────────────

@Composable
private fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val visibilityDescription = stringResource(if (visible) R.string.community_hide_password else R.string.community_show_password)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(painterResource(R.drawable.lock), contentDescription = null) },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.semantics { contentDescription = visibilityDescription },
            ) {
                Canvas(Modifier.size(24.dp)) {
                    val eye = Path().apply {
                        moveTo(size.width * 0.08f, size.height * 0.5f)
                        cubicTo(size.width * 0.25f, size.height * 0.18f, size.width * 0.4f, size.height * 0.1f, size.width * 0.5f, size.height * 0.1f)
                        cubicTo(size.width * 0.6f, size.height * 0.1f, size.width * 0.75f, size.height * 0.18f, size.width * 0.92f, size.height * 0.5f)
                        cubicTo(size.width * 0.75f, size.height * 0.82f, size.width * 0.6f, size.height * 0.9f, size.width * 0.5f, size.height * 0.9f)
                        cubicTo(size.width * 0.4f, size.height * 0.9f, size.width * 0.25f, size.height * 0.82f, size.width * 0.08f, size.height * 0.5f)
                    }
                    drawPath(eye, tint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(tint, radius = 2.1.dp.toPx())
                    if (!visible) {
                        drawLine(tint, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.82f), 1.8.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        },
        singleLine = true,
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthGate(state: CommunityState) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showIntro by rememberSaveable { mutableStateOf(!state.registrationIntroSeen) }
    var registerMode by rememberSaveable { mutableStateOf(true) }
    var username by rememberSaveable { mutableStateOf(state.rememberedUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var forgotMode by rememberSaveable { mutableStateOf(false) }
    var recoveryEmail by rememberSaveable { mutableStateOf("") }
    var recoveryCode by rememberSaveable { mutableStateOf("") }
    var recoveryPassword by rememberSaveable { mutableStateOf("") }
    var showRecoveryPassword by rememberSaveable { mutableStateOf(false) }
    var recoveryCodeSent by rememberSaveable { mutableStateOf(false) }
    var recoveryMessage by remember { mutableStateOf<String?>(null) }
    var agree by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val insets = LocalPlayerAwareWindowInsets.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    if (showIntro) {
        AlertDialog(
            onDismissRequest = { showIntro = false },
            title = { Text("Daftar dulu ya") },
            text = { Text("Silakan daftar terlebih dahulu sebelum bisa login ke Komunitas Sanzxcy. Kalau sudah punya akun, pilih Login.") },
            confirmButton = { TextButton(onClick = { showIntro = false; scope.launch { state.acknowledgeRegistrationIntro() } }) { Text("Mengerti") } },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_launcher_static_foreground),
            contentDescription = null,
            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black),
        )
        Spacer(Modifier.height(16.dp))
        Text("Komunitas Sanzxcy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Ngobrol bareng, dengar lagu bersama, dan kenalan dengan pengguna lain.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = registerMode, onClick = { registerMode = true; error = null }, label = { Text("Daftar") })
                    FilterChip(selected = !registerMode, onClick = { registerMode = false; error = null }, label = { Text("Login") })
                }
                Spacer(Modifier.height(12.dp))

                state.blockedMessage?.let {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = if (registerMode) it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20)
                        else it.trim().lowercase().take(254)
                    },
                    label = { Text(if (registerMode) "Username" else "Username atau email terverifikasi") },
                    leadingIcon = { Icon(painterResource(R.drawable.account), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                PasswordInput(password, { password = it.take(64) }, "Password", showPassword, { showPassword = !showPassword })
                if (registerMode) {
                    Spacer(Modifier.height(10.dp))
                    PasswordInput(confirm, { confirm = it.take(64) }, "Ulangi password", showConfirm, { showConfirm = !showConfirm })
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().glassBorder(RoundedCornerShape(18.dp), LocalLiquidGlass.current),
                    ) {
                        Text(
                            "Email pemulihan bersifat opsional. Setelah diverifikasi, kamu bisa reset password dengan kode email. Jika tidak menautkan email dan lupa password, minta staf/admin/owner mereset akun. Jangan bagikan kode atau password. IP perangkat dicatat untuk keamanan komunitas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Checkbox(checked = agree, onCheckedChange = { agree = it })
                        Text("Saya setuju dengan aturan Global Chat", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center) }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !busy && username.length >= 3 && password.length >= 6 && (!registerMode || (agree && confirm == password)),
                    onClick = {
                        busy = true; error = null
                        scope.launch {
                            val r = state.authenticate(registerMode, username, password)
                            busy = false
                            if (!r.ok) error = r.error
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else {
                        Icon(painterResource(R.drawable.login), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (registerMode) "Daftar" else "Login")
                    }
                }
                if (registerMode && confirm.isNotEmpty() && confirm != password) {
                    Text("Password tidak sama", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                if (!registerMode) {
                    TextButton(onClick = {
                        recoveryEmail = username.takeIf { it.contains('@') }.orEmpty()
                        forgotMode = !forgotMode; recoveryCodeSent = false; recoveryMessage = null; error = null
                    }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) { Text(if (forgotMode) "Kembali ke login" else "Lupa password?") }
                    if (forgotMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("Email pemulihan harus sudah diverifikasi pada profil. Jika akunmu tidak memakai email, minta staf/admin/owner mereset password.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(recoveryEmail, { recoveryEmail = it.trim().lowercase().take(254) }, label = { Text("Email pemulihan") }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                            if (!recoveryCodeSent) {
                                Button(enabled = !busy && recoveryEmail.contains('@'), onClick = {
                                    scope.launch {
                                        busy = true; error = null
                                        val r = CommunityApi.requestPasswordReset(recoveryEmail)
                                        busy = false
                                        if (r.ok) { recoveryCodeSent = true; recoveryMessage = "Jika email terdaftar dan terverifikasi, kode sudah dikirim. Periksa Inbox/Spam." }
                                        else error = r.error
                                    }
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Kirim kode ke Gmail") }
                            } else {
                                OutlinedTextField(recoveryCode, { recoveryCode = it.filter(Char::isDigit).take(6) }, label = { Text("Kode 6 digit") }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                                PasswordInput(recoveryPassword, { recoveryPassword = it.take(64) }, "Password baru (min. 6)", showRecoveryPassword, { showRecoveryPassword = !showRecoveryPassword })
                                Button(enabled = !busy && recoveryCode.length == 6 && recoveryPassword.length >= 6, onClick = {
                                    scope.launch {
                                        busy = true; error = null
                                        val r = state.completePasswordReset(recoveryEmail, recoveryCode, recoveryPassword)
                                        busy = false
                                        if (!r.ok) error = r.error
                                    }
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Simpan password dan masuk") }
                            }
                            recoveryMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                } else {
                    TextButton(onClick = { showIntro = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) { Text("Kenapa harus daftar?") }
                }
            }
        }
    }
}

// ───────────────────────── Beranda Komunitas ─────────────────────────

@Composable
private fun CommunityHome(state: CommunityState, onOpenListenTogether: () -> Unit, initialListenTab: Boolean = false) {
    val scope = rememberCoroutineScope()
    val username = state.me?.username.orEmpty()
    val listenRoomSession = remember(username) { CommunityListenRoomSessions.forUser(username) }
    var page by remember { mutableStateOf<Page>(Page.Main) }
    var tab by rememberSaveable(initialListenTab) { mutableStateOf(if (initialListenTab) 4 else 0) }
    var profileUser by remember { mutableStateOf<String?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackSent by remember { mutableStateOf(false) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(state.mustChangePassword) }
    var stickerMakerReturnPage by remember { mutableStateOf<Page>(Page.EditProfile) }
    val token = state.token ?: return
    val me = state.me ?: return
    val context = LocalContext.current
    var pendingInviteCount by remember { mutableStateOf(0) }
    ListenRoomSyncEffect(state, listenRoomSession)
    fun openStickerMaker(returnPage: Page) {
        stickerMakerReturnPage = returnPage
        page = Page.StickerMaker
    }

    LaunchedEffect(token) {
        var previousCount = -1
        var previousSentStatuses = emptyMap<String, String>()
        var sentStatusInitialized = false
        while (true) {
            val result = CommunityApi.invites(token)
            if (result.ok) {
                val invites = result.json.optJSONArray("invites")
                val count = if (invites == null) 0 else (0 until invites.length()).count { invites.optJSONObject(it)?.optString("status") == "pending" }
                pendingInviteCount = count
                if (previousCount >= 0 && count > previousCount) android.widget.Toast.makeText(context, "Kamu punya undangan teman baru", android.widget.Toast.LENGTH_SHORT).show()
                previousCount = count
                val sent = result.json.optJSONArray("sent")
                val currentSentInvites = if (sent == null) emptyList() else (0 until sent.length()).mapNotNull { sent.optJSONObject(it) }
                val currentSentStatuses = currentSentInvites.associate { it.optString("id") to it.optString("status") }
                if (sentStatusInitialized) {
                    currentSentStatuses.forEach { (id, status) ->
                        if (previousSentStatuses[id] == "pending" && status in setOf("accepted", "declined")) {
                            val recipient = currentSentInvites.firstOrNull { it.optString("id") == id }?.optString("to").orEmpty()
                            val message = context.getString(
                                if (status == "accepted") R.string.community_invite_accepted_by else R.string.community_invite_declined_by,
                                recipient,
                            )
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                previousSentStatuses = currentSentStatuses
                sentStatusInitialized = true
            }
            delay(25_000)
        }
    }

    BackHandler(enabled = page != Page.Main) { page = if (page == Page.StickerMaker) stickerMakerReturnPage else Page.Main }

    if (showPasswordDialog) ChangePasswordDialog(state, forced = state.mustChangePassword, onDismiss = { showPasswordDialog = false })
    if (showRules) RulesDialog(
        onDismiss = { showRules = false },
        onAcknowledge = { showRules = false; scope.launch { state.acknowledgeRules() } },
    )
    if (showFeedback) Dialog(onDismissRequest = { showFeedback = false }) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            CommunityFeedbackForm(
                token = token,
                onDismiss = { showFeedback = false },
                onSubmitted = { showFeedback = false; feedbackSent = true },
            )
        }
    }
    profileUser?.let { u -> ProfileSheet(username = u, state = state, onDismiss = { profileUser = null }, onMessage = { room, peer -> profileUser = null; page = Page.Dm(room, peer) }) }

    when (val p = page) {
        Page.EditProfile -> EditProfileScreen(state, onBack = { page = Page.Main }, onStickerMaker = { openStickerMaker(Page.EditProfile) }, onChangePassword = { showPasswordDialog = true })
        Page.StickerMaker -> StickerMakerScreen(state, onBack = { page = stickerMakerReturnPage })
        Page.Invites -> FriendInvitesScreen(state, onBack = { page = Page.Main })
        Page.Announcements -> CommunityAnnouncementsScreen(state, onBack = { page = Page.Main })
        is Page.Dm -> Column(Modifier.fillMaxSize()) {
            DmHeader(peer = p.peer, onBack = { page = Page.Main }, onProfile = { profileUser = p.peer.username })
            ChatRoom(room = p.room, state = state, onOpenProfile = { profileUser = it }, onStickerMaker = { openStickerMaker(Page.Dm(p.room, p.peer)) })
        }
        Page.Main -> Column(Modifier.fillMaxSize()) {
            val headerShape = RoundedCornerShape(28.dp)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .glassBorder(headerShape, LocalLiquidGlass.current),
                shape = headerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { sidebarExpanded = true }) {
                                Icon(painterResource(R.drawable.settings), contentDescription = "Buka sidebar")
                            }
                            DropdownMenu(expanded = sidebarExpanded, onDismissRequest = { sidebarExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Profil saya") },
                                    onClick = { sidebarExpanded = false; page = Page.EditProfile },
                                )
                                DropdownMenuItem(
                                    text = { Text("Undangan teman${if (pendingInviteCount > 0) " ($pendingInviteCount)" else ""}") },
                                    onClick = { sidebarExpanded = false; page = Page.Invites },
                                )
                                DropdownMenuItem(
                                    text = { Text("Pengumuman") },
                                    onClick = { sidebarExpanded = false; page = Page.Announcements },
                                )
                                DropdownMenuItem(
                                    text = { Text("Aturan komunitas") },
                                    onClick = { sidebarExpanded = false; showRules = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Keluar") },
                                    onClick = { sidebarExpanded = false; scope.launch { state.logout() } },
                                )
                            }
                        }
                        UserAvatar(me.avatarUrl, me.displayName, 44.dp, Modifier.clickable { page = Page.EditProfile }, fallbackUrls = me.avatarUrls)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.community_header_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("@${me.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        TextButton(onClick = { page = Page.Invites }) {
                            Text(if (pendingInviteCount > 0) "Undangan $pendingInviteCount" else "Undangan")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                        TextButton(onClick = { page = Page.Announcements }) { Text(stringResource(R.string.community_announcement_nav)) }
                        TextButton(onClick = { feedbackSent = false; showFeedback = true }) { Text(stringResource(R.string.community_feedback_button)) }
                        TextButton(onClick = { showRules = true }) { Text("Aturan") }
                    }
                }
            }
            if (feedbackSent) {
                Text(stringResource(R.string.community_feedback_submitted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(stringResource(R.string.community_status_tab), "Global", "Pesan", "Teman", "Dengar bersama", "Riwayat Masukan").forEachIndexed { i, t ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t) })
                }
            }
            Spacer(Modifier.height(6.dp))
            when (tab) {
                0 -> CommunityStatusScreen(state)
                1 -> ChatRoom(room = "global", state = state, onOpenProfile = { profileUser = it }, onStickerMaker = { openStickerMaker(Page.Main) }, showRulesOnFirstOpen = true)
                2 -> DmList(state, onOpen = { room, peer -> page = Page.Dm(room, peer) })
                3 -> FriendList(
                    state,
                    onOpenProfile = { profileUser = it },
                    onOpenChat = { peer -> scope.launch { val r = CommunityApi.dmRoom(token, peer.username); if (r.ok) page = Page.Dm(r.json.optString("room"), peer) } },
                    onInvite = { peer -> scope.launch { val r = CommunityApi.createInvite(token, peer.username); android.widget.Toast.makeText(context, if (r.ok) "Undangan dikirim ke @${peer.username}" else r.error.ifBlank { "Undangan gagal dikirim" }, android.widget.Toast.LENGTH_SHORT).show() } },
                )
                4 -> ListenPanel(state, listenRoomSession, onOpenListenTogether)
                else -> CommunityFeedbackHistory(token)
            }
        }
    }
}

@Composable
private fun FriendList(state: CommunityState, onOpenProfile: (String) -> Unit, onOpenChat: (UserDto) -> Unit, onInvite: (UserDto) -> Unit) {
    val token = state.token ?: return
    val insets = LocalPlayerAwareWindowInsets.current
    var members by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { loading = true; val r = CommunityApi.communityMembers(token); loading = false; if (r.ok) { members = r.json.optJSONArray("users")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::parseUser) } }.orEmpty(); error = "" } else error = r.error } }
    LaunchedEffect(token) { refresh() }
    val filteredMembers = remember(members, query) {
        if (query.isBlank()) members else members.filter {
            it.username.contains(query.trim(), ignoreCase = true) ||
                it.displayName.contains(query.trim(), ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Teman komunitas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${members.size} akun terdaftar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = ::refresh) { Text("Muat ulang") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            label = { Text("Cari teman") },
            placeholder = { Text("Nama atau username") },
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(insets.only(WindowInsetsSides.Bottom)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filteredMembers, key = { it.username }) { user ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable { onOpenProfile(user.username) }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(user.avatarUrl, user.displayName, 44.dp, fallbackUrls = user.avatarUrls)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(user.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1); RoleBadge(user.role, Modifier.padding(start = 5.dp)); user.title?.let { TitleChip(it, Modifier.padding(start = 4.dp)) } }
                            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (user.username != state.me?.username) {
                            Column(horizontalAlignment = Alignment.End) {
                                TextButton(onClick = { onInvite(user) }) { Text("Undang") }
                                TextButton(onClick = { onOpenChat(user) }) { Text("Chat") }
                            }
                        } else Text("Kamu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!loading && filteredMembers.isEmpty() && error.isBlank()) item {
                Text(
                    if (members.isEmpty()) "Belum ada akun terdaftar." else "Tidak ada teman yang cocok.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DmHeader(peer: UserDto, onBack: () -> Unit, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("‹ Kembali") }
        Row(Modifier.weight(1f).clickable(onClick = onProfile), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(peer.avatarUrl, peer.displayName, 36.dp, fallbackUrls = peer.avatarUrls)
                Column(Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(peer.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    RoleBadge(peer.role, Modifier.padding(start = 5.dp))
                    peer.title?.let { TitleChip(it, Modifier.padding(start = 5.dp)) }
                }
                Text("@${peer.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RulesDialog(onDismiss: () -> Unit, onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rules of Global Chat") },
        text = {
            Column {
                Text("Komunitas resmi Sanzxcy", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "Global Chat untuk bersosialisasi. Bukan tempat jualan; promo dan harga tetap dilarang di sini.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RulesSection("Diperbolehkan (Global Chat)", RULES_ALLOWED)
                RulesSection(
                    "Jualan & transaksi",
                    listOf("Jualan, promo, sebut harga, nomor WA, link, atau ajakan pindah chat dilarang di Global Chat."),
                )
                RulesSection("Dilarang di Global Chat", RULES_FORBIDDEN)
                RulesSection(
                    "Moderasi sistem",
                    listOf(
                        "Pesan yang melanggar dihapus dan diganti notice dari Moderation Bot.",
                        "Tiap pelanggaran = 1 warn. Total 3 warn = mute Global Chat 7 hari.",
                    ),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Pelanggaran berulang dari perangkat yang sama dapat berujung blokir IP. Password bisa direset melalui email terverifikasi; akun tanpa email harus meminta reset kepada staf/admin/owner.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAcknowledge) { Text("Saya paham") } },
    )
}

@Composable
private fun RulesSection(title: String, items: List<String>) {
    Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    items.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 3.dp)) }
}

@Composable
private fun ChangePasswordDialog(state: CommunityState, forced: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var showOld by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!forced) onDismiss() },
        title = { Text(if (forced) "Ganti password sementara" else "Ganti password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (forced) Text("Password akunmu direset oleh staf/admin/owner atau akun dibuat dari panel. Buat password baru sekarang.", style = MaterialTheme.typography.bodySmall)
                else PasswordInput(old, { old = it.take(64) }, "Password lama", showOld, { showOld = !showOld })
                PasswordInput(new, { new = it.take(64) }, "Password baru (min. 6)", showNew, { showNew = !showNew })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = new.length >= 6, onClick = {
                scope.launch {
                    val r = state.changePassword(old, new)
                    if (r.ok) onDismiss() else error = r.error
                }
            }) { Text("Simpan") }
        },
        dismissButton = { if (!forced) TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ───────────────────────── Chat ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatRoom(
    room: String,
    state: CommunityState,
    onOpenProfile: (String) -> Unit,
    onStickerMaker: () -> Unit,
    showRulesOnFirstOpen: Boolean = false,
) {
    val token = state.token ?: return
    val me = state.me ?: return
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val messages = remember(room) { mutableStateListOf<ChatMessage>() }
    var pinnedMessage by remember(room) { mutableStateOf<ChatMessage?>(null) }
    var mutedUntil by remember { mutableLongStateOf(0L) }
    var cooldownUntil by remember { mutableLongStateOf(0L) }
    var cooldownNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var cooldownJustFinished by remember { mutableStateOf(false) }
    var input by remember(room) { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var dialogMsg by remember { mutableStateOf<String?>(null) }
    var showStickers by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf<List<String>>(emptyList()) }
    var favoriteStickers by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFavoriteStickers by remember { mutableStateOf(false) }
    var replyTo by remember(room) { mutableStateOf<ReplySnapshot?>(null) }
    var showRules by rememberSaveable(room) { mutableStateOf(false) }
    var showPollComposer by remember(room) { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    val appContext = LocalContext.current
    val isStaff = me.role in listOf("staff", "admin", "owner")
    val insets = LocalPlayerAwareWindowInsets.current

    LaunchedEffect(room, showRulesOnFirstOpen, state.rulesAcknowledged) {
        if (showRulesOnFirstOpen && !state.rulesAcknowledged) showRules = true
    }
    if (showRules) RulesDialog(
        onDismiss = { showRules = false },
        onAcknowledge = { showRules = false; scope.launch { state.acknowledgeRules() } },
    )
    dialogMsg?.let { AlertDialog(onDismissRequest = { dialogMsg = null }, title = { Text("Pesan tidak terkirim") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { dialogMsg = null }) { Text("OK") } }) }
    if (showPollComposer && room == "global" && me.role == "owner") {
        PollComposer(token, onDismiss = { showPollComposer = false }, onCreated = { message ->
            if (messages.none { it.id == message.id }) messages.add(message)
            showPollComposer = false
            notice = "Polling berhasil dibuat."
        })
    }
    LaunchedEffect(token) {
        val result = CommunityApi.stickers(token)
        if (result.ok) favoriteStickers = result.json.optJSONArray("favorites")?.let { a -> (0 until a.length()).map { i -> a.getString(i) } }.orEmpty()
    }

    LaunchedEffect(room, token) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var tick = 0
            while (true) {
                val lastId = messages.lastOrNull()?.id ?: 0L
                val full = tick % 8 == 0
                val r = CommunityApi.poll(token, room, if (full) 0L else lastId)
                if (r.ok) {
                    val list = CommunityApi.parseMessages(r.json.optJSONArray("messages"))
                    pinnedMessage = r.json.optJSONObject("pinned")?.optJSONObject("message")?.let(::parseMessage)
                    if (full) {
                        if (list.map { it.id } != messages.map { it.id }) { messages.clear(); messages.addAll(list) }
                    } else if (list.isNotEmpty()) {
                        list.filter { n -> messages.none { it.id == n.id } }.forEach { messages.add(it) }
                    }
                    mutedUntil = r.json.optLong("mutedUntil")
                    if (r.json.has("cooldownUntil") && !r.json.isNull("cooldownUntil")) {
                        cooldownUntil = maxOf(
                            normalizeEpochMillis(r.json.optLong("cooldownUntil")),
                            normalizeEpochMillis(mutedUntil),
                        )
                    }
                } else state.handleAuthFailure(r)
                tick++
                delay(2000)
            }
        }
    }
    LaunchedEffect(room) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            messages.removeAll { it.type == "notice" && it.expiresAt <= now }
        }
    }
    LaunchedEffect(cooldownUntil) {
        cooldownJustFinished = false
        if (cooldownUntil <= 0L) return@LaunchedEffect
        while (System.currentTimeMillis() < cooldownUntil) {
            cooldownNow = System.currentTimeMillis()
            delay(1000)
        }
        cooldownNow = System.currentTimeMillis()
        cooldownJustFinished = true
        delay(1500)
        cooldownJustFinished = false
    }
    LaunchedEffect(notice) { if (notice != null) { delay(3500); notice = null } }

    fun toggleFavorite(url: String) {
        val wasFavorite = url in favoriteStickers
        scope.launch {
            val r = if (wasFavorite) CommunityApi.unfavoriteSticker(token, url) else CommunityApi.favoriteSticker(token, url)
            if (r.ok) {
                favoriteStickers = if (wasFavorite) favoriteStickers.filterNot { it == url } else (favoriteStickers + url).distinct()
            } else notice = r.error
        }
    }

    fun send(text: String?, sticker: String?) {
        val sentReplyId = replyTo?.id
        scope.launch {
            val r = CommunityApi.send(token, room, text, sticker, sentReplyId)
            if (r.ok) {
                val m = r.json.optJSONObject("message")?.let(::parseMessage)
                if (r.json.optBoolean("cleared")) { messages.clear(); pinnedMessage = null }
                else if (m != null && messages.none { it.id == m.id }) messages.add(m)
                if (text != null) input = ""
                if (replyTo?.id == sentReplyId) replyTo = null
                showStickers = false
                cooldownUntil = normalizeEpochMillis(r.json.optLong("cooldownUntil").takeIf { it > 0L } ?: (System.currentTimeMillis() + 40_000L))
            } else {
                when (r.code) {
                    "moderated" -> {
                        if (input == text) input = ""
                        cooldownUntil = maxOf(
                            normalizeEpochMillis(r.json.optLong("cooldownUntil").takeIf { it > 0L } ?: (System.currentTimeMillis() + 40_000L)),
                            normalizeEpochMillis(r.json.optLong("mutedUntil")),
                        )
                    }
                    "muted" -> {
                        mutedUntil = normalizeEpochMillis(r.json.optLong("mutedUntil"))
                        cooldownUntil = maxOf(
                            normalizeEpochMillis(r.json.optLong("cooldownUntil").takeIf { it > 0L } ?: mutedUntil),
                            mutedUntil,
                        )
                        if (input == text) input = ""
                    }
                    "banned" -> dialogMsg = r.error
                    "cooldown", "rate_limited" -> {
                        cooldownUntil = normalizeEpochMillis(r.json.optLong("cooldownUntil").takeIf { it > 0L } ?: (System.currentTimeMillis() + 40_000L))
                    }
                    else -> notice = r.error
                }
                state.handleAuthFailure(r)
            }
        }
    }

    val muted = mutedUntil > System.currentTimeMillis()
    val cooldownRemaining = (cooldownUntil - cooldownNow).coerceAtLeast(0L)
    val coolingDown = cooldownRemaining > 0L
    val cooldownSecondsLeft = (cooldownRemaining + 999L) / 1000L
    val cooldownLabel = "${cooldownSecondsLeft / 60}:${(cooldownSecondsLeft % 60).toString().padStart(2, '0')}"

    Column(Modifier.fillMaxSize()) {
        if (room == "global" && me.role == "owner") {
            TextButton(onClick = { showPollComposer = true }, modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)) { Text("＋ Buat polling") }
        }
        pinnedMessage?.let { pinned ->
            val pinShape = RoundedCornerShape(18.dp)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                shape = pinShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glassBorder(pinShape, LocalLiquidGlass.current),
            ) {
                Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = painterResource(R.drawable.ic_push_pin), contentDescription = "Pesan disematkan", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Disematkan · ${pinned.displayName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(pinned.text.ifBlank { "Stiker" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (isStaff) {
                        TextButton(onClick = {
                            scope.launch {
                                val r = CommunityApi.unpinMessage(token, room)
                                if (r.ok) pinnedMessage = null else notice = r.error
                            }
                        }) { Text("Lepas") }
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val reversed = messages.asReversed()
            items(reversed, key = { it.id }) { m ->
                val idx = reversed.indexOf(m)
                val older = reversed.getOrNull(idx + 1)
                val mine = m.from == me.username
                val showSender = older == null || older.from != m.from || older.type == "notice"
                Box(Modifier.animateItem()) {
                    MessageRow(
                        m = m,
                        mine = mine,
                        showSender = showSender,
                        isGlobal = room == "global",
                        username = me.username,
                        onAvatarClick = { onOpenProfile(m.from) },
                        onLongPress = { menuFor = m.id },
                        onPollVote = { id, option ->
                            scope.launch {
                                val result = CommunityApi.votePoll(token, id, option)
                                val updated = result.json.optJSONObject("message")?.let(::parseMessage)
                                if (result.ok && updated != null) {
                                    val index = messages.indexOfFirst { it.id == id }
                                    if (index >= 0) messages[index] = updated
                                } else notice = result.error.ifBlank { "Suara tidak dapat disimpan." }
                            }
                        },
                        onPollClose = { id ->
                            scope.launch {
                                val result = CommunityApi.closePoll(token, id)
                                val updated = result.json.optJSONObject("message")?.let(::parseMessage)
                                if (result.ok && updated != null) {
                                    val index = messages.indexOfFirst { it.id == id }
                                    if (index >= 0) messages[index] = updated
                                } else notice = result.error.ifBlank { "Poll tidak dapat ditutup." }
                            }
                        },
                    )
                    DropdownMenu(expanded = menuFor == m.id, onDismissRequest = { menuFor = null }) {
                        if (m.type != "notice") DropdownMenuItem(
                            text = { Text("Balas") },
                            onClick = {
                                replyTo = ReplySnapshot(m.id, m.from, m.displayName, m.type, m.text, m.stickerUrl)
                                menuFor = null
                            },
                        )
                        if (m.type == "text") DropdownMenuItem(text = { Text("Salin") }, onClick = { (appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("pesan", m.text)); menuFor = null })
                        if (m.type != "notice" && !mine) DropdownMenuItem(text = { Text("Lihat profil") }, onClick = { onOpenProfile(m.from); menuFor = null })
                        if (m.type == "sticker" && !mine) DropdownMenuItem(
                            text = { Text(if (m.stickerUrl in favoriteStickers) "Hapus dari favorit" else "Simpan stiker favorit") },
                            onClick = { menuFor = null; toggleFavorite(m.stickerUrl) },
                        )
                        if (room == "global" && m.type != "notice" && isStaff) DropdownMenuItem(
                            text = { Text(if (pinnedMessage?.id == m.id) "Lepas sematan" else "Sematkan") },
                            onClick = {
                                menuFor = null
                                scope.launch {
                                    val r = if (pinnedMessage?.id == m.id) CommunityApi.unpinMessage(token, room) else CommunityApi.pinMessage(token, room, m.id)
                                    if (r.ok) pinnedMessage = r.json.optJSONObject("pinned")?.optJSONObject("message")?.let(::parseMessage) else notice = r.error
                                }
                            },
                        )
                        if (m.type != "notice" && (mine || isStaff)) DropdownMenuItem(text = { Text("Hapus") }, onClick = {
                            menuFor = null
                            scope.launch {
                                val r = CommunityApi.deleteMessage(token, room, m.id)
                                if (r.ok) {
                                    messages.removeAll { it.id == m.id }
                                    if (pinnedMessage?.id == m.id) pinnedMessage = null
                                } else notice = r.error
                            }
                        })
                    }
                }
            }
        }

        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        AnimatedVisibility(
            visible = showStickers,
            enter = slideInVertically(spring(Spring.DampingRatioLowBouncy)) { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            LaunchedEffect(Unit) {
                CommunityApi.stickers(token).let { result ->
                    if (result.ok) {
                        stickers = result.json.optJSONArray("stickers")?.let { a -> (0 until a.length()).map { i -> a.getString(i) } }.orEmpty()
                        favoriteStickers = result.json.optJSONArray("favorites")?.let { a -> (0 until a.length()).map { i -> a.getString(i) } }.orEmpty()
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.fillMaxWidth().height(240.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !showFavoriteStickers, onClick = { showFavoriteStickers = false }, label = { Text("Stiker saya") })
                        FilterChip(selected = showFavoriteStickers, onClick = { showFavoriteStickers = true }, label = { Text("Favorit (${favoriteStickers.size}/60)") })
                    }
                    LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!showFavoriteStickers) item {
                            Box(Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer).clickable(onClick = onStickerMaker), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        gridItems(if (showFavoriteStickers) favoriteStickers else stickers) { url ->
                            Box(Modifier.size(76.dp)) {
                                AsyncImage(model = url, contentDescription = "Stiker", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable(enabled = !muted && !coolingDown) { send(null, url) })
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(27.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).clickable { toggleFavorite(url) },
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (url in favoriteStickers) "★" else "☆", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp) }
                            }
                        }
                    }
                }
            }
        }

        if (muted) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text("Kamu di-mute sampai ${formatDate(mutedUntil)} ${formatClock(mutedUntil)}.", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
            }
        }
        replyTo?.let { selected ->
            ReplySnapshotView(
                reply = selected,
                onCancel = { replyTo = null },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (coolingDown || cooldownJustFinished) {
            Text(
                if (coolingDown) "Jeda kirim pesan · $cooldownLabel" else "Cooldown selesai · 0:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(insets.only(WindowInsetsSides.Bottom)).imePadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = !muted && !coolingDown) { showStickers = !showStickers },
                contentAlignment = Alignment.Center,
            ) { Text("☺", fontSize = 22.sp) }
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(500) },
                enabled = !muted && !coolingDown,
                placeholder = { Text(if (room == "global") "Tulis pesan ke semua orang…" else "Tulis pesan…") },
                shape = RoundedCornerShape(26.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(if (input.isNotBlank() && !muted && !coolingDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(enabled = input.isNotBlank() && !muted && !coolingDown) { send(input.trim(), null) },
                contentAlignment = Alignment.Center,
            ) { Text("➤", fontSize = 18.sp, color = if (input.isNotBlank() && !muted && !coolingDown) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ReplySnapshotView(
    reply: ReplySnapshot,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.widthIn(max = 280.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
                Text(reply.displayName.ifBlank { reply.from }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Text(
                    if (reply.type == "sticker") "Stiker" else reply.text.ifBlank { "Pesan" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (reply.type == "sticker" && reply.stickerUrl.isNotBlank()) {
                AsyncImage(model = reply.stickerUrl, contentDescription = "Stiker yang dibalas", contentScale = ContentScale.Crop, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)))
            }
            onCancel?.let {
                Box(Modifier.padding(start = 4.dp).size(26.dp).clip(CircleShape).clickable(onClick = it), contentAlignment = Alignment.Center) {
                    Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: ChatMessage,
    mine: Boolean,
    showSender: Boolean,
    isGlobal: Boolean,
    username: String,
    onAvatarClick: () -> Unit,
    onLongPress: () -> Unit,
    onPollVote: (Long, Int) -> Unit,
    onPollClose: (Long) -> Unit,
) {
    if (m.type == "notice") {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = Modifier.glassBorder(RoundedCornerShape(14.dp), false)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(m.displayName.ifBlank { "Sanzxcy Bot" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Text(m.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        return
    }
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    val mineColor = if (dark) Color(0xFF005C4B) else Color(0xFFD9FDD3)
    val otherColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleShape =
        if (mine) RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp)
        else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 5.dp, bottomEnd = 20.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine && isGlobal) {
            if (showSender) UserAvatar(m.avatarUrl, m.displayName, 32.dp, Modifier.clickable(onClick = onAvatarClick), fallbackUrls = m.avatarUrls)
            else Spacer(Modifier.size(32.dp))
            Spacer(Modifier.size(6.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 340.dp)) {
            if (showSender) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
                    Text(m.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    RoleBadge(m.role, Modifier.padding(start = 5.dp))
                    m.title?.let { TitleChip(it, Modifier.padding(start = 5.dp)) }
                }
            }
            if (m.type == "poll" && m.poll != null) {
                Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    PollMessageCard(
                        poll = m.poll,
                        username = username,
                        canClose = mine,
                        onVote = { option -> onPollVote(m.id, option) },
                        onClose = { onPollClose(m.id) },
                    )
                    Text(formatClock(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                }
            } else if (m.type == "sticker") {
                Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    m.reply?.let { ReplySnapshotView(it, Modifier.padding(bottom = 4.dp)) }
                    AsyncImage(
                        model = m.stickerUrl, contentDescription = "Stiker", contentScale = ContentScale.Fit,
                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(24.dp)).combinedClickable(onClick = {}, onLongClick = onLongPress),
                    )
                    Text(formatClock(m.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                }
            } else {
                Surface(
                    color = if (mine) mineColor else otherColor,
                    shape = bubbleShape,
                    modifier = Modifier.clip(bubbleShape).combinedClickable(onClick = {}, onLongClick = onLongPress),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                        m.reply?.let { ReplySnapshotView(it, Modifier.padding(bottom = 4.dp)) }
                        Row(Modifier.padding(start = 6.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                            Text(m.text, color = if (mine && !dark) Color(0xFF0B1A14) else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                            Text(
                                formatClock(m.ts), fontSize = 10.sp,
                                color = (if (mine && !dark) Color(0xFF0B1A14) else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

// ───────────────────────── Pesan pribadi ─────────────────────────

@Composable
private fun DmList(state: CommunityState, onOpen: (String, UserDto) -> Unit) {
    val token = state.token ?: return
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var dmItems by remember { mutableStateOf<List<DmItem>>(emptyList()) }
    var showNew by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val r = CommunityApi.dms(token)
                if (r.ok) {
                    val a = r.json.optJSONArray("dms")
                    dmItems = (0 until (a?.length() ?: 0)).mapNotNull { i ->
                        val o = a!!.optJSONObject(i) ?: return@mapNotNull null
                        DmItem(o.optString("room"), parseUser(o.getJSONObject("peer")), o.optJSONObject("last")?.let(::parseMessage))
                    }
                }
                delay(4000)
            }
        }
    }
    LaunchedEffect(query) {
        if (query.length >= 2) { delay(300); val r = CommunityApi.searchUsers(token, query); if (r.ok) { val a = r.json.optJSONArray("users"); results = (0 until (a?.length() ?: 0)).map { parseUser(a!!.getJSONObject(it)) } } } else results = emptyList()
    }

    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("Pesan baru") },
            text = {
                Column {
                    OutlinedTextField(query, { query = it.lowercase().trim() }, label = { Text("Cari username") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    results.forEach { u ->
                        Row(Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                val r = CommunityApi.dmRoom(token, u.username)
                                if (r.ok) { showNew = false; onOpen(r.json.getString("room"), u) } else error = r.error
                            }
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(u.avatarUrl, u.displayName, 36.dp, fallbackUrls = u.avatarUrls)
                            Column(Modifier.padding(start = 10.dp)) { Text(u.displayName, fontWeight = FontWeight.SemiBold); Text("@${u.username}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNew = false }) { Text("Tutup") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Button(onClick = { showNew = true; query = ""; results = emptyList(); error = null }, modifier = Modifier.padding(16.dp).fillMaxWidth().height(48.dp)) { Text("Pesan baru") }
        if (dmItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada pesan pribadi.\nKetuk Pesan baru untuk mulai ngobrol.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(dmItems, key = { it.room }) { d ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(d.room, d.peer) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(d.peer.avatarUrl, d.peer.displayName, 48.dp, fallbackUrls = d.peer.avatarUrls)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.peer.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            RoleBadge(d.peer.role, Modifier.padding(start = 6.dp))
                        }
                        Text(
                            d.last?.let { if (it.type == "sticker") "Stiker" else it.text }.orEmpty(),
                            maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    d.last?.let { Text(formatClock(it.ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
