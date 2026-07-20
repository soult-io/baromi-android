package io.soult.baromi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-device store.
 *
 * v2 adds the reminder_time table. exportSchema is off until migrations begin; while the app is
 * unreleased we drop-and-recreate on version bumps (destructive fallback) rather than write
 * migrations for data that doesn't exist in the wild yet.
 */
@Database(
    entities = [Checkin::class, Tag::class, CheckinTag::class, ReminderTime::class],
    version = 2,
    exportSchema = false,
)
abstract class BaromiDatabase : RoomDatabase() {
    abstract fun checkinDao(): CheckinDao
    abstract fun tagDao(): TagDao
    abstract fun reminderTimeDao(): ReminderTimeDao

    companion object {
        const val NAME = "baromi.db"

        @Volatile
        private var instance: BaromiDatabase? = null

        fun getInstance(context: Context): BaromiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BaromiDatabase::class.java,
                    NAME,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
