package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test
    fun parsesKmpReleaseArtifact() {
        val response =
            """
            {
              "tag_name": "v1.2.3",
              "body": null,
              "published_at": "2026-09-05T12:00:00Z",
              "assets": [{
                "name": "Metrolist.apk",
                "browser_download_url": "https://example.com/Metrolist.apk",
                "size": 42
              }]
            }
            """.trimIndent()
        val release = checkNotNull(Updater.parseKmpRelease(response))

        assertEquals("1.2.3", release.versionName)
        assertEquals("", release.description)
        assertEquals("https://example.com/Metrolist.apk", release.assets.single().downloadUrl)
        assertNull(Updater.parseKmpRelease(response.replace("Metrolist.apk", "Metrolist-with-Google-Cast.apk")))
    }

    @Test
    fun parsesReleaseWhenTagNameIsMissingButNameExists() {
        val response =
            """
            {
              "name": "1.2.4",
              "body": "Perbaikan dan peningkatan.",
              "published_at": "2026-10-27T12:00:00Z",
              "assets": [{
                "name": "Metrolist.apk",
                "browser_download_url": "https://example.com/Metrolist.apk",
                "size": 42
              }]
            }
            """.trimIndent()

        val release = checkNotNull(Updater.parseKmpRelease(response))
        assertEquals("1.2.4", release.tagName)
        assertEquals("1.2.4", release.versionName)
    }

    @Test
    fun ignoresErrorPayloadWithoutTagAndName() {
        assertNull(Updater.parseKmpRelease("""{"message":"Not Found","assets":[]}"""))
    }
}
