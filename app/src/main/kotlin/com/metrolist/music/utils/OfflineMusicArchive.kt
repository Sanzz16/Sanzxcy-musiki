package com.metrolist.music.utils

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.room.withTransaction
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val OFFLINE_ARCHIVE_FORMAT = "metrolist-offline-music"
private const val OFFLINE_ARCHIVE_VERSION = 1
private const val MAX_SONG_BYTES = 512L * 1024 * 1024

@Serializable
internal data class ArchiveManifest(
    val format: String = OFFLINE_ARCHIVE_FORMAT,
    val version: Int = OFFLINE_ARCHIVE_VERSION,
    val songs: List<ArchiveSong>,
    val playlists: List<ArchivePlaylist>,
)

@Serializable
internal data class ArchiveSong(
    val id: String,
    val title: String,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val size: Long,
    val sha256: String,
)

@Serializable
internal data class ArchivePlaylist(val name: String, val songIds: List<String>)

internal fun validateOfflineArchiveManifest(manifest: ArchiveManifest) {
    require(manifest.format == OFFLINE_ARCHIVE_FORMAT && manifest.version == OFFLINE_ARCHIVE_VERSION) { "Unsupported offline archive version" }
    require(manifest.songs.size <= 100_000 && manifest.playlists.size <= 10_000) { "Archive contains too many items" }
    require(manifest.songs.map { it.id }.distinct().size == manifest.songs.size) { "Duplicate song ID" }
    require(manifest.songs.all { song ->
        song.id.matches(Regex("[A-Za-z0-9_-]{1,128}") ) && song.title.isNotBlank() && song.title.length <= 2048 &&
            song.duration >= 0 && song.size in 1..(512L * 1024 * 1024) && song.sha256.matches(Regex("[a-f0-9]{64}")) &&
            (song.thumbnailUrl == null || (song.thumbnailUrl.startsWith("https://") && song.thumbnailUrl.length <= 4096))
    }) { "Invalid song metadata" }
    val ids = manifest.songs.mapTo(HashSet()) { it.id }
    require(manifest.playlists.sumOf { it.songIds.size.toLong() } <= 1_000_000L) { "Archive contains too many playlist entries" }
    require(manifest.playlists.all { playlist -> playlist.name.isNotBlank() && playlist.name.length <= 512 &&
        playlist.songIds.size <= 100_000 && playlist.songIds.all { it in ids } }) { "Invalid playlist metadata" }
    require(manifest.songs.sumOf { it.size } <= 20L * 1024 * 1024 * 1024) { "Archive audio size exceeds limit" }
}

internal fun validateOfflineArchiveEntryPath(path: String) {
    require(!path.startsWith('/') && '\\' !in path && path.split('/').none { it == ".." || it == "." } && path.length <= 180) {
        "Unsafe ZIP path"
    }
}

/** Offline-only archive, independent of the application's .backup format. */
class OfflineMusicArchive(
    private val context: Context,
    private val database: MusicDatabase,
    private val cache: Cache,
) {
    private val json = Json { ignoreUnknownKeys = false }

    suspend fun export(uri: Uri, progress: (Int) -> Unit) {
        val songs = database.downloadedSongEntitiesByNameAsc().filterNot { it.isEpisode }
        require(songs.size <= MAX_SONGS) { "Too many downloaded tracks" }
        val songIds = songs.mapTo(HashSet()) { it.id }
        val playlists = database.playlistEntitiesByNameAsc()
            .filter { it.isLocal && it.id !in RESERVED_PLAYLIST_IDS }
            .map { playlist ->
                ArchivePlaylist(playlist.name, database.playlistSongIds(playlist.id).filter { it in songIds })
            }
        val assets = songs.map { song ->
            val spans = contiguousSpans(song.id)
            val size = spans.sumOf { it.length }
            require(size in 1..MAX_SONG_BYTES) { "Invalid cached audio size for ${song.id}" }
            ArchiveSong(song.id, song.title, song.duration, song.thumbnailUrl, size, digest(spans))
        }
        val manifest = json.encodeToString(ArchiveManifest(songs = assets, playlists = playlists))
        require(manifest.toByteArray().size <= MAX_MANIFEST_BYTES) { "Archive manifest too large" }

        context.contentResolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                var done = 0L
                val total = assets.sumOf { it.size }.coerceAtLeast(1L)
                assets.forEach { asset ->
                    currentCoroutineContext().ensureActive()
                    zip.putNextEntry(ZipEntry(audioEntry(asset.id)))
                    val spans = contiguousSpans(asset.id)
                    spanStream(spans).use { input ->
                        copyChecked(input, zip, asset.size, null) { amount ->
                            done += amount
                            progress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Cannot open destination document")
        progress(100)
    }

    suspend fun import(uri: Uri, progress: (Int) -> Unit) {
        val staging = File(context.cacheDir, "offline-archive-${System.nanoTime()}").apply { mkdirs() }
        try {
            val manifest: ArchiveManifest
            val staged = mutableMapOf<String, File>()
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    val first = zip.nextEntry ?: error("Empty archive")
                    require(first.name == MANIFEST_ENTRY && !first.isDirectory) { "Manifest must be first" }
                    val manifestBytes = readLimited(zip, MAX_MANIFEST_BYTES)
                    manifest = json.decodeFromString(manifestBytes.toString(Charsets.UTF_8))
                    validateOfflineArchiveManifest(manifest)
                    zip.closeEntry()
                    val byPath = manifest.songs.associateBy { audioEntry(it.id) }
                    val expectedPaths = byPath.keys
                    val seen = HashSet<String>()
                    var processed = 0L
                    val total = manifest.songs.sumOf { it.size }.coerceAtLeast(1L)
                    require(staging.usableSpace >= total) { "Not enough temporary storage" }
                    var entry = zip.nextEntry
                    while (entry != null) {
                        currentCoroutineContext().ensureActive()
                        validateOfflineArchiveEntryPath(entry.name)
                        require(!entry.isDirectory && entry.name in expectedPaths && seen.add(entry.name)) {
                            "Unexpected or duplicate ZIP entry"
                        }
                        val song = requireNotNull(byPath[entry.name])
                        val file = File(staging, "${song.id}.audio")
                        val digest = MessageDigest.getInstance("SHA-256")
                        FileOutputStream(file).use { output ->
                            copyChecked(zip, output, song.size, digest) { count ->
                                processed += count
                                progress(((processed * 100) / total).toInt().coerceIn(0, 99))
                            }
                        }
                        require(hex(digest.digest()) == song.sha256) { "Audio hash mismatch for ${song.id}" }
                        staged[song.id] = file
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    require(seen == expectedPaths) { "Archive is missing audio entries" }
                }
            } ?: error("Cannot open archive")

            val targetSongs = manifest.songs.associateBy { it.id }
            val cacheWrites = mutableListOf<Pair<ArchiveSong, File>>()
            var cacheBytesNeeded = 0L
            manifest.songs.forEach { song ->
                val spans = cache.getCachedSpans(song.id).sortedBy { it.position }
                if (spans.isEmpty()) {
                    cacheWrites += song to requireNotNull(staged[song.id])
                    cacheBytesNeeded += song.size
                } else {
                    require(spans.first().position == 0L && spans.zipWithNext().all { (a, b) -> a.position + a.length == b.position } && spans.sumOf { it.length } == song.size) {
                        "Existing cache data conflicts with ${song.id}; no changes made"
                    }
                    require(digest(spans) == song.sha256) { "Existing cached audio differs for ${song.id}" }
                }
            }
            require(context.filesDir.usableSpace >= cacheBytesNeeded) { "Not enough cache storage" }

            val committed = mutableListOf<String>()
            try {
                cacheWrites.forEach { (song, file) ->
                    currentCoroutineContext().ensureActive()
                    val span = cache.startReadWrite(song.id, 0L, song.size)
                    require(!span.isCached) { "Cache changed during import for ${song.id}" }
                    try {
                        val cacheFile = cache.startFile(song.id, 0L, song.size)
                        try {
                            FileInputStream(file).use { input -> FileOutputStream(cacheFile).use { output ->
                                copyChecked(input, output, song.size, null) {}
                            } }
                            cache.commitFile(cacheFile, song.size)
                            committed += song.id
                        } catch (t: Throwable) {
                            cacheFile.delete()
                            throw t
                        }
                    } finally {
                        cache.releaseHoleSpan(span)
                    }
                }
                database.withTransaction {
                    manifest.songs.forEach { audio ->
                        val current = songEntity(audio.id)
                        if (current == null) {
                            insert(SongEntity(id = audio.id, title = audio.title, duration = audio.duration,
                                thumbnailUrl = audio.thumbnailUrl, isDownloaded = true, isLocal = true))
                        } else {
                            updateDownloadedInfo(audio.id, true, java.time.LocalDateTime.now())
                        }
                    }
                    manifest.playlists.forEach { archivePlaylist ->
                        val localId = PlaylistEntity.generatePlaylistId()
                        insert(PlaylistEntity(id = localId, name = archivePlaylist.name, isLocal = true))
                        archivePlaylist.songIds.forEachIndexed { index, songId ->
                            if (songId in targetSongs) insert(PlaylistSongMap(playlistId = localId, songId = songId, position = index))
                        }
                    }
                }
            } catch (t: Throwable) {
                committed.forEach { runCatching { cache.removeResource(it) } }
                throw t
            }
            progress(100)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun contiguousSpans(key: String): List<CacheSpan> {
        val spans = cache.getCachedSpans(key).sortedBy { it.position }
        require(spans.isNotEmpty() && spans.first().position == 0L) { "Downloaded audio missing for $key" }
        require(spans.zipWithNext().all { (a, b) -> a.position + a.length == b.position }) { "Cached audio has a gap for $key" }
        require(spans.all { it.file?.isFile == true }) { "Cached audio file missing for $key" }
        return spans
    }

    private suspend fun digest(spans: List<CacheSpan>): String = spanStream(spans).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        hex(digest.digest())
    }

    private fun spanStream(spans: List<CacheSpan>): InputStream = SequenceInputStream(Collections.enumeration(
        spans.map { FileInputStream(requireNotNull(it.file)) as InputStream }
    ))

    private suspend fun copyChecked(input: InputStream, output: java.io.OutputStream, expected: Long, digest: MessageDigest?, onBytes: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            require(copied <= expected) { "Entry exceeds declared size" }
            digest?.update(buffer, 0, count)
            output.write(buffer, 0, count)
            onBytes(count.toLong())
        }
        require(copied == expected) { "Entry size mismatch" }
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Manifest too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun audioEntry(id: String) = "audio/$id.bin"
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
        const val MAX_SONGS = 100_000
        const val MAX_PLAYLISTS = 10_000
        val RESERVED_PLAYLIST_IDS = setOf("LP_LIKED", "LP_DOWNLOADED", "LP_WEEKLY_MOST", "LP_MONTHLY_MOST")
    }
}
