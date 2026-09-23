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

    /**
     * 是否「一次性任务」——该习惯在它整个生命周期里只会排期一次。
     *
     * 覆盖两种情形：
     * 1. 显式单次（[TYPE_NONE]）；
     * 2. 循环类型但生效区间里**只落得到一天**（例如「每天」+ 起止同为 2026-09-22，
     *    或「每周三」+ 区间只罩住那一个周三）。
     *
     * ⚠️ 为什么不能只判 `recurrenceType == TYPE_NONE`：
     * 上面第 2 类在语义上和单次毫无区别 —— 一生只出现一天，完成后必然算出
     * 「连续 1 天 / 最长 1 天」这种纯噪音。只认 type 的话卡片照样挂着那行，
     * 而且排期说明的显示条件（`type != daily`）还刚好把它**藏掉**：
     * 该显的不显、该藏的不藏，两头都错。所以判定必须落在「排期语义」上而不是字段字面值上。
     *
     * 开销：最多两趟「命中即返回」的短扫描，且只在有 endDate 时才扫；
     * 常规循环计划第一趟就直接命中第二天。
     */
    fun isOneShot(habit: Habit): Boolean {
        if (habit.recurrenceType == TYPE_NONE) return true

        // 没有明确结束日 = 无限循环，不可能是「只排一次」
        val startStr = habit.startDate.takeIf { it.isNotBlank() } ?: return false
        val endStr = habit.endDate?.takeIf { it.isNotBlank() } ?: return false
        val start = runCatching { LocalDate.parse(startStr, ISO) }.getOrNull() ?: return false
        val end = runCatching { LocalDate.parse(endStr, ISO) }.getOrNull() ?: return false
        if (end.isBefore(start)) return false

        // 从「起点前一天」起算，好让起点当天本身也进入候选
        val spanDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val first = nextScheduledDate(habit, start.minusDays(1), spanDays) ?: return false

        // 区间内只有第一次那一天
        val remaining = ChronoUnit.DAYS.between(first, end).toInt()
        if (remaining <= 0) return true
        return nextScheduledDate(habit, first, remaining) == null
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

    /**
     * 判定该习惯/日程是否已结束或往期已完成：
     *
     * 1. 明确已到期：有 endDate 且 endDate < todayStr
     * 2. 一次性任务（包含显式单次与区间内仅一天的伪循环）：
     *    - 排期日期在过去（无论是否打卡，该日程已过去）
     *    - 已打过卡（totalCheckIns > 0，该单次任务已完成）
     * 3. 显式单次任务（TYPE_NONE）且排期在过去
     */
    fun isFinished(habit: Habit, todayDate: LocalDate, totalCheckIns: Int): Boolean {
        val todayStr = todayDate.toString()
        val end = habit.endDate?.takeIf { it.isNotBlank() }
        if (end != null && end < todayStr) return true

        if (isOneShot(habit)) {
            if (habit.startDate.isNotBlank() && habit.startDate < todayStr) return true
            if (totalCheckIns > 0) return true
        }

        if (habit.recurrenceType == TYPE_NONE && habit.startDate.isNotBlank() && habit.startDate < todayStr) {
            return true
        }

        return false
    }
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
