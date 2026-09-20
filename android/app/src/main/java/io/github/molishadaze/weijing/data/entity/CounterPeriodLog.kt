package io.github.molishadaze.weijing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 独立计数器的**已结算周期**归档。
 *
 * 为什么需要这张表：周期归零天然会丢数据。用户想说的是
 * 「今天喝三杯，明天从零开始，但昨天那三杯得留着」——
 * 于是清零前先把上一周期的最终值抄一份到这里，再清 [StandaloneCounter.currentCount]。
 *
 * 归档只发生在三种时刻（都通过 `HabitRepository.archiveCounterPeriod` 走同一段逻辑）：
 * 1. 惰性结算：任意一次前台恢复 / 计数前发现已经跨到新周期；
 * 2. 用户手动「清零」——等价于提前结算当前周期；
 * 3. 无。**删除计数器不归档**，历史随 CASCADE 一起消失（用户删实体就该连历史一起删）。
 *
 * 同一天同一计数器可能被多次归档（比如手动清零后又喝了两杯，次日再结算），
 * 因此 (counterId, periodStart) 在仓库层做**合并累加**，而不是插两条 ——
 * 否则历史列表里会出现「9月20日 3 杯」「9月20日 2 杯」这种看着像 bug 的重复行。
 *
 * 日期用 "yyyy-MM-dd" 字符串而非时间戳，与 [CheckIn.date] 保持一致，
 * 将来两端交换数据时不用再做时区换算。
 */
@Entity(
    tableName = "counter_period_logs",
    foreignKeys = [
        ForeignKey(
            entity = StandaloneCounter::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("counterId")]
)
data class CounterPeriodLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 归属的计数器。 */
    val counterId: Long,

    /** 周期首日（含），"yyyy-MM-dd"。 */
    val periodStart: String,

    /** 周期末日（含），"yyyy-MM-dd"。 */
    val periodEnd: String,

    /** 该周期结束时的累计值。恒 > 0 —— 空周期不留记录，避免历史里全是 0。 */
    val count: Int,

    /**
     * 归档当时的单位快照。
     *
     * 刻意冗余存储：用户之后可能把「杯」改成「毫升」，
     * 老记录不该跟着变（那就不是当时记的东西了）。
     */
    val unit: String,

    val createdAt: Long = System.currentTimeMillis()
)
