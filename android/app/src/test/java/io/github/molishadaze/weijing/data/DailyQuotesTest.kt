package io.github.molishadaze.weijing.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 格言库与轮换逻辑的回归测试。
 *
 * 这个文件守着两件事：
 *   1. **数据卫生** —— 格言是手写的常量，最容易出的是「重复粘贴同一句」、
 *      「多敲了一个换行」、「出处忘了填」这类肉眼扫不出来的问题；
 *   2. **轮换不跳号** —— 用 `toEpochDay()` 取模而不是 `dayOfYear`，
 *      就是为了跨年时不把第 1 条连出两次。这条是最容易改错、也最难发现的，
 *      所以专门有跨年边界的用例。
 */
class DailyQuotesTest {

    private val all = DailyQuotes.ALL

    @Test
    fun `格言数量够用`() {
        // 数量太少会让用户三天内看到重复的句子，轮换就没意义了
        assertTrue("格言条数偏少：${all.size}", all.size >= 30)
    }

    @Test
    fun `正文与出处都不为空且格式干净`() {
        all.forEachIndexed { i, q ->
            assertTrue("第 $i 条正文为空", q.text.isNotBlank())
            assertTrue("第 $i 条出处为空：${q.text}", q.source.isNotBlank())
            assertEquals("第 $i 条正文首尾有空白：'${q.text}'", q.text.trim(), q.text)
            assertEquals("第 $i 条出处首尾有空白：'${q.source}'", q.source.trim(), q.source)
            assertTrue("第 $i 条正文含换行：${q.text}", !q.text.contains('\n'))
            assertTrue("第 $i 条正文过长（${q.text.length} 字）：${q.text}", q.text.length <= 40)
            assertTrue("第 $i 条出处过长：${q.source}", q.source.length <= 30)
        }
    }

    @Test
    fun `没有重复的格言`() {
        val dup = all.groupBy { it.text }.filterValues { it.size > 1 }.keys
        assertTrue("存在重复格言：$dup", dup.isEmpty())
    }

    @Test
    fun `同一天多次取结果是同一条`() {
        val d = LocalDate.of(2026, 9, 21)
        assertEquals(DailyQuotes.forDate(d), DailyQuotes.forDate(d))
    }

    @Test
    fun `连续若干天恰好把整个库轮换一遍且不重复`() {
        val start = LocalDate.of(2026, 1, 1)
        val seen = (0 until all.size).map { DailyQuotes.forDate(start.plusDays(it.toLong())) }
        assertEquals("一个轮换周期内应恰好覆盖全部条目", all.size, seen.toSet().size)
        assertEquals("轮换应遍历整个库", all.toSet(), seen.toSet())
    }

    @Test
    fun `跨年时不会把第一条连出两次`() {
        // 这是 toEpochDay() vs dayOfYear 的分水岭：
        // 若改用 dayOfYear，2027-01-01 会退回索引 0，与 2026-12-31 的句子重复
        val dec31 = DailyQuotes.forDate(LocalDate.of(2026, 12, 31))
        val jan1 = DailyQuotes.forDate(LocalDate.of(2027, 1, 1))
        assertNotEquals("跨年当天出现了重复格言", dec31, jan1)

        // 而且必须是紧接着的下一条
        val idx = all.indexOf(dec31).toLong()
        assertEquals(all[(idx + 1).mod(all.size.toLong()).toInt()], jan1)
    }

    @Test
    fun `闰年 2 月 29 日也能正常取值`() {
        val leap = LocalDate.of(2028, 2, 29)
        DailyQuotes.forDate(leap)
        assertNotEquals(DailyQuotes.forDate(leap.minusDays(1)), DailyQuotes.forDate(leap))
    }

    @Test
    fun `遥远日期与负数 epochDay 不会越界`() {
        // mod 对负数返回非负，这是它能安全取模的前提；用 rem 会直接 IndexOutOfBounds
        listOf(
            LocalDate.of(1970, 1, 1),
            LocalDate.of(1900, 1, 1),
            LocalDate.of(2038, 1, 19),
            LocalDate.of(2100, 12, 31)
        ).forEach { d ->
            val q = DailyQuotes.forDate(d)
            assertTrue("$d 取到了空格言", q.text.isNotBlank())
        }
    }
}
