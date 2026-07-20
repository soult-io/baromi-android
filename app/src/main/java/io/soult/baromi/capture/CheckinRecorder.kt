package io.soult.baromi.capture

import androidx.room.withTransaction
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinTag

/**
 * Writes one check-in and its tag links atomically. The [Checkin] constructor enforces the
 * ranges/comment guards, so an invalid capture throws before anything is persisted.
 */
class CheckinRecorder(private val db: BaromiDatabase) {

    suspend fun record(
        mood: Int,
        energy: Int,
        anxiety: Int,
        comment: String?,
        tagIds: List<Long>,
        promptTime: Long?,
        answeredTime: Long,
    ): Long = db.withTransaction {
        val checkinDao = db.checkinDao()
        val id = checkinDao.insert(
            Checkin(
                promptTime = promptTime,
                answeredTime = answeredTime,
                mood = mood,
                energy = energy,
                anxiety = anxiety,
                comment = comment?.trim()?.ifEmpty { null },
            ),
        )
        tagIds.forEach { checkinDao.attachTag(CheckinTag(id, it)) }
        id
    }
}
