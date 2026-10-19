package dev.hexgate

import android.content.ClipData
import android.content.ClipboardManager
import android.view.*
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ClipboardShortcutsTest {
    private fun terminal(v:View):TerminalView? {
        if(v is TerminalView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)terminal(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun shortcut(v:TerminalView, key:Int, shift:Boolean=true) {
        v.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CTRL_LEFT))
        val now=android.os.SystemClock.uptimeMillis()
        v.dispatchKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN,key,0,KeyEvent.META_CTRL_ON or (if(shift)KeyEvent.META_SHIFT_ON else 0)))
        v.dispatchKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_UP,key,0,KeyEvent.META_CTRL_ON or (if(shift)KeyEvent.META_SHIFT_ON else 0)))
        v.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_CTRL_LEFT))
    }
    private fun drag(v:TerminalView, start:Float, end:Float) {
        val props=MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE }
        val now=android.os.SystemClock.uptimeMillis()
        for(step in 0..6) {
            val action=when(step) { 0->MotionEvent.ACTION_DOWN;6->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE }
            val coords=MotionEvent.PointerCoords().apply { x=(start+(end-start)*step/6)*v.mRenderer.fontWidth;y=v.mRenderer.fontLineSpacing/2f }
            val e=MotionEvent.obtain(now,now+step*20L,action,1,arrayOf(props),arrayOf(coords),0,
                if(step==6)0 else MotionEvent.BUTTON_PRIMARY,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0)
            assertTrue(v.dispatchTouchEvent(e));e.recycle()
        }
    }
    @Test fun mouseDragAndCtrlShiftCSelectMixedWidthTextInBothDirections() {
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { a ->
                val v=requireNotNull(terminal(a.window.decorView))
                val clipboard=a.getSystemService(ClipboardManager::class.java)
                a.model.terminal.receive("\u001b[2J\u001b[HCOPY한글 other".toByteArray())
                drag(v,0.2f,7.2f)
                assertEquals("COPY한글",v.selectedText)
                shortcut(v,KeyEvent.KEYCODE_C)
                assertEquals("COPY한글",clipboard.primaryClip!!.getItemAt(0).text.toString())
                drag(v,7.2f,0.2f)
                assertEquals("COPY한글",v.selectedText)
                shortcut(v,KeyEvent.KEYCODE_C)
                assertEquals("COPY한글",clipboard.primaryClip!!.getItemAt(0).text.toString())
                drag(v,0.2f,6.2f) // Ending on the first cell of a wide character still selects it whole.
                assertEquals("COPY한글",v.selectedText)
                clipboard.clearPrimaryClip()
            }
        }
    }
    @Test fun ctrlShiftVPastesOverSshAndCtrlCStillInterrupts() {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        val profile=Profile(name="Clipboard fixture",host=args.getString("sshHost")!!,port=22222,user=args.getString("sshUser","testuser"))
        launchHexgateTestActivity().use { scenario ->
            lateinit var model:TerminalModel
            lateinit var v:TerminalView
            fun await(message:String, predicate:()->Boolean) {
                var ready=false;val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                while(!ready && System.nanoTime()<end) { scenario.onActivity { ready=predicate() };Thread.sleep(100) }
                assertTrue(message,ready)
            }
            scenario.onActivity { a ->
                model=a.model;v=requireNotNull(terminal(a.window.decorView))
                model.store.trust(profile.host,profile.port,args.getString("sshFingerprint")!!)
                model.save(profile,File("/data/local/tmp/hexgate-test-key").readText());model.connect(profile)
            }
            try {
                await("SSH missing") { model.connected && model.terminal.emulator.screen.transcriptText.contains(profile.user) }
                scenario.onActivity { a ->
                    a.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture","printf 'PASTE_한글_OK\\n'"))
                    shortcut(v,KeyEvent.KEYCODE_V);model.sendKey("\r")
                }
                await("Ctrl+Shift+V did not paste UTF-8 over SSH") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="PASTE_한글_OK" } }
                scenario.onActivity {
                    model.sendKey("stty raw -echo; printf 'RAW_READY\\r\\n'; dd bs=1 count=1 2>/dev/null | od -An -tu1; stty sane\r")
                }
                await("Raw input fixture not ready") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="RAW_READY" } }
                scenario.onActivity { shortcut(v,KeyEvent.KEYCODE_V,false) }
                await("Plain Ctrl+V was intercepted instead of sending byte 22") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="22" } }
                val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.serviceInfo=automation.serviceInfo.apply {
                    flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
                scenario.onActivity { a ->
                    a.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture","printf 'MENU_PASTE_OK\\n'"))
                    assertTrue(v.showContextMenu(80f,80f))
                }
                fun findPaste(n:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
                    if(n.text?.toString()=="붙여넣기")return n
                    for(i in 0 until n.childCount)n.getChild(i)?.let { findPaste(it)?.let { found -> return found } }
                    return null
                }
                var tapped=false
                val menuDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                while(!tapped && System.nanoTime()<menuDeadline) {
                    val node=(automation.windows.mapNotNull { it.root }+listOfNotNull(automation.rootInActiveWindow))
                        .firstNotNullOfOrNull { findPaste(it) }
                    if(node!=null && node.isEnabled) {
                        val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                        val now=android.os.SystemClock.uptimeMillis()
                        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                            val e=MotionEvent.obtain(now,now,action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                            e.source=InputDevice.SOURCE_TOUCHSCREEN;automation.injectInputEvent(e,true);e.recycle()
                        }
                        tapped=true
                    }
                    Thread.sleep(100)
                }
                assertTrue("Paste menu unavailable",tapped)
                scenario.onActivity { model.sendKey("\r") }
                await("Context-menu paste did not reach SSH") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="MENU_PASTE_OK" } }
                scenario.onActivity { model.sendKey("printf 'SLEEP_READY\\n'; sleep 30\r") }
                await("Sleep command not ready") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="SLEEP_READY" } }
                scenario.onActivity { a ->
                    model.terminal.receive("\u001b[2J\u001b[HCOPY한글 other".toByteArray())
                    drag(v,0.2f,7.2f)
                    a.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("fixture","CTRL_C_MUST_NOT_COPY"))
                    shortcut(v,KeyEvent.KEYCODE_C,false);model.sendKey("printf 'INTERRUPT_OK\\n'\r")
                    assertEquals("CTRL_C_MUST_NOT_COPY",a.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
                }
                await("Ctrl+C no longer interrupts terminal commands") { model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="INTERRUPT_OK" } }
            } finally { scenario.onActivity { a -> model.disconnect();model.delete(profile);a.getSystemService(ClipboardManager::class.java).clearPrimaryClip() } }
        }
    }
}
