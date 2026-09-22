package io.github.molishadaze.weijing.util

/**
 * 应用自更新的**纯规则层**：发布 tag 的版本号解析、版本比较、以及从发布资产里挑出 APK。
 *
 * 为什么单独抽出来：这几条规则最容易写错，又最难在真机上复现 —— 想验证「远端 tag 是 v2.0、
 * 本地是 1.3.0」这种组合，得先真的去 GitHub 发一个 2.0 的 release。做成不依赖 `android.*`
 * 的纯函数之后，JVM 单测就能把所有边界一次钉死（见 AppUpdatePolicyTest）。
 *
 * 版本号编码规则与 `app/build.gradle.kts` 里 versionCode 的注释**必须一致**：
 * `major * 10000 + minor * 100 + patch`。两边一旦不同步，就会出现
 * 「明明发了新版却提示已是最新」这种最难查的故障 —— 因为两边各自看都完全正常。
 */
object AppUpdatePolicy {

    /**
     * 发布 tag 的形状：`1.3.1` / `v1.3.1` / `v2.0`（缺省段按 0 补）。
     *
     * 刻意不接受 `v1.3.1-beta` 这类预发布标记。本 App 的分发方式（群 + 固定地址）没有灰度概念，
     * 让一个预发 tag 混进来只会把半成品推给所有人。要发就发正式版。
     */
    private val TAG_PATTERN = Regex("""^v?(\d{1,3})\.(\d{1,2})(?:\.(\d{1,2}))?$""")

    /** 把发布 tag 解析成 versionCode；不合法返回 null。 */
    fun parseVersionCode(tag: String): Int? {
        val match = TAG_PATTERN.matchEntire(tag.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        // 第三段没写时 groupValues 给的是空串，不是 null。
        val patch = match.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return null

        // minor / patch 各占两位。超了会在乘法里进位串号（1.100.0 会算成 2.0.0），
        // 与其产生一个看着正常、其实错位的号，不如直接判为无效 tag，让上层跳过这次发布。
        if (minor > 99 || patch > 99) return null

        return major * 10_000 + minor * 100 + patch
    }

    /** 把 versionCode 还原成展示用版本名，输出形式与 build.gradle.kts 的 versionName 对齐。 */
    fun formatVersion(code: Int): String {
        val major = code / 10_000
        val minor = code / 100 % 100
        val patch = code % 100
        return "$major.$minor.$patch"
    }

    /**
     * 远端版本是否比本地新。
     *
     * 用 `>` 而不是 `!=`：万一远端比本地旧（用户手动装过更高的包），
     * 不该反过来提示「有更新」然后把人降级回去。
     */
    fun isNewer(remoteCode: Int, localCode: Int): Boolean = remoteCode > localCode

    /**
     * 从发布资产的名称列表里挑出 APK，返回下标；列表里没有 APK 则返回 null。
     *
     * 优先认名字里带 `weijing` 的那个：发布页上同时挂着源码归档（Source code zip/tar.gz）
     * 是常态，只按 `.apk` 后缀筛本已够用；但万一以后要同时挂多个架构 / 多个渠道的包，
     * 这一步能把「选错包」的概率再降一档。
     */
    fun pickApkAsset(names: List<String>): Int? {
        val apkIndices = names.indices.filter { names[it].endsWith(".apk", ignoreCase = true) }
        if (apkIndices.isEmpty()) return null
        return apkIndices.firstOrNull { names[it].contains("weijing", ignoreCase = true) }
            ?: apkIndices.first()
    }
}
