package io.soult.baromi.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.data.BaromiDatabase
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

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recorder = CheckinRecorder(db)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun records_checkin_with_prompt_time_and_tags() = runTest {
        val work = db.tagDao().insert(Tag(label = "work"))
        val id = recorder.record(
            mood = 4, energy = 2, anxiety = 5,
            comment = "  hi  ", tagIds = listOf(work),
            promptTime = 1000L, answeredTime = 2000L,
        )
        val withTags = db.checkinDao().getWithTags(id)!!
        assertEquals(4, withTags.checkin.mood)
        assertEquals(2, withTags.checkin.energy)
        assertEquals(5, withTags.checkin.anxiety)
        assertEquals(1000L, withTags.checkin.promptTime)
        assertEquals(2000L, withTags.checkin.answeredTime)
        assertEquals("hi", withTags.checkin.comment) // trimmed
        assertEquals(listOf("work"), withTags.tags.map { it.label })
    }

    @Test
    fun blank_comment_is_stored_as_null() = runTest {
        val id = recorder.record(3, 3, 3, "   ", emptyList(), null, 10L)
        assertNull(db.checkinDao().getById(id)!!.comment)
    }

    @Test
    fun manual_open_records_null_prompt_time() = runTest {
        val id = recorder.record(3, 3, 3, null, emptyList(), null, 10L)
        assertNull(db.checkinDao().getById(id)!!.promptTime)
    }

    @Test
    fun invalid_rating_throws_and_persists_nothing() = runTest {
        var threw = false
        try {
            recorder.record(6, 3, 3, null, emptyList(), null, 10L)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("out-of-range mood must be rejected", threw)
        assertEquals("transaction must roll back", 0, db.checkinDao().getAll().size)
    }
}
