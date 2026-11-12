/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.sanzxcy.music.com.BuildConfig
import com.sanzxcy.music.com.R
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.constants.ListenTogetherInTopBarKey
import com.metrolist.music.constants.ListenTogetherUsernameKey
import com.metrolist.music.community.CommunityApi
import com.metrolist.music.community.CommunityTokenKey
import com.metrolist.music.community.CropBitmapDialog
import com.metrolist.music.community.PublicListenRoom
import com.metrolist.music.community.compressJpeg
import com.metrolist.music.community.decodeBitmap
import com.metrolist.music.community.roomUptimeText
import com.metrolist.music.community.uploadedUrls
import com.metrolist.music.listentogether.ConnectionState
import com.metrolist.music.listentogether.JoinRequestPayload
import com.metrolist.music.listentogether.ListenTogetherEvent
import com.metrolist.music.listentogether.RoomRole
import com.metrolist.music.listentogether.SuggestionReceivedPayload
import com.metrolist.music.listentogether.UserInfo
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.theme.LocalLiquidGlass
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.IconButton as MaterialIconButton
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController,
    showTopBar: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listenTogetherManager = LocalListenTogetherManager.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val joiningRoomTemplate = stringResource(R.string.joining_room)

    if (listenTogetherManager == null) {
        NotConfiguredContent()
        return
    }

    val connectionState by listenTogetherManager.connectionState.collectAsStateWithLifecycle()
    val roomState by listenTogetherManager.roomState.collectAsStateWithLifecycle()
    val userId by listenTogetherManager.userId.collectAsStateWithLifecycle()
    val pendingJoinRequests by listenTogetherManager.pendingJoinRequests.collectAsStateWithLifecycle()
    val pendingSuggestions by listenTogetherManager.pendingSuggestions.collectAsStateWithLifecycle()
    val roomVisibility by listenTogetherManager.roomVisibility.collectAsStateWithLifecycle()

    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
    val (communityToken) = rememberPreference(CommunityTokenKey, "")
    val shouldShowTopBar = showTopBar || listenTogetherInTopBar

    var savedUsername by rememberPreference(ListenTogetherUsernameKey, "")
    var roomCodeInput by rememberSaveable { mutableStateOf("") }
    var usernameInput by rememberSaveable { mutableStateOf(savedUsername) }
    var isPublicRoom by rememberSaveable { mutableStateOf(false) }
    var publicRooms by remember { mutableStateOf(emptyList<PublicListenRoom>()) }
    var loadingPublicRooms by remember { mutableStateOf(false) }
    var roomUptimeNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var activeRoomName by rememberSaveable(roomState?.roomCode) { mutableStateOf("") }
    var activeRoomBanner by rememberSaveable(roomState?.roomCode) { mutableStateOf("") }
    var activeRoomWallpaper by rememberSaveable(roomState?.roomCode) { mutableStateOf("") }

    var isCreatingRoom by rememberSaveable { mutableStateOf(false) }
    var isJoiningRoom by rememberSaveable { mutableStateOf(false) }
    var joinErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var selectedUserForMenu by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUsername by rememberSaveable { mutableStateOf<String?>(null) }

    val waitingForApprovalText = stringResource(R.string.waiting_for_approval)
    val invalidRoomCodeText = stringResource(R.string.invalid_room_code)
    val joinRequestDeniedText = stringResource(R.string.join_request_denied)

    LaunchedEffect(savedUsername) {
        if (usernameInput.isBlank() && savedUsername.isNotBlank()) {
            usernameInput = savedUsername
        }
    }

    LaunchedEffect(listenTogetherManager) {
        listenTogetherManager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> {
                    val reason = event.reason
                    joinErrorMessage =
                        when {
                            reason.isNullOrBlank() -> joinRequestDeniedText
                            reason.contains("invalid", ignoreCase = true) -> invalidRoomCodeText
                            else -> "$joinRequestDeniedText: $reason"
                        }
                    isJoiningRoom = false
                    isCreatingRoom = false
                }

                is ListenTogetherEvent.JoinApproved -> {
                    isJoiningRoom = false
                    joinErrorMessage = null
                }

                is ListenTogetherEvent.RoomCreated -> {
                    isCreatingRoom = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ListenTogetherRoom", event.roomCode)
                    clipboard.setPrimaryClip(clip)
                }

                else -> {}
            }
        }
    }

    val isInRoom = listenTogetherManager.isInRoom
    val isHost = roomState?.hostId == userId

    // User action menu dialog
    if (selectedUserForMenu != null && selectedUsername != null) {
        UserActionDialog(
            username = selectedUsername ?: "",
            onKick = {
                selectedUserForMenu?.let {
                    listenTogetherManager.kickUser(it, "Removed by host")
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onPermanentKick = {
                selectedUserForMenu?.let { userId ->
                    selectedUsername?.let { username ->
                        listenTogetherManager.blockUser(username)
                        listenTogetherManager.kickUser(userId, R.string.user_blocked_by_host.toString())
                    }
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onTransferOwnership = {
                val newHostId = selectedUserForMenu
                val newHostUsername = selectedUsername
                if (newHostId != null) {
                    // Server 1 has its own websocket host state; mirror the
                    // transfer to the metadata API before changing the socket
                    // role so the new host can also edit banner/wallpaper.
                    coroutineScope.launch {
                        val code = roomState?.roomCode
                        val synced = if (!communityToken.isNullOrBlank() && !code.isNullOrBlank() && !newHostUsername.isNullOrBlank()) {
                            CommunityApi.listenTransfer(communityToken, code, newHostUsername).ok
                        } else true
                        if (synced) listenTogetherManager.transferHost(newHostId)
                        else Toast.makeText(context, "Kepemilikan room belum tersinkron. Coba lagi.", Toast.LENGTH_SHORT).show()
                    }
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onDismiss = {
                selectedUserForMenu = null
                selectedUsername = null
            },
        )
    }

    val lazyListState = rememberLazyListState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    suspend fun refreshPublicRooms() {
        loadingPublicRooms = true
        val result = CommunityApi.listenPublicRooms()
        val array = result.json.optJSONArray("rooms")
        publicRooms = if (result.ok && array != null) {
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { room ->
                    PublicListenRoom(
                        code = room.optString("code"),
                        hostName = room.optString("hostName", "Host"),
                        members = room.optInt("members", 1),
                        createdAt = room.optLong("createdAt"),
                        transport = room.optString("transport", "websocket"),
                        roomName = room.optString("roomName"),
                        wallpaperUrl = room.optString("wallpaperUrl"),
                        bannerUrl = room.optString("bannerUrl"),
                    )
                }
            }
        } else emptyList()
        loadingPublicRooms = false
    }

    LaunchedEffect(Unit) { refreshPublicRooms() }
    LaunchedEffect(roomState?.roomCode, communityToken, publicRooms) {
        val code = roomState?.roomCode ?: return@LaunchedEffect
        publicRooms.firstOrNull { it.code == code }?.let { room ->
            activeRoomName = room.roomName
            activeRoomBanner = room.bannerUrl
            activeRoomWallpaper = room.wallpaperUrl
        }
        if (communityToken.isNotBlank()) {
            val metadata = CommunityApi.listenGet(communityToken, code)
            if (metadata.ok && metadata.json.optString("transport") == "websocket") {
                activeRoomName = metadata.json.optString("roomName")
                activeRoomBanner = metadata.json.optString("bannerUrl")
                activeRoomWallpaper = metadata.json.optString("wallpaperUrl")
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            roomUptimeNow = System.currentTimeMillis()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        if (isInRoom && activeRoomWallpaper.startsWith("https://", ignoreCase = true)) {
            AsyncImage(
                model = activeRoomWallpaper,
                contentDescription = "Latar belakang ruangan",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)))
        }

        LazyColumn(
        state = lazyListState,
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = windowInsets.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = windowInsets.asPaddingValues().calculateBottomPadding() + 16.dp + AppBarHeight,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            HeaderSection(isInRoom = isInRoom)
        }

        // Connection status card
        item {
            ConnectionStatusCard(
                connectionState = connectionState,
                onConnect = { listenTogetherManager.connect() },
                onDisconnect = { listenTogetherManager.disconnect() },
                onReconnect = { listenTogetherManager.forceReconnect() },
            )
        }

        if (connectionState == ConnectionState.CONNECTED && !isInRoom) {
            item {
                Text(
                    text = stringResource(R.string.listen_together_background_disconnect_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isInRoom) {
            // Room status card
            roomState?.let { room ->
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp))) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        if (activeRoomBanner.startsWith("https://", ignoreCase = true)) {
                            AsyncImage(
                                model = activeRoomBanner,
                                contentDescription = "Banner ruangan",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Surface(color = Color.Black.copy(alpha = 0.48f), modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.Bottom) {
                                Text(activeRoomName.ifBlank { "Dengar bersama" }, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text("${room.usersList.count { it.isConnected }} pendengar terhubung", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    RoomStatusCard(
                        roomCode = room.roomCode,
                        isHost = isHost,
                        isPublic = roomVisibility,
                        onVisibilityChange = listenTogetherManager::setRoomVisibility,
                        context = context,
                    )
                }

                if (isHost && communityToken.isNotBlank()) {
                    item {
                        RoomAppearancePanel(
                            roomCode = room.roomCode,
                            token = communityToken,
                            roomName = activeRoomName,
                            bannerUrl = activeRoomBanner,
                            wallpaperUrl = activeRoomWallpaper,
                            onSave = { name, banner, wallpaper ->
                                coroutineScope.launch {
                                    val saved = CommunityApi.listenUpdateRoom(communityToken, room.roomCode, name, banner, wallpaper)
                                    if (saved.ok) {
                                        activeRoomName = name
                                        activeRoomBanner = banner
                                        activeRoomWallpaper = wallpaper
                                        refreshPublicRooms()
                                        Toast.makeText(context, "Tampilan room disimpan", Toast.LENGTH_SHORT).show()
                                    } else Toast.makeText(context, saved.error.ifBlank { "Tampilan room gagal disimpan" }, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }

                // Connected users
                val connectedUsers = room.usersList.filter { it.isConnected }
                val currentUserIdValue = userId ?: ""
                item {
                    ConnectedUsersSection(
                        users = connectedUsers,
                        isHost = isHost,
                        currentUserId = currentUserIdValue,
                        onUserClick = { clickedUserId, username ->
                            if (isHost && clickedUserId != currentUserIdValue) {
                                selectedUserForMenu = clickedUserId
                                selectedUsername = username
                            }
                        },
                    )
                }

                // Pending join requests (host only)
                if (isHost && pendingJoinRequests.isNotEmpty()) {
                    item {
                        PendingJoinRequestsSection(
                            requests = pendingJoinRequests,
                            onApprove = { listenTogetherManager.approveJoin(it) },
                            onReject = { listenTogetherManager.rejectJoin(it, "Rejected by host") },
                        )
                    }
                }

                // Pending suggestions (host only)
                if (isHost && pendingSuggestions.isNotEmpty()) {
                    item {
                        PendingSuggestionsSection(
                            suggestions = pendingSuggestions,
                            onApprove = { listenTogetherManager.approveSuggestion(it) },
                            onReject = { listenTogetherManager.rejectSuggestion(it, "Rejected by host") },
                        )
                    }
                }

                // Leave room button
                item {
                    Button(
                        onClick = { listenTogetherManager.leaveRoom() },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.logout),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.leave_room),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        } else {
            // Join/Create room section
            item {
                JoinCreateRoomSection(
                    usernameInput = usernameInput,
                    onUsernameChange = { usernameInput = it },
                    roomCodeInput = roomCodeInput,
                    onRoomCodeChange = { roomCodeInput = it },
                    savedUsername = savedUsername,
                    isPublicRoom = isPublicRoom,
                    onPublicRoomChange = { isPublicRoom = it },
                    isJoiningRoom = isJoiningRoom,
                    joinErrorMessage = joinErrorMessage,
                    waitingForApprovalText = waitingForApprovalText,
                    bringIntoViewRequester = bringIntoViewRequester,
                    onCreateRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            Toast.makeText(context, R.string.creating_room, Toast.LENGTH_SHORT).show()
                            isCreatingRoom = true
                            isJoiningRoom = false
                            joinErrorMessage = null
                            listenTogetherManager.connect()
                            listenTogetherManager.createRoom(finalUsername, isPublicRoom)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onJoinRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            Toast
                                .makeText(
                                    context,
                                    String.format(joiningRoomTemplate, roomCodeInput),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            isJoiningRoom = true
                            isCreatingRoom = false
                            joinErrorMessage = null
                            listenTogetherManager.connect()
                            listenTogetherManager.joinRoom(roomCodeInput, finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFieldFocused = {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Public rooms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { coroutineScope.launch { refreshPublicRooms() } }, enabled = !loadingPublicRooms) {
                            Text(if (loadingPublicRooms) "Loading…" else "Refresh")
                        }
                    }
                    if (loadingPublicRooms && publicRooms.isEmpty()) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    if (!loadingPublicRooms && publicRooms.isEmpty()) Text("No public rooms are available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    publicRooms.forEach { room ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (room.bannerUrl.startsWith("https://", ignoreCase = true)) {
                                    AsyncImage(model = room.bannerUrl, contentDescription = "Banner ${room.roomName.ifBlank { "room publik" }}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp)))
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(room.roomName.ifBlank { room.hostName }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (room.roomName.isNotBlank()) Text("Host: ${room.hostName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${room.code} · ${room.members} listener${if (room.members == 1) "" else "s"} · Berjalan ${roomUptimeText(room.createdAt, roomUptimeNow)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(onClick = {
                                        val name = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                                        if (name.isNotBlank()) {
                                            savedUsername = name.trim()
                                            isJoiningRoom = true
                                            isCreatingRoom = false
                                            listenTogetherManager.connect()
                                            listenTogetherManager.joinRoom(room.code, name.trim())
                                        } else Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                                    }, enabled = !isJoiningRoom && (usernameInput.isNotBlank() || savedUsername.isNotBlank())) { Text("Join") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings link
        item {
            SettingsLinkCard(
                onClick = { navController.navigate("settings/integrations/listen_together") },
            )
        }
        }
    }

    if (shouldShowTopBar) {
        TopAppBar(
            title = { Text(stringResource(R.string.together)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )
    }
}

@Composable
private fun RoomAppearancePanel(
    roomCode: String,
    token: String,
    roomName: String,
    bannerUrl: String,
    wallpaperUrl: String,
    onSave: (String, String, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glass = LocalLiquidGlass.current
    var draftName by rememberSaveable(roomCode) { mutableStateOf(roomName) }
    var draftBanner by rememberSaveable(roomCode) { mutableStateOf(bannerUrl) }
    var draftWallpaper by rememberSaveable(roomCode) { mutableStateOf(wallpaperUrl) }
    var wallpaperToCrop by remember(roomCode) { mutableStateOf<Bitmap?>(null) }
    var wallpaperBusy by remember(roomCode) { mutableStateOf(false) }
    var wallpaperStatus by remember(roomCode) { mutableStateOf<String?>(null) }
    var wallpaperStatusError by remember(roomCode) { mutableStateOf(false) }

    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            wallpaperStatus = "Pemilihan wallpaper dibatalkan."
            wallpaperStatusError = false
        } else {
            scope.launch {
                wallpaperBusy = true
                wallpaperStatus = "Membaca gambar wallpaper…"
                wallpaperStatusError = false
                try {
                    wallpaperToCrop = withContext(Dispatchers.IO) { decodeBitmap(context, uri, 1920) }
                    if (wallpaperToCrop == null) {
                        wallpaperStatus = "Gambar tidak bisa dibaca. Pilih gambar lain."
                        wallpaperStatusError = true
                    } else {
                        wallpaperStatus = null
                    }
                } catch (e: Exception) {
                    wallpaperStatus = e.message?.takeIf(String::isNotBlank) ?: "Gambar tidak bisa dibaca."
                    wallpaperStatusError = true
                } finally {
                    wallpaperBusy = false
                }
            }
        }
    }

    wallpaperToCrop?.let { bitmap ->
        CropBitmapDialog(
            bitmap = bitmap,
            aspectRatio = 16f / 9f,
            isAvatar = false,
            fixedAspectRatio = true,
            title = "Pangkas wallpaper room (16:9)",
            onDismiss = {
                wallpaperToCrop = null
                wallpaperStatus = "Pemangkasan wallpaper dibatalkan."
                wallpaperStatusError = false
            },
            onConfirm = { cropped ->
                wallpaperToCrop = null
                scope.launch {
                    wallpaperBusy = true
                    wallpaperStatus = "Mengompres dan mengunggah wallpaper…"
                    wallpaperStatusError = false
                    try {
                        val bytes = withContext(Dispatchers.IO) { compressJpeg(cropped, 2_800_000) }
                        val uploaded = CommunityApi.upload(token, "roomWallpaper", "image/jpeg", bytes)
                        if (!uploaded.ok) {
                            wallpaperStatus = uploaded.error.ifBlank { "Wallpaper gagal diunggah." }
                            wallpaperStatusError = true
                        } else {
                            val url = uploadedUrls(uploaded.json).firstOrNull { it.length <= 500 && isValidHttpsImageUrl(it) }
                            if (url == null) {
                                wallpaperStatus = "Server tidak mengembalikan URL wallpaper HTTPS yang valid (maks. 500 karakter)."
                                wallpaperStatusError = true
                            } else {
                                draftWallpaper = url
                                wallpaperStatus = "Wallpaper diunggah. Tekan Simpan tampilan untuk menerapkan."
                            }
                        }
                    } catch (e: Exception) {
                        wallpaperStatus = e.message?.takeIf(String::isNotBlank) ?: "Wallpaper gagal diunggah."
                        wallpaperStatusError = true
                    } finally {
                        wallpaperBusy = false
                    }
                }
            },
        )
    }

    LaunchedEffect(roomName, bannerUrl, wallpaperUrl) {
        draftName = roomName
        draftBanner = bannerUrl
        draftWallpaper = wallpaperUrl
    }
    val bannerValid = isValidHttpsImageUrl(draftBanner)
    val wallpaperValid = isValidHttpsImageUrl(draftWallpaper)
    val panelShape = RoundedCornerShape(30.dp)
    Card(
        modifier = Modifier.fillMaxWidth().clip(panelShape).glassBorder(panelShape, glass),
        shape = panelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glass) 0.78f else 1f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tampilan ruangan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Banner tampil di daftar room publik dan header ruangan. Wallpaper menjadi latar penuh di belakang konten ruangan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(draftName, { draftName = it.take(60) }, label = { Text("Nama room") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = draftBanner,
                onValueChange = { draftBanner = it.take(500) },
                label = { Text("Banner URL HTTPS") },
                singleLine = true,
                isError = !bannerValid,
                supportingText = { if (!bannerValid) Text("Masukkan URL HTTPS yang valid (maks. 500 karakter).") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (draftBanner.isNotBlank() && bannerValid) {
                AsyncImage(draftBanner.trim(), contentDescription = "Pratinjau banner room", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)))
            }
            OutlinedButton(
                onClick = { wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !wallpaperBusy,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (wallpaperBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Pilih & crop wallpaper room (16:9)")
            }
            OutlinedTextField(
                value = draftWallpaper,
                onValueChange = { draftWallpaper = it.take(500) },
                label = { Text("Atau tempel URL wallpaper HTTPS") },
                singleLine = true,
                isError = !wallpaperValid,
                supportingText = { if (!wallpaperValid) Text("Masukkan URL HTTPS yang valid (maks. 500 karakter).") },
                modifier = Modifier.fillMaxWidth(),
            )
            wallpaperStatus?.let {
                Text(it, color = if (wallpaperStatusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (draftWallpaper.isNotBlank() && wallpaperValid) {
                AsyncImage(draftWallpaper.trim(), contentDescription = "Pratinjau wallpaper room", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)))
            }
            Button(onClick = { onSave(draftName.trim(), draftBanner.trim(), draftWallpaper.trim()) }, enabled = bannerValid && wallpaperValid && !wallpaperBusy, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Simpan tampilan") }
        }
    }
}

private fun isValidHttpsImageUrl(value: String): Boolean {
    if (value.isBlank()) return true
    return runCatching {
        URI(value.trim()).let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
    }.getOrDefault(false)
}

@Composable
private fun NotConfiguredContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.group),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.listen_together),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.listen_together_not_configured),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderSection(isInRoom: Boolean = false) {
    if (isInRoom) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.group_outlined),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.listen_together),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.listen_together_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                ),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when (connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.secondaryContainer
                        ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                color =
                                    when (connectionState) {
                                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
                                        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                    },
                            ),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text =
                        when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
                            ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
                            ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        when (connectionState) {
                            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
                            ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.connect), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Text(stringResource(R.string.disconnect), fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = onReconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Reconnect", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomStatusCard(
    roomCode: String,
    isHost: Boolean,
    isPublic: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    context: Context,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.room_code),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = roomCode,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    if (isHost) {
                        stringResource(R.string.listen_together_you_are_host)
                    } else {
                        stringResource(R.string.listen_together_you_are_guest)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (isHost) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("${if (isPublic) "Public" else "Private"} room", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { onVisibilityChange(!isPublic) }) {
                    Text(if (isPublic) "Make private" else "Make public")
                }
                Spacer(modifier = Modifier.height(4.dp))
                val inviteLink =
                    remember(roomCode) {
                        "${BuildConfig.SANZXCY_SERVER_URL}/join.html?code=$roomCode"
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Listen Together Link", inviteLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = stringResource(R.string.copy_link),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_link))
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Room Code", roomCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = stringResource(R.string.copy_code),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_code))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedUsersSection(
    users: List<UserInfo>,
    isHost: Boolean,
    currentUserId: String,
    onUserClick: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "${stringResource(R.string.connected_users)} (${users.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                users.forEach { user ->
                    UserAvatar(
                        user = user,
                        isCurrentUser = user.userId == currentUserId,
                        isClickable = isHost && user.userId != currentUserId,
                        onClick = { onUserClick(user.userId, user.username) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(
    user: UserInfo,
    isCurrentUser: Boolean,
    isClickable: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .width(72.dp)
                .clickable(enabled = isClickable, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color =
                    when {
                        user.isHost -> MaterialTheme.colorScheme.primary
                        isCurrentUser -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color =
                            when {
                                user.isHost -> MaterialTheme.colorScheme.onPrimary
                                isCurrentUser -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }

            if (user.isHost || isCurrentUser) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(20.dp),
                    shape = CircleShape,
                    color = if (user.isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (user.isHost) R.drawable.crown else R.drawable.person,
                                ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = user.username,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium,
            color = if (user.isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (user.isHost) {
            Text(
                text = stringResource(R.string.host_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        } else if (isCurrentUser) {
            Text(
                text = stringResource(R.string.you_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PendingJoinRequestsSection(
    requests: List<JoinRequestPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.listen_together_join_requests),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            requests.forEach { request ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Text(
                                text = request.username.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = request.username,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    MaterialIconButton(onClick = { onApprove(request.userId) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.approve),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    MaterialIconButton(onClick = { onReject(request.userId) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSuggestionsSection(
    suggestions: List<SuggestionReceivedPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pending_suggestions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.trackInfo.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = suggestion.fromUsername,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    MaterialIconButton(onClick = { onApprove(suggestion.suggestionId) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.approve),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    MaterialIconButton(onClick = { onReject(suggestion.suggestionId) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinCreateRoomSection(
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    savedUsername: String,
    isPublicRoom: Boolean,
    onPublicRoomChange: (Boolean) -> Unit,
    isJoiningRoom: Boolean,
    joinErrorMessage: String?,
    waitingForApprovalText: String,
    bringIntoViewRequester: BringIntoViewRequester,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onFieldFocused: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Username input
            OutlinedTextField(
                value = usernameInput,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text(stringResource(R.string.enter_username)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.person),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (usernameInput.isNotBlank()) {
                        MaterialIconButton(onClick = { onUsernameChange("") }) {
                            Icon(painterResource(R.drawable.close), null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) onFieldFocused() },
            )

            // Room visibility selector (private by default).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                (listOf(false to "Private" , true to "Public")).forEach { (public, label) ->
                    if (isPublicRoom == public) Button(onClick = { onPublicRoomChange(public) }, modifier = Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton(onClick = { onPublicRoomChange(public) }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
            if (isPublicRoom) Text("Public rooms require a community account for discovery metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Room code input
            OutlinedTextField(
                value = roomCodeInput,
                onValueChange = { if (it.length <= 8) onRoomCodeChange(it.uppercase()) },
                label = { Text(stringResource(R.string.room_code)) },
                placeholder = { Text(stringResource(R.string.enter_room_code)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.group),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (roomCodeInput.isNotBlank()) {
                        MaterialIconButton(onClick = { onRoomCodeChange("") }) {
                            Icon(painterResource(R.drawable.close), null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { if (it.isFocused) onFieldFocused() },
            )

            // Waiting for approval indicator
            AnimatedVisibility(
                visible = isJoiningRoom,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = waitingForApprovalText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Error message
            AnimatedVisibility(
                visible = joinErrorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.error),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = joinErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Action buttons
            val hasUsername = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
            val hasRoomCode = roomCodeInput.length == 8

            // Create Room button - visible when username is provided
            AnimatedVisibility(visible = hasUsername && !hasRoomCode) {
                Button(
                    onClick = onCreateRoom,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasUsername,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_room), fontWeight = FontWeight.SemiBold)
                }
            }

            // Join Room button - visible when username and room code are provided
            AnimatedVisibility(visible = hasUsername && hasRoomCode) {
                Button(
                    onClick = onJoinRoom,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hasUsername && hasRoomCode,
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.login),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.join_room), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsLinkCard(onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.settings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.listen_together_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun UserActionDialog(
    username: String,
    onKick: () -> Unit,
    onPermanentKick: () -> Unit,
    onTransferOwnership: () -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.group),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.manage_user),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Kick button
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Permanently kick button
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPermanentKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permanently_kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.permanently_kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Transfer ownership button
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTransferOwnership),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.crown),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.transfer_ownership),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.transfer_ownership_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
