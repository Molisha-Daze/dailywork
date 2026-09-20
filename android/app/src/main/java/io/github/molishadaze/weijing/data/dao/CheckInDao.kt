package io.github.molishadaze.weijing.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Update
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.Habit
import kotlinx.coroutines.flow.Flow

data class CheckInWithHabit(
    @Embedded val checkIn: CheckIn,
    @Relation(
        parentColumn = "habitId",
        entityColumn = "id"
    )
    val habit: Habit?
)

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: CheckIn): Long

    @Update
    suspend fun update(checkIn: CheckIn)

    @Delete
    suspend fun delete(checkIn: CheckIn)

    @Query("DELETE FROM check_ins WHERE habitId = :habitId AND date = :date")
    suspend fun deleteByHabitAndDate(habitId: Long, date: String)

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getCheckIn(habitId: Long, date: String): CheckIn?

    @Query("SELECT * FROM check_ins WHERE date = :date")
    fun getCheckInsForDate(date: String): Flow<List<CheckIn>>

    @Query("SELECT * FROM check_ins WHERE date = :date")
    suspend fun getCheckInsForDateSync(date: String): List<CheckIn>

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId ORDER BY date ASC")
    suspend fun getCheckInsForHabitSync(habitId: Long): List<CheckIn>

    @Query("SELECT * FROM check_ins ORDER BY date DESC, createdAt DESC")
    fun getAllCheckIns(): Flow<List<CheckIn>>

    @androidx.room.Transaction
    @Query("SELECT * FROM check_ins ORDER BY date DESC, createdAt DESC")
    fun getAllCheckInsWithHabit(): Flow<List<CheckInWithHabit>>

    /** 全量导出用的一次性快照。 */
    @Query("SELECT * FROM check_ins ORDER BY id ASC")
    suspend fun getAllCheckInsSync(): List<CheckIn>

    /** 仅供备份恢复时清空表。 */
    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()

    @Query("UPDATE check_ins SET photoPath = :photoPath WHERE habitId = :habitId AND date = :date")
    suspend fun updatePhotoPath(habitId: Long, date: String, photoPath: String?)
}
