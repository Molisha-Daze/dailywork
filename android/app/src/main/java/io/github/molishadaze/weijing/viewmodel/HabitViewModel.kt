package io.github.molishadaze.weijing.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.molishadaze.weijing.data.AppUpdateSource
import io.github.molishadaze.weijing.data.DailyQuote
import io.github.molishadaze.weijing.data.DailyQuotes
import io.github.molishadaze.weijing.data.QuotePolicy
import io.github.molishadaze.weijing.data.RemoteAppUpdate
import io.github.molishadaze.weijing.data.RemoteQuoteSource
import io.github.molishadaze.weijing.data.dao.CheckInWithHabit
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.data.repository.HabitRepository
import io.github.molishadaze.weijing.model.HabitWithStats
import io.github.molishadaze.weijing.model.UpcomingHabit
import io.github.molishadaze.weijing.util.ApkDownloader
import io.github.molishadaze.weijing.util.ApkInstaller
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.util.AppUpdatePolicy
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.util.ImageStorageManager
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitViewModel(private val repository: HabitRepository) : ViewModel() {

    /** 全部活跃习惯（含今天没排期的），供「习惯管理」页使用。 */
    val habitsWithStats: StateFlow<List<HabitWithStats>> = repository.habitsWithStats
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 今天真正有排期的习惯，供「今日打卡」页使用。 */
    val todayHabits: StateFlow<List<HabitWithStats>> = repository.habitsWithStats
        .map { list -> list.filter { it.scheduledToday } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 「即将到来」：今天没排期、但未来 60 天内会出现的习惯，每个习惯只取**下一个**日期。
     *
     * 用途是给今日页兜底 —— 今天恰好没有排期时，整页只剩一个空状态会让人以为
     * 「我的计划都没了」，而实际上下周还有一堆。
     *
     * 只在 [todayHabits] 为空时才被 UI 读；这里不做条件过滤，因为两个 Flow 的发射
     * 时机不同步，条件放在 UI 侧（同一个 Compose 快照里判断）才不会闪。
     */
    val upcomingHabits: StateFlow<List<UpcomingHabit>> = repository.habitsWithStats
        .map { list ->
            // 每次发射都重新取今天，别在 VM 构造时缓存：跨过零点后列表得自己更新。
            val today = DateUtils.todayDate()
            list.asSequence()
                .filter { !it.scheduledToday }
                .mapNotNull { item ->
                    HabitSchedule.nextScheduledDate(item.habit, today)
                        ?.let { date -> UpcomingHabit(habit = item.habit, date = date) }
                }
                // 先按日期近的排，同一天再按习惯本身的顺序（id 即创建顺序）
                .sortedWith(compareBy<UpcomingHabit> { it.date }.thenBy { it.habit.id })
                .toList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allHabits: StateFlow<List<Habit>> = repository.allHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 全部已归档习惯，供「习惯管理」折叠查看使用。 */
    val archivedHabits: StateFlow<List<Habit>> = repository.allArchivedHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allCheckIns: StateFlow<List<CheckIn>> = repository.allCheckIns
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val standaloneCounters: StateFlow<List<StandaloneCounter>> = repository.allStandaloneCounters
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * 计数器历史周期归档，按 counterId 分好组。
     *
     * 在 VM 里分组而不是让每个卡片各自去查库：计数器数量级很小（个位数），
     * 一次全量查询 + 内存分组的成本远低于 N 个 Composable 各自持有查询。
     */
    val counterPeriodLogs: StateFlow<Map<Long, List<CounterPeriodLog>>> =
        repository.allCounterPeriodLogs
            .map { logs -> logs.groupBy { it.counterId } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    val historyRecords: StateFlow<List<CheckInWithHabit>> = repository.allCheckInsWithHabits
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleCheckIn(habitId: Long, date: String = DateUtils.today()) {
        viewModelScope.launch {
            repository.toggleCheckIn(habitId, date)
        }
    }

    /** 计数器习惯 +1 */
    fun incrementCheckIn(habitId: Long, date: String = DateUtils.today()) {
        viewModelScope.launch {
            repository.incrementCheckIn(habitId, date)
        }
    }

    /** 计数器习惯 -1 */
    fun decrementCheckIn(habitId: Long, date: String = DateUtils.today()) {
        viewModelScope.launch {
            repository.decrementCheckIn(habitId, date)
        }
    }

    /**
     * Persists the photo from PhotoPicker temporary Uri into app private storage,
     * then saves the absolute path in Room.
     */
    fun attachPhotoFromUri(context: Context, habitId: Long, uri: Uri, date: String = DateUtils.today()) {
        viewModelScope.launch {
            val localPath = ImageStorageManager.saveImageToAppStorage(
                context = context,
                uri = uri,
                habitId = habitId,
                date = date
            )
            if (localPath != null) {
                repository.attachPhoto(habitId, date, localPath)
            }
        }
    }

    fun removePhoto(habitId: Long, date: String = DateUtils.today()) {
        viewModelScope.launch {
            repository.removePhoto(habitId, date)
        }
    }

    fun addHabit(habit: Habit) {
        viewModelScope.launch {
            val nextOrder = habitsWithStats.value.size
            repository.addHabit(habit.copy(sortOrder = nextOrder))
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            repository.updateHabit(habit)
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    fun archiveHabit(habit: Habit) {
        viewModelScope.launch {
            repository.archiveHabit(habit)
        }
    }

    fun unarchiveHabit(habit: Habit) {
        viewModelScope.launch {
            repository.unarchiveHabit(habit)
        }
    }

    fun archiveFinishedHabits(habits: List<Habit>) {
        viewModelScope.launch {
            repository.archiveHabits(habits)
        }
    }

    fun moveHabit(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = habitsWithStats.value.map { it.habit }.toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                repository.updateHabitOrder(current)
            }
        }
    }

    // ---------- 独立计数器 ----------

    fun addCounter(counter: StandaloneCounter) {
        viewModelScope.launch {
            repository.addCounter(counter)
        }
    }

    fun updateCounter(counter: StandaloneCounter) {
        viewModelScope.launch {
            repository.updateCounter(counter)
        }
    }

    fun deleteCounter(id: Long) {
        viewModelScope.launch {
            repository.deleteCounter(id)
        }
    }

    /** 增减计数。UI 传入的已经是带符号的步长（如 -step / +step）。 */
    fun stepCounter(id: Long, delta: Int) {
        viewModelScope.launch {
            repository.stepCounter(id, delta)
        }
    }

    fun resetCounter(id: Long) {
        viewModelScope.launch {
            repository.resetCounter(id)
        }
    }

    /**
     * 结算已经过期的计数器周期。
     *
     * 由「App 回到前台」和「进入计数器页」触发。仓库层按天短路，
     * 所以可以放心地在每次前台恢复时都调一次。
     */
    fun rolloverCounterPeriods() {
        viewModelScope.launch {
            repository.rolloverCounterPeriods()
        }
    }

    // ---------- 备份与恢复 ----------

    /** 生成备份 JSON 字符串。由调用方负责写入目标 Uri。 */
    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    fun importBackupJson(json: String) {
        viewModelScope.launch {
            repository.importBackupJson(json)
        }
    }

    // ---------- 教学示例数据 ----------

    /** 冷启动调用一次即可：内部有持久化标记，重复调用无副作用。 */
    fun ensureSampleSeeded(settings: AppSettings) {
        viewModelScope.launch {
            repository.seedSampleDataIfFirstRun(settings)
        }
    }

    /** 用户主动载入示例（追加，不清空现有数据）。 */
    fun loadSampleData() {
        viewModelScope.launch {
            repository.loadSampleData()
        }
    }

    // ---------- 细化小计划（子任务） ----------

    fun toggleSubTask(habitId: Long, subTaskId: String, date: String = DateUtils.today()) {
        viewModelScope.launch {
            repository.toggleSubTask(habitId, subTaskId, date)
        }
    }

    fun toggleSubTaskGroup(habitId: Long, date: String = DateUtils.today()) {
        viewModelScope.launch {
            repository.toggleSubTaskGroup(habitId, date)
        }
    }

    // ---------- 每日格言 ----------

    /**
     * 今天要显示的格言。
     *
     * 初值直接取内置库当天的句子 —— **首帧必须立刻有内容**：远程接口实测首包要 4 秒，
     * 等它回来再渲染，等于让用户对着一个空洞的占位符发呆。
     */
    private val _todayQuote = MutableStateFlow(DailyQuotes.forDate(DateUtils.todayDate()))
    val todayQuote: StateFlow<DailyQuote> = _todayQuote

    /**
     * 本次进程内已经尝试过远程请求的日期。
     *
     * 没有它的话，每次切回「今日打卡」tab 都会重发一次请求：当天有缓存时还好（命中缓存直接返回），
     * 但「当天没网」这种情形会变成来回切几次就白试几次。一张内存标记就能省掉，且进程重启后自动重置，
     * 用户第二天连上网仍有重新尝试的机会。
     */
    private var quoteFetchAttemptedOn: LocalDate? = null

    /**
     * 取今天的格言：**在线优先、缓存兜底、本地保底**。
     *
     * 可以重复调用，代价最多是每天一次请求：
     * 1. 当天已缓存远程句子 → 直接用它并返回，**不发请求**。这一步同时保证同一天里
     *    句子不会来回变 —— 接口是「随机漫步」，每调一次换一句，不做缓存就不叫「每日格言」了；
     * 2. 没有缓存 → 异步取一次，取到且通过 [QuotePolicy] 内容校验才落盘并刷新界面；
     * 3. 无网 / 超时 / 被过滤 / 接口挂了 → **什么都不做**，保持内置库当天的句子，用户无感知。
     *
     * 缓存在 [settings]（SharedPreferences）里，所以冷启动的第一帧就能命中，不用等网络。
     */
    fun refreshTodayQuote(settings: AppSettings) {
        val today = DateUtils.todayDate()

        settings.cachedRemoteQuote(today)?.let { cached ->
            _todayQuote.value = DailyQuote(cached.text, cached.source)
            return
        }

        if (quoteFetchAttemptedOn == today) return
        quoteFetchAttemptedOn = today

        viewModelScope.launch {
            val remote = RemoteQuoteSource.fetch() ?: return@launch
            val quote = QuotePolicy.toDailyQuote(remote) ?: return@launch
            settings.saveRemoteQuote(today, quote.text, quote.source)
            _todayQuote.value = quote
        }
    }

    // ---------- 应用内自更新 ----------

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** 更新流程的当前状态，供界面渲染弹窗。 */
    val updateState: StateFlow<UpdateState> = _updateState

    private var updateJob: Job? = null

    /**
     * 检查有没有新版本。
     *
     * [localVersionCode] 由界面层读好传进来，而不是在这里拿 Context 去取 ——
     * ViewModel 不碰 Context 是本工程的硬约定（只有 Haptics 那种必须在非 UI 层
     * 拿 Context 的场景才打破它，这里没那个必要）。
     *
     * [silent] = true 是启动时的静默检查，它有两条刻意的**不对称**：
     * 1. 24 小时内不重复请求（理由见 [AppSettings.lastUpdateCheckAt]）；
     * 2. 只有真的发现新版本才改状态，没新版时安静地回到 Idle，什么都不弹。
     * 手动检查正好相反：无论结果如何都要给反馈 ——「点了一下没反应」
     * 比「明确告诉你已经是最新」糟糕得多。
     */
    fun checkForUpdate(settings: AppSettings, localVersionCode: Int, silent: Boolean) {
        val current = _updateState.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) return

        if (silent) {
            val elapsed = System.currentTimeMillis() - settings.lastUpdateCheckAt()
            // elapsed 为负说明系统时间被往回调过，这种情况直接放行，不要把用户永久卡在「检查不了」里。
            if (elapsed in 0 until UPDATE_CHECK_INTERVAL_MS) return
        }

        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            val latest = AppUpdateSource.fetchLatest()
            settings.markUpdateChecked(System.currentTimeMillis())

            _updateState.value = when {
                latest == null ->
                    if (silent) UpdateState.Idle
                    else UpdateState.Failed("检查更新失败，请检查网络后重试")
                AppUpdatePolicy.isNewer(latest.versionCode, localVersionCode) ->
                    UpdateState.Available(latest)
                silent -> UpdateState.Idle
                else -> UpdateState.UpToDate
            }
        }
    }

    /**
     * 下载并校验新版本。成功后进 [UpdateState.Ready]，由界面层接手安装。
     *
     * 下载地址和请求头都取自 [RemoteAppUpdate] —— 它们**必须**是 `AppUpdateSource`
     * 算好的那一份，不能在这里自己拼：GitHub 的包只有走 API 资产端点 + `Accept`
     * 才拿得到，Gitee 则是普通直链，两者规则不同，混了就必然有一边下不来。
     *
     * 下载跑在 viewModelScope 上，所以**用户把弹窗关掉也不会中断**；
     * 真想停下来得走 [cancelUpdate]。
     */
    fun downloadUpdate(context: Context) {
        val update = (_updateState.value as? UpdateState.Available)?.update ?: return
        updateJob?.cancel()

        val appContext = context.applicationContext
        _updateState.value = UpdateState.Downloading(0)
        updateJob = viewModelScope.launch {
            try {
                var lastPercent = -1
                val apk = ApkDownloader.download(
                    context = appContext,
                    url = update.apkUrl,
                    fileName = "weijing-${update.versionCode}.apk",
                    expectedSize = update.sizeBytes,
                    headers = update.apkHeaders,
                    onProgress = { done, total ->
                        val percent = if (total > 0L) ((done * 100) / total).toInt() else 0
                        // 64KB 一次回调，20MB 就是三百多次。不节流的话，除了那 100 次
                        // 「百分比真的变了」的，其余全是在白白触发重组。
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _updateState.value = UpdateState.Downloading(percent)
                        }
                    }
                )

                val problem = ApkInstaller.verify(appContext, apk, update.versionCode)
                if (problem == null) {
                    _updateState.value = UpdateState.Ready(apk, update)
                } else {
                    // 校验不过的包留着没有任何意义，只会占地方并干扰下一次下载。
                    apk.delete()
                    _updateState.value = UpdateState.Failed(problem)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _updateState.value = UpdateState.Failed("下载失败：${e.message ?: "网络异常"}")
            }
        }
    }

    /** 主动中断下载（弹窗上的「取消下载」）。 */
    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = UpdateState.Idle
    }

    /**
     * 界面层调起系统安装器失败时回报。
     *
     * 传 [message] 是为了区分两种失败：设备上没有安装器（用默认文案），
     * 与文件已被系统清掉（需要用户重新下载 —— 说清楚他才知道下一步干什么）。
     */
    fun reportInstallFailure(message: String? = null) {
        _updateState.value = UpdateState.Failed(
            message ?: "无法调起系统安装界面，请稍后重试"
        )
    }

    /** 关掉更新弹窗回到 Idle。下载过程中不允许调用，由界面层拦住。 */
    fun dismissUpdate() {
        if (_updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Idle
    }
}

/**
 * 自更新的界面状态。
 *
 * 用密封接口而不是「几个布尔量 + 一个进度值」：自更新的各阶段天然互斥，
 * 用布尔量拼装立刻就会出现「既在下载、又没有可用版本」这种非法组合。
 * 非法状态只要存在，早晚会有人在某个分支里读到它，然后花半天去查它为什么不可能发生。
 */
sealed interface UpdateState {
    /** 什么都没发生。 */
    data object Idle : UpdateState

    /** 正在问远端要版本信息。 */
    data object Checking : UpdateState

    /** 已是最新（只有手动检查才会进这个状态）。 */
    data object UpToDate : UpdateState

    /** 发现新版本，等用户决定要不要下。 */
    data class Available(val update: RemoteAppUpdate) : UpdateState

    /** 下载中，[percent] 取 0~100。 */
    data class Downloading(val percent: Int) : UpdateState

    /** 已下载且校验通过，可以交给系统安装器了。 */
    data class Ready(val apk: File, val update: RemoteAppUpdate) : UpdateState

    /** 出错了，[message] 是可以直接讲给用户听的说明。 */
    data class Failed(val message: String) : UpdateState
}

/**
 * 启动时静默检查的最小间隔。
 *
 * 选择按「时间间隔」限频，而不是「每个版本只提示一次」，是因为后者有个尴尬的副作用：
 * 用户这次拒绝了更新，就再也不会被提醒 —— 直到他自己想起来去点「检查更新」，
 * 而那恰恰是我们想避免的情况。按时间限频则温和得多：拒绝之后隔一天再问一次。
 */
private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

class HabitViewModelFactory(private val repository: HabitRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
