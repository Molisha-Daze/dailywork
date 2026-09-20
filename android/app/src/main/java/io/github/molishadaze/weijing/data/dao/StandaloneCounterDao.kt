package io.github.molishadaze.weijing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.molishadaze.weijing.data.entity.StandaloneCounter
import kotlinx.coroutines.flow.Flow

@Dao
interface StandaloneCounterDao {

    /**
     * 列表顺序固定为**创建顺序**（id 升序），绝不跟随 updatedAt。
     *
     * 早先这里是 `ORDER BY updatedAt DESC`，本意是「最近动的排最前」，
     * 但 [applyStep] / [setCount] 每调用一次都会写 updatedAt ——
     * 于是每点一下 +1，这张卡立刻变成「最新」，在列表里向上跳一位。
     * 用户看到的现象就是「计数器加一后位置自动上去了」，多张卡时更是来回乱窜。
     *
     * 按 id 升序后位置绝对稳定；新建的追加在末尾，也和网页版
     * `IndexedDB.getAll()` 天然按主键升序返回的顺序一致（两边观感对齐）。
     */
    @Query("SELECT * FROM standalone_counters ORDER BY id ASC")
    fun getAllCounters(): Flow<List<StandaloneCounter>>

    @Query("SELECT * FROM standalone_counters WHERE id = :id LIMIT 1")
    suspend fun getCounterById(id: Long): StandaloneCounter?

    /**
     * 同为正整数固定的增visit：这里没有外键级联风险（计数器是孤立实体），
     * 但依然用 IGNORE —— 更新统一走 [update]，避免任何"先删后插"的语义意外。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(counter: StandaloneCounter): Long

    @Update
    suspend fun update(counter: StandaloneCounter)

    /** 全量导出用的一次性快照。 */
    @Query("SELECT * FROM standalone_counters ORDER BY id ASC")
    suspend fun getAllCountersSync(): List<StandaloneCounter>

    /** 仅供备份恢复时清空表。 */
    @Query("DELETE FROM standalone_counters")
    suspend fun deleteAll()

    @Query("DELETE FROM standalone_counters WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 原子自增/自减，避免「先读再写」在多协程下丢 Updates。
     * 金额式写法与自己读出来 + delta 再 [update] 的区别在于：这里在 SQL 层完成累加。
     */
    @Query(
        """
        UPDATE standalone_counters
        SET currentCount = MAX(0, currentCount + :delta),
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun applyStep(id: Long, delta: Int, now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE standalone_counters
        SET currentCount = :value,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun setCount(id: Long, value: Int, now: Long = System.currentTimeMillis())

    /**
     * 结算后把锚点推进到新周期的首日，并把计数清零。
     *
     * 清零与推进锚点必须在**同一条 SQL** 里完成：拆成两次写的话，
     * 中间若被进程杀死（低电量后台被回收很常见），就会留下
     * 「锚点已推进、计数还是旧值」的状态 —— 那个旧值会被当成新周期的数据，悄悄重复计一次。
     *
     * 这里的 periodStartDate 是**无条件覆盖**的，不做 CAS（不比对旧锚点）：
     * 同一天跨重入时传入的新锚点两次算出来是同一个值，重复执行无副作用。
     */
    @Query(
        """
        UPDATE standalone_counters
        SET currentCount = 0,
            periodStartDate = :periodStartDate,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun applyRollover(id: Long, periodStartDate: String, now: Long = System.currentTimeMillis())

    /** 只给锚点补个值（老数据升级后 periodStartDate 为 NULL 时用），不动计数。 */
    @Query(
        """
        UPDATE standalone_counters
        SET periodStartDate = :periodStartDate,
            updatedAt = :now
        WHERE id = :id AND periodStartDate IS NULL
        """
    )
    suspend fun seedPeriodStartIfNull(
        id: Long,
        periodStartDate: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 结算只需处理配了周期的计数器。
     *
     * 加这条查询是为了别让「每次点 +1 都全表扫一遍」——绝大多数计数器是
     * 不归零的库存量（如「冰箱里的可乐」），它们根本没有周期可言。
     */
    @Query("SELECT * FROM standalone_counters WHERE resetPeriod != 'none' ORDER BY id ASC")
    suspend fun getPeriodicCountersSync(): List<StandaloneCounter>
}
