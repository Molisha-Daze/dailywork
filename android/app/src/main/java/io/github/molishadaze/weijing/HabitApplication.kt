package io.github.molishadaze.weijing

import android.app.Application
import io.github.molishadaze.weijing.data.repository.HabitRepository
import io.github.molishadaze.weijing.notification.NotificationHelper
import io.github.molishadaze.weijing.util.Haptics
import io.github.molishadaze.weijing.util.Sounds

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
        // 音效同理，但有一处关键差异：它在后台**不出声**。
        // Sounds 自己通过 ActivityLifecycleCallbacks 跟踪可见性，通知栏那条路径
        // 会被自动拦下 —— 振动保留（私密），声音不要（用户可能正在开会）。
        Sounds.attach(this)
    }
}
