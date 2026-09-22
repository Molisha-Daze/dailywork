package io.github.molishadaze.weijing.model

import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate

/**
 * 大计划之下的细化小计划。
 *
 * 注意**完成状态不在这里**：勾选是"某天"的行为，因此存在 [io.github.molishadaze.weijing.data.entity.CheckIn]
 * 的 `completedSubTaskIds` 里。习惯本身只定义"有哪些子项"，不记录"谁做完了"。
 */
data class SubTask(
    val id: String, // 稳定标识符，勾选状态靠它关联，改名不会丢进度
    val title: String
) {
    companion object {
        /** 新建子任务时生成 id：时间 + 序号，避免列表内重复。 */
        fun newId(seed: Int = (0..Int.MAX_VALUE).random()): String = "sub-${System.currentTimeMillis()}-$seed"
    }
}

data class HabitWithStats(
    val habit: Habit,
    /** 今天是否有排期（由 HabitSchedule.isScheduled 判定，今日页据此过滤） */
    val scheduledToday: Boolean = true,
    /** 今天是否已完成（计数器习惯按 count >= targetCount 判定） */
    val isCompletedToday: Boolean,
    val todayCheckIn: CheckIn? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalCheckIns: Int = 0,
    /** 细化小计划总数（无子任务时为 0）。 */
    val totalSubTaskCount: Int = 0,
    /** 今天已勾选的细化小计划数量。 */
    val completedSubTaskCount: Int = 0,
    /** 是否是大计划（有可勾选的子任务）。UI 据此决定要不要渲染勾选列表。 */
    val hasSubTasks: Boolean = false
)

/**
 * 一条「即将到来」的日程：某习惯在 [date] 会第一次出现。
 *
 * 每个习惯在列表里只会出现一次（取它下一次的日期），
 * 所以循环习惯不会把未来几十次排期全铺开。
 */
data class UpcomingHabit(
    val habit: Habit,
    val date: LocalDate
)
