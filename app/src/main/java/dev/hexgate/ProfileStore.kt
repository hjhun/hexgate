package dev.hexgate

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Development PC",
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val command: String = "",
    val herdrSession: String = "",
)

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    private val vault = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("hexgate-keys", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("hexgate-keys", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun profiles(): List<Profile> {
        val a = JSONArray(prefs.getString("list", "[]"))
        return (0 until a.length()).map { i -> a.getJSONObject(i).let {
            Profile(it.getString("id"), it.getString("name"), it.getString("host"), it.getInt("port"), it.getString("user"), it.optString("command"), it.optString("herdrSession"))
        } }
    }
    fun save(profile: Profile, privateKey: String?) {
        val all = profiles().filterNot { it.id == profile.id } + profile
        val a = JSONArray()
        all.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port).put("user", it.user).put("command", it.command).put("herdrSession", it.herdrSession)) }
        prefs.edit().putString("list", a.toString()).apply()
        if (!privateKey.isNullOrBlank()) {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = c.doFinal(privateKey.toByteArray(Charsets.UTF_8))
            vault.edit().putString(profile.id, Base64.encodeToString(c.iv + encrypted, Base64.NO_WRAP)).apply()
        }
    }
    fun privateKey(profile: Profile): String {
        val data = Base64.decode(requireNotNull(vault.getString(profile.id, null)) { "Import an SSH private key first" }, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
        return String(c.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
    }
    fun delete(profile: Profile) {
        val kept = profiles().filterNot { it.id == profile.id }
        prefs.edit().remove("list").apply()
        kept.forEach { save(it, null) }
        vault.edit().remove(profile.id).apply()
        prefs.edit().remove("recent:${profile.id}").apply()
    }
    fun recentFiles(profileId: String): List<String> {
        val rows = JSONArray(prefs.getString("recent:$profileId", "[]"))
        return (0 until rows.length()).map { rows.getString(it) }
    }
    fun saveRecentFiles(profileId: String, paths: List<String>) { prefs.edit().putString("recent:$profileId", JSONArray(paths.take(12)).toString()).apply() }
    fun autoConnect(): Boolean = prefs.getBoolean("autoConnect",true)
    fun saveAutoConnect(enabled: Boolean) { prefs.edit().putBoolean("autoConnect",enabled).apply() }
    fun themeId(): String = AppThemes.validId(prefs.getString("themeId","hexgate").orEmpty())
    fun saveThemeId(id: String) { prefs.edit().putString("themeId",AppThemes.validId(id)).apply() }
    fun fontId(): String = TerminalFonts.resolve(prefs.getString("fontId", "jetbrains").orEmpty()).id
    fun saveFontId(id: String) { prefs.edit().putString("fontId",TerminalFonts.resolve(id).id).apply() }
    fun fontSize(): Float = prefs.getFloat("fontSize", 14f).coerceIn(8f, 28f)
    fun saveFontSize(size: Float) { prefs.edit().putFloat("fontSize",size.coerceIn(8f,28f)).apply() }
    fun lastProfile(): Profile? = profiles().find { it.id == prefs.getString("lastProfile", null) }
    fun saveLastProfile(profile: Profile) { prefs.edit().putString("lastProfile",profile.id).apply() }
    fun trusted(host: String, port: Int): String? = prefs.getString("trust:$host:$port", null)
    fun forgetTrust(host: String, port: Int) { prefs.edit().remove("trust:$host:$port").apply() }
    fun trust(host: String, port: Int, fingerprint: String) { prefs.edit().putString("trust:$host:$port", fingerprint).apply() }
}
