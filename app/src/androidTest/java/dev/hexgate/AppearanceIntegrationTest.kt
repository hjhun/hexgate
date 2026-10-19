package dev.hexgate

import android.graphics.Bitmap
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AppearanceIntegrationTest {
    @Test fun bundledFontsKeepAsciiCellsAndRequiredGlyphs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TerminalFonts.choices.forEach { font ->
            val paint = Paint().apply { typeface = TerminalFonts.load(context, font.id); textSize = 32f }
            val cell = paint.measureText("0")
            "iW1l|┌─┐".forEach { assertEquals("${font.label}: cell width for $it",cell,paint.measureText(it.toString()),0.05f) }
            assertTrue("${font.label}: Powerline glyph missing",paint.hasGlyph(""))
            assertTrue("${font.label}: terminal icon missing",paint.hasGlyph(""))
            assertTrue("${font.label}: Korean fallback missing",paint.hasGlyph("한"))
        }
    }

    @Test fun changingFacesKeepsLiveSshAndPersistsAcrossRecreation() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeNotNull(args.getString("sshHost"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val key = File("/data/local/tmp/hexgate-test-key").readText()
        val fingerprint = requireNotNull(args.getString("sshFingerprint"))
        val profile = Profile(name = "Hexgate UI verification", host = args.getString("sshHost")!!,port = 22222,
            user = args.getString("sshUser","testuser"),herdrSession = "hexgate-appearance-${System.currentTimeMillis()}")
        val oldFont = ProfileStore(context).fontId()
        launchHexgateTestActivity().use { scenario ->
            lateinit var model: TerminalModel
            fun await(message: String, predicate: (MainActivity) -> Boolean) {
                val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(20)
                var ready = false
                while(System.nanoTime()<deadline && !ready) { scenario.onActivity { ready=predicate(it) }; Thread.sleep(50) }
                assertTrue(message,ready)
            }
            fun terminal(v: View): TerminalView? {
                if(v is TerminalView)return v
                if(v is ViewGroup)for(i in 0 until v.childCount)terminal(v.getChildAt(i))?.let { return it }
                return null
            }
            try {
                scenario.onActivity { model=it.model;model.store.trust(profile.host,profile.port,fingerprint);model.save(profile,key);model.connect(profile) }
                await("Herdr shell not ready") { it.model.connected && it.model.terminal.emulator.screen.transcriptText.lines().any { row -> row.contains(profile.user) && row.trimEnd().lastOrNull() in listOf('$','#','%') } }
                for(font in TerminalFonts.choices) {
                    val face = TerminalFonts.load(context,font.id)
                    scenario.onActivity { model.updateFont(font.id) }
                    await("Native face ${font.id} was not applied") { terminal(it.window.decorView)?.tag === face && it.model.connected }
                    // Font metrics resize the remote PTY; type only after the native face
                    // and terminal geometry have settled, rather than during SIGWINCH.
                    var previousColumns=-1
                    var stable=0
                    await("Font ${font.id} geometry did not settle") {
                        val columns=model.terminal.emulator.mColumns
                        stable=if(columns==previousColumns)stable+1 else 0
                        previousColumns=columns
                        stable>=3
                    }
                    scenario.onActivity { model.sendKey("printf 'FONT_${font.id} 0O 1lI 한글 ┌─┐  \\n'\r") }
                    await("SSH font sample ${font.id} did not arrive") { it.model.terminal.emulator.screen.transcriptText.lines().any { row -> row.contains("FONT_${font.id}") && !row.contains("printf") } }
                    Thread.sleep(500)
                    val image=instrumentation.uiAutomation.takeScreenshot()
                    File(context.cacheDir,"appearance-${font.id}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
                }
                scenario.recreate()
                await("Recreation lost connection/font") { it.model===model && it.model.connected && it.model.fontId=="source" && ProfileStore(context).fontId()=="source" }
            } finally {
                scenario.onActivity { model.updateFont(oldFont);model.disconnect();model.delete(profile) }
                val cleanup=SshConnection()
                try { cleanup.connect(profile.copy(herdrSession=""),key,"") { it==fingerprint };cleanup.exec("\"\$HOME/.local/bin/herdr\" --session "+SshConnection.quote(profile.herdrSession)+" server stop") }
                finally { cleanup.shutdown() }
            }
        }
    }
}
