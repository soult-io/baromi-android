package io.soult.baromi.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The on-device store. Version 1.
 *
 * exportSchema is off until migrations begin; it will be enabled (with the generated schema
 * JSON committed) once there is a second version to migrate to and migration tests to run.
 */
@Database(
    entities = [Checkin::class, Tag::class, CheckinTag::class],
    version = 1,
    exportSchema = false,
)
abstract class BaromiDatabase : RoomDatabase() {
    abstract fun checkinDao(): CheckinDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "baromi.db"
    }
}
