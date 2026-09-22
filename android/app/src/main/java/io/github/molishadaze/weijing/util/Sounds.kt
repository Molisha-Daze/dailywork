package io.github.molishadaze.weijing.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RawRes
import io.github.molishadaze.weijing.R

/**
 * 全局音效引擎。
 *
 * 与 [Haptics] 是一对：同样的 object 单例、同样的 attach 时机、同样的「绝不抛异常」原则。
 * 分开写而不是合并成一个类，是因为两者的**可用性判定条件完全不同**
 * （振动看硬件马达 + 系统触觉开关；音效看音频流音量 + 前台状态），
 * 硬塞进一个类会让 settle 的开关矩阵变成一团 if。
 * 对外统一入口是 [Feedback]，它负责把一次用户动作同时翻译成触觉与音效。
 *
 * ⚠️ 为什么音效比触觉保守得多（设计前提，改触发点前先读）：
 * 振动是私密的（手握着才有知觉，旁人听不见），声音是公开的（会议室、地铁、深夜床边）。
 * 所以本引擎**不做点击音、不做导航音**，只在「值得被听见」的成就与确认时刻发声。
 * 一旦有人想给 tab 切换或 FAB 点击加音效，请先回来读这一段。
 *
 * 依赖：[attach] 必须在 `HabitApplication.onCreate()` 里调用一次。
 * 未 attach 时所有调用都是安全 no-op。
 */
object Sounds {

    /**
     * 音效清单。`resId` 直接硬引用 `R.raw.*` —— 这本身就是一道编译期保险：
     * 万一 `res/raw/` 里少了某个文件，`R.raw` 就不存在该字段，编译直接失败，
     * 不会出现「运行时静默无声」这种最难查的故障。
     *
     * `detail = true` 的音效归「细节音效」开关管，默认关闭。判别标准是**触发频率**：
     * 高频重复的短音会被大脑归类成噪音而不是反馈（听觉适应），默认开等于自毁体验。
     */
    enum class Sfx(@RawRes val resId: Int, val detail: Boolean) {
        /** 单个习惯打卡完成。全 App 最高频的正反馈。 */
        ACHIEVE_HABIT(R.raw.sfx_achieve_habit, false),

        /** 计划内子任务全部勾满。 */
        ACHIEVE_PLAN(R.raw.sfx_achieve_plan, false),

        /** 今日排期全部完成。全 App 最高等级的时刻。 */
        ACHIEVE_DAY(R.raw.sfx_achieve_day, false),

        /** 计数器恰好达标。 */
        COUNTER_GOAL(R.raw.sfx_counter_goal, false),

        /** 独立计数器到达上限。 */
        COUNTER_LIMIT(R.raw.sfx_counter_limit, false),

        /** 计数器清零 / 删除确认等破坏性操作已执行。 */
        CLEARED(R.raw.sfx_clear, false),

        /** 备份恢复完成。 */
        RESTORED(R.raw.sfx_restore, false),

        /** 取消打卡 / 撤销。默认关：撤销是「退回」，配声音会变成一种微妙的奖励。 */
        UNDO(R.raw.sfx_undo, true),

        /** 勾选子任务 / 计数器步进。默认关：连点会变成机关枪。 */
        STEP_SOFT(R.raw.sfx_step_soft, true),
    }

    private const val TAG = "Sounds"

    /**
     * 同时播放的流数上限。设大了不会更好 —— 只会在「本不该重叠」的意外场景下
     * 让杂音叠得更响。4 足够覆盖最快的合法节奏。
     */
    private const val MAX_STREAMS = 4

    /** 整体回放增益。波形里已经用峰值表达了响度层级，这里只留一点点余量防扬声器失真。 */
    private const val PLAY_VOLUME = 0.9f

    /**
     * 音频属性 —— 本引擎最重要的一个技术决策。
     *
     * 走 `USAGE_ASSISTANCE_SONIFICATION`（映射到系统音量流 STREAM_SYSTEM），
     * 而**不是** `USAGE_MEDIA`（媒体音量流）。原因：
     * 手机的「静音模式」只静铃声与通知，**不动媒体音量**。若走媒体流，
     * 用户在会议室按下静音键之后再打卡，依然会响一声 —— 这是不可接受的失误。
     * 两害相权：宁可「系统音量调为 0 时听不见」，也不要「该静的时候响了」。
     *
     * 代价是必须给用户一条诊断路径，所以设置页有系统音量提示 + 试听按钮。
     */
    private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    /**
     * 以下四个字段都加 `@Volatile`。
     *
     * 不是防御性冗余：[CheckInActionReceiver] 在 `Dispatchers.IO` 的协程里调用
     * `Feedback.fire(...)`，也就是**非主线程**读写这些字段。虽然竞态的最坏后果
     * 只是「节流偶尔失效一次」而不会崩，但可见性问题是那种上线后偶发、本地永远复现不了的
     * 一类型 bug，提前标清楚比事后查便宜得多。
     */
    @Volatile
    private var pool: SoundPool? = null

    /** 已发起加载的 resId → load 返回的 soundId。 */
    private val soundIdOf = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** soundId → resId，用于在加载完成回调里反查。 */
    private val resIdOf = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** 已解码就绪的 resId → soundId。**只有在这里面的才播得响**。 */
    private val readyIds = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** App 内总开关（管理中心可关）。 */
    @Volatile
    private var userEnabled = true

    /** 细节音效开关（勾选子任务、计数器步进、撤销）。 */
    @Volatile
    private var detailEnabled = false

    /** 当前处于 started 状态的 Activity 数。> 0 才算「App 在前台」。 */
    @Volatile
    private var visibleActivities = 0

    /** 必须持有强引用：SharedPreferences 的监听器是弱引用存表，局部变量会被 GC 掉。 */
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** 每个音效**各自**计时。这样「连点计数器被节流」不会顺手吞掉「恰好达标」那一声。 */
    private val lastPlayedAt = LongArray(Sfx.entries.size)

    /**
     * 前台状态跟踪。用 framework 的 ActivityLifecycleCallbacks 而不是 `lifecycle-process`，
     * 因为后者要新增依赖，而本项目**离线构建、不引新依赖**。
     *
     * 为什么需要它：`CheckInActionReceiver`（通知栏「标记完成」）在后台运行。
     * 那里的振动是对的（私密），但音效是错的 —— 用户可能正在开会，或人在别的 app 里。
     */
    private val visibilityTracker = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            visibleActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            // coerceAtLeast 只是防御：理论上 start/stop 严格配对，但配置变更期间
            // 偶发的乱序不该让计数变成负数，否则音效会永久静音。
            visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /** 在 `Application.onCreate()` 里调用一次。重复调用是幂等的。 */
    fun attach(context: Context) {
        if (appContext != null) return

        val app = context.applicationContext
        appContext = app

        val sp = app.getSharedPreferences(AppSettings.PREF_NAME, Context.MODE_PRIVATE)
        prefs = sp
        userEnabled = sp.getBoolean(AppSettings.KEY_SOUND_ENABLED, AppSettings.DEFAULT_SOUND_ENABLED)
        detailEnabled = sp.getBoolean(
            AppSettings.KEY_SOUND_DETAIL_ENABLED, AppSettings.DEFAULT_SOUND_DETAIL_ENABLED
        )

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                AppSettings.KEY_SOUND_ENABLED ->
                    userEnabled = p.getBoolean(
                        AppSettings.KEY_SOUND_ENABLED, AppSettings.DEFAULT_SOUND_ENABLED
                    )

                AppSettings.KEY_SOUND_DETAIL_ENABLED ->
                    detailEnabled = p.getBoolean(
                        AppSettings.KEY_SOUND_DETAIL_ENABLED, AppSettings.DEFAULT_SOUND_DETAIL_ENABLED
                    )
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        prefsListener = listener

        (app as? Application)?.registerActivityLifecycleCallbacks(visibilityTracker)

        // 立刻发起加载。SoundPool.load 是异步的（解码在后台线程），这里的调用本身
        // 只是读文件头、耗时可忽略，但能保证「用户第一次点打卡」时就已就绪 ——
        // 否则第一个音效会静默消失，而那恰恰是用户第一次体验这个功能的一瞬间。
        ensurePool()
    }

    /** 设备当前是否处于「会出声」状态。 */
    fun isActive(): Boolean = userEnabled && (pool != null)

    /** 细节音效开关当前是否打开。设置页用。 */
    fun isDetailEnabled(): Boolean = detailEnabled

    /**
     * 触发一次音效。任何情况下都不抛异常：音效是锦上添花，
     * 绝不能因为音频策略或资源问题把打卡这个主流程搞崩。
     */
    fun play(sfx: Sfx) {
        if (!isEnabledFor(sfx)) return
        if (!isAppVisible()) return

        val slot = sfx.ordinal
        val now = SystemClock.uptimeMillis()
        if (isThrottled(lastPlayedAt[slot], now, SFX_THROTTLE_MS)) return
        lastPlayedAt[slot] = now

        start(sfx)
    }

    /**
     * 试听：绕过所有开关与节流。
     *
     * 存在意义：用户点「试听」时开关可能正关着（他就是在决定要不要开），
     * 此时静默等于什么都没告诉他。这是唯一一个可以无视用户设置的入口。
     */
    fun preview(sfx: Sfx) {
        start(sfx)
    }

    /**
     * 系统音量流（STREAM_SYSTEM）当前的百分比。取不到返回 -1。
     *
     * 设置页用它回答用户唯一会问的那个问题：「为什么没声音」。
     */
    fun systemVolumePercent(): Int {
        val ctx = appContext ?: return -1
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return -1
            val max = am.getStreamMaxVolume(AudioManager.STREAM_SYSTEM)
            if (max <= 0) -1
            else am.getStreamVolume(AudioManager.STREAM_SYSTEM) * 100 / max
        } catch (e: Exception) {
            -1
        }
    }

    /** 系统音量是否已静音。设置页据此给出「不是 bug」的明确提示。 */
    fun isSystemVolumeSilent(): Boolean = systemVolumePercent() == 0

    private fun isEnabledFor(sfx: Sfx): Boolean {
        if (!userEnabled) return false
        // 细节开关是主开关的下级：主开关关掉时，它也一并失效。
        if (sfx.detail && !detailEnabled) return false
        return true
    }

    private fun isAppVisible(): Boolean = visibleActivities > 0

    /**
     * 构造 SoundPool 并一次性加载全部音效。幂等，失败返回 null（后续调用会重试）。
     *
     * 关于 `OnLoadCompleteListener` 必须在 `load()` **之前**注册：
     * 反过来写在后面时，极快的加载可能在监听器挂上之前就完成，
     * 那一条完成事件就永远收不到 —— 表现为「某个音效永远不响」，而且无法复现。
     */
    private fun ensurePool(): SoundPool? {
        pool?.let { return it }
        val ctx = appContext ?: return null

        return try {
            val p = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(AUDIO_ATTRIBUTES)
                .build()

            p.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    resIdOf[sampleId]?.let { resId -> readyIds[resId] = sampleId }
                } else {
                    Log.w(TAG, "load failed: sampleId=$sampleId status=$status")
                }
            }

            Sfx.entries.forEach { sfx ->
                val id = p.load(ctx, sfx.resId, 1)
                if (id != 0) {
                    soundIdOf[sfx.resId] = id
                    resIdOf[id] = sfx.resId
                }
            }

            pool = p
            p
        } catch (e: Exception) {
            // 少数 ROM 在音频服务不可用时会抛异常，吞掉即可 —— 没声音不该影响打卡。
            Log.w(TAG, "SoundPool 初始化失败", e)
            null
        }
    }

    private fun start(sfx: Sfx) {
        val p = pool ?: return
        val soundId = readyIds[sfx.resId] ?: return

        try {
            val streamId = p.play(soundId, PLAY_VOLUME, PLAY_VOLUME, 1, 0, 1f)
            // release 版也保留这行日志，且这是刻意的：
            // 音效是全项目唯一无法靠截图、录屏或单测验证的行为，
            // logcat 是唯一能确认「play 被调到、且没被节流误拦」的观测点。
            Log.d(TAG, "play ${sfx.name} soundId=$soundId stream=$streamId")
        } catch (e: Exception) {
            Log.w(TAG, "play ${sfx.name} 失败", e)
        }
    }
}

/**
 * 同音效的最小触发间隔。比 [Haptics] 的 70ms 长得多，原因是物理上的：
 * 振动只有 12~32ms，连点不会叠加；音效有尾音（最长的 `sfx_achieve_day` 有 1.05s），
 * 两次播放叠在一起就是噪音。150ms 足够吃掉连点，又不影响正常操作节奏。
 */
private const val SFX_THROTTLE_MS = 150L

/**
 * 节流判定。刻意抽成**文件级纯函数**而不是塞进 [Sounds] 内部：
 * `Sounds` 的静态初始化依赖 android.media.SoundPool 等运行时类，
 * 在 JVM 单测里碰到就会抛 "Stub!"；而纯函数可以被 `SoundsTest` 直接断言。
 * `Sounds.Sfx` 是独立的静态嵌套类，单测访问它不会触发外部 object 的初始化。
 */
internal fun isThrottled(lastPlayedAt: Long, now: Long, throttleMs: Long = SFX_THROTTLE_MS): Boolean =
    now - lastPlayedAt < throttleMs
