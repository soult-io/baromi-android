package io.soult.baromi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Proves [MIGRATION_3_4] reconciles the seeded default tags on an existing v3 database: the retired
 * defaults (and their check-in links) are removed, the current three built-ins are established, and
 * tags the user added themselves survive untouched. Room's own post-migration schema validation runs
 * on open (v4's schema equals v3's), so a botched migration fails here loudly.
 */
@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private companion object {
        const val TEST_DB = "migration-3-4-test.db"
    }

    @Before
    fun clean() {
        ctx.deleteDatabase(TEST_DB)
    }

    @After
    fun teardown() {
        ctx.deleteDatabase(TEST_DB)
    }

    /** Creates a v3 database whose tag table holds the old six defaults (as custom rows, the state
     *  MIGRATION_2_3 leaves) plus one genuinely user-added tag, with a check-in linking to a retired
     *  default and to the custom tag. */
    private fun seedV3Database() {
        val db = ctx.openOrCreateDatabase(TEST_DB, Context.MODE_PRIVATE, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`prompt_time` INTEGER, `answered_time` INTEGER NOT NULL, `comment` TEXT, `locked` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tag` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`label` TEXT NOT NULL, `builtin` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)",
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
                "CREATE TABLE IF NOT EXISTS `category` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`key` TEXT, `name` TEXT NOT NULL, `builtin` INTEGER NOT NULL, " +
                    "`enabled` INTEGER NOT NULL, `position` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `checkin_value` (" +
                    "`checkin_id` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, `value` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`checkin_id`, `category_id`), " +
                    "FOREIGN KEY(`checkin_id`) REFERENCES `checkin`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_checkin_value_category_id` ON `checkin_value` (`category_id`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `reminder_time` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_time_hour_minute` ON `reminder_time` (`hour`, `minute`)",
            )

            db.execSQL(
                "INSERT INTO `category` (`id`, `key`, `name`, `builtin`, `enabled`, `position`) VALUES " +
                    "(1, 'mood', 'Mood', 1, 1, 0), (2, 'energy', 'Energy', 1, 1, 1), (3, 'anxiety', 'Anxiety', 1, 1, 2)",
            )
            // Old six defaults, as custom rows, plus one genuinely user-added tag.
            db.execSQL(
                "INSERT INTO `tag` (`id`, `label`, `builtin`, `enabled`) VALUES " +
                    "(1, 'work', 0, 1), (2, 'sleep-poor', 0, 1), (3, 'social', 0, 1), " +
                    "(4, 'exercise', 0, 1), (5, 'sick', 0, 1), (6, 'alone', 0, 1), (7, 'my-tag', 0, 1)",
            )
            db.execSQL("INSERT INTO `checkin` (`id`, `prompt_time`, `answered_time`, `comment`, `locked`) VALUES (1, NULL, 2000, 'note', 0)")
            db.execSQL("INSERT INTO `checkin_value` (`checkin_id`, `category_id`, `value`) VALUES (1, 1, 4)")
            // Link the check-in to a retired default ('work'=1) and to the custom tag ('my-tag'=7).
            db.execSQL("INSERT INTO `checkin_tag` (`checkin_id`, `tag_id`) VALUES (1, 1), (1, 7)")

            db.version = 3
        } finally {
            db.close()
        }
    }

    private fun openMigrated(): BaromiDatabase =
        Room.databaseBuilder(ctx, BaromiDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

    @Test
    fun migrate_3_to_4_reconciles_default_tags() = runTest {
        seedV3Database()

        val db = openMigrated()
        try {
            val tags = db.tagDao().getAll()
            val byLabel = tags.associateBy { it.label }

            // Retired defaults are gone.
            listOf("work", "sleep-poor", "sick", "alone").forEach {
                assertFalse("retired default '$it' must be removed", byLabel.containsKey(it))
            }

            // The three built-in defaults exist and are marked built-in.
            assertEquals(
                setOf("poor sleep", "exercise", "social"),
                tags.filter { it.builtin }.map { it.label }.toSet(),
            )
            // 'social' and 'exercise' were promoted from the old custom rows (ids preserved).
            assertEquals(3L, byLabel.getValue("social").id)
            assertEquals(4L, byLabel.getValue("exercise").id)

            // The user's own tag survives, untouched, as a custom tag.
            val myTag = byLabel.getValue("my-tag")
            assertFalse("user tag stays custom", myTag.builtin)
            assertTrue(myTag.enabled)

            // The check-in survives; its link to the retired 'work' is gone, its link to 'my-tag' stays.
            val details = db.checkinDao().getWithDetails(1)!!
            assertEquals(setOf("my-tag"), details.tags.map { it.label }.toSet())
            // Category value is untouched by this data-only migration.
            assertEquals(mapOf(1L to 4), details.values.associate { it.categoryId to it.value })
        } finally {
            db.close()
        }
    }
}
