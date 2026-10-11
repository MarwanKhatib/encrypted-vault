package com.example.encryptedvault.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.crypto.SecretKey

/**
 * ExoPlayer data source that serves a vaulted video straight from its ciphertext.
 *
 * URIs look like `vault://<hash>`. Reads go through [com.example.encryptedvault.crypto.VaultFiles.openSeekable],
 * which decrypts and authenticates one 1 MiB segment at a time, so:
 *  - nothing is ever written to disk in plaintext (the previous design decrypted the whole video into the cache),
 *  - seeking is instant (only the segment at the target position is decrypted),
 *  - memory use stays at about one segment, even for a 500 MB video.
 *
 * @param key the unlocked master key (read per open, so a lock between opens fails cleanly instead of using a stale key)
 * @param fileFor maps the `<hash>` of the URI to the encrypted file on disk
 */
@UnstableApi
class VaultDataSource(
    private val key: () -> SecretKey?,
    private val fileFor: (String) -> File,
    private val openChannel: (SecretKey, File) -> SeekableByteChannel = { k, f -> com.example.encryptedvault.crypto.VaultFiles.openSeekable(k, f) },
) : BaseDataSource(/* isNetwork = */ false) {

    private var channel: SeekableByteChannel? = null
    private var openedUri: Uri? = null
    private var bytesRemaining = 0L

    /** Opens the file named by [dataSpec] and positions at [DataSpec.position]; returns the number of bytes that can be read. */
    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        transferInitializing(dataSpec)
        val hash = dataSpec.uri.host ?: throw IOException("missing item id in ${dataSpec.uri}")
        val masterKey = key() ?: throw IOException("vault is locked")
        val ch = try { openChannel(masterKey, fileFor(hash)) } catch (e: Exception) { throw IOException("cannot open encrypted video", e) }
        channel = ch
        try {
            ch.position(dataSpec.position)
            val available = ch.size() - dataSpec.position
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(dataSpec.length, available)
        } catch (e: Exception) {
            close()
            throw IOException("cannot seek in encrypted video", e)
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    /** Decrypts up to [length] bytes into [buffer]; returns [C.RESULT_END_OF_INPUT] at the end. Authentication failures become IOExceptions. */
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val ch = channel ?: throw IOException("data source is not open")
        val wanted = minOf(length.toLong(), bytesRemaining).toInt()
        val read = try { ch.read(ByteBuffer.wrap(buffer, offset, wanted)) } catch (e: Exception) { throw IOException("decryption failed", e) }
        if (read < 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    /** The URI passed to the last successful [open], or null when closed. */
    override fun getUri(): Uri? = openedUri

    /** Closes the channel (and with it the file). Safe to call repeatedly. */
    override fun close() {
        val wasOpen = channel != null
        try { channel?.close() } finally {
            channel = null
            openedUri = null
            bytesRemaining = 0
            if (wasOpen) transferEnded()
        }
    }
}
