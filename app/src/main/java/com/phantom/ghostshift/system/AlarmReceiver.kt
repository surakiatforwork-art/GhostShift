package com.phantom.ghostshift.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nextTag = intent.getStringExtra("EXTRA_NEXT_TAG") ?: ""
        val soundUri = intent.getStringExtra("EXTRA_SOUND_URI")

        Log.d("AlarmReceiver", "Alarm Fired! Tag=$nextTag")

        val title = if (nextTag.startsWith("OUT", ignoreCase = true)) {
            "ครบเวลา Check Out ได้แล้ว"
        } else {
            "ครบเวลา Check In ได้แล้ว"
        }
        val body = "Next: $nextTag"

        NotificationHelper.showAlarmNotification(context, title, body, soundUri)
    }
}
