package io.github.molishadaze.weijing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.molishadaze.weijing.model.SubTask

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null, // 多行内容描述（如训练动作文本）
    val iconName: String = "Star",
    val colorHex: String = "#10B981",
    val reminderTime: String? = null, // e.g. "08:30"
    val sortOrder: Int = 0,
    val archived: Boolean = false,

    // 重复方式: "none", "daily", "weekly", "monthly", "interval"
    val recurrenceType: String = "daily",
    val startDate: String = "", // "yyyy-MM-dd"
    val endDate: String? = null, // "yyyy-MM-dd"
    val weeklyDays: String? = null, // comma-separated: "1,3,5" (1=Mon, 7=Sun)
    val monthlyDays: String? = null, // comma-separated: "15"
    val intervalDays: Int = 1, // 每 N 天

    // 计数器功能
    val isCounter: Boolean = false,
    val targetCount: Int = 1, // 如 3 杯水
    val unit: String = "次", // "杯", "组", "次"

    // 大计划 + 细化小计划（子任务）。
    // 只定义"有哪些子项"，**完成状态在 CheckIn.completedSubTaskIds 里**。
    // 两者独立：isParentPlan 为 true 但 subTasks 为空时，退化成普通习惯。
    val isParentPlan: Boolean = false,
    val subTasks: List<SubTask>? = null
) {
    /** 安全读取子任务，屏蔽 null。 */
    val subTaskList: List<SubTask>
        get() = subTasks.orEmpty()
}
