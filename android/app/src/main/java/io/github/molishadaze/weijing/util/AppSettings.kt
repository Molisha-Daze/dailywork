package io.github.molishadaze.weijing.util

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 当天从远程语录接口取回的格言。
 *
 * 刻意不带日期字段：它是「某一天的那条」，日期由调用方比对，
 * 存进来时就已经和某一天绑定，取出来时只关心「是不是今天」。
 *
 * 放在 util 包而不是 data 包，是为了避免 util → data 的反向依赖
 * （data 层的 HabitRepository 已经依赖本类，反过来再依赖会形成包循环）。
 */
data class CachedQuote(val text: String, val source: String)

/**
 * 应用级偏好设置（界面字号、整体主题、触觉反馈、示例播种标记）。
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
     * 音效反馈主开关，默认开启。
     *
     * 与 [hapticEnabled] 完全同构（同步 + Flow 双形态），原因也一样：
     * [Sounds] 在每次播放前需要**同步**判定，等不及 Flow 发射；
     * 而管理中心的开关 UI 需要响应式刷新。
     *
     * 默认开而细节音效默认关，是一条刻意的分界线：
     * 成就音（打卡完成、计数达标）频率低、信息量大，值得默认开；
     * 细节音频率高，默认开等于把反馈变成噪音。
     */
    val soundEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SOUND_ENABLED) {
                trySend(prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** 同步读取音效总开关，供 [Sounds] 使用。 */
    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    /**
     * 细节音效开关（勾选子任务、计数器步进、撤销），默认**关闭**。
     *
     * 是 [soundEnabled] 的下级：主开关关掉时它一并失效，反之不成立。
     */
    val soundDetailEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SOUND_DETAIL_ENABLED) {
                trySend(prefs.getBoolean(KEY_SOUND_DETAIL_ENABLED, DEFAULT_SOUND_DETAIL_ENABLED))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean(KEY_SOUND_DETAIL_ENABLED, DEFAULT_SOUND_DETAIL_ENABLED))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun isSoundDetailEnabled(): Boolean =
        prefs.getBoolean(KEY_SOUND_DETAIL_ENABLED, DEFAULT_SOUND_DETAIL_ENABLED)

    fun setSoundDetailEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_DETAIL_ENABLED, enabled).apply()
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

    /**
     * 上一次检查应用更新的时间戳（毫秒）。0 表示从未检查过。
     *
     * 存在的唯一理由是**限频**：启动时的静默检查如果不加限制，用户一天开 20 次 App
     * 就会打 20 次 GitHub API —— 未认证请求每小时只有 60 次额度，同一个出口 IP 下
     * 几个人一起用很容易被限流；而限流的表现是「从此再也检查不到更新」，极难排查。
     * 24 小时一次足够：这个 App 没有需要当天内修复的紧急问题。
     */
    fun lastUpdateCheckAt(): Long = prefs.getLong(KEY_UPDATE_CHECK_AT, 0L)

    fun markUpdateChecked(at: Long) {
        prefs.edit().putLong(KEY_UPDATE_CHECK_AT, at).apply()
    }

    /**
     * 界面主题 id（取值见 ui.theme.AppTheme）。
     *
     * 这里只存字符串、不去引用 AppTheme：本类在 util 层，让它反向依赖 ui 包
     * 会把「偏好存储」和「界面呈现」的分层搅乱；由调用方拿着 id 去查枚举。
     * 同样属于本地显示偏好，**不进备份文件**。
     */
    val themeId: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME) {
                trySend(currentThemeId())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(currentThemeId())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * 同步读取主题 id。
     *
     * 必须提供同步形态：主题要在 setContent 的第一帧之前决定，
     * 等 Flow 发射完再生效会闪一下旧主题。
     */
    fun currentThemeId(): String = prefs.getString(KEY_THEME, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID

    fun setThemeId(id: String) {
        prefs.edit().putString(KEY_THEME, id).apply()
    }

    /**
     * 读取「今天那条远程格言」。
     *
     * 只有缓存的日期正好等于 [today] 才返回，否则一律返回 null —— 这一步是
     * 「每日格言」语义的关键：跨天后旧句子立刻失效，界面会退回内置库当天的句子，
     * 直到拿到新的远程句子为止。
     *
     * 同样是本地派生数据，**不进备份文件**：它只是一句可以重新拉取的句子，
     * 恢复备份时没必要把它带到另一台设备上去。
     */
    fun cachedRemoteQuote(today: LocalDate): CachedQuote? {
        if (prefs.getString(KEY_QUOTE_DATE, null) != today.toString()) return null
        val text = prefs.getString(KEY_QUOTE_TEXT, null)?.takeIf { it.isNotBlank() } ?: return null
        val source = prefs.getString(KEY_QUOTE_SOURCE, null) ?: return null
        return CachedQuote(text, source)
    }

    /** 记下今天拉到的远程格言。三个字段必须同时写，缺一个都会让 [cachedRemoteQuote] 判为无效。 */
    fun saveRemoteQuote(today: LocalDate, text: String, source: String) {
        prefs.edit()
            .putString(KEY_QUOTE_DATE, today.toString())
            .putString(KEY_QUOTE_TEXT, text)
            .putString(KEY_QUOTE_SOURCE, source)
            .apply()
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

        /** 同样公开：[Sounds] 也要直接监听同一个文件。 */
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_SOUND_DETAIL_ENABLED = "sound_detail_enabled"

        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_SAMPLE_SEEDED = "sample_seeded"
        private const val KEY_THEME = "theme_id"
        private const val KEY_UPDATE_CHECK_AT = "update_check_at"

        // 「今天那条远程格言」的三件套。日期存 ISO 字符串（yyyy-MM-dd），
        // 与 LocalDate.toString() 严格一致，比较时不做任何解析。
        private const val KEY_QUOTE_DATE = "quote_date"
        private const val KEY_QUOTE_TEXT = "quote_text"
        private const val KEY_QUOTE_SOURCE = "quote_source"
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_HAPTIC_ENABLED = true

        /** 成就音默认开、细节音默认关 —— 分界线见 [soundEnabled] 的注释。 */
        const val DEFAULT_SOUND_ENABLED = true
        const val DEFAULT_SOUND_DETAIL_ENABLED = false

        /**
         * 默认跟随系统。取值必须与 AppTheme.AUTO.id 一致 ——
         * 两边一旦不符，新用户装上就会落到未知 id；AppThemeTest 守着这条。
         */
        const val DEFAULT_THEME_ID = "auto"

        /** 从当前缩放值反查对应的档位，找不到（例如旧数据 0.9）则归为标准。 */
        fun sizeOf(scale: Float): FontSize =
            FontSize.entries.firstOrNull { it.scale == scale } ?: FontSize.NORMAL
    }
}
