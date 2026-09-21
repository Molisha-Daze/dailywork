package io.github.molishadaze.weijing.data.repository

import android.content.Context
import io.github.molishadaze.weijing.data.HabitDatabase
import io.github.molishadaze.weijing.data.SampleData
import io.github.molishadaze.weijing.data.dao.CheckInWithHabit
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.model.HabitWithStats
import io.github.molishadaze.weijing.notification.NotificationHelper
import io.github.molishadaze.weijing.util.AppSettings
import io.github.molishadaze.weijing.util.BackupCodec
import io.github.molishadaze.weijing.util.CounterPeriod
import io.github.molishadaze.weijing.util.CounterPeriodCalculator
import io.github.molishadaze.weijing.util.DateUtils
import io.github.molishadaze.weijing.util.HabitSchedule
import io.github.molishadaze.weijing.util.ImageStorageManager
import io.github.molishadaze.weijing.util.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class HabitRepository(private val context: Context) {
    private val db = HabitDatabase.getInstance(context)
    private val habitDao = db.habitDao()
    private val checkInDao = db.checkInDao()
    private val counterDao = db.standaloneCounterDao()
    private val periodLogDao = db.counterPeriodLogDao()

    /**
     * 周期结算的互斥锁。
     *
     * 结算会在多处被触发（前台恢复、点 +1 之前、点清零之前），
     * 而它是「读旧值 → 写归档 → 清零」三步，没有锁的话两次并发结算
     * 会把同一个周期的值归档两遍（历史里凭空多出一份计数）。
     * 注意 [Mutex] 不可重入：持锁的函数里不能再调用另一个会拿同一把锁的函数。
     */
    private val counterRolloverMutex = Mutex()

    /**
     * 上一次成功结算的日期，用来把「每次点 +1 都全表查一遍」压成「每天一次」。
     *
     * 只在结算**成功之后**才写入：中途抛异常时保持旧值，下次调用会重试而不是静默跳过。
     * 跨天时字符串自然不相等，检查自动恢复。
     */
    private var lastRolloverDate: String? = null

    /** 全部独立计数器，按 id 升序（创建顺序）。 */
    val allStandaloneCounters: Flow<List<StandaloneCounter>> = counterDao.getAllCounters()

    /** 全部周期归档记录，最近的周期在前。 */
    val allCounterPeriodLogs: Flow<List<CounterPeriodLog>> = periodLogDao.getAllLogs()

    val allHabits: Flow<List<Habit>> = habitDao.getAllActiveHabits()

    val allCheckIns: Flow<List<CheckIn>> = checkInDao.getAllCheckIns()

    val allCheckInsWithHabits: Flow<List<CheckInWithHabit>> = checkInDao.getAllCheckInsWithHabit()

    /**
     * 全部活跃习惯 + 连续打卡统计。
     *
     * 这里返回的是**所有**活跃习惯（含今天没有排期的），因为「习惯管理」页要列出全部；
     * 今日页请用 [io.github.molishadaze.weijing.viewmodel.HabitViewModel.todayHabits]，
     * 它按 [HabitSchedule.isScheduled] 过滤出今天真正该做的。
     */
    val habitsWithStats: Flow<List<HabitWithStats>> = combine(
        habitDao.getAllActiveHabits(),
        checkInDao.getAllCheckIns()
    ) { habits, allCheckIns ->
        val todayStr = DateUtils.today()
        val todayDate = DateUtils.todayDate()

        val checkInsByHabit = allCheckIns.groupBy { it.habitId }

        habits.map { habit ->
            val habitCheckIns = checkInsByHabit[habit.id] ?: emptyList()
            val checkInsByDate = habitCheckIns.associateBy { it.date }
            val todayCheckIn = checkInsByDate[todayStr]

            // 连续打卡按「有排期的日期」计算，周计划 / 月计划 / 间隔计划才不会被没排期的日子打断
            val streak = StreakCalculator.calculate(habit, checkInsByDate, todayDate)

            // 有大计划时，完成与否**完全由子任务勾选情况决定**，不再看 count/targetCount。
            // 这样即使旧的打卡记录里 isCompleted=true 但没勾任何子项，也会正确显示为未完成。
            val subTasks = habit.subTaskList
            val completedSubTaskIds = todayCheckIn?.completedSubTaskIdList.orEmpty()

            HabitWithStats(
                habit = habit,
                scheduledToday = HabitSchedule.isScheduled(habit, todayDate),
                isCompletedToday = if (subTasks.isNotEmpty()) {
                    completedSubTaskIds.size >= subTasks.size
                } else {
                    HabitSchedule.isCompleted(habit, todayCheckIn)
                },
                todayCheckIn = todayCheckIn,
                currentStreak = streak.currentStreak,
                longestStreak = streak.longestStreak,
                totalCheckIns = habitCheckIns.size,
                totalSubTaskCount = subTasks.size,
                completedSubTaskCount = completedSubTaskIds.size,
                hasSubTasks = subTasks.isNotEmpty()
            )
        }
    }

    suspend fun addHabit(habit: Habit): Long {
        val id = habitDao.insert(habit)
        val created = habit.copy(id = id)
        if (created.reminderTime != null) {
            NotificationHelper.scheduleDailyReminder(context, created)
        }
        return id
    }

    suspend fun updateHabit(habit: Habit) {
        habitDao.update(habit)
        if (habit.reminderTime != null) {
            NotificationHelper.scheduleDailyReminder(context, habit)
        } else {
            NotificationHelper.cancelReminder(context, habit.id)
        }
    }

    suspend fun deleteHabit(habit: Habit) {
        NotificationHelper.cancelReminder(context, habit.id)
        NotificationHelper.cancelNotification(context, habit.id)
        // clean up associated photos
        val checkIns = checkInDao.getCheckInsForHabitSync(habit.id)
        for (c in checkIns) {
            ImageStorageManager.deleteImageFile(c.photoPath)
        }
        habitDao.delete(habit)
    }

    suspend fun updateHabitOrder(habits: List<Habit>) {
        habits.forEachIndexed { index, habit ->
            habitDao.updateSortOrder(habit.id, index)
        }
    }

    /**
     * 打卡开关。普通习惯是「有记录则删除 / 无记录则新建」；
     * 计数器习惯转发到 [incrementCheckIn]，点满目标后再点一次归零。
     *
     * @return 操作后是否处于「已完成」状态；未来日期或习惯不存在时返回 null。
     */
    suspend fun toggleCheckIn(habitId: Long, date: String = DateUtils.today()): Boolean? {
        val habit = habitDao.getHabitById(habitId) ?: return null
        if (!canWrite(habit, date)) return null
        if (habit.isCounter) {
            val existing = checkInDao.getCheckIn(habitId, date)
            return if (existing != null && existing.count >= HabitSchedule.effectiveTarget(habit)) {
                ImageStorageManager.deleteImageFile(existing.photoPath)
                checkInDao.deleteByHabitAndDate(habitId, date)
                false
            } else {
                val next = incrementCheckIn(habitId, date) ?: return null
                next >= HabitSchedule.effectiveTarget(habit)
            }
        }

        val existing = checkInDao.getCheckIn(habitId, date)
        val nowCompleted = if (existing != null) {
            ImageStorageManager.deleteImageFile(existing.photoPath)
            checkInDao.deleteByHabitAndDate(habitId, date)
            false
        } else {
            checkInDao.insert(
                CheckIn(
                    habitId = habitId,
                    date = date,
                    count = 1,
                    isCompleted = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        }
        if (nowCompleted) NotificationHelper.cancelNotification(context, habitId)
        return nowCompleted
    }

    /**
     * 计数器习惯 +1，达到目标次数后置为完成。
     * @return 操作后的当前次数；未来日期或习惯不存在时返回 null。
     */
    suspend fun incrementCheckIn(habitId: Long, date: String = DateUtils.today()): Int? {
        val habit = habitDao.getHabitById(habitId) ?: return null
        if (!canWrite(habit, date)) return null

        val target = HabitSchedule.effectiveTarget(habit)
        val existing = checkInDao.getCheckIn(habitId, date)
        val next = ((existing?.count ?: 0) + 1).coerceAtMost(if (habit.isCounter) target else 1)

        if (existing == null) {
            checkInDao.insert(
                CheckIn(
                    habitId = habitId,
                    date = date,
                    count = next,
                    isCompleted = next >= target,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            checkInDao.update(existing.copy(count = next, isCompleted = next >= target))
        }
        if (next >= target) NotificationHelper.cancelNotification(context, habitId)
        return next
    }

    /**
     * 计数器习惯 -1，减到 0 时删除整条打卡记录（连带清理照片）。
     * @return 操作后的当前次数；未来日期或习惯不存在时返回 null。
     */
    suspend fun decrementCheckIn(habitId: Long, date: String = DateUtils.today()): Int? {
        val habit = habitDao.getHabitById(habitId) ?: return null
        if (!canWrite(habit, date)) return null

        val existing = checkInDao.getCheckIn(habitId, date) ?: return 0
        val next = (existing.count - 1).coerceAtLeast(0)

        if (next <= 0) {
            ImageStorageManager.deleteImageFile(existing.photoPath)
            checkInDao.deleteByHabitAndDate(habitId, date)
        } else {
            checkInDao.update(
                existing.copy(count = next, isCompleted = next >= HabitSchedule.effectiveTarget(habit))
            )
        }
        return next
    }

    suspend fun attachPhoto(habitId: Long, date: String, photoPath: String) {
        val habit = habitDao.getHabitById(habitId) ?: return
        if (!canWrite(habit, date)) return
        val existing = checkInDao.getCheckIn(habitId, date)
        if (existing != null) {
            // Remove previous photo if different
            if (existing.photoPath != null && existing.photoPath != photoPath) {
                ImageStorageManager.deleteImageFile(existing.photoPath)
            }
            checkInDao.updatePhotoPath(habitId, date, photoPath)
        } else {
            // If not yet checked in, create check in with photo
            checkInDao.insert(
                CheckIn(
                    habitId = habitId,
                    date = date,
                    photoPath = photoPath,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removePhoto(habitId: Long, date: String) {
        val existing = checkInDao.getCheckIn(habitId, date)
        if (existing?.photoPath != null) {
            ImageStorageManager.deleteImageFile(existing.photoPath)
            checkInDao.updatePhotoPath(habitId, date, null)
        }
    }

    /**
     * 写入打卡记录的前置校验：
     * 1. 不得是未来日期
     * 2. 该日期必须在习惯的排期内——否则改了排期后，残留的通知 Action 或历史界面
     *    仍能在不该执行的日期写入完成记录
     */
    private fun canWrite(habit: Habit, date: String): Boolean =
        date <= DateUtils.today() && HabitSchedule.isScheduled(habit, date)

    // ---------- 独立计数器 ----------
    // 计数器是脱离排期的孤立实体，不参与 doze-redemption/热力图/连续天数，
    // 因此这里没有 Habit 那一套 canWrite 校验。

    suspend fun addCounter(counter: StandaloneCounter): Long = counterDao.insert(
        counter.copy(
            // 归一化：名称去空白、步长至少 1、单位兜底。
            // 与网页版 AddEditCounterModal.handleSubmit 的规则保持一致。
            name = counter.name.trim(),
            unit = counter.unit.trim().ifBlank { "次" },
            step = counter.step.coerceAtLeast(1),
            currentCount = counter.currentCount.coerceAtLeast(0),
            limitCount = if (counter.hasLimit) counter.limitCount?.coerceAtLeast(1) else null,
            resetIntervalDays = counter.resetIntervalDays.coerceAtLeast(1),
            periodStartDate = newPeriodAnchor(counter.resetPeriod, counter.resetIntervalDays)
        )
    )

    suspend fun updateCounter(counter: StandaloneCounter) {
        val previous = counterDao.getCounterById(counter.id)
        val normalized = counter.copy(
            name = counter.name.trim(),
            unit = counter.unit.trim().ifBlank { "次" },
            step = counter.step.coerceAtLeast(1),
            currentCount = counter.currentCount.coerceAtLeast(0),
            limitCount = if (counter.hasLimit) counter.limitCount?.coerceAtLeast(1) else null,
            resetIntervalDays = counter.resetIntervalDays.coerceAtLeast(1),
            updatedAt = System.currentTimeMillis()
        )
        counterDao.update(normalized.withCorrectedPeriodAnchor(previous))
    }

    suspend fun deleteCounter(id: Long) {
        counterDao.deleteById(id)
    }

    /**
     * 按 [delta] 增减计数。
     *
     * 走 DAO 里的 SQL 原子累加而非"读出 + 改 + 写回"，避免快速连点时的丢更新。
     * 下限由 SQL 的 MAX(0, ...) 保证；**上限不在此处拦截** —— 与网页版一致，
     * 允许超过上限并由 UI 显示"已达上限"，这样记录「多喝了一罐」这类情况不会被吞掉。
     *
     * 前面那次 [rolloverCounterPeriods] 是必须的：App 在后台驻留一整夜之后，
     * 用户回到界面点第一下 +1，如果不先结算，这一下会被加进**昨天那个已经结束的周期**，
     * 然后被下一次结算当成昨天的数据归档走。
     */
    suspend fun stepCounter(id: Long, delta: Int) {
        rolloverCounterPeriods()
        counterDao.applyStep(id, delta)
    }

    /**
     * 清零。
     *
     * 配了周期的计数器，清零等价于「提前结算当前周期」：先把已有的值归档，再归零。
     * 这样「今天喝够 3 杯，手动清零准备重新数」不会让那 3 杯凭空消失；
     * 若当天之后又喝了 2 杯，次日结算时会并进同一条记录（3 + 2 = 5）。
     *
     * 未配周期时保持原有语义，纯粹归零、不留痕迹。
     */
    suspend fun resetCounter(id: Long) {
        rolloverCounterPeriods()
        counterRolloverMutex.withLock {
            val counter = counterDao.getCounterById(id) ?: return@withLock
            if (CounterPeriodCalculator.isPeriodic(counter.resetPeriod)) {
                val today = DateUtils.todayDate()
                val anchor = parseDateOrNull(counter.periodStartDate) ?: today
                val period = CounterPeriodCalculator.periodOf(
                    counter.resetPeriod, counter.resetIntervalDays, anchor, today
                )
                if (period != null) {
                    archiveCounterPeriod(counter, period, counter.currentCount)
                }
            }
            counterDao.setCount(id, 0)
        }
    }

    // ---------- 周期结算 ----------

    /**
     * 惰性周期结算：把所有到期该归零的计数器归档并清零。
     *
     * 触发点：App 回到前台、点 +1 之前、点清零之前。重复调用无副作用（同一天内直接短路）。
     *
     * **为什么不做定时任务**：本 App 无 INTERNET 权限、把省电放在第一优先，
     * 而 WorkManager / AlarmManager 在国产 ROM 的后台管控下被延迟甚至直接不执行是常态，
     * 一旦漏跑，用户看到的就是「过了零点却没归零」。惰性结算把正确性绑在
     * 「用户正看着屏幕的那一刻」上 —— 触发即必然正确，还不用申请任何后台权限。
     * 代价只有一个：归档记录的时间戳取决于"用户什么时候打开 App"，而不是零点整。
     * 对「昨天喝了几杯」这个问题没有影响。
     */
    suspend fun rolloverCounterPeriods() {
        val todayText = DateUtils.today()
        if (lastRolloverDate == todayText) return
        counterRolloverMutex.withLock {
            // 等锁期间别的协程可能已经结算过了，双重检查一次再干活。
            if (lastRolloverDate == todayText) return@withLock
            val today = DateUtils.todayDate()
            counterDao.getPeriodicCountersSync().forEach { counter ->
                val anchor = parseDateOrNull(counter.periodStartDate) ?: today
                val current = CounterPeriodCalculator.periodOf(
                    counter.resetPeriod, counter.resetIntervalDays, anchor, today
                ) ?: return@forEach

                if (!CounterPeriodCalculator.needsRollover(
                        counter.resetPeriod, counter.resetIntervalDays, anchor, today
                    )
                ) {
                    // 周期还没结束。只有一种情况需要动：老数据升级上来时锚点还是 NULL，
                    // 补一个当前周期的首日即可，计数保持原样。
                    counterDao.seedPeriodStartIfNull(counter.id, DateUtils.formatDate(current.start))
                    return@forEach
                }

                // 归档的是"锚点所在的那个周期"（可能就是上一周期），
                // 而不是 current —— current 已经是新周期了。
                if (counter.currentCount > 0) {
                    val previous = CounterPeriodCalculator.periodOfAnchor(
                        counter.resetPeriod, counter.resetIntervalDays, anchor
                    ) ?: current
                    archiveCounterPeriod(counter, previous, counter.currentCount)
                }
                // 清零 + 推进锚点在同一条 SQL 里完成，避免中途被杀进程时留下半截状态。
                counterDao.applyRollover(counter.id, DateUtils.formatDate(current.start))
            }
            lastRolloverDate = todayText
        }
    }

    /**
     * 把一个周期的最终值写进归档。同一周期被重复归档时**累加**而不是新增。
     *
     * 累加对应的是真实场景：用户上午喝了 3 杯就手动清零、下午又喝 2 杯，
     * 次日结算时若各插一条，历史里会出现「9月20日 3 杯」和「9月20日 2 杯」两行，
     * 看着像 bug。合并成一行 5 杯才符合直觉。
     *
     * 空周期不留记录（[count] <= 0 直接返回）：跨了很多天才打开 App 时，
     * 历史列表里堆一串「0」毫无信息量。
     */
    private suspend fun archiveCounterPeriod(
        counter: StandaloneCounter,
        period: CounterPeriod,
        count: Int
    ) {
        if (count <= 0) return
        val start = DateUtils.formatDate(period.start)
        val end = DateUtils.formatDate(period.end)
        val existing = periodLogDao.findByCounterAndStart(counter.id, start)
        if (existing == null) {
            periodLogDao.insert(
                CounterPeriodLog(
                    counterId = counter.id,
                    periodStart = start,
                    periodEnd = end,
                    count = count,
                    unit = counter.unit
                )
            )
        } else {
            periodLogDao.update(
                existing.copy(
                    count = existing.count + count,
                    periodEnd = end,
                    // 单位取归档当时的值：用户之后把「杯」改成「毫升」，
                    // 老记录不该跟着变，否则就不是"当时记下的东西"了。
                    unit = counter.unit
                )
            )
        }
    }

    /**
     * 新建计数器时的周期锚点，取**当前周期的首日**而非今天。
     *
     * 周六（09-19）新建一个「每周归零」的计数器，锚点该是 09-14（那周的周一）。
     * 若直接用今天，本周日一过就会立刻结算一次 —— 用户会看到一个凭空冒出来的空周期。
     */
    private fun newPeriodAnchor(resetPeriod: String, intervalDays: Int): String? {
        if (!CounterPeriodCalculator.isPeriodic(resetPeriod)) return null
        val today = DateUtils.todayDate()
        val start = CounterPeriodCalculator.currentPeriodStart(resetPeriod, intervalDays, today) ?: today
        return DateUtils.formatDate(start)
    }

    /**
     * 编辑保存时校正锚点。
     *
     * - 关掉自动归零 → 锚点清空（不归零的计数器没有「周期」这回事）；
     * - 周期配置变了（类型或天数）→ 锚点按**今天**重算，否则把「每 3 天」改成
     *   「每 30 天」时，四天前的旧锚点会让新周期当场就到期、立刻结算一次；
     * - 周期没变 → 保留原锚点。注意这一步是**幂等**的：以原锚点作为基准再算一次
     *   「当前周期首日」，得到的还是原锚点，因此不会在保存时把待结算的周期悄悄跳过去。
     */
    private fun StandaloneCounter.withCorrectedPeriodAnchor(
        previous: StandaloneCounter?
    ): StandaloneCounter {
        if (!CounterPeriodCalculator.isPeriodic(resetPeriod)) {
            return copy(periodStartDate = null)
        }
        val today = DateUtils.todayDate()
        val changed = previous == null ||
            previous.resetPeriod != resetPeriod ||
            previous.resetIntervalDays != resetIntervalDays
        val anchor = if (changed) today else (parseDateOrNull(periodStartDate) ?: today)
        val start = CounterPeriodCalculator.currentPeriodStart(resetPeriod, resetIntervalDays, anchor)
            ?: anchor
        return copy(periodStartDate = DateUtils.formatDate(start))
    }

    private fun parseDateOrNull(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return runCatching { DateUtils.parseDate(text) }.getOrNull()
    }

    // ---------- 细化小计划（子任务勾选） ----------

    /**
     * 勾选 / 取消勾选某个子任务。
     *
     * **全部勾满才算这个习惯当天完成**（与网页版 `db.ts:toggleSubTask` 一致）：
     * `isCompleted = 已勾数 >= 子任务总数`。反过来，取消任意一个子项，
     * 整体就退回未完成——避免出现"3/4 项却被标记为已完成"这种自相矛盾的状态。
     */
    suspend fun toggleSubTask(habitId: Long, subTaskId: String, date: String = DateUtils.today()) {
        val habit = habitDao.getHabitById(habitId) ?: return
        if (!canWrite(habit, date)) return

        val total = habit.subTaskList.size
        val existing = checkInDao.getCheckIn(habitId, date)

        val completedIds = existing
            ?.completedSubTaskIdList
            ?.toMutableList()
            ?: mutableListOf()

        if (completedIds.contains(subTaskId)) {
            completedIds.remove(subTaskId)
        } else {
            completedIds.add(subTaskId)
        }

        val allCompleted = total > 0 && completedIds.size >= total

        if (existing != null) {
            checkInDao.update(
                existing.copy(
                    completedSubTaskIds = completedIds,
                    isCompleted = allCompleted
                )
            )
        } else {
            checkInDao.insert(
                CheckIn(
                    habitId = habitId,
                    date = date,
                    isCompleted = allCompleted,
                    completedSubTaskIds = completedIds,
                    count = if (allCompleted) 1 else 0
                )
            )
        }
    }

    /**
     * 整组一次性全选 / 全不选（点击整张卡片时的行为）。
     *
     * 只用于**有子任务**的习惯；普通习惯仍走 [toggleCheckIn]。
     * 注意这里用「当前是否已全部完成」来决定方向，而不是看 completedIds 是否为空：
     * 否则出现过半途取消的情况（勾了 2/4 后点整卡），会错误地又全选一遍。
     */
    suspend fun toggleSubTaskGroup(habitId: Long, date: String = DateUtils.today()) {
        val habit = habitDao.getHabitById(habitId) ?: return
        if (!canWrite(habit, date)) return

        val subTasks = habit.subTaskList
        if (subTasks.isEmpty()) return

        val existing = checkInDao.getCheckIn(habitId, date)
        val allIds = subTasks.map { it.id }
        val currentlyAllDone =
            existing != null &&
                existing.isCompleted &&
                existing.completedSubTaskIdList.size >= subTasks.size

        if (currentlyAllDone) {
            checkInDao.update(
                existing.copy(
                    completedSubTaskIds = emptyList(),
                    isCompleted = false,
                    count = 0
                )
            )
        } else {
            if (existing != null) {
                checkInDao.update(
                    existing.copy(
                        completedSubTaskIds = allIds,
                        isCompleted = true,
                        count = 1
                    )
                )
            } else {
                checkInDao.insert(
                    CheckIn(
                        habitId = habitId,
                        date = date,
                        isCompleted = true,
                        completedSubTaskIds = allIds,
                        count = 1
                    )
                )
            }
        }
    }

    // ---------- 备份与恢复 ----------

    /** 生成完整备份 JSON（含已归档习惯与计数器周期归档）。 */
    suspend fun exportBackupJson(): String = BackupCodec.exportToJson(
        BackupCodec.BackupData(
            habits = habitDao.getAllHabitsSync(),
            checkIns = checkInDao.getAllCheckInsSync(),
            counters = counterDao.getAllCountersSync(),
            counterPeriodLogs = periodLogDao.getAllLogsSync()
        )
    )

    /**
     * 用备份文件**覆盖式**恢复当前数据。
     *
     * 为什么是覆盖而不是合并：习惯 id 与打卡记录通过外键绑定，局部合并很容易
     * 出现同一习惯两套 id 的冲突，用户很难预期结果。明确告诉用户"会覆盖"更安全。
     *
     * 顺序不可调换：
     * 1. 先清 check_ins 再清 habits（habits 的删除会 CASCADE 到 check_ins，显式清理避免歧义）；
     * 2. 写入时先 habits 后 check_ins —— Room 在事务里开启了 `foreign_keys=ON`，
     *    check_ins.habitId 引用不存在的 habit 会直接抛 FOREIGN KEY constraint failed。
     *    同理 `counter_period_logs.counterId` 引用计数器，必须先写 counters 再写归档记录。
     */
    suspend fun importBackupJson(json: String) {
        val data = BackupCodec.parse(json)
        checkInDao.deleteAll()
        habitDao.deleteAll()
        periodLogDao.deleteAll()
        counterDao.deleteAll()

        data.habits.forEach { habitDao.insert(it) }
        data.checkIns.forEach { checkInDao.insert(it) }
        data.counters.forEach { counterDao.insert(it) }
        data.counterPeriodLogs.forEach { periodLogDao.insert(it) }
    }

    // ---------- 教学示例数据 ----------

    /**
     * 首次安装时播种示例数据。
     *
     * 「是否首次」由 [AppSettings.hasSeededSample] 的持久标记判定，**不靠"数据为空"判定**——
     * 后者会让用户手动删完所有习惯后，下次冷启动示例又自己长回来，永远删不干净。
     * 老用户升级安装时 habits 非空，也只会打个标记而不会强塞示例。
     */
    suspend fun seedSampleDataIfFirstRun(settings: AppSettings) {
        if (settings.hasSeededSample()) return
        if (habitDao.getAllHabitsSync().isNotEmpty()) {
            // 升级前就已在使用的用户：只补标记，不插入示例。
            settings.markSampleSeeded()
            return
        }
        insertSampleData()
        settings.markSampleSeeded()
    }

    /** 显式载入示例，**追加**而非覆盖，不会动已有数据。 */
    suspend fun loadSampleData() {
        insertSampleData()
    }

    private suspend fun insertSampleData() {
        val today = DateUtils.today()
        SampleData.habits(today).forEach { habitDao.insert(it) }
        // 走 Dao 直接插入会绕过 addCounter 里的归一化，所以这里显式补上周期锚点 ——
        // 否则示例里的「今日咖啡记录」要等用户点过第一下 +1 才拿到锚点，
        // 在那之前卡片上的周期徽章是空的。
        SampleData.counters().forEach { counter ->
            counterDao.insert(
                counter.copy(
                    periodStartDate = newPeriodAnchor(
                        counter.resetPeriod, counter.resetIntervalDays
                    )
                )
            )
        }
    }
}
