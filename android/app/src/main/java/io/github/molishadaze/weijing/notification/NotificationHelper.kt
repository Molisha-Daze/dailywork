package io.github.molishadaze.weijing.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.molishadaze.weijing.MainActivity
import io.github.molishadaze.weijing.R
import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object NotificationHelper {
    const val CHANNEL_ID = "habit_daily_reminders"
    const val CHANNEL_NAME = "每日习惯提醒"
    const val EXTRA_HABIT_ID = "extra_habit_id"
    const val EXTRA_HABIT_NAME = "extra_habit_name"
    const val EXTRA_REMINDER_TIME = "extra_reminder_time"
    const val ACTION_CHECK_IN = "io.github.molishadaze.weijing.ACTION_CHECK_IN"

    // PendingIntent requestCode 与通知 ID 一律从这里生成。
    // 以前各文件裸写 habitId.toInt() / habitId.toInt() + 100000，改一处忘一处就会静默串台。
    private const val REQ_ALARM = 1_000_000
    private const val REQ_ACTION = 2_000_000
    private const val REQ_CONTENT = 3_000_000

    fun alarmRequestCode(habitId: Long): Int = REQ_ALARM + habitId.toInt()
    fun actionRequestCode(habitId: Long): Int = REQ_ACTION + habitId.toInt()
    fun contentRequestCode(habitId: Long): Int = REQ_CONTENT + habitId.toInt()
    fun notificationId(habitId: Long): Int = habitId.toInt()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "未竟的每日计划提醒，支持直接在通知中打卡"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 当前是否能使用精确闹钟。
     *
     * Android 12（API 31/32）的 SCHEDULE_EXACT_ALARM 默认授予，但用户可以在系统设置里关掉；
     * Android 13+ 同理。返回 false 时提醒会降级为非精确，应当把这个状态告诉用户。
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    /**
     * Schedules a daily alarm for a habit at the specified time (e.g. "08:30").
     *
     * @return 是否成功使用了**精确**闹钟；false 表示已降级为非精确（可能延迟数分钟），
     *         调用方应当把这个状态反馈到界面上，而不是静默吞掉。
     */
    fun scheduleDailyReminder(context: Context, habit: Habit): Boolean {
        val reminderTime = habit.reminderTime ?: return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false

        return try {
            val parts = reminderTime.split(":")
            if (parts.size != 2) return false
            val hour = parts[0].toIntOrNull() ?: return false
            val minute = parts[1].toIntOrNull() ?: return false

            val now = LocalDateTime.now(ZoneId.systemDefault())
            var targetDateTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute, 0))

            // If time has already passed today, schedule for tomorrow
            if (targetDateTime.isBefore(now) || targetDateTime.isEqual(now)) {
                targetDateTime = targetDateTime.plusDays(1)
            }

            val triggerMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(EXTRA_HABIT_ID, habit.id)
                putExtra(EXTRA_HABIT_NAME, habit.name)
                putExtra(EXTRA_REMINDER_TIME, reminderTime)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmRequestCode(habit.id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val useExact = canScheduleExactAlarms(context)
            if (useExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            useExact
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Cancels the scheduled reminder for a habit.
     */
    fun cancelReminder(context: Context, habitId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmRequestCode(habitId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /** 用户在 App 内完成打卡后，撤掉已经弹出来的那条提醒。 */
    fun cancelNotification(context: Context, habitId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(notificationId(habitId))
    }

    /**
     * Builds and displays the notification with action to check in directly.
     */
    fun showReminderNotification(context: Context, habitId: Long, habitName: String) {
        createNotificationChannel(context)

        // Open App Intent
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            contentRequestCode(habitId),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Check-in Action Intent
        val checkInIntent = Intent(context, CheckInActionReceiver::class.java).apply {
            action = ACTION_CHECK_IN
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_HABIT_NAME, habitName)
        }
        val checkInPendingIntent = PendingIntent.getBroadcast(
            context,
            actionRequestCode(habitId),
            checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("打卡提醒: $habitName")
            .setContentText("今天还没完成【$habitName】，坚持就是胜利！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "一键完成打卡",
                checkInPendingIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId(habitId), notification)
    }
}
