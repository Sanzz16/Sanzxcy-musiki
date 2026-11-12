package com.metrolist.music.utils

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMusicArchiveTest {
    private val song = ArchiveSong(
        id = "song_123",
        title = "Contoh lagu",
        duration = 180_000,
        size = 4096,
        sha256 = "a".repeat(64),
    )

    @Test
    fun acceptsManifestWithLocalPlaylist() {
        val manifest = ArchiveManifest(
            songs = listOf(song),
            playlists = listOf(ArchivePlaylist("Offline", listOf(song.id))),
        )
        validateOfflineArchiveManifest(manifest)
        validateOfflineArchiveEntryPath("audio/song_123.bin")
        assertTrue(manifest.playlists.single().songIds.contains(song.id))
    }

    @Test
    fun rejectsUnsafeAndDuplicateArchiveEntries() {
        assertThrows(IllegalArgumentException::class.java) { validateOfflineArchiveEntryPath("../manifest.json") }
        assertThrows(IllegalArgumentException::class.java) { validateOfflineArchiveEntryPath("audio/../song.bin") }
        assertThrows(IllegalArgumentException::class.java) { validateOfflineArchiveEntryPath("audio\\song.bin") }
    }

    @Test
    fun rejectsPlaylistReferencesOutsideExportedAudioSet() {
        val manifest = ArchiveManifest(
            songs = listOf(song),
            playlists = listOf(ArchivePlaylist("Offline", listOf("missing_song"))),
        )
        assertThrows(IllegalArgumentException::class.java) { validateOfflineArchiveManifest(manifest) }
    }
}
