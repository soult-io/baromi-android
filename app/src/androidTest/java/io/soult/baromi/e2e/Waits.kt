package io.soult.baromi.e2e

/** Espresso doesn't wait on background coroutines (Room queries); retry an assertion until it passes. */
object Waits {
    fun waitUntil(timeoutMs: Long = 6000, action: () -> Unit) {
        val end = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < end) {
            try {
                action()
                return
            } catch (t: Throwable) {
                last = t
                Thread.sleep(100)
            }
        }
        throw last ?: AssertionError("waitUntil timed out")
    }
}
