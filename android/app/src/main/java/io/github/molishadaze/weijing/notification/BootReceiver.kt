package io.github.molishadaze.weijing.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.molishadaze.weijing.data.HabitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers all active habit daily reminders when the device reboots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = HabitDatabase.getInstance(context)
                    val habits = db.habitDao().getAllActiveHabitsList()
                    for (habit in habits) {
                        if (habit.reminderTime != null) {
                            NotificationHelper.scheduleDailyReminder(context, habit)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
