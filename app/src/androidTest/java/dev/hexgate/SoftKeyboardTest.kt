package dev.hexgate

import android.view.View
import android.view.ViewGroup
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test

class SoftKeyboardTest {
    private fun terminal(v:View):TerminalView? {
        if(v is TerminalView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)terminal(v.getChildAt(i))?.let{return it}
        return null
    }
    @Test fun nativeTerminalCanReceiveTouchFocus() {
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity {
                val view=requireNotNull(terminal(it.window.decorView))
                assertTrue("Terminal must accept focus without a physical keyboard",view.isFocusableInTouchMode)
                assertTrue("Terminal must become the IME target",view.requestFocus())
            }
        }
    }

    @Test fun touchAndKeyboardButtonOpenRealImeAndSendSshText() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val args=androidx.test.platform.app.InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        val profile=Profile(name="IME fixture",host=args.getString("sshHost")!!,port=22222,
            user=args.getString("sshUser","testuser"),herdrSession="hexgate-ime-${System.nanoTime()}")
        val key=java.io.File("/data/local/tmp/hexgate-test-key").readText()
        val fingerprint=requireNotNull(args.getString("sshFingerprint"))
        launchHexgateTestActivity().use { scenario ->
            lateinit var model:TerminalModel
            lateinit var view:TerminalView
            fun await(message:String,predicate:()->Boolean) {
                val end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(25)
                var ready=false
                while(System.nanoTime()<end && !ready) {
                    scenario.onActivity { ready=predicate() };Thread.sleep(100)
                }
                assertTrue(message,ready)
            }
            fun imeVisible()=androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
            fun capture(name:String) {
                val image=instrumentation.uiAutomation.takeScreenshot()
                java.io.File(instrumentation.targetContext.cacheDir,name).outputStream().use {
                    image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                };image.recycle()
            }
            try {
                scenario.onActivity {
                    model=it.model;model.store.trust(profile.host,profile.port,fingerprint)
                    model.save(profile,key);model.connect(profile)
                    view=requireNotNull(terminal(it.window.decorView))
                }
                var prompts=0
                await("Herdr shell did not settle") {
                    val text=model.terminal.emulator.screen.transcriptText
                    prompts=if(text.lines().any { it.contains(profile.user) && it.trimEnd().endsWith("$") })prompts+1 else 0
                    model.connected && model.terminal.emulator.isAlternateBufferActive && text.contains("agents") && prompts>=3
                }
                scenario.onActivity { androidx.core.view.ViewCompat.getWindowInsetsController(view)?.hide(androidx.core.view.WindowInsetsCompat.Type.ime()) }
                await("IME baseline did not hide") { !imeVisible() }
                Thread.sleep(400) // Finish the Android insets animation before measuring rows.
                var rows=0
                scenario.onActivity {
                    rows=model.terminal.emulator.mRows
                    java.io.File(instrumentation.targetContext.cacheDir,"ime-geometry-before.txt").writeText("rows=$rows height=${view.height} ime="+androidx.core.view.ViewCompat.getRootWindowInsets(view)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()))
                    val now=android.os.SystemClock.uptimeMillis()
                    for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                        val event=android.view.MotionEvent.obtain(now,now+30,action,100f,100f,0)
                        event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                        view.dispatchTouchEvent(event);event.recycle()
                    }
                }
                await("Touch did not open Android IME") { imeVisible() && view.hasFocus() }
                // Fresh emulator Gboard may display its stylus tutorial instead of the docked IME.
                Thread.sleep(600)
                fun named(node:android.view.accessibility.AccessibilityNodeInfo,label:String):android.view.accessibility.AccessibilityNodeInfo? {
                    if(node.text?.toString()==label)return node
                    for(j in 0 until node.childCount)node.getChild(j)?.let { named(it,label)?.let { found->return found } }
                    return null
                }
                instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                    if(named(root,"Try out your stylus")!=null) {
                        var cancel=named(root,"Cancel")
                        while(cancel!=null && !cancel.isClickable)cancel=cancel.parent
                        cancel?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }

                Thread.sleep(600)
                capture("ime-diagnostic.png")
                scenario.onActivity { java.io.File(instrumentation.targetContext.cacheDir,"ime-geometry-after.txt").writeText("rows=${model.terminal.emulator.mRows} height=${view.height} ime="+androidx.core.view.ViewCompat.getRootWindowInsets(view)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())) }
                await("Keyboard did not resize SSH terminal") { model.connected && model.terminal.emulator.mRows<rows }
                capture("ime-touch.png")
                scenario.onActivity {
                    val input=requireNotNull(view.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    input.commitText("printf 'IME_TOUCH_한글\\n'",1)
                    input.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER))
                }
                await("IME text did not reach real SSH") {
                    model.terminal.emulator.screen.transcriptText.lines().any { it.contains("IME_TOUCH_한글") && !it.contains("printf") }
                }
                scenario.onActivity { androidx.core.view.ViewCompat.getWindowInsetsController(view)?.hide(androidx.core.view.WindowInsetsCompat.Type.ime()) }
                await("IME did not hide") { !imeVisible() }
                fun find(node:android.view.accessibility.AccessibilityNodeInfo):android.view.accessibility.AccessibilityNodeInfo? {
                    if(node.text?.toString()=="Keyboard")return node
                    for(j in 0 until node.childCount)node.getChild(j)?.let { find(it)?.let { found->return found } }
                    return null
                }
                var node=find(requireNotNull(instrumentation.uiAutomation.rootInActiveWindow))
                while(node!=null && !node.isClickable)node=node.parent
                assertTrue("Keyboard action missing",node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true)
                await("Keyboard button did not reopen IME") { imeVisible() && view.hasFocus() && model.connected }
                capture("ime-button.png")
            } finally {
                scenario.onActivity { model.disconnect();model.delete(profile) }
                val cleanup=SshConnection()
                try {
                    cleanup.connect(profile.copy(herdrSession=""),key,"") { it==fingerprint }
                    cleanup.exec("\"\$HOME/.local/bin/herdr\" --session "+SshConnection.quote(profile.herdrSession)+" server stop")
                } finally { cleanup.shutdown() }
            }
        }
    }
}
