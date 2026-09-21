package io.github.molishadaze.weijing.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 远程格言准入规则的回归测试。
 *
 * 🚨 这里的用例**全部来自实测数据**，不是凭空构造的：
 * 2026-09-21 连打 `https://card.gudong.site/api/random-note` 8 次，
 * 8 条里有 3 条是佛学内容，占比和观感都不能接受。那些样本被原样写成用例，
 * 就是为了防止以后有人「顺手放宽」过滤规则时悄悄把宗教内容放回来。
 */
class QuotePolicyTest {

    private fun remote(
        text: String,
        collectionId: String = "luxun",
        tags: List<String> = emptyList(),
        source: String = "《杂文》"
    ) = RemoteQuote(text = text, source = source, collectionId = collectionId, tags = tags)

    // ---------- 实测语料：这些必须被挡掉 ----------

    @Test
    fun `实测的三条佛学内容全部被拒`() {
        val buddhistSamples = listOf(
            remote("正见、正思惟、正语、正业、正命、正精进、正念、正定。", "buddhism", listOf("佛教/修行")),
            remote("慈、悲、喜、舍。", "buddhism", listOf("佛教/修行")),
            remote("因果循环，报应不爽。", "buddhism", listOf("佛教/因果"), "《涅槃经》")
        )
        buddhistSamples.forEach { sample ->
            assertFalse("佛教内容漏过了：${sample.text}", QuotePolicy.isAcceptable(sample.text, sample.collectionId, sample.tags))
            assertNull("佛教内容被转成了可显示的格言：${sample.text}", QuotePolicy.toDailyQuote(sample))
        }
    }

    @Test
    fun `实测的合格内容应当通过`() {
        val goodSamples = listOf(
            remote("生命如同故事，重要的不是它有多长，而是它有多好。", "seneca", listOf("塞内卡/生命意义")),
            remote("猛兽总是独行，牛羊才成群结队。", "luxun", listOf("鲁迅/独立精神")),
            remote("小说的结构如同建筑，你需要强大的逻辑和精密的安排。", "yuhua", listOf("余华/文学与叙事"))
        )
        goodSamples.forEach { sample ->
            assertTrue("合格内容被误杀：${sample.text}", QuotePolicy.isAcceptable(sample.text, sample.collectionId, sample.tags))
            assertEquals(sample.text, QuotePolicy.toDailyQuote(sample)?.text)
        }
    }

    // ---------- 合集与 tag 的匹配细节 ----------

    @Test
    fun `合集 id 大小写不敏感`() {
        assertFalse(QuotePolicy.isAcceptable("一句正常长度的话。", "BUDDHISM", emptyList()))
        assertFalse(QuotePolicy.isAcceptable("一句正常长度的话。", " Buddhism ", emptyList()))
    }

    @Test
    fun `合集改名也挡得住 —— tag 关键词是第二道闸`() {
        // 站点随时可能把合集 id 从 buddhism 改成别的；只要 tag 还带佛教标记就依然挡得住
        assertFalse(QuotePolicy.isAcceptable("一句正常长度的话。", "zen-2027", listOf("佛教/修行")))
        assertFalse(QuotePolicy.isAcceptable("一句正常长度的话。", "zen-2027", listOf("宗教/禅修")))
    }

    @Test
    fun `tag 里出现关键词是包含匹配而非全等`() {
        assertFalse(QuotePolicy.isAcceptable("一句正常长度的话。", "unknown", listOf("某某/佛学智慧")))
    }

    // ---------- 长度与格式 ----------

    @Test
    fun `长度边界`() {
        assertFalse("少于 4 字应当拒绝", QuotePolicy.isAcceptable("三个字", "luxun", emptyList()))
        assertTrue("恰好 4 字应当通过", QuotePolicy.isAcceptable("知行合一", "luxun", emptyList()))
        assertTrue("恰好 40 字应当通过", QuotePolicy.isAcceptable("字".repeat(40), "luxun", emptyList()))
        assertFalse("超过 40 字应当拒绝", QuotePolicy.isAcceptable("字".repeat(41), "luxun", emptyList()))
    }

    @Test
    fun `长度按 trim 之后计算`() {
        // 前后空白不该让一句合法的短句被误判为超长、也不该让空串蒙混过关
        assertTrue(QuotePolicy.isAcceptable("  知行合一  ", "luxun", emptyList()))
        assertFalse(QuotePolicy.isAcceptable("        ", "luxun", emptyList()))
    }

    @Test
    fun `带换行的内容一律拒绝`() {
        // 换行会把卡片行高与竖线高度算错，属于格式层面必须挡下的东西
        assertFalse(QuotePolicy.isAcceptable("第一行\n第二行", "luxun", emptyList()))
    }

    @Test
    fun `转换结果会去掉正文与出处的首尾空白`() {
        val q = QuotePolicy.toDailyQuote(
            remote("  知行合一  ", source = "  王阳明  ")
        )
        assertEquals("知行合一", q?.text)
        assertEquals("王阳明", q?.source)
    }
}
