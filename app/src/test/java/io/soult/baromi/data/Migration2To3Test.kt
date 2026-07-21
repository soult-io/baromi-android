package io.soult.baromi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves [MIGRATION_2_3] carries a real v2 database forward: the flexible category model is
 * created and seeded, the old mood/energy/anxiety columns are backfilled into checkin_value, and
 * every existing row (check-ins, comments, tags, tag links, reminders) survives.
 *
 * The v2 database is built here with the exact DDL Room shipped at version 2, then opened through
 * Room with the migration registered. Room runs its own post-migration schema validation on open,
 * so a migration that produces anything other than the exact v3 schema fails this test loudly —
 * the same failure a release build would hit.
 */
@RunWith(RobolectricTestRunner::class)
class Migration2To3Test {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private companion object {
        const val TEST_DB = "migration-2-3-test.db"
    }

    @Before
    fun clean() {
        ctx.deleteDatabase(TEST_DB)
    }

    @After
    fun teardown() {
        ctx.deleteDatabase(TEST_DB)
    }

    /** Creates a version-2 database file populated with representative rows, then closes it. */
    private fun seedV2Database() {
        val db = ctx.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null)
        try {
            // Exact v2 schema (pre-flexible-categories).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`prompt_time` INTEGER, `answered_time` INTEGER NOT NULL, " +
                    "`mood` INTEGER NOT NULL, `energy` INTEGER NOT NULL, `anxiety` INTEGER NOT NULL, " +
                    "`comment` TEXT, `locked` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tag` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_label` ON `tag` (`label`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_tag` (" +
                    "`checkin_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`checkin_id`, `tag_id`), " +
                    "FOREIGN KEY(`checkin_id`) REFERENCES `checkin`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`tag_id`) REFERENCES `tag`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkin_tag_tag_id` ON `checkin_tag` (`tag_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminder_time` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_time_hour_minute` " +
                    "ON `reminder_time` (`hour`, `minute`)",
            )

            // Two check-ins with distinct mood/energy/anxiety, one with a null prompt_time.
            db.execSQL(
                "INSERT INTO `checkin` (`id`, `prompt_time`, `answered_time`, `mood`, `energy`, `anxiety`, `comment`, `locked`) " +
                    "VALUES (1, 1000, 2000, 5, 1, 3, 'good day', 0)",
            )
            db.execSQL(
                "INSERT INTO `checkin` (`id`, `prompt_time`, `answered_time`, `mood`, `energy`, `anxiety`, `comment`, `locked`) " +
                    "VALUES (2, NULL, 3000, 2, 4, 1, NULL, 1)",
            )
            db.execSQL("INSERT INTO `tag` (`id`, `label`) VALUES (1, 'work'), (2, 'social')")
            db.execSQL("INSERT INTO `checkin_tag` (`checkin_id`, `tag_id`) VALUES (1, 1), (1, 2)")
            db.execSQL("INSERT INTO `reminder_time` (`id`, `hour`, `minute`) VALUES (1, 9, 0)")

            db.version = 2
        } finally {
            db.close()
        }
    }

    private fun openMigrated(): BaromiDatabase =
        Room.databaseBuilder(ctx, BaromiDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    @Test
    fun migrate_2_to_3_preserves_values() = runTest {
        seedV2Database()

        // Opening through Room triggers the migration and Room's own v3 schema validation; an
        // incorrect migration throws here before any assertion runs.
        val db = openMigrated()
        try {
            // Built-ins seeded with stable keys, ids and positions.
            val categories = db.categoryDao().getAll()
            assertEquals(3, categories.size)
            assertEquals(listOf("mood", "energy", "anxiety"), categories.map { it.key })
            assertEquals(listOf(0, 1, 2), categories.map { it.position })
            assertEquals(listOf(1L, 2L, 3L), categories.map { it.id })
            assertEquals(listOf(true, true, true), categories.map { it.builtin })

            // Check-in 1: old mood=5 energy=1 anxiety=3 → checkin_value rows on categories 1/2/3.
            val one = db.checkinDao().getWithDetails(1)!!
            assertEquals(2000L, one.checkin.answeredTime)
            assertEquals(1000L, one.checkin.promptTime)
            assertEquals("good day", one.checkin.comment)
            assertEquals(false, one.checkin.locked)
            assertEquals(
                mapOf(1L to 5, 2L to 1, 3L to 3),
                one.values.associate { it.categoryId to it.value },
            )
            // Tag links survive.
            assertEquals(setOf("work", "social"), one.tags.map { it.label }.toSet())

            // Check-in 2: null prompt_time and locked survive; values backfilled.
            val two = db.checkinDao().getWithDetails(2)!!
            assertNull(two.checkin.promptTime)
            assertEquals(true, two.checkin.locked)
            assertEquals(
                mapOf(1L to 2, 2L to 4, 3L to 1),
                two.values.associate { it.categoryId to it.value },
            )

            // Migrated tags are custom + enabled (not silently promoted to built-in).
            val tags = db.tagDao().getAll().sortedBy { it.id }
            assertEquals(listOf("work", "social"), tags.map { it.label })
            assertEquals(listOf(false, false), tags.map { it.builtin })
            assertEquals(listOf(true, true), tags.map { it.enabled })

            // Reminder untouched.
            assertEquals(1, db.reminderTimeDao().getAll().size)

            // The whole record, newest first, reads back through the v3 relations.
            val all = db.checkinDao().getAllWithDetails()
            assertEquals(listOf(2L, 1L), all.map { it.checkin.id })
            assertFalse("every migrated check-in carries its three values", all.any { it.values.size != 3 })
        } finally {
            db.close()
        }
    }
}
