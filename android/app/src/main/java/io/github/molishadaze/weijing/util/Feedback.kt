package io.github.molishadaze.weijing.util

/**
 * 反馈门面：一次用户动作 → 触觉 + 音效。
 *
 * 为什么要有这一层，而不是在 15 个调用点各写一行 `Haptics.play(...)` 加一行 `Sounds.play(...)`：
 * 这两者**必须成对出现且成对演进**。分散写法下，将来加第 10 个音效时总会漏掉某几处，
 * 而漏掉是**静默的** —— 不报错、不崩溃，只是那个地方没声音，靠人肉 review 抓不住。
 * 收成一个映射表之后，「哪些动作有反馈」变成一张能一眼看完、也能被单测覆盖的表。
 *
 * 事件到反馈的对应关系刻意放在枚举构造参数里，而不是 `when` 分支里：
 * 编译器会强制每个枚举项都给出映射，新增事件时漏配会直接编译失败。
 */
object Feedback {

    /**
     * 用户可感知的交互事件。
     *
     * 注意这里**没有** tab 切换、翻月、FAB 展开、弹窗开合这类导航事件 ——
     * 不是遗漏，是刻意排除。理由见 [Sounds] 顶部关于「声音是公开的」那段。
     */
    enum class Event(val haptic: Haptics.Level?, val sfx: Sounds.Sfx?) {

        /** 打卡完成（今日页圆圈、历史详情补打卡、整组全选）。 */
        HABIT_DONE(Haptics.Level.STRONG, Sounds.Sfx.ACHIEVE_HABIT),

        /** 取消打卡。触觉给轻振，音效走 [Sounds.Sfx.UNDO]（默认关）。 */
        HABIT_UNDONE(Haptics.Level.LIGHT, Sounds.Sfx.UNDO),

        /** 勾选/取消单个子任务（非最后一项）。 */
        SUBTASK_TOGGLED(Haptics.Level.LIGHT, Sounds.Sfx.STEP_SOFT),

        /** 计划内子任务全部勾满。 */
        PLAN_DONE(Haptics.Level.STRONG, Sounds.Sfx.ACHIEVE_PLAN),

        /**
         * 今日全部排期完成。
         *
         * 由调用点**提前判定**（见 `HabitCard` 的 `completesDay` 参数），
         * 而不是等状态回流后在页面层做边沿检测 —— 后者会让 [PLAN_DONE] 与它
         * 在几百毫秒内先后响两声，叠成噪音；而「补响」又会让声音与动作错位。
         */
        DAY_DONE(Haptics.Level.STRONG, Sounds.Sfx.ACHIEVE_DAY),

        /** 习惯内计数器 +1 但未达标。 */
        COUNTER_STEP(Haptics.Level.LIGHT, Sounds.Sfx.STEP_SOFT),

        /** 习惯内计数器 -1（撤销语义）。 */
        COUNTER_BACK(Haptics.Level.LIGHT, Sounds.Sfx.UNDO),

        /** 习惯内计数器**恰好**踩到目标值。 */
        COUNTER_GOAL(Haptics.Level.STRONG, Sounds.Sfx.COUNTER_GOAL),

        /** 独立计数器到达上限。 */
        COUNTER_LIMIT(Haptics.Level.STRONG, Sounds.Sfx.COUNTER_LIMIT),

        /** 计数器清零、删除计数器等破坏性操作已执行。 */
        CLEARED(Haptics.Level.MEDIUM, Sounds.Sfx.CLEARED),

        /** 备份恢复完成。只发声不振动 —— 用户此刻正盯着屏幕，振动没有额外信息量。 */
        RESTORED(null, Sounds.Sfx.RESTORED),
    }

    /**
     * 触发一次反馈。
     *
     * 两个子系统各自判定自己的开关与节流：用户可能只关了声音留着振动（或反过来），
     * 所以这里不做「一起开一起关」的协调，只负责把同一个语义同时告诉两边。
     */
    fun fire(event: Event) {
        event.haptic?.let { Haptics.play(it) }
        event.sfx?.let { Sounds.play(it) }
    }
}
