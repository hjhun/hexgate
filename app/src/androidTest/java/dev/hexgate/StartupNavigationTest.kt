package dev.hexgate

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StartupNavigationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val args get()=InstrumentationRegistry.getArguments()
    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(context,MainActivity::class.java))
    private fun await(scenario: ActivityScenario<MainActivity>, message: String, predicate: (MainActivity)->Boolean) {
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25)
        var ready=false
        while(System.nanoTime()<end && !ready) { scenario.onActivity { ready=predicate(it) };Thread.sleep(50) }
        assertTrue(message,ready)
    }
    private fun click(label: String) {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
            for(i in 0 until node.childCount)node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
        while(System.nanoTime()<end) {
            var node=instrumentation.uiAutomation.rootInActiveWindow?.let { find(it) }
            while(node!=null) {
                if(node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return
                node=node.parent
            }
            Thread.sleep(100)
        }
        fail("UI action missing: $label")
    }
    private fun screenshot(name: String) {
        Thread.sleep(500)
        val image=instrumentation.uiAutomation.takeScreenshot()
        File(context.cacheDir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun preview(view: View): DocumentPreview? {
        if(view is DocumentPreview)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)preview(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun profile(suffix: String): Profile {
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        return Profile(name="Mobile workspace $suffix",host=args.getString("sshHost")!!,port=22222,
            user=args.getString("sshUser","testuser"),herdrSession="hexgate-startup-${System.nanoTime()}")
    }
    private fun cleanup(profile: Profile) {
        val connection=SshConnection()
        try {
            connection.connect(profile.copy(herdrSession=""),File("/data/local/tmp/hexgate-test-key").readText(),"") { it==args.getString("sshFingerprint") }
            connection.exec("\"\$HOME/.local/bin/herdr\" --session "+SshConnection.quote(profile.herdrSession)+" server stop")
        } finally { connection.shutdown() }
    }
    @Test fun launchConnectsLastProfileAndDrawerThemesPreserveTheWorkspace() {
        val profile=profile("theme fixture")
        val store=ProfileStore(context)
        val oldAuto=store.autoConnect();val oldTheme=store.themeId();val oldLast=store.lastProfile()
        store.save(profile,File("/data/local/tmp/hexgate-test-key").readText());store.saveLastProfile(profile);store.saveAutoConnect(true);store.saveThemeId("hexgate")
        store.trust(profile.host,profile.port,requireNotNull(args.getString("sshFingerprint")))
        try {
            launch().use { scenario ->
                lateinit var model: TerminalModel
                await(scenario,"Last profile did not auto-connect") { a -> model=a.model;a.model.connected && a.model.terminal.emulator.screen.transcriptText.lines().any { it.contains(profile.user) && it.trimEnd().lastOrNull() in listOf('$','#','%') } }
                var columns=0
                scenario.onActivity { columns=model.terminal.emulator.mColumns }
                click("Open menu");screenshot("panel-connections.png")
                click("Settings");screenshot("panel-settings.png")
                click("Light");click("Done")
                await(scenario,"Settings changed the SSH lifetime or PTY") { it.model===model && model.connected && model.themeId=="light" && model.terminal.emulator.mColumns==columns }
                click("Open menu");click("Preview")
                await(scenario,"Panel preview did not capture this Herdr pane") { model.previewRequested && !model.previewBusy && model.selectedPane!=null }
                scenario.onActivity { model.openDocument(requireNotNull(args.getString("fixtureRoot"))+"/preview.md") }
                for(id in listOf("light","amber","dracula","gruvbox","solarized","hexgate","system")) {
                    scenario.onActivity { model.updateTheme(id) }
                    var pageReady=false
                    var bodyColor=""
                    val systemDark=(context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val theme=AppThemes.resolve(id,systemDark)
                    val expected="rgb(${theme.background ushr 16 and 255}, ${theme.background ushr 8 and 255}, ${theme.background and 255})"
                    await(scenario,"Theme $id did not render the existing document") { a ->
                        preview(a.window.decorView)?.evaluateJavascript("JSON.stringify({ready:window.previewReady===true && Array.from(document.querySelectorAll('svg')).some(s=>s.textContent.includes('Android') && s.getBoundingClientRect().height>20) && Array.from(document.querySelectorAll('.mxgraph svg')).some(s=>s.getBoundingClientRect().height>20) && Array.from(document.images).some(i=>i.naturalWidth>0),bg:getComputedStyle(document.body).backgroundColor})") { value ->
                            if(value!="null")try {
                                val raw=org.json.JSONTokener(value).nextValue() as String
                                val json=org.json.JSONObject(raw);pageReady=json.optBoolean("ready");bodyColor=json.optString("bg")
                            } catch (_: Exception) {}
                        }
                        val barsMatch=if(android.os.Build.VERSION.SDK_INT>=30) {
                            val lightBars=(a.window.insetsController!!.systemBarsAppearance and android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0
                            lightBars==theme.light
                        } else true
                        barsMatch && model.connected && model.previewDocument?.path?.endsWith("preview.md")==true &&
                            model.terminal.emulator.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND]==theme.background && pageReady && bodyColor==expected
                    }
                    assertEquals(id,store.themeId())
                    if(id in listOf("light","amber","dracula"))screenshot("theme-$id.png")
                }
                scenario.onActivity { model.disconnect() }
                scenario.recreate()
                Thread.sleep(500)
                scenario.onActivity { assertSame(model,it.model);assertFalse(model.connected);assertFalse(model.connecting);assertNull(model.keyPromptProfile) }
            }
        } finally {
            store.delete(profile);store.saveAutoConnect(oldAuto);store.saveThemeId(oldTheme);oldLast?.let { store.saveLastProfile(it) };cleanup(profile)
        }
    }
    @Test fun encryptedStartupPromptsBeforeConnectingAndCanBeCancelled() {
        val profile=profile("encrypted fixture");val store=ProfileStore(context)
        val oldAuto=store.autoConnect();val oldLast=store.lastProfile()
        store.save(profile,File("/data/local/tmp/hexgate-test-key-encrypted").readText());store.saveLastProfile(profile);store.saveAutoConnect(true)
        store.trust(profile.host,profile.port,requireNotNull(args.getString("sshFingerprint")))
        try {
            launch().use { scenario ->
                lateinit var model: TerminalModel
                await(scenario,"Encrypted last key was not prompted") { a -> model=a.model;model.keyPromptProfile?.id==profile.id && !model.connected && !model.connecting }
                click("Cancel");scenario.recreate();Thread.sleep(500)
                scenario.onActivity { assertNull(model.keyPromptProfile);assertFalse(model.connecting);model.connectSaved(profile) }
                await(scenario,"Selecting the encrypted profile did not re-prompt") { model.keyPromptProfile?.id==profile.id }
                scenario.onActivity { model.answerKeyPassphrase("hexgate-fixture-only") }
                await(scenario,"Passphrase did not connect the saved profile") { model.connected && model.terminal.emulator.screen.transcriptText.lines().any { it.contains(profile.user) && it.trimEnd().lastOrNull() in listOf('$','#','%') } }
                val files=listOf("profiles","vault").map { File(context.applicationInfo.dataDir,"shared_prefs/$it.xml") }
                assertFalse(files.any { it.exists() && it.readText().contains("hexgate-fixture-only") })
                scenario.onActivity { model.disconnect() }
            }
        } finally { store.delete(profile);store.saveAutoConnect(oldAuto);oldLast?.let { store.saveLastProfile(it) };cleanup(profile) }
    }
    @Test fun disablingStartupReconnectLeavesSavedProfileIdle() {
        val profile=profile("manual fixture");val store=ProfileStore(context)
        val oldAuto=store.autoConnect();val oldLast=store.lastProfile()
        store.save(profile,File("/data/local/tmp/hexgate-test-key").readText());store.saveLastProfile(profile);store.saveAutoConnect(false)
        try {
            launch().use { scenario ->
                instrumentation.waitForIdleSync();Thread.sleep(500)
                scenario.onActivity { assertFalse(it.model.connected);assertFalse(it.model.connecting);assertNull(it.model.keyPromptProfile) }
            }
        } finally { store.delete(profile);store.saveAutoConnect(oldAuto);oldLast?.let { store.saveLastProfile(it) } }
    }
}
