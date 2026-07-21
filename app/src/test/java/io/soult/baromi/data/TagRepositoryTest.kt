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
        assertTrue("defaults are built-in", repo.all().all { it.builtin })
    }

    @Test
    fun seeding_is_idempotent() = runTest {
        repo.seedDefaultsIfEmpty()
        repo.seedDefaultsIfEmpty()
        assertEquals(Tag.DEFAULTS.size, repo.all().size)
    }

    @Test
    fun add_trims_and_inserts_custom() = runTest {
        assertEquals(TagRepository.AddResult.ADDED, repo.add("  focus  "))
        val tag = repo.all().single()
        assertEquals("focus", tag.label)
        assertFalse("added tags are custom", tag.builtin)
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
    fun enabled_filters_disabled_tags() = runTest {
        repo.add("work")
        val tag = repo.all().single()
        repo.setEnabled(tag, false)
        assertTrue(repo.enabled().isEmpty())
        repo.setEnabled(repo.all().single(), true)
        assertEquals(1, repo.enabled().size)
    }

    @Test
    fun remove_custom_tag_succeeds() = runTest {
        repo.add("work")
        assertTrue(repo.remove(repo.all().single()))
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun remove_builtin_tag_is_blocked() = runTest {
        repo.seedDefaultsIfEmpty()
        val builtin = repo.all().first()
        assertFalse("built-in tags are disable-only", repo.remove(builtin))
        assertEquals(Tag.DEFAULTS.size, repo.all().size)
    }

    @Test
    fun remove_in_use_tag_is_blocked() = runTest {
        repo.add("work")
        val tag = repo.all().single()
        val checkinId = db.checkinDao().insert(Checkin(promptTime = null, answeredTime = 1L, comment = null))
        db.checkinDao().attachTag(CheckinTag(checkinId, tag.id))

        assertFalse("a referenced tag must not be removable", repo.remove(tag))
        assertEquals(1, repo.all().size)
    }
}
