package io.github.molishadaze.weijing.model

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
