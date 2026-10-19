package dev.hexgate

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class TransportPolicyTest {
    @Test fun encryptedKeyHeadersRequireAnInteractivePassphrase() {
        fun openSsh(cipher: String): String {
            val body=java.io.ByteArrayOutputStream()
            body.write("openssh-key-v1\u0000".toByteArray())
            java.io.DataOutputStream(body).use { it.writeInt(cipher.length);it.write(cipher.toByteArray()) }
            return "-----BEGIN OPENSSH PRIVATE KEY-----\n"+java.util.Base64.getEncoder().encodeToString(body.toByteArray())+"\n-----END OPENSSH PRIVATE KEY-----"
        }
        assertFalse(SshConnection.keyNeedsPassphrase(openSsh("none")))
        assertTrue(SshConnection.keyNeedsPassphrase(openSsh("aes256-ctr")))
        assertTrue(SshConnection.keyNeedsPassphrase("-----BEGIN ENCRYPTED PRIVATE KEY-----"))
        assertTrue(SshConnection.keyNeedsPassphrase("-----BEGIN OPENSSH PRIVATE KEY-----\nbroken"))
    }

    @Test fun boundedReadRejectsOversizedRemoteFiles() {
        assertArrayEquals(byteArrayOf(1,2,3), SshConnection.readBounded(ByteArrayInputStream(byteArrayOf(1,2,3)), 3))
        assertThrows(IllegalArgumentException::class.java) { SshConnection.readBounded(ByteArrayInputStream(ByteArray(4)), 3) }
    }
    @Test fun documentResourcesCannotEscapeTheirRoot() {
        assertTrue(RemoteFiles.inside("/repo/docs", "/repo/docs/img/a.png"))
        assertFalse(RemoteFiles.inside("/repo/docs", "/repo/docs-other/a.png"))
        assertFalse(RemoteFiles.inside("/repo/docs", "/repo/docs/../../.ssh/id_ed25519"))
    }
    @Test fun arrowsRespectApplicationModeAndControlModifiers() {
        assertEquals("\u001bOD",com.termux.terminal.KeyHandler.getCode(android.view.KeyEvent.KEYCODE_DPAD_LEFT,0,true,false))
        assertEquals("\u001b[1;5D",com.termux.terminal.KeyHandler.getCode(android.view.KeyEvent.KEYCODE_DPAD_LEFT,com.termux.terminal.KeyHandler.KEYMOD_CTRL,false,false))
    }
    @Test fun shellArgumentsRemainOneLiteralArgument() {
        assertEquals("'a'\\''b; $(whoami)'", SshConnection.quote("a'b; $(whoami)"))
    }
}
