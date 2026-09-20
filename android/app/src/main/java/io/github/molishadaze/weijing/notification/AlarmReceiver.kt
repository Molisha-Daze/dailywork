package io.github.molishadaze.weijing.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.molishadaze.weijing.data.HabitDatabase
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.HabitSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(NotificationHelper.EXTRA_HABIT_ID, -1L)
        if (habitId <= 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = HabitDatabase.getInstance(context)
                val habit = db.habitDao().getHabitById(habitId)

                // 习惯已被删除 / 已归档 / 已取消提醒：不再打扰，也不再续排
                if (habit == null || habit.archived || habit.reminderTime == null) {
                    NotificationHelper.cancelReminder(context, habitId)
                    return@launch
                }

                // 用户今天已经完成过了，或者今天根本没有排期（如「每周一三五」），就别再弹提醒
                val today = DateUtils.today()
                val todayCheckIn = db.checkInDao().getCheckIn(habitId, today)
                val shouldNotify = HabitSchedule.isScheduled(habit, DateUtils.todayDate()) &&
                    !HabitSchedule.isCompleted(habit, todayCheckIn)
                if (shouldNotify) {
                    // 用数据库里的最新名称，而不是 intent 里那个可能已经过期的旧名字
                    NotificationHelper.showReminderNotification(context, habitId, habit.name)
                }

                // Reschedule for tomorrow at the same time
                NotificationHelper.scheduleDailyReminder(context, habit)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
