package dev.hexgate

import android.content.ClipboardManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test

class ContextCopyTest {
    @Test fun rightClickWhileSelectingCopiesOnlyTheSelectedText() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.serviceInfo=instrumentation.uiAutomation.serviceInfo.apply {
            flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun terminal(v:View):TerminalView? {
            if(v is TerminalView)return v
            if(v is ViewGroup)for(i in 0 until v.childCount)terminal(v.getChildAt(i))?.let { return it }
            return null
        }
        fun find(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
            if(n.text?.toString()=="복사")return n
            for(i in 0 until n.childCount)n.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        launchHexgateTestActivity().use { scenario ->
            lateinit var view:TerminalView
            var expected=""
            scenario.onActivity { a ->
                view=requireNotNull(terminal(a.window.decorView))
                a.model.terminal.receive("\u001b[2J\u001b[HCOPY한글 other text".toByteArray())
                val selection=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,
                    view.mRenderer.fontWidth*2,view.mRenderer.fontLineSpacing/2f,0)
                view.startTextSelectionMode(selection);selection.recycle()
                expected=requireNotNull(view.selectedText)
                assertEquals("COPY한글",expected)
            }
            Thread.sleep(500)
            scenario.onActivity {
                val properties=MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE }
                val coords=MotionEvent.PointerCoords().apply { x=view.mRenderer.fontWidth*2;y=view.mRenderer.fontLineSpacing/2f }
                val now=android.os.SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(now,now,action,1,arrayOf(properties),arrayOf(coords),0,
                        if(action==MotionEvent.ACTION_DOWN)MotionEvent.BUTTON_SECONDARY else 0,
                        1f,1f,0,0,InputDevice.SOURCE_MOUSE,0)
                    assertTrue(view.dispatchTouchEvent(event));event.recycle()
                }
            }
            Thread.sleep(500)
            val image=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(instrumentation.targetContext.cacheDir,"context-copy-menu.png").outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            image.recycle()
            var copied=false
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while(!copied && System.nanoTime()<deadline) {
                val roots=instrumentation.uiAutomation.windows.mapNotNull { it.root } +
                    listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                val node=roots.firstNotNullOfOrNull { find(it) }
                if(node!=null && node.isEnabled) {
                    val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                    if(!bounds.isEmpty) {
                        val now=android.os.SystemClock.uptimeMillis()
                        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                            val event=MotionEvent.obtain(now,now,action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                            event.source=InputDevice.SOURCE_TOUCHSCREEN
                            instrumentation.uiAutomation.injectInputEvent(event,true);event.recycle()
                        }
                        copied=true
                    }
                }
                Thread.sleep(100)
            }
            assertTrue("Right-click copy menu was not available during selection",copied)
            scenario.onActivity { a ->
                val clipboard=a.getSystemService(ClipboardManager::class.java)
                assertEquals(expected,clipboard.primaryClip!!.getItemAt(0).coerceToText(a).toString())
                clipboard.clearPrimaryClip()
                view.stopTextSelectionMode()
            }
        }
    }
}
