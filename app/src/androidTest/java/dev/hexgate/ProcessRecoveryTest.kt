package dev.hexgate

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Run seed and verify as separate instrumentation processes with force-stop between them. */
@RunWith(AndroidJUnit4::class)
class ProcessRecoveryTest {
    @Test fun recoverStoredProfileKeyAndRemoteHerdrAfterProcessDeath() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        val stage=args.getString("recoveryStage")
        org.junit.Assume.assumeTrue(stage=="seed" || stage=="verify")
        val context=instrumentation.targetContext
        val store=ProfileStore(context)
        val fixture=context.getSharedPreferences("process-recovery-fixture",0)
        val host=requireNotNull(args.getString("sshHost"))
        val fingerprint=requireNotNull(args.getString("sshFingerprint"))
        val key=File("/data/local/tmp/hexgate-test-key").readText()
        val profile=if(stage=="seed") {
            store.profiles().find { it.id=="hexgate-process-recovery-fixture" }?.let { old ->
                val cleanup=SshConnection()
                try {
                    cleanup.connect(old.copy(herdrSession=""),key,"") { it==fingerprint }
                    try { cleanup.exec("\"\$HOME/.local/bin/herdr\" --session "+SshConnection.quote(old.herdrSession)+" server stop") } catch (_: Exception) {}
                } finally { cleanup.shutdown();store.delete(old) }
            }
            fixture.edit().putInt("seedPid",android.os.Process.myPid())
                .putString("lastBefore",store.lastProfile()?.id)
                .putString("pinBefore",store.trusted(host,22222)).commit()
            Profile(id="hexgate-process-recovery-fixture",name="Process recovery fixture",host=host,port=22222,
                user=args.getString("sshUser","testuser"),herdrSession="hexgate-recovery-${System.currentTimeMillis()}").also {
                store.save(it,key);store.saveLastProfile(it)
                store.saveRecentFiles(it.id,listOf("/fixture/recent.md"));store.trust(host,22222,fingerprint)
            }
        } else {
            assertNotEquals("The app process must actually change",fixture.getInt("seedPid",-1),android.os.Process.myPid())
            requireNotNull(store.lastProfile()).also {
                assertEquals("hexgate-process-recovery-fixture",it.id)
                assertEquals(key,store.privateKey(it))
                assertEquals(listOf("/fixture/recent.md"),store.recentFiles(it.id))
            }
        }
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            scenario.onActivity { a -> model=a.model;model.reconnect() }
            try {
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
                var connected=false
                var promptObservations=0
                while(System.nanoTime()<deadline && !connected) {
                    scenario.onActivity {
                        val prompt=model.terminal.emulator.screen.transcriptText.lines().any { it.contains(profile.user) && it.trimEnd().lastOrNull() in listOf('$','#','%') }
                        promptObservations=if(prompt)promptObservations+1 else 0
                        connected=model.connected && model.terminal.emulator.isAlternateBufferActive && promptObservations>=2
                    }
                    Thread.sleep(100)
                }
                assertTrue("Stored profile could not reconnect",connected)
                if(stage=="seed") scenario.onActivity {
                    model.terminal.emulator.paste("printf 'HEXGATE_PROCESS_RECOVERY\\n'")
                    model.sendKey("\r")
                }
                val markerDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                var marker=false
                while(System.nanoTime()<markerDeadline && !marker) {
                    scenario.onActivity { marker=model.terminal.emulator.screen.transcriptText.lines().any { it.contains("HEXGATE_PROCESS_RECOVERY") && !it.contains("printf") } }
                    Thread.sleep(100)
                }
                scenario.onActivity { File(context.cacheDir,"recovery-buffer.txt").writeText(model.terminal.emulator.screen.transcriptText) }
                assertTrue("The remote Herdr pane was not retained",marker)
                if(stage=="verify") {
                    val bitmap=instrumentation.uiAutomation.takeScreenshot()
                    File(context.cacheDir,"process-recovery.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                    scenario.onActivity { assertEquals(listOf("/fixture/recent.md"),model.recentFiles) }
                }
            } finally {
                scenario.onActivity { model.disconnect() }
                if(stage=="verify") {
                    val cleanup=SshConnection()
                    try {
                        cleanup.connect(profile.copy(herdrSession=""),key,"") { it==fingerprint }
                        cleanup.exec("\"\$HOME/.local/bin/herdr\" --session "+SshConnection.quote(profile.herdrSession)+" server stop")
                    } finally {
                        cleanup.shutdown();store.delete(profile)
                        fixture.getString("lastBefore",null)?.let { old -> store.profiles().find { it.id==old }?.let { store.saveLastProfile(it) } }
                        fixture.getString("pinBefore",null)?.let { store.trust(host,22222,it) } ?: store.forgetTrust(host,22222)
                        fixture.edit().clear().commit()
                    }
                }
            }
        }
    }
}
