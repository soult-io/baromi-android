package io.soult.baromi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.soult.baromi.BuildConfig

/**
 * The on-device store.
 *
 * v2 adds the reminder_time table. exportSchema is off until migrations begin. Destructive
 * fallback is DEBUG-only: while unreleased we drop-and-recreate on version bumps, but a release
 * build must never silently wipe the record — it will fail loudly until a real migration exists.
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
                    .apply {
                        if (BuildConfig.DEBUG) {
                            fallbackToDestructiveMigration(dropAllTables = true)
                        }
                    }
                    .build()
                    .also { instance = it }
            }
    }
}
