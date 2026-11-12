package com.metrolist.music.community

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.MainActivity
import com.metrolist.music.utils.dataStore
import com.sanzxcy.music.com.R
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

internal val AnnouncementLastPollKey = longPreferencesKey("sanzxcyAnnouncementLastPollAt")
private val NotifiedInviteIdsKey = stringSetPreferencesKey("sanzxcyCommunityNotifiedInvites")

class CommunityAnnouncementWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        val token = prefs[CommunityTokenKey]?.takeIf { it.isNotBlank() } ?: return Result.success()
        val lastPoll = prefs[AnnouncementLastPollKey] ?: System.currentTimeMillis()
        val result = CommunityApi.announcements(token, lastPoll)
        if (!result.ok) {
            return if (result.httpStatus >= 500 || result.httpStatus == 0) Result.retry() else Result.success()
        }
        val items = result.json.optJSONArray("announcements")
        if (items != null) {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title").ifBlank { applicationContext.getString(R.string.community_announcement_title_default) }
                val description = item.optString("description")
                if (id.isNotBlank()) notifyAnnouncement(id, title, description)
            }
        }
        val inviteResult = CommunityApi.invites(token)
        if (inviteResult.ok) {
            val inviteArray = inviteResult.json.optJSONArray("invites")
            val seen = prefs[NotifiedInviteIdsKey].orEmpty().toMutableSet()
            if (inviteArray != null) for (index in 0 until inviteArray.length()) {
                val invite = inviteArray.optJSONObject(index) ?: continue
                val id = invite.optString("id")
                if (id.isBlank() || invite.optString("status") != "pending") continue
                if (seen.add(id)) {
                    notifyInvite(id, invite.optString("fromDisplayName").ifBlank { invite.optString("from") })
                }
            }
            if (seen.size > 100) seen.removeAll(seen.take(seen.size - 100).toSet())
            applicationContext.dataStore.edit { it[NotifiedInviteIdsKey] = seen }
        }
        applicationContext.dataStore.edit { it[AnnouncementLastPollKey] = result.json.optLong("serverTime", System.currentTimeMillis()) }
        return Result.success()
    }

    private fun notifyAnnouncement(id: String, title: String, description: String) {
        if (!canPostNotifications()) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.community_announcement_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notificationId = id.hashCode() and 0x7fffffff
        val openApp = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ANNOUNCEMENT_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(notificationId, notification) }
    }

    private fun notifyInvite(id: String, sender: String) {
        if (!canPostNotifications()) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.community_announcement_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notificationId = id.hashCode() and 0x7fffffff
        val openApp = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            Intent(applicationContext, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(applicationContext.getString(R.string.community_invite_notification_title, sender))
            .setContentText(applicationContext.getString(R.string.community_invite_notification_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, applicationContext.getString(R.string.community_invite_accept_action), inviteAction(id, InviteNotificationActionReceiver.ACTION_ACCEPT))
            .addAction(0, applicationContext.getString(R.string.community_invite_decline_action), inviteAction(id, InviteNotificationActionReceiver.ACTION_DECLINE))
        runCatching { NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build()) }
    }

    private fun inviteAction(id: String, action: String): PendingIntent {
        val requestCode = 31 * id.hashCode() + action.hashCode()
        val intent = Intent(applicationContext, InviteNotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(InviteNotificationActionReceiver.EXTRA_INVITE_ID, id)
        return PendingIntent.getBroadcast(
            applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "community_announcements"
        const val EXTRA_ANNOUNCEMENT_ID = "community_announcement_id"
        private const val WORK_NAME = "community_announcements_poll"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CommunityAnnouncementWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
