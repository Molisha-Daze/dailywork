package io.github.molishadaze.weijing.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.molishadaze.weijing.data.dao.CheckInWithHabit
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.data.repository.HabitRepository
import io.github.molishadaze.weijing.model.DayProgress
import io.github.molishadaze.weijing.model.HabitWithStats
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.ImageStorageManager
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

    val allHabits: StateFlow<List<Habit>> = repository.allHabits
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

    val heatMapProgress: StateFlow<List<DayProgress>> = repository.getHeatMapProgress(35)
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
}

class HabitViewModelFactory(private val repository: HabitRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
