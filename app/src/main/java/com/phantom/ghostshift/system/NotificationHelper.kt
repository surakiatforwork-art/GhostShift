package com.phantom.ghostshift.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phantom.ghostshift.MainActivity
import com.phantom.ghostshift.R

object NotificationHelper {
    private const val CHANNEL_GROUP_ID = "ghostshift_alarms"
    
    // Generates a channel ID based on the sound URI hash to ensure uniqueness
    fun getChannelId(soundUri: String?): String {
        return if (soundUri.isNullOrBlank()) {
            "channel_alarm_default"
        } else {
            "channel_alarm_${soundUri.hashCode()}_v1"
        }
    }

    fun ensureChannelExists(context: Context, soundUri: String?, silent: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channelId = if (silent) "channel_alarm_silent" else getChannelId(soundUri)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        
        if (manager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.channel_name_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_desc_alarm)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500) // Vibration pattern
            
            if (!silent && !soundUri.isNullOrBlank()) {
                val uri = Uri.parse(soundUri)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(uri, audioAttributes)
            }
        }
        manager.createNotificationChannel(channel)
    }

    fun showAlarmNotification(context: Context, title: String, body: String, soundUri: String?, silent: Boolean = false) {
        ensureChannelExists(context, soundUri, silent)
        val channelId = if (silent) "channel_alarm_silent" else getChannelId(soundUri)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // TODO: use vector icon if available
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        // For pre-Oero, set sound manually on builder logic? (Deprecated but compat)
        if (!silent && Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !soundUri.isNullOrBlank()) {
            builder.setSound(Uri.parse(soundUri))
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        }

        try {
            // Permission check happens at caller or we assume proper flow
            NotificationManagerCompat.from(context).notify(1001, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // Ongoing Notification for Background Reliability (Countdown)
    fun showOngoingNotification(context: Context, nextAt: Long, nextTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel("channel_ongoing") == null) {
                val channel = NotificationChannel(
                    "channel_ongoing",
                    "GhostShift Active Timer",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows active countdown to next photo"
                    setShowBadge(false)
                }
                manager?.createNotificationChannel(channel)
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
             context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, "channel_ongoing")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Timer Running")
            .setContentText("Next: $nextTag")
            .setWhen(nextAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            NotificationManagerCompat.from(context).notify(1002, builder.build())
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    fun cancelOngoingNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(1002)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
