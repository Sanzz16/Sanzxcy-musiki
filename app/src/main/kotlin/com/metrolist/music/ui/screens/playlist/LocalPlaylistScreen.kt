/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.sanzxcy.music.com.R
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.PlaylistEditLockKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.constants.SwipeToRemoveSongKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.DraggableScrollbar
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.OverlayEditButton
import com.metrolist.music.ui.component.glassBorder
import com.metrolist.music.ui.component.pressScale
import com.metrolist.music.ui.component.GlassWindowBlur
import com.metrolist.music.ui.theme.LocalLiquidGlass
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.menu.CustomThumbnailMenu
import com.metrolist.music.ui.menu.LocalPlaylistMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val onlinePlaylist by viewModel.onlinePlaylist.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSongSortTypeKey,
            PlaylistSongSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            PlaylistSongSortDescendingKey,
            true,
        )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists
                            .fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<Int>()
        }
    var selectionAnchorMapId by remember { mutableStateOf<Int?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorMapId = null
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }

        if (selectionAnchorMapId != null && songs.none { it.map.id == selectionAnchorMapId }) {
            selectionAnchorMapId = songs.firstOrNull { it.map.id in selection }?.map?.id
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showAddSongs by rememberSaveable { mutableStateOf(false) }
    if (showAddSongs && playlist != null) AddSongsToLocalPlaylistDialog(playlist = playlist!!, onDismiss = { showAddSongs = false })
    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                    )
                },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue =
                    TextFieldValue(
                        playlistEntity.name,
                        TextRange(playlistEntity.name.length),
                    ),
                onDone = { name ->
                    database.query {
                        update(
                            playlistEntity.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }

                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    // Sync order with YT Music
                    val browseId = viewModel.playlist.value?.playlist?.browseId
                    if (browseId != null) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val setVideoId = playlistSongMap.getOrNull(to)?.setVideoId
                        val successorSetVideoId = playlistSongMap.getOrNull(to + 1)?.setVideoId

                        if (setVideoId != null) {
                            YouTube.moveSongPlaylist(
                                browseId,
                                setVideoId,
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist?.let { playlist ->
                if (playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            LocalPlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                onlinePlaylist = onlinePlaylist,
                                onShowEditDialog = { showEditDialog = true },
                                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                                onshowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
                                onStartSearch = { isSearching = true },
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    item(key = "controls_row") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .padding(start = 16.dp)
                                    .animateItem(),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.ALBUM -> R.string.sort_by_album
                                        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (editable) {
                                Button(
                                    onClick = { showAddSongs = true },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                ) {
                                    Icon(painter = painterResource(R.drawable.playlist_add_rounded), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Tambah lagu", style = MaterialTheme.typography.labelLarge)
                                }
                                IconButton(
                                    onClick = { locked = !locked },
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(if (locked) R.drawable.lock_rounded else R.drawable.lock_open_rounded),
                                        contentDescription = if (locked) "Urutan terkunci. Ketuk untuk mengaktifkan pengurutan manual" else "Urutan terbuka. Ketuk untuk mengunci urutan",
                                        tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val displayedSongs = if (isSearching) filteredSongs else mutableSongs

            itemsIndexed(
                items = displayedSongs,
                key = { _, song -> song.map.id },
            ) { index, song ->
                ReorderableItem(
                    state = reorderableState,
                    key = song.map.id,
                ) {
                    val currentItem by rememberUpdatedState(song)

                    fun deleteFromPlaylist() {
                        // Capture values before deletion — DB entry will be gone afterwards
                        val browseId = playlist?.playlist?.browseId
                        val setVideoId = currentItem.map.setVideoId
                        val songId = currentItem.map.songId
                        val playlistId = currentItem.map.playlistId

                        database.transaction {
                            move(playlistId, currentItem.map.position, Int.MAX_VALUE)
                            delete(currentItem.map.copy(position = Int.MAX_VALUE))
                        }

                        if (browseId != null) {
                            syncUtils.scheduleRemoveFromPlaylist(
                                browseId,
                                songId,
                                playlistId
                            ) {
                                setVideoId
                            }
                        }
                    }

                    val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)
                    val dismissBoxState =
                        rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                        )
                    var processedDismiss by remember { mutableStateOf(false) }
                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (swipeRemoveEnabled && !processedDismiss && (
                                dv == SwipeToDismissBoxValue.StartToEnd ||
                                    dv == SwipeToDismissBoxValue.EndToStart
                            )
                        ) {
                            processedDismiss = true
                            deleteFromPlaylist()
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) {
                            processedDismiss = false
                        }
                    }

                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(song.map.id)
                        } else {
                            selection.remove(Integer.valueOf(song.map.id))
                        }
                    }

                    val content: @Composable () -> Unit = {
                        SongListItem(
                            song = song.song,
                            isActive = song.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = selection.contains(song.map.id),
                                        onCheckedChange = onCheckedChange,
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }

                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(!selection.contains(song.map.id))
                                            } else if (song.song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist!!.playlist.name,
                                                        items = songs.map { it.song.toMediaItem() },
                                                        startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                inSelectMode = true
                                                onCheckedChange(true)
                                                selectionAnchorMapId = song.map.id
                                            } else {
                                                val anchorIndex =
                                                    selectionAnchorMapId?.let { anchorMapId ->
                                                        displayedSongs.indexOfFirst { it.map.id == anchorMapId }
                                                    } ?: -1

                                                if (anchorIndex == -1) {
                                                    onCheckedChange(true)
                                                    selectionAnchorMapId = song.map.id
                                                } else {
                                                    val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                    for (rangeIndex in range) {
                                                        val rangeMapId = displayedSongs[rangeIndex].map.id
                                                        if (rangeMapId !in selection) {
                                                            selection.add(rangeMapId)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    ),
                        )
                    }

                    if (locked || inSelectMode || !swipeRemoveEnabled) {
                        Box(modifier = Modifier.animateItem()) {
                            content()
                        }
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {},
                            modifier = Modifier.animateItem(),
                        ) {
                            content()
                        }
                    }
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 2,
        )

        TopAppBar(
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                if (inSelectMode) {
                    IconButton(onClick = onExitSelectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == songs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(songs.map { it.map.id })
                            }
                        },
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        selection.mapNotNull { mapId ->
                                            songs.find { it.map.id == mapId }?.song
                                        },
                                    songPosition =
                                        selection.mapNotNull { mapId ->
                                            songs.find { it.map.id == mapId }?.map
                                        },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    // Only search button remains in TopAppBar
                    IconButton(
                        onClick = { isSearching = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsToLocalPlaylistDialog(playlist: Playlist, onDismiss: () -> Unit) {
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val glass = LocalLiquidGlass.current
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var addingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var added by remember { mutableStateOf("") }
    var addedIds by remember { mutableStateOf(emptySet<String>()) }
    var undoMap by remember { mutableStateOf<PlaylistSongMap?>(null) }
    val categories = listOf("Lagu", "Playlist", "Artis", "Album", "Episode", "Podcast")
    val categoryIcons = listOf(R.drawable.music_note, R.drawable.playlist_play, R.drawable.person, R.drawable.album, R.drawable.play, R.drawable.radio)
    val filters = listOf(
        YouTube.SearchFilter.FILTER_SONG,
        YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
        YouTube.SearchFilter.FILTER_ARTIST,
        YouTube.SearchFilter.FILTER_ALBUM,
        YouTube.SearchFilter.FILTER_EPISODE,
        YouTube.SearchFilter.FILTER_PODCAST,
    )
    LaunchedEffect(query, category) {
        val typedQuery = query.trim()
        if (typedQuery.isNotEmpty() && typedQuery.length < 2) { results = emptyList(); return@LaunchedEffect }
        if (typedQuery.isNotEmpty()) delay(350)
        loading = true
        error = ""
        val searchTerm = typedQuery.ifBlank { "lagu populer Indonesia" }
        val found = mutableListOf<YTItem>()
        YouTube.search(searchTerm, filters[category]).onSuccess { response ->
            found += response.items
        }.onFailure { error = it.message ?: "Pencarian gagal. Coba lagi." }
        if (category == 1) {
            YouTube.search(searchTerm, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).onSuccess { response ->
                found += response.items
            }.onFailure { if (found.isEmpty()) error = it.message ?: "Pencarian playlist gagal." }
        }
        results = found.distinctBy { it.id }.let { if (typedQuery.isEmpty()) it.take(18) else it }
        loading = false
    }
    fun subtitle(item: YTItem): String = when (item) {
        is SongItem -> "Lagu · ${item.artists.joinToString(", ") { it.name }}${if (item.isVideoSong) " · Video" else ""}"
        is EpisodeItem -> "Episode · ${item.author?.name.orEmpty()}"
        is AlbumItem -> "Album · ${item.artists.orEmpty().joinToString(", ") { it.name }}"
        is ArtistItem -> "Artis"
        is PlaylistItem -> "Playlist · ${item.author?.name.orEmpty()}"
        is PodcastItem -> "Podcast · ${item.author?.name.orEmpty()}"
    }
    fun openItem(item: YTItem) {
        when (item) {
            is SongItem -> Unit
            is EpisodeItem -> Unit
            is AlbumItem -> { onDismiss(); navController.navigate("album/${item.id}") }
            is ArtistItem -> { onDismiss(); navController.navigate("artist/${item.id}") }
            is PlaylistItem -> { onDismiss(); navController.navigate("online_playlist/${item.id}") }
            is PodcastItem -> { onDismiss(); navController.navigate("online_podcast/${item.id}") }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassWindowBlur()
        Surface(modifier = Modifier.fillMaxSize().padding(8.dp), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = if (glass) 0.96f else 1f), tonalElevation = 5.dp) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.arrow_back), contentDescription = "Kembali") }
                    Column(Modifier.weight(1f)) {
                        Text("Temukan musik", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(playlist.playlist.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEachIndexed { index, label ->
                        FilterChip(
                            selected = category == index,
                            onClick = { category = index },
                            label = { Text(label) },
                            leadingIcon = { Icon(painterResource(categoryIcons[index]), contentDescription = null, modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(50.dp),
                        )
                    }
                }
                Text(if (query.isBlank()) "Direkomendasikan untuk ditambahkan" else "Hasil pencarian · ${categories[category]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp, top = 6.dp, bottom = 7.dp))
                if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp), strokeWidth = 2.dp)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                if (added.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1DB954).copy(alpha = 0.13f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.check), contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(18.dp))
                        Text(added, color = Color(0xFF1DB954), modifier = Modifier.weight(1f).padding(start = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = {
                            val map = undoMap ?: return@TextButton
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    database.transaction {
                                        move(map.playlistId, map.position, Int.MAX_VALUE)
                                        delete(map.copy(position = Int.MAX_VALUE))
                                    }
                                }
                                addedIds = addedIds - map.songId
                                added = ""
                                undoMap = null
                            }
                        }) { Text("Urungkan", color = Color(0xFF1DB954)) }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    itemsIndexed(results, key = { _, item -> "${category}-${item.id}" }) { _, item ->
                        val shape = RoundedCornerShape(20.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glass) 0.72f else 1f)).glassBorder(shape, glass).clickable { openItem(item) }.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)))
                            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            when (item) {
                                is SongItem -> PlaylistAddActionButton(
                                    added = item.id in addedIds,
                                    loading = addingId == item.id,
                                    enabled = addingId == null && item.id !in addedIds,
                                    onClick = {
                                        scope.launch {
                                            addingId = item.id
                                            try {
                                                val insertedMap = withContext(Dispatchers.IO) {
                                                    database.insert(item.toMediaMetadata())
                                                    database.addSongsToPlaylist(playlist, listOf(item.id to item.setVideoId))
                                                    database.playlistSongMaps(item.id).firstOrNull { it.playlistId == playlist.playlist.id }
                                                }
                                                undoMap = insertedMap
                                                addedIds = addedIds + item.id
                                                added = "Ditambahkan · ${item.title}"
                                            } catch (e: Exception) { error = e.message ?: "Lagu gagal ditambahkan." }
                                            addingId = null
                                        }
                                    },
                                )
                                is EpisodeItem -> PlaylistAddActionButton(
                                    added = item.id in addedIds,
                                    loading = addingId == item.id,
                                    enabled = addingId == null && item.id !in addedIds,
                                    onClick = {
                                        scope.launch {
                                            addingId = item.id
                                            try {
                                                val insertedMap = withContext(Dispatchers.IO) {
                                                    database.insert(item.toMediaMetadata())
                                                    database.addSongsToPlaylist(playlist, listOf(item.id to null))
                                                    database.playlistSongMaps(item.id).firstOrNull { it.playlistId == playlist.playlist.id }
                                                }
                                                undoMap = insertedMap
                                                addedIds = addedIds + item.id
                                                added = "Episode ditambahkan · ${item.title}"
                                            } catch (e: Exception) { error = e.message ?: "Episode gagal ditambahkan." }
                                            addingId = null
                                        }
                                    },
                                )
                                else -> Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                    if (!loading && results.isEmpty() && error.isBlank()) item { Text(if (query.isBlank()) "Rekomendasi belum tersedia. Coba cari musik atau artis." else "Tidak ada hasil. Coba kata kunci lain.", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; added = "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(50.dp),
                    leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.close), contentDescription = "Hapus pencarian") } },
                    placeholder = { Text("Apa yang ingin kamu tambahkan?") },
                )
            }
        }
    }
}

@Composable
private fun PlaylistAddActionButton(
    added: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val green = Color(0xFF1DB954)
    val containerColor by animateColorAsState(
        targetValue = if (added) green else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(durationMillis = 260),
        label = "playlist-add-color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (added) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 260),
        label = "playlist-add-content-color",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.pressScale(0.94f),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (added) green else MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = if (added) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        AnimatedContent(
            targetState = if (loading) 2 else if (added) 1 else 0,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.90f, animationSpec = tween(150)))
            },
            label = "playlist-add-action",
        ) { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    2 -> CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = contentColor)
                    1 -> Icon(painterResource(R.drawable.check), contentDescription = null, modifier = Modifier.size(17.dp))
                    else -> Icon(painterResource(R.drawable.playlist_add_rounded), contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = when (state) { 2 -> "Menambahkan"; 1 -> "Ditambahkan"; else -> "Tambah" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onlinePlaylist: PlaylistItem?,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit,
    onStartSearch: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    val editPlaylistCoverStr = stringResource(R.string.edit_playlist_cover)
    val playlistSyncedStr = stringResource(R.string.playlist_synced)

    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val liked = playlist.playlist.bookmarkedAt != null
    val editable: Boolean = playlist.playlist.isEditable

    val overrideThumbnail = remember { mutableStateOf<String?>(null) }
    var isCustomThumbnail: Boolean =
        playlist.thumbnails.firstOrNull()?.let {
            it.contains("studio_square_thumbnail") || it.contains("content://com.metrolist.music")
        } ?: false

    val result = remember { mutableStateOf<Uri?>(null) }
    var pendingCropDestUri by remember { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
                if (output != null) result.value = output
            }
        }

    val (darkMode, _) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )

    val cropColor = MaterialTheme.colorScheme
    val darkTheme = darkMode == DarkMode.ON || (darkMode == DarkMode.AUTO && isSystemInDarkTheme())

    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let { sourceUri ->
                val destFile = java.io.File(context.cacheDir, "playlist_cover_crop_${System.currentTimeMillis()}.jpg")
                val destUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
                pendingCropDestUri = destUri

                val options =
                    UCrop.Options().apply {
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                        setCompressionQuality(90)
                        setHideBottomControls(true)
                        setToolbarTitle(editPlaylistCoverStr)

                        setStatusBarLight(!darkTheme)

                        setToolbarColor(cropColor.surface.toArgb())
                        setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                        setRootViewBackgroundColor(cropColor.surface.toArgb())
                        setLogoColor(cropColor.surface.toArgb())
                    }

                val intent =
                    UCrop
                        .of(sourceUri, destUri)
                        .withAspectRatio(1f, 1f)
                        .withOptions(options)
                        .getIntent(context)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cropLauncher.launch(intent)
            }
        }

    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when {
                playlist.playlist.browseId == null -> {
                    overrideThumbnail.value = uri.toString()
                    isCustomThumbnail = true

                    // Update the database with the new thumbnail
                    database.query {
                        update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                    }
                }

                else -> {
                    val bytes = uriToByteArray(context, uri)
                    YouTube
                        .uploadCustomThumbnailLink(
                            playlist.playlist.browseId,
                            bytes!!,
                        ).onSuccess { newThumbnailUrl ->
                            overrideThumbnail.value = newThumbnailUrl
                            isCustomThumbnail = true

                            // Update the database with the new thumbnail URL
                            database.query {
                                update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                            }
                        }.onFailure {
                            if (it is ClientRequestException) {
                                snackbarHostState.showSnackbar("${it.response.status.value} ${it.response.status.description}")
                            }
                            reportException(it)
                        }
                }
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showEditNoteDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.edit_playlist_cover),
                onDismiss = { showEditNoteDialog = false },
                onConfirm = {
                    showEditNoteDialog = false
                    pickLauncher.launch(
                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCancel = { showEditNoteDialog = false },
            ) {
                if (playlist.playlist.browseId != null) {
                    Text(
                        text = stringResource(R.string.edit_playlist_cover_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        // Playlist Thumbnail(s) - Large centered with shadow
        Box(
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        ) {
            when (playlist.thumbnails.size) {
                0 -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = RoundedCornerShape(3.dp),
                                ),
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                1 -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(3.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        AsyncImage(
                            model = overrideThumbnail.value ?: playlist.thumbnails[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (editable) {
                        OverlayEditButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                            onClick = {
                                if (isCustomThumbnail) {
                                    menuState.show(
                                        {
                                            CustomThumbnailMenu(
                                                onEdit = {
                                                    pickLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                        ),
                                                    )
                                                },
                                                onRemove = {
                                                    when {
                                                        playlist.playlist.browseId == null -> {
                                                            overrideThumbnail.value = null
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = null))
                                                            }
                                                        }

                                                        else -> {
                                                            scope.launch(Dispatchers.IO) {
                                                                YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl ->
                                                                    overrideThumbnail.value = newThumbnailUrl
                                                                    database.query {
                                                                        update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    isCustomThumbnail = false
                                                },
                                                onDismiss = menuState::dismiss,
                                            )
                                        },
                                    )
                                } else {
                                    showEditNoteDialog = true
                                }
                            },
                        )
                    }
                }

                else -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(3.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            listOf(
                                Alignment.TopStart,
                                Alignment.TopEnd,
                                Alignment.BottomStart,
                                Alignment.BottomEnd,
                            ).fastForEachIndexed { index, alignment ->
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(index),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .align(alignment)
                                            .size(120.dp),
                                )
                            }
                        }
                    }
                    if (editable) {
                        OverlayEditButton(
                            visible = true,
                            alignment = Alignment.BottomEnd,
                            onClick = {
                                if (isCustomThumbnail) {
                                    menuState.show(
                                        {
                                            CustomThumbnailMenu(
                                                onEdit = {
                                                    pickLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                        ),
                                                    )
                                                },
                                                onRemove = {
                                                    when {
                                                        playlist.playlist.browseId == null -> {
                                                            overrideThumbnail.value = null
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = null))
                                                            }
                                                        }

                                                        else -> {
                                                            scope.launch(Dispatchers.IO) {
                                                                YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl ->
                                                                    overrideThumbnail.value = newThumbnailUrl
                                                                    database.query {
                                                                        update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    isCustomThumbnail = false
                                                },
                                                onDismiss = menuState::dismiss,
                                            )
                                        },
                                    )
                                } else {
                                    showEditNoteDialog = true
                                }
                            },
                        )
                    }
                }
            }
        }

        // Playlist Name
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata - Song Count • Duration
        val songCount =
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                playlist.playlist.remoteSongCount
            } else {
                playlist.songCount
            }
        val nSongs = pluralStringResource(R.plurals.n_song, songCount, songCount)
        val durationText = if (playlistLength > 0) makeTimeString(playlistLength * 1000L) else null
        val metadataString = buildString {
            append(nSongs)
            if (durationText != null) {
                append(" ")
                append(durationText)
            }
        }
        Text(
            text = metadataString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        val onlineAuthor = onlinePlaylist?.author
        if (onlineAuthor != null) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (onlineAuthor.id != null) {
                                navController.navigate("artist/${onlineAuthor.id}")
                            }
                        },
                    ),
            ) {
                if (onlinePlaylist.authorAvatarUrl != null) {
                    AsyncImage(
                        model = onlinePlaylist.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                    )
                }
                Text(
                    text = onlineAuthor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val description = onlinePlaylist?.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle Button - Smaller secondary button
            Surface(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.shuffled().map { it.song.toMediaItem() },
                        ),
                    )
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Play Button - Larger primary circular button
            Surface(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaItem() },
                        ),
                    )
                },
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Menu Button - Smaller secondary button
            Surface(
                onClick = {
                    menuState.show {
                        LocalPlaylistMenu(
                            playlist = playlist,
                            songs = songs,
                            context = context,
                            downloadState = downloadState,
                            onEdit = onShowEditDialog,
                            onSync = {
                                scope.launch(Dispatchers.IO) {
                                    syncUtils.syncPlaylistSuspend(
                                        playlist.playlist.browseId!!,
                                        playlist.id,
                                    )
                                    withContext(Dispatchers.Main) {
                                        snackbarHostState.showSnackbar(playlistSyncedStr)
                                    }
                                }
                            },
                            onDelete = onshowDeletePlaylistDialog,
                            onDownload = {
                                when (downloadState) {
                                    Download.STATE_COMPLETED -> {
                                        onShowRemoveDownloadDialog()
                                    }

                                    Download.STATE_DOWNLOADING -> {
                                        songs.forEach { song ->
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                song.song.id,
                                                false,
                                            )
                                        }
                                    }

                                    else -> {
                                        songs.forEach { song ->
                                            downloadUtil.download(song.song)
                                        }
                                    }
                                }
                            },
                            onQueue = {
                                playerConnection.addToQueue(
                                    items = songs.map { it.song.toMediaItem() },
                                )
                            },
                            onDismiss = { menuState.dismiss() },
                        )
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

fun uriToByteArray(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: SecurityException) {
        null
    }
