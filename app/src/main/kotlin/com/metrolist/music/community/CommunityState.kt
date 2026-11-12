/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.community

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first

internal val CommunityTokenKey = stringPreferencesKey("sanzxcyCommunityToken")
private val CommunityUsernameKey = stringPreferencesKey("sanzxcyCommunityUsername")
private val CommunityRulesSeenKey = booleanPreferencesKey("sanzxcyCommunityRulesSeen")
private val CommunityIntroSeenKey = booleanPreferencesKey("sanzxcyCommunityIntroSeen")

/** Sesi komunitas: token disimpan permanen di DataStore, profil sendiri disegarkan dari server. */
class CommunityState(
    private val context: Context,
) {
    var token by mutableStateOf<String?>(null)
        private set
    var me by mutableStateOf<UserDto?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var restoreError by mutableStateOf<String?>(null)
        private set
    var rememberedUsername by mutableStateOf("")
        private set
    var mustChangePassword by mutableStateOf(false)
    var blockedMessage by mutableStateOf<String?>(null)
    var rulesAcknowledged by mutableStateOf(false)
        private set
    var registrationIntroSeen by mutableStateOf(false)
        private set

    val loggedIn: Boolean get() = token != null && me != null
    val hasSavedSession: Boolean get() = !token.isNullOrBlank() && me == null

    suspend fun restore() {
        ready = false
        restoreError = null
        try {
            val prefs = context.dataStore.data.first()
            rulesAcknowledged = prefs[CommunityRulesSeenKey] ?: false
            registrationIntroSeen = prefs[CommunityIntroSeenKey] ?: false
            rememberedUsername = prefs[CommunityUsernameKey].orEmpty()
            val saved = prefs[CommunityTokenKey]
            if (saved.isNullOrBlank()) {
                token = null
                me = null
                CommunityAnnouncementWorker.cancel(context)
                return
            }
            ensureAnnouncementNotifications()
            val r = CommunityApi.me(saved)
            when {
                r.ok -> {
                    val restoredUser = parseUser(r.json.getJSONObject("user"))
                    token = saved
                    me = restoredUser
                    mustChangePassword = r.json.optBoolean("mustChange")
                }
                r.code == "unauthorized" -> clearToken()
                r.code == "banned" || r.code == "ip_banned" -> { blockedMessage = r.error; clearToken() }
                else -> {
                    // Jangan tampilkan formulir login seolah sesi hilang saat /me gagal sementara.
                    token = saved
                    me = null
                    restoreError = r.error.ifBlank { "Sesi tersimpan, tetapi profil belum dapat dimuat." }
                }
            }
        } catch (e: Exception) {
            restoreError = e.message ?: "Sesi komunitas belum dapat dipulihkan. Periksa koneksi lalu coba lagi."
        } finally {
            ready = true
        }
    }

    private suspend fun clearToken() {
        CommunityListenRoomSessions.clear(me?.username)
        token = null; me = null
        restoreError = null
        context.dataStore.edit { it.remove(CommunityTokenKey); it.remove(AnnouncementLastPollKey) }
        CommunityAnnouncementWorker.cancel(context)
    }

    private suspend fun saveToken(t: String) {
        context.dataStore.edit { it[CommunityTokenKey] = t }
        token = t
        restoreError = null
        ensureAnnouncementNotifications()
    }

    private suspend fun ensureAnnouncementNotifications(resetBaseline: Boolean = false) {
        context.dataStore.edit {
            if (resetBaseline || it[AnnouncementLastPollKey] == null) it[AnnouncementLastPollKey] = System.currentTimeMillis()
        }
        CommunityAnnouncementWorker.schedule(context)
    }

    suspend fun acknowledgeRules() {
        rulesAcknowledged = true
        context.dataStore.edit { it[CommunityRulesSeenKey] = true }
    }

    suspend fun acknowledgeRegistrationIntro() {
        registrationIntroSeen = true
        context.dataStore.edit { it[CommunityIntroSeenKey] = true }
    }

    suspend fun authenticate(register: Boolean, username: String, password: String): ApiResult {
        val r = if (register) CommunityApi.register(username, password) else CommunityApi.login(username, password)
        if (r.ok) {
            try {
                val authenticatedUser = parseUser(r.json.getJSONObject("user"))
                val authenticatedToken = r.json.getString("token")
                context.dataStore.edit {
                    it[CommunityTokenKey] = authenticatedToken
                    it[CommunityUsernameKey] = authenticatedUser.username
                    it[AnnouncementLastPollKey] = System.currentTimeMillis()
                }
                CommunityAnnouncementWorker.schedule(context)
                rememberedUsername = authenticatedUser.username
                token = authenticatedToken
                mustChangePassword = r.json.optBoolean("mustChange")
                me = authenticatedUser
                restoreError = null
            } catch (_: Exception) {
                return ApiResult(false, "storage", "Login berhasil, tetapi sesi belum bisa disimpan di perangkat. Coba lagi.")
            }
        } else if (r.code == "banned" || r.code == "ip_banned") {
            blockedMessage = r.error
        }
        return r
    }

    suspend fun logout() {
        val t = token
        // Hapus lokal dahulu; keluar akun tidak boleh bergantung pada jaringan.
        clearToken()
        if (t != null) runCatching { CommunityApi.logout(t) }
    }

    fun updateMe(user: UserDto) { me = user }

    suspend fun changePassword(old: String, new: String): ApiResult {
        val t = token ?: return ApiResult(false, "unauthorized", "Belum login")
        val r = CommunityApi.changePassword(t, old, new)
        if (r.ok) { saveToken(r.json.getString("token")); mustChangePassword = false }
        return r
    }

    suspend fun completePasswordReset(email: String, code: String, newPassword: String): ApiResult {
        val r = CommunityApi.resetPassword(email, code, newPassword)
        if (r.ok) {
            me = parseUser(r.json.getJSONObject("user"))
            mustChangePassword = false
            saveToken(r.json.getString("token"))
        }
        return r
    }

    /** Dipanggil saat server menjawab 401/403 (sesi habis, diblokir, dll). */
    suspend fun handleAuthFailure(r: ApiResult) {
        when (r.code) {
            "unauthorized" -> clearToken()
            "banned", "ip_banned" -> { blockedMessage = r.error; clearToken() }
        }
    }
}

@Composable
fun rememberCommunityState(): CommunityState {
    val context = LocalContext.current.applicationContext
    return remember { CommunityState(context) }
}
