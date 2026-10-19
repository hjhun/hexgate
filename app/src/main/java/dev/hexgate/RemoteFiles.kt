package dev.hexgate

import net.schmizz.sshj.sftp.OpenMode
import java.io.ByteArrayInputStream
import java.nio.file.Paths

data class RemoteEntry(val path: String, val name: String, val directory: Boolean)
data class RemoteDocument(val path: String, val root: String, val kind: String, val text: String)

class RemoteFiles(private val connection: SshConnection) {
    fun home(): String = connection.exec("pwd").trim().also { require(it.startsWith('/')) }
    fun list(path: String): List<RemoteEntry> = connection.files().use { sftp ->
        val base = sftp.canonicalize(path)
        sftp.ls(base).asSequence().filter { it.name != "." && it.name != ".." }.take(500)
            .map { RemoteEntry(base.trimEnd('/') + "/" + it.name, it.name, it.isDirectory) }
            .sortedWith(compareByDescending<RemoteEntry> { it.directory }.thenBy { it.name }).toList()
    }
    fun document(path: String): RemoteDocument = connection.files().use { sftp ->
        val canonical = sftp.canonicalize(path)
        val root = canonical.substringBeforeLast('/').ifEmpty { "/" }
        val kind = when (canonical.substringAfterLast('.').lowercase()) {
            "md", "markdown" -> "markdown"
            "html", "htm" -> "html"
            "drawio", "xml" -> "drawio"
            "mmd", "mermaid" -> "mermaid"
            else -> error("Choose Markdown, HTML, Mermaid or draw.io")
        }
        val attrs = sftp.stat(canonical)
        require(attrs.size <= DOCUMENT_LIMIT) { "Document exceeds 2 MiB" }
        val text = sftp.open(canonical, setOf(OpenMode.READ)).use { f ->
            f.RemoteFileInputStream().use { SshConnection.readBounded(it, DOCUMENT_LIMIT).toString(Charsets.UTF_8) }
        }
        RemoteDocument(canonical, root, kind, text)
    }
    fun resource(root: String, relative: String): ByteArray = connection.files().use { sftp ->
        require(!relative.contains('\u0000') && !relative.startsWith('/'))
        val candidate = sftp.canonicalize(root.trimEnd('/') + "/" + relative)
        require(inside(root, candidate)) { "Resource outside document directory" }
        require(sftp.stat(candidate).size <= RESOURCE_LIMIT) { "Resource exceeds 1 MiB" }
        sftp.open(candidate, setOf(OpenMode.READ)).use { f -> f.RemoteFileInputStream().use { SshConnection.readBounded(it, RESOURCE_LIMIT) } }
    }
    fun captureHerdr(session: String, tty: String, helper: String): HerdrContext {
        val payload = connection.exec("python3 -c " + SshConnection.quote(helper) + " " + SshConnection.quote(session) + " " + SshConnection.quote(tty))
        val json = org.json.JSONObject(payload)
        require(!json.has("error"))
        val rows = json.getJSONArray("panes")
        val panes = (0 until rows.length()).map { rows.getJSONObject(it).let { p -> HerdrPane(p.getString("id"),p.getString("cwd"),p.getString("name")) } }
        return HerdrContext(json.getBoolean("automatic"),json.optString("focused").takeUnless { it == "null" || it.isBlank() },panes,json.optString("reason"))
    }

    companion object {
        const val DOCUMENT_LIMIT = 2 * 1024 * 1024
        const val RESOURCE_LIMIT = 1024 * 1024
        fun inside(root: String, candidate: String): Boolean {
            val r = Paths.get(root).normalize().toString().trimEnd('/')
            val p = Paths.get(candidate).normalize().toString()
            return p == r || p.startsWith("$r/")
        }
    }
}

data class HerdrPane(val id: String, val cwd: String, val name: String)
data class HerdrContext(val automatic: Boolean, val focused: String?, val panes: List<HerdrPane>, val reason: String)
