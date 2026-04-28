package dev.anilbeesetti.nextplayer.feature.vrplayer.smb

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists SMB server configurations + (encrypted) passwords using
 * Android Jetpack Security's [EncryptedSharedPreferences]. The whole
 * file is encrypted at rest (AES-256-GCM keyed off the device's
 * Keystore-backed master key), so no plaintext credentials hit disk.
 */
class SmbServerStore(context: Context) {

    private val prefs = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun list(): List<SmbServer> {
        val raw = prefs.getString(KEY_SERVERS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SmbServer(
                id = o.getString("id"),
                host = o.getString("host"),
                share = o.getString("share"),
                username = o.optString("username", ""),
                domain = o.optString("domain").takeIf { it.isNotEmpty() },
                displayName = o.optString("displayName", "${o.getString("host")}/${o.getString("share")}"),
            )
        }
    }

    fun add(server: SmbServer, password: String): SmbServer {
        val id = server.id.ifBlank { UUID.randomUUID().toString() }
        val final = server.copy(id = id)
        val arr = JSONArray(prefs.getString(KEY_SERVERS, "[]") ?: "[]")
        arr.put(
            JSONObject(
                mapOf(
                    "id" to id,
                    "host" to final.host,
                    "share" to final.share,
                    "username" to final.username,
                    "domain" to (final.domain ?: ""),
                    "displayName" to final.displayName,
                ),
            ),
        )
        prefs.edit()
            .putString(KEY_SERVERS, arr.toString())
            .putString(passwordKey(id), password)
            .apply()
        return final
    }

    fun remove(id: String) {
        val arr = JSONArray(prefs.getString(KEY_SERVERS, "[]") ?: "[]")
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") != id) out.put(o)
        }
        prefs.edit()
            .putString(KEY_SERVERS, out.toString())
            .remove(passwordKey(id))
            .apply()
    }

    fun password(id: String): String? = prefs.getString(passwordKey(id), null)

    private fun passwordKey(id: String) = "pw_$id"

    companion object {
        private const val FILE = "vrplayer_smb"
        private const val KEY_SERVERS = "servers"
    }
}
