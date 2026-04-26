package dev.anilbeesetti.nextplayer.feature.vrplayer.smb

/**
 * One configured SMB / CIFS endpoint. The user has 0..N of these saved
 * in [SmbServerStore]. Passwords are stored separately in
 * EncryptedSharedPreferences and looked up by [id] when connecting.
 */
data class SmbServer(
    val id: String,
    val host: String,
    val share: String,
    val username: String,
    val domain: String? = null,
    val displayName: String = "$host/$share",
)
