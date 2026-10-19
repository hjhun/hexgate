package dev.hexgate

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Owns a single authenticated connection; no dependency on Android views. */
class SshConnection {
    @Volatile private var client: SSHClient? = null
    @Volatile private var session: Session? = null
    @Volatile private var shell: Session.Shell? = null
    @Volatile private var generation = 0
    @Volatile private var closed = false
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(256), ThreadPoolExecutor.AbortPolicy())
    private val pendingBytes = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var terminalSize = 80 to 24
    val columns: Int get() = terminalSize.first
    val rows: Int get() = terminalSize.second

    fun connect(profile: Profile, privateKey: String, passphrase: String, verify: (String) -> Boolean) {
        initializeCrypto()
        val c = SSHClient()
        synchronized(this) { check(!closed) { "Connection cancelled" }; client = c }
        c.transport.config.channelReadTimeoutMs = 0
        c.transport.config.channelErrorReadTimeoutMs = 0
        c.connectTimeout = 15000
        c.timeout = 120000
        c.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: java.security.PublicKey) = !closed && verify(fingerprint(key))
            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })
        try {
            check(!closed)
            c.connect(profile.host, profile.port)
            check(!closed)
            c.timeout = 20000
            val password = object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>?): CharArray = passphrase.toCharArray()
                override fun shouldRetry(resource: Resource<*>?) = false
            }
            val key = c.loadKeys(privateKey, null, password)
            c.authPublickey(profile.user, key)
            c.connection.keepAlive.keepAliveInterval = 15
            val s = c.startSession()
            session = s
            val initialSize = terminalSize
            s.allocatePTY("xterm-256color", initialSize.first, initialSize.second, 0, 0, emptyMap())
            shell = s.startShell()
            // A fold/IME resize between PTY allocation and shell publication must not be lost.
            queueLatestResize()
            send("printf '\\033]2;hexgate-tty:%s\\007' \"\$SSH_TTY\"\n".toByteArray())
            val startup = if (profile.herdrSession.isBlank()) profile.command else {
                require(profile.herdrSession.matches(Regex("[A-Za-z0-9_-]{1,80}")))
                "export PATH=\"\$HOME/.local/bin:\$PATH\"; herdr --session " + quote(profile.herdrSession)
            }
            if (startup.isNotBlank()) send((startup + "\n").toByteArray())
        } catch (e: Exception) { close(); throw e }
    }
    fun read(onBytes: (ByteArray) -> Unit) {
        val input = requireNotNull(shell).inputStream
        val buffer = ByteArray(16 * 1024)
        while (true) { val n = input.read(buffer); if (n < 0) break; onBytes(buffer.copyOf(n)) }
    }
    fun send(bytes: ByteArray) {
        require(bytes.size <= 1024 * 1024) { "Input exceeds 1 MiB" }
        val target = shell ?: return
        val ticket = generation
        val queued = pendingBytes.addAndGet(bytes.size)
        if (queued > 2 * 1024 * 1024) { pendingBytes.addAndGet(-bytes.size); error("Input queue exceeds 2 MiB") }
        try {
            writer.execute {
                try { if (ticket == generation) { target.outputStream.write(bytes); target.outputStream.flush() } }
                catch (_: IOException) { }
                finally { pendingBytes.addAndGet(-bytes.size) }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) { pendingBytes.addAndGet(-bytes.size); throw e }
    }
    fun resize(cols: Int, lines: Int) {
        terminalSize = cols to lines
        queueLatestResize()
    }
    /** SIGWINCH prompts a complete remote redraw without writing control characters. */
    fun refreshDisplay() {
        val target = shell ?: return
        val ticket = generation
        try {
            writer.execute {
                if (ticket != generation || target !== shell) return@execute
                try {
                    val size = terminalSize
                    target.changeWindowDimensions(if (size.first > 4) size.first - 1 else size.first + 1, size.second, 0, 0)
                    val latest = terminalSize
                    target.changeWindowDimensions(latest.first, latest.second, 0, 0)
                } catch (_: IOException) { }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }
    private fun queueLatestResize() {
        val target = shell ?: return
        val ticket = generation
        writer.execute {
            if (ticket == generation && target === shell) {
                val latest = terminalSize
                try { target.changeWindowDimensions(latest.first, latest.second, 0, 0) } catch (_: IOException) { }
            }
        }
    }
    @Synchronized fun files(): SFTPClient = requireNotNull(client) { "Connect first" }.newSFTPClient()
    @Synchronized private fun querySession(): Session {
        val c = requireNotNull(client) { "Connect first" }
        val old = c.transport.config.channelReadTimeoutMs
        val oldError = c.transport.config.channelErrorReadTimeoutMs
        return try {
            c.transport.config.channelReadTimeoutMs = 15000; c.transport.config.channelErrorReadTimeoutMs = 15000
            c.startSession()
        } finally { c.transport.config.channelReadTimeoutMs = old; c.transport.config.channelErrorReadTimeoutMs = oldError }
    }
    fun exec(command: String): String {
        querySession().use { s ->
            s.exec(command).use { c ->
                val bytes = readBounded(c.inputStream, 1024 * 1024)
                c.join(15, TimeUnit.SECONDS)
                check(c.exitStatus == 0) { "Remote query failed" }
                return bytes.toString(Charsets.UTF_8)
            }
        }
    }
    fun close() {
        closed = true
        generation++
        writer.queue.clear()
        try { client?.disconnect() } catch (_: Exception) { }
        try { client?.close() } catch (_: Exception) { }
        shell = null; session = null; client = null
    }
    fun shutdown() { close(); writer.shutdownNow() }
    companion object {
        private var cryptoInitialized = false
        @Synchronized private fun initializeCrypto() {
            if (cryptoInitialized) return
            // Android ships a reduced provider also named BC. SSHJ needs the bundled full provider.
            val bundled = org.bouncycastle.jce.provider.BouncyCastleProvider()
            val current = java.security.Security.getProvider(bundled.name)
            if (current?.javaClass != bundled.javaClass) {
                if (current != null) java.security.Security.removeProvider(bundled.name)
                java.security.Security.addProvider(bundled)
            }
            SecurityUtils.setRegisterBouncyCastle(false)
            SecurityUtils.setSecurityProvider(bundled.name)
            cryptoInitialized = true
        }
        /** Inspect encryption metadata only; never log or persist the key/passphrase. */
        fun keyNeedsPassphrase(text: String): Boolean {
            if (text.contains("ENCRYPTED")) return true
            if (!text.contains("BEGIN OPENSSH PRIVATE KEY")) return false
            return try {
                val body = text.lineSequence().filterNot { it.startsWith("-----") }.joinToString("") { it.trim() }
                val bytes = java.util.Base64.getDecoder().decode(body)
                val magic = "openssh-key-v1\u0000".toByteArray(Charsets.US_ASCII)
                require(bytes.size >= magic.size + 4 && bytes.copyOfRange(0,magic.size).contentEquals(magic))
                val buffer = java.nio.ByteBuffer.wrap(bytes).apply { position(magic.size) }
                val size = buffer.int
                require(size in 1..64 && buffer.remaining() >= size)
                val cipher = ByteArray(size).also { buffer.get(it) }.toString(Charsets.US_ASCII)
                cipher != "none"
            } catch (_: Exception) { true }
        }
        fun fingerprint(key: java.security.PublicKey): String {
            val wire = net.schmizz.sshj.common.Buffer.PlainBuffer()
            wire.putPublicKey(key)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(wire.compactData)
            return "SHA256:" + java.util.Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
        fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"
        fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) return out.toByteArray()
                require(out.size() + n <= limit) { "File exceeds ${limit / 1024} KiB limit" }
                out.write(buffer, 0, n)
            }
        }
    }
}
