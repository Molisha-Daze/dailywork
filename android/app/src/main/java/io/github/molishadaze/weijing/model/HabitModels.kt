package io.github.molishadaze.weijing.model

import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import java.time.LocalDate

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

