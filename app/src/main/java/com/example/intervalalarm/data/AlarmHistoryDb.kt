package com.example.intervalalarm.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alarm_history")
data class AlarmHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AlarmHistoryDao {

    @Query("SELECT * FROM alarm_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AlarmHistoryEntry>>

    @Insert
    suspend fun insert(entry: AlarmHistoryEntry)

    @Query("DELETE FROM alarm_history")
    suspend fun clearAll()
}

@Database(entities = [AlarmHistoryEntry::class], version = 1, exportSchema = false)
abstract class AlarmHistoryDatabase : RoomDatabase() {
    abstract fun dao(): AlarmHistoryDao

    companion object {
        @Volatile private var INSTANCE: AlarmHistoryDatabase? = null

        fun get(ctx: Context): AlarmHistoryDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    ctx.applicationContext,
                    AlarmHistoryDatabase::class.java,
                    "alarm_history.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
    }
}
