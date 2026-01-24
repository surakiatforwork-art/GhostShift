package com.phantom.ghostshift.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import com.phantom.ghostshift.GhostShiftApp
import com.phantom.ghostshift.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("RescheduleReceiver", "Reschedule event: $action")
        
        val goAsync = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        scope.launch {
            try {
                val app = context.applicationContext as GhostShiftApp
                val prefs = app.container.userPreferences
                val scheduler = AlarmScheduler(context)
                
                val (dueAt, nextTag, soundUri) = prefs.alarmState.first()
                // Use stored soundUri or null if not set
                
                if (dueAt != null && nextTag != null && dueAt > System.currentTimeMillis()) {
                     scheduler.scheduleExact(dueAt, nextTag, soundUri) 
                     Log.d("RescheduleReceiver", "Rescheduled $nextTag at $dueAt")
                }
            } catch (e: Exception) {
                Log.e("RescheduleReceiver", "Error rescheduling", e)
            } finally {
                goAsync.finish()
            }
        }
    }
}
