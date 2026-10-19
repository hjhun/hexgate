package dev.hexgate

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class FullScreenTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
        for(i in 0 until node.childCount)node.getChild(i)?.let { find(it,label)?.let { found -> return found } }
        return null
    }
    private fun click(label: String) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<end) {
            val root=instrumentation.uiAutomation.rootInActiveWindow
            // Android's first-use immersive tutorial can appear after a delayed recreation.
            var tutorial=root?.let { find(it,"Got it") }
            while(tutorial!=null) {
                if(tutorial.isClickable && tutorial.performAction(AccessibilityNodeInfo.ACTION_CLICK))break
                tutorial=tutorial.parent
            }
            var node=root?.let { find(it,label) }
            while(node!=null) {
                if(node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return
                node=node.parent
            }
            Thread.sleep(100)
        }
        fail("UI action missing: $label")
    }
    @Test fun immersiveBarsRestoreAcrossRecreationAndEnterRunsPendingCommand() {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        val profile=Profile(name="Immersive test", host=args.getString("sshHost")!!, port=22222,
            user=args.getString("sshUser","testuser"))
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            fun await(message:String, predicate:(MainActivity)->Boolean) {
                var ready=false
                val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                while(!ready && System.nanoTime()<end) { scenario.onActivity { ready=predicate(it) };Thread.sleep(100) }
                assertTrue(message,ready)
            }
            fun bars(activity:MainActivity, visible:Boolean):Boolean {
                val insets=ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return false
                return insets.isVisible(WindowInsetsCompat.Type.statusBars())==visible &&
                    insets.isVisible(WindowInsetsCompat.Type.navigationBars())==visible
            }
            fun screenshot(name:String) {
                val image=instrumentation.uiAutomation.takeScreenshot()
                File(instrumentation.targetContext.cacheDir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                image.recycle()
            }
            scenario.onActivity {
                model=it.model
                model.store.trust(profile.host,profile.port,args.getString("sshFingerprint")!!)
                model.save(profile,File("/data/local/tmp/hexgate-test-key").readText());model.connect(profile)
            }
            try {
                await("SSH prompt missing") { model.connected && model.terminal.emulator.screen.transcriptText.contains(profile.user) }
                await("Normal system bars missing") { bars(it,true) }
                val terminal=model.terminal
                var originalRows=0
                scenario.onActivity { originalRows=terminal.emulator.mRows }
                click("Enter full screen")
                await("Immersive mode did not hide status and navigation bars") { it.fullScreen && bars(it,false) && terminal.emulator.mRows>originalRows }
                screenshot("fullscreen-active.png")
                scenario.recreate()
                await("Recreation lost immersive mode or SSH") { it.fullScreen && bars(it,false) && it.model===model && model.connected && model.terminal===terminal }
                scenario.onActivity { model.sendKey("printf 'ACCESSORY_ENTER_OK\\n'") }
                // On a narrow window, scroll the actual accessory row rather than the screen edge.
                var width=0
                scenario.onActivity { width=it.window.decorView.width }
                val keyboard=instrumentation.uiAutomation.rootInActiveWindow?.let { find(it,"Keyboard") }
                val bounds=android.graphics.Rect()
                keyboard?.getBoundsInScreen(bounds)
                repeat(3) {
                    val enter=instrumentation.uiAutomation.rootInActiveWindow?.let { find(it,"Enter") }
                    if(enter?.isVisibleToUser!=true) {
                        val start=android.os.SystemClock.uptimeMillis()
                        for(step in 0..10) {
                            val action=when(step) { 0->MotionEvent.ACTION_DOWN;10->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE }
                            val event=MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),action,
                                width*(0.85f-0.07f*step),bounds.exactCenterY(),0)
                            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                            instrumentation.uiAutomation.injectInputEvent(event,true);event.recycle();Thread.sleep(20)
                        }
                    }
                }
                screenshot("fullscreen-enter.png")
                click("Enter")
                await("Enter accessory did not submit the SSH command") {
                    model.terminal.emulator.screen.transcriptText.lines().any { it.trim()=="ACCESSORY_ENTER_OK" }
                }
                click("Exit full screen")
                await("Window mode did not restore both system bars") { !it.fullScreen && bars(it,true) && model.connected && model.terminal===terminal }
                screenshot("fullscreen-restored.png")
            } finally { scenario.onActivity { model.disconnect();model.delete(profile) } }
        }
    }
}
