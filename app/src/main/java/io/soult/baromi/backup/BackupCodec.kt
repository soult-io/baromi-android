package io.soult.baromi.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialises [BackupData] to/from a versioned JSON document. Kept separate from I/O and the DB so
 * the round-trip is pure and unit-testable. Decode rejects anything that isn't a Baromi backup or
 * is a newer format than this build understands, so a bad file fails loudly instead of corrupting.
 */
object BackupCodec {
    const val FORMAT = "baromi-backup"
    const val VERSION = 1

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("scaleVersion", data.scaleVersion)
        root.put("exportedAt", data.exportedAt)

        root.put(
            "categories",
            JSONArray().apply {
                data.categories.forEach { c ->
                    put(
                        JSONObject()
                            .put("id", c.id)
                            .put("key", c.key ?: JSONObject.NULL)
                            .put("name", c.name)
                            .put("builtin", c.builtin)
                            .put("enabled", c.enabled)
                            .put("position", c.position),
                    )
                }
            },
        )
        root.put(
            "tags",
            JSONArray().apply {
                data.tags.forEach { t ->
                    put(
                        JSONObject()
                            .put("id", t.id)
                            .put("label", t.label)
                            .put("builtin", t.builtin)
                            .put("enabled", t.enabled),
                    )
                }
            },
        )
        root.put(
            "reminderTimes",
            JSONArray().apply {
                data.reminderTimes.forEach { r ->
                    put(JSONObject().put("hour", r.hour).put("minute", r.minute))
                }
            },
        )
        root.put(
            "checkins",
            JSONArray().apply {
                data.checkins.forEach { ci ->
                    put(
                        JSONObject()
                            .put("promptTime", ci.promptTime ?: JSONObject.NULL)
                            .put("answeredTime", ci.answeredTime)
                            .put("comment", ci.comment ?: JSONObject.NULL)
                            .put("locked", ci.locked)
                            .put(
                                "values",
                                JSONArray().apply {
                                    ci.values.forEach { v ->
                                        put(JSONObject().put("categoryId", v.categoryId).put("value", v.value))
                                    }
                                },
                            )
                            .put(
                                "tagIds",
                                JSONArray().apply { ci.tagIds.forEach { put(it) } },
                            ),
                    )
                }
            },
        )
        return root.toString(2)
    }

    fun decode(json: String): BackupData {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Not a Baromi backup file" }
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) { "Unsupported backup version: $version" }

        return BackupData(
            scaleVersion = root.optInt("scaleVersion", 0),
            exportedAt = root.optLong("exportedAt", 0L),
            categories = root.array("categories").mapObjects { o ->
                BackupCategory(
                    id = o.getLong("id"),
                    key = o.nullableString("key"),
                    name = o.getString("name"),
                    builtin = o.optBoolean("builtin", false),
                    enabled = o.optBoolean("enabled", true),
                    position = o.optInt("position", 0),
                )
            },
            tags = root.array("tags").mapObjects { o ->
                BackupTag(
                    id = o.getLong("id"),
                    label = o.getString("label"),
                    builtin = o.optBoolean("builtin", false),
                    enabled = o.optBoolean("enabled", true),
                )
            },
            reminderTimes = root.array("reminderTimes").mapObjects { o ->
                BackupReminder(hour = o.getInt("hour"), minute = o.getInt("minute"))
            },
            checkins = root.array("checkins").mapObjects { o ->
                BackupCheckin(
                    promptTime = o.nullableLong("promptTime"),
                    answeredTime = o.getLong("answeredTime"),
                    comment = o.nullableString("comment"),
                    locked = o.optBoolean("locked", false),
                    values = o.array("values").mapObjects { v ->
                        BackupValue(categoryId = v.getLong("categoryId"), value = v.getInt("value"))
                    },
                    tagIds = o.array("tagIds").let { arr -> (0 until arr.length()).map { arr.getLong(it) } },
                )
            },
        )
    }

    private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
