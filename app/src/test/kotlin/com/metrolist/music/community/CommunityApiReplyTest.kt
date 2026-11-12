package com.metrolist.music.community

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommunityApiReplyTest {
    @Test
    fun `older chat messages without reply remain parseable`() {
        val message = parseMessage(JSONObject("""
            {"id":12,"type":"text","from":"alice","displayName":"Alice","text":"hello"}
        """))

        assertEquals(12L, message.id)
        assertNull(message.reply)
    }

    @Test
    fun `reply snapshot is parsed without depending on original message`() {
        val message = parseMessage(JSONObject("""
            {
              "id":20,"type":"sticker","from":"bob","displayName":"Bob","stickerUrl":"https://example.test/sticker.png",
              "reply":{"id":17,"from":"alice","displayName":"Alice A.","type":"text","text":"Original text","stickerUrl":""}
            }
        """))

        assertEquals(
            ReplySnapshot(
                id = 17L,
                from = "alice",
                displayName = "Alice A.",
                type = "text",
                text = "Original text",
                stickerUrl = "",
            ),
            message.reply,
        )
    }

    @Test
    fun `bot moderation settings parse spam toggle and server defaults`() {
        val settings = parseBotModerationSettings(JSONObject("""
            {"rudeEnabled":true,"linksEnabled":false,"promotionEnabled":true,"spamEnabled":false,
             "extraBlockedWords":["bad"],"cooldownSeconds":40,"spamMuteSeconds":80,
             "warningLimit":3,"warningMuteMinutes":10080,"noticeSeconds":10}
        """))!!

        assertEquals(false, settings.spamEnabled)
        assertEquals(false, settings.linksEnabled)
        assertEquals(80, settings.spamMuteSeconds)
        assertEquals(listOf("bad"), settings.extraBlockedWords)
    }

    @Test
    fun `poll image URL is retained for rendering in the community feed`() {
        val message = parseMessage(JSONObject("""
            {"id":31,"type":"poll","from":"owner","poll":{"question":"Pilihan?","options":["A","B"],"imageUrl":"https://media.example.com/poll.jpg","votes":[],"closed":false}}
        """))

        assertEquals("https://media.example.com/poll.jpg", message.poll?.imageUrl)
    }

    @Test
    fun `server base accepts either origin or api prefix`() {
        assertEquals("https://community.example", normalizeCommunityBaseUrl(" https://community.example/api/ "))
        assertEquals("https://community.example", normalizeCommunityBaseUrl("https://community.example/"))
    }
}
