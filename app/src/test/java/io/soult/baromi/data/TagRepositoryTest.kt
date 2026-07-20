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
class TagRepositoryTest {

    private lateinit var db: BaromiDatabase
    private lateinit var repo: TagRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TagRepository(db.tagDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun seeds_default_tags_when_empty() = runTest {
        repo.seedDefaultsIfEmpty()
        assertEquals(Tag.DEFAULTS.size, repo.all().size)
    }

    @Test
    fun seeding_is_idempotent() = runTest {
        repo.seedDefaultsIfEmpty()
        repo.seedDefaultsIfEmpty()
        assertEquals(Tag.DEFAULTS.size, repo.all().size)
    }

    @Test
    fun add_trims_and_inserts() = runTest {
        assertEquals(TagRepository.AddResult.ADDED, repo.add("  focus  "))
        assertEquals(listOf("focus"), repo.all().map { it.label })
    }

    @Test
    fun add_rejects_duplicate() = runTest {
        repo.add("work")
        assertEquals(TagRepository.AddResult.DUPLICATE, repo.add("work"))
        assertEquals(1, repo.all().size)
    }

    @Test
    fun add_rejects_blank_and_semicolon() = runTest {
        assertEquals(TagRepository.AddResult.INVALID, repo.add("   "))
        assertEquals(TagRepository.AddResult.INVALID, repo.add("a;b"))
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun remove_unused_tag_succeeds() = runTest {
        repo.add("work")
        val tag = repo.all().first()
        assertTrue(repo.remove(tag))
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun remove_in_use_tag_is_blocked() = runTest {
        repo.add("work")
        val tag = repo.all().first()
        val checkinId = db.checkinDao().insert(
            Checkin(promptTime = null, answeredTime = 1L, mood = 3, energy = 3, anxiety = 3, comment = null),
        )
        db.checkinDao().attachTag(CheckinTag(checkinId, tag.id))

        assertFalse("a referenced tag must not be removable", repo.remove(tag))
        assertEquals(1, repo.all().size)
    }
}
