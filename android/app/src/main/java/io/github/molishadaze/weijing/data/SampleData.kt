package io.github.molishadaze.weijing.data

import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.model.SubTask

/**
 * 教学用示例数据。
 *
 * 内容与网页版 `src/services/db.ts` 的 `initSampleDataIfEmpty()` 保持一致的文案，
 * 目的不是"填充数据"，而是让新用户打开 App 就能看懂每种习惯长什么样：
 * - 喝水 → 计数器型习惯（有目标次数与单位）
 * - 力量训练 → 多行描述的复合习惯
 * - 晨跑 / 阅读 → 普通每日习惯（一个带早提醒、一个带晚提醒）
 * 两个计数器则分别演示「有上限」与「无上限」两种形态。
 *
 * ⚠️ 图标名必须是 [io.github.molishadaze.weijing.ui.components.getIconVector] 支持的枚举值，
 * 否则会静默回退成 Star，用户会以为是 App 的 bug。
 */
object SampleData {

    /** @param today 起始日期 "yyyy-MM-dd"，一般传今天，这样示例当天就处在有效期内。 */
    fun habits(today: String): List<Habit> = listOf(
        Habit(
            name = "今日喝水打卡",
            description = "保持身体水分充盈，每天定时补充健康饮用水",
            iconName = "Water",
            colorHex = "#14B8A6",
            reminderTime = "10:00",
            sortOrder = 0,
            recurrenceType = "daily",
            startDate = today,
            // 计数器型习惯：目标 3 杯才算完成
            isCounter = true,
            targetCount = 3,
            unit = "杯"
        ),
        Habit(
            name = "力量与体能训练",
            // 演示「大计划 + 细化小计划」：4 个动作可逐项勾选，全勾完才算当天完成。
            // id 用固定字符串而非随机值——示例是静态数据，固定 id 便于将来对照与排错。
            description = "按顺序完成下列动作，全部勾完即视为今天的训练已完成",
            iconName = "Fitness",
            colorHex = "#8B5CF6",
            reminderTime = "18:30",
            sortOrder = 1,
            recurrenceType = "daily",
            startDate = today,
            isParentPlan = true,
            subTasks = listOf(
                SubTask(id = "sub-squat", title = "深蹲 4 组 x 10 次"),
                SubTask(id = "sub-bench", title = "卧推 4 组 x 10 次"),
                SubTask(id = "sub-pullup", title = "引体向上 3 组 x 8 次"),
                SubTask(id = "sub-stretch", title = "肌肉静态拉伸 10 分钟")
            )
        ),
        Habit(
            name = "晨跑打卡 3 公里",
            description = "清晨有氧慢跑，配速保持在 6 分钟/公里，唤醒整天元气",
            iconName = "Run",
            colorHex = "#10B981",
            reminderTime = "07:30",
            sortOrder = 2,
            recurrenceType = "daily",
            startDate = today
        ),
        Habit(
            name = "深度阅读 30 分钟",
            description = "专注沉浸式读书，记录精彩文段与思考笔记",
            iconName = "Book",
            colorHex = "#3B82F6",
            reminderTime = "21:00",
            sortOrder = 3,
            recurrenceType = "daily",
            startDate = today
        )
    )

    /**
     * 分别演示三种形态：
     * - 「冰箱里的可乐」有上限、不归零 —— 库存量，喝掉一罐就该少一罐，跨天不能清零；
     * - 「今日咖啡记录」无上限、每日归零 —— 演示周期归零：今天喝两杯从 0 数起，
     *   过了零点自动归零，但今天这两杯会留在历史记录里。
     */
    fun counters(): List<StandaloneCounter> = listOf(
        StandaloneCounter(
            name = "冰箱里的可乐",
            currentCount = 6,
            hasLimit = true,
            limitCount = 12,
            unit = "罐",
            step = 1,
            colorHex = "#EF4444",
            note = "喝一次点一下，随时掌握库存量，少于 3 罐及时补货"
        ),
        StandaloneCounter(
            name = "今日咖啡记录",
            currentCount = 2,
            hasLimit = false,
            unit = "杯",
            step = 1,
            colorHex = "#F59E0B",
            note = "喝一杯点一次，每天自动归零，历史记录里可回看每天喝了几杯",
            resetPeriod = StandaloneCounter.RESET_DAILY
        )
    )
}
