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
 * exportSchema is on. Room writes the generated schema for each version to `app/schemas/`
 * (location set by the `room.schemaLocation` KSP arg in build.gradle.kts); the version-3 baseline
 * `app/schemas/io.soult.baromi.data.BaromiDatabase/3.json` is committed. That committed baseline is
 * the record of the current on-disk schema — the next migration is written against it. The
 * Robolectric migration test still exercises Room's own runtime schema check against a real v2
 * database.
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
    exportSchema = true,
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
