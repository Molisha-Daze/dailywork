package io.github.molishadaze.weijing.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * 全局触觉反馈引擎。
 *
 * 为什么不用 Compose 的 `LocalHapticFeedback`：
 * 本项目锁定的 Compose BOM 只提供 `LongPress` / `TextHandleMove` 两种类型，
 * 表达不出「打卡完成」和「计数器 +1」该有的力度差异。要让完成感明显强于步进感，
 * 必须自己控振幅和波形，所以走 Vibrator + VibrationEffect。
 *
 * 为什么是 object 而不是 Composable 参数：
 * 除了 Compose 界面，`CheckInActionReceiver`（通知栏「标记完成」）在后台也要振——
 * 那里根本没有 Compose 上下文。做成全局单例，两条路径共用同一套开关与节流状态。
 *
 * 依赖：[attach] 必须在 `HabitApplication.onCreate()` 里调用一次。
 * 未 attach 时所有调用都是安全 no-op，不会崩。
 */
object Haptics {

    /**
     * 振动档位。刻意只分三档——档位越多，用户越分辨不出来，收益递减。
     */
    enum class Level {
        /** 轻：计数器 ±1、单个子任务勾选、取消打卡。 */
        LIGHT,

        /** 中：长按清零、确认清零这类「破坏性操作已执行」。 */
        MEDIUM,

        /** 强：打卡完成、计数达标、子任务全勾满、独立计数器到达上限。 */
        STRONG
    }

    /**
     * 同档位的最小触发间隔。计数器按钮是给人连点用的，没有节流的话
     * 一秒能振五六次，手机会变成按摩棒。70ms 足够吃掉连点，又不影响正常手速。
     */
    private const val THROTTLE_MS = 70L

    private var vibrator: Vibrator? = null
    private var canVibrate = false

    /** 设备是否支持振幅控制。不支持时只能用设备默认振幅，不能传具体的 amplitude 值。 */
    private var amplitudeControl = false

    /** 系统的「触觉反馈」总开关。用户关掉它，我们就不该再振。 */
    private var systemHapticsEnabled = true

    /** App 内自己的开关（管理中心可关）。 */
    private var userEnabled = true

    /** 必须持有强引用：SharedPreferences 的监听器是弱引用存表，局部变量会被 GC 掉。 */
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var prefs: SharedPreferences? = null

    /** 每档各自计时，避免「连点轻振」把紧接着的「达标强振」也一起吞掉。 */
    private val lastPlayedAt = LongArray(Level.entries.size)

    /** 在 `Application.onCreate()` 里调用一次。重复调用是幂等的。 */
    fun attach(context: Context) {
        if (vibrator != null) return

        val app = context.applicationContext

        vibrator = resolveVibrator(app)
        canVibrate = vibrator?.hasVibrator() == true
        amplitudeControl = vibrator?.hasAmplitudeControl() == true

        val sp = app.getSharedPreferences(AppSettings.PREF_NAME, Context.MODE_PRIVATE)
        prefs = sp
        userEnabled = sp.getBoolean(AppSettings.KEY_HAPTIC_ENABLED, true)
        systemHapticsEnabled = readSystemHapticSetting(app)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == AppSettings.KEY_HAPTIC_ENABLED) {
                userEnabled = p.getBoolean(AppSettings.KEY_HAPTIC_ENABLED, true)
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        prefsListener = listener
    }

    /** 设备是否真的能振。设置页据此决定要不要把开关置灰。 */
    fun isSupported(): Boolean = canVibrate

    /**
     * 最终是否处于「会振」状态。三个条件全满足才行：
     * 设备有马达 + App 内开关打开 + 系统触觉总开关打开。
     */
    fun isActive(): Boolean = canVibrate && userEnabled && systemHapticsEnabled

    /** 系统触觉总开关是否被关掉了。设置页用它给出提示文案。 */
    fun isSystemHapticsDisabled(): Boolean = !systemHapticsEnabled

    /**
     * 触发一次振动。任何情况下都不抛异常：触觉是锦上添花，
     * 绝不能因为某些 ROM 的振动策略把打卡这个主流程搞崩。
     */
    fun play(level: Level) {
        if (!isActive()) return
        val v = vibrator ?: return

        val index = level.ordinal
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayedAt[index] < THROTTLE_MS) return
        lastPlayedAt[index] = now

        try {
            val effect = buildEffect(level)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 声明为「触摸」用途，系统才会按触觉的规则处理（例如勿扰模式下该静就静），
                // 而不是当成闹钟类振动强行放行。
                v.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect)
            }
        } catch (e: Exception) {
            // 少数 ROM 在振动被策略拦截时会抛异常/权限异常，直接吞掉。
        }
    }

    private fun resolveVibrator(app: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private fun readSystemHapticSetting(app: Context): Boolean = try {
        Settings.System.getInt(
            app.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) == 1
    } catch (e: Exception) {
        // 读不到就当开启，宁可多振一下，也不要因为读设置失败让功能整体静默失效。
        true
    }

    private fun buildEffect(level: Level): VibrationEffect = when (level) {
        // 单次短促、低振幅：只是「我收到你的点击了」。
        Level.LIGHT -> oneShot(12L, 70)

        // 稍长稍重，用于破坏性操作已执行的确认。
        Level.MEDIUM -> oneShot(22L, 150)

        // 两段式：先轻点一下起手，隔一小段再来一记重的收尾。
        // 单一的「加长振动」只有时长变化，手感反而像卡顿；节奏才是完成感的来源。
        Level.STRONG -> if (amplitudeControl) {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 18L, 55L, 32L),
                intArrayOf(0, 170, 0, 255),
                -1
            )
        } else {
            // 设备不支持振幅控制时不能传 amplitudes 数组（0 表示停振，会被静音），
            // 改用只给 timings 的重载，全部按设备默认振幅播放。
            VibrationEffect.createWaveform(longArrayOf(0L, 18L, 55L, 32L), -1)
        }
    }

    private fun oneShot(durationMs: Long, amplitude: Int): VibrationEffect =
        if (amplitudeControl) {
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
}
