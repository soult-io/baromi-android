package io.soult.baromi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.soult.baromi.BuildConfig

/**
 * The on-device store.
 *
 * v3 replaces the fixed mood/energy/anxiety columns with a flexible category model: a `category`
 * table plus one `checkin_value` row per enabled category. [MIGRATION_2_3] carries an existing v2
 * database forward (backfilling values from the old columns). The DEBUG-only destructive fallback
 * stays as a backstop — a release build has a real migration and never silently wipes the record.
 *
 * exportSchema is still off: enabling it is only worthwhile once the generated JSON baseline is
 * committed, which needs a build environment that surfaces `app/schemas/` (the container build host
 * does not). Until then the migration is validated by Room's own runtime schema check, which the
 * Robolectric migration test exercises against a real v2 database.
 */
@Database(
    entities = [
        Checkin::class,
        Tag::class,
        CheckinTag::class,
        ReminderTime::class,
        Category::class,
        CheckinValue::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class BaromiDatabase : RoomDatabase() {
    abstract fun checkinDao(): CheckinDao
    abstract fun tagDao(): TagDao
    abstract fun reminderTimeDao(): ReminderTimeDao
    abstract fun categoryDao(): CategoryDao

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
                    .addMigrations(MIGRATION_2_3)
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
