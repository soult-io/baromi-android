package io.soult.baromi.capture

import androidx.room.withTransaction
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinTag
import io.soult.baromi.data.CheckinValue

/**
 * Writes one check-in, its per-category values, and its tag links atomically. The [Checkin] and
 * [CheckinValue] constructors enforce the comment/range guards, so an invalid capture throws
 * before anything is persisted.
 */
class CheckinRecorder(private val db: BaromiDatabase) {

    /**
     * @param values category id -> 1..5 rating for every enabled category being recorded.
     */
    suspend fun record(
        values: Map<Long, Int>,
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
                comment = comment?.trim()?.ifEmpty { null },
            ),
        )
        values.forEach { (categoryId, value) ->
            checkinDao.insertValue(CheckinValue(checkinId = id, categoryId = categoryId, value = value))
        }
        tagIds.forEach { checkinDao.attachTag(CheckinTag(id, it)) }
        id
    }
}
