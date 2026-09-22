package io.github.molishadaze.weijing.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.molishadaze.weijing.util.ApkInstaller
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.viewmodel.HabitViewModel
import io.github.molishadaze.weijing.viewmodel.UpdateState
import java.io.File

/**
 * 自更新的界面宿主：把 [HabitViewModel.updateState] 翻译成弹窗、权限引导和安装动作。
 *
 * **为什么这一层必须待在 Compose 里，而不是 ViewModel 里**：
 * 「调起系统安装器」和「跳设置页开未知来源权限」都需要 Activity 级的
 * `startActivity` + 结果回调（ActivityResultLauncher），ViewModel 拿不到，也不该拿。
 * ViewModel 只负责「下载好、校验过、文件在哪」，最后一步永远由界面层走。
 *
 * 启动时的静默检查也挂在这里（[LaunchedEffect] 无 key，一个进程只跑一次），
 * 正好对上「每次冷启动最多检查一次」的语义。
 */
@Composable
fun AppUpdateHost(viewModel: HabitViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val state by viewModel.updateState.collectAsState()

    // 待安装的 APK：用户还没开「未知来源」权限时先存着，从设置页回来接着装。
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    var showPermissionHint by remember { mutableStateOf(false) }

    /**
     * 尝试调起安装。没权限就先把文件记下、弹说明，等用户去设置里放行。
     *
     * 注意这里**不做静默安装**，也做不到：Android 要求每一次安装都由用户在系统界面上
     * 明确点一次「安装」。我们能优化的是「不用去别的地方找安装包」这一段。
     */
    val tryInstall: (File) -> Unit = { apk ->
        if (ApkInstaller.canRequestInstall(context)) {
            if (!ApkInstaller.install(context, apk)) viewModel.reportInstallFailure()
        } else {
            pendingInstall = apk
            showPermissionHint = true
        }
    }

    // 从「允许安装未知应用」设置页回来：授权了就接着装，没授权就明确说清卡在哪，
    // 而不是让用户对着一个没反应的应用发呆。
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = pendingInstall ?: return@rememberLauncherForActivityResult
        pendingInstall = null
        if (ApkInstaller.canRequestInstall(context)) {
            tryInstall(apk)
        } else {
            viewModel.reportInstallFailure("还没有允许「未竟」安装应用，更新无法完成")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate(
            settings = settings,
            localVersionCode = ApkInstaller.localVersionCode(context),
            silent = true
        )
    }

    // 下载并校验完成后自动尝试安装；文件被系统清掉了就说清楚要重新下载。
    LaunchedEffect(state) {
        val ready = state as? UpdateState.Ready ?: return@LaunchedEffect
        if (!ready.apk.exists()) {
            viewModel.reportInstallFailure("更新包已被系统清理，请重新下载")
        } else {
            tryInstall(ready.apk)
        }
    }

    val current = state
    if (current !is UpdateState.Idle) {
        UpdateDialog(
            state = current,
            onDownload = { viewModel.downloadUpdate(context) },
            onInstall = { (current as? UpdateState.Ready)?.let { tryInstall(it.apk) } },
            onCancelDownload = { viewModel.cancelUpdate() },
            onRetry = {
                viewModel.checkForUpdate(
                    settings = settings,
                    localVersionCode = ApkInstaller.localVersionCode(context),
                    silent = false
                )
            },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }

    if (showPermissionHint) {
        AlertDialog(
            onDismissRequest = { showPermissionHint = false },
            title = { Text("需要先开启一项权限") },
            text = {
                Text(
                    "Android 不允许应用自行安装更新包，需要你在系统设置里允许「未竟」安装其他应用。\n\n" +
                        "这是系统级限制，任何应用都绕不过去。开启后会自动回到安装步骤。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionHint = false
                        unknownSourcesLauncher.launch(ApkInstaller.unknownSourcesIntent(context))
                    }
                ) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionHint = false }) { Text("稍后") }
            }
        )
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val downloading = state is UpdateState.Downloading

    AlertDialog(
        // 下载中不允许点外面关掉：关掉了进度就看不见，用户只会以为它坏了。
        // 想中断请走「取消下载」，那里会真的把协程停掉。
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text(titleOf(state)) },
        text = { UpdateBody(state) },
        confirmButton = {
            when (state) {
                UpdateState.Checking ->
                    TextButton(onClick = {}, enabled = false) { Text("检查中…") }
                is UpdateState.Available ->
                    TextButton(onClick = onDownload) { Text("立即更新") }
                is UpdateState.Downloading ->
                    TextButton(onClick = {}, enabled = false) { Text("下载中 ${state.percent}%") }
                is UpdateState.Ready ->
                    TextButton(onClick = onInstall) { Text("立即安装") }
                is UpdateState.Failed ->
                    TextButton(onClick = onRetry) { Text("重试") }
                UpdateState.UpToDate, UpdateState.Idle ->
                    TextButton(onClick = onDismiss) { Text("好") }
            }
        },
        dismissButton = when {
            downloading -> ({ TextButton(onClick = onCancelDownload) { Text("取消下载") } })
            state is UpdateState.Checking -> null
            state is UpdateState.UpToDate -> null
            else -> ({ TextButton(onClick = onDismiss) { Text("稍后") } })
        }
    )
}

@Composable
private fun UpdateBody(state: UpdateState) {
    when (state) {
        is UpdateState.Available -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "v${state.update.versionName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (state.update.sizeBytes > 0L) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "安装包 ${formatSize(state.update.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.update.changelog.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.update.changelog.abbreviated(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is UpdateState.Downloading -> Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${state.percent}% · 下载期间可以返回其他页面，不会中断",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is UpdateState.Ready -> Text(
            "更新包已下载并校验通过。\n\n" +
                "如果没有自动弹出安装界面，点下面的「立即安装」。\n" +
                "系统随后还会让你确认一次 —— 这是 Android 的强制要求，绕不过去。"
        )

        is UpdateState.Failed -> Text(state.message)

        UpdateState.Checking -> Text("正在获取最新版本信息…")

        UpdateState.UpToDate -> Text("当前已经是最新版本，无需更新。")

        UpdateState.Idle -> Unit
    }
}

private fun titleOf(state: UpdateState): String = when (state) {
    UpdateState.Idle -> "应用更新"
    UpdateState.Checking -> "检查更新"
    UpdateState.UpToDate -> "已是最新版本"
    is UpdateState.Available -> "发现新版本"
    is UpdateState.Downloading -> "正在下载更新"
    is UpdateState.Ready -> "准备安装"
    is UpdateState.Failed -> "更新未完成"
}

/** 更新日志可能写得很长，弹窗里只留前几行 —— 想看全的可以去发布页。 */
private fun String.abbreviated(limit: Int = 400): String =
    if (length <= limit) this else take(limit).trimEnd() + "…"

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
