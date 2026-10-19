package dev.hexgate

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FoldPostureTest {
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { it.readBytes().toString(Charsets.UTF_8) }

    @Test fun closeOpenAndRotationKeepTheSshSessionAndResizeTerminal() {
        val args=InstrumentationRegistry.getArguments()
        val states=shell("cmd device_state print-states")
        val closed=Regex("identifier=(\\d+), name='CLOSED'").find(states)?.groupValues?.get(1)
        val opened=Regex("identifier=(\\d+), name='OPENED'").find(states)?.groupValues?.get(1)
        org.junit.Assume.assumeNotNull(closed,opened,args.getString("sshHost"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val key=File("/data/local/tmp/hexgate-test-key").readText()
        val fingerprint=requireNotNull(args.getString("sshFingerprint"))
        val profile=Profile(name="Fold posture fixture",host=args.getString("sshHost")!!,port=22222,
            user=args.getString("sshUser","testuser"),herdrSession="hexgate-fold-${System.currentTimeMillis()}")
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            var openWidth=0
            var openColumns=0
            fun await(message: String, predicate: (MainActivity) -> Boolean) {
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                var ready=false
                while(System.nanoTime()<deadline && !ready) { scenario.onActivity { ready=predicate(it) };Thread.sleep(100) }
                assertTrue(message,ready)
            }
            fun screenshot(name: String) {
                Thread.sleep(500)
                val image=instrumentation.uiAutomation.takeScreenshot()
                File(context.cacheDir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                image.recycle()
            }
            try {
                shell("cmd device_state state $opened")
                scenario.onActivity { a -> model=a.model;model.store.trust(profile.host,profile.port,fingerprint);model.save(profile,key);model.connect(profile) }
                await("Herdr did not connect") { a -> a.model.connected && a.model.terminal.emulator.screen.transcriptText.lines().any { it.contains(profile.user) && it.trimEnd().lastOrNull() in listOf('$','#','%') } }
                var stablePrompt=0
                await("Herdr prompt was not stable") {
                    stablePrompt=if(model.terminal.emulator.isAlternateBufferActive && model.terminal.emulator.screen.transcriptText.contains("agents"))stablePrompt+1 else 0
                    stablePrompt>=3
                }
                scenario.onActivity { model.sendKey("printf 'FOLD_KEEP_한글\\n'\r") }
                await("Persistent marker did not reach Herdr") { model.terminal.emulator.screen.transcriptText.lines().any { it.contains("FOLD_KEEP_한글") && !it.contains("printf") } }
                fun reconnectAndRecreate(label:String) {
                    val previous=model.terminal
                    scenario.onActivity { model.disconnect();model.connect(profile) }
                    await("$label reconnect lost Herdr output") {
                        model.connected && model.terminal!==previous && model.terminal.emulator.isAlternateBufferActive &&
                            model.terminal.emulator.screen.transcriptText.lines().any { it.contains("FOLD_KEEP_한글") && !it.contains("printf") }
                    }
                    val session=model.terminal
                    scenario.recreate()
                    await("$label recreation lost terminal ownership") { a ->
                        fun find(v:android.view.View):com.termux.view.TerminalView? {
                            if(v is com.termux.view.TerminalView)return v
                            if(v is android.view.ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it}
                            return null
                        }
                        val v=find(a.window.decorView)
                        a.model===model && model.connected && model.terminal===session && v!=null &&
                            session.emulator.mColumns==kotlin.math.max(4,(v.width/v.mRenderer.fontWidth).toInt())
                    }
                    screenshot("fold-$label-reconnect.png")
                }
                scenario.onActivity { a -> openWidth=a.resources.configuration.screenWidthDp;openColumns=model.terminal.emulator.mColumns }
                screenshot("fold-open.png")
                scenario.onActivity { model.togglePreview() }
                await("Selected-pane preview did not open") { a -> !a.model.previewBusy && a.model.selectedPane!=null && a.model.terminal.emulator.mColumns<openColumns }
                val fixtureRoot=args.getString("fixtureRoot")
                if(fixtureRoot!=null) {
                    scenario.onActivity { model.openDocument(fixtureRoot+"/preview.md") }
                    var rendered=false
                    fun preview(v: android.view.View): DocumentPreview? {
                        if(v is DocumentPreview)return v
                        if(v is android.view.ViewGroup)for(i in 0 until v.childCount)preview(v.getChildAt(i))?.let { return it }
                        return null
                    }
                    await("Embedded preview did not render alongside Herdr") { a ->
                        preview(a.window.decorView)?.evaluateJavascript("window.previewReady===true") { rendered=it=="true" }
                        rendered
                    }
                }
                var splitColumns=0
                var pixelsWide=0
                var pixelsHigh=0
                scenario.onActivity { a -> splitColumns=model.terminal.emulator.mColumns;pixelsWide=a.resources.displayMetrics.widthPixels;pixelsHigh=a.resources.displayMetrics.heightPixels }
                val downTime=android.os.SystemClock.uptimeMillis()
                for(step in 0..10) {
                    val action=when(step) { 0->android.view.MotionEvent.ACTION_DOWN;10->android.view.MotionEvent.ACTION_UP;else->android.view.MotionEvent.ACTION_MOVE }
                    val event=android.view.MotionEvent.obtain(downTime,android.os.SystemClock.uptimeMillis(),action,pixelsWide*(0.6f+0.012f*step),pixelsHigh*0.5f,0)
                    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.uiAutomation.injectInputEvent(event,true);event.recycle();Thread.sleep(16)
                }
                await("Dragging the split divider did not resize the terminal") { a -> a.model.connected && a.model.terminal.emulator.mColumns>splitColumns }
                screenshot("fold-open-preview.png")
                scenario.onActivity { model.togglePreview() }
                await("Closing preview did not restore terminal width") { a -> a.model.terminal.emulator.mColumns==openColumns }
                shell("cmd device_state state $closed")
                shell("input keyevent 224");shell("wm dismiss-keyguard")
                await("Closing did not resize the connected terminal") { a -> a.model===model && a.model.connected && a.resources.configuration.screenWidthDp<openWidth && a.model.terminal.emulator.mColumns<openColumns }
                screenshot("fold-closed.png")
                reconnectAndRecreate("closed")
                shell("cmd device_state state $opened")
                await("Opening lost the SSH session") { a -> a.model===model && a.model.connected && a.resources.configuration.screenWidthDp==openWidth }
                reconnectAndRecreate("open")
                shell("wm user-rotation lock 1")
                await("Rotation lost the SSH session or window resize") { a -> a.model===model && a.model.connected && a.resources.configuration.screenWidthDp!=openWidth }
                screenshot("fold-rotated.png")
                shell("wm user-rotation lock 0")
                await("Restoring rotation lost the session") { a -> a.model===model && a.model.connected && a.resources.configuration.screenWidthDp==openWidth }
                scenario.onActivity { model.terminal.emulator.paste("printf 'HEXGATE_FOLD_RESUME_OK\\n'");model.sendKey("\r") }
                await("The folded/unfolded connection no longer accepts input") { a -> a.model.terminal.emulator.screen.transcriptText.lines().any { it.contains("HEXGATE_FOLD_RESUME_OK") && !it.contains("printf") } }
            } finally {
                shell("cmd device_state state reset");shell("wm user-rotation free")
                scenario.onActivity { model.disconnect();model.delete(profile) }
                val cleanup=SshConnection()
                try { cleanup.connect(profile.copy(herdrSession=""),key,"") { it==fingerprint };cleanup.exec("\"\$HOME/.local/bin/herdr\" --session "+SshConnection.quote(profile.herdrSession)+" server stop") }
                finally { cleanup.shutdown() }
            }
        }
    }
}
