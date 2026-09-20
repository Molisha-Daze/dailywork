package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 独立计数器周期归零算法的边界验证。
 *
 * 2026 年 9 月的对照表（写死在注释里，方便改测试时不必再查日历）：
 *   09-14 周一 / 09-15 周二 / 09-16 周三 / 09-17 周四 / 09-18 周五 / 09-19 周六 / 09-20 周日
 *   09-21 周一（新的一周）
 *   10-01 周四，所在周为 09-28(一) ～ 10-04(日)
 *   2027-01-01 周五，所在周为 2026-12-28(一) ～ 2027-01-03(日) —— 跨年周
 *   2028 年 2 月有 29 天
 *
 * 依赖系统当前时间的路径全都被排除在本类之外（[CounterPeriodCalculator] 是纯函数），
 * 所以这些断言在任何一天跑结果都一致。
 */
class CounterPeriodCalculatorTest {

    private fun d(s: String): LocalDate = LocalDate.parse(s)

    private fun periodOf(
        period: String,
        intervalDays: Int = 1,
        anchor: String = "2026-09-14",
        date: String
    ): CounterPeriod? =
        CounterPeriodCalculator.periodOf(period, intervalDays, d(anchor), d(date))

    // ---------- 不归零 ----------

    @Test
    fun `不归零时算不出周期也不结算`() {
        assertFalse(CounterPeriodCalculator.isPeriodic(StandaloneCounter.RESET_NONE))
        assertNull(periodOf(StandaloneCounter.RESET_NONE, date = "2026-09-20"))
        assertNull(periodOfAnchorFor(StandaloneCounter.RESET_NONE, "2026-09-14"))
        assertFalse(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_NONE, 1, d("2026-09-01"), d("2026-12-31")
            )
        )
        assertNull(
            CounterPeriodCalculator.badgeText(StandaloneCounter.RESET_NONE, 1, d("2026-09-14"), d("2026-09-20"))
        )
        assertEquals("不自动归零", CounterPeriodCalculator.displayName(StandaloneCounter.RESET_NONE, 1))
    }

    private fun periodOfAnchorFor(period: String, anchor: String): CounterPeriod? =
        CounterPeriodCalculator.periodOfAnchor(period, 1, d(anchor))

    // ---------- 每日 ----------

    @Test
    fun `每日周期就是当天一天`() {
        val p = periodOf(StandaloneCounter.RESET_DAILY, date = "2026-09-20")!!
        assertEquals(d("2026-09-20"), p.start)
        assertEquals(d("2026-09-20"), p.end)
        assertEquals(1, p.lengthDays)

        // 昨天建的计数器，今天必须结算
        assertTrue(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_DAILY, 1, d("2026-09-19"), d("2026-09-20")
            )
        )
        // 同一天内反复调用不结算 —— 这是幂等性的关键
        assertFalse(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_DAILY, 1, d("2026-09-20"), d("2026-09-20")
            )
        )
    }

    // ---------- 每周 ----------

    @Test
    fun `每周对齐自然周而不是创建日`() {
        // 周日属于「09-14 周一」开始的那一周，所以周三建的计数器到周日都还没换周期
        val sunday = periodOf(StandaloneCounter.RESET_WEEKLY, anchor = "2026-09-16", date = "2026-09-20")!!
        assertEquals(d("2026-09-14"), sunday.start)
        assertEquals(d("2026-09-20"), sunday.end)
        assertEquals(7, sunday.lengthDays)

        assertFalse(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_WEEKLY, 1, d("2026-09-16"), d("2026-09-20")
            )
        )
        // 周一零点一过就得结算
        assertTrue(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_WEEKLY, 1, d("2026-09-20"), d("2026-09-21")
            )
        )
        // 周一当天自己就是新周期的首日
        assertEquals(
            d("2026-09-21"),
            periodOf(StandaloneCounter.RESET_WEEKLY, anchor = "2026-09-21", date = "2026-09-21")!!.start
        )
    }

    @Test
    fun `每周周期可以横跨年份`() {
        val p = periodOf(StandaloneCounter.RESET_WEEKLY, anchor = "2026-12-28", date = "2027-01-01")!!
        assertEquals(d("2026-12-28"), p.start)
        assertEquals(d("2027-01-03"), p.end)
        // 同一跨年周内不该结算
        assertFalse(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_WEEKLY, 1, d("2026-12-30"), d("2027-01-01")
            )
        )
    }

    // ---------- 每月 ----------

    @Test
    fun `每月对齐自然月并正确处理闰年二月`() {
        val sep = periodOf(StandaloneCounter.RESET_MONTHLY, anchor = "2026-09-14", date = "2026-09-20")!!
        assertEquals(d("2026-09-01"), sep.start)
        assertEquals(d("2026-09-30"), sep.end)

        val leapFeb = periodOf(StandaloneCounter.RESET_MONTHLY, anchor = "2028-02-05", date = "2028-02-10")!!
        assertEquals(d("2028-02-01"), leapFeb.start)
        assertEquals(d("2028-02-29"), leapFeb.end)
    }

    @Test
    fun `每月周期跨年时上一个月是十二月`() {
        // 锚点在 12 月、今天已经进了 1 月 —— 必须结算，且归档的是 12 月那一段
        assertTrue(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_MONTHLY, 1, d("2026-12-15"), d("2027-01-01")
            )
        )
        val dec = periodOfAnchorFor(StandaloneCounter.RESET_MONTHLY, "2026-12-15")!!
        assertEquals(d("2026-12-01"), dec.start)
        assertEquals(d("2026-12-31"), dec.end)

        val jan = periodOf(StandaloneCounter.RESET_MONTHLY, anchor = "2027-01-01", date = "2027-01-01")!!
        assertEquals(d("2027-01-01"), jan.start)
        assertEquals(d("2027-01-31"), jan.end)
    }

    // ---------- 每 N 天 ----------

    @Test
    fun `每N天从锚点起滚动`() {
        // 锚点 09-18，每 3 天：09-18~09-20 / 09-21~09-23 / 09-24~09-26
        val first = periodOf(StandaloneCounter.RESET_INTERVAL, 3, "2026-09-18", "2026-09-20")!!
        assertEquals(d("2026-09-18"), first.start)
        assertEquals(d("2026-09-20"), first.end)
        assertEquals(3, first.dayIndex(d("2026-09-20")))
        assertEquals(3, first.lengthDays)

        val second = periodOf(StandaloneCounter.RESET_INTERVAL, 3, "2026-09-18", "2026-09-21")!!
        assertEquals(d("2026-09-21"), second.start)
        assertEquals(d("2026-09-23"), second.end)
        assertEquals(1, second.dayIndex(d("2026-09-21")))

        assertFalse(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_INTERVAL, 3, d("2026-09-18"), d("2026-09-20")
            )
        )
        assertTrue(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_INTERVAL, 3, d("2026-09-18"), d("2026-09-21")
            )
        )
    }

    @Test
    fun `每N天周期可以跨月`() {
        val p = periodOf(StandaloneCounter.RESET_INTERVAL, 7, "2026-09-28", "2026-10-02")!!
        assertEquals(d("2026-09-28"), p.start)
        assertEquals(d("2026-10-04"), p.end)
    }

    @Test
    fun `每N天在跳过多个周期后仍落在正确的窗口`() {
        // 锚点 09-01、每 3 天，一直没打开 App 到 09-20：
        // 09-01 起第 7 个窗口 = 09-19~09-21，不该落回第一个窗口
        val p = periodOf(StandaloneCounter.RESET_INTERVAL, 3, "2026-09-01", "2026-09-20")!!
        assertEquals(d("2026-09-19"), p.start)
        assertEquals(d("2026-09-21"), p.end)
    }

    @Test
    fun `每N天的天数下限被夹到1`() {
        // intervalDays 传 0 或负数（脏数据）时按「每日」处理，而不是抛异常或死循环
        val p = periodOf(StandaloneCounter.RESET_INTERVAL, 0, "2026-09-18", "2026-09-20")!!
        assertEquals(d("2026-09-20"), p.start)
        assertEquals(d("2026-09-20"), p.end)
    }

    @Test
    fun `锚点晚于今天时不崩溃也不误判结算`() {
        // 数据异常（锚点在未来）时按第 0 个窗口处理：宁可显示得不精确，也不能让页面崩掉
        val p = periodOf(StandaloneCounter.RESET_INTERVAL, 5, "2026-09-25", "2026-09-20")!!
        assertEquals(d("2026-09-25"), p.start)
        assertEquals(d("2026-09-29"), p.end)
        assertFalse(
            CounterPeriodCalculator.needsRollover(
                StandaloneCounter.RESET_INTERVAL, 5, d("2026-09-25"), d("2026-09-20")
            )
        )
    }

    // ---------- 锚点初始化 ----------

    @Test
    fun `新建时锚点取当前周期首日而不是今天`() {
        // 周六（09-19）新建一个「每周」计数器，锚点应当是那周的周一 09-14，
        // 否则本周日一过就会立刻结算一次，等于白送用户一个空周期。
        assertEquals(
            d("2026-09-14"),
            CounterPeriodCalculator.currentPeriodStart(StandaloneCounter.RESET_WEEKLY, 1, d("2026-09-19"))
        )
        assertEquals(
            d("2026-09-01"),
            CounterPeriodCalculator.currentPeriodStart(StandaloneCounter.RESET_MONTHLY, 1, d("2026-09-19"))
        )
        assertEquals(
            d("2026-09-19"),
            CounterPeriodCalculator.currentPeriodStart(StandaloneCounter.RESET_DAILY, 1, d("2026-09-19"))
        )
        assertNull(
            CounterPeriodCalculator.currentPeriodStart(StandaloneCounter.RESET_NONE, 1, d("2026-09-19"))
        )
    }

    // ---------- 展示文案 ----------

    @Test
    fun `周期区间的文案在同年同月时省略重复信息`() {
        assertEquals(
            "9月20日",
            CounterPeriodCalculator.formatRange(CounterPeriod(d("2026-09-20"), d("2026-09-20")))
        )
        assertEquals(
            "9月14日 - 20日",
            CounterPeriodCalculator.formatRange(CounterPeriod(d("2026-09-14"), d("2026-09-20")))
        )
        assertEquals(
            "9月28日 - 10月4日",
            CounterPeriodCalculator.formatRange(CounterPeriod(d("2026-09-28"), d("2026-10-04")))
        )
        assertEquals(
            "2026年12月28日 - 2027年1月3日",
            CounterPeriodCalculator.formatRange(CounterPeriod(d("2026-12-28"), d("2027-01-03")))
        )
    }

    @Test
    fun `徽章文案`() {
        val today = d("2026-09-20")
        assertEquals("今天", CounterPeriodCalculator.badgeText(StandaloneCounter.RESET_DAILY, 1, today, today))
        assertEquals("本周", CounterPeriodCalculator.badgeText(StandaloneCounter.RESET_WEEKLY, 1, today, today))
        assertEquals("本月", CounterPeriodCalculator.badgeText(StandaloneCounter.RESET_MONTHLY, 1, today, today))
        assertEquals(
            "第 3/3 天",
            CounterPeriodCalculator.badgeText(StandaloneCounter.RESET_INTERVAL, 3, d("2026-09-18"), today)
        )
    }

    @Test
    fun `设置项标题`() {
        assertEquals("每日归零", CounterPeriodCalculator.displayName(StandaloneCounter.RESET_DAILY, 1))
        assertEquals("每周归零", CounterPeriodCalculator.displayName(StandaloneCounter.RESET_WEEKLY, 1))
        assertEquals("每月归零", CounterPeriodCalculator.displayName(StandaloneCounter.RESET_MONTHLY, 1))
        assertEquals("每 3 天归零", CounterPeriodCalculator.displayName(StandaloneCounter.RESET_INTERVAL, 3))
        // 脏数据兜底
        assertEquals("每 1 天归零", CounterPeriodCalculator.displayName(StandaloneCounter.RESET_INTERVAL, 0))
    }

    // ---------- 结算序列 ----------

    /**
     * 把三个纯函数按 Repository 的结算顺序串起来走一遍。
     *
     * 这条链路就是需求的验收标准：「今天喝三杯，明天归零，但昨天那三杯要留下」。
     * 单个函数的边界都测过之后，还必须确认它们**组合起来**的语义是对的 ——
     * 归档的是"锚点所在的那个周期"（也就是刚过去的那个），而不是推进之后的新周期。
     * 这一点错了，历史里就会出现"今天的日期配昨天的数量"这种看起来对、其实错位的记录。
     */
    @Test
    fun `每日计数器跨天结算后昨天的记录被保留`() {
        val period = StandaloneCounter.RESET_DAILY
        val day1 = d("2026-09-19")
        val day2 = d("2026-09-20")

        // —— 第一天建计数器：锚点落在当天，晚上喝了三杯 ——
        var anchor = day1
        var count = 3

        // —— 第二天打开 App，触发结算 ——
        assertTrue(CounterPeriodCalculator.needsRollover(period, 1, anchor, day2))

        // 归档的是锚点所在的周期（9/19 这一天），数量是刚过去的这一周期的累计值
        val archived = CounterPeriodCalculator.periodOfAnchor(period, 1, anchor)!!
        assertEquals(day1, archived.start)
        assertEquals(day1, archived.end)
        assertEquals(3, count)
        assertEquals("9月19日", CounterPeriodCalculator.formatRange(archived))

        // 然后才推进锚点 + 计数归零
        anchor = CounterPeriodCalculator.periodOf(period, 1, anchor, day2)!!.start
        count = 0
        assertEquals(day2, anchor)
        assertEquals(0, count)

        // 结算必须幂等：同一天再进几次页面都不该再归档一遍
        assertFalse(CounterPeriodCalculator.needsRollover(period, 1, anchor, day2))
    }

    @Test
    fun `每周计数器同一周内多次打开不会提前结算`() {
        val period = StandaloneCounter.RESET_WEEKLY
        // 周三建、周日打开 —— 都在 09-14 那一周里，不该产生任何归档
        val anchor = d("2026-09-16")
        for (day in listOf("2026-09-16", "2026-09-18", "2026-09-20")) {
            assertFalse(
                "打开日期 $day 时不该结算",
                CounterPeriodCalculator.needsRollover(period, 1, anchor, d(day))
            )
        }
        // 到了下周一才结算一次，且归档的是完整的一整周
        assertTrue(CounterPeriodCalculator.needsRollover(period, 1, anchor, d("2026-09-21")))
        val archived = CounterPeriodCalculator.periodOfAnchor(period, 1, anchor)!!
        assertEquals(d("2026-09-14"), archived.start)
        assertEquals(d("2026-09-20"), archived.end)
        assertEquals(7, archived.lengthDays)
    }
}
