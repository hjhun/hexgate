package dev.hexgate

/** A single explicit gesture may accept one bounded OSC52 reply from its own session. */
internal class SelectionClipboardGrant {
    private var owner: Any? = null
    private var expires = 0L
    fun clear() { owner = null; expires = 0 }
    fun arm(session: Any, now: Long) { owner = session; expires = now + 5000 }
    fun consume(session: Any, now: Long, text: String): Boolean {
        if (owner !== session || now >= expires) return false
        clear()
        return text.isNotEmpty() && text.length <= 1024 * 1024 && text.toByteArray().size <= 1024 * 1024
    }
}
