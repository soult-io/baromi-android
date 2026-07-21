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
    private lateinit var categoryDao: CategoryDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        checkinDao = db.checkinDao()
        tagDao = db.tagDao()
        categoryDao = db.categoryDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun checkin(
        comment: String? = null,
        promptTime: Long? = null,
        answeredTime: Long = 10L,
    ) = Checkin(
        promptTime = promptTime,
        answeredTime = answeredTime,
        comment = comment,
    )

    @Test
    fun checkin_roundtrips() = runTest {
        val id = checkinDao.insert(checkin(comment = "ok", promptTime = 1000L, answeredTime = 2000L))
        val read = checkinDao.getById(id)!!
        assertEquals(1000L, read.promptTime)
        assertEquals(2000L, read.answeredTime)
        assertEquals("ok", read.comment)
        assertEquals(false, read.locked)
    }

    @Test
    fun values_attach_and_resolve() = runTest {
        val mood = categoryDao.insert(Category(key = "mood", name = "Mood", builtin = true, position = 0))
        val energy = categoryDao.insert(Category(key = "energy", name = "Energy", builtin = true, position = 1))
        val cid = checkinDao.insert(checkin())
        checkinDao.insertValue(CheckinValue(cid, mood, 4))
        checkinDao.insertValue(CheckinValue(cid, energy, 2))

        val details = checkinDao.getWithDetails(cid)!!
        assertEquals(mapOf(mood to 4, energy to 2), details.values.associate { it.categoryId to it.value })
    }

    @Test
    fun boundary_extremes_roundtrip() = runTest {
        val cat = categoryDao.insert(Category(key = "mood", name = "Mood", builtin = true, position = 0))
        val maxComment = "x".repeat(Checkin.COMMENT_MAX_LEN)
        val cid = checkinDao.insert(checkin(comment = maxComment))
        checkinDao.insertValue(CheckinValue(cid, cat, 1))
        checkinDao.insertValue(CheckinValue(checkinDao.insert(checkin(answeredTime = 11L)), cat, 5))

        val read = checkinDao.getById(cid)!!
        assertEquals(Checkin.COMMENT_MAX_LEN, read.comment!!.length)
        assertEquals(1, checkinDao.getWithDetails(cid)!!.values.single().value)
    }

    @Test
    fun null_prompt_time_persists() = runTest {
        val id = checkinDao.insert(checkin(promptTime = null))
        assertNull(checkinDao.getById(id)!!.promptTime)
    }

    @Test
    fun deleting_checkin_cascades_values() = runTest {
        val cat = categoryDao.insert(Category(key = "mood", name = "Mood", builtin = true, position = 0))
        val cid = checkinDao.insert(checkin())
        checkinDao.insertValue(CheckinValue(cid, cat, 3))

        checkinDao.delete(checkinDao.getById(cid)!!)

        assertNull(checkinDao.getWithDetails(cid))
        assertEquals("category itself should remain", 1, categoryDao.getAll().size)
    }

    @Test
    fun deleting_category_cascades_values() = runTest {
        val cat = Category(id = 0, key = null, name = "Custom", builtin = false, position = 3)
        val catId = categoryDao.insert(cat)
        val cid = checkinDao.insert(checkin())
        checkinDao.insertValue(CheckinValue(cid, catId, 3))

        categoryDao.delete(cat.copy(id = catId))

        assertTrue("value should cascade away", checkinDao.getWithDetails(cid)!!.values.isEmpty())
    }

    @Test
    fun tags_attach_and_resolve() = runTest {
        val cid = checkinDao.insert(checkin())
        val work = tagDao.insert(Tag(label = "work"))
        val social = tagDao.insert(Tag(label = "social"))
        checkinDao.attachTag(CheckinTag(cid, work))
        checkinDao.attachTag(CheckinTag(cid, social))

        val details = checkinDao.getWithDetails(cid)!!
        assertEquals(setOf("work", "social"), details.tags.map { it.label }.toSet())
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
    fun duplicate_tag_label_is_ignored() = runTest {
        tagDao.insert(Tag(label = "work"))
        val second = tagDao.insert(Tag(label = "work"))
        assertEquals("duplicate insert is a no-op returning -1", -1L, second)
        assertEquals("unique label index keeps a single row", 1, tagDao.getAll().size)
    }

    @Test
    fun duplicate_category_name_is_ignored() = runTest {
        categoryDao.insert(Category(key = null, name = "Focus", builtin = false, position = 3))
        val second = categoryDao.insert(Category(key = null, name = "Focus", builtin = false, position = 4))
        assertEquals("duplicate insert is a no-op returning -1", -1L, second)
        assertEquals(1, categoryDao.getAll().size)
    }

    @Test
    fun get_all_ordered_by_answered_time_desc() = runTest {
        checkinDao.insert(checkin(comment = "older", answeredTime = 100L))
        checkinDao.insert(checkin(comment = "newer", answeredTime = 200L))
        val all = checkinDao.getAll()
        assertEquals(listOf("newer", "older"), all.map { it.comment })
    }
}
