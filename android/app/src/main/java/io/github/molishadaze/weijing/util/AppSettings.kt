package io.github.molishadaze.weijing.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 应用级偏好设置（目前只有界面字号）。
 *
 * 用 SharedPreferences + Listener 转 Flow，而不是数据库：
 * 字号属于设备本地的显示偏好，不属于用户数据，**不应该进备份文件**，
 * 也不该跟着数据迁移走。
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 界面字号缩放倍数，默认 1.0（标准）。 */
    val fontScale: Flow<Float> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_FONT_SCALE) {
                trySend(prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    /**
     * 触觉反馈（振动）开关，默认开启。
     *
     * 这里同时提供同步读 + Flow 两种形态，因为使用者分两类：
     * - [Haptics] 在每次振动前需要**同步**判定，等不及 Flow 发射；
     * - 管理中心的开关 UI 需要响应式刷新。
     */
    val hapticEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HAPTIC_ENABLED) {
                trySend(prefs.getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_HAPTIC_ENABLED))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_HAPTIC_ENABLED))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** 同步读取当前开关值，供 [Haptics] 这类非 Compose 调用方使用。 */
    fun isHapticEnabled(): Boolean = prefs.getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_HAPTIC_ENABLED)

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    /**
     * 示例数据是否已经播种过。
     *
     * 必须记这个标记，不能简单地「数据为空就播种」：那样用户一旦手动删完所有习惯，
     * 下次冷启动示例又会自己冒出来，删不掉。
     * 播种只发生在这台设备的第一次运行；之后即使清空数据也不会再自动插入，
     * 想再看示例走「计划清单」空状态里的显式按钮。
     */
    fun hasSeededSample(): Boolean = prefs.getBoolean(KEY_SAMPLE_SEEDED, false)

    fun markSampleSeeded() {
        prefs.edit().putBoolean(KEY_SAMPLE_SEEDED, true).apply()
    }

    enum class FontSize(val label: String, val scale: Float, val percent: String) {
        SMALL("小号", 0.875f, "87.5%"),
        NORMAL("标准", 1.0f, "100%"),
        LARGE("大号", 1.125f, "112.5%"),
        HUGE("特大", 1.25f, "125%")
    }

    companion object {
        /**
         * 故意公开：`Haptics` 需要直接监听同一个 SharedPreferences 文件，
         * 才能在任何线程、任何进程入口（含广播接收器）同步拿到开关的实时值。
         */
        const val PREF_NAME = "app_settings"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"

        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_SAMPLE_SEEDED = "sample_seeded"
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_HAPTIC_ENABLED = true

        /** 从当前缩放值反查对应的档位，找不到（例如旧数据 0.9）则归为标准。 */
        fun sizeOf(scale: Float): FontSize =
            FontSize.entries.firstOrNull { it.scale == scale } ?: FontSize.NORMAL
    }
}
