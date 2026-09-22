package io.github.molishadaze.weijing.data

import android.util.Log
import io.github.molishadaze.weijing.util.AppUpdatePolicy
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 更新包的托管渠道。
 *
 * 存在的唯一理由是：**下载行为必须跟着渠道变**。两家的「发布信息」接口字段几乎一样，
 * 但「安装包怎么下」完全不同 ——
 *
 * - Gitee 的附件直链 `gitee.com/.../releases/download/...` 在国内直连可达（实测 302 两次后
 *   落到 `foruda.gitee.com`，HTTP 200）；
 * - GitHub 的附件直链 `github.com/.../releases/download/...` 在国内 **TCP 都建不起来**
 *   （实测 12s 超时、connect 耗时 0），只有 `api.github.com/.../assets/{id}` 配
 *   `Accept: application/octet-stream` 能拿到 —— 它在服务端 302 到
 *   `release-assets.githubusercontent.com`，那个域名是通的。
 *
 * 所以渠道不是「备注信息」，而是决定 [AppUpdateSource.downloadRequest] 走哪条路的分支依据。
 */
enum class UpdateChannel { GITEE, GITHUB }

/**
 * 某一条发布上找到的可用更新。
 *
 * [changelog] 直接取 release 的正文，发版时写的说明就是用户看到的更新日志 ——
 * 少维护一个字段就少一处会忘的地方。
 */
data class RemoteAppUpdate(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    /** 这条信息来自哪家。版本号相同、两家都有包时，用来决定下载走谁。 */
    val channel: UpdateChannel,
    /** 真正该去 GET 的地址。**不是**发布页上「下载」按钮那个地址。 */
    val apkUrl: String,
    /** 下载 [apkUrl] 时必须一起发的请求头。GitHub 靠 `Accept` 区分「JSON 元数据」和「二进制」。 */
    val apkHeaders: Map<String, String>,
    /** 安装包字节数。**0 表示未知** —— Gitee 的接口不返回这个字段。 */
    val sizeBytes: Long,
    val changelog: String
)

/**
 * 应用更新源：读发布页上的「最新发布」，换算出可以下载的 APK。
 *
 * ## 为什么是两家而不是一家
 *
 * 任一单一源都可能整条链路不可用，而且**失败是静默的** —— 用户只会看到「已是最新」，
 * 永远收不到更新，且没有任何报错可查。所以这里并行问两家、取版本号大的那条：
 *
 * - 两家都发了 → 走版本号大的那家
 * - Gitee 还没建仓库 / 没发版 → 自动只用 GitHub
 * - 一家挂了 → 另一家照常工作
 *
 * 代价是每次检查多一个请求。检查本身有 24 小时限频（见 `AppSettings.lastUpdateCheckAt`），
 * 一天两个请求，可以忽略。
 *
 * ## 为什么用 `/releases/latest` 而不是自己维护一份 version.json
 *
 * 「最新非 draft、非预发布的那条发布」这个语义由平台保证，发版动作就只有
 * 「打个 tag + 传个 APK」两步；自建清单则多一个「记得同步更新」的环节，
 * 而忘记同步的后果是用户永远收不到新版，且没有任何报错。
 *
 * ## 失败必须静默
 *
 * ⚠️ 与 [RemoteQuoteSource] 完全同构的取舍：这两家都由第三方托管，**没有任何可用性承诺**。
 * 所以这里任何失败都降级为 null ——「检查更新失败」对用户来说是「这次没发现新版」，
 * 绝不能弹错误对话框。
 *
 * ⚠️ [parse] 依赖 `org.json`，它在 JVM 单测里是空实现（调用即抛），
 * 所以这一层刻意写得极薄且不做判断，回归测试压在 [AppUpdatePolicy] 上（见 AppUpdatePolicyTest）。
 */
object AppUpdateSource {

    private const val TAG = "AppUpdateSource"

    /**
     * Gitee 仓库路径，形如 `用户名/仓库名`。
     *
     * 🚨 **留空 = 不启用 Gitee**：不会发出任何请求，只用 GitHub。
     * 填上之后 Gitee 成为主源 —— 国内直连，实测首页 connect 0.039s / TLS 0.12s，
     * APK 附件匿名可下（`/releases/download/` → `/attach_files/{id}/download/` → 200）。
     *
     * 仓库名不必和 GitHub 那边一致，但有两个硬性前提：
     * 1. **仓库必须公开** —— 私有仓库的发行版附件匿名拿不到，本功能会 100% 静默失败；
     * 2. 这个仓库里必须有一条发行版，且发行版里挂了 `.apk` 附件。
     *
     * ⚠️ 发版脚本 `android/release_gitee.py` 会从这个常量里读仓库路径，
     * 保持一致即可，不要在脚本里另写一份。
     */
    private const val GITEE_REPO = "weijingzhishi/weijing"

    /** GitHub 仓库路径 `用户名/仓库名`。 */
    private const val GITHUB_REPO = "Molisha-Daze/dailywork"

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    /** 一个候选更新源。 */
    private data class Source(
        val channel: UpdateChannel,
        val latestUrl: String,
        val accept: String
    )

    /**
     * 候选源列表。顺序**不表示优先级** —— 结果是取版本号大的那条，不是取第一个成功的，
     * 这里只决定并行请求的发起顺序。
     */
    private val SOURCES: List<Source> = buildList {
        if (GITEE_REPO.isNotBlank()) {
            add(
                Source(
                    channel = UpdateChannel.GITEE,
                    latestUrl = "https://gitee.com/api/v5/repos/$GITEE_REPO/releases/latest",
                    // Gitee 这个接口对 Accept 不挑（实测不带也一样 200），
                    // 但明确写出来能避免将来它加了内容协商时踩空。
                    accept = "application/json"
                )
            )
        }
        add(
            Source(
                channel = UpdateChannel.GITHUB,
                latestUrl = "https://api.github.com/repos/$GITHUB_REPO/releases/latest",
                accept = "application/vnd.github+json"
            )
        )
    }

    /**
     * 取可用的最新版本。两家并行问，返回版本号最大的那条；全失败返回 null。
     *
     * 并行而不是「先问 Gitee、失败再问 GitHub」，是因为后者的失败判定要等超时 ——
     * GitHub 一旦被丢包，用户要干等 5 秒才轮得到 Gitee 出结果。并行的话总耗时
     * 就是「较慢那家」的时间。
     */
    suspend fun fetchLatest(): RemoteAppUpdate? = coroutineScope {
        SOURCES
            .map { source -> async { fetchFrom(source) } }
            .awaitAll()
            .filterNotNull()
            .maxByOrNull { it.versionCode }
    }

    /**
     * 问单个源。任何异常（无网、DNS 失败、超时、仓库还没有 release、tag 不合规、
     * 接口结构变了）都返回 null。
     */
    private suspend fun fetchFrom(source: Source): RemoteAppUpdate? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(source.latestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                // 两家都**强制**要求 User-Agent（GitHub 缺了直接回 403）。
                setRequestProperty("User-Agent", "Weijing-Android")
                setRequestProperty("Accept", source.accept)
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                // 仓库还没发过 release 时这里会是 404，属于正常状态，不用惊动用户。
                Log.d(TAG, "${source.channel} 检查更新未成功：HTTP ${conn.responseCode}")
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(body, source.channel)
        } catch (e: CancellationException) {
            // 协程取消不是「失败」。吞掉它会让上层的取消语义失效。
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "${source.channel} 检查更新异常（${e.javaClass.simpleName}）")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 解析发布接口的响应体。
     *
     * 两家的 JSON 结构高度同构（`tag_name` / `body` / `assets[].name` /
     * `assets[].browser_download_url`），所以共用一份解析；差异只有三处：
     * 1. Gitee 的 `assets` 里**混着自动生成的源码包**（地址形如 `/archive/refs/tags/` 后面
     *    接 `.zip`，而不是 `/releases/download/`）——
     *    靠 [AppUpdatePolicy.pickApkAsset] 只认 `.apk` 后缀天然排除；
     * 2. Gitee 不返回 `size`，解析结果为 0（未知）；
     * 3. 下载地址的取法不同，见 [downloadRequest]。
     *
     * 字段缺失一律当作「没有可用更新」。
     */
    internal fun parse(body: String, channel: UpdateChannel): RemoteAppUpdate? = try {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        val code = AppUpdatePolicy.parseVersionCode(tag)
        val assets = json.optJSONArray("assets")

        if (code == null || assets == null || assets.length() == 0) {
            null
        } else {
            val names = (0 until assets.length()).map {
                assets.optJSONObject(it)?.optString("name").orEmpty()
            }
            val index = AppUpdatePolicy.pickApkAsset(names)
            val asset = index?.let { assets.optJSONObject(it) }
            val request = asset?.let { downloadRequest(it, channel) }

            if (asset == null || request == null) {
                null
            } else {
                RemoteAppUpdate(
                    versionCode = code,
                    versionName = AppUpdatePolicy.formatVersion(code),
                    tag = tag,
                    channel = channel,
                    apkUrl = request.url,
                    apkHeaders = request.headers,
                    // Gitee 没有这个字段，optLong 的默认值就是为它准备的。
                    sizeBytes = asset.optLong("size", 0L),
                    changelog = json.optString("body").trim()
                )
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "发布信息解析失败（${e.javaClass.simpleName}）")
        null
    }

    /** 一次下载所需的全部请求信息。 */
    private data class DownloadRequest(val url: String, val headers: Map<String, String>)

    /**
     * 决定「去哪个地址、带什么头」才能下到这个 APK。
     *
     * 🚨 **这是整个自更新里最容易写错的一处**：写错了的表现是「下载失败」而不是
     * 「编译失败」，并且在国内网络下必然复现、在开发者挂着代理的机器上必然不复现。
     *
     * - **GitHub**：资产对象的 `browser_download_url` 指向 `github.com`，国内不可达。
     *   必须改用它自己的 `api.github.com/.../releases/assets/{id}`（就是资产对象的 `url`
     *   字段），并且带上 `Accept: application/octet-stream` —— 不带这个头，
     *   API 会回一段描述资产的 JSON 而不是文件本身。
     *   实测：带上之后 HTTP 200，1.96MB / 1.4s。
     * - **Gitee**：直接用它给的 `browser_download_url`，不需要额外请求头。
     *
     * 返回 null 表示这个资产拿不到可用地址，这一条发布就作废。
     */
    private fun downloadRequest(asset: JSONObject, channel: UpdateChannel): DownloadRequest? {
        val browserUrl = asset.optString("browser_download_url").trim()

        return when (channel) {
            UpdateChannel.GITEE ->
                browserUrl.takeIf { it.isNotEmpty() }?.let { DownloadRequest(it, emptyMap()) }

            UpdateChannel.GITHUB -> {
                val apiUrl = asset.optString("url").trim()
                when {
                    apiUrl.isNotEmpty() ->
                        DownloadRequest(apiUrl, mapOf("Accept" to "application/octet-stream"))

                    // 兜底：这条路径在国内走不通（github.com 不可达），但比什么地址都不给强，
                    // 而且挂了代理 / 海外用户走它是正常的。
                    browserUrl.isNotEmpty() -> DownloadRequest(browserUrl, emptyMap())

                    else -> null
                }
            }
        }
    }
}
