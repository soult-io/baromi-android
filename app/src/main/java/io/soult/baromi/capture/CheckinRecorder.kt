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
     * Overwrites an existing check-in's comment and tags, and the values for the supplied categories,
     * atomically. Values for categories NOT in [values] are left intact — so a value recorded for a
     * category that was later disabled (and is therefore absent from the edit form) is preserved, not
     * silently dropped. Original prompt/answered timestamps are kept (an edit must not shift the
     * record's timeline). No-op if the check-in no longer exists.
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
        val categoryIds = values.keys.toList()
        if (categoryIds.isNotEmpty()) checkinDao.deleteValuesForCategories(checkinId, categoryIds)
        values.forEach { (categoryId, value) ->
            checkinDao.insertValue(CheckinValue(checkinId = checkinId, categoryId = categoryId, value = value))
        }
        checkinDao.clearTags(checkinId)
        tagIds.forEach { checkinDao.attachTag(CheckinTag(checkinId, it)) }
    }
}
