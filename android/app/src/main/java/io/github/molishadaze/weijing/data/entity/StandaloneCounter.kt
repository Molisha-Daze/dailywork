package io.github.molishadaze.weijing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 独立计数器：脱离「习惯/排期」概念的自由计数实体。
 *
 * 与 [Habit.isCounter] 是两回事，切勿混用：
 * - [Habit.isCounter] 是习惯内部的完成方式（如"喝水 3 杯"达标才算打卡），归属 today/history 链路；
 * - 本实体则是完全独立的计数器（如"冰箱里还剩几罐可乐"），没有日期、没有连续天数、不参与热力图。
 *
 * 字段命名与网页版 src/types.ts 的 StandaloneCounter 保持一致，
 * 便于将来两边做数据交换时对照。
 */
@Entity(tableName = "standalone_counters")
data class StandaloneCounter(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // 计数器名称，如"冰箱里的可乐"
    val currentCount: Int = 0, // 当前计数值
    val hasLimit: Boolean = false, // 是否设置上限
    val limitCount: Int? = null, // 上限值，未设置时为 null
    val unit: String = "次", // 单位：罐、杯、次、件
    val step: Int = 1, // 单次点击增减步长，至少为 1
    val colorHex: String = "#EF4444", // 主题色
    val note: String? = null, // 备注说明
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * 归零周期。取值见下方 RESET_* 常量；默认 [RESET_NONE] 表示永不自动归零
     * （「冰箱里还剩几罐可乐」这类库存计数必须保持这个语义）。
     *
     * 周期到了之后不是"丢掉"计数，而是把上一周期的值归档进
     * [CounterPeriodLog]，再把 [currentCount] 清零 —— 历史始终可查。
     */
    val resetPeriod: String = RESET_NONE,

    /** 仅 [RESET_INTERVAL] 使用：每隔几天归零，至少为 1。 */
    val resetIntervalDays: Int = 1,

    /**
     * 当前周期的锚点日期 "yyyy-MM-dd"。
     *
     * 语义是「当前这个周期是从哪天开始的」，不是「上次归零发生在哪天」：
     * - 周/月周期对齐自然周（周一）/ 自然月（1 号），所以锚点会被校正到周期首日；
     * - 每 N 天周期以锚点为起点滚动。
     *
     * [RESET_NONE] 时恒为 null。老数据升级后该列为 NULL，首次结算时按今天补齐。
     */
    val periodStartDate: String? = null
) {
    companion object {
        /** 不自动归零（默认）。 */
        const val RESET_NONE = "none"

        /** 每个自然日归零。 */
        const val RESET_DAILY = "daily"

        /** 每个自然周（周一为一周之始）归零。 */
        const val RESET_WEEKLY = "weekly"

        /** 每个自然月（1 号）归零。 */
        const val RESET_MONTHLY = "monthly"

        /** 每 N 个自然日归零，N 取 [resetIntervalDays]。 */
        const val RESET_INTERVAL = "interval"

        /**
         * 弹窗里的展示顺序：不归零 → 每日 → 每周 → 每月 → 每 N 天。
         * 由粗到细再回到自定义，跟用户的思考顺序一致。
         */
        val RESET_OPTIONS = listOf(
            RESET_NONE, RESET_DAILY, RESET_WEEKLY, RESET_MONTHLY, RESET_INTERVAL
        )

        /** 可选主题色，与网页版 AddEditCounterModal 的 PRESET_COLORS 对齐。 */
        val PRESET_COLORS = listOf(
            "#EF4444", // 活力红
            "#F59E0B", // 琥珀橙
            "#10B981", // 翡翠绿
            "#0EA5E9", // 天蓝色
            "#8B5CF6", // 幻紫色
            "#EC4899" // 玫瑰粉
        )
    }
}
