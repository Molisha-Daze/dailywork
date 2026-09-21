package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 排期感知的连续打卡算法验证。
 *
 * 2026-09-19 是周六，据此推算：
 *   09-14 周一 / 09-15 周二 / 09-16 周三 / 09-17 周四 / 09-18 周五
 *   09-07 周一（上一周）
 */
class StreakCalculatorTest {

    /** 生成 2026-09-{range} 的日期串数组 */
    private fun september(range: IntRange): Array<String> =
        range.map { String.format("2026-09-%02d", it) }.toTypedArray()

    private fun checkInsOn(vararg dates: String): Map<String, CheckIn> =
        dates.associateWith { CheckIn(habitId = 1, date = it, isCompleted = true) }

    private fun weekly(days: String, start: String) = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_WEEKLY,
        startDate = start,
        weeklyDays = days
    )

    private fun monthly(days: String, start: String) = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_MONTHLY,
        startDate = start,
        monthlyDays = days
    )

    private fun daily(start: String = "2026-09-01") = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_DAILY,
        startDate = start
    )

    private fun oneShot(date: String) = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_NONE,
        startDate = date
    )

    /** 报告用例 1：每周一三五，连续完成周一和周三 → 周三应显示 2，而不是 1 */
    @Test
    fun weeklyMonWedFri_countsScheduledOccurrencesNotNaturalDays() {
        val habit = weekly("1,3,5", "2026-09-14")
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-14", "2026-09-16"),
            referenceToday = LocalDate.of(2026, 9, 16) // 周三
        )
        assertEquals(2, result.currentStreak)
        assertEquals(2, result.longestStreak)
    }

    /** 报告用例 2：每周一，周四查看时周一已完成 → 应显示 1，而不是 0 */
    @Test
    fun weeklyMonday_streakSurvivesNonScheduledDays() {
        val habit = weekly("1", "2026-09-14")
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-14"),
            referenceToday = LocalDate.of(2026, 9, 17) // 周四，离周一已过 3 天
        )
        assertEquals(1, result.currentStreak)
    }

    /** 回溯窗口必须真的生效：窗口调小后，统计结果应被截断 */
    @Test
    fun historyWindowActuallyBoundsTheScan() {
        val habit = daily("2026-09-01")
        val checkIns = checkInsOn(*september(1..17))
        val today = LocalDate.of(2026, 9, 17)

        // 默认窗口（10 年）足够大：17 天全部计入
        assertEquals(17, StreakCalculator.calculate(habit, checkIns, today).currentStreak)

        // 窗口只有 5 天：只能从 09-12 数起，共 6 天
        assertEquals(
            6,
            StreakCalculator.calculate(habit, checkIns, today, historyWindowDays = 5).currentStreak
        )
    }

    /**
     * 报告场景：习惯开始于 2018 年，当前 2026 年。
     * 旧的 min() 实现会把扫描起点拉到 2018 年（无界遍历）；
     * 取 max() 后窗口才真正约束住起点。
     */
    @Test
    fun oldStartDateDoesNotPullScanBackUnbounded() {
        val habit = daily("2018-01-01")
        val checkIns = checkInsOn(*september(1..17))
        val today = LocalDate.of(2026, 9, 17)

        // 窗口 5 天 → 只数 09-12 起的 6 天，而不是从 2018 年扫到今天
        assertEquals(
            6,
            StreakCalculator.calculate(habit, checkIns, today, historyWindowDays = 5).currentStreak
        )
        // 默认窗口下结果为 17（只有最近 17 天有打卡，更早全是未完成的排期）
        assertEquals(
            17,
            StreakCalculator.calculate(habit, checkIns, today).currentStreak
        )
    }

    /** 起始日之前的打卡不在排期内，不计入连续 */
    @Test
    fun checkInBeforeStartDateIsExcluded() {
        val habit = weekly("1", "2026-09-07")
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-08-31", "2026-09-07", "2026-09-14"),
            referenceToday = LocalDate.of(2026, 9, 17)
        )
        assertEquals(2, result.currentStreak)
    }

    /** 每周一连续三周都完成 → 3 */
    @Test
    fun weeklyMonday_threeConsecutiveWeeks() {
        // startDate 必须早于第一次打卡，否则起始日之前的记录不算排期内
        val habit = weekly("1", "2026-08-31")
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-08-31", "2026-09-07", "2026-09-14"),
            referenceToday = LocalDate.of(2026, 9, 17)
        )
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    /** 每周一漏掉最近一次 → 断签归零（不能因为「今天没排期」就误判为保持） */
    @Test
    fun weeklyMonday_missedLastOccurrenceResets() {
        val habit = weekly("1", "2026-09-07")
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-07"), // 09-14 那个周一没打卡
            referenceToday = LocalDate.of(2026, 9, 17)
        )
        assertEquals(0, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    /** 每月 15 号：中间 29 天没有排期，不应打断连续 */
    @Test
    fun monthlyFixedDay_notBrokenByGapDays() {
        val habit = monthly("15", "2026-06-15")
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-07-15", "2026-08-15"),
            referenceToday = LocalDate.of(2026, 9, 10)
        )
        assertEquals(2, result.currentStreak)
    }

    /** 每日习惯：今天还没完成不算断签，从昨天往前数 */
    @Test
    fun daily_todayNotYetDoneGrace() {
        val habit = daily()
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-16", "2026-09-17"),
            referenceToday = LocalDate.of(2026, 9, 18)
        )
        assertEquals(2, result.currentStreak)
    }

    /** 每日习惯：昨天也没打卡 → 断签 */
    @Test
    fun daily_missedYesterdayResets() {
        val habit = daily()
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-15"),
            referenceToday = LocalDate.of(2026, 9, 18)
        )
        assertEquals(0, result.currentStreak)
    }

    /** 计数器习惯：次数没达到目标不算完成 */
    @Test
    fun counter_belowTargetDoesNotCount() {
        val habit = Habit(
            name = "water",
            recurrenceType = HabitSchedule.TYPE_DAILY,
            startDate = "2026-09-01",
            isCounter = true,
            targetCount = 3
        )
        val notEnough = mapOf(
            "2026-09-17" to CheckIn(habitId = 1, date = "2026-09-17", count = 2, isCompleted = false)
        )
        assertEquals(
            0,
            StreakCalculator.calculate(habit, notEnough, LocalDate.of(2026, 9, 17)).currentStreak
        )

        val enough = mapOf(
            "2026-09-17" to CheckIn(habitId = 1, date = "2026-09-17", count = 3, isCompleted = true)
        )
        assertEquals(
            1,
            StreakCalculator.calculate(habit, enough, LocalDate.of(2026, 9, 17)).currentStreak
        )
    }

    /** 未来的打卡记录不得计入统计 */
    @Test
    fun futureCheckInsAreIgnored() {
        val habit = daily()
        val result = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-17", "2026-09-25"), // 09-25 是未来
            referenceToday = LocalDate.of(2026, 9, 18)
        )
        assertEquals(1, result.currentStreak)
    }

    /**
     * 单次计划不产生任何连续统计。
     *
     * 它全生命周期只有一天排期（matches() 仅在 startDate 命中），
     * 完成后必然算出 1/1 —— 而「连续 1 天、最长 1 天」对纯提醒日程是纯噪音。
     * 这里锁住「归零」这个语义，UI 侧据此换成说明性文案。
     */
    @Test
    fun oneShotScheduleNeverProducesStreak() {
        val habit = oneShot("2026-09-17")

        // 当天已完成
        val done = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-17"),
            referenceToday = LocalDate.of(2026, 9, 17)
        )
        assertEquals(0, done.currentStreak)
        assertEquals(0, done.longestStreak)

        // 次日回看：依然不产生连续
        val nextDay = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = checkInsOn("2026-09-17"),
            referenceToday = LocalDate.of(2026, 9, 18)
        )
        assertEquals(0, nextDay.currentStreak)
        assertEquals(0, nextDay.longestStreak)

        // 完全没打卡时同样是 0/0，不能因为排期只有一天就给出 1
        val missed = StreakCalculator.calculate(
            habit = habit,
            checkInsByDate = emptyMap(),
            referenceToday = LocalDate.of(2026, 9, 17)
        )
        assertEquals(0, missed.currentStreak)
        assertEquals(0, missed.longestStreak)
    }
}
