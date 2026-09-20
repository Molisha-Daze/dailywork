package io.github.molishadaze.weijing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId", "date"], unique = true),
        Index(value = ["date"])
    ]
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val date: String, // format "yyyy-MM-dd" in local timezone
    val photoPath: String? = null,
    val note: String? = null,
    val count: Int = 1, // 当前计数 (如喝水 2 杯)
    val isCompleted: Boolean = true, // 是否已完成
    /**
     * 当天已勾选的细化小计划 id 列表（对应 Habit.subTasks）。
     * 勾选是"某天"的行为，所以存在这里而不是 Habit 上。
     * 全部勾满时，上层会把 isCompleted 置为 true（见 Repository.toggleSubTask）。
     */
    val completedSubTaskIds: List<String>? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 安全读取，屏蔽 null。 */
    val completedSubTaskIdList: List<String>
        get() = completedSubTaskIds.orEmpty()
}
