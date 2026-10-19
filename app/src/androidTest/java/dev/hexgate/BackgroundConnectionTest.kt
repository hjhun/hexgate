package dev.hexgate

import android.app.ActivityManager
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class BackgroundConnectionTest {
    private fun click(label: String) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
            for(i in 0 until node.childCount)node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<deadline) {
            var node=automation.rootInActiveWindow?.let { find(it) }
            while(node!=null) {
                if(node.isClickable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return
                node=node.parent
            }
            Thread.sleep(100)
        }
        fail("UI action missing: $label")
    }

    @Test fun batteryPermissionButtonRequestsSystemConsent() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val power=context.getSystemService(android.os.PowerManager::class.java)
        val previous=power.isIgnoringBatteryOptimizations(context.packageName)
        org.junit.Assume.assumeFalse(previous)
        try {
            launchHexgateTestActivity().use {
                click("Open menu");click("Settings");click("Background connection permission")
                click("Allow")
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
                while(!power.isIgnoringBatteryOptimizations(context.packageName) && System.nanoTime()<deadline)Thread.sleep(100)
                assertTrue("System battery consent was not granted",power.isIgnoringBatteryOptimizations(context.packageName))
            }
        } finally {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("dumpsys deviceidle whitelist -dev.hexgate")).use { it.readBytes() }
        }
    }

    @Test fun backgroundAndNewWindowRetainSshAndRefreshPreservesPendingInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        val profile = Profile(name="Background fixture", host=args.getString("sshHost")!!,
            port=22222, user=args.getString("sshUser", "testuser"))
        val key = File("/data/local/tmp/hexgate-test-key").readText()
        var scenario = launchHexgateTestActivity()
        lateinit var model: TerminalModel
        fun await(message: String, predicate: () -> Boolean) {
            var ready = false
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
            while (!ready && System.nanoTime() < end) {
                instrumentation.runOnMainSync { ready = predicate() }
                Thread.sleep(100)
            }
            assertTrue(message, ready)
        }
        fun serviceRunning() = context.getSystemService(ActivityManager::class.java)
            .getRunningServices(50).any { it.service.className == ConnectionService::class.java.name && it.foreground }
        try {
            scenario.onActivity {
                model=it.model
                model.store.trust(profile.host,profile.port,args.getString("sshFingerprint")!!)
                model.save(profile,key);model.connect(profile)
            }
            await("SSH did not connect") { model.connected && model.terminal.emulator.screen.transcriptText.contains(profile.user) }
            scenario.onActivity { model.sendKey("export HEXGATE_LIVE_PID=\$\$; printf 'BEFORE:%s\\n' \"\$HEXGATE_LIVE_PID\"\r") }
            await("Shell marker missing") { Regex("(?m)^BEFORE:[0-9]+\\s*$").containsMatchIn(model.terminal.emulator.screen.transcriptText) }
            var pid=""
            scenario.onActivity { pid=Regex("(?m)^BEFORE:([0-9]+)\\s*$").find(model.terminal.emulator.screen.transcriptText)!!.groupValues[1] }
            assertTrue("Active SSH has no foreground service",serviceRunning())
            val terminal=model.terminal
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            Thread.sleep(20000)
            instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            instrumentation.runOnMainSync { assertTrue(model.connected);assertSame(terminal,model.terminal) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.close() // Finish the window, beyond ordinary Home/background or rotation.
            scenario=launchHexgateTestActivity()
            scenario.onActivity { assertSame("Reopening replaced the active connection",model,it.model);assertTrue(model.connected) }
            // Leave an incomplete command in the remote line editor. Refresh must not send Ctrl-L/Enter.
            scenario.onActivity { model.sendKey("printf 'AFTER:%s\\n' \"\$HEXGATE_LIVE_PID\""); repeat(3) { model.refreshTerminal() } }
            click("Refresh terminal")
            Thread.sleep(1000)
            scenario.onActivity { model.sendKey("\r") }
            await("Refresh changed the SSH shell or damaged pending input") {
                Regex("(?m)^AFTER:${pid}\\s*$").containsMatchIn(model.terminal.emulator.screen.transcriptText)
            }
            scenario.onActivity { model.sendKey("stty size\r") }
            await("Refresh did not restore the final PTY size") {
                Regex("(?m)^${model.terminal.emulator.mRows} ${model.terminal.emulator.mColumns}\\s*$")
                    .containsMatchIn(model.terminal.emulator.screen.transcriptText)
            }
            // Exercise the same explicit action as the ongoing notification's Disconnect button.
            context.startService(Intent(context,ConnectionService::class.java).setAction("dev.hexgate.DISCONNECT"))
            await("Disconnect action retained the SSH connection") { !model.connected && !model.connecting }
            await("Foreground service remained after disconnect") { !serviceRunning() }
        } finally {
            instrumentation.runOnMainSync { model.disconnect();model.delete(profile) }
            scenario.close()
        }
    }
}
