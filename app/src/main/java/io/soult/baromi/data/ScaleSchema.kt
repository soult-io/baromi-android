package io.soult.baromi.data

/**
 * Frozen definition of the 1..5 rating scale.
 *
 * Every axis points the same way: **higher is the better state**, including Anxiety
 * (5 = very calm, 1 = very anxious). This lets averages and future charts treat all three
 * axes uniformly. [VERSION] is embedded in exports so historic rows stay interpretable if
 * the scale is ever changed.
 */
object ScaleSchema {
    const val VERSION = 1
    const val MIN = 1
    const val MAX = 5

    /**
     * Human-readable anchors per axis, ordered (low = 1) to (high = 5). Used verbatim in
     * export headers so a shared file is self-documenting.
     */
    val axes: Map<String, Pair<String, String>> = linkedMapOf(
        "mood" to ("very low" to "very good"),
        "energy" to ("drained" to "energized"),
        "anxiety" to ("very anxious" to "very calm"),
    )

    fun inRange(value: Int): Boolean = value in MIN..MAX
}
