package io.soult.baromi.data

/**
 * Presentation for a category's 1..5 options. Built-in categories use their face-emoji sets and
 * worded anchors; custom categories use number-keycap emoji (1..5) until custom icons exist.
 */
object CategoryDefs {

    private data class Def(val emojis: List<String>, val labels: List<String>)

    private val BUILTIN: Map<String, Def> = mapOf(
        "mood" to Def(
            listOf("😢", "😕", "😐", "🙂", "😄"),
            listOf("very low", "low", "okay", "good", "very good"),
        ),
        "energy" to Def(
            listOf("😴", "🥱", "😐", "🙂", "⚡"),
            listOf("drained", "low", "okay", "good", "energized"),
        ),
        "anxiety" to Def(
            listOf("😰", "😟", "😐", "🙂", "😌"),
            listOf("very anxious", "anxious", "okay", "calm", "very calm"),
        ),
    )

    private val NUMBER_KEYCAPS = listOf("1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣")
    private val NUMBER_LABELS = listOf("1", "2", "3", "4", "5")

    fun emojisFor(category: Category): List<String> =
        category.key?.let { BUILTIN[it]?.emojis } ?: NUMBER_KEYCAPS

    fun labelsFor(category: Category): List<String> =
        category.key?.let { BUILTIN[it]?.labels } ?: NUMBER_LABELS
}
