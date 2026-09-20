package io.github.molishadaze.weijing.util

import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import io.github.molishadaze.weijing.model.SubTask
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件的编解码。
 *
 * 刻意不引入 Gson / Moshi：
 * - 实体字段是固定的、数量有限，手写编解码反而更可控；
 * - 更重要的是**保持字段顺序与空值语义显式可见**，将来某个字段改名时，
 *   这里会显式报错，而不是像反射序列化那样静默产生读不回来的备份文件。
 *
 * 注意：本类依赖 org.json，因此**不能在纯 JVM 单元测试里使用**
 * （android.jar 的方法桩会抛 RuntimeException("Stub!")）。
 */
object BackupCodec {

    // 随「细化小计划」加入而升版为 3，随「计数器周期归零」升为 4。
    // 旧文件（1/2/3）依然可被解析：缺的字段各自取默认值（子任务为空列表、
    // 计数器不归零、周期归档为空），不会报错。
    const val FORMAT_VERSION = 4

    data class BackupData(
        val habits: List<Habit>,
        val checkIns: List<CheckIn>,
        val counters: List<StandaloneCounter>,
        /**
         * 独立计数器的历史周期归档。
         *
         * 默认空列表是为了让 v1~v3 的旧备份文件仍能构造出 [BackupData] ——
         * 老备份里没有这一段，解析出来就是空的，导入后计数器照常工作，只是没有历史。
         */
        val counterPeriodLogs: List<CounterPeriodLog> = emptyList()
    )

    fun exportToJson(data: BackupData): String {
        val habitsArray = JSONArray()
        data.habits.forEach { h ->
            habitsArray.put(
                JSONObject().apply {
                    put("id", h.id)
                    put("name", h.name)
                    putNullable("description", h.description)
                    put("iconName", h.iconName)
                    put("colorHex", h.colorHex)
                    putNullable("reminderTime", h.reminderTime)
                    put("sortOrder", h.sortOrder)
                    put("archived", h.archived)
                    put("recurrenceType", h.recurrenceType)
                    put("startDate", h.startDate)
                    putNullable("endDate", h.endDate)
                    putNullable("weeklyDays", h.weeklyDays)
                    putNullable("monthlyDays", h.monthlyDays)
                    put("intervalDays", h.intervalDays)
                    put("isCounter", h.isCounter)
                    put("targetCount", h.targetCount)
                    put("unit", h.unit)
                    put("isParentPlan", h.isParentPlan)
                    put("subTasks", subTasksToArray(h.subTaskList))
                }
            )
        }

        val checkInsArray = JSONArray()
        data.checkIns.forEach { c ->
            checkInsArray.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("habitId", c.habitId)
                    put("date", c.date)
                    putNullable("photoPath", c.photoPath)
                    putNullable("note", c.note)
                    put("count", c.count)
                    put("isCompleted", c.isCompleted)
                    put("completedSubTaskIds", idsToArray(c.completedSubTaskIdList))
                    put("createdAt", c.createdAt)
                }
            )
        }

        val countersArray = JSONArray()
        data.counters.forEach { c ->
            countersArray.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("currentCount", c.currentCount)
                    put("hasLimit", c.hasLimit)
                    putNullable("limitCount", c.limitCount)
                    put("unit", c.unit)
                    put("step", c.step)
                    put("colorHex", c.colorHex)
                    putNullable("note", c.note)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                    put("resetPeriod", c.resetPeriod)
                    put("resetIntervalDays", c.resetIntervalDays)
                    putNullable("periodStartDate", c.periodStartDate)
                }
            )
        }

        val periodLogsArray = JSONArray()
        data.counterPeriodLogs.forEach { l ->
            periodLogsArray.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("counterId", l.counterId)
                    put("periodStart", l.periodStart)
                    put("periodEnd", l.periodEnd)
                    put("count", l.count)
                    put("unit", l.unit)
                    put("createdAt", l.createdAt)
                }
            )
        }

        return JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("habits", habitsArray)
            put("checkIns", checkInsArray)
            put("standaloneCounters", countersArray)
            put("counterPeriodLogs", periodLogsArray)
        }.toString(2) // 缩进 2 空格，便于用户在文本编辑器里查看
    }

    /** @throws org.json.JSONException 文件不是合法 JSON 或缺少字段时抛出。 */
    fun parse(json: String): BackupData {
        val root = JSONObject(json)
        val version = root.optInt("formatVersion", 1)
        if (version > FORMAT_VERSION) {
            throw IllegalArgumentException(
                "备份文件版本 $version 高于当前 App 支持的 $FORMAT_VERSION，请升级 App 后再恢复。"
            )
        }

        val habitsArray = root.optJSONArray("habits") ?: JSONArray()
        val habits = buildList {
            repeat(habitsArray.length()) { i ->
                val o = habitsArray.getJSONObject(i)
                add(
                    Habit(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", ""),
                        description = o.optStringOrNull("description"),
                        iconName = o.optString("iconName", "Star"),
                        colorHex = o.optString("colorHex", "#10B981"),
                        reminderTime = o.optStringOrNull("reminderTime"),
                        sortOrder = o.optInt("sortOrder", 0),
                        archived = o.optBoolean("archived", false),
                        recurrenceType = o.optString("recurrenceType", "daily"),
                        startDate = o.optString("startDate", ""),
                        endDate = o.optStringOrNull("endDate"),
                        weeklyDays = o.optStringOrNull("weeklyDays"),
                        monthlyDays = o.optStringOrNull("monthlyDays"),
                        intervalDays = o.optInt("intervalDays", 1),
                        isCounter = o.optBoolean("isCounter", false),
                        targetCount = o.optInt("targetCount", 1),
                        unit = o.optString("unit", "次"),
                        isParentPlan = o.optBoolean("isParentPlan", false),
                        subTasks = parseSubTasks(o.optJSONArray("subTasks"))
                    )
                )
            }
        }

        val checkInsArray = root.optJSONArray("checkIns") ?: JSONArray()
        val checkIns = buildList {
            repeat(checkInsArray.length()) { i ->
                val o = checkInsArray.getJSONObject(i)
                add(
                    CheckIn(
                        id = o.optLong("id", 0L),
                        habitId = o.optLong("habitId", 0L),
                        date = o.optString("date", ""),
                        photoPath = o.optStringOrNull("photoPath"),
                        note = o.optStringOrNull("note"),
                        count = o.optInt("count", 1),
                        isCompleted = o.optBoolean("isCompleted", true),
                        completedSubTaskIds = parseIds(o.optJSONArray("completedSubTaskIds")),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val countersArray = root.optJSONArray("standaloneCounters") ?: JSONArray()
        val counters = buildList {
            repeat(countersArray.length()) { i ->
                val o = countersArray.getJSONObject(i)
                add(
                    StandaloneCounter(
                        id = o.optLong("id", 0L),
                        name = o.optString("name", ""),
                        currentCount = o.optInt("currentCount", 0),
                        hasLimit = o.optBoolean("hasLimit", false),
                        limitCount = o.optIntOrNull("limitCount"),
                        unit = o.optString("unit", "次"),
                        step = o.optInt("step", 1),
                        colorHex = o.optString("colorHex", "#EF4444"),
                        note = o.optStringOrNull("note"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        // 未知的周期名一律退回"不归零"：宁可少归零，
                        // 也不能让一个读不懂的值变成某种"每天都在清数据"的行为。
                        resetPeriod = o.optString("resetPeriod", StandaloneCounter.RESET_NONE)
                            .takeIf { it in StandaloneCounter.RESET_OPTIONS }
                            ?: StandaloneCounter.RESET_NONE,
                        resetIntervalDays = o.optInt("resetIntervalDays", 1).coerceAtLeast(1),
                        periodStartDate = o.optStringOrNull("periodStartDate")
                    )
                )
            }
        }

        // 缺少这一段（v3 及更早的备份）时返回空列表，不报错。
        val periodLogsArray = root.optJSONArray("counterPeriodLogs") ?: JSONArray()
        val periodLogs = buildList {
            repeat(periodLogsArray.length()) { i ->
                val o = periodLogsArray.getJSONObject(i)
                add(
                    CounterPeriodLog(
                        id = o.optLong("id", 0L),
                        counterId = o.optLong("counterId", 0L),
                        periodStart = o.optString("periodStart", ""),
                        periodEnd = o.optString("periodEnd", ""),
                        count = o.optInt("count", 0),
                        unit = o.optString("unit", "次"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return BackupData(habits, checkIns, counters, periodLogs)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun subTasksToArray(tasks: List<SubTask>): JSONArray = JSONArray().apply {
        tasks.forEach { task ->
            put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
            })
        }
    }

    private fun parseSubTasks(array: JSONArray?): List<SubTask>? {
        if (array == null) return null
        return buildList {
            repeat(array.length()) { i ->
                val o = array.getJSONObject(i)
                add(SubTask(id = o.optString("id", ""), title = o.optString("title", "")))
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun idsToArray(ids: List<String>): JSONArray = JSONArray().apply {
        ids.forEach { put(it) }
    }

    private fun parseIds(array: JSONArray?): List<String>? {
        if (array == null) return null
        return buildList {
            repeat(array.length()) { i -> add(array.optString(i, "")) }
        }.takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}
