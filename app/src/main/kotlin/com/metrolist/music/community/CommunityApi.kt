/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: klien API komunitas (OkHttp + org.json, tanpa dependensi baru).
 */

package com.metrolist.music.community

import android.util.Base64
import com.sanzxcy.music.com.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ApiResult(
    val ok: Boolean,
    val code: String = "",
    val error: String = "",
    val json: JSONObject = JSONObject(),
    val httpStatus: Int = 0,
)

data class TitleDto(
    val text: String,
    val color: String,
    val shape: String,
    val icon: String,
)

data class UserDto(
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val bannerType: String,
    val role: String,
    val title: TitleDto?,
    val joinedAt: Long,
    val mutedUntil: Long = 0,
    val warns: Int = 0,
    val email: String = "",
    val emailVerified: Boolean = false,
    val avatarUrls: List<String> = emptyList(),
    val bannerUrls: List<String> = emptyList(),
    val lastSeen: Long = 0,
    val banned: Boolean = false,
    val mustChange: Boolean = false,
    val archivedAt: Long = 0,
    val lastIp: String = "",
    val ips: List<String> = emptyList(),
)

data class ReplySnapshot(
    val id: Long,
    val from: String,
    val displayName: String,
    val type: String,
    val text: String,
    val stickerUrl: String,
)

data class PollVote(val username: String, val option: Int)
data class ChatPoll(val question: String, val options: List<String>, val imageUrl: String, val votes: List<PollVote>, val closed: Boolean)

data class ChatMessage(
    val id: Long,
    val type: String, // text | sticker | notice | poll
    val from: String,
    val displayName: String,
    val avatarUrl: String,
    val role: String,
    val title: TitleDto?,
    val text: String,
    val stickerUrl: String,
    val ts: Long,
    val avatarUrls: List<String> = emptyList(),
    val reply: ReplySnapshot? = null,
    val expiresAt: Long = 0L,
    val poll: ChatPoll? = null,
)

data class BotModerationSettings(
    val rudeEnabled: Boolean = true,
    val linksEnabled: Boolean = true,
    val promotionEnabled: Boolean = true,
    val spamEnabled: Boolean = true,
    val extraBlockedWords: List<String> = emptyList(),
    val cooldownSeconds: Int = 40,
    val spamMuteSeconds: Int = 80,
    val warningLimit: Int = 3,
    val warningMuteMinutes: Int = 10080,
    val noticeSeconds: Int = 10,
)

data class DmItem(val room: String, val peer: UserDto, val last: ChatMessage?)

data class FriendInvite(
    val id: String,
    val from: String,
    val fromDisplayName: String,
    val to: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long,
)

fun parseFriendInvite(json: JSONObject) = FriendInvite(
    id = json.optString("id"),
    from = json.optString("from"),
    fromDisplayName = json.optString("fromDisplayName").ifBlank { json.optString("from") },
    to = json.optString("to"),
    status = json.optString("status", "pending"),
    createdAt = json.optLong("createdAt"),
    expiresAt = json.optLong("expiresAt"),
)

data class ListenState(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumb: String,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val serverTs: Long,
)

data class PublicListenRoom(
    val code: String,
    val hostName: String,
    val members: Int,
    val createdAt: Long,
    val transport: String = "websocket",
    val roomName: String = "",
    val wallpaperUrl: String = "",
    val bannerUrl: String = "",
)

data class ListenRoomMember(val username: String, val displayName: String, val avatarUrl: String)
data class ListenEntryRequest(val username: String, val displayName: String, val requestedAt: Long)
data class ListenSongRequest(val id: String, val username: String, val displayName: String, val videoId: String, val title: String, val artist: String, val requestedAt: Long, val status: String = "pending")

data class CommunityStatus(
    val id: String,
    val from: String,
    val displayName: String,
    val photoUrl: String,
    val caption: String,
    val createdAt: Long,
    val expiresAt: Long,
    val views: Int,
    val author: UserDto?,
)

internal fun parseCommunityStatus(o: JSONObject): CommunityStatus = CommunityStatus(
    id = o.str("id"),
    from = o.str("from"),
    displayName = o.str("displayName"),
    photoUrl = o.str("photoUrl"),
    caption = o.str("caption"),
    createdAt = normalizeEpochMillis(o.optLong("createdAt")),
    expiresAt = normalizeEpochMillis(o.optLong("expiresAt")),
    views = o.optInt("views"),
    author = o.optJSONObject("author")?.let(::parseUser),
)

fun roomUptimeText(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    if (createdAt <= 0L) return "durasi tidak diketahui"
    val totalMinutes = ((now - createdAt).coerceAtLeast(0L) / 60_000L)
    if (totalMinutes < 1L) return "baru saja"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    val days = hours / 24L
    return when {
        days > 0L -> "$days hari ${hours % 24L} jam"
        hours > 0L -> "$hours jam $minutes menit"
        else -> "$totalMinutes menit"
    }
}

data class AppStatus(
    val maintenanceEnabled: Boolean = false,
    val maintenanceMessage: String = "",
    val maintenanceEta: String = "",
    val updateEnabled: Boolean = false,
    val updateVersionName: String = "",
    val updateVersionCode: Long = 0,
    val updateMessage: String = "",
    val updateUrl: String = "",
    val changelogMd: String = "",
)


private fun JSONObject.str(k: String) = if (has(k) && !isNull(k)) optString(k, "") else ""
private fun JSONObject.strList(k: String): List<String> =
    optJSONArray(k)?.let { array -> (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.distinct() }.orEmpty()

internal fun parseTitle(o: JSONObject?): TitleDto? =
    if (o == null) null else TitleDto(o.str("text"), o.str("color").ifBlank { "#9ED7CB" }, o.str("shape").ifBlank { "pill" }, o.str("icon").ifBlank { "none" })

internal fun parseUser(o: JSONObject): UserDto =
    UserDto(
        username = o.str("username"),
        displayName = o.str("displayName").ifBlank { o.str("username") },
        bio = o.str("bio"),
        avatarUrl = o.str("avatarUrl"),
        bannerUrl = o.str("bannerUrl"),
        bannerType = o.str("bannerType").ifBlank { "image" },
        role = o.str("role").ifBlank { "user" },
        title = parseTitle(o.optJSONObject("title")),
        joinedAt = o.optLong("joinedAt", 0),
        mutedUntil = o.optLong("mutedUntil", 0),
        warns = o.optInt("warns", 0),
        email = o.str("email"),
        emailVerified = o.optBoolean("emailVerified", false),
        avatarUrls = o.strList("avatarUrls").ifEmpty { listOf(o.str("avatarUrl")).filter { it.isNotBlank() } },
        bannerUrls = o.strList("bannerUrls").ifEmpty { listOf(o.str("bannerUrl")).filter { it.isNotBlank() } },
        lastSeen = o.optLong("lastSeen", 0),
        banned = o.optBoolean("banned", false),
        mustChange = o.optBoolean("mustChange", false),
        archivedAt = o.optLong("archivedAt", 0),
        lastIp = o.str("lastIp"),
        ips = o.optJSONArray("ips")?.let { a -> (0 until a.length()).map { a.optJSONObject(it)?.str("ip").orEmpty() }.filter { it.isNotBlank() } }.orEmpty(),
    )

internal fun parseMessage(o: JSONObject): ChatMessage =
    ChatMessage(
        id = o.optLong("id"),
        type = o.str("type").ifBlank { "text" },
        from = o.str("from"),
        displayName = o.str("displayName").ifBlank { o.str("from") },
        avatarUrl = o.str("avatarUrl"),
        role = o.str("role").ifBlank { "user" },
        title = parseTitle(o.optJSONObject("title")),
        text = o.str("text"),
        stickerUrl = o.str("stickerUrl"),
        ts = normalizeEpochMillis(o.optLong("ts")),
        avatarUrls = o.strList("avatarUrls").ifEmpty { listOf(o.str("avatarUrl")).filter { it.isNotBlank() } },
        reply = o.optJSONObject("reply")?.let { r ->
            ReplySnapshot(
                id = r.optLong("id"),
                from = r.str("from"),
                displayName = r.str("displayName").ifBlank { r.str("from") },
                type = r.str("type").ifBlank { "text" },
                text = r.str("text"),
                stickerUrl = r.str("stickerUrl"),
            )
        },
        expiresAt = normalizeEpochMillis(o.optLong("expiresAt").takeIf { it > 0L } ?: if (o.str("type") == "notice") normalizeEpochMillis(o.optLong("ts")) + 10_000L else 0L),
        poll = o.optJSONObject("poll")?.let { p ->
            val options = p.optJSONArray("options")?.let { a -> (0 until a.length()).map { index -> a.optString(index) } }.orEmpty()
            val votes = p.optJSONArray("votes")?.let { a -> (0 until a.length()).mapNotNull { index ->
                a.optJSONObject(index)?.let { vote -> PollVote(vote.optString("user"), vote.optInt("option")) }
            } }.orEmpty()
            ChatPoll(p.optString("question"), options, p.optString("imageUrl"), votes, p.optBoolean("closed"))
        },
    )

internal fun normalizeEpochMillis(value: Long): Long = if (value in 1L..99_999_999_999L) value * 1000L else value

internal fun normalizeCommunityBaseUrl(value: String): String =
    value.trim().trimEnd('/').replace(Regex("/api$", RegexOption.IGNORE_CASE), "").trimEnd('/')

internal fun parseBotModerationSettings(o: JSONObject?): BotModerationSettings? {
    if (o == null) return null
    return BotModerationSettings(
        rudeEnabled = o.optBoolean("rudeEnabled", true),
        linksEnabled = o.optBoolean("linksEnabled", true),
        promotionEnabled = o.optBoolean("promotionEnabled", true),
        spamEnabled = o.optBoolean("spamEnabled", true),
        extraBlockedWords = o.strList("extraBlockedWords"),
        cooldownSeconds = o.optInt("cooldownSeconds", 40).coerceIn(5, 300),
        spamMuteSeconds = o.optInt("spamMuteSeconds", 80).coerceIn(10, 86400),
        warningLimit = o.optInt("warningLimit", 3).coerceIn(1, 10),
        warningMuteMinutes = o.optInt("warningMuteMinutes", 10080).coerceIn(1, 43200),
        noticeSeconds = o.optInt("noticeSeconds", 10).coerceIn(1, 60),
    )
}

internal fun parseListenState(o: JSONObject?): ListenState? {
    if (o == null) return null
    return ListenState(
        videoId = o.str("videoId"),
        title = o.str("title"),
        artist = o.str("artist"),
        thumb = o.str("thumb"),
        positionMs = o.optLong("positionMs"),
        durationMs = o.optLong("durationMs"),
        playing = o.optBoolean("playing"),
        serverTs = o.optLong("serverTs"),
    )
}

object CommunityApi {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    val baseUrl: String get() = normalizeCommunityBaseUrl(BuildConfig.SANZXCY_SERVER_URL)

    private suspend fun call(
        method: String,
        path: String,
        token: String?,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
    ): ApiResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/api/$path".toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
                val rb = Request.Builder().url(url)
                if (!token.isNullOrBlank()) rb.header("Authorization", "Bearer $token")
                when (method) {
                    "GET" -> rb.get()
                    "DELETE" -> rb.delete()
                    else -> rb.method(method, (body ?: JSONObject()).toString().toRequestBody(JSON))
                }
                client.newCall(rb.build()).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val j = runCatching { JSONObject(text) }.getOrNull()
                        ?: return@withContext ApiResult(false, "bad_response", "Respons server tidak valid (${resp.code}).", httpStatus = resp.code)
                    ApiResult(j.optBoolean("ok"), j.optString("code"), j.optString("error"), j, resp.code)
                }
            } catch (e: Exception) {
                ApiResult(false, "network", "Tidak bisa terhubung ke server. Periksa koneksi internet.")
            }
        }

    // ---- Auth ----
    suspend fun register(username: String, password: String) =
        call("POST", "auth", null, mapOf("action" to "register"), JSONObject().put("username", username).put("password", password))

    // Server production saat ini membaca "username"; versi terbaru juga menerima "identifier".
    suspend fun login(identifier: String, password: String) =
        call("POST", "auth", null, mapOf("action" to "login"), JSONObject().put("identifier", identifier).put("username", identifier).put("password", password))

    suspend fun me(token: String) = call("GET", "auth", token, mapOf("action" to "me"))
    suspend fun logout(token: String) = call("POST", "auth", token, mapOf("action" to "logout"))
    suspend fun changePassword(token: String, old: String, new: String) =
        call("POST", "auth", token, mapOf("action" to "password"), JSONObject().put("oldPassword", old).put("newPassword", new))
    suspend fun requestEmailCode(token: String, email: String) =
        call("POST", "auth", token, mapOf("action" to "email.request"), JSONObject().put("email", email))
    suspend fun verifyEmailCode(token: String, email: String, code: String) =
        call("POST", "auth", token, mapOf("action" to "email.verify"), JSONObject().put("email", email).put("code", code))
    suspend fun requestPasswordReset(email: String) =
        call("POST", "auth", null, mapOf("action" to "password.request"), JSONObject().put("email", email))
    suspend fun resetPassword(email: String, code: String, newPassword: String) =
        call("POST", "auth", null, mapOf("action" to "password.reset"), JSONObject().put("email", email).put("code", code).put("newPassword", newPassword))

    // ---- Profil ----
    suspend fun profile(token: String, username: String) = call("GET", "profile", token, mapOf("u" to username))
    suspend fun communityMembers(token: String) = call("GET", "profile", token, mapOf("action" to "members"))
    suspend fun invites(token: String) = call("GET", "invites", token)
    suspend fun createInvite(token: String, username: String) =
        call("POST", "invites", token, body = JSONObject().put("action", "create").put("username", username))
    suspend fun acceptInvite(token: String, id: String) =
        call("POST", "invites", token, body = JSONObject().put("action", "accept").put("id", id))
    suspend fun declineInvite(token: String, id: String) =
        call("POST", "invites", token, body = JSONObject().put("action", "decline").put("id", id))
    suspend fun searchUsers(token: String, q: String) = call("GET", "profile", token, mapOf("search" to q))
    suspend fun updateProfile(token: String, body: JSONObject) = call("POST", "profile", token, body = body)

    suspend fun upload(token: String, kind: String, contentType: String, bytes: ByteArray): ApiResult =
        call(
            "POST", "upload", token,
            body = JSONObject().put("kind", kind).put("contentType", contentType).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)),
        )

    // ---- Status komunitas ----
    suspend fun statuses(token: String) = call("GET", "statuses", token)
    suspend fun createStatus(token: String, photoUrl: String, caption: String) =
        call("POST", "statuses", token, body = JSONObject().put("action", "create").put("photoUrl", photoUrl).put("caption", caption))
    suspend fun viewStatus(token: String, id: String) =
        call("POST", "statuses", token, body = JSONObject().put("action", "view").put("id", id))
    suspend fun deleteStatus(token: String, id: String) = call("DELETE", "statuses", token, mapOf("id" to id))

    suspend fun announcements(token: String, since: Long = 0L) = call("GET", "announcements", token, mapOf("since" to since.toString()))
    suspend fun ownerAnnouncements(token: String) = ownerRead(token, "announcements.list")
    suspend fun ownerAnnouncementHistory(token: String, id: String) = ownerRead(token, "announcements.history", mapOf("id" to id))
    suspend fun saveAnnouncement(token: String, id: String?, title: String, description: String) =
        ownerWrite(
            token,
            if (id.isNullOrBlank()) "announcements.create" else "announcements.update",
            JSONObject().put("id", id ?: "").put("title", title).put("description", description),
        )
    suspend fun deleteAnnouncement(token: String, id: String) =
        ownerWrite(token, "announcements.delete", JSONObject().put("id", id))

    suspend fun submitFeedback(token: String, type: String, title: String, description: String, contentType: String?, bytes: ByteArray?): ApiResult {
        if (bytes != null && bytes.size > 2 * 1024 * 1024) return ApiResult(false, "media_too_large", "Lampiran akhir maksimal 2 MiB.")
        val body = JSONObject().put("type", type).put("title", title).put("description", description)
        if (bytes != null && contentType != null) body.put("contentType", contentType).put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
        return call("POST", "feedback", token, body = body)
    }

    suspend fun myFeedback(token: String) = call("GET", "feedback", token, mapOf("action" to "mine"))

    // ---- Chat ----
    suspend fun poll(token: String, room: String, after: Long) = call("GET", "chat", token, mapOf("room" to room, "after" to after.toString()))
    suspend fun send(token: String, room: String, text: String?, stickerUrl: String?, replyToId: Long? = null) =
        call(
            "POST", "chat", token,
            body = JSONObject().put("room", room).apply {
                if (stickerUrl != null) put("type", "sticker").put("stickerUrl", stickerUrl) else put("type", "text").put("text", text ?: "")
                replyToId?.let { put("replyToId", it) }
            },
        )
    suspend fun createPoll(token: String, question: String, options: List<String>, imageUrl: String = "") =
        call("POST", "chat", token, body = JSONObject().put("action", "createPoll").put("room", "global").put("question", question).put("options", JSONArray(options)).put("imageUrl", imageUrl))
    suspend fun votePoll(token: String, id: Long, option: Int) =
        call("POST", "chat", token, body = JSONObject().put("action", "votePoll").put("room", "global").put("id", id).put("option", option))
    suspend fun closePoll(token: String, id: Long) =
        call("POST", "chat", token, body = JSONObject().put("action", "closePoll").put("room", "global").put("id", id))
    suspend fun botModerationSettings(token: String) =
        call("GET", "owner", token, mapOf("action" to "bot.settings.get"))

    suspend fun setBotModerationSettings(token: String, settings: BotModerationSettings): ApiResult =
        call(
            "POST", "owner", token,
            body = JSONObject()
                .put("action", "bot.settings.set")
                .put(
                    "settings",
                    JSONObject()
                        .put("rudeEnabled", settings.rudeEnabled)
                        .put("linksEnabled", settings.linksEnabled)
                        .put("promotionEnabled", settings.promotionEnabled)
                        .put("spamEnabled", settings.spamEnabled)
                        .put("extraBlockedWords", JSONArray(settings.extraBlockedWords))
                        .put("cooldownSeconds", settings.cooldownSeconds)
                        .put("spamMuteSeconds", settings.spamMuteSeconds)
                        .put("warningLimit", settings.warningLimit)
                        .put("warningMuteMinutes", settings.warningMuteMinutes)
                        .put("noticeSeconds", settings.noticeSeconds),
                ),
        )

    // Manajemen user dari layar Moderasi Bot; server memvalidasi owner-only.
    suspend fun ownerUsers(token: String, query: String = "", archived: Boolean = false): ApiResult =
        call("GET", "owner", token, mapOf("action" to "users.search", "q" to query, "status" to if (archived) "archived" else "active"))

    suspend fun ownerUserAction(token: String, action: String, username: String, extra: JSONObject = JSONObject()): ApiResult =
        call("POST", "owner", token, body = JSONObject(extra.toString()).put("action", action).put("username", username))

    suspend fun ownerCreateUser(token: String, username: String, role: String): ApiResult =
        call("POST", "owner", token, body = JSONObject().put("action", "user.create").put("username", username).put("role", role))

    suspend fun ownerRead(token: String, action: String, query: Map<String, String> = emptyMap()): ApiResult =
        call("GET", "owner", token, mapOf("action" to action) + query)

    suspend fun ownerWrite(token: String, action: String, body: JSONObject = JSONObject()): ApiResult =
        call("POST", "owner", token, body = JSONObject(body.toString()).put("action", action))

    suspend fun remoteChangelog(): ApiResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(CHANGELOG_URL).get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) ApiResult(true, json = JSONObject().put("md", text))
                else ApiResult(false, "http_${response.code}", "Changelog GitHub gagal dimuat (${response.code}).")
            }
        } catch (_: Exception) {
            ApiResult(false, "network", "Tidak dapat terhubung ke GitHub.")
        }
    }

    suspend fun deleteMessage(token: String, room: String, id: Long) = call("DELETE", "chat", token, mapOf("room" to room, "id" to id.toString()))
    suspend fun pinMessage(token: String, room: String, id: Long) =
        call("POST", "chat", token, body = JSONObject().put("action", "pin").put("room", room).put("id", id))
    suspend fun unpinMessage(token: String, room: String) =
        call("POST", "chat", token, body = JSONObject().put("action", "unpin").put("room", room))
    suspend fun dms(token: String) = call("GET", "chat", token, mapOf("action" to "dms"))
    suspend fun dmRoom(token: String, peer: String) = call("GET", "chat", token, mapOf("action" to "room", "peer" to peer))

    // ---- Stiker ----
    suspend fun stickers(token: String) = call("GET", "stickers", token)
    suspend fun addSticker(token: String, url: String) = call("POST", "stickers", token, body = JSONObject().put("url", url))
    suspend fun removeSticker(token: String, url: String) = call("DELETE", "stickers", token, mapOf("url" to url))
    suspend fun favoriteSticker(token: String, url: String) =
        call("POST", "stickers", token, body = JSONObject().put("action", "favorite").put("url", url))
    suspend fun unfavoriteSticker(token: String, url: String) =
        call("DELETE", "stickers", token, mapOf("action" to "favorite", "url" to url))

    // ---- Dengar bersama ----
    private fun stateJson(s: ListenState) =
        JSONObject().put("videoId", s.videoId).put("title", s.title).put("artist", s.artist).put("thumb", s.thumb)
            .put("positionMs", s.positionMs).put("durationMs", s.durationMs).put("playing", s.playing)

    suspend fun listenCreate(token: String, s: ListenState, visibility: String = "private") =
        call("POST", "listen", token, mapOf("action" to "create"), JSONObject().put("state", stateJson(s)).put("visibility", visibility))
    suspend fun listenJoin(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "join"), JSONObject().put("code", code))
    suspend fun listenPush(token: String, code: String, s: ListenState) =
        call("POST", "listen", token, mapOf("action" to "push"), JSONObject().put("code", code).put("state", stateJson(s)))
    suspend fun listenUpdateRoom(token: String, code: String, roomName: String, bannerUrl: String, wallpaperUrl: String) =
        call("POST", "listen", token, mapOf("action" to "settings"), JSONObject().put("code", code).put("roomName", roomName).put("bannerUrl", bannerUrl).put("wallpaperUrl", wallpaperUrl))
    suspend fun listenUpdateRoom(token: String, code: String, roomName: String, wallpaperUrl: String) =
        call("POST", "listen", token, mapOf("action" to "settings"), JSONObject().put("code", code).put("roomName", roomName).put("wallpaperUrl", wallpaperUrl))
    suspend fun listenGet(token: String, code: String) = call("GET", "listen", token, mapOf("code" to code))
    suspend fun listenInviteInfo(code: String) = call("GET", "listen", null, mapOf("action" to "invite", "code" to code))
    suspend fun listenLeave(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "leave"), JSONObject().put("code", code))
    suspend fun listenMembers(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "members"), JSONObject().put("code", code))
    suspend fun listenEntryRequests(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "entryRequests"), JSONObject().put("code", code))
    suspend fun listenEntryDecision(token: String, code: String, username: String, accept: Boolean) =
        call("POST", "listen", token, mapOf("action" to "entryDecision"), JSONObject().put("code", code).put("username", username).put("accept", accept))
    suspend fun listenSongRequest(token: String, code: String, state: ListenState) =
        call("POST", "listen", token, mapOf("action" to "songRequest"), JSONObject().put("code", code).put("videoId", state.videoId).put("title", state.title).put("artist", state.artist))
    suspend fun listenSongRequestStatus(token: String, code: String, requestId: String) =
        call("POST", "listen", token, mapOf("action" to "songRequestStatus"), JSONObject().put("code", code).put("requestId", requestId))
    suspend fun listenSongRequests(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "songRequests"), JSONObject().put("code", code))
    suspend fun listenSongDecision(token: String, code: String, requestId: String, accept: Boolean) =
        call("POST", "listen", token, mapOf("action" to "songDecision"), JSONObject().put("code", code).put("requestId", requestId).put("accept", accept))
    suspend fun listenTransfer(token: String, code: String, username: String) =
        call("POST", "listen", token, mapOf("action" to "transfer"), JSONObject().put("code", code).put("username", username))

    // Public discovery can be filtered by transport; legacy callers keep the WS-only default.
    suspend fun listenPublicRooms(transport: String = "websocket") =
        call("GET", "listen", null, mapOf("action" to "list", "transport" to transport))
    suspend fun listenRegisterMetadata(token: String, code: String, visibility: String) =
        call("POST", "listen", token, mapOf("action" to "metadata"), JSONObject().put("code", code).put("visibility", visibility))
    suspend fun listenHeartbeat(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "heartbeat"), JSONObject().put("code", code))
    suspend fun listenSetVisibility(token: String, code: String, visibility: String) =
        call("POST", "listen", token, mapOf("action" to "visibility"), JSONObject().put("code", code).put("visibility", visibility))
    suspend fun listenCloseMetadata(token: String, code: String) =
        call("POST", "listen", token, mapOf("action" to "close"), JSONObject().put("code", code))

    // ---- Status aplikasi (publik) ----
    suspend fun appStatus(): AppStatus? {
        val r = call("GET", "app", null).let { primary ->
            if (primary.ok || primary.httpStatus !in setOf(404, 405)) primary else call("GET", "status", null)
        }
        if (!r.ok) return null
        val m = r.json.optJSONObject("maintenance")
        val u = r.json.optJSONObject("update")
        val c = r.json.optJSONObject("changelog")
        return AppStatus(
            maintenanceEnabled = m?.optBoolean("enabled") ?: false,
            maintenanceMessage = m?.optString("message").orEmpty(),
            maintenanceEta = m?.optString("eta").orEmpty(),
            updateEnabled = u?.optBoolean("enabled") ?: false,
            updateVersionName = u?.optString("versionName").orEmpty(),
            updateVersionCode = u?.optLong("versionCode") ?: 0,
            updateMessage = u?.optString("message").orEmpty(),
            updateUrl = u?.optString("downloadUrl").orEmpty(),
            changelogMd = c?.optString("md").orEmpty(),
        )
    }

    const val CHANGELOG_URL = "https://raw.githubusercontent.com/Sanzz16/Sanzxcy/main/changelog.md"

    /** Changelog dari GitHub milik developer; null bila gagal (aplikasi lalu memakai salinan server). */
    suspend fun githubChangelog(): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(CHANGELOG_URL).header("Cache-Control", "no-cache").build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string()?.takeIf { it.isNotBlank() } else null
                }
            }.getOrNull()
        }

    fun parseMessages(arr: JSONArray?): List<ChatMessage> =
        if (arr == null) emptyList() else (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.let(::parseMessage) }
            .filter { it.type != "notice" || it.expiresAt > System.currentTimeMillis() }
}
