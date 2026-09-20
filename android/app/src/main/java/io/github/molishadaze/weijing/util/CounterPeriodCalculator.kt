package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 一个周期的闭区间：从 [start] 到 [end]（含两端）。
 *
 * 用闭区间而不是半开区间，是因为它最终要落成 "yyyy-MM-dd" 字符串存档，
 * 「9月20日 - 9月20日」比「9月20日 - 9月21日(不含)」更贴近用户看到的日期。
 */
data class CounterPeriod(val start: LocalDate, val end: LocalDate) {
    /** 周期内第几天（1 起），用于「第 2/3 天」这类展示。 */
    fun dayIndex(date: LocalDate): Int = (ChronoUnit.DAYS.between(start, date) + 1).toInt()

    /** 周期总天数。 */
    val lengthDays: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
}

/**
 * 独立计数器的周期归零算法。
 *
 * **刻意做成纯函数、不碰 Room / Context / System.currentTimeMillis()**：
 * 周期边界（尤其是跨月、跨年、闰年和「每 N 天」的滚动锚点）是这块最容易错、
 * 又最难在真机上复现的部分，必须能在纯 JVM 单测里把边界日子直接喂进来验。
 *
 * 三种周期的「周期首日」取值口径：
 * - [StandaloneCounter.RESET_DAILY]：就是当天；
 * - [StandaloneCounter.RESET_WEEKLY]：所在自然周的周一；
 * - [StandaloneCounter.RESET_MONTHLY]：所在自然月的 1 号；
 * - [StandaloneCounter.RESET_INTERVAL]：以 [anchor] 为起点按 N 天滚动。
 *
 * 前三种对齐**自然**周/月，符合「每周归零」的日常直觉（用户不会认为
 * "我是周三建的，所以我的星期三是新的一周"）。只有「每 N 天」没有自然边界可言，
 * 才退化成从锚点起算的滚动窗口。
 */
object CounterPeriodCalculator {

    /** 是否配置了自动归零。 */
    fun isPeriodic(resetPeriod: String): Boolean =
        resetPeriod != StandaloneCounter.RESET_NONE

    /**
     * 算出 [date] 落在哪个周期。不归零的类型返回 null。
     *
     * @param anchor 滚动锚点，仅 [StandaloneCounter.RESET_INTERVAL] 使用；
     *               若锚点在 [date] 之后（数据异常），按第 0 个周期处理而不是抛异常 ——
     *               这里宁可给出一个"看起来正常"的结果，也不要让 UI 因为脏数据崩掉。
     */
    fun periodOf(
        resetPeriod: String,
        intervalDays: Int,
        anchor: LocalDate,
        date: LocalDate
    ): CounterPeriod? = when (resetPeriod) {
        StandaloneCounter.RESET_DAILY -> CounterPeriod(date, date)

        StandaloneCounter.RESET_WEEKLY -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            CounterPeriod(monday, monday.plusDays(6))
        }

        StandaloneCounter.RESET_MONTHLY -> {
            val first = date.withDayOfMonth(1)
            CounterPeriod(first, first.plusMonths(1).minusDays(1))
        }

        StandaloneCounter.RESET_INTERVAL -> {
            val n = intervalDays.coerceAtLeast(1)
            val elapsed = ChronoUnit.DAYS.between(anchor, date)
            val index = if (elapsed < 0) 0L else elapsed / n
            val start = anchor.plusDays(index * n)
            CounterPeriod(start, start.plusDays(n - 1L))
        }

        else -> null
    }

    /**
     * 锚点所在的周期。
     *
     * 这就是「上一个周期」的定义 —— 结算时把它的起止日期写进归档记录。
     * 注意它未必以锚点为起点：锚点是 9 月 18 日（周四）、周期是「每周」时，
     * 它的周期是 9 月 16 日（周一）～ 9 月 22 日（周日），整周都算。
     */
    fun periodOfAnchor(
        resetPeriod: String,
        intervalDays: Int,
        anchor: LocalDate
    ): CounterPeriod? = periodOf(resetPeriod, intervalDays, anchor, anchor)

    /**
     * 是否需要结算：今天所在的周期已经不是锚点所在的周期了。
     *
     * 只比较**周期首日**而不是日期本身 —— 每周周期里，周一建的和周五建的
     * 如果在同一周内，就不该归零。
     */
    fun needsRollover(
        resetPeriod: String,
        intervalDays: Int,
        anchor: LocalDate,
        today: LocalDate
    ): Boolean {
        if (!isPeriodic(resetPeriod)) return false
        val current = periodOf(resetPeriod, intervalDays, anchor, today) ?: return false
        val anchored = periodOfAnchor(resetPeriod, intervalDays, anchor) ?: return false
        return current.start != anchored.start
    }

    /**
     * 今天所属周期的首日。新建/改周期配置时用它当锚点，
     * 保证计数器从「当前周期」而不是「今天」开始算。
     */
    fun currentPeriodStart(
        resetPeriod: String,
        intervalDays: Int,
        today: LocalDate
    ): LocalDate? = periodOf(resetPeriod, intervalDays, today, today)?.start

    /** 设置项标题，如「每 3 天归零」。 */
    fun displayName(resetPeriod: String, intervalDays: Int): String = when (resetPeriod) {
        StandaloneCounter.RESET_DAILY -> "每日归零"
        StandaloneCounter.RESET_WEEKLY -> "每周归零"
        StandaloneCounter.RESET_MONTHLY -> "每月归零"
        StandaloneCounter.RESET_INTERVAL -> "每 ${intervalDays.coerceAtLeast(1)} 天归零"
        else -> "不自动归零"
    }

    /** 卡片上那枚小徽章的文案；不归零时返回 null（不显示徽章）。 */
    fun badgeText(
        resetPeriod: String,
        intervalDays: Int,
        anchor: LocalDate,
        today: LocalDate
    ): String? {
        val period = periodOf(resetPeriod, intervalDays, anchor, today) ?: return null
        return when (resetPeriod) {
            StandaloneCounter.RESET_DAILY -> "今天"
            StandaloneCounter.RESET_WEEKLY -> "本周"
            StandaloneCounter.RESET_MONTHLY -> "本月"
            StandaloneCounter.RESET_INTERVAL ->
                "第 ${period.dayIndex(today)}/${period.lengthDays} 天"
            else -> null
        }
    }

    /**
     * 周期区间的可读文本。
     *
     * 单日周期（每日归零）不写成「9月20日 - 9月20日」，太蠢了。
     * 同年省略年份、同月省略月份，跨年才把年份补齐。
     */
    fun formatRange(period: CounterPeriod): String {
        val s = period.start
        val e = period.end
        if (s == e) return "${s.monthValue}月${s.dayOfMonth}日"
        if (s.year != e.year) {
            return "${s.year}年${s.monthValue}月${s.dayOfMonth}日 - " +
                "${e.year}年${e.monthValue}月${e.dayOfMonth}日"
        }
        if (s.monthValue != e.monthValue) {
            return "${s.monthValue}月${s.dayOfMonth}日 - ${e.monthValue}月${e.dayOfMonth}日"
        }
        return "${s.monthValue}月${s.dayOfMonth}日 - ${e.dayOfMonth}日"
    }
}
