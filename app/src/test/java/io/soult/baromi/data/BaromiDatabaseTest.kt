package io.soult.baromi.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Room behaviour verified against an in-memory database under Robolectric. */
@RunWith(RobolectricTestRunner::class)
class BaromiDatabaseTest {

    private lateinit var db: BaromiDatabase
    private lateinit var checkinDao: CheckinDao
    private lateinit var tagDao: TagDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        checkinDao = db.checkinDao()
        tagDao = db.tagDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun checkin_roundtrips() = runTest {
        val id = checkinDao.insert(
            Checkin(
                promptTime = 1000L,
                answeredTime = 2000L,
                mood = 4,
                energy = 3,
                anxiety = 5,
                comment = "ok",
            ),
        )
        val read = checkinDao.getById(id)!!
        assertEquals(1000L, read.promptTime)
        assertEquals(2000L, read.answeredTime)
        assertEquals(4, read.mood)
        assertEquals(3, read.energy)
        assertEquals(5, read.anxiety)
        assertEquals("ok", read.comment)
        assertEquals(false, read.locked)
    }

    @Test
    fun null_prompt_time_persists() = runTest {
        val id = checkinDao.insert(
            Checkin(promptTime = null, answeredTime = 10L, mood = 3, energy = 3, anxiety = 3, comment = null),
        )
        assertNull(checkinDao.getById(id)!!.promptTime)
    }

    @Test
    fun tags_attach_and_resolve() = runTest {
        val cid = checkinDao.insert(
            Checkin(promptTime = null, answeredTime = 10L, mood = 3, energy = 3, anxiety = 3, comment = null),
        )
        val work = tagDao.insert(Tag(label = "work"))
        val social = tagDao.insert(Tag(label = "social"))
        checkinDao.attachTag(CheckinTag(cid, work))
        checkinDao.attachTag(CheckinTag(cid, social))

        val withTags = checkinDao.getWithTags(cid)!!
        assertEquals(setOf("work", "social"), withTags.tags.map { it.label }.toSet())
    }

    @Test
    fun deleting_checkin_cascades_links_but_keeps_tags() = runTest {
        val cid = checkinDao.insert(
            Checkin(promptTime = null, answeredTime = 10L, mood = 3, energy = 3, anxiety = 3, comment = null),
        )
        val work = tagDao.insert(Tag(label = "work"))
        checkinDao.attachTag(CheckinTag(cid, work))
        assertEquals(1, checkinDao.linkCount())

        checkinDao.delete(checkinDao.getById(cid)!!)

        assertNull(checkinDao.getWithTags(cid))
        assertEquals("link row should cascade away", 0, checkinDao.linkCount())
        assertEquals("tag itself should remain", 1, tagDao.getAll().size)
    }

    @Test
    fun duplicate_tag_label_rejected() = runTest {
        tagDao.insert(Tag(label = "work"))
        var threw = false
        try {
            tagDao.insert(Tag(label = "work"))
        } catch (e: SQLiteConstraintException) {
            threw = true
        }
        assertTrue("duplicate label should violate the unique index", threw)
    }

    @Test
    fun get_all_ordered_by_answered_time_desc() = runTest {
        checkinDao.insert(
            Checkin(promptTime = null, answeredTime = 100L, mood = 3, energy = 3, anxiety = 3, comment = "older"),
        )
        checkinDao.insert(
            Checkin(promptTime = null, answeredTime = 200L, mood = 3, energy = 3, anxiety = 3, comment = "newer"),
        )
        val all = checkinDao.getAll()
        assertEquals(listOf("newer", "older"), all.map { it.comment })
    }
}
