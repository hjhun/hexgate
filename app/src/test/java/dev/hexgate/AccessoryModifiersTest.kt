package dev.hexgate

import com.termux.terminal.KeyHandler.*
import org.junit.Assert.*
import org.junit.Test

class AccessoryModifiersTest {
    @Test fun tappedModifiersCombineForOneInput() {
        val keys = AccessoryModifiers()
        for (key in listOf(KEYMOD_CTRL, KEYMOD_ALT, KEYMOD_SHIFT)) {
            keys.press(key); keys.release(key, false)
        }
        assertEquals(KEYMOD_CTRL or KEYMOD_ALT or KEYMOD_SHIFT, keys.mask)
        keys.consume()
        assertEquals(0, keys.mask)
    }
    @Test fun heldModifiersSurviveRepeatedInputAndReleaseIndependently() {
        val keys = AccessoryModifiers()
        keys.press(KEYMOD_CTRL); keys.press(KEYMOD_SHIFT)
        repeat(3) { keys.consume(); assertEquals(KEYMOD_CTRL or KEYMOD_SHIFT, keys.mask) }
        keys.release(KEYMOD_CTRL, false)
        assertEquals(KEYMOD_SHIFT, keys.mask)
        keys.consume(); keys.release(KEYMOD_SHIFT, false)
        assertEquals(0, keys.mask)
    }
    @Test fun cancellationDoesNotLatchAndSecondTapTurnsOff() {
        val keys = AccessoryModifiers()
        keys.press(KEYMOD_SHIFT); keys.release(KEYMOD_SHIFT, true)
        assertEquals(0, keys.mask)
        repeat(2) { keys.press(KEYMOD_SHIFT); keys.release(KEYMOD_SHIFT, false) }
        assertEquals(0, keys.mask)
    }
}
