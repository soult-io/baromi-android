package io.soult.baromi.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Room runs these with foreign-key enforcement off and inside a transaction,
 * so a table can be dropped and recreated without cascading its children away — as long as row ids
 * are preserved so the child foreign keys still resolve afterward.
 *
 * Every `CREATE`/`INDEX` statement below is frozen literal DDL that must reproduce Room's generated
 * v3 schema exactly: a fresh v3 install and a migrated v2 database must end up byte-identical, or
 * Room's post-migration schema validation fails loudly at open. The exact target shape is the
 * committed baseline `app/schemas/io.soult.baromi.data.BaromiDatabase/3.json` (exportSchema is on) —
 * consult it when changing this DDL. `Migration2To3Test` guards the migration by migrating a real
 * v2 database and letting that runtime validation run. Keep this SQL as literals — wiring it to the
 * entities would let a future entity refactor silently rewrite history.
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

/**
 * Data-only migration (no schema change): reconcile the seeded default tags to the pared-down set.
 *
 * v2's defaults were `work, sleep-poor, social, exercise, sick, alone`; the design pared them to the
 * three built-ins `poor sleep, exercise, social`. [MIGRATION_2_3] preserved every old tag as a
 * *custom* row, so a database that predates the pared set still shows the old six. This retires the
 * dropped defaults (and their check-in links — FK enforcement is off during a migration), then marks
 * the current three as built-in (creating any that are missing). Room still requires a version bump
 * to run DML like this, so the schema is identical to v3 — `4.json`'s shape matches `3.json`.
 *
 * Retirement is by label, which is a deliberate, bounded trade-off: it cannot run only in the v2->v3
 * step (already-migrated v3 databases must still be fixed here), and a tag row carries no provenance
 * distinguishing a former default from a user-created tag of the same name. So on the intended
 * v2->v3->v4 path — where these four labels are always the old defaults — nothing user-made is lost;
 * the only exception is a database first created at v3 in which the user happened to add a custom tag
 * named exactly `work`/`sleep-poor`/`sick`/`alone`, which would be removed (the check-ins survive,
 * only the tag link is dropped). Accepted as vanishingly unlikely for this app.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Retire the old defaults the pared set dropped (sleep-poor is superseded by 'poor sleep').
        val retired = "('work', 'sleep-poor', 'sick', 'alone')"
        db.execSQL("DELETE FROM `checkin_tag` WHERE `tag_id` IN (SELECT `id` FROM `tag` WHERE `label` IN $retired)")
        db.execSQL("DELETE FROM `tag` WHERE `label` IN $retired")

        // Establish the current built-in defaults: create any that are missing (the unique-label
        // index makes OR IGNORE a no-op for ones the user already has), then mark the set built-in.
        db.execSQL(
            "INSERT OR IGNORE INTO `tag` (`label`, `builtin`, `enabled`) VALUES " +
                "('poor sleep', 1, 1), ('exercise', 1, 1), ('social', 1, 1)",
        )
        db.execSQL("UPDATE `tag` SET `builtin` = 1 WHERE `label` IN ('poor sleep', 'exercise', 'social')")
    }
}
