package com.example.encryptedvault.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultStorage

/** ExoPlayer that streams [item] straight from its ciphertext through [VaultDataSource]. The caller prepares and releases it. */
fun buildVaultPlayer(context: Context, item: VaultItem): ExoPlayer {
    val source = DataSource.Factory { VaultDataSource({ VaultApp.sessionKey }, { hash -> VaultStorage.content(context, hash) }) }
    return ExoPlayer.Builder(context).build().apply {
        setMediaSource(ProgressiveMediaSource.Factory(source).createMediaSource(MediaItem.Builder().setUri("vault://${item.hash}").setMimeType(item.mimeType).build()))
    }
}
