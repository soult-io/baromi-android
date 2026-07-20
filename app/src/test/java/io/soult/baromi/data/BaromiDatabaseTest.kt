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

    private fun checkin(
        mood: Int = 3,
        energy: Int = 3,
        anxiety: Int = 3,
        comment: String? = null,
        promptTime: Long? = null,
        answeredTime: Long = 10L,
    ) = Checkin(
        promptTime = promptTime,
        answeredTime = answeredTime,
        mood = mood,
        energy = energy,
        anxiety = anxiety,
        comment = comment,
    )

    @Test
    fun checkin_roundtrips() = runTest {
        val id = checkinDao.insert(
            checkin(mood = 4, energy = 3, anxiety = 5, comment = "ok", promptTime = 1000L, answeredTime = 2000L),
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
    fun boundary_extremes_roundtrip() = runTest {
        // Min and max on each axis, plus a max-length comment, survive a DB round-trip
        // (guards run on the cursor read too, so this proves the extremes are valid).
        val maxComment = "x".repeat(Checkin.COMMENT_MAX_LEN)
        val id = checkinDao.insert(checkin(mood = 5, energy = 1, anxiety = 5, comment = maxComment))
        val read = checkinDao.getById(id)!!
        assertEquals(5, read.mood)
        assertEquals(1, read.energy)
        assertEquals(5, read.anxiety)
        assertEquals(Checkin.COMMENT_MAX_LEN, read.comment!!.length)
    }

    @Test
    fun null_prompt_time_persists() = runTest {
        val id = checkinDao.insert(checkin(promptTime = null))
        assertNull(checkinDao.getById(id)!!.promptTime)
    }

    @Test
    fun tags_attach_and_resolve() = runTest {
        val cid = checkinDao.insert(checkin())
        val work = tagDao.insert(Tag(label = "work"))
        val social = tagDao.insert(Tag(label = "social"))
        checkinDao.attachTag(CheckinTag(cid, work))
        checkinDao.attachTag(CheckinTag(cid, social))

        val withTags = checkinDao.getWithTags(cid)!!
        assertEquals(setOf("work", "social"), withTags.tags.map { it.label }.toSet())
    }

    @Test
    fun attaching_same_tag_twice_is_idempotent() = runTest {
        val cid = checkinDao.insert(checkin())
        val work = tagDao.insert(Tag(label = "work"))
        checkinDao.attachTag(CheckinTag(cid, work))
        checkinDao.attachTag(CheckinTag(cid, work)) // must not throw on the composite-PK conflict
        assertEquals(1, checkinDao.countTagLinks())
    }

    @Test
    fun deleting_checkin_cascades_links_but_keeps_tags() = runTest {
        val cid = checkinDao.insert(checkin())
        val work = tagDao.insert(Tag(label = "work"))
        checkinDao.attachTag(CheckinTag(cid, work))
        assertEquals(1, checkinDao.countTagLinks())

        checkinDao.delete(checkinDao.getById(cid)!!)

        assertNull(checkinDao.getWithTags(cid))
        assertEquals("link row should cascade away", 0, checkinDao.countTagLinks())
        assertEquals("tag itself should remain", 1, tagDao.getAll().size)
    }

    @Test
    fun deleting_in_use_tag_is_restricted() = runTest {
        val cid = checkinDao.insert(checkin())
        val work = tagDao.insert(Tag(label = "work"))
        checkinDao.attachTag(CheckinTag(cid, work))

        var threw = false
        try {
            tagDao.delete(Tag(id = work, label = "work")) // RESTRICT: tag is referenced by history
        } catch (e: SQLiteConstraintException) {
            threw = true
        }
        assertTrue("deleting a referenced tag must be restricted", threw)
        assertEquals("tag and its link must survive the blocked delete", 1, checkinDao.countTagLinks())
        assertEquals(1, tagDao.getAll().size)
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
        checkinDao.insert(checkin(comment = "older", answeredTime = 100L))
        checkinDao.insert(checkin(comment = "newer", answeredTime = 200L))
        val all = checkinDao.getAll()
        assertEquals(listOf("newer", "older"), all.map { it.comment })
    }
}
