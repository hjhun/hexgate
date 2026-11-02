package dev.hexgate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Tap to latch for one input; hold to modify every input until release. */
class AccessoryModifiers {
    private var latched by mutableIntStateOf(0)
    private var held by mutableIntStateOf(0)
    private var used = 0
    val mask: Int get() = latched or held
    fun active(key: Int) = mask and key != 0
    fun toggle(key: Int) { latched = latched xor key }
    fun set(key: Int, enabled: Boolean) { latched = if (enabled) latched or key else latched and key.inv() }
    fun press(key: Int) { held = held or key; used = used and key.inv() }
    fun release(key: Int, cancelled: Boolean) {
        if (!cancelled && used and key == 0) toggle(key)
        held = held and key.inv()
        used = used and key.inv()
    }
    fun markUsed() { used = used or held }
    fun consume() { markUsed(); latched = 0 }
}
