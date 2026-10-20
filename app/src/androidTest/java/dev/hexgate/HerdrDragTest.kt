package dev.hexgate

import android.view.*
import android.content.ClipData
import android.content.ClipboardManager
import com.termux.view.TerminalView
import com.termux.terminal.TerminalSession
import org.junit.Assert.*
import org.junit.Test

class HerdrDragTest {
    private fun find(v: View): TerminalView? {
        if (v is TerminalView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun trackedPaneDragReachesRemoteWithoutLocalCrossPaneSelection() {
        launchHexgateTestActivity().use { scenario -> scenario.onActivity { a ->
            val v = requireNotNull(find(a.window.decorView))
            val output = StringBuilder()
            val grants = mutableListOf<Boolean>()
            v.setTerminalViewClient(object : QuietViewClient() {
                override fun onRemoteSelectionCopy(enabled: Boolean) { grants += enabled }
            })
            val session = TerminalSession(object : TerminalSession.Transport {
                override fun send(data: ByteArray) { output.append(String(data)) }
                override fun resize(columns: Int, rows: Int) {}
            }, QuietSessionClient())
            v.attachSession(session)
            session.receive("\u001b[?1002h\u001b[?1006hLEFT | RIGHT\r\nLEFT | RIGHT".toByteArray())
            grants.clear()
            val now = android.os.SystemClock.uptimeMillis()
            val pointer = MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE }
            fun drag(shift: Boolean) { for (step in 0..5) {
                val action = when(step) { 0 -> MotionEvent.ACTION_DOWN; 5 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val coords = MotionEvent.PointerCoords().apply { x=30f+step*20; y=30f+step*10 }
                val e=MotionEvent.obtain(now,now+step*20,action,1,arrayOf(pointer),arrayOf(coords),if(shift)KeyEvent.META_SHIFT_ON else 0,
                    if(step==5)0 else MotionEvent.BUTTON_PRIMARY,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0)
                v.dispatchTouchEvent(e);e.recycle()
            } }
            drag(false)
            assertEquals(listOf(false), grants)
            assertEquals("No duplicate drag reports", 6, output.count { it == '\u001b' })
            grants.clear()
            output.clear()
            drag(true)
            assertTrue("Shift drag must authorize pane copy", grants.contains(true))
            assertTrue("Herdr must receive the initial press",output.contains("\u001b[<0;"))
            assertTrue("Herdr must receive drag motion",output.contains("\u001b[<32;"))
            assertFalse("Remote pane selection must not select adjacent local panes",v.isSelectingText)
        } }
    }
    @Test fun remoteClipboardNeedsShiftGestureAndRejectsUnsolicitedOrRepeatedReplies() {
        launchHexgateTestActivity().use { scenario -> scenario.onActivity { a ->
            val v = requireNotNull(find(a.window.decorView))
            val clipboard = a.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("test", "unchanged"))
            fun reply(text: String) {
                val encoded = android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)
                a.model.terminal.receive("\u001b]52;c;$encoded\u0007".toByteArray())
            }
            reply("unsolicited")
            assertEquals("unchanged", clipboard.primaryClip!!.getItemAt(0).text.toString())
            val client = a.model.viewClient(v)
            client.onRemoteSelectionCopy(true)
            reply("selected pane only")
            assertEquals("selected pane only", clipboard.primaryClip!!.getItemAt(0).text.toString())
            reply("second response")
            assertEquals("selected pane only", clipboard.primaryClip!!.getItemAt(0).text.toString())
            client.onRemoteSelectionCopy(true)
            client.onRemoteSelectionCopy(false)
            reply("cancelled gesture")
            assertEquals("selected pane only", clipboard.primaryClip!!.getItemAt(0).text.toString())
            clipboard.clearPrimaryClip()
        } }
    }

}
