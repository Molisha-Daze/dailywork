package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 习惯排期与完成判定的唯一权威来源。
 *
 * 今日页、月历、热力图统计、通知链路都必须走这里，避免同一份数据被多处各算一遍
 * 得出互相矛盾的结果（历史上 isHabitScheduled 只是 CalendarMonthView 的 private
 * 函数，导致今日页与月历口径不一致）。
 */
object HabitSchedule {

    const val TYPE_NONE = "none"
    const val TYPE_DAILY = "daily"
    const val TYPE_WEEKLY = "weekly"
    const val TYPE_MONTHLY = "monthly"
    const val TYPE_INTERVAL = "interval"

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val DEFAULT_TYPE = TYPE_DAILY

    /** 该习惯在 [date] 是否有排期。 */
    fun isScheduled(habit: Habit, date: LocalDate): Boolean = of(habit).matches(date)

    /** 字符串日期重载，供只拿到 "yyyy-MM-dd" 的调用方使用。 */
    fun isScheduled(habit: Habit, dateStr: String): Boolean {
        val date = runCatching { LocalDate.parse(dateStr, ISO) }.getOrNull() ?: return false
        return isScheduled(habit, date)
    }

    /**
     * 预解析版排期匹配器。
     *
     * 逐日遍历（StreakCalculator 会回溯数千天）时，如果每天都重新 split 一次
     * weeklyDays / monthlyDays 字符串，会产生大量临时对象。用这个可以把解析只做一次。
     */
    fun of(habit: Habit): ScheduleMatcher = ScheduleMatcher(
        type = habit.recurrenceType,
        startDate = habit.startDate,
        endDate = habit.endDate,
        weeklyDays = parseWeeklyDays(habit.weeklyDays),
        monthlyDays = parseMonthlyDays(habit.monthlyDays),
        intervalDays = habit.intervalDays.coerceAtLeast(1)
    )

    /**
     * 「即将到来」向前看多少天。
     *
     * 取 60 而不是 30：每月 31 日的习惯在 2 月会一路跳到 3 月 31 日（约 58 天），
     * 窗口只有 30 的话这类计划会被漏掉，看起来像「排了却没有」。
     */
    const val UPCOMING_WINDOW_DAYS = 60

    /**
     * 从 [after] 之后（不含当天）起，在 [windowDays] 天内找该习惯**下一次**的排期日期；
     * 窗口内都没有就返回 null（例如单次计划已过期、习惯已结束、或每周某天但没选任何天）。
     *
     * ⚠️ 刻意只找**一次**，不是把窗口内所有命中日期都列出来：
     * 循环习惯（每天 / 每周一三五 / 每月 15 号）在 60 天里会命中十几次到几十次，
     * 全列出来等于把「未来两个月」糊到今日页上。今日页要回答的是「下一个是什么时候」，
     * 所以每个习惯至多贡献一条 —— 这正是「循环日程只显示一个」的落点。
     *
     * 逐日匹配而不是反解公式：排期判定只有 [ScheduleMatcher.matches] 一份实现，
     * 另写一套「下次出现日」的闭式解就会有两份逻辑，改一处漏一处。
     * 开销可控（习惯数 × 60 次布尔判断），StreakCalculator 回溯几千年也是这么走的。
     */
    fun nextScheduledDate(
        habit: Habit,
        after: LocalDate,
        windowDays: Int = UPCOMING_WINDOW_DAYS
    ): LocalDate? {
        if (windowDays <= 0) return null
        val matcher = of(habit)
        for (offset in 1..windowDays) {
            val date = after.plusDays(offset.toLong())
            if (matcher.matches(date)) return date
        }
        return null
    }

    /** 计数器目标次数，至少为 1。 */
    fun effectiveTarget(habit: Habit): Int = habit.targetCount.coerceAtLeast(1)

    /** 当天已记录的次数。 */
    fun currentCount(checkIn: CheckIn?): Int = checkIn?.count ?: 0

    /**
     * 统一的“是否已完成”判定。
     * - 计数器习惯：累计次数达到目标才算完成
     * - 普通习惯：有打卡记录即算完成
     */
    fun isCompleted(habit: Habit, checkIn: CheckIn?): Boolean {
        if (checkIn == null) return false
        return if (habit.isCounter) {
            checkIn.count >= effectiveTarget(habit)
        } else {
            checkIn.isCompleted || checkIn.count > 0
        }
    }

    /** 把当前次数夹到合法区间内（0..target）。 */
    fun clampCount(habit: Habit, count: Int): Int =
        count.coerceIn(0, if (habit.isCounter) effectiveTarget(habit) else 1)

    fun parseWeeklyDays(raw: String?): Set<Int> =
        raw?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            ?: emptySet()

    fun parseMonthlyDays(raw: String?): Set<Int> =
        raw?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..31 }
            ?.toSet()
            ?: emptySet()

    fun formatWeeklyDays(days: Set<Int>): String? =
        if (days.isEmpty()) null else days.sorted().joinToString(",")

    fun formatMonthlyDays(days: Set<Int>): String? =
        if (days.isEmpty()) null else days.sorted().joinToString(",")
}

/**
 * 预解析的排期匹配器。判定逻辑只在这里实现一份，
 * [HabitSchedule.isScheduled] 与 [StreakCalculator] 都通过它，避免两处逻辑漂移。
 */
data class ScheduleMatcher(
    val type: String,
    val startDate: String,
    val endDate: String?,
    val weeklyDays: Set<Int>,
    val monthlyDays: Set<Int>,
    val intervalDays: Int
) {
    private val iso: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun matches(date: LocalDate): Boolean {
        val dateStr = date.format(iso)

        // startDate 为空串表示「不限起始」（老数据的语义），不能当成 1970 或用今天替代
        if (startDate.isNotBlank() && dateStr < startDate) return false
        val end = endDate
        if (!end.isNullOrBlank() && dateStr > end) return false

        return when (type) {
            HabitSchedule.TYPE_NONE -> dateStr == startDate
            HabitSchedule.TYPE_DAILY -> true
            HabitSchedule.TYPE_WEEKLY -> weeklyDays.contains(date.dayOfWeek.value)
            HabitSchedule.TYPE_MONTHLY -> monthlyDays.contains(date.dayOfMonth)
            HabitSchedule.TYPE_INTERVAL -> {
                if (startDate.isBlank()) return true
                val startObj = runCatching { LocalDate.parse(startDate, iso) }.getOrNull() ?: return true
                val diff = ChronoUnit.DAYS.between(startObj, date)
                diff >= 0 && diff % intervalDays == 0L
            }
            else -> true
        }
    }
}
