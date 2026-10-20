package dev.hexgate

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppSshIntegrationTest {
    @Test fun changedHostKeyRequiresExplicitApprovalAndRejectPreservesPin() {
        val args=InstrumentationRegistry.getArguments()
        val host=args.getString("sshHost")
        val fingerprint=args.getString("sshFingerprint")
        assumeNotNull(host,fingerprint)
        val key=File("/data/local/tmp/hexgate-test-key").readText()
        val profile=Profile(host=host!!,port=22222,user=args.getString("sshUser","testuser"))
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            var previous: String?=null
            scenario.onActivity { a ->
                model=a.model;previous=model.store.trusted(host,22222)
                model.store.trust(host,22222,"SHA256:intentionally-wrong-test-pin")
                model.save(profile,key);model.connect(profile)
            }
            try {
                var challenged=false
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                while(System.nanoTime()<deadline && !challenged) {
                    scenario.onActivity { model.challenge?.let { c ->
                        assertEquals("SHA256:intentionally-wrong-test-pin",c.previous)
                        assertEquals(fingerprint,c.fingerprint)
                        assertFalse(model.connected)
                        model.answerHost(false);challenged=true
                    } }
                    Thread.sleep(100)
                }
                assertTrue("Changed host key did not require approval",challenged)
                scenario.onActivity {
                    assertEquals("SHA256:intentionally-wrong-test-pin",model.store.trusted(host,22222))
                    assertFalse(model.connected)
                }
            } finally {
                scenario.onActivity {
                    model.disconnect();model.delete(profile)
                    if(previous==null)model.store.forgetTrust(host,22222) else model.store.trust(host,22222,previous!!)
                }
            }
        }
    }
    @Test fun actualHerdrRendersInsideTheNativeApp() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("sshHost")
        val fingerprint = args.getString("sshFingerprint")
        assumeNotNull(host, fingerprint)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionName="hexgate-emulator-${System.currentTimeMillis()}"
        val trace=args.getString("traceHerdr")=="true"
        val tracePath=args.getString("fixtureRoot")?.removeSuffix("/docs/fixtures")?.plus("/.tools/herdr-input-trace.txt")
        val profile = Profile(name="Herdr emulator integration", host=host!!, port=22222,
            user=args.getString("sshUser","testuser"),
            herdrSession=if(trace) "" else sessionName,
            command=if(trace) "exec strace -f -qq -s 1024 -e trace=read -e trace-fds=0 -o ${SshConnection.quote(requireNotNull(tracePath))} \"\$HOME/.local/bin/herdr\" --session $sessionName" else "")
        val key = File("/data/local/tmp/hexgate-test-key").readText()
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            scenario.onActivity { a -> model=a.model; model.save(profile,key); model.connect(profile) }
            try {
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
                var ready=false
                var promptObservations=0
                while(System.nanoTime()<deadline && !ready) {
                    scenario.onActivity {
                        model.challenge?.let { challenge -> assertEquals(fingerprint,challenge.fingerprint);model.answerHost(true) }
                        val text=model.terminal.emulator.screen.transcriptText
                        val prompt=text.lines().any { it.contains(profile.user) && it.trimEnd().lastOrNull() in listOf('$','#','%') }
                        promptObservations=if(prompt)promptObservations+1 else 0
                        ready=model.connected && model.terminal.emulator.isAlternateBufferActive && promptObservations>=2 && text.contains("spaces",ignoreCase=true) && text.contains("agents",ignoreCase=true)
                    }
                    Thread.sleep(100)
                }
                assertTrue("Real Herdr did not render its workspace UI",ready)
                val inputStart=System.nanoTime()
                scenario.onActivity { a ->
                    fun terminal(v: android.view.View): com.termux.view.TerminalView? {
                        if(v is com.termux.view.TerminalView)return v
                        if(v is android.view.ViewGroup) for(i in 0 until v.childCount)terminal(v.getChildAt(i))?.let { return it }
                        return null
                    }
                    val view=requireNotNull(terminal(a.window.decorView))
                    val input=requireNotNull(view.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    input.commitText("printf 'HEXGATE_IME_한글 ┌─┐ \\n'",1)
                    view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER))
                    view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_ENTER))
                }
                val inputDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                var inputReady=false
                while(System.nanoTime()<inputDeadline && !inputReady) {
                    scenario.onActivity { inputReady=model.terminal.emulator.screen.transcriptText.lines().any { it.contains("HEXGATE_IME_한글") && !it.contains("printf") } }
                    Thread.sleep(100)
                }
                scenario.onActivity { java.io.File(context.cacheDir,"ime-buffer.txt").writeText(model.terminal.emulator.screen.transcriptText) }
                val inputObservedMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inputStart)
                assertTrue("IME Unicode/hardware Enter did not reach the remote Herdr pane",inputReady)
                // Exercise Herdr's own pane extraction and OSC52 over the real SSH fixture.
                if (!trace) {
                    scenario.onActivity { a ->
                        fun find(v: android.view.View): com.termux.view.TerminalView? {
                            if (v is com.termux.view.TerminalView) return v
                            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
                            return null
                        }
                        val v = requireNotNull(find(a.window.decorView))
                        val em = model.terminal.emulator
                        assertTrue("Herdr mouse tracking missing", em.isMouseTrackingActive)
                        val row = (0 until em.mRows).first { r ->
                            val line = em.screen.getSelectedText(0, r, em.mColumns-1, r)
                            line.contains("HEXGATE_IME_") && !line.contains("printf")
                        }
                        val column = em.screen.getSelectedText(0, row, em.mColumns-1, row).indexOf("HEXGATE_IME_")
                        a.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("fixture", "not copied"))
                        val pointer = android.view.MotionEvent.PointerProperties().apply { id=0;toolType=android.view.MotionEvent.TOOL_TYPE_MOUSE }
                        val now = android.os.SystemClock.uptimeMillis()
                        for (step in 0..5) {
                            val action = when(step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 5 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                            val coords = android.view.MotionEvent.PointerCoords().apply {
                                x = (column + 0.2f + step * 2) * v.mRenderer.fontWidth
                                y = (row + 0.8f) * v.mRenderer.fontLineSpacing
                            }
                            val event = android.view.MotionEvent.obtain(now,now+step*20,action,1,arrayOf(pointer),arrayOf(coords),android.view.KeyEvent.META_SHIFT_ON,
                                if(step==5)0 else android.view.MotionEvent.BUTTON_PRIMARY,1f,1f,0,0,android.view.InputDevice.SOURCE_MOUSE,0)
                            v.dispatchTouchEvent(event);event.recycle()
                        }
                        assertFalse("Herdr selection was intercepted locally",v.isSelectingText)
                    }
                    var copied = false
                    val copyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (!copied && System.nanoTime() < copyDeadline) {
                        scenario.onActivity { a ->
                            copied = a.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.contains("HEXGATE") == true
                        }
                        Thread.sleep(50)
                    }
                    assertTrue("Shift pane selection did not copy Herdr OSC52 over SSH",copied)
                }
                Thread.sleep(500)
                val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(context.cacheDir,"herdr-tablet.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
                screenshot.recycle()
                val frameTimes=java.util.Collections.synchronizedList(mutableListOf<Long>())
                val frameThread=android.os.HandlerThread("Hexgate-test-frames").apply { start() }
                val listener=android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frameTimes.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION)) }
                scenario.onActivity { a -> a.window.addOnFrameMetricsAvailableListener(listener,android.os.Handler(frameThread.looper)) }
                val loadStart=System.nanoTime()
                try {
                    scenario.onActivity { model.sendKey("for i in \$(seq 1 10000); do printf 'HEXGATE_LOAD_%s 한글\\n' \"\$i\"; done; printf 'HEXGATE_LOAD_DONE\\n'\r") }
                    val loadDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
                    var loadDone=false
                    while(System.nanoTime()<loadDeadline && !loadDone) {
                        scenario.onActivity { loadDone=model.terminal.emulator.screen.transcriptText.lines().any { it.contains("HEXGATE_LOAD_DONE") && !it.contains("printf") } }
                        Thread.sleep(50)
                    }
                    assertTrue("10000-line output did not finish",loadDone)
                    val loadMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-loadStart)
                    val samples=synchronized(frameTimes) { frameTimes.toList() }
                    val metrics=org.json.JSONObject().put("inputRoundTripObservedMs",inputObservedMs).put("inputPollMs",100).put("lines",10000).put("loadObservedMs",loadMs).put("frameSamples",samples.size).put("framesOver16ms",samples.count { it>16666667 }).put("maxFrameMs",(samples.maxOrNull() ?: 0)/1e6).put("appPssKiB",android.os.Debug.getPss())
                    java.io.File(context.cacheDir,"performance.json").writeText(metrics.toString(2))
                } finally {
                    scenario.onActivity { a -> a.window.removeOnFrameMetricsAvailableListener(listener) }
                    frameThread.quitSafely()
                }
                if(trace) return@use
                scenario.onActivity { model.togglePreview() }
                val contextDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                var contextReady=false
                while(System.nanoTime()<contextDeadline && !contextReady) {
                    scenario.onActivity { contextReady=model.selectedPane!=null && !model.previewBusy }
                    Thread.sleep(100)
                }
                var contextError=""
                scenario.onActivity { contextError=model.previewError }
                assertTrue("Selected Herdr pane was not captured: $contextError",contextReady)
                val secondClient=SshConnection()
                try {
                    secondClient.connect(profile,key,"") { it==fingerprint }
                    Thread.sleep(1000)
                    scenario.onActivity { model.previewRequested=false;model.togglePreview() }
                    val ambiguousDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                    var ambiguous=false
                    while(System.nanoTime()<ambiguousDeadline && !ambiguous) {
                        scenario.onActivity { ambiguous=!model.previewBusy && model.paneChoices.isNotEmpty() && model.selectedPane==null }
                        Thread.sleep(100)
                    }
                    assertTrue("Multiple Herdr clients must disable automatic pane selection",ambiguous)
                } finally { secondClient.shutdown() }
                if(args.getString("networkFaults")=="true") {
                    fun shell(command: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
                    try {
                        shell("svc wifi disable")
                        val lossDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                        var lost=false
                        while(System.nanoTime()<lossDeadline && !lost) {
                            scenario.onActivity { lost=!model.connected && model.status.contains("Network changed") }
                            Thread.sleep(100)
                        }
                        assertTrue("Network loss did not update connection state",lost)
                    } finally { shell("svc wifi enable") }
                    val network=context.getSystemService(android.net.ConnectivityManager::class.java)
                    val availableDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                    while(System.nanoTime()<availableDeadline && network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)!=true)Thread.sleep(100)
                    assertTrue("Emulator Wi-Fi did not return",network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)==true)
                } else scenario.onActivity { model.disconnect() }
                scenario.onActivity { model.reconnect() }
                val reconnectDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                var reconnected=false
                while(System.nanoTime()<reconnectDeadline && !reconnected) {
                    scenario.onActivity { reconnected=model.connected && model.terminal.emulator.screen.transcriptText.contains("HEXGATE_LOAD_DONE") }
                    Thread.sleep(100)
                }
                assertTrue("Reconnection did not preserve the remote Herdr pane",reconnected)
                scenario.recreate()
                scenario.onActivity { a -> assertSame(model,a.model);assertTrue(a.model.connected) }
            } finally {
                // Detach the app, then stop only this test-created session.
                scenario.onActivity { model.disconnect(); model.delete(profile) }
                val cleanup=SshConnection()
                try {
                    cleanup.connect(profile.copy(command="",herdrSession=""),key,"") { it==fingerprint }
                    cleanup.exec("\"\$HOME/.local/bin/herdr\" --session " + SshConnection.quote(sessionName) + " server stop")
                } finally { cleanup.shutdown() }
            }
        }
    }
}
