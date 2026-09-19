package io.soult.baromi.backup

/**
 * A full, self-contained snapshot of the user's data for backup/restore — categories, tags,
 * reminder times, and every check-in with its values and tags. Ids are the source database's own
 * ids, used only to wire a check-in's values/tags back together on import; the importer remaps them
 * to the target database's ids (matching categories by key/name and tags by label).
 */
data class BackupData(
    val scaleVersion: Int,
    val exportedAt: Long,
    val categories: List<BackupCategory>,
    val tags: List<BackupTag>,
    val reminderTimes: List<BackupReminder>,
    val checkins: List<BackupCheckin>,
)

data class BackupCategory(
    val id: Long,
    val key: String?,
    val name: String,
    val builtin: Boolean,
    val enabled: Boolean,
    val position: Int,
)

data class BackupTag(
    val id: Long,
    val label: String,
    val builtin: Boolean,
    val enabled: Boolean,
)

data class BackupReminder(
    val hour: Int,
    val minute: Int,
)

data class BackupCheckin(
    val promptTime: Long?,
    val answeredTime: Long,
    val comment: String?,
    val locked: Boolean,
    val values: List<BackupValue>,
    val tagIds: List<Long>,
)

data class BackupValue(
    val categoryId: Long,
    val value: Int,
)

/** Outcome of a merge import: how many check-ins were added and how many were skipped as dupes. */
data class ImportResult(
    val imported: Int,
    val skipped: Int,
)
