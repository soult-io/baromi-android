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

@RunWith(RobolectricTestRunner::class)
class ReminderRepositoryTest {

    private lateinit var db: BaromiDatabase
    private lateinit var repo: ReminderRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ReminderRepository(db.reminderTimeDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun seeds_three_defaults_when_empty() = runTest {
        repo.seedDefaultsIfEmpty()
        assertEquals(3, repo.all().size)
    }

    @Test
    fun seeding_is_idempotent() = runTest {
        repo.seedDefaultsIfEmpty()
        repo.seedDefaultsIfEmpty()
        assertEquals(3, repo.all().size)
    }

    @Test
    fun add_is_capped_at_max_per_day() = runTest {
        repeat(ReminderTime.MAX_PER_DAY) { i ->
            assertTrue(repo.add(hour = 8, minute = i))
        }
        assertFalse("9th add must be rejected", repo.add(hour = 23, minute = 0))
        assertEquals(ReminderTime.MAX_PER_DAY, repo.all().size)
    }

    @Test
    fun duplicate_time_is_not_added_twice() = runTest {
        assertTrue(repo.add(hour = 9, minute = 0))
        assertFalse("same time must not duplicate", repo.add(hour = 9, minute = 0))
        assertEquals(1, repo.all().size)
    }

    @Test
    fun remove_will_not_drop_below_min_per_day() = runTest {
        repo.seedDefaultsIfEmpty() // 3
        val times = repo.all()
        assertTrue(repo.remove(times[0])) // 3 -> 2
        assertTrue(repo.remove(times[1])) // 2 -> 1
        assertFalse("removing the last must be rejected", repo.remove(times[2])) // stays 1
        assertEquals(ReminderTime.MIN_PER_DAY, repo.all().size)
    }
}
