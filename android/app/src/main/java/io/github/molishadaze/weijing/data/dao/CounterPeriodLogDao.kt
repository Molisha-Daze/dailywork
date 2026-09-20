package io.github.molishadaze.weijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import kotlinx.coroutines.flow.Flow

/**
 * 独立计数器的周期归档表。
 *
 * 列表顺序固定为**最近的周期在前**（periodStart 降序，同日再按 id 降序兜底）——
 * 这里挂时间排序是安全的：归档记录一旦写入就不再变动（合并累加会保持 periodStart 不变），
 * 不会出现"点一下列表就跳位"的问题。
 */
@Dao
interface CounterPeriodLogDao {

    @Query("SELECT * FROM counter_period_logs ORDER BY periodStart DESC, id DESC")
    fun getAllLogs(): Flow<List<CounterPeriodLog>>

    /** 全量导出用的一次性快照。 */
    @Query("SELECT * FROM counter_period_logs ORDER BY periodStart DESC, id DESC")
    suspend fun getAllLogsSync(): List<CounterPeriodLog>

    /**
     * 查某个计数器在指定周期是否已有归档。
     *
     * 供「合并累加」判定使用：同一天先手动清零、之后又喝了，应当并入同一条记录。
     */
    @Query(
        """
        SELECT * FROM counter_period_logs
        WHERE counterId = :counterId AND periodStart = :periodStart
        LIMIT 1
        """
    )
    suspend fun findByCounterAndStart(counterId: Long, periodStart: String): CounterPeriodLog?

    /**
     * 用 IGNORE 而非 REPLACE：REPLACE 会先删后插、换掉 id，
     * 而本表的插入判定已在仓库层用 [findByCounterAndStart] 做过，这里只需兜底不重复。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: CounterPeriodLog): Long

    @Update
    suspend fun update(log: CounterPeriodLog)

    /** 仅供备份恢复时清空表。 */
    @Query("DELETE FROM counter_period_logs")
    suspend fun deleteAll()
}
