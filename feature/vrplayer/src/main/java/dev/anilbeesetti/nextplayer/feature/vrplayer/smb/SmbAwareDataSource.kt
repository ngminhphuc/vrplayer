package dev.anilbeesetti.nextplayer.feature.vrplayer.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * `DataSource` that picks at [open] time between an internal [SmbDataSource]
 * (for `smb://` URIs) and a delegate produced by `DefaultDataSource.Factory`
 * (for everything else: file/asset/http(s)/content). Lets a single
 * `ExoPlayer` instance mix mounted-NAS clips with local files without
 * juggling two separate factories at the call site.
 */
class SmbAwareDataSource(
    private val store: SmbServerStore,
    private val defaultDelegate: DataSource,
) : DataSource {

    private var active: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun open(dataSpec: DataSpec): Long {
        val ds = if ("smb".equals(dataSpec.uri.scheme, ignoreCase = true)) {
            SmbDataSource(store).also { d ->
                listeners.forEach { d.addTransferListener(it) }
            }
        } else {
            defaultDelegate
        }
        active = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        defaultDelegate.addTransferListener(transferListener)
        active?.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = active?.uri

    override fun close() {
        active?.close()
        active = null
    }
}
