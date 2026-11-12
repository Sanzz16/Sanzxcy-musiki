package com.metrolist.music.community

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class InviteNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_INVITE_ID)?.takeIf { it.isNotBlank() } ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val token = appContext.dataStore.data.first()[CommunityTokenKey]
                if (token.isNullOrBlank()) return@launch
                val result = when (action) {
                    ACTION_ACCEPT -> CommunityApi.acceptInvite(token, id)
                    ACTION_DECLINE -> CommunityApi.declineInvite(token, id)
                    else -> return@launch
                }
                if (result.ok) NotificationManagerCompat.from(appContext).cancel(id.hashCode() and 0x7fffffff)
            } catch (_: Exception) {
                // Keep the request notification visible so the user can retry after a transient failure.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_INVITE_ID = "community_invite_id"
        const val ACTION_ACCEPT = "com.sanzxcy.music.community.ACCEPT_INVITE"
        const val ACTION_DECLINE = "com.sanzxcy.music.community.DECLINE_INVITE"
    }
}
