package com.phantom.ghostshift.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.phantom.ghostshift.data.PhotoEntity
import com.phantom.ghostshift.domain.Kind

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nextTag = intent.getStringExtra("EXTRA_NEXT_TAG") ?: ""
        val soundUri = intent.getStringExtra("EXTRA_SOUND_URI")

        Log.d("AlarmReceiver", "Alarm Fired! Tag=$nextTag")

        // 1. Show Notification
        val title = if (nextTag.startsWith("OUT", ignoreCase = true)) {
            "ครบเวลา Check Out ได้แล้ว"
        } else {
            "ครบเวลา Check In ได้แล้ว"
        }
        val body = "Next: $nextTag"
        NotificationHelper.showAlarmNotification(context, title, body, soundUri)
        
        // 2. Headless Reschedule (Strict Rule A1)
        // Must run in background scope
        val goAsync = goAsync()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        
        scope.launch {
            try {
                // Dependency Injection manual retrieval
                // We assume GhostShiftApp has a container
                val app = context.applicationContext as com.phantom.ghostshift.GhostShiftApp
                val repo = app.container.photoRepository
                val prefs = app.container.userPreferences
                val alarmScheduler = AlarmScheduler(context)

                // Get fresh state
                // Note: repo.allPhotos is a flow. We need the current snapshot.
                // Assuming repo has a way to get snapshot or we take first from flow
                val allPhotos = repo.allPhotos.first() 
                val timerState = prefs.timerState.first()
                val (currentDueAt, currentTag, currentChannel) = prefs.alarmState.first() // snapshot current
                val currentSound = prefs.soundPref.first()

                if (!timerState.running) {
                    Log.d("AlarmReceiver", "Timer not running, skipping reschedule")
                    return@launch
                }
                
                // Re-run Schedule Calculation
                // Need to convert to SchedulePhoto for domain
                // We must sort same way as VM
                fun kindOrder(k: com.phantom.ghostshift.domain.Kind): Int = if (k == com.phantom.ghostshift.domain.Kind.IN) 0 else 1
                val sortedPhotos = allPhotos.sortedWith(
                    compareBy<com.phantom.ghostshift.data.PhotoEntity> { it.idx }.thenBy { kindOrder(it.kind) }
                ).map { p ->
                    com.phantom.ghostshift.domain.SchedulePhoto(
                        id = p.id,
                        tag = p.tag,
                        kind = p.kind,
                        idx = p.idx,
                        downloaded = p.downloaded,
                        downloadedAt = p.downloadedAt
                    )
                }

                // Calculate
                val result = com.phantom.ghostshift.domain.ScheduleCalculator.computeScheduleExactFit(sortedPhotos, timerState)

                if (result.ok && result.nextAt != null && result.nextTag != null) {
                    val nextAt = result.nextAt!!
                    val nTag = result.nextTag!!

                    // Gate check: Alarm only if gate is Open (at least 1 download)
                    // (Strict Rule T1 implies Gate logic for Alarms)
                    val gateOpen = sortedPhotos.any { it.downloadedAt != null }
                    
                    if (gateOpen) {
                        // Prevent scheduling past
                        if (nextAt > System.currentTimeMillis()) {
                             Log.d("AlarmReceiver", "Rescheduling Next: $nTag at $nextAt")
                             alarmScheduler.scheduleExact(nextAt, nTag, currentSound)
                             prefs.saveAlarmState(nextAt, nTag)
                        } else {
                            // If nextAt is now or past, we might be in a loop or falling behind.
                            // In strict exact fit, nextAt should be future. 
                            Log.w("AlarmReceiver", "Next schedule is in past? $nextAt vs ${System.currentTimeMillis()}")
                        }
                    } else {
                         Log.d("AlarmReceiver", "Gate closed (no exports), suppressing alarm")
                    }
                } else {
                    Log.d("AlarmReceiver", "Schedule calculation failed or finished: ${result.error}")
                }

            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Headless reschedule failed", e)
            } finally {
                goAsync.finish()
            }
        }
    }
}
