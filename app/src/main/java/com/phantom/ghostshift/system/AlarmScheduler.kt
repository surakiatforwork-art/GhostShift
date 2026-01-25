package com.phantom.ghostshift.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    fun scheduleExact(dueAtMs: Long, nextTag: String, soundUri: String?) {
        if (!canScheduleExactAlarms()) {
            Log.w("AlarmScheduler", "Exact alarm permission missing. Fallback not implemented fully yet (using WorkManager could be an option).")
            // In strict mode, we might just try setExact anyway and catch SecurityException 
            // but caller should check permission first.
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_NEXT_TAG", nextTag)
            putExtra("EXTRA_SOUND_URI", soundUri)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use setAlarmClock or setExactAndAllowWhileIdle
            // setAlarmClock is strongest for visibility (shows icon) but strict exact.
            // setExactAndAllowWhileIdle is good for background w/o icon.
            // JS version uses "Exact".
            // Let's use setExactAndAllowWhileIdle.
            
            // Ensure future
            if (dueAtMs <= System.currentTimeMillis()) return 

            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueAtMs,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Scheduled $nextTag at $dueAtMs")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Failed to schedule exact alarm", e)
        }
    }

    fun updateOngoing(dueAtMs: Long, nextTag: String) {
        NotificationHelper.showOngoingNotification(context, dueAtMs, nextTag)
    }

    fun cancel() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)
        NotificationHelper.cancelOngoingNotification(context)
    }

    companion object {
        const val REQUEST_CODE = 777
    }
}
