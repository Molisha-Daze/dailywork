package io.github.molishadaze.weijing.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.molishadaze.weijing.data.entity.Habit
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun getAllActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllArchivedHabits(): Flow<List<Habit>>

    @Query("UPDATE habits SET archived = 1 WHERE id = :id")
    suspend fun archiveHabit(id: Long)

    @Query("UPDATE habits SET archived = 0 WHERE id = :id")
    suspend fun unarchiveHabit(id: Long)

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllActiveHabitsList(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabitById(id: Long): Habit?

    /**
     * 新增习惯。
     *
     * 注意：这里必须用 IGNORE 而不是 REPLACE。REPLACE 的 SQLite 语义是「先删后插」，
     * 而 check_ins 对 habits 有 ForeignKey(onDelete = CASCADE)，一旦有人误把已存在的
     * habit 传进来，会连带把该习惯的全部打卡记录删掉。
     * 更新一律走 [update]。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Delete
    suspend fun delete(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 全量导出用：**包含已归档**的习惯，否则备份会丢数据。
     * 注意与 [getAllActiveHabitsList] 区分，后者是给界面用的。
     */
    @Query("SELECT * FROM habits ORDER BY id ASC")
    suspend fun getAllHabitsSync(): List<Habit>

    /** 仅供备份恢复时清空表。会级联删除 check_ins（外键 onDelete = CASCADE）。 */
    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    @Query("UPDATE habits SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
