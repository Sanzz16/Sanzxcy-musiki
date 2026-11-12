/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: Dengar Bersama. Host membagikan kode/link, tamu otomatis memutar lagu yang sama
 * dan disinkronkan posisi serta status play/pause-nya (polling ringan tiap 2 detik).
 */

package com.metrolist.music.community

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.listentogether.ConnectionState
import com.metrolist.music.listentogether.ListenTogetherEvent
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.theme.LocalLiquidGlass
import com.sanzxcy.music.com.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Kode ruangan dari link/deep link (sanzxcy://join?code=XXXXXX) menunggu diproses ListenPanel. */
object CommunityDeepLink {
    val pendingCode = MutableStateFlow<String?>(null)
}

private val YT_ID = Regex("^[A-Za-z0-9_-]{11}$")
private val JOIN_BASE: String get() = CommunityApi.baseUrl + "/join/"

class ListenRoomSession {
    val code = mutableStateOf<String?>(null)
    val isHost = mutableStateOf(false)
    val isPublic = mutableStateOf(false)
    val members = mutableIntStateOf(1)
    val memberList = mutableStateOf<List<ListenRoomMember>>(emptyList())
    val hostName = mutableStateOf("")
    val remote = mutableStateOf<ListenState?>(null)
    val joinInput = mutableStateOf("")
    val roomName = mutableStateOf("")
    val bannerUrl = mutableStateOf("")
    val wallpaperUrl = mutableStateOf("")
    val pendingEntryCode = mutableStateOf<String?>(null)
    val pendingSongRequestId = mutableStateOf<String?>(null)
}

/** Keep active REST-room controls alive while top-level navigation replaces CommunityRoot. */
object CommunityListenRoomSessions {
    private val sessions = mutableMapOf<String, ListenRoomSession>()

    @Synchronized
    fun forUser(username: String): ListenRoomSession =
        sessions.getOrPut(username.trim().lowercase()) { ListenRoomSession() }

    @Synchronized
    fun clear(username: String?) {
        if (!username.isNullOrBlank()) {
            CommunityListenRoomSync.stop(username.trim().lowercase())
            sessions.remove(username.trim().lowercase())
        }
    }
}

/** REST room playback sync belongs to the active room, not to one navigation destination. */
private object CommunityListenRoomSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var activeKey: String? = null
    private var activeUsername: String? = null
    private var activeSession: ListenRoomSession? = null
    private var activePlayer: com.metrolist.music.playback.PlayerConnection? = null

    @Synchronized
    fun start(token: String, username: String, session: ListenRoomSession, player: com.metrolist.music.playback.PlayerConnection?) {
        val code = session.code.value?.takeIf { it.isNotBlank() }
        if (code == null) {
            stop(username.trim().lowercase())
            return
        }
        val normalizedUsername = username.trim().lowercase()
        val key = "$normalizedUsername|$code|$token"
        if (activeKey == key && activeSession === session && activePlayer === player && job?.isActive == true) return
        job?.cancel()
        activeKey = key
        activeUsername = normalizedUsername
        activeSession = session
        activePlayer = player
        job = scope.launch {
            var lastRequested = ""
            while (isActive && session.code.value == code) {
                if (session.isHost.value) {
                    val metadata = player?.mediaMetadata?.value
                    if (metadata != null && YT_ID.matches(metadata.id)) {
                        val current = ListenState(
                            videoId = metadata.id,
                            title = metadata.title,
                            artist = metadata.artists.joinToString(", ") { it.name },
                            thumb = metadata.thumbnailUrl.orEmpty(),
                            positionMs = player.player.currentPosition.coerceAtLeast(0),
                            durationMs = player.player.duration.coerceAtLeast(0),
                            playing = player.player.playWhenReady,
                            serverTs = 0,
                        )
                        val result = CommunityApi.listenPush(token, code, current)
                        if (result.ok) {
                            session.members.intValue = result.json.optInt("members", session.members.intValue)
                        } else if (result.httpStatus == 404) {
                            session.code.value = null
                            session.isHost.value = false
                            session.isPublic.value = false
                            session.members.intValue = 0
                            session.hostName.value = ""
                            session.roomName.value = ""
                            session.bannerUrl.value = ""
                            session.wallpaperUrl.value = ""
                            session.memberList.value = emptyList()
                            session.remote.value = null
                            break
                        }
                        session.remote.value = current
                    } else {
                        val heartbeat = CommunityApi.listenGet(token, code)
                        if (heartbeat.ok) {
                            session.memberList.value = parseRoomMembers(heartbeat.json.optJSONArray("memberList"))
                            session.members.intValue = heartbeat.json.optInt("members", session.members.intValue)
                            session.roomName.value = heartbeat.json.optString("roomName")
                            session.bannerUrl.value = heartbeat.json.optString("bannerUrl")
                            session.wallpaperUrl.value = heartbeat.json.optString("wallpaperUrl")
                            session.isPublic.value = heartbeat.json.optString("visibility") == "public"
                        } else if (heartbeat.httpStatus == 404 || heartbeat.error.contains("ditutup", ignoreCase = true)) {
                            session.code.value = null
                            session.isHost.value = false
                            session.isPublic.value = false
                            session.members.intValue = 0
                            session.hostName.value = ""
                            session.roomName.value = ""
                            session.bannerUrl.value = ""
                            session.wallpaperUrl.value = ""
                            session.memberList.value = emptyList()
                            session.remote.value = null
                            break
                        }
                    }
                } else {
                    val result = CommunityApi.listenGet(token, code)
                    if (!result.ok) {
                        if (result.httpStatus == 404 || result.code == "bad_response" || result.error.contains("ditutup", ignoreCase = true)) {
                            session.code.value = null
                            session.isHost.value = false
                            session.isPublic.value = false
                            session.members.intValue = 0
                            session.hostName.value = ""
                            session.roomName.value = ""
                            session.bannerUrl.value = ""
                            session.wallpaperUrl.value = ""
                            session.remote.value = null
                            session.memberList.value = emptyList()
                            break
                        }
                    } else {
                        val remote = parseListenState(result.json.optJSONObject("state"))
                        val serverNow = result.json.optLong("serverTs")
                        session.members.intValue = result.json.optInt("members", session.members.intValue)
                        session.memberList.value = parseRoomMembers(result.json.optJSONArray("memberList"))
                        session.isHost.value = result.json.optString("host") == normalizedUsername
                        session.hostName.value = result.json.optString("hostName")
                        session.roomName.value = result.json.optString("roomName")
                        session.bannerUrl.value = result.json.optString("bannerUrl")
                        session.wallpaperUrl.value = result.json.optString("wallpaperUrl")
                        session.isPublic.value = result.json.optString("visibility") == "public"
                        session.remote.value = remote
                        if (remote != null && remote.videoId.isNotBlank() && player != null) {
                            if (player.mediaMetadata.value?.id != remote.videoId) {
                                if (lastRequested != remote.videoId) {
                                    lastRequested = remote.videoId
                                    player.playQueue(YouTubeQueue(WatchEndpoint(videoId = remote.videoId), null))
                                }
                            } else {
                                val expected = remote.positionMs + if (remote.playing) (serverNow - remote.serverTs).coerceAtLeast(0) else 0L
                                if (abs(player.player.currentPosition - expected) > 3500) player.player.seekTo(expected)
                                if (player.player.playWhenReady != remote.playing) player.player.playWhenReady = remote.playing
                            }
                        }
                    }
                }
                delay(if (session.isHost.value) 2500 else 2000)
            }
        }
    }

    @Synchronized
    fun stop(username: String? = null) {
        if (username != null && activeUsername != username) return
        job?.cancel()
        job = null
        activeKey = null
        activeUsername = null
        activeSession = null
        activePlayer = null
    }
}

@Composable
fun ListenRoomSyncEffect(state: CommunityState, roomSession: ListenRoomSession) {
    val token = state.token
    val username = state.me?.username.orEmpty()
    val playerConnection = LocalPlayerConnection.current
    val code = roomSession.code.value
    LaunchedEffect(username, token, code, playerConnection) {
        if (token.isNullOrBlank()) CommunityListenRoomSync.stop(username.ifBlank { null })
        else CommunityListenRoomSync.start(token, username, roomSession, playerConnection)
    }
}

@Composable
fun ListenPanel(state: CommunityState, roomSession: ListenRoomSession, onOpenListenTogether: () -> Unit = {}) {
    val token = state.token ?: return
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val insets = LocalPlayerAwareWindowInsets.current

    var code by roomSession.code
    var isHost by roomSession.isHost
    var isPublicRoom by roomSession.isPublic
    var members by roomSession.members
    var hostName by roomSession.hostName
    var remote by roomSession.remote
    var joinInput by roomSession.joinInput
    var roomName by roomSession.roomName
    var bannerUrl by roomSession.bannerUrl
    var wallpaperUrl by roomSession.wallpaperUrl
    var pendingEntryCode by roomSession.pendingEntryCode
    var pendingSongRequestId by roomSession.pendingSongRequestId
    var roomServer by rememberSaveable { mutableStateOf("server2") }
    var transferTarget by remember { mutableStateOf<ListenRoomMember?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showSongSearch by remember { mutableStateOf(false) }
    var songQuery by rememberSaveable(code) { mutableStateOf("") }
    var songResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searchingSongs by remember { mutableStateOf(false) }
    var songSearchError by remember { mutableStateOf<String?>(null) }

    fun enterJoinedRoom(c: String, json: JSONObject) {
        code = c.uppercase().trim(); isHost = json.optString("host") == state.me?.username
        members = json.optInt("members", 1); hostName = json.optString("hostName")
        roomName = json.optString("roomName"); bannerUrl = json.optString("bannerUrl"); wallpaperUrl = json.optString("wallpaperUrl")
        isPublicRoom = json.optString("visibility") == "public"
        remote = parseListenState(json.optJSONObject("state"))
        roomSession.memberList.value = parseRoomMembers(json.optJSONArray("memberList"))
        pendingEntryCode = null
        notice = "Berhasil bergabung ke ruang dengar."
    }

    fun joinRoom(c: String) {
        scope.launch {
            busy = true; error = null
            val r = CommunityApi.listenJoin(token, c.uppercase().trim())
            busy = false
            if (r.ok) {
                when (r.json.optString("status", "joined")) {
                    "pending" -> { pendingEntryCode = c.uppercase().trim(); notice = "Permintaan masuk dikirim. Tunggu persetujuan owner room." }
                    "rejected" -> { pendingEntryCode = null; error = r.json.optJSONObject("rejection")?.optString("message").orEmpty().ifBlank { "Permintaan masuk ditolak oleh owner room." } }
                    else -> enterJoinedRoom(c, r.json)
                }
            } else error = r.error
        }
    }

    LaunchedEffect(pendingEntryCode, token) {
        val requestedCode = pendingEntryCode ?: return@LaunchedEffect
        while (true) {
            delay(4_000L)
            val result = CommunityApi.listenJoin(token, requestedCode)
            if (result.ok) when (result.json.optString("status", "joined")) {
                "joined" -> { enterJoinedRoom(requestedCode, result.json); break }
                "rejected" -> {
                    pendingEntryCode = null
                    error = result.json.optJSONObject("rejection")?.optString("message").orEmpty().ifBlank { "Permintaan masuk ditolak oleh owner room." }
                    notice = null
                    android.widget.Toast.makeText(context, "Permintaan masuk ditolak", android.widget.Toast.LENGTH_SHORT).show()
                    break
                }
            }
        }
    }

    LaunchedEffect(pendingSongRequestId, code, token) {
        val requestId = pendingSongRequestId ?: return@LaunchedEffect
        val roomCode = code ?: return@LaunchedEffect
        while (true) {
            delay(5_000L)
            val result = CommunityApi.listenSongRequestStatus(token, roomCode, requestId)
            val status = result.json.optJSONObject("request")?.optString("status")
            if (result.ok && status != null && status != "pending") {
                pendingSongRequestId = null
                android.widget.Toast.makeText(context, if (status == "accepted") "Permintaan lagu diterima" else "Permintaan lagu ditolak", android.widget.Toast.LENGTH_SHORT).show()
                break
            }
        }
    }

    // Kode dari deep link
    val pending by CommunityDeepLink.pendingCode.collectAsState()
    LaunchedEffect(pending) {
        val p = pending
        if (!p.isNullOrBlank() && code == null) { CommunityDeepLink.pendingCode.value = null; joinInput = p; joinRoom(p) }
    }

    fun currentState(): ListenState? {
        val pc = playerConnection ?: return null
        val m = pc.mediaMetadata.value ?: return null
        if (!YT_ID.matches(m.id)) return null
        return ListenState(
            videoId = m.id, title = m.title, artist = m.artists.joinToString(", ") { it.name }, thumb = m.thumbnailUrl.orEmpty(),
            positionMs = pc.player.currentPosition.coerceAtLeast(0), durationMs = pc.player.duration.coerceAtLeast(0),
            playing = pc.player.playWhenReady, serverTs = 0,
        )
    }

    var roomMembers by remember(code) { mutableStateOf(roomSession.memberList.value) }
    var entryRequests by remember(code) { mutableStateOf<List<ListenEntryRequest>>(emptyList()) }
    var songRequests by remember(code) { mutableStateOf<List<ListenSongRequest>>(emptyList()) }
    var requestTrack by rememberSaveable(code) { mutableStateOf("") }
    var requestTitle by rememberSaveable(code) { mutableStateOf("") }
    var requestArtist by rememberSaveable(code) { mutableStateOf("") }
    LaunchedEffect(code, isHost, token) {
        val roomCode = code ?: return@LaunchedEffect
        while (true) {
            val memberResult = CommunityApi.listenMembers(token, roomCode)
            if (memberResult.ok) {
                roomMembers = parseRoomMembers(memberResult.json.optJSONArray("members"))
                roomSession.memberList.value = roomMembers
                members = memberResult.json.optInt("count", roomMembers.size)
            }
            if (isHost) {
                val admissions = CommunityApi.listenEntryRequests(token, roomCode)
                if (admissions.ok) entryRequests = parseEntryRequests(admissions.json.optJSONArray("requests"))
                val requests = CommunityApi.listenSongRequests(token, roomCode)
                if (requests.ok) songRequests = parseSongRequests(requests.json.optJSONArray("requests"))
            }
            delay(4_000L)
        }
    }

    transferTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text("Pindahkan kepemilikan?") },
            text = { Text("@${target.username} akan menjadi host dan memperoleh kontrol penuh atas ruangan ini.") },
            dismissButton = { TextButton(onClick = { transferTarget = null }) { Text("Batal") } },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val transfer = CommunityApi.listenTransfer(token, code.orEmpty(), target.username)
                        busy = false
                        transferTarget = null
                        if (transfer.ok) {
                            isHost = false
                            hostName = transfer.json.optString("hostName", target.displayName)
                            notice = "Kepemilikan ruangan dipindahkan ke @${target.username}."
                            android.widget.Toast.makeText(context, "Pemilik ruangan berhasil dipindahkan", android.widget.Toast.LENGTH_SHORT).show()
                        } else error = transfer.error
                    }
                }) { Text("Pindahkan") }
            },
        )
    }

    if (showSongSearch) {
        AlertDialog(
            onDismissRequest = { showSongSearch = false },
            title = { Text("Cari lagu untuk diminta") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = songQuery,
                        onValueChange = { songQuery = it.take(120) },
                        label = { Text("Judul lagu atau artis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        enabled = !searchingSongs && songQuery.trim().length >= 2,
                        onClick = {
                            scope.launch {
                                searchingSongs = true
                                songSearchError = null
                                val response = YouTube.search(songQuery.trim(), YouTube.SearchFilter.FILTER_SONG)
                                songResults = response.getOrNull()?.items?.filterIsInstance<SongItem>()
                                    ?.filter { YT_ID.matches(it.id) }
                                    .orEmpty()
                                if (songResults.isEmpty()) songSearchError = response.exceptionOrNull()?.message ?: "Lagu tidak ditemukan. Coba kata kunci lain."
                                searchingSongs = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(if (searchingSongs) "Mencari…" else "Cari") }
                    songSearchError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    if (searchingSongs) CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp))
                    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        songResults.forEach { song ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    requestTrack = song.id
                                    requestTitle = song.title
                                    requestArtist = song.artists.joinToString(", ") { it.name }
                                    showSongSearch = false
                                }.padding(vertical = 9.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(song.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(song.artists.joinToString(", ") { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showSongSearch = false }) { Text("Batal") } },
            confirmButton = {},
        )
    }

    fun shareText(c: String) =
        "Dengarkan musik bareng aku di Sanzxcy Music! Buka tab Komunitas → Dengar bersama dan masukkan kode: $c\nAtau buka link: $JOIN_BASE$c"

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(insets.only(WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PublicListenRoomsSection(
            token = token,
            username = state.me?.username.orEmpty(),
            roomSession = roomSession,
            selectedServer = roomServer,
            onServerSelected = { roomServer = it },
            onOpenListenTogether = onOpenListenTogether,
        )
        val c = code
        if (c == null) {
            if (!pendingEntryCode.isNullOrBlank()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Menunggu persetujuan owner", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Kode $pendingEntryCode · status diperiksa otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            if (roomServer == "server2") {
            GlassCard {
                Text("Buat ruangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.community_listen_room_create_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isPublicRoom) "Ruangan publik" else "Ruangan privat", fontWeight = FontWeight.SemiBold)
                        Text(if (isPublicRoom) "Tampil di daftar publik dan bisa dimasuki siapa saja." else "Tidak muncul di daftar publik; bergabung perlu kode dan izin akses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isPublicRoom, onCheckedChange = { isPublicRoom = it })
                }
                Button(
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = {
                        val s = currentState()
                        if (s == null) { error = resources.getString(R.string.community_listen_room_requires_track); return@Button }
                        scope.launch {
                            busy = true; error = null
                            val r = CommunityApi.listenCreate(token, s, if (isPublicRoom) "public" else "private")
                            busy = false
                            if (r.ok) {
                                code = r.json.getString("code"); isHost = true; members = 1; remote = s; roomName = ""; bannerUrl = ""; wallpaperUrl = ""; isPublicRoom = r.json.optString("visibility") == "public"
                                roomMembers = listOf(ListenRoomMember(state.me?.username.orEmpty(), state.me?.displayName.orEmpty(), state.me?.avatarUrl.orEmpty()))
                                roomSession.memberList.value = roomMembers
                            } else error = r.error
                        }
                    },
                ) { Text("Buat ruangan") }
                OutlinedButton(
                    onClick = onOpenListenTogether,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Buat room publik / privat") }
            }
            GlassCard {
                Text("Gabung ruangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = joinInput, onValueChange = { joinInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) },
                    label = { Text("Kode (6 karakter)") }, singleLine = true, shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(enabled = !busy && joinInput.length == 6, onClick = { joinRoom(joinInput) }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Gabung") }
            }
            } else {
                GlassCard {
                    Text("Server 1 · Stabil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Ruangan memakai layanan Listen Together. Banner dapat diatur lewat tautan HTTPS setelah room dibuat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenListenTogether, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Buat atau gabung di Server 1") }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth()) {
                if (wallpaperUrl.startsWith("https://", ignoreCase = true)) {
                    AsyncImage(
                        model = wallpaperUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().clip(RoundedCornerShape(26.dp)),
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier.matchParentSize(),
                    ) {}
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (bannerUrl.startsWith("https://", ignoreCase = true)) {
                        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(22.dp))) {
                            AsyncImage(model = bannerUrl, contentDescription = "Banner ruangan", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Surface(color = Color.Black.copy(alpha = 0.46f), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxSize()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                                    Text(roomName.ifBlank { "Ruang dengar bersama" }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("${if (isHost) "Host" else "Bersama"} · ${hostName.ifBlank { state.me?.displayName.orEmpty() }}", color = Color.White.copy(alpha = .9f), maxLines = 1)
                                }
                            }
                        }
                    }
            GlassCard {
                Text(roomName.ifBlank { if (isHost) "Kamu host ruangan ini" else "Terhubung ke ruangan ${hostName.ifBlank { "host" }}" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(c, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text("$members pendengar", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("kode", c))
                    }, modifier = Modifier.weight(1f).height(46.dp)) { Text("Salin kode") }
                    Button(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText(c)) }, "Bagikan ke"))
                    }, modifier = Modifier.weight(1f).height(46.dp)) { Text("Bagikan") }
                }
            }
            GlassCard {
                Text("Pendengar di ruangan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (roomMembers.isEmpty()) Text("Memuat daftar pendengar…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                roomMembers.forEach { member ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(member.avatarUrl, member.displayName, 38.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(member.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("@${member.username}${if (member.username == state.me?.username) " · Kamu" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (isHost && member.username != state.me?.username) {
                            TextButton(onClick = { transferTarget = member }, enabled = !busy) { Text("Jadikan host") }
                        }
                    }
                }
            }
            remote?.takeIf { it.videoId.isNotBlank() }?.let { s ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = s.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Sedang diputar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(s.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(s.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            if (isHost && currentState() == null) {
                Text(stringResource(R.string.community_listen_room_host_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (!isHost) {
                GlassCard {
                    Text("Minta lagu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Cari dan pilih lagu terlebih dahulu. Owner akan menyetujui sebelum lagu diputar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(enabled = pendingSongRequestId == null, onClick = { showSongSearch = true }, modifier = Modifier.fillMaxWidth()) { Text(if (requestTrack.isBlank()) "Cari lagu" else "Ganti lagu") }
                    if (requestTrack.isNotBlank()) {
                        Text("$requestTitle${if (requestArtist.isBlank()) "" else " · $requestArtist"}", fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                    Button(enabled = !busy && YT_ID.matches(requestTrack) && pendingSongRequestId == null, onClick = {
                        scope.launch {
                            busy = true
                            val requested = ListenState(requestTrack, requestTitle.ifBlank { "Lagu $requestTrack" }, requestArtist, "", 0, 0, false, 0)
                            val result = CommunityApi.listenSongRequest(token, c, requested)
                            busy = false
                            if (result.ok) {
                                pendingSongRequestId = result.json.optJSONObject("request")?.optString("id")?.takeIf { it.isNotBlank() }
                                notice = "Permintaan lagu dikirim ke owner."
                                requestTrack = ""; requestTitle = ""; requestArtist = ""
                            } else error = result.error
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (pendingSongRequestId != null) "Menunggu keputusan owner…" else "Kirim permintaan") }
                }
            }
            if (isHost && (entryRequests.isNotEmpty() || songRequests.isNotEmpty())) {
                GlassCard {
                    Text("Permintaan pendengar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    entryRequests.forEach { request ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(request.displayName, fontWeight = FontWeight.Medium)
                                Text("@${request.username} ingin bergabung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { scope.launch { val r = CommunityApi.listenEntryDecision(token, c, request.username, false); if (r.ok) { entryRequests = entryRequests.filterNot { it.username == request.username }; android.widget.Toast.makeText(context, "Permintaan dari @${request.username} ditolak", android.widget.Toast.LENGTH_SHORT).show() } else error = r.error } }) { Text("Tolak") }
                            Button(onClick = { scope.launch { val r = CommunityApi.listenEntryDecision(token, c, request.username, true); if (r.ok) { entryRequests = entryRequests.filterNot { it.username == request.username }; members = r.json.optInt("members", members); roomMembers = parseRoomMembers(r.json.optJSONArray("memberList")); roomSession.memberList.value = roomMembers } else error = r.error } }) { Text("Terima") }
                        }
                    }
                    songRequests.forEach { request ->
                        Column(Modifier.fillMaxWidth()) {
                            Text("${request.displayName} meminta lagu", fontWeight = FontWeight.Medium)
                            Text(request.title.ifBlank { request.videoId } + if (request.artist.isBlank()) "" else " · ${request.artist}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { scope.launch { val r = CommunityApi.listenSongDecision(token, c, request.id, false); if (r.ok) songRequests = songRequests.filterNot { it.id == request.id } else error = r.error } }, modifier = Modifier.weight(1f)) { Text("Tolak") }
                                Button(onClick = {
                                    scope.launch {
                                        val r = CommunityApi.listenSongDecision(token, c, request.id, true)
                                        if (r.ok) {
                                            songRequests = songRequests.filterNot { it.id == request.id }
                                            playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = request.videoId), null))
                                            notice = "Permintaan lagu diterima."
                                        } else error = r.error
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Terima & putar") }
                            }
                        }
                    }
                }
            }
            if (isHost) {
                var draftName by rememberSaveable(c) { mutableStateOf(roomName) }
                var draftBanner by rememberSaveable(c) { mutableStateOf(bannerUrl) }
                var draftWallpaper by rememberSaveable(c) { mutableStateOf(wallpaperUrl) }
                var uploadingImage by remember(c) { mutableStateOf<String?>(null) }
                var roomImageToCrop by remember(c) { mutableStateOf<Bitmap?>(null) }
                var roomImageCropTarget by remember(c) { mutableStateOf<String?>(null) }

                fun uploadRoomImage(bitmap: Bitmap, target: String) {
                    scope.launch {
                        busy = true; uploadingImage = target; error = null
                        try {
                            val bytes = withContext(Dispatchers.IO) { compressJpeg(bitmap, 2_800_000).also { bitmap.recycle() } }
                            val uploaded = CommunityApi.upload(token, "roomWallpaper", "image/jpeg", bytes)
                            if (!uploaded.ok) error = uploaded.error
                            else {
                                val url = uploaded.json.optString("url")
                                if (!url.startsWith("https://", ignoreCase = true) || url.length > 500) {
                                    error = "Server tidak mengembalikan URL gambar HTTPS yang valid."
                                } else if (target == "banner") {
                                    draftBanner = url
                                } else {
                                    draftWallpaper = url
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Gambar gagal diunggah."
                        } finally {
                            busy = false
                            uploadingImage = null
                        }
                    }
                }

                fun prepareRoomImage(uri: Uri?, target: String) {
                    if (uri != null) scope.launch {
                        busy = true; error = null
                        try {
                            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 1920) }
                            if (bitmap == null) {
                                error = "Gambar tidak dapat dibaca. Coba pilih file gambar lain."
                            } else {
                                roomImageCropTarget = target
                                roomImageToCrop = bitmap
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Gambar tidak dapat dibaca."
                        } finally {
                            busy = false
                        }
                    }
                }
                val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> prepareRoomImage(uri, "banner") }
                val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> prepareRoomImage(uri, "wallpaper") }
                roomImageToCrop?.let { image ->
                    val target = roomImageCropTarget ?: "wallpaper"
                    val ratio = if (target == "banner") 16f / 6f else 16f / 9f
                    CropBitmapDialog(
                        bitmap = image,
                        aspectRatio = ratio,
                        isAvatar = false,
                        fixedAspectRatio = true,
                        title = if (target == "banner") "Pangkas banner ruangan (16:6)" else "Pangkas wallpaper ruangan (16:9)",
                        onDismiss = { roomImageToCrop = null; roomImageCropTarget = null },
                        onConfirm = { cropped ->
                            roomImageToCrop = null
                            roomImageCropTarget = null
                            uploadRoomImage(cropped, target)
                        },
                    )
                }
                GlassCard {
                    Text("Tampilan ruangan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (isPublicRoom) "Ruangan publik" else "Ruangan privat", fontWeight = FontWeight.SemiBold)
                            Text(if (isPublicRoom) "Terlihat di daftar ruangan publik." else "Hanya host dan anggota yang dapat mengakses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isPublicRoom,
                            enabled = !busy,
                            onCheckedChange = { next ->
                                scope.launch {
                                    busy = true
                                    val changed = CommunityApi.listenSetVisibility(token, c, if (next) "public" else "private")
                                    busy = false
                                    if (changed.ok) isPublicRoom = next else error = changed.error
                                }
                            },
                        )
                    }
                    OutlinedTextField(draftName, { draftName = it.take(60) }, label = { Text("Nama room") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draftBanner, { draftBanner = it.take(500) }, label = { Text("URL banner HTTPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy, onClick = { bannerPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                        Text(if (uploadingImage == "banner") "Mengunggah banner…" else "Pilih / upload banner dari galeri")
                    }
                    if (draftBanner.startsWith("https://", ignoreCase = true)) AsyncImage(model = draftBanner, contentDescription = "Pratinjau banner", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(18.dp)))
                    OutlinedTextField(draftWallpaper, { draftWallpaper = it.take(500) }, label = { Text("URL wallpaper HTTPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy, onClick = { wallpaperPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                        Text(if (uploadingImage == "wallpaper") "Mengunggah wallpaper…" else "Pilih / upload wallpaper dari galeri")
                    }
                    if (draftWallpaper.startsWith("https://", ignoreCase = true)) AsyncImage(model = draftWallpaper, contentDescription = "Pratinjau wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)))
                    Button(enabled = !busy, onClick = {
                        val invalidUrl = listOf(draftBanner, draftWallpaper).any { value ->
                            val url = value.trim()
                            url.isNotEmpty() && (!url.startsWith("https://", ignoreCase = true) || url.length > 500)
                        }
                        if (invalidUrl) {
                            error = "URL banner dan wallpaper harus berupa HTTPS maksimal 500 karakter."
                        } else scope.launch {
                            busy = true
                            val saved = CommunityApi.listenUpdateRoom(token, c, draftName, draftBanner.trim(), draftWallpaper.trim())
                            busy = false
                            if (saved.ok) {
                                roomName = draftName
                                bannerUrl = draftBanner.trim()
                                wallpaperUrl = draftWallpaper.trim()
                            } else error = saved.error
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Simpan nama, banner, dan wallpaper") }
                    Text("Foto galeri diubah ke JPEG dan dikompresi maksimal sekitar 2,8 MB sebelum diunggah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Chat ruangan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 540.dp).clip(RoundedCornerShape(24.dp))) {
                ChatRoom(room = "listen:$c", state = state, onOpenProfile = {}, onStickerMaker = {})
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        val left = if (isHost) CommunityApi.listenCloseMetadata(token, c) else CommunityApi.listenLeave(token, c)
                        busy = false
                        if (left.ok) {
                            code = null; isHost = false; isPublicRoom = false; members = 0
                            hostName = ""; roomName = ""; bannerUrl = ""; wallpaperUrl = ""; remote = null
                            roomMembers = emptyList(); roomSession.memberList.value = emptyList()
                            roomSession.pendingEntryCode.value = null; pendingSongRequestId = null
                        } else error = left.error
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (isHost) "Tutup ruangan" else "Keluar", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PublicListenRoomsSection(
    token: String,
    username: String,
    roomSession: ListenRoomSession,
    selectedServer: String,
    onServerSelected: (String) -> Unit,
    onOpenListenTogether: () -> Unit,
) {
    val manager = LocalListenTogetherManager.current
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<PublicListenRoom>>(emptyList()) }
    var hasLoadedRooms by remember { mutableStateOf(false) }
    var loadingRooms by remember { mutableStateOf(false) }
    var roomsError by remember { mutableStateOf<String?>(null) }
    var joiningCode by remember { mutableStateOf<String?>(null) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var uptimeNow by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val connectionState = manager?.connectionState?.collectAsStateWithLifecycle()?.value ?: ConnectionState.DISCONNECTED
    val roomState = manager?.roomState?.collectAsStateWithLifecycle()?.value
    val activeRestCode = roomSession.code.value
    val canChangeServer = roomState == null && activeRestCode == null
    var serverMenuExpanded by remember { mutableStateOf(false) }
    val glass = LocalLiquidGlass.current

    suspend fun refreshRooms() {
        loadingRooms = true
        roomsError = null
        val result = CommunityApi.listenPublicRooms(if (selectedServer == "server1") "websocket" else "rest")
        if (result.ok) {
            val array = result.json.optJSONArray("rooms")
            if (array == null) {
                roomsError = "Daftar ruangan publik tidak valid. Coba muat ulang."
            } else {
                rooms = (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { room ->
                        val code = room.optString("code").trim()
                        if (code.isBlank()) null else PublicListenRoom(
                            code = code,
                            hostName = room.optString("hostName", "Host").ifBlank { "Host" },
                            members = room.optInt("members", 1),
                            createdAt = room.optLong("createdAt"),
                            transport = room.optString("transport", "websocket"),
                            roomName = room.optString("roomName"),
                            bannerUrl = room.optString("bannerUrl"),
                            wallpaperUrl = room.optString("wallpaperUrl"),
                        )
                    }
                }
                hasLoadedRooms = true
            }
        } else {
            roomsError = result.error.ifBlank { "Tidak dapat memuat ruangan publik. Periksa koneksi lalu coba lagi." }
        }
        loadingRooms = false
    }

    LaunchedEffect(activeRestCode, roomSession.isPublic.value, roomSession.roomName.value, roomSession.bannerUrl.value, roomSession.wallpaperUrl.value, selectedServer) { refreshRooms() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            uptimeNow = System.currentTimeMillis()
        }
    }
    LaunchedEffect(manager) {
        manager?.events?.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinApproved,
                is ListenTogetherEvent.Reconnected -> {
                    joiningCode = null
                    joinError = null
                }
                is ListenTogetherEvent.JoinRejected -> {
                    joiningCode = null
                    joinError = event.reason.ifBlank { "Permintaan bergabung ditolak oleh host." }
                }
                is ListenTogetherEvent.ConnectionError -> {
                    if (joiningCode != null) {
                        joiningCode = null
                        joinError = event.error.ifBlank { "Koneksi Listen Together gagal. Coba bergabung lagi." }
                    }
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(roomState) {
        if (roomState != null) joiningCode = null
    }

    GlassCard {
        Box(Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).glassBorder(RoundedCornerShape(20.dp), glass).clickable(enabled = canChangeServer) { serverMenuExpanded = true },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glass) 0.88f else 1f),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (selectedServer == "server1") "Server 1 · Stabil" else "Server 2 · Stabil · Direkomendasikan", fontWeight = FontWeight.SemiBold)
                        Text(if (selectedServer == "server1") "Listen Together" else "Room komunitas Sanzxcy · banner galeri atau URL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (canChangeServer) "⌄" else "Aktif", color = MaterialTheme.colorScheme.primary)
                }
            }
            DropdownMenu(expanded = serverMenuExpanded, onDismissRequest = { serverMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Column { Text("Server 1", fontWeight = FontWeight.SemiBold); Text("Stabil · Listen Together", style = MaterialTheme.typography.bodySmall) } },
                    onClick = { serverMenuExpanded = false; onServerSelected("server1") },
                )
                DropdownMenuItem(
                    text = { Column { Text("Server 2 · Direkomendasikan", fontWeight = FontWeight.SemiBold); Text("Stabil · room Sanzxcy dan banner galeri/URL", style = MaterialTheme.typography.bodySmall) } },
                    onClick = { serverMenuExpanded = false; onServerSelected("server2") },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ruangan publik", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (selectedServer == "server1") "Temukan room publik pada Server 1." else "Temukan room publik pada server Sanzxcy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(enabled = !loadingRooms, onClick = { scope.launch { refreshRooms() } }) {
                Text("Muat ulang")
            }
        }

        if (loadingRooms && !hasLoadedRooms) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("Memuat ruangan publik…", modifier = Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        roomsError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (!loadingRooms) OutlinedButton(onClick = { scope.launch { refreshRooms() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Coba lagi")
            }
        }
        if (hasLoadedRooms && rooms.isEmpty() && roomsError == null && !loadingRooms) {
            Text("Belum ada ruangan publik yang tersedia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loadingRooms && hasLoadedRooms) {
            Text("Memperbarui daftar…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (roomState != null) {
            val activeRoom = roomState
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kamu sudah bergabung", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Ruang ${activeRoom.roomCode} · ${if (manager?.isHost == true) "Host" else "Pendengar"} · ${activeRoom.usersList.count { it.isConnected }} terhubung",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenListenTogether, modifier = Modifier.weight(1f)) {
                            Text("Kontrol lengkap")
                        }
                        OutlinedButton(onClick = { manager?.leaveRoom(); joiningCode = null }, modifier = Modifier.weight(1f)) {
                            Text("Keluar")
                        }
                    }
                }
            }
        } else if (activeRestCode != null) {
            Text("Room komunitas aktif: $activeRestCode · Kontrol dan status host ada di panel ruangan.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        } else if (joiningCode != null) {
            Text(
                "Menghubungkan ke ruangan $joiningCode…${if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) " Menyiapkan koneksi." else ""}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        joinError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        rooms.forEach { room ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (room.bannerUrl.startsWith("https://", ignoreCase = true)) {
                        AsyncImage(
                            model = room.bannerUrl,
                            contentDescription = "Banner ${room.roomName.ifBlank { "ruangan publik" }}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(room.roomName.ifBlank { room.hostName }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (room.roomName.isNotBlank()) Text("Host: ${room.hostName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text("${room.members} pendengar · Kode ${room.code} · ${if (room.transport == "rest") "Komunitas" else "Listen Together"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Berjalan ${roomUptimeText(room.createdAt, uptimeNow)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            enabled = roomState == null && activeRestCode == null && joiningCode == null &&
                                (if (room.transport == "rest") token.isNotBlank() else manager != null && username.isNotBlank() &&
                                    manager.hasPersistedSession.not() && connectionState != ConnectionState.CONNECTING && connectionState != ConnectionState.RECONNECTING),
                            onClick = {
                                joinError = null
                                joiningCode = room.code
                                if (room.transport == "rest") {
                                    scope.launch {
                                        val joined = CommunityApi.listenJoin(token, room.code)
                                        joiningCode = null
                                        if (joined.ok) {
                                            roomSession.code.value = room.code
                                            roomSession.isHost.value = joined.json.optString("host") == username
                                            roomSession.isPublic.value = joined.json.optString("visibility") == "public"
                                            roomSession.members.intValue = joined.json.optInt("members", 1)
                                            roomSession.hostName.value = joined.json.optString("hostName")
                                            roomSession.roomName.value = joined.json.optString("roomName")
                                            roomSession.bannerUrl.value = joined.json.optString("bannerUrl")
                                            roomSession.wallpaperUrl.value = joined.json.optString("wallpaperUrl")
                                            roomSession.remote.value = parseListenState(joined.json.optJSONObject("state"))
                                            roomSession.memberList.value = parseRoomMembers(joined.json.optJSONArray("memberList"))
                                        } else joinError = joined.error
                                    }
                                } else {
                                    val listenTogetherManager = manager
                                    if (listenTogetherManager == null) {
                                        joiningCode = null
                                        joinError = "Fitur Listen Together belum tersedia di perangkat ini."
                                    } else {
                                        // joinRoom queues the request and opens one connection itself when needed.
                                        listenTogetherManager.joinRoom(room.code, username)
                                    }
                                }
                            },
                        ) {
                            Text(if (joiningCode == room.code) "Menghubungkan…" else "Gabung")
                        }
                    }
                }
            }
        }
        val hasWebSocketRooms = rooms.any { it.transport != "rest" }
        if (manager == null && hasWebSocketRooms) {
            Text("Fitur Listen Together belum tersedia di perangkat ini.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else if (hasWebSocketRooms && username.isBlank()) {
            Text("Nama akun komunitas belum tersedia untuk bergabung.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else if (hasWebSocketRooms && roomState == null && manager?.hasPersistedSession == true) {
            Text("Sesi Listen Together sebelumnya sedang dipulihkan; tunggu sampai status ruangan muncul sebelum bergabung ke ruangan lain.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        } else if (hasWebSocketRooms && roomState == null && (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING)) {
            Text("Koneksi Listen Together sedang disiapkan.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GlassCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = shape, modifier = Modifier.fillMaxWidth().glassBorder(shape, LocalLiquidGlass.current)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun parseRoomMembers(array: JSONArray?): List<ListenRoomMember> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { item ->
            val username = item.optString("username")
            if (username.isBlank()) null else ListenRoomMember(
                username = username,
                displayName = item.optString("displayName").ifBlank { username },
                avatarUrl = item.optString("avatarUrl"),
            )
        }
    }
}

private fun parseEntryRequests(array: JSONArray?): List<ListenEntryRequest> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { item ->
            val username = item.optString("username")
            if (username.isBlank()) null else ListenEntryRequest(username, item.optString("displayName").ifBlank { username }, item.optLong("requestedAt"))
        }
    }
}

private fun parseSongRequests(array: JSONArray?): List<ListenSongRequest> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { item ->
            val id = item.optString("id"); val videoId = item.optString("videoId")
            if (id.isBlank() || videoId.isBlank()) null else ListenSongRequest(
                id = id, username = item.optString("username"), displayName = item.optString("displayName").ifBlank { item.optString("username") },
                videoId = videoId, title = item.optString("title"), artist = item.optString("artist"), requestedAt = item.optLong("requestedAt"), status = item.optString("status", "pending"),
            )
        }
    }
}
