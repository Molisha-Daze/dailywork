package io.github.molishadaze.weijing.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.molishadaze.weijing.data.dao.CheckInDao
import io.github.molishadaze.weijing.data.dao.CounterPeriodLogDao
import io.github.molishadaze.weijing.data.dao.HabitDao
import io.github.molishadaze.weijing.data.dao.StandaloneCounterDao
import io.github.molishadaze.weijing.data.entity.CheckIn
import io.github.molishadaze.weijing.data.entity.CounterPeriodLog
import io.github.molishadaze.weijing.data.entity.Habit
import io.github.molishadaze.weijing.data.entity.StandaloneCounter

/**
 * 1 → 2：新增独立计数器表。
 *
 * 这是纯粹的新表创建，不影响 habits / check_ins 的任何既有数据，
 * 因此对已安装用户是无损升级。但仍然必须显式写出 migration —— 见下方注释。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `standalone_counters` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `currentCount` INTEGER NOT NULL,
                `hasLimit` INTEGER NOT NULL,
                `limitCount` INTEGER,
                `unit` TEXT NOT NULL,
                `step` INTEGER NOT NULL,
                `colorHex` TEXT NOT NULL,
                `note` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * 2 → 3：为「大计划 + 细化小计划」加字段。
 *
 * 两张表各加列，纯附加、不改既有列，因此对已安装用户是无损升级。
 * SQLite 的 ALTER TABLE 一条语句只能加一列，所以必须拆成三句。
 * `isParentPlan` 用 NOT NULL DEFAULT 0，保证老行有确定值而不是 NULL。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN isParentPlan INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN subTasks TEXT")
        db.execSQL("ALTER TABLE check_ins ADD COLUMN completedSubTaskIds TEXT")
    }
}

/**
 * 3 → 4：独立计数器支持「按周期自动归零」。
 *
 * 三列加在 standalone_counters 尾部，外加一张归档表。
 * 老数据（含用户已有的计数器）默认 resetPeriod = 'none'，即维持原有"永不归零"行为，
 * 不会因为升级就突然把谁的数据清了。
 *
 * ⚠️ NOT NULL 的新列**必须**带 DEFAULT —— SQLite 的 ALTER TABLE ADD COLUMN 无法
 * 给既有行填值，不带 DEFAULT 会直接报错。这里实体侧没有声明 `@ColumnInfo(defaultValue)`
 * （Kotlin 的默认参数值不会进 DDL），Room 便不会去比对这一列的 default，
 * 所以 DDL 里带 DEFAULT、实体里不带，是安全且必要的组合。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE standalone_counters ADD COLUMN resetPeriod TEXT NOT NULL DEFAULT 'none'"
        )
        db.execSQL(
            "ALTER TABLE standalone_counters ADD COLUMN resetIntervalDays INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL("ALTER TABLE standalone_counters ADD COLUMN periodStartDate TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `counter_period_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `counterId` INTEGER NOT NULL,
                `periodStart` TEXT NOT NULL,
                `periodEnd` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `unit` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`counterId`) REFERENCES `standalone_counters`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_counter_period_logs_counterId` " +
                "ON `counter_period_logs` (`counterId`)"
        )
    }
}

@Database(
    entities = [Habit::class, CheckIn::class, StandaloneCounter::class, CounterPeriodLog::class],
    version = 4,
    // 打开 schema 导出，app/schemas/ 下的 JSON 要纳入版本管理。
    // 否则后续既无法使用 AutoMigration，手写迁移时也缺少可比对的历史 schema。
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
    abstract fun standaloneCounterDao(): StandaloneCounterDao
    abstract fun counterPeriodLogDao(): CounterPeriodLogDao

    companion object {
        @Volatile
        private var INSTANCE: HabitDatabase? = null

        fun getInstance(context: Context): HabitDatabase {
            return INSTANCE ?: synchronized(this) {
                // 不使用 fallbackToDestructiveMigration()：它会在任何未编写迁移的 schema
                // 变更时直接删库，导致用户的习惯、打卡历史和照片关联全部丢失。
                // 今后每次改实体，必须在这里 addMigrations(...) 显式编写迁移。
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "habit_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
