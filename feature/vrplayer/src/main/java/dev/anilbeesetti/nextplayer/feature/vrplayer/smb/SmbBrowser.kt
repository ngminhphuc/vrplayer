package dev.anilbeesetti.nextplayer.feature.vrplayer.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.io.Closeable
import java.util.EnumSet
import timber.log.Timber

/**
 * Single-server SMB browser. Lazily opens a connection on first use and
 * keeps it warm until [close] is called. Browse calls run synchronously;
 * the caller (a coroutine) is responsible for IO dispatcher.
 */
class SmbBrowser(
    private val server: SmbServer,
    private val password: String,
) : Closeable {

    data class Entry(val name: String, val isDirectory: Boolean, val sizeBytes: Long)

    private val client: SMBClient by lazy {
        SMBClient(SmbConfig.builder().withDfsEnabled(false).build())
    }
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    private fun ensureShare(): DiskShare {
        share?.let { return it }
        // Build connection/session/share locally first so a failure in any
        // step doesn't half-populate the fields and leak resources on the
        // next retry. We only commit to the fields once all three succeed.
        var conn: Connection? = null
        var sess: Session? = null
        try {
            conn = client.connect(server.host)
            val auth = AuthenticationContext(
                server.username,
                password.toCharArray(),
                server.domain ?: "",
            )
            sess = conn.authenticate(auth)
            val sh = sess.connectShare(server.share) as DiskShare
            connection = conn
            session = sess
            share = sh
            return sh
        } catch (t: Throwable) {
            runCatching { sess?.close() }
            runCatching { conn?.close() }
            throw t
        }
    }

    fun list(folder: String): List<Entry> {
        val sh = ensureShare()
        // smbj uses backslashes; normalise.
        val path = folder.trim('/', '\\').replace('/', '\\')
        return sh.list(path).mapNotNull { info ->
            val n = info.fileName
            if (n == "." || n == "..") return@mapNotNull null
            val isDir = (info.fileAttributes and 0x10L) != 0L
            Entry(n, isDir, info.endOfFile)
        }
    }

    /** Open a remote file for streaming. Caller must close the returned file. */
    fun open(remotePath: String): SmbFile {
        val sh = ensureShare()
        val path = remotePath.trim('/', '\\').replace('/', '\\')
        return sh.openFile(
            path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
    }

    override fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client.close() }
        share = null
        session = null
        connection = null
        Timber.tag("SmbBrowser").d("closed ${server.displayName}")
    }
}
