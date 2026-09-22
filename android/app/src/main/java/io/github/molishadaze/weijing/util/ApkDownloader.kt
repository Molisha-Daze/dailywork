package io.github.molishadaze.weijing.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 把新版本 APK 下载到应用私有目录。
 *
 * 存 `cacheDir/update/` 而不是外部存储：一是不需要任何存储权限（Android 10 起
 * 写公共目录要走 MediaStore，为一个安装包去碰那套东西完全不值），
 * 二是卸载即清、系统空间紧张时也能自动回收。
 *
 * ⚠️ 这个类只负责「把字节拿全」。**校验交给 [ApkInstaller.verify]，调起安装交给 [ApkInstaller]** ——
 * 下载、校验、安装三件事各自的失败模式完全不同，混在一个函数里会变成一团没法单测的 if。
 */
object ApkDownloader {

    private const val TAG = "ApkDownloader"
    private const val DIR_NAME = "update"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val BUFFER_SIZE = 64 * 1024

    /** 实测 Gitee 需要两跳、GitHub 需要一跳，留够余量。 */
    private const val MAX_REDIRECTS = 5

    /** 暂存目录，同时是 FileProvider 白名单里唯一暴露出去的路径（见 res/xml/update_file_paths.xml）。 */
    fun updateDir(context: Context): File =
        File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 下载 APK，返回落盘的文件。进度回调在 IO 线程触发，调用方自己决定怎么节流 / 切线程。
     *
     * [headers] 是调用方要求的附加请求头，**每一跳都会带上**。存在的唯一场景是
     * GitHub 的资产端点：那个地址不带 `Accept: application/octet-stream` 的话，
     * 回的是描述资产的 JSON 而不是 APK 本身（详见 `AppUpdateSource.downloadRequest`）。
     *
     * 每次开始前清空暂存目录：断点续传在 20MB 这个体量下收益很小，而
     * 「半截文件 + 『文件已存在』的判断」这个组合是自更新里最容易埋出「装上就崩」的坑，
     * 干脆不给它留机会。
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        expectedSize: Long,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = updateDir(context)
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, fileName)

        try {
            val conn = openFollowingRedirects(url, headers)
            try {
                val total = conn.contentLengthLong.takeIf { it > 0L } ?: expectedSize
                conn.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            // 协程被取消时立刻中止 —— 别把剩下的十几 MB 白下完再退出。
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (target.length() <= 0L) error("下载内容为空")
            target
        } catch (e: Exception) {
            // 失败就删干净：留个半截文件在这里，下一次只会让「文件是否可用」的判断更可疑。
            target.delete()
            Log.d(TAG, "APK 下载失败（${e.javaClass.simpleName}）")
            throw e
        }
    }

    /**
     * 打开连接并手动跟随 3xx。
     *
     * 不用 `instanceFollowRedirects = true`：HttpURLConnection 的自动跟随只在同协议间生效，
     * 而且跟随过程中会把自定义请求头丢掉 —— 对 GitHub 那条链路是致命的，
     * 丢了 `Accept: application/octet-stream` 就换不回文件。手动处理多十几行，
     * 但每一步都看得见，出错时日志里也有据可查。
     *
     * 目前两条链路的跳转情况：
     * - Gitee：`gitee.com/.../releases/download/...` → `/attach_files/{id}/download/...` → `foruda.gitee.com`（两跳）
     * - GitHub：`api.github.com/.../assets/{id}` → `release-assets.githubusercontent.com`（一跳）
     */
    private fun openFollowingRedirects(url: String, headers: Map<String, String>): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("User-Agent", "Weijing-Android")
                // 放在默认值之后设置，让调用方可以覆盖（比如换成自己的 UA）。
                // 空 key / 空 value 直接跳过：setRequestProperty 收到它们会抛异常，
                // 而这个异常要等到真正发请求时才暴露，很难查。
                headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) setRequestProperty(name, value)
                }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) error("重定向缺少 Location")
                current = URL(URL(current), location).toString()
            } else {
                if (code != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    error("服务器返回 HTTP $code")
                }
                return conn
            }
        }
        error("重定向次数超过 $MAX_REDIRECTS 次")
    }
}
