package io.github.molishadaze.weijing.data

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 远程语录接口返回的一条原始记录。
 *
 * 除正文与出处外，还带上 [collectionId] 和 [tags]：接口本身不支持按合集筛选，
 * 唯一能挡住不合适内容的办法就是**拿到之后自己判**，所以这两个字段必须解析出来，
 * 交给 [QuotePolicy] 决定去留。
 */
data class RemoteQuote(
    val text: String,
    val source: String,
    val collectionId: String,
    val tags: List<String>
)

/**
 * 远程格言源：inBox Card 的公开随机语录接口。
 *
 * 端点：`https://card.gudong.site/api/random-note`（免 Key、无鉴权、支持 CORS）
 *
 * 🚨 **接入这个源时必须记住它的性质**：
 * - 它由个人开发者维护，**没有任何可用性承诺** —— 随时可能改协议、限流或下线。
 *   所以它永远只是「锦上添花」，内置库 [DailyQuotes] 才是保底；这里任何失败都必须
 *   静默降级，绝不能让今日页出现空白或转圈。
 * - 它**不接受任何查询参数**（官方文档确认），无法按合集 / 日期 / 种子取句。
 *   想要什么就只能在客户端筛（见 [QuotePolicy]）。
 * - 实测响应 0.9~4.1 秒（首包偏慢），因此必须有短超时 + 异步，绝不可同步调用。
 *
 * ⚠️ 本类只做「取回来」一件事，**不做**内容判断、不写缓存、不碰 UI ——
 * 判断在 [QuotePolicy]（纯函数、可单测），缓存在 `AppSettings`，由 ViewModel 串起来。
 *
 * ⚠️ [parse] 依赖 `org.json`，它在 JVM 单测里是空实现（调用即抛），
 * 所以这一层刻意写得极薄且无分支逻辑，回归测试压在 [QuotePolicy] 上。
 */
object RemoteQuoteSource {

    private const val TAG = "RemoteQuoteSource"
    private const val ENDPOINT = "https://card.gudong.site/api/random-note"

    /**
     * 超时给得比较短：这是个「有更好、没有也行」的增强项，
     * 让用户为了它多等 10 秒是完全不划算的取舍。连不上就立刻回退本地库。
     */
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 3_000

    /**
     * 取一条随机语录。任何异常（无网络、DNS 失败、超时、证书问题、JSON 格式变了）
     * 都返回 null，由调用方退回内置库。
     */
    suspend fun fetch(): RemoteQuote? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                // 只带一个能说明来源的 UA，便于对方在日志里认出流量。
                // 不带任何设备标识 / 用户标识 / Cookie —— 这个 App 没有账号体系，
                // 没有理由在请求里泄露任何一点用户信息。
                setRequestProperty("User-Agent", "Weijing (Android)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(body)
        } catch (e: Exception) {
            // 不区分异常类型：对用户来说「没网」「超时」「接口挂了」是同一件事 ——
            // 都该安静地看到内置库里的句子。这里只留一行 debug 日志。
            Log.d(TAG, "远程格言获取失败（${e.javaClass.simpleName}），回退内置格言库")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** 解析响应体。字段缺失一律降级为默认值，解析不出正文就当作失败。 */
    internal fun parse(body: String): RemoteQuote? = try {
        val json = JSONObject(body)
        val content = json.optString("content").trim()
        if (content.isEmpty()) {
            null
        } else {
            val source = json.optString("source").trim()
                .ifEmpty { json.optString("collectionName").trim() }
                .ifEmpty { "未详" }
            val tags = json.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList()
            RemoteQuote(
                text = content,
                source = source,
                collectionId = json.optString("collectionId").trim(),
                tags = tags
            )
        }
    } catch (e: Exception) {
        Log.d(TAG, "远程格言解析失败（${e.javaClass.simpleName}）")
        null
    }
}
