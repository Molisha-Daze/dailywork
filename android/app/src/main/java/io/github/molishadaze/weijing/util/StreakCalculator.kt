package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate

data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int
)

object StreakCalculator {

    /**
     * 回溯窗口上限（天）。默认 10 年，即最多逐日遍历约 3650 天。
     *
     * 这是**真正的**上限：扫描起点不会早于 [referenceToday] 减去该值，
     * 因此开销恒定有界，不会随习惯使用年限增长而膨胀。
     * 代价是超过 10 年的更早历史不计入统计——对自用习惯工具而言这个取舍是合理的，
     * 且比「注释说有上限、实际无界」要诚实得多。
     */
    const val DEFAULT_HISTORY_WINDOW_DAYS = 3650L

    /**
     * 按「有排期的日期」计算连续打卡，而不是按自然日。
     *
     * 这是与每周 / 每月 / 每 N 天 / 单次排期配套的关键改动：
     * - 「每周一三五」的习惯，周一、周三都完成 → 连续 2 次，周二没打卡不再把它打断
     * - 「每周一」的习惯，周四查看时仍显示连续 1 次，而不是归零
     * - 「每月 15 号」不再因为中间 29 天没有打卡而永远显示 0
     *
     * @param checkInsByDate 该习惯的全部打卡记录，按 "yyyy-MM-dd" 索引
     * @param referenceToday 参照「今天」，用于判定今天是否还算宽限期内
     * @param historyWindowDays 回溯窗口上限（天），保证遍历量有界
     */
    fun calculate(
        habit: Habit,
        checkInsByDate: Map<String, CheckIn>,
        referenceToday: LocalDate = DateUtils.todayDate(),
        historyWindowDays: Long = DEFAULT_HISTORY_WINDOW_DAYS
    ): StreakResult {
        // 单次计划（TYPE_NONE）在语义上根本没有「连续」这个概念 —— 它全生命周期只有一天排期，
        // 完成即 1/1、未完成即 0/0，无论怎么算都毫无信息量。
        // 在这里直接归零，而不是让每个调用方（卡片、未来的统计页）各自特判：
        // 口径只此一份，UI 就不必再关心「这个 1 到底是不是真的连续一天」。
        if (habit.recurrenceType == HabitSchedule.TYPE_NONE) return StreakResult(0, 0)

        if (checkInsByDate.isEmpty()) return StreakResult(0, 0)

        val earliestCheckIn = checkInsByDate.keys
            .mapNotNull { runCatching { DateUtils.parseDate(it) }.getOrNull() }
            .minOrNull()
        val startDate = habit.startDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { DateUtils.parseDate(it) }.getOrNull() }

        // 三个候选起点取【最晚】的那个：
        // 1. 窗口下界 —— 这是硬性上限，保证扫描天数不超过 historyWindowDays
        // 2. 习惯开始日期 —— 更早的日期根本没有排期，跳过不影响任何结果
        // 3. 最早一次打卡 —— 更早的日期没有完成记录，不可能延长任何连续段
        //
        // 注意：必须是 max 而不是 min。取 min 会让「开始于 2018 年」这类老习惯
        // 一路扫到 2018 年，窗口形同虚设（这正是这里此前的行为）。
        val scanStart = listOfNotNull(
            referenceToday.minusDays(historyWindowDays),
            earliestCheckIn,
            startDate
        ).max()

        // 排期判定预解析一次，避免逐日重复 split 字符串
        val matcher = HabitSchedule.of(habit)

        // 只收集「有排期」的日期。未来日期一律排除，避免提前打卡污染统计。
        val scheduled = buildList {
            var cursor = scanStart
            while (!cursor.isAfter(referenceToday)) {
                if (matcher.matches(cursor)) add(cursor)
                cursor = cursor.plusDays(1)
            }
        }
        if (scheduled.isEmpty()) return StreakResult(0, 0)

        fun isDone(date: LocalDate): Boolean =
            HabitSchedule.isCompleted(habit, checkInsByDate[DateUtils.formatDate(date)])

        // 最长连续：按排期日期顺序跑一遍
        var longest = 0
        var run = 0
        for (d in scheduled) {
            if (isDone(d)) {
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }

        // 当前连续：从最近一次排期往前数。
        // 若最后一次排期就是今天且尚未完成，今天还没过完，不算断签，从更早一次继续往前数。
        var current = 0
        var idx = scheduled.size - 1
        if (idx >= 0 && scheduled[idx] == referenceToday && !isDone(referenceToday)) idx--
        while (idx >= 0 && isDone(scheduled[idx])) {
            current++
            idx--
        }

        return StreakResult(current, maxOf(longest, current))
    }
}
