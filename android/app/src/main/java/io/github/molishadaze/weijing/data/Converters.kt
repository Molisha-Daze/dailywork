package io.github.molishadaze.weijing.data

import androidx.room.TypeConverter
import io.github.molishadaze.weijing.model.SubTask
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room 无法直接存储对象列表，这里把子任务列表与"已勾选的 id 列表"序列化成 JSON 文本。
 *
 * 与 [io.github.molishadaze.weijing.util.BackupCodec] 一样用 org.json 手写而非引入 Gson/Moshi：
 * 字段极少，手写更可控，且不引新依赖。
 * ⚠️ 同 BackupCodec：依赖 org.json，**不能放进纯 JVM 单元测试**。
 * 目前所有单测都不触达 Room，因此不受影响。
 *
 * 空列表一律存成 SQL NULL 而不是 "[]"，这样旧版本的数据列天然是 NULL，语义一致。
 */
class Converters {

    @TypeConverter
    fun subTasksToJson(value: List<SubTask>?): String? {
        if (value.isNullOrEmpty()) return null
        val arr = JSONArray()
        value.forEach { task ->
            arr.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                }
            )
        }
        return arr.toString()
    }

    @TypeConverter
    fun jsonToSubTasks(value: String?): List<SubTask>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(value)
            buildList {
                repeat(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    add(SubTask(id = o.optString("id", ""), title = o.optString("title", "")))
                }
            }
        }.getOrNull() // 解析失败退化为空，宁可丢子任务也不要让整张表读不出来
    }

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? {
        if (value.isNullOrEmpty()) return null
        return JSONArray().apply { value.forEach { put(it) } }.toString()
    }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(value)
            buildList {
                repeat(arr.length()) { i -> add(arr.optString(i, "")) }
            }
        }.getOrNull()
    }
}
