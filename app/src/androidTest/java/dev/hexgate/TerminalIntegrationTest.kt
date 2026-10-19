package dev.hexgate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.TerminalSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TerminalIntegrationTest {
    @Test fun streamAdapterPreservesUnicodeCursorAndAlternateScreen() {
        val sent = ArrayList<ByteArray>()
        val sizes = ArrayList<Pair<Int,Int>>()
        val session = TerminalSession(object : TerminalSession.Transport {
            override fun send(bytes: ByteArray) { sent.add(bytes) }
            override fun resize(columns: Int, rows: Int) { sizes.add(columns to rows) }
        }, QuietSessionClient())
        session.updateSize(100, 30, 10, 20)
        session.receive("한글 ┌─┐\u001b[31mRED\u001b[0m".toByteArray())
        assertTrue(session.emulator.screen.transcriptText.contains("한글 ┌─┐RED"))
        assertEquals(11, session.emulator.cursorCol)
        session.receive("\u001b[?1049h\u001b[2J\u001b[HHerdr".toByteArray())
        assertTrue(session.emulator.isAlternateBufferActive)
        session.receive("\u001b[?1049l".toByteArray())
        assertFalse(session.emulator.isAlternateBufferActive)
        assertTrue(session.emulator.screen.transcriptText.contains("한글"))
        session.writeCodePoint(true, '가'.code)
        assertEquals("\u001b가", sent.last().toString(Charsets.UTF_8))
        assertEquals(100 to 30, sizes.last())
    }
    @Test fun nativeImeProducesOrderedUtf8AndHardwareEnter() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val sent=java.io.ByteArrayOutputStream()
        var writes=0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session=TerminalSession(object: TerminalSession.Transport {
                override fun send(bytes: ByteArray) { sent.write(bytes);writes++ }
                override fun resize(columns: Int, rows: Int) {}
            },QuietSessionClient())
            val view=com.termux.view.TerminalView(context,null)
            view.setTextSize(20)
            view.setTerminalViewClient(QuietViewClient())
            view.attachSession(session)
            view.layout(0,0,800,600)
            val input=requireNotNull(view.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
            input.commitText("printf 'HEXGATE_IME_한글 ┌─┐ \\n'",1)
            view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER))
            view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_ENTER))
        }
        assertEquals("printf 'HEXGATE_IME_한글 ┌─┐ \\n'\r",sent.toString("UTF-8"))
        assertEquals("One IME batch plus hardware Enter",2,writes)
    }
    @Test fun imeTextCommitRespectsBracketedPasteMode() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val sent=java.io.ByteArrayOutputStream()
        var writes=0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session=TerminalSession(object:TerminalSession.Transport {
                override fun send(bytes: ByteArray) { sent.write(bytes);writes++ }
                override fun resize(columns: Int, rows: Int) {}
            },QuietSessionClient())
            val view=com.termux.view.TerminalView(context,null)
            view.setTextSize(20);view.setTerminalViewClient(QuietViewClient());view.attachSession(session);view.layout(0,0,800,600)
            session.receive("\u001b[?2004h".toByteArray())
            requireNotNull(view.onCreateInputConnection(android.view.inputmethod.EditorInfo())).commitText("한글 text",1)
        }
        assertEquals("\u001b[200~한글 text\u001b[201~",sent.toString("UTF-8"))
        assertEquals(1,writes)
    }
    @Test fun importedPrivateKeysAreEncryptedAndRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ProfileStore(context)
        val p = Profile(name = "Instrumented test", host = "localhost", user = "fixture")
        val secret = "test-only-private-key-${System.nanoTime()}"
        try {
            store.save(p, secret)
            assertEquals(secret, store.privateKey(p))
            val raw = context.getSharedPreferences("vault", 0).getString(p.id, "")!!
            assertFalse(raw.contains(secret))
            store.trust("fixture.invalid", 23456, "fingerprint-a")
            assertEquals("fingerprint-a", store.trusted("fixture.invalid",23456))
        } finally { store.delete(p) }
    }
    @Test fun encryptedOpenSshKeyAuthenticatesWithMemoryOnlyPassphrase() {
        val args=InstrumentationRegistry.getArguments()
        val host=args.getString("sshHost")
        org.junit.Assume.assumeNotNull(host)
        val connection=SshConnection()
        try {
            val encrypted=File("/data/local/tmp/hexgate-test-key-encrypted").readText()
            connection.connect(Profile(host=host!!,port=22222,user=args.getString("sshUser","testuser")),encrypted,"hexgate-fixture-only") { it==args.getString("sshFingerprint") }
            assertEquals("ENCRYPTED_KEY_OK",connection.exec("printf ENCRYPTED_KEY_OK"))
        } finally { connection.shutdown() }
    }
    @Test fun realSshPtyAndSftpWhenFixtureIsConfigured() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("sshHost")
        org.junit.Assume.assumeNotNull(host)
        val port = args.getString("sshPort", "22222").toInt()
        val user = args.getString("sshUser", "testuser")
        val key = File("/data/local/tmp/hexgate-test-key").readText()
        val connection = SshConnection()
        try {
            connection.connect(Profile(host = host!!, port = port, user = user), key, "") { true }
            val out = StringBuilder()
            val complete = CountDownLatch(1)
            val reader = Thread { try { connection.read { synchronized(out) { out.append(it.toString(Charsets.UTF_8)); if (out.contains("HEXGATE_PTY_OK") && out.contains("33 91")) complete.countDown() } } } catch (_: Exception) {} }
            reader.start()
            connection.resize(91, 33)
            connection.send("printf 'HEXGATE_PTY_OK\\n'; stty size\n".toByteArray())
            assertTrue(complete.await(15, TimeUnit.SECONDS))
            assertEquals("HEXGATE_EXEC_OK", connection.exec("printf HEXGATE_EXEC_OK"))
            connection.files().use { sftp -> assertTrue(sftp.ls(".").isNotEmpty()) }
        } finally { connection.shutdown() }
    }
}
