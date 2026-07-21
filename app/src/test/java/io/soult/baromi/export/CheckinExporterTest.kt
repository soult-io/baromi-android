package io.soult.baromi.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinValue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckinExporterTest {

    private lateinit var db: BaromiDatabase
    private lateinit var exporter: CheckinExporter

    private var moodId = 0L
    private var energyId = 0L
    private var anxietyId = 0L

    @Before
    fun setup() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = CheckinExporter(db)
        moodId = db.categoryDao().insert(Category(key = "mood", name = "Mood", builtin = true, position = 0))
        energyId = db.categoryDao().insert(Category(key = "energy", name = "Energy", builtin = true, position = 1))
        anxietyId = db.categoryDao().insert(Category(key = "anxiety", name = "Anxiety", builtin = true, position = 2))
    }

    @After
    fun teardown() = db.close()

    private suspend fun record(answeredTime: Long, comment: String, mood: Int, energy: Int, anxiety: Int) {
        val id = db.checkinDao().insert(Checkin(promptTime = null, answeredTime = answeredTime, comment = comment))
        db.checkinDao().insertValue(CheckinValue(id, moodId, mood))
        db.checkinDao().insertValue(CheckinValue(id, energyId, energy))
        db.checkinDao().insertValue(CheckinValue(id, anxietyId, anxiety))
    }

    @Test
    fun empty_record_exports_header_only() = runTest {
        assertEquals(1, exporter.exportCsv().trimEnd('\n').split("\n").size)
    }

    @Test
    fun records_are_exported_newest_first() = runTest {
        record(answeredTime = 100L, comment = "older", mood = 1, energy = 2, anxiety = 3)
        record(answeredTime = 200L, comment = "newer", mood = 4, energy = 5, anxiety = 1)
        val out = exporter.exportCsv().trimEnd('\n').split("\n")
        assertEquals(3, out.size) // header + 2 rows
        assertTrue("newest first", out[1].contains(",4,5,1,newer,"))
        assertTrue(out[2].contains(",1,2,3,older,"))
    }
}
