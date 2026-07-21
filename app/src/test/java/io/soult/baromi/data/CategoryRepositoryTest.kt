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
class CategoryRepositoryTest {

    private lateinit var db: BaromiDatabase
    private lateinit var repo: CategoryRepository

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CategoryRepository(db.categoryDao())
    }

    @After
    fun teardown() = db.close()

    @Test
    fun seeds_builtins_when_empty() = runTest {
        repo.seedDefaultsIfEmpty()
        assertEquals(Category.BUILTINS.size, repo.all().size)
        assertTrue("seeded categories are built-in", repo.all().all { it.builtin })
        assertEquals(listOf("Mood", "Energy", "Anxiety"), repo.all().map { it.name })
    }

    @Test
    fun seeding_is_idempotent() = runTest {
        repo.seedDefaultsIfEmpty()
        repo.seedDefaultsIfEmpty()
        assertEquals(Category.BUILTINS.size, repo.all().size)
    }

    @Test
    fun add_custom_appends_after_builtins() = runTest {
        repo.seedDefaultsIfEmpty()
        assertEquals(CategoryRepository.AddResult.ADDED, repo.add("  Focus  "))
        val focus = repo.all().last()
        assertEquals("Focus", focus.name)
        assertFalse(focus.builtin)
        assertEquals(Category.BUILTINS.size, focus.position)
    }

    @Test
    fun add_rejects_blank_and_comma() = runTest {
        assertEquals(CategoryRepository.AddResult.INVALID, repo.add("   "))
        assertEquals(CategoryRepository.AddResult.INVALID, repo.add("a,b"))
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun add_rejects_duplicate_name() = runTest {
        repo.add("Focus")
        assertEquals(CategoryRepository.AddResult.DUPLICATE, repo.add("Focus"))
        assertEquals(1, repo.all().size)
    }

    @Test
    fun add_caps_custom_at_max() = runTest {
        repeat(Category.MAX_CUSTOM) { i -> assertEquals(CategoryRepository.AddResult.ADDED, repo.add("c$i")) }
        assertEquals(CategoryRepository.AddResult.MAX_REACHED, repo.add("one-too-many"))
        assertEquals(Category.MAX_CUSTOM, repo.all().size)
    }

    @Test
    fun enabled_filters_disabled_categories() = runTest {
        repo.seedDefaultsIfEmpty()
        val mood = repo.all().first()
        repo.setEnabled(mood, false)
        assertEquals(Category.BUILTINS.size - 1, repo.enabled().size)
        assertFalse(repo.enabled().any { it.name == "Mood" })
    }

    @Test
    fun remove_custom_succeeds() = runTest {
        repo.add("Focus")
        assertTrue(repo.remove(repo.all().single()))
        assertTrue(repo.all().isEmpty())
    }

    @Test
    fun remove_builtin_is_blocked() = runTest {
        repo.seedDefaultsIfEmpty()
        assertFalse("built-ins are disable-only", repo.remove(repo.all().first()))
        assertEquals(Category.BUILTINS.size, repo.all().size)
    }

    @Test
    fun removing_custom_frees_a_slot() = runTest {
        repeat(Category.MAX_CUSTOM) { i -> repo.add("c$i") }
        repo.remove(repo.all().first { !it.builtin })
        assertEquals(CategoryRepository.AddResult.ADDED, repo.add("replacement"))
    }
}
