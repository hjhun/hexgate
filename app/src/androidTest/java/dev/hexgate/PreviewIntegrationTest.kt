package dev.hexgate

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PreviewIntegrationTest {
    private fun screenshot(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        Thread.sleep(500)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        java.io.File(instrumentation.targetContext.cacheDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    private fun evaluate(view: WebView, script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        return result
    }
    private fun waitFor(view: WebView, predicate: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        while (System.nanoTime() < deadline) { if (evaluate(view, predicate) == "true") return; Thread.sleep(100) }
        fail("Preview predicate did not become true: $predicate; " + evaluate(view,"document.body.innerText"))
    }
    private fun withPreview(doc: RemoteDocument, remote: RemoteFiles = RemoteFiles(SshConnection()), block: (DocumentPreview) -> Unit) {
        launchHexgateTestActivity().use { scenario ->
            lateinit var view: DocumentPreview
            scenario.onActivity { activity ->
                view = DocumentPreview(activity, doc.kind != "html")
                activity.setContentView(view)
                view.show(doc, remote)
            }
            try { block(view) } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { view.destroy() } }
        }
    }
    @Test fun markdownEmbedsDrawioAndImagesThroughBoundedSftp() {
        val args=InstrumentationRegistry.getArguments()
        val host=args.getString("sshHost")
        val root=args.getString("fixtureRoot")
        org.junit.Assume.assumeNotNull(host,root)
        val connection=SshConnection()
        try {
            connection.connect(Profile(host=host!!,port=22222,user=args.getString("sshUser","testuser")),java.io.File("/data/local/tmp/hexgate-test-key").readText(),"") { it==args.getString("sshFingerprint") }
            val remote=RemoteFiles(connection)
            val document=remote.document(root!!+"/preview.md")
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { remote.resource(root,"outside.png") }
            withPreview(document,remote) { view ->
                waitFor(view,"window.previewReady===true && document.querySelectorAll('.mxgraph svg').length>0 && Array.from(document.images).some(i=>i.naturalWidth>0)")
                assertEquals("true",evaluate(view,"document.body.textContent.includes('Hexgate SSH')"))
                screenshot("embedded-drawio.png")
            }
        } finally { connection.shutdown() }
    }
    @Test fun markdownAndMermaidRenderAndStripDocumentScripts() {
        val text = "# Hexgate preview\n\n한글 문서\n\n<script>window.documentAttack=true</script>\n\n```mermaid\nflowchart LR\n A[Android] --> B[SSH]\n```"
        withPreview(RemoteDocument("/test/guide.md","/test","markdown",text)) { view ->
            waitFor(view,"window.previewReady===true && document.querySelectorAll('svg').length>0")
            assertEquals("true",evaluate(view,"document.body.textContent.includes('한글 문서')"))
            assertEquals("true",evaluate(view,"window.documentAttack===undefined"))
            assertEquals("true",evaluate(view,"document.querySelector('svg').textContent.includes('Android')"))
            val dimensions=evaluate(view,"JSON.stringify({width:document.querySelector('svg').getBoundingClientRect().width,height:document.querySelector('svg').getBoundingClientRect().height,viewBox:document.querySelector('svg').getAttribute('viewBox')})")
            assertEquals("true",evaluate(view,"document.querySelector('svg').getBoundingClientRect().width>150 && document.querySelector('svg').getBoundingClientRect().height>20"))
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"mermaid-dom.txt").writeText(evaluate(view,"document.querySelector('svg').outerHTML")+"\n"+dimensions)
            screenshot("markdown-mermaid.png")
        }
    }
    @Test fun rerenderClearsReadinessAndKeepsTheLatestDiagram() {
        val text="```mermaid\nflowchart LR\n A[Android] --> B[SSH]\n```"
        withPreview(RemoteDocument("/test/race.md","/test","markdown",text)) { view ->
            waitFor(view,"window.previewReady===true && document.querySelector('svg')!==null")
            val payload=org.json.JSONObject().put("kind","markdown").put("text",text).put("light",true)
            assertEquals("false",evaluate(view,"window.renderDocument($payload); window.previewReady===true"))
            // A second caller arrives before the first asynchronous Mermaid render finishes.
            evaluate(view,"window.renderDocument($payload); true")
            waitFor(view,"window.previewReady===true && document.querySelector('svg')?.textContent.includes('Android')===true")
            assertEquals("1",evaluate(view,"document.querySelectorAll('#document svg').length"))
            assertEquals("true",evaluate(view,"document.querySelector('svg').getBoundingClientRect().height>20"))
        }
    }
    @Test fun drawioRendersLocally() {
        val xml = """<mxfile><diagram name="Page-1"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="2" value="Hexgate" vertex="1" parent="1" style="rounded=1;whiteSpace=wrap;html=1;"><mxGeometry x="20" y="20" width="120" height="60" as="geometry"/></mxCell></root></mxGraphModel></diagram></mxfile>"""
        withPreview(RemoteDocument("/test/system.drawio","/test","drawio",xml)) { view ->
            waitFor(view,"window.previewReady===true && document.querySelectorAll('svg').length>0")
            assertEquals("true",evaluate(view,"document.body.textContent.includes('Hexgate')"))
            screenshot("drawio.png")
        }
    }
    @Test fun htmlCannotExecuteDocumentScripts() {
        withPreview(RemoteDocument("/test/report.html","/test","html","<title>SAFE</title><h1>HTML fixture</h1><script>document.title='EXECUTED'</script>")) { view ->
            val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(10)
            var title: String? = null
            while(System.nanoTime()<deadline && title!="SAFE" && title!="EXECUTED") {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { title=view.title }
                Thread.sleep(100)
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync { assertFalse(view.settings.javaScriptEnabled); assertEquals("SAFE",view.title) }
        }
    }
}
