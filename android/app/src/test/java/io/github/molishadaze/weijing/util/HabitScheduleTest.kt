package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HabitSchedule.nextScheduledDate] 的边界验证 —— 今日页「即将到来」的取数逻辑。
 *
 * 2026 年 9 月对照表（写死在注释里，改测试时不必再查日历）：
 *   09-20 周日 / 09-21 周一 / 09-22 周二 / 09-23 周三 / 09-24 周四
 *   09-25 周五 / 09-26 周六 / 09-27 周日
 *   2026-10-15 周四，2026-10-31 周六
 *   2026 年 2 月只有 28 天，所以「每月 31 日」从 2 月会一路跳到 3 月 31 日（58 天后）
 */
class HabitScheduleTest {

    private fun d(s: String): LocalDate = LocalDate.parse(s)

    private fun daily(start: String = "2026-09-01", end: String? = null) = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_DAILY,
        startDate = start,
        endDate = end
    )

    private fun weekly(days: String, start: String = "2026-09-14") = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_WEEKLY,
        startDate = start,
        weeklyDays = days
    )

    private fun monthly(days: String, start: String = "2026-06-01") = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_MONTHLY,
        startDate = start,
        monthlyDays = days
    )

    private fun interval(every: Int, start: String) = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_INTERVAL,
        startDate = start,
        intervalDays = every
    )

    private fun single(on: String) = Habit(
        name = "h",
        recurrenceType = HabitSchedule.TYPE_NONE,
        startDate = on
    )

    // ---------- 基本语义 ----------

    /** 起点当天必须被排除：今天已经排期的习惯，问的是「下一次」，不该把今天又报一遍。 */
    @Test
    fun 起点当天不计入下一次() {
        assertEquals(d("2026-09-21"), HabitSchedule.nextScheduledDate(daily(), d("2026-09-20")))
    }

    @Test
    fun 每天就是明天() {
        assertEquals(d("2026-09-21"), HabitSchedule.nextScheduledDate(daily(), d("2026-09-20")))
    }

    @Test
    fun 每周只跳到最近的一个选中日() {
        // 周日出发：一三五 → 周一；二四 → 周二
        assertEquals(d("2026-09-21"), HabitSchedule.nextScheduledDate(weekly("1,3,5"), d("2026-09-20")))
        assertEquals(d("2026-09-22"), HabitSchedule.nextScheduledDate(weekly("2,4"), d("2026-09-20")))
        // 周三出发、一三五 → 周五（不是下周一）
        assertEquals(d("2026-09-25"), HabitSchedule.nextScheduledDate(weekly("1,3,5"), d("2026-09-23")))
    }

    @Test
    fun 每月跳到下个月的指定日() {
        assertEquals(d("2026-10-15"), HabitSchedule.nextScheduledDate(monthly("15"), d("2026-09-20")))
        // 还没到本月的 25 号 → 就是本月 25 号
        assertEquals(d("2026-09-25"), HabitSchedule.nextScheduledDate(monthly("25"), d("2026-09-20")))
    }

    /**
     * 这条就是「窗口取 60 而不是 30」的理由。
     *
     * 每月 31 日 + 2 月只有 28 天 → 下一次落在 3 月 31 日，距今 58 天。
     * 窗口 30 会漏掉它，用户看到的就是「我排了计划却什么都不显示」。
     */
    @Test
    fun 每月31日在短月后需要60天窗口() {
        // 起始日必须早于查询日，否则「还没开始」会先把所有日期挡掉
        val habit = monthly("31", start = "2026-01-01")
        assertEquals(d("2026-03-31"), HabitSchedule.nextScheduledDate(habit, d("2026-02-01")))
        assertNull(
            "窗口 30 天装不下跨短月的 31 日",
            HabitSchedule.nextScheduledDate(habit, d("2026-02-01"), windowDays = 30)
        )
    }

    @Test
    fun 每N天从锚点滚动到下一个命中日() {
        // 锚点 09-18、每 3 天：09-18 / 09-21 / 09-24 …
        assertEquals(d("2026-09-21"), HabitSchedule.nextScheduledDate(interval(3, "2026-09-18"), d("2026-09-20")))
        // 锚点就是今天 → 下一次是今天 +3
        assertEquals(d("2026-09-23"), HabitSchedule.nextScheduledDate(interval(3, "2026-09-20"), d("2026-09-20")))
    }

    // ---------- 单次计划 ----------

    @Test
    fun 单次计划在未来时能查到() {
        assertEquals(d("2026-09-25"), HabitSchedule.nextScheduledDate(single("2026-09-25"), d("2026-09-20")))
    }

    /** 单次计划的日期已经过去 → 它不会再出现，不应该出现在「即将到来」里。 */
    @Test
    fun 已过期的单次计划查不到() {
        assertNull(HabitSchedule.nextScheduledDate(single("2026-09-01"), d("2026-09-20")))
    }

    // ---------- 边界与兜底 ----------

    @Test
    fun 习惯已结束则查不到() {
        // 结束日就在明天 → 明天仍算
        assertEquals(
            d("2026-09-21"),
            HabitSchedule.nextScheduledDate(daily(end = "2026-09-21"), d("2026-09-20"))
        )
        // 结束日在今天之前 → 之后都不排期
        assertNull(HabitSchedule.nextScheduledDate(daily(end = "2026-09-15"), d("2026-09-20")))
    }

    /** 每周却一天都没勾（脏数据）→ 不能死循环，也不能误报「明天」。 */
    @Test
    fun 每周但没选任何天时查不到() {
        assertNull(HabitSchedule.nextScheduledDate(weekly(""), d("2026-09-20")))
        assertNull(HabitSchedule.nextScheduledDate(monthly(""), d("2026-09-20")))
    }

    @Test
    fun 窗口为零或负数时直接返回空() {
        assertNull(HabitSchedule.nextScheduledDate(daily(), d("2026-09-20"), windowDays = 0))
        assertNull(HabitSchedule.nextScheduledDate(daily(), d("2026-09-20"), windowDays = -1))
    }

    /** 窗口刚好差一天时应当查不到 —— 证明窗口是被严格执行的上界，不是「至少 N 天」。 */
    @Test
    fun 窗口长度是硬上界() {
        // 周一（1 天后）在窗口内，周五（5 天后）在窗口外
        assertEquals(d("2026-09-21"), HabitSchedule.nextScheduledDate(weekly("1"), d("2026-09-20"), windowDays = 1))
        assertNull(HabitSchedule.nextScheduledDate(weekly("5"), d("2026-09-20"), windowDays = 1))
    }

    /**
     * 循环习惯在窗口内只返回**一个**日期 —— 这正是「循环日程只显示一个」的落点。
     *
     * 函数签名返回单个 LocalDate，所以这里验的是「拿到的是最近的那一个」，
     * 而不是把窗口内所有命中日都交出去让 UI 自己挑。
     */
    @Test
    fun 循环习惯返回最近的一次而不是全部() {
        val habit = daily()
        val next = HabitSchedule.nextScheduledDate(habit, d("2026-09-20"))!!
        // 下一个是明天；不是 09-25、也不是窗口末尾的 11-19
        assertEquals(d("2026-09-21"), next)
        // 从「下一次」再往后问，才拿到再下一次，说明返回的单点是可递推的
        assertEquals(d("2026-09-22"), HabitSchedule.nextScheduledDate(habit, next))
    }

    // ---------- 一次性任务判定（isOneShot）----------
    //
    // 这组用例守的是「卡片上那行 连续/最长 该不该出现」：
    // 只要习惯一生只排期一次，那行就必然是「连续 1 天 · 最长 1 天」的噪音。
    // 关键点是**不能只看 recurrenceType**，否则「每天 + 单日区间」会漏判。

    @Test
    fun 显式单次计划是一次性任务() {
        assertTrue(HabitSchedule.isOneShot(single("2026-09-22")))
        // 区间写得多远都不影响：TYPE_NONE 的排期判定只认起点那一天
        assertTrue(HabitSchedule.isOneShot(single("2026-09-22").copy(endDate = "2027-09-22")))
    }

    /** 常规循环计划不能被误判成一次性，否则「连续天数」会被整片摘掉。 */
    @Test
    fun 常规循环计划不是一次性任务() {
        assertFalse(HabitSchedule.isOneShot(daily()))
        assertFalse(HabitSchedule.isOneShot(daily(end = "2026-12-31")))
        assertFalse(HabitSchedule.isOneShot(weekly("1,3,5")))
        assertFalse(HabitSchedule.isOneShot(monthly("15")))
        assertFalse(HabitSchedule.isOneShot(interval(3, "2026-09-18")))
    }

    /** 「每天」+ 起止同一天：用户视角就是一次性的，哪怕底层 type 是 daily。 */
    @Test
    fun 每天加单日生效区间算一次性任务() {
        assertTrue(HabitSchedule.isOneShot(daily(start = "2026-09-22", end = "2026-09-22")))
    }

    @Test
    fun 每天加多日区间仍不是一次性任务() {
        assertFalse(HabitSchedule.isOneShot(daily(start = "2026-09-20", end = "2026-09-22")))
    }

    /** 每周三 + 区间只罩住那一个周三 → 一次性；罩住两个周三 → 不是。 */
    @Test
    fun 每周加单日区间按落点个数判定() {
        // 2026-09-21 周一 → 2026-09-23 周三，区间内只有 09-23 一个周三
        assertTrue(HabitSchedule.isOneShot(weekly("3", "2026-09-21").copy(endDate = "2026-09-23")))
        // 延长到 09-30 → 09-23 与 09-30 两个周三
        assertFalse(HabitSchedule.isOneShot(weekly("3", "2026-09-21").copy(endDate = "2026-09-30")))
    }

    @Test
    fun 每月加单日区间按落点个数判定() {
        assertTrue(HabitSchedule.isOneShot(monthly("15", "2026-09-01").copy(endDate = "2026-09-30")))
        assertFalse(HabitSchedule.isOneShot(monthly("15", "2026-09-01").copy(endDate = "2026-10-31")))
    }

    /** 间隔计划：区间短于间隔 → 只落一次；刚好够到第二个锚点 → 不是。 */
    @Test
    fun 每N天按区间是否够到第二跳判定() {
        assertTrue(HabitSchedule.isOneShot(interval(3, "2026-09-20").copy(endDate = "2026-09-22")))
        assertFalse(HabitSchedule.isOneShot(interval(3, "2026-09-20").copy(endDate = "2026-09-23")))
    }

    /** 脏数据兜底：结束日早于开始日、每周一天没勾，都不能算「一次性」而把排期说明混淆掉。 */
    @Test
    fun 异常区间不会被判成一次性任务() {
        assertFalse(HabitSchedule.isOneShot(daily(start = "2026-09-22", end = "2026-09-01")))
        assertFalse(HabitSchedule.isOneShot(weekly("", "2026-09-21").copy(endDate = "2026-09-23")))
    }
}
