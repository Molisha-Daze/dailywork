package io.github.molishadaze.weijing.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * 把下载好的 APK 交给系统安装器，并在动手之前把能提前查的都查一遍。
 *
 * 这是自更新里**唯一一个真正有系统风险的环节**，所以每一条防线都写清楚了为什么在：
 * 安装一段代码到用户设备上这件事，出错的代价和「更新提示没弹出来」完全不是一个量级。
 */
object ApkInstaller {

    private const val TAG = "ApkInstaller"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 与 AndroidManifest.xml 里 provider 的 authorities 必须一致。 */
    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * 用户是否已经在系统里放行「安装未知应用」。
     *
     * minSdk 29 ≥ O(26)，`canRequestPackageInstalls()` 必然存在，不需要 Build.VERSION 判断。
     */
    fun canRequestInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 跳系统设置页去开「允许安装未知应用」。
     *
     * 后面那段 `package:` 很关键：不带的话跳的是「所有应用」的列表页，
     * 用户得自己在一堆应用里把「未竟」翻出来 —— 多一步就是多一半的放弃率。
     */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /** 本机已安装版本的 versionCode；读不到时返回 0。 */
    fun localVersionCode(context: Context): Int = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    } catch (e: Exception) {
        0
    }

    /**
     * 校验下载下来的 APK。**返回 null 表示通过，否则返回给用户看的错误文案。**
     *
     * 为什么非要自己先查一遍，而不是直接丢给系统安装器：
     * - 下载被中间设备劫持 / 运营商插页 → 装的是个垃圾文件，系统只会弹一句语焉不详的
     *   「解析包错误」，用户完全不知道发生了什么；
     * - 发布页上挂错包（传了旧版本）→ 系统以「版本号更低」为由拒绝，同样难排查；
     * - **签名不一致** → 系统抛 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，而且是写到一半才失败。
     *
     * 这三件事都能在读一遍 APK 头信息时提前发现，于是就能给一句人话，
     * 而不是把用户丢给系统安装器的报错界面。注意这里**只是提前告知**，
     * 最终校验权始终在系统手上，我们不（也无法）绕过它。
     */
    fun verify(context: Context, apk: File, expectedVersionCode: Int): String? {
        if (!apk.exists() || apk.length() <= 0L) return "安装包不完整，请重新下载"

        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return "安装包无法解析，可能下载过程中已损坏"

        if (archive.packageName != context.packageName) {
            return "安装包与当前应用不匹配，出于安全考虑已阻止安装"
        }

        val localCode = localVersionCode(context)
        if (archive.longVersionCode < localCode) {
            return "下载到的版本比当前还旧，已阻止安装"
        }
        // 发布 tag 说是 1.3.1、包里却是别的号 —— 说明发布页传错了文件，别装。
        if (expectedVersionCode > 0 && archive.longVersionCode != expectedVersionCode.toLong()) {
            return "安装包版本与发布信息不一致，已阻止安装"
        }

        // 签名读不到时（个别 ROM 的 PackageManager 不返回归档签名）不阻断：
        // 真正的仲裁者是系统安装器，它会自己再校验一次；我们这一步的作用是
        // 「把能提前说清的问题提前说清」，不是取代系统。
        val local = localSignatures(context)
        val downloaded = archiveSignatures(context, apk.absolutePath)
        if (local.isNotEmpty() && downloaded.isNotEmpty() && local != downloaded) {
            return "更新包签名与当前版本不一致，无法覆盖安装。请告知发布者用同一份密钥重新打包"
        }

        return null
    }

    /**
     * 调起系统安装界面。返回 false 表示设备上没有能处理这个 Intent 的安装器。
     *
     * 🚨 两个标志缺一不可：
     * `FLAG_GRANT_READ_URI_PERMISSION` —— 少了它安装器读不到 `content://` 指向的文件，
     * 表现是那句经典的「解析包错误」，且和文件损坏长得一模一样；
     * `FLAG_ACTIVITY_NEW_TASK` —— 传进来的 context 可能是 Application。
     */
    fun install(context: Context, apk: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, authority(context), apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "调起系统安装器失败（${e.javaClass.simpleName}）")
        false
    }

    private fun localSignatures(context: Context): List<String> = try {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        context.packageManager.getPackageInfo(context.packageName, flags)
            .signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun archiveSignatures(context: Context, path: String): List<String> = try {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        context.packageManager.getPackageArchiveInfo(path, flags)
            ?.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
