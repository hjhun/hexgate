package dev.hexgate

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanCompositionTest {
    private fun question(action: Int): KeyEvent {
        val now = SystemClock.uptimeMillis()
        return KeyEvent(now, now, action, KeyEvent.KEYCODE_SLASH, 0,
            KeyEvent.META_SHIFT_ON, KeyCharacterMap.VIRTUAL_KEYBOARD, 0)
    }

    @Test fun keyEventPunctuationFollowsPendingKoreanSyllable() {
        val sent = java.io.ByteArrayOutputStream()
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                val session = TerminalSession(object : TerminalSession.Transport {
                    override fun send(bytes: ByteArray) { sent.write(bytes) }
                    override fun resize(columns: Int, rows: Int) {}
                }, QuietSessionClient())
                val view = TerminalView(activity, null)
                view.setTextSize(20); view.setTerminalViewClient(activity.model.viewClient(view))
                view.attachSession(session); activity.setContentView(view); view.layout(0, 0, 800, 600); view.requestFocus()
                val input = requireNotNull(view.onCreateInputConnection(EditorInfo()))
                input.commitText("할까", 1)
                input.setComposingText("요", 1)
                view.dispatchKeyEvent(question(KeyEvent.ACTION_DOWN))
                view.dispatchKeyEvent(question(KeyEvent.ACTION_UP))
                input.finishComposingText()
                input.finishComposingText()
            }
            assertEquals("할까요?", sent.toString("UTF-8"))
        }
    }

    @Test fun enterFollowsPendingKoreanSyllable() {
        val sent = java.io.ByteArrayOutputStream()
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                val session = TerminalSession(object : TerminalSession.Transport {
                    override fun send(bytes: ByteArray) { sent.write(bytes) }
                    override fun resize(columns: Int, rows: Int) {}
                }, QuietSessionClient())
                val view = TerminalView(activity, null)
                view.setTextSize(20); view.setTerminalViewClient(activity.model.viewClient(view))
                view.attachSession(session); view.layout(0, 0, 800, 600)
                val input = requireNotNull(view.onCreateInputConnection(EditorInfo()))
                input.commitText("할까", 1); input.setComposingText("요", 1)
                view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                input.finishComposingText()
            }
            assertEquals("할까요\r", sent.toString("UTF-8"))
        }
    }
}
