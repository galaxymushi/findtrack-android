package com.findtrack.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A device the user explicitly saved (or auto-saved by starting a tracking session). */
@Entity(tableName = "saved_devices")
data class SavedDeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    val addedAt: Long,
    /** User-chosen alias; shown instead of the advertised name when present. */
    val customName: String? = null
)

/** One recorded "last seen" sighting of a device with the phone's GPS fix. */
@Entity(
    tableName = "history",
    indices = [Index(value = ["timestamp"]), Index(value = ["address", "timestamp"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val name: String,
    val rssi: Int,
    val lat: Double?,
    val lon: Double?,
    val place: String,
    val timestamp: Long,
    val isDemo: Boolean = false
)

@Dao
interface DeviceDao {
    @Query("SELECT * FROM saved_devices ORDER BY addedAt DESC")
    fun observeSaved(): Flow<List<SavedDeviceEntity>>

    @Query("SELECT address FROM saved_devices")
    suspend fun savedAddresses(): List<String>

    @Query("SELECT * FROM saved_devices WHERE address = :address")
    suspend fun find(address: String): SavedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: SavedDeviceEntity)

    @Query("UPDATE saved_devices SET customName = :customName WHERE address = :address")
    suspend fun updateCustomName(address: String, customName: String?)

    @Query("DELETE FROM saved_devices WHERE address = :address")
    suspend fun delete(address: String)

    @Query("DELETE FROM saved_devices")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun all(): List<HistoryEntity>

    @Query("SELECT COUNT(*) FROM history")
    fun observeCount(): Flow<Int>

    @Query("SELECT MAX(timestamp) FROM history WHERE address = :address")
    suspend fun lastTimestampFor(address: String): Long?

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Database(
    entities = [SavedDeviceEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "findtrack.db"

        /** Adds the optional custom-name column without dropping saved devices. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_devices ADD COLUMN customName TEXT")
            }
        }
    }
}
