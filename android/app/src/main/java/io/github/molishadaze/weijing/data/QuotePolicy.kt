package io.github.molishadaze.weijing.data

/**
 * 「每日格言」的来源策略：**远程来的句子，什么情况下才允许替换掉内置库的**。
 *
 * 🚨 为什么需要这个类：`/api/random-note` 是**完全随机且不收参数**的接口
 * （官方文档已确认，无法按合集 / 日期 / 种子取句），也就是说拿回来的东西
 * 一定是「人给的什么就有什么」。实测连打 8 次，8 条里 3 条是佛学内容
 * （八正道、四无量心、《涅槃经》「因果循环，报应不爽」）—— 一个习惯打卡 App
 * 每天推一句佛教教义，显然不是用户要的。接口不改，那就只能在客户端筛。
 *
 * 这里是**纯函数**，没有 IO、不碰 Android API，所以能直接在 JVM 单测里穷举验证 ——
 * 而网络层 [RemoteQuoteSource] 刻意被削到只剩「取和解析」，没有可测的分支。
 */
object QuotePolicy {

    /**
     * 不采用的合集。取值来自接口返回的 `collectionId` 字段。
     *
     * 实测见过的合集：`sun-tzu`（孙子兵法）、`buddhism`（佛学智慧）、`yuhua`（余华）、
     * `seneca`（塞内卡·斯多葛）、`duan-yongping`（段永平商业语录）、`luxun`（鲁迅）、
     * `moyan`（莫言）。目前只挡宗教类 —— 其余的「贴不贴合打卡场景」交给用户自己感受，
     * 挡太多会让远程句子几乎全被否掉，回退逻辑就白写了。
     *
     * 想加就加一行（必须小写，比较时会 lowercase）。
     */
    val BLOCKED_COLLECTIONS = setOf(
        "buddhism" // 佛学智慧：宗教内容，不适合出现在工具类 App 的每日格言里
    )

    /**
     * tag 里出现这些词也挡掉。
     *
     * 与 [BLOCKED_COLLECTIONS] 是双保险：合集 id 是站点内部命名，随时可能改名，
     * 但 tag 上的「佛教/修行」这类词更贴近内容本身，顺带能挡住新开的宗教合集。
     */
    private val BLOCKED_TAG_KEYWORDS = listOf("佛教", "佛学", "宗教", "修行", "禅")

    /** 太短的句子撑不起一张卡片（「慈、悲、喜、舍。」这种），太长的放不下。 */
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 40

    /** 这条远程句子能不能用。 */
    fun isAcceptable(content: String, collectionId: String, tags: List<String>): Boolean {
        val text = content.trim()
        if (text.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (text.contains('\n')) return false
        if (collectionId.trim().lowercase() in BLOCKED_COLLECTIONS) return false
        if (tags.any { tag -> BLOCKED_TAG_KEYWORDS.any { it in tag } }) return false
        return true
    }

    /**
     * 远程记录 → 可显示的格言。被判定不可用时返回 null，
     * 调用方应当**什么都不做**（保留内置库当天的句子），而不是换一条没经过校验的。
     */
    fun toDailyQuote(remote: RemoteQuote): DailyQuote? =
        if (isAcceptable(remote.text, remote.collectionId, remote.tags)) {
            DailyQuote(text = remote.text.trim(), source = remote.source.trim())
        } else {
            null
        }
}
