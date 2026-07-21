package io.soult.baromi.data

/**
 * The frozen 1..5 rating scale shared by every category. Higher = the better/more state; each
 * category defines what its ends mean (built-ins via [CategoryDefs], custom via number keycaps).
 */
object ScaleSchema {
    const val VERSION = 2
    const val MIN = 1
    const val MAX = 5

    fun inRange(value: Int): Boolean = value in MIN..MAX

    fun requireInRange(name: String, value: Int) =
        require(inRange(value)) { "$name out of range $MIN..$MAX: $value" }
}
