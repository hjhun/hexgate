package dev.hexgate

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.KeyHandler.*
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test

class AccessoryKeyboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun nativeInputCombinesHeldModifiersAndClearsTappedShift() {
        val sent = java.io.ByteArrayOutputStream()
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                val model = activity.model
                val session = TerminalSession(object : TerminalSession.Transport {
                    override fun send(bytes: ByteArray) { sent.write(bytes) }
                    override fun resize(columns: Int, rows: Int) {}
                }, QuietSessionClient())
                val view = TerminalView(activity, null)
                view.setTextSize(20); view.setTerminalViewClient(model.viewClient(view))
                view.attachSession(session); view.layout(0, 0, 800, 600)
                model.modifiers.press(KEYMOD_CTRL); model.modifiers.press(KEYMOD_SHIFT)
                repeat(2) { view.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)) }
                model.modifiers.release(KEYMOD_CTRL, false); model.modifiers.release(KEYMOD_SHIFT, false)
                // Key-code handling consumes one-shot state on the main queue.
            }
            instrumentation.waitForIdleSync()
            assertEquals("\u001b[1;6D\u001b[1;6D", sent.toString("UTF-8"))
            scenario.onActivity { activity ->
                val model = activity.model
                assertEquals(0, model.modifiers.mask)
                val session = TerminalSession(object : TerminalSession.Transport {
                    override fun send(bytes: ByteArray) { sent.write(bytes) }
                    override fun resize(columns: Int, rows: Int) {}
                }, QuietSessionClient())
                val view = TerminalView(activity, null)
                view.setTextSize(20); view.setTerminalViewClient(model.viewClient(view))
                view.attachSession(session); view.layout(0, 0, 800, 600)
                model.shift = true
                view.onKeyDown(KeyEvent.KEYCODE_A, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
                view.onKeyDown(KeyEvent.KEYCODE_B, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B))
            }
            assertEquals("\u001b[1;6D\u001b[1;6DAb", sent.toString("UTF-8"))
        }
    }

    @Test fun imeBatchHonorsTappedAndHeldShift() {
        val sent = java.io.ByteArrayOutputStream()
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                val model = activity.model
                val session = TerminalSession(object : TerminalSession.Transport {
                    override fun send(bytes: ByteArray) { sent.write(bytes) }
                    override fun resize(columns: Int, rows: Int) {}
                }, QuietSessionClient())
                val view = TerminalView(activity, null)
                view.setTextSize(20); view.setTerminalViewClient(model.viewClient(view))
                view.attachSession(session); view.layout(0, 0, 800, 600)
                val input = requireNotNull(view.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                model.shift = true; input.commitText("ab", 1)
                assertEquals("Tapped Shift applies to the next character only", "Ab", sent.toString("UTF-8"))
                assertFalse(model.shift)
                model.modifiers.press(KEYMOD_SHIFT); input.commitText("cd", 1)
                assertEquals("AbCD", sent.toString("UTF-8")); assertTrue(model.shift)
                model.modifiers.release(KEYMOD_SHIFT, false)
                assertFalse(model.shift)
            }
        }
    }

    @Test fun accessoryChordsSendExactBytesOverSsh() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        val host = requireNotNull(args.getString("sshHost"))
        val fingerprint = requireNotNull(args.getString("sshFingerprint"))
        val profile = Profile(name = "Accessory fixture", host = host, port = 22222, user = args.getString("sshUser", "testuser"))
        val key = java.io.File("/data/local/tmp/hexgate-test-key").readText()
        val expected = "\u001b[1;2A\u001b[1;4C\u001b[1;8B\u001b[1;6D\u001b[1;6D\u001b[Z\r\u001b[C"
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            var previous: String? = null
            fun await(message: String, predicate: () -> Boolean) {
                val end = SystemClock.uptimeMillis() + 15000
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < end) { scenario.onActivity { ready = predicate() }; Thread.sleep(100) }
                assertTrue(message, ready)
            }
            try {
                scenario.onActivity {
                    model = it.model; previous = model.store.trusted(host, 22222)
                    model.store.trust(host, 22222, fingerprint); model.save(profile, key); model.connect(profile)
                }
                await("SSH fixture did not connect") { model.connected }
                val reader = "import os; print(\"CHORD_READY\",flush=True); b=b\"\"; exec(\"while len(b)<${expected.length}: b+=os.read(0,${expected.length}-len(b))\"); print(\"CHORD_HEX=\"+b.hex(),flush=True)"
                scenario.onActivity { model.sendKey("stty raw -echo; python3 -u -c " + SshConnection.quote(reader) + "; stty sane\r") }
                await("Raw SSH reader not ready") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim() == "CHORD_READY" } }
                scenario.onActivity {
                    model.shift = true; model.sendAccessory(KeyEvent.KEYCODE_DPAD_UP)
                    model.alt = true; model.shift = true; model.sendAccessory(KeyEvent.KEYCODE_DPAD_RIGHT)
                    model.ctrl = true; model.alt = true; model.shift = true; model.sendAccessory(KeyEvent.KEYCODE_DPAD_DOWN)
                    model.modifiers.press(KEYMOD_CTRL); model.modifiers.press(KEYMOD_SHIFT)
                    repeat(2) { model.sendAccessory(KeyEvent.KEYCODE_DPAD_LEFT) }
                    model.modifiers.release(KEYMOD_CTRL, false); model.modifiers.release(KEYMOD_SHIFT, false)
                    model.shift = true; model.sendAccessory(KeyEvent.KEYCODE_TAB)
                    model.sendAccessory(KeyEvent.KEYCODE_ENTER); model.sendAccessory(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
                val hex = expected.toByteArray().joinToString("") { "%02x".format(it) }
                await("Accessory bytes differed across real SSH") { model.terminal.emulator.screen.transcriptText.contains("CHORD_HEX=" + hex) }
            } finally {
                scenario.onActivity {
                    model.disconnect(); model.delete(profile)
                    if (previous == null) model.store.forgetTrust(host, 22222) else model.store.trust(host, 22222, previous!!)
                }
            }
        }
    }

    private fun find(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == label) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, label)?.let { found -> return found } }
        return null
    }
    private fun point(label: String): Pair<Float, Float> {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val node = instrumentation.uiAutomation.rootInActiveWindow?.let { find(it, label) }
            if (node != null) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) return bounds.exactCenterX() to bounds.exactCenterY()
            }
            Thread.sleep(100)
        }
        error("Missing visible key: $label")
    }

    @Test fun threeFingerChordAndRepeatedArrowsUseRealTouchDispatch() {
        launchHexgateTestActivity().use { scenario ->
            instrumentation.waitForIdleSync(); Thread.sleep(300)
            val ctrl = point("Ctrl"); val shift = point("Shift"); val arrow = point("←")
            assertTrue("Shift must precede arrows", shift.first < arrow.first)
            val downTime = SystemClock.uptimeMillis()
            fun event(action: Int, positions: List<Pair<Int, Pair<Float, Float>>>) {
                val props = positions.map { (id, _) -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
                val coords = positions.map { (_, pos) -> MotionEvent.PointerCoords().apply { x = pos.first; y = pos.second; pressure = 1f; size = 1f } }.toTypedArray()
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, positions.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
                instrumentation.waitForIdleSync()
            }
            event(MotionEvent.ACTION_DOWN, listOf(0 to ctrl))
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), listOf(0 to ctrl, 1 to shift))
            repeat(2) {
                event(MotionEvent.ACTION_POINTER_DOWN or (2 shl 8), listOf(0 to ctrl, 1 to shift, 2 to arrow))
                scenario.onActivity { assertEquals(KEYMOD_CTRL or KEYMOD_SHIFT, it.model.modifiers.mask) }
                event(MotionEvent.ACTION_POINTER_UP or (2 shl 8), listOf(0 to ctrl, 1 to shift, 2 to arrow))
            }
            event(MotionEvent.ACTION_POINTER_UP or (1 shl 8), listOf(0 to ctrl, 1 to shift))
            scenario.onActivity { assertEquals(KEYMOD_CTRL, it.model.modifiers.mask) }
            event(MotionEvent.ACTION_UP, listOf(0 to ctrl))
            scenario.onActivity { assertEquals("Used held keys must not latch", 0, it.model.modifiers.mask) }
            // Cancellation must also release a held key without toggling it on.
            event(MotionEvent.ACTION_DOWN, listOf(0 to shift))
            event(MotionEvent.ACTION_CANCEL, listOf(0 to shift))
            scenario.onActivity { assertEquals(0, it.model.modifiers.mask) }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir, "accessory-shift.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
    }
}
