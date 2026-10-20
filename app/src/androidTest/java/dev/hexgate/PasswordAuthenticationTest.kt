package dev.hexgate

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class PasswordAuthenticationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun credentials(): JSONObject {
        val file = File("/data/local/tmp/hexgate-password-fixture.json")
        org.junit.Assume.assumeTrue("Password fixture not supplied", file.exists())
        return JSONObject(file.readText())
    }
    private fun profile(c: JSONObject) = Profile(name="Password fixture", host="10.0.2.2", port=22223,
        user=c.getString("user"), authMethod=AuthMethod.PASSWORD)

    @Test fun vaultEncryptsReplacesRetainsAndDeletesPasswordWithoutAKey() {
        val store = ProfileStore(context)
        val p = Profile(authMethod=AuthMethod.PASSWORD)
        val password = "fixture-秘密-${java.util.UUID.randomUUID()}"
        try {
            store.save(p, null, password)
            assertEquals(password, store.password(p))
            val fresh = ProfileStore(context)
            assertEquals(AuthMethod.PASSWORD, fresh.profiles().first { it.id == p.id }.authMethod)
            assertEquals(password, fresh.password(p))
            for (name in listOf("profiles", "vault")) {
                assertFalse("Plaintext secret persisted", context.getSharedPreferences(name, Context.MODE_PRIVATE).all.values.any { it.toString().contains(password) })
            }
            store.save(p.copy(name="Renamed"), null)
            assertEquals(password, store.password(p))
            store.save(p, null, "replacement")
            assertEquals("replacement", store.password(p))
            val keyProfile = p.copy(authMethod=AuthMethod.PRIVATE_KEY)
            store.save(keyProfile, null)
            assertFalse(store.hasPassword(p))
            store.save(p, null, password)
            store.delete(p)
            assertFalse(store.hasPassword(p))
            assertFalse(store.profiles().any { it.id == p.id })
        } finally { store.delete(p) }
    }

    @Test fun editorShowsMaskedPasswordInputAndSavesItSecurely() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val name="Password editor ${System.nanoTime()}"
        fun find(label: String, action: Int, value: String? = null) {
            fun match(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
                if(label=="SSH password" && node.isPassword && node.isEditable) return node
                if((if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) node.text?.toString()==label else node.text?.toString()?.contains(label)==true) || node.contentDescription?.toString()==label) return node
                for(i in 0 until node.childCount) node.getChild(i)?.let { match(it)?.let { found -> return found } }
                return null
            }
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
            while(System.nanoTime()<deadline) {
                var node=instrumentation.uiAutomation.rootInActiveWindow?.let { match(it) }
                while(node!=null) {
                    node.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    val args=android.os.Bundle().apply { if(value!=null)putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value) }
                    if(node.performAction(action,args)) return
                    node=node.parent
                }
                if(label=="SSH password") {
                    fun scroll(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
                        if(node.isScrollable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                        for(i in 0 until node.childCount) node.getChild(i)?.let { if(scroll(it))return true }
                        return false
                    }
                    instrumentation.uiAutomation.rootInActiveWindow?.let { scroll(it) }
                }
                Thread.sleep(100)
            }
            fail("Editor UI action missing: $label")
        }
        val click=android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
        val set=android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT
        launchHexgateTestActivity().use { scenario ->
            try {
                find("Open menu",click);find("Add connection",click)
                find("Name",set,name);find("Host / DDNS",set,"example.invalid");find("Ubuntu user",set,"fixture")
                find("Password",click);find("SSH password",set,"editor-fixture-secret")
                find("Save",click)
                var saved:Profile?=null
                scenario.onActivity { a ->
                    saved=a.model.profiles.firstOrNull { it.name==name }
                    assertNotNull("Password profile was not saved by the UI",saved)
                    assertEquals(AuthMethod.PASSWORD,saved!!.authMethod)
                    assertEquals("editor-fixture-secret",a.model.store.password(saved!!))
                }
            } finally {
                scenario.onActivity { a -> a.model.profiles.filter { it.name==name }.forEach { a.model.delete(it) } }
            }
        }
    }

    @Test fun legacyProfilesStillDefaultToPrivateKey() {
        val store=ProfileStore(context)
        val p=Profile()
        try {
            store.save(p,null)
            val prefs=context.getSharedPreferences("profiles",Context.MODE_PRIVATE)
            val list=org.json.JSONArray(prefs.getString("list","[]"))
            for(i in 0 until list.length()) if(list.getJSONObject(i).getString("id")==p.id) list.getJSONObject(i).remove("authMethod")
            prefs.edit().putString("list",list.toString()).commit()
            assertEquals(AuthMethod.PRIVATE_KEY,store.profiles().first { it.id==p.id }.authMethod)
        } finally { store.delete(p) }
    }

    @Test fun savedPasswordConnectsAndReconnectsWithoutKeyPrompt() {
        val c=credentials();val p=profile(c)
        launchHexgateTestActivity().use { scenario ->
            lateinit var model:TerminalModel
            scenario.onActivity { a ->
                model=a.model
                model.save(p,"",c.getString("password"))
                model.store.trust(p.host,p.port,c.getString("fingerprint"))
                model.connectSaved(p)
            }
            fun awaitReady() {
                var ready=false;val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                while(!ready && System.nanoTime()<deadline) {
                    scenario.onActivity { ready=model.connected && model.terminal.emulator.screen.transcriptText.contains("PASSWORD_SHELL_READY");assertNull(model.keyPromptProfile) }
                    Thread.sleep(50)
                }
                assertTrue("Saved password connection missing",ready)
            }
            try {
                awaitReady()
                scenario.recreate()
                scenario.onActivity { a -> assertSame(model,a.model);assertTrue(model.connected);model.disconnect();model.reconnect() }
                awaitReady()
            } finally { scenario.onActivity { model.disconnect();model.delete(p);model.store.forgetTrust(p.host,p.port) } }
        }
    }

    @Test fun passwordTransportRejectsWrongPasswordAndUntrustedHost() {
        val c=credentials();val p=profile(c)
        val valid=SshConnection()
        try {
            valid.connect(p,"","",c.getString("password")) { it==c.getString("fingerprint") }
            assertEquals("PASSWORD_EXEC_OK",valid.exec("fixture-check").trim())
        } finally { valid.shutdown() }
        val wrong=SshConnection()
        try {
            var rejected=false
            try { wrong.connect(p,"","","incorrect") { it==c.getString("fingerprint") } } catch (_: Exception) { rejected=true }
            assertTrue("Invalid password accepted",rejected)
        } finally { wrong.shutdown() }
        val untrusted=SshConnection()
        try {
            var rejected=false;var verified=false
            try { untrusted.connect(p,"","",c.getString("password")) { verified=true;false } } catch (_: Exception) { rejected=true }
            assertTrue(verified);assertTrue("Untrusted host accepted",rejected)
        } finally { untrusted.shutdown() }
    }
}
