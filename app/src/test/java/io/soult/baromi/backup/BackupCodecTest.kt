package io.soult.baromi.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric so the real org.json is on the classpath (not the stubbed android.jar one).
@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {

    private val sample = BackupData(
        scaleVersion = 2,
        exportedAt = 1_700_000_000_000L,
        categories = listOf(
            BackupCategory(id = 1, key = "mood", name = "Mood", builtin = true, enabled = true, position = 0),
            BackupCategory(id = 5, key = null, name = "Focus", builtin = false, enabled = false, position = 3),
        ),
        tags = listOf(BackupTag(id = 2, label = "poor sleep", builtin = true, enabled = true)),
        reminderTimes = listOf(BackupReminder(9, 0), BackupReminder(20, 30)),
        checkins = listOf(
            BackupCheckin(
                promptTime = null,
                answeredTime = 1_699_000_000_000L,
                comment = "rough day",
                locked = false,
                values = listOf(BackupValue(categoryId = 1, value = 2), BackupValue(categoryId = 5, value = 4)),
                tagIds = listOf(2),
            ),
        ),
    )

    @Test
    fun round_trips_all_fields() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun rejects_a_non_baromi_document() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("""{"format":"something-else","version":1}""") }
    }

    @Test
    fun rejects_a_future_version() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"format":"baromi-backup","version":999}""")
        }
    }
}
