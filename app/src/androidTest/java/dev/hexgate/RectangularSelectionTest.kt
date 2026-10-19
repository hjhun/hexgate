package dev.hexgate

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.*
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test

class RectangularSelectionTest {
    private fun terminal(v:View):TerminalView? {
        if(v is TerminalView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)terminal(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun drag(v:TerminalView,x1:Float,y1:Int,x2:Float,y2:Int,rect:Boolean) {
        val p=MotionEvent.PointerProperties().apply { id=0;toolType=MotionEvent.TOOL_TYPE_MOUSE }
        val now=android.os.SystemClock.uptimeMillis()
        for(step in 0..8) {
            val action=when(step) {0->MotionEvent.ACTION_DOWN;8->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
            val coords=MotionEvent.PointerCoords().apply {
                x=(x1+(x2-x1)*step/8)*v.mRenderer.fontWidth
                y=(y1+0.5f+(y2-y1)*step/8f)*v.mRenderer.fontLineSpacing
            }
            val modifiers=if(rect && step==0)KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON else 0
            val e=MotionEvent.obtain(now,now+step*20,action,1,arrayOf(p),arrayOf(coords),modifiers,
                if(step==8)0 else MotionEvent.BUTTON_PRIMARY,1f,1f,0,0,InputDevice.SOURCE_MOUSE,0)
            assertTrue(v.dispatchTouchEvent(e));e.recycle()
        }
    }
    @Test fun ctrlShiftDragHighlightsAndCopiesOnlyTheRectangleAcrossPanelRows() {
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { a ->
                val v=requireNotNull(terminal(a.window.decorView))
                a.model.terminal.receive("\u001b[2J\u001b[HLEFT1 | ONE   | RIGHT1\r\nLEFT2 | TWO   | RIGHT2\r\nLEFT3 | THREE | RIGHT3".toByteArray())
                fun image():Bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
                val before=image()
                drag(v,8.2f,0,12.2f,2,true)
                assertEquals("ONE\nTWO\nTHREE",v.selectedText)
                val selected=image()
                val line=v.mRenderer.fontLineSpacing
                val rowY=(line*1.9f).toInt()
                for(col in listOf(1,4,16,20)) {
                    val x=((col+0.5f)*v.mRenderer.fontWidth).toInt()
                    assertEquals("Rectangle highlighted adjacent panel at column $col",before.getPixel(x,rowY),selected.getPixel(x,rowY))
                }
                var changed=false
                for(y in line+2 until line*2-2)for(x in (9*v.mRenderer.fontWidth).toInt() until (11*v.mRenderer.fontWidth).toInt())
                    if(before.getPixel(x,y)!=selected.getPixel(x,y))changed=true
                assertTrue("Rectangle selection did not paint the middle row",changed)
                before.recycle();selected.recycle()
                val now=android.os.SystemClock.uptimeMillis()
                v.dispatchKeyEvent(KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_C,0,KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
                assertEquals("ONE\nTWO\nTHREE",a.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
                drag(v,12.2f,2,8.2f,0,true)
                assertEquals("ONE\nTWO\nTHREE",v.selectedText)
                drag(v,8.2f,0,12.2f,2,false)
                assertTrue("Regular drag must retain linear selection",v.selectedText!!.contains("RIGHT1"))
                assertTrue(v.selectedText!!.contains("LEFT2"))
                a.getSystemService(ClipboardManager::class.java).clearPrimaryClip()
            }
        }
    }
    @Test fun rectangularSelectionCopiesWholeKoreanCharactersAtCellBoundaries() {
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { a ->
                val v=requireNotNull(terminal(a.window.decorView))
                a.model.terminal.receive("\u001b[2J\u001b[HAAAA | 한글A | RIGHT\r\nBBBB | 한글B | RIGHT".toByteArray())
                drag(v,8.2f,0,10.2f,1,true)
                assertEquals("한글\n한글",v.selectedText)
            }
        }
    }
}
