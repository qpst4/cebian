package com.slideindex.app.otp

/**
 * Cross-path deduplication for OTP capture: LSPosed SMS hooks, telephony provider,
 * and notification listener can observe the same message within seconds.
 */
object OtpCaptureDeduplicator {
    const val CODE_WINDOW_MS = 60_000L
    const val SMS_BODY_WINDOW_MS = 60_000L
    const val AUTO_FILL_WINDOW_MS = 30_000L

    private val lock = Any()
    private val recentSmsBodies = LinkedHashMap<String, Long>()
    private val recentCodes = LinkedHashMap<String, Long>()
    private val recentAutoFillCodes = LinkedHashMap<String, Long>()

    /** Returns true when this SMS body should be forwarded to the app module. */
    fun tryConsumeSmsForward(sender: String, body: String): Boolean {
        val normalizedBody = body.trim()
        if (normalizedBody.isEmpty()) return false
        val key = "${sender.trim()}|$normalizedBody"
        return tryConsume(recentSmsBodies, key, SMS_BODY_WINDOW_MS)
    }

    /** Returns true when this extracted code should trigger record / side effects. */
    fun tryConsumeExtractedCode(code: String): Boolean {
        val key = code.trim()
        if (key.isEmpty()) return false
        return tryConsume(recentCodes, key, CODE_WINDOW_MS)
    }

    /** Returns true when a new auto-fill attempt should be started for this code. */
    fun tryConsumeAutoFillRequest(code: String): Boolean {
        val key = code.trim()
        if (key.isEmpty()) return false
        return tryConsume(recentAutoFillCodes, key, AUTO_FILL_WINDOW_MS)
    }

    private fun tryConsume(
        map: LinkedHashMap<String, Long>,
        key: String,
        windowMs: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            prune(map, now, windowMs)
            val previous = map.put(key, now)
            return previous == null
        }
    }

    private fun prune(map: LinkedHashMap<String, Long>, now: Long, windowMs: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > windowMs) {
                iterator.remove()
            }
        }
    }

    internal fun clearForTests() {
        synchronized(lock) {
            recentSmsBodies.clear()
            recentCodes.clear()
            recentAutoFillCodes.clear()
        }
    }
}
