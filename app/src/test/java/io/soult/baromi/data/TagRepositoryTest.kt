package io.soult.baromi.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
}
