package dev.anilbeesetti.nextplayer.feature.vrplayer.smb

import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.smbj.share.File as SmbFile
import java.io.IOException
import kotlin.math.min

/**
 * `androidx.media3.datasource.DataSource` implementation backed by smbj.
 * URIs use the `smb://<host>/<share>/<path>` scheme. The factory looks
 * up credentials in [SmbServerStore] using the host+share key.
 */
class SmbDataSource(
    private val store: SmbServerStore,
) : BaseDataSource(true) {

    private var browser: SmbBrowser? = null
    private var file: SmbFile? = null
    private var bytesRemaining: Long = 0
    private var position: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        require(uri.scheme == "smb") { "SmbDataSource expects smb:// URI" }

        val host = uri.host ?: throw IOException("Missing host in $uri")
        val pathParts = uri.pathSegments
        if (pathParts.isEmpty()) throw IOException("Missing share in $uri")
        val share = pathParts[0]
        val remotePath = pathParts.drop(1).joinToString("\\")

        val server = store.list().firstOrNull { it.host == host && it.share == share }
            ?: throw IOException("Unknown SMB server $host/$share")
        val pwd = store.password(server.id) ?: throw IOException("Missing password for ${server.displayName}")

        val br = SmbBrowser(server, pwd).also { browser = it }
        val f = br.open(remotePath).also { file = it }

        val length = f.fileInformation.standardInformation.endOfFile
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            length - position
        } else {
            min(dataSpec.length, length - position)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = min(length.toLong(), bytesRemaining).toInt()
        val read = file?.read(buffer, position, offset, toRead)
            ?: throw IOException("SmbDataSource not opened")
        if (read == -1) return C.RESULT_END_OF_INPUT
        position += read
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri() = browser?.let {
        android.net.Uri.parse("smb://$it")
    }

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        runCatching { file?.close() }
        runCatching { browser?.close() }
        file = null
        browser = null
        bytesRemaining = 0
        position = 0
    }

    class Factory(private val store: SmbServerStore) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(store)
    }
}
