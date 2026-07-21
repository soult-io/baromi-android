package io.soult.baromi.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Category
import io.soult.baromi.data.Tag
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckinRecorderTest {

    private lateinit var db: BaromiDatabase
    private lateinit var recorder: CheckinRecorder

    private var moodId = 0L
    private var energyId = 0L

    @Before
    fun setup() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recorder = CheckinRecorder(db)
        moodId = db.categoryDao().insert(Category(key = "mood", name = "Mood", builtin = true, position = 0))
        energyId = db.categoryDao().insert(Category(key = "energy", name = "Energy", builtin = true, position = 1))
    }

    @After
    fun teardown() = db.close()

    @Test
    fun records_checkin_with_prompt_time_values_and_tags() = runTest {
        val work = db.tagDao().insert(Tag(label = "work"))
        val id = recorder.record(
            values = mapOf(moodId to 4, energyId to 2),
            comment = "  hi  ", tagIds = listOf(work),
            promptTime = 1000L, answeredTime = 2000L,
        )
        val details = db.checkinDao().getWithDetails(id)!!
        assertEquals(1000L, details.checkin.promptTime)
        assertEquals(2000L, details.checkin.answeredTime)
        assertEquals("hi", details.checkin.comment) // trimmed
        assertEquals(mapOf(moodId to 4, energyId to 2), details.values.associate { it.categoryId to it.value })
        assertEquals(listOf("work"), details.tags.map { it.label })
    }

    @Test
    fun blank_comment_is_stored_as_null() = runTest {
        val id = recorder.record(mapOf(moodId to 3), "   ", emptyList(), null, 10L)
        assertNull(db.checkinDao().getById(id)!!.comment)
    }

    @Test
    fun manual_open_records_null_prompt_time() = runTest {
        val id = recorder.record(mapOf(moodId to 3), null, emptyList(), null, 10L)
        assertNull(db.checkinDao().getById(id)!!.promptTime)
    }

    @Test
    fun invalid_rating_throws_and_persists_nothing() = runTest {
        var threw = false
        try {
            recorder.record(mapOf(moodId to 6), null, emptyList(), null, 10L)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("out-of-range value must be rejected", threw)
        assertEquals("transaction must roll back", 0, db.checkinDao().getAll().size)
    }
}
