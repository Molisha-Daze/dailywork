package io.github.molishadaze.weijing

import android.app.Application
import io.github.molishadaze.weijing.data.repository.HabitRepository
import io.github.molishadaze.weijing.notification.NotificationHelper
import io.github.molishadaze.weijing.util.Haptics

class HabitApplication : Application() {
    val repository: HabitRepository by lazy {
        HabitRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        // 触觉反馈引擎必须在这里初始化：它不依赖任何 Activity，
        // 通知栏「标记完成」的广播接收器也要用它（那种场景下界面根本没起来）。
        Haptics.attach(this)
    }
}
