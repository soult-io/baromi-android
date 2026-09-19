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

    /**
     * Overwrites an existing check-in's values, comment, and tags atomically, preserving its
     * original prompt/answered timestamps (the record's timeline must not shift on an edit).
     * No-op if the check-in no longer exists.
     */
    suspend fun update(
        checkinId: Long,
        values: Map<Long, Int>,
        comment: String?,
        tagIds: List<Long>,
    ) = db.withTransaction {
        val checkinDao = db.checkinDao()
        val existing = checkinDao.getById(checkinId) ?: return@withTransaction
        checkinDao.update(existing.copy(comment = comment?.trim()?.ifEmpty { null }))
        checkinDao.deleteValues(checkinId)
        values.forEach { (categoryId, value) ->
            checkinDao.insertValue(CheckinValue(checkinId = checkinId, categoryId = categoryId, value = value))
        }
        checkinDao.clearTags(checkinId)
        tagIds.forEach { checkinDao.attachTag(CheckinTag(checkinId, it)) }
    }
}
