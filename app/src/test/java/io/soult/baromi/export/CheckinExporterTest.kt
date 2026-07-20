package io.soult.baromi.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Checkin
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

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BaromiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = CheckinExporter(db)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun empty_record_exports_legend_and_header_only() = runTest {
        assertEquals(2, exporter.exportCsv().trimEnd('\n').split("\n").size)
    }

    @Test
    fun records_are_exported_newest_first() = runTest {
        db.checkinDao().insert(Checkin(promptTime = null, answeredTime = 100L, mood = 1, energy = 2, anxiety = 3, comment = "older"))
        db.checkinDao().insert(Checkin(promptTime = null, answeredTime = 200L, mood = 4, energy = 5, anxiety = 1, comment = "newer"))
        val out = exporter.exportCsv().trimEnd('\n').split("\n")
        assertEquals(4, out.size) // legend + header + 2 rows
        assertTrue("newest first", out[2].contains(",4,5,1,newer,"))
        assertTrue(out[3].contains(",1,2,3,older,"))
    }
}
