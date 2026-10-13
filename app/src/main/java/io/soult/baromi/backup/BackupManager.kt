package io.soult.baromi.backup

import androidx.room.withTransaction
import io.soult.baromi.data.BaromiDatabase
import io.soult.baromi.data.Category
import io.soult.baromi.data.Checkin
import io.soult.baromi.data.CheckinTag
import io.soult.baromi.data.CheckinValue
import io.soult.baromi.data.ReminderTime
import io.soult.baromi.data.ScaleSchema
import io.soult.baromi.data.Tag

/**
 * Backup (export) and restore (import) of the whole local dataset. Local-only app, so this is how a
 * user carries their tracking between installs/devices. Import MERGES and SKIPS DUPLICATES: it never
 * deletes existing data; a check-in is treated as a duplicate (and skipped) when its answered time
 * already exists locally. Categories are matched by key (built-ins) or name (custom) and tags by
 * label, inserting any that are missing; a check-in's values/tags are remapped onto those local ids.
 * The whole import runs in one transaction, so a malformed backup rolls back cleanly.
 */
class BackupManager(private val db: BaromiDatabase) {

    suspend fun export(): BackupData {
        val categories = db.categoryDao().getAll()
        val tags = db.tagDao().getAll()
        val times = db.reminderTimeDao().getAll()
        val checkins = db.checkinDao().getAllWithDetails()
        return BackupData(
            scaleVersion = ScaleSchema.VERSION,
            exportedAt = System.currentTimeMillis(),
            categories = categories.map { BackupCategory(it.id, it.key, it.name, it.builtin, it.enabled, it.position) },
            tags = tags.map { BackupTag(it.id, it.label, it.builtin, it.enabled) },
            reminderTimes = times.map { BackupReminder(it.hour, it.minute) },
            checkins = checkins.map { cwd ->
                BackupCheckin(
                    promptTime = cwd.checkin.promptTime,
                    answeredTime = cwd.checkin.answeredTime,
                    comment = cwd.checkin.comment,
                    locked = cwd.checkin.locked,
                    values = cwd.values.map { BackupValue(it.categoryId, it.value) },
                    tagIds = cwd.tags.map { it.id },
                )
            },
        )
    }

    suspend fun exportJson(): String = BackupCodec.encode(export())

    suspend fun importJson(json: String): ImportResult = import(BackupCodec.decode(json))

    suspend fun import(data: BackupData): ImportResult = db.withTransaction {
        val categoryDao = db.categoryDao()
        val tagDao = db.tagDao()
        val reminderDao = db.reminderTimeDao()
        val checkinDao = db.checkinDao()

        // Resolve every backup category to a local id (matching, else inserting), keyed by the
        // backup's own id so a check-in's values can be remapped.
        val catIdMap = HashMap<Long, Long>()
        run {
            val local = categoryDao.getAll()
            val byKey = local.filter { it.key != null }.associateBy { it.key!! }
            // Exact names first; then by normalized name, so a stored name from before the length limit
            // still matches the same name in a backup (re-importing must not duplicate it).
            val byExactName = local.associateBy { it.name }
            val byName = local.associateBy { Category.normalizeName(it.name) }
            for (bc in data.categories) {
                // A backup may come from anywhere: hold its names to the same length limit as Settings.
                val name = Category.normalizeName(bc.name)
                val byKeyMatch = bc.key?.let { byKey[it] }
                if (byKeyMatch == null && name.isEmpty()) continue
                val existing = byKeyMatch ?: byExactName[bc.name.trim()] ?: byName[name]
                val localId = existing?.id ?: run {
                    val inserted = categoryDao.insert(
                        Category(key = bc.key, name = name, builtin = bc.builtin, enabled = bc.enabled, position = bc.position),
                    )
                    // insert IGNOREs on the unique name index (returns -1); resolve by re-reading.
                    if (inserted >= 0) inserted else categoryDao.getAll().firstOrNull { it.name == name }?.id
                }
                if (localId != null) catIdMap[bc.id] = localId
            }
        }

        val tagIdMap = HashMap<Long, Long>()
        run {
            val byLabel = tagDao.getAll().associateBy { it.label }
            for (bt in data.tags) {
                val localId = byLabel[bt.label]?.id ?: run {
                    val inserted = tagDao.insert(Tag(label = bt.label, builtin = bt.builtin, enabled = bt.enabled))
                    if (inserted >= 0) inserted else tagDao.getAll().firstOrNull { it.label == bt.label }?.id
                }
                if (localId != null) tagIdMap[bt.id] = localId
            }
        }

        // Reminder times: insert IGNOREs on the unique (hour,minute) index (dedup-safe), and we
        // stop at MAX_PER_DAY so a hand-edited/old backup can't exceed the app's reminder cap.
        var reminderSlots = ReminderTime.MAX_PER_DAY - reminderDao.count()
        for (r in data.reminderTimes) {
            if (reminderSlots <= 0) break
            val inserted = reminderDao.insert(ReminderTime(hour = r.hour, minute = r.minute))
            if (inserted >= 0) reminderSlots-- // -1 == IGNORE'd duplicate, which used no slot
        }

        val existingAnswered = checkinDao.getAll().map { it.answeredTime }.toMutableSet()
        var imported = 0
        var skipped = 0
        for (bc in data.checkins) {
            if (!existingAnswered.add(bc.answeredTime)) {
                skipped++
                continue
            }
            val newId = checkinDao.insert(
                Checkin(promptTime = bc.promptTime, answeredTime = bc.answeredTime, comment = bc.comment, locked = bc.locked),
            )
            bc.values.forEach { v ->
                val localCat = catIdMap[v.categoryId] ?: return@forEach
                checkinDao.insertValue(CheckinValue(checkinId = newId, categoryId = localCat, value = v.value))
            }
            bc.tagIds.forEach { backupTagId ->
                val localTag = tagIdMap[backupTagId] ?: return@forEach
                checkinDao.attachTag(CheckinTag(newId, localTag))
            }
            imported++
        }
        ImportResult(imported = imported, skipped = skipped)
    }
}
