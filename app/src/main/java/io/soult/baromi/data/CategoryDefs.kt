package io.soult.baromi.data

import io.soult.baromi.R

/**
 * Presentation for a category's 1..5 options. Built-in categories (mood/energy/anxiety) use custom
 * single-colour face icons + worded anchors; custom categories fall back to number-keycap emoji
 * (1..5) until they get their own icons.
 */
object CategoryDefs {

    private data class Def(
        val icons: List<Int>,
        val labels: List<String>,
    )

    private val BUILTIN: Map<String, Def> = mapOf(
        "mood" to Def(
            listOf(
                R.drawable.ic_face_mood_1,
                R.drawable.ic_face_mood_2,
                R.drawable.ic_face_mood_3,
                R.drawable.ic_face_mood_4,
                R.drawable.ic_face_mood_5,
            ),
            listOf("very low", "low", "okay", "good", "very good"),
        ),
        "energy" to Def(
            listOf(
                R.drawable.ic_face_energy_1,
                R.drawable.ic_face_energy_2,
                R.drawable.ic_face_energy_3,
                R.drawable.ic_face_energy_4,
                R.drawable.ic_face_energy_5,
            ),
            listOf("drained", "low", "okay", "good", "energized"),
        ),
        "anxiety" to Def(
            listOf(
                R.drawable.ic_face_anxiety_1,
                R.drawable.ic_face_anxiety_2,
                R.drawable.ic_face_anxiety_3,
                R.drawable.ic_face_anxiety_4,
                R.drawable.ic_face_anxiety_5,
            ),
            listOf("very anxious", "anxious", "okay", "calm", "very calm"),
        ),
    )

    private val NUMBER_KEYCAPS = listOf("1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣")
    private val NUMBER_LABELS = listOf("1", "2", "3", "4", "5")

    /** Drawable icons for a built-in category's 1..5 options, or null for a custom category. */
    fun iconsFor(category: Category): List<Int>? =
        category.key?.let { BUILTIN[it]?.icons }

    /** Number-keycap emoji fallback for custom categories (which have no face icons). */
    fun emojisFor(category: Category): List<String> = NUMBER_KEYCAPS

    fun labelsFor(category: Category): List<String> =
        category.key?.let { BUILTIN[it]?.labels } ?: NUMBER_LABELS
}
