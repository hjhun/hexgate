package dev.hexgate

import org.junit.Assert.*
import org.junit.Test

class SelectionClipboardGrantTest {
    @Test fun onlyExplicitGestureAllowsOneReplyFromItsOwnSession() {
        val grant = SelectionClipboardGrant()
        val session = Any()
        assertFalse(grant.consume(session, 0, "unsolicited"))
        grant.arm(session, 10)
        assertFalse(grant.consume(Any(), 11, "other session"))
        assertTrue(grant.consume(session, 12, "pane text"))
        assertFalse(grant.consume(session, 13, "second reply"))
        grant.arm(session, 20)
        assertFalse(grant.consume(session, 5020, "late"))
        grant.arm(session, 30)
        grant.clear()
        assertFalse(grant.consume(session, 31, "cancelled"))
        grant.arm(session, 40)
        assertFalse(grant.consume(session, 41, "한".repeat(400000)))
    }
}
