package io.soult.baromi.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Room runs these with foreign-key enforcement off and inside a transaction,
 * so a table can be dropped and recreated without cascading its children away — as long as row ids
 * are preserved so the child foreign keys still resolve afterward.
 *
 * Every `CREATE`/`INDEX` statement below is copied verbatim from Room's generated v3 schema
 * (`app/schemas/.../3.json`); a fresh v3 install and a migrated v2 database must end up
 * byte-identical or Room's post-migration schema validation fails loudly at open.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. The new flexible-category table + its unique-name index.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`key` TEXT, `name` TEXT NOT NULL, `builtin` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, `position` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")

        // 2. Seed the three built-ins with fixed ids so the backfill below can map the old
        //    mood/energy/anxiety columns to them deterministically. A fresh install seeds the
        //    same rows (ids 1..3) via CategoryRepository, so both paths agree.
        db.execSQL(
            "INSERT INTO `category` (`id`, `key`, `name`, `builtin`, `enabled`, `position`) VALUES " +
                "(1, 'mood', 'Mood', 1, 1, 0), " +
                "(2, 'energy', 'Energy', 1, 1, 1), " +
                "(3, 'anxiety', 'Anxiety', 1, 1, 2)",
        )

        // 3. The per-category value table + its category_id index.
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

        // 4. Backfill: one checkin_value row per existing check-in per built-in category, reading
        //    the old fixed columns while they still exist.
        db.execSQL("INSERT INTO `checkin_value` (`checkin_id`, `category_id`, `value`) SELECT `id`, 1, `mood` FROM `checkin`")
        db.execSQL("INSERT INTO `checkin_value` (`checkin_id`, `category_id`, `value`) SELECT `id`, 2, `energy` FROM `checkin`")
        db.execSQL("INSERT INTO `checkin_value` (`checkin_id`, `category_id`, `value`) SELECT `id`, 3, `anxiety` FROM `checkin`")

        // 5. Drop mood/energy/anxiety from checkin. SQLite before 3.35 (minSdk 26) has no
        //    DROP COLUMN, so recreate the table with the v3 shape and copy the surviving columns.
        //    Ids are preserved, so checkin_value / checkin_tag foreign keys still resolve.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_checkin` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`prompt_time` INTEGER, `answered_time` INTEGER NOT NULL, `comment` TEXT, `locked` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `_new_checkin` (`id`, `prompt_time`, `answered_time`, `comment`, `locked`) " +
                "SELECT `id`, `prompt_time`, `answered_time`, `comment`, `locked` FROM `checkin`",
        )
        db.execSQL("DROP TABLE `checkin`")
        db.execSQL("ALTER TABLE `_new_checkin` RENAME TO `checkin`")

        // 6. v3 also added builtin/enabled to tag. Adding NOT NULL columns to a populated table
        //    needs a DEFAULT, but a DB-level default the entity doesn't declare would fail Room's
        //    schema validation — so recreate tag instead. Existing tags become custom (builtin=0)
        //    and enabled, preserving the user's tag list and its history links.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_tag` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`label` TEXT NOT NULL, `builtin` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `_new_tag` (`id`, `label`, `builtin`, `enabled`) SELECT `id`, `label`, 0, 1 FROM `tag`",
        )
        db.execSQL("DROP TABLE `tag`")
        db.execSQL("ALTER TABLE `_new_tag` RENAME TO `tag`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_label` ON `tag` (`label`)")
    }
}
