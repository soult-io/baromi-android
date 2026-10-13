package io.soult.baromi.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinTag
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.ReminderTime
import io.soult.baromi.data.Tag
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbs = mutableListOf<BaromiDatabase>()

    private fun newDb(): BaromiDatabase =
        Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java).allowMainThreadQueries().build()
            .also { dbs.add(it) }

    @After
    fun teardown() = dbs.forEach { it.close() }

    // Seeds mood+energy categories and a "poor sleep" tag, returns (moodId, energyId, tagId).
    private suspend fun seed(db: BaromiDatabase, positionBase: Int = 0): Triple<Long, Long, Long> {
        val mood = db.categoryDao().insert(Category(key = "mood", name = "Mood", builtin = true, position = positionBase))
        val energy = db.categoryDao().insert(Category(key = "energy", name = "Energy", builtin = true, position = positionBase + 1))
        val tag = db.tagDao().insert(Tag(label = "poor sleep", builtin = true))
        return Triple(mood, energy, tag)
    }

    private suspend fun addCheckin(db: BaromiDatabase, answeredTime: Long, mood: Long, moodVal: Int, energy: Long, energyVal: Int, tag: Long?) {
        val id = db.checkinDao().insert(Checkin(promptTime = null, answeredTime = answeredTime, comment = "c$answeredTime"))
        db.checkinDao().insertValue(CheckinValue(checkinId = id, categoryId = mood, value = moodVal))
        db.checkinDao().insertValue(CheckinValue(checkinId = id, categoryId = energy, value = energyVal))
        if (tag != null) db.checkinDao().attachTag(CheckinTag(id, tag))
    }

    @Test
    fun export_then_import_into_a_fresh_db_reproduces_checkins_remapped_by_key_and_label() = runTest {
        val source = newDb()
        val (mood, energy, tag) = seed(source)
        addCheckin(source, answeredTime = 1000L, mood = mood, moodVal = 5, energy = energy, energyVal = 3, tag = tag)

        val backup = BackupManager(source).export()

        // Target seeded with the same built-ins FIRST, so its ids differ from the source's.
        val target = newDb()
        val (tMood, tEnergy, _) = seed(target, positionBase = 0)
        val result = BackupManager(target).import(backup)

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
        val details = target.checkinDao().getAllWithDetails().single()
        assertEquals(1000L, details.checkin.answeredTime)
        // Values remapped onto the TARGET category ids by key.
        assertEquals(mapOf(tMood to 5, tEnergy to 3), details.values.associate { it.categoryId to it.value })
        assertEquals(listOf("poor sleep"), details.tags.map { it.label })
    }

    @Test
    fun import_caps_reminder_times_at_the_daily_max() = runTest {
        val target = newDb()
        val data = BackupData(
            scaleVersion = 2,
            exportedAt = 0L,
            categories = emptyList(),
            tags = emptyList(),
            reminderTimes = (0 until 12).map { BackupReminder(hour = it, minute = 0) },
            checkins = emptyList(),
        )
        BackupManager(target).import(data)
        assertEquals(ReminderTime.MAX_PER_DAY, target.reminderTimeDao().getAll().size)
    }

    @Test
    fun import_skips_duplicate_answered_times_and_keeps_existing_data() = runTest {
        val source = newDb()
        val (mood, energy, tag) = seed(source)
        addCheckin(source, answeredTime = 1000L, mood = mood, moodVal = 5, energy = energy, energyVal = 3, tag = tag)
        addCheckin(source, answeredTime = 2000L, mood = mood, moodVal = 1, energy = energy, energyVal = 2, tag = null)
        val backup = BackupManager(source).export()

        // Target already has the 1000L check-in; importing must skip it and add only 2000L.
        val target = newDb()
        val (tMood, tEnergy, tTag) = seed(target)
        addCheckin(target, answeredTime = 1000L, mood = tMood, moodVal = 4, energy = tEnergy, energyVal = 4, tag = tTag)

        val result = BackupManager(target).import(backup)

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        val times = target.checkinDao().getAll().map { it.answeredTime }.sorted()
        assertEquals(listOf(1000L, 2000L), times)
        // The pre-existing 1000L entry is untouched (mood still 4, not the backup's 5).
        val existing = target.checkinDao().getAllWithDetails().first { it.checkin.answeredTime == 1000L }
        assertEquals(4, existing.values.first { it.categoryId == tMood }.value)
    }

    @Test
    fun import_shortens_category_names_over_the_length_limit() = runTest {
        val target = newDb()
        val longName = "n".repeat(Category.MAX_NAME_LENGTH + 25)
        val data = BackupData(
            scaleVersion = 2,
            exportedAt = 0L,
            categories = listOf(BackupCategory(id = 9, key = null, name = longName, builtin = false, enabled = true, position = 5)),
            tags = emptyList(),
            reminderTimes = emptyList(),
            checkins = listOf(BackupCheckin(promptTime = null, answeredTime = 5000L, comment = null, locked = false, values = listOf(BackupValue(9, 2)), tagIds = emptyList())),
        )
        BackupManager(target).import(data)
        val custom = target.categoryDao().getAll().single { !it.builtin }
        assertEquals("n".repeat(Category.MAX_NAME_LENGTH), custom.name)
        // The check-in's value is still attached to the (renamed) category.
        val values = target.checkinDao().getAllWithDetails().single { it.checkin.answeredTime == 5000L }.values
        assertEquals(custom.id, values.single().categoryId)
    }

    @Test
    fun reimport_does_not_duplicate_a_stored_name_longer_than_the_limit() = runTest {
        val target = newDb()
        val longName = "m".repeat(Category.MAX_NAME_LENGTH + 5) // stored by an import from before the limit
        target.categoryDao().insert(Category(key = null, name = longName, builtin = false, position = 5))
        val data = BackupData(
            scaleVersion = 2,
            exportedAt = 0L,
            categories = listOf(BackupCategory(id = 7, key = null, name = longName, builtin = false, enabled = true, position = 5)),
            tags = emptyList(),
            reminderTimes = emptyList(),
            checkins = emptyList(),
        )
        BackupManager(target).import(data)
        assertEquals(1, target.categoryDao().getAll().count { !it.builtin })
    }

    @Test
    fun builtin_with_a_blank_name_in_the_backup_still_matches_by_key() = runTest {
        val target = newDb()
        target.categoryDao().insert(Category(key = "mood", name = "Mood", builtin = true, position = 0))
        val data = BackupData(
            scaleVersion = 2,
            exportedAt = 0L,
            categories = listOf(BackupCategory(id = 1, key = "mood", name = "  ", builtin = true, enabled = true, position = 0)),
            tags = emptyList(),
            reminderTimes = emptyList(),
            checkins = listOf(BackupCheckin(promptTime = null, answeredTime = 7000L, comment = null, locked = false, values = listOf(BackupValue(1, 4)), tagIds = emptyList())),
        )
        BackupManager(target).import(data)
        val mood = target.categoryDao().getAll().single { it.key == "mood" }
        val values = target.checkinDao().getAllWithDetails().single { it.checkin.answeredTime == 7000L }.values
        assertEquals(mood.id, values.single().categoryId)
    }

    @Test
    fun exact_name_wins_over_a_long_name_with_the_same_start() = runTest {
        val target = newDb()
        val exact = "p".repeat(Category.MAX_NAME_LENGTH)
        val exactId = target.categoryDao().insert(Category(key = null, name = exact, builtin = false, position = 5))
        target.categoryDao().insert(Category(key = null, name = exact + "suffix", builtin = false, position = 6))
        val data = BackupData(
            scaleVersion = 2,
            exportedAt = 0L,
            categories = listOf(BackupCategory(id = 3, key = null, name = exact, builtin = false, enabled = true, position = 5)),
            tags = emptyList(),
            reminderTimes = emptyList(),
            checkins = listOf(BackupCheckin(promptTime = null, answeredTime = 8000L, comment = null, locked = false, values = listOf(BackupValue(3, 2)), tagIds = emptyList())),
        )
        BackupManager(target).import(data)
        val values = target.checkinDao().getAllWithDetails().single { it.checkin.answeredTime == 8000L }.values
        assertEquals(exactId, values.single().categoryId)
    }
}
