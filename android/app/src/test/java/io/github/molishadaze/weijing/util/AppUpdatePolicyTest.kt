package io.github.molishadaze.weijing.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自更新纯规则层的边界验证。
 *
 * 这一层之所以单独测，是因为它出错的方式最难发现：tag 解析错了、或者发布页上挑错了资产，
 * 表现都是「检查更新没反应」——不崩、不报错、不打日志，只能靠人去猜。
 * 而把它做成不依赖 `android.*` 的纯函数之后，这些组合在 JVM 上几毫秒就能跑完。
 *
 * 另一半（网络请求与 JSON 解析，见 `AppUpdateSource`）在这里测不了：
 * 它依赖 `org.json`，而 `android.jar` 在 JVM 单测里是空实现，`new JSONObject(...)` 直接抛。
 * 所以那边刻意写得极薄，判断逻辑全部推到本类覆盖的这几个函数里。
 *
 * 版本号编码规则与 `app/build.gradle.kts` 里的 `versionCode` **必须一致**：
 * `major * 10000 + minor * 100 + patch`。两边一旦不同步，就会出现
 * 「明明发了新版却提示已是最新」，且两边各自看都完全正常。
 */
class AppUpdatePolicyTest {

    private fun codeOf(tag: String): Int =
        AppUpdatePolicy.parseVersionCode(tag) ?: error("tag 本应能解析成版本号：$tag")

    // ---------- tag → versionCode ----------

    @Test
    fun `常见的 tag 写法都收敛到同一个版本号`() {
        assertEquals(10301, codeOf("v1.3.1"))
        assertEquals(10301, codeOf("1.3.1"))
        assertEquals(10300, codeOf("v1.3"))
        assertEquals(10300, codeOf("1.3.0"))
        assertEquals(20000, codeOf("v2.0"))
        assertEquals(10000, codeOf("v1.0.0"))
    }

    @Test
    fun `tag 前后的空白被忽略`() {
        // 手打 tag 时多一个空格是常事（尤其在手机上改 release）。
        // 不 trim 的话这个 tag 会静默作废 —— 用户永远收不到那次更新。
        assertEquals(10301, codeOf("  v1.3.1  "))
        assertEquals(10301, codeOf("\tv1.3.1\n"))
    }

    @Test
    fun `同一版本在两家用不同 tag 写法时算出的号一致`() {
        // 双源（Gitee + GitHub）取版本号大的那条。如果「v」前缀会造成号不同，
        // 就会出现「两家都是 1.3.1，却判定成一方更新」这种无法解释的提示。
        assertEquals(codeOf("v1.3.1"), codeOf("1.3.1"))
        assertEquals(codeOf("v2.0"), codeOf("2.0.0"))
    }

    @Test
    fun `预发布 tag 一律不认`() {
        // 本 App 的分发方式（群 + 固定地址）没有灰度概念，
        // 让预发 tag 混进来只会把半成品推给所有人。
        assertNull(AppUpdatePolicy.parseVersionCode("v1.3.1-beta"))
        assertNull(AppUpdatePolicy.parseVersionCode("v1.3.1-rc1"))
        assertNull(AppUpdatePolicy.parseVersionCode("v1.3.1+dev"))
    }

    @Test
    fun `不合法或缺失段位的 tag 返回 null`() {
        assertNull(AppUpdatePolicy.parseVersionCode(""))
        assertNull(AppUpdatePolicy.parseVersionCode("   "))
        assertNull(AppUpdatePolicy.parseVersionCode("latest"))
        assertNull(AppUpdatePolicy.parseVersionCode("release-1.3"))
        assertNull(AppUpdatePolicy.parseVersionCode("1"))          // 缺 minor
        assertNull(AppUpdatePolicy.parseVersionCode("v1.3.1.4"))   // 多了一段
        assertNull(AppUpdatePolicy.parseVersionCode("v1.100.0"))   // minor 超两位
        assertNull(AppUpdatePolicy.parseVersionCode("v1.3.100"))   // patch 超两位
        assertNull(AppUpdatePolicy.parseVersionCode("v1234.0.0"))  // major 超三位
    }

    @Test
    fun `三位以内的 major 正常进位`() {
        assertEquals(9990000, codeOf("v999.0.0"))
        assertEquals(9999900, codeOf("v999.99"))
        assertEquals(9999999, codeOf("v999.99.99"))
    }

    // ---------- versionCode → 展示名 ----------

    @Test
    fun `版本号还原成展示名与 gradle 的 versionName 对齐`() {
        assertEquals("1.3.1", AppUpdatePolicy.formatVersion(10301))
        assertEquals("1.3.0", AppUpdatePolicy.formatVersion(10300))
        assertEquals("2.0.0", AppUpdatePolicy.formatVersion(20000))
        assertEquals("1.0.0", AppUpdatePolicy.formatVersion(10000))
        assertEquals("0.0.1", AppUpdatePolicy.formatVersion(1))
        assertEquals("0.0.0", AppUpdatePolicy.formatVersion(0))
    }

    @Test
    fun `解析与还原互为逆运算`() {
        for (tag in listOf("1.0.0", "v1.3.1", "v2.0", "v999.99.99")) {
            val code = codeOf(tag)
            assertEquals(code, codeOf(AppUpdatePolicy.formatVersion(code)))
        }
    }

    // ---------- 版本比较 ----------

    @Test
    fun `只有远端严格更新才算有更新`() {
        assertTrue(AppUpdatePolicy.isNewer(10301, 10300))
        assertTrue(AppUpdatePolicy.isNewer(20000, 10399))
        // 相等不算 —— 否则每次冷启动都会提示一遍「发现新版本 1.3.0」。
        assertFalse(AppUpdatePolicy.isNewer(10300, 10300))
        // 远端更旧时不能反过来提示，否则会把用户降级回去
        // （发生场景：用户手动装过更高的包，或者发布页挂错了文件）。
        assertFalse(AppUpdatePolicy.isNewer(10299, 10300))
        assertFalse(AppUpdatePolicy.isNewer(0, 10300))
    }

    // ---------- 从发布资产里挑 APK ----------

    @Test
    fun `Gitee 自动附带的源码包不会被当成安装包`() {
        // 🚨 这是接 Gitee 之后最容易踩的坑：它的 API 返回的 assets 里**永远**混着两个
        // 平台自动生成的源码归档（注意它们的 URL 形如 /archive/refs/tags/...，
        // 不是 /releases/download/...）。真实上传的 APK 和它们混在同一个数组里，
        // 所以「取第 0 个」这种偷懒写法必然选错 —— 那条链路的终点是一个 .zip，
        // 用户会看到「安装包无法解析」。
        val giteeStyle = listOf("v1.3.0.zip", "v1.3.0.tar.gz", "weijing-1.3.0.apk")
        assertEquals(2, AppUpdatePolicy.pickApkAsset(giteeStyle))
    }

    @Test
    fun `源码包排在 APK 后面时也不会漏掉 APK`() {
        // 依赖 assets 的返回顺序迟早会翻车，顺序反过来也必须能找到。
        val reversed = listOf("weijing-1.3.0.apk", "v1.3.0.zip", "v1.3.0.tar.gz")
        assertEquals(0, AppUpdatePolicy.pickApkAsset(reversed))
    }

    @Test
    fun `只有源码包时判定为没有可用更新`() {
        // 发布时忘了传 APK。这时候必须返回 null 让上层跳过，
        // 而不是退而求其次去下源码包。
        assertNull(AppUpdatePolicy.pickApkAsset(listOf("v1.3.0.zip", "v1.3.0.tar.gz")))
        assertNull(AppUpdatePolicy.pickApkAsset(emptyList()))
        assertNull(AppUpdatePolicy.pickApkAsset(listOf("readme.md", "checksums.txt")))
    }

    @Test
    fun `多个 APK 时优先选名字里带 weijing 的`() {
        val names = listOf("arm64-v8a.apk", "weijing-1.3.0.apk", "universal.apk")
        assertEquals(1, AppUpdatePolicy.pickApkAsset(names))

        // 没有带 weijing 的，就退回到第一个 —— 有总比没有强。
        assertEquals(0, AppUpdatePolicy.pickApkAsset(listOf("app-release.apk", "other.apk")))
    }

    @Test
    fun `apk 后缀不区分大小写`() {
        assertEquals(0, AppUpdatePolicy.pickApkAsset(listOf("Weijing-1.3.0.APK")))
        assertEquals(0, AppUpdatePolicy.pickApkAsset(listOf("weijing-1.3.0.Apk")))
    }

    @Test
    fun `名字里含 apk 但不是后缀的不算`() {
        // 「apk 备份.zip」「apk-notes.txt」这类名字不能让过滤失效。
        assertNull(AppUpdatePolicy.pickApkAsset(listOf("apk-backup.zip", "apk-notes.txt")))
    }
}
