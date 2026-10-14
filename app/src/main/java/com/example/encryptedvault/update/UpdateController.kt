package com.example.encryptedvault.update

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.example.encryptedvault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Check, download, verify and install a newer GitHub release (ADR-015).
 *
 * Android does not allow silent installs: the system shows its own confirm dialog. The install keeps the vault
 * when the signer matches (same release key), so the user never has to uninstall. The download is deleted once
 * Android has taken its own copy of the APK.
 */
object UpdateController {
    /** What the Settings "Updates" section shows. */
    sealed interface State {
        /** Nothing checked yet in this session. */
        data object Idle : State

        /** Asking GitHub for the latest release. */
        data object Checking : State

        /** Installed version is the latest. */
        data object UpToDate : State

        /** A newer [release] exists and can be downloaded. */
        data class Available(
            val release: Release,
        ) : State

        /** Downloading the APK; [progress] is 0..1. */
        data class Downloading(
            val progress: Float,
        ) : State

        /** Handed to Android; waiting for the user's confirm or the result. */
        data object Installing : State

        /** Something stopped the update; [message] says what. */
        data class Failed(
            val message: String,
        ) : State
    }

    private val stateFlow = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = stateFlow.asStateFlow()

    private fun workDir(context: Context) = File(context.cacheDir, "update")

    /** Asks GitHub for the latest release and updates [state]. */
    suspend fun check() {
        stateFlow.value = State.Checking
        val release = withContext(Dispatchers.IO) { ReleaseFeed.fetchLatest() }
        stateFlow.value =
            when {
                release == null -> State.Failed("Could not reach GitHub. Check the connection and try again.")
                UpdateRules.isNewer(BuildConfig.VERSION_NAME, release.tag) -> State.Available(release)
                else -> State.UpToDate
            }
    }

    /** Downloads [release], verifies it, and hands it to Android to install. Keeps the vault: same signer, same app id. */
    suspend fun updateTo(
        context: Context,
        release: Release,
    ) {
        if (!UpdateRules.isNewer(BuildConfig.VERSION_NAME, release.tag)) {
            stateFlow.value = State.UpToDate
            return
        }
        val work = workDir(context).apply { deleteRecursively() }.apply { mkdirs() }
        val apk = File(work, ReleaseFeed.APK_NAME)
        try {
            val problem = withContext(Dispatchers.IO) { stage(context, release, apk) }
            if (problem != null) {
                stateFlow.value = State.Failed(problem)
                return
            }
            stateFlow.value = State.Installing
            withContext(Dispatchers.IO) { install(context, apk) }
            // Android has its own copy of the APK now; the download is removed right away.
            work.deleteRecursively()
        } catch (e: java.io.IOException) {
            stateFlow.value = State.Failed("Install could not start: ${e.message ?: "I/O error"}")
        } finally {
            work.deleteRecursively()
        }
    }

    /** Called by [InstallResultReceiver] when Android reports the outcome. [error] is null on success. */
    internal fun finished(
        context: Context,
        error: String?,
    ) {
        stateFlow.value = if (error == null) State.UpToDate else State.Failed(error)
        workDir(context).deleteRecursively()
    }

    /** Downloads and verifies the APK. Returns null when it may be installed, otherwise the reason it was refused. */
    private fun stage(
        context: Context,
        release: Release,
        apk: File,
    ): String? {
        if (!ReleaseFeed.download(release.apkUrl, apk) { stateFlow.value = State.Downloading(it) }) return "Download failed. Try again."
        val sha = ReleaseFeed.httpGet(release.shaUrl) ?: return "Checksum file not found. Update refused."
        if (!UpdateRules.checksumMatches(sha, sha256Hex(apk.readBytes()))) return "Checksum mismatch. The download was discarded."
        if (!sameSignerAsInstalled(context, apk)) return "Signature does not match this app. The download was discarded."
        return null
    }

    private fun install(
        context: Context,
        apk: File,
    ) {
        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(id).use { session ->
            session.openWrite(ReleaseFeed.APK_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result =
                PendingIntent.getBroadcast(
                    context,
                    id,
                    android.content.Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            session.commit(result.intentSender)
        }
    }

    /** True when [apk] is signed with the same certificate(s) as the installed app. */
    private fun sameSignerAsInstalled(
        context: Context,
        apk: File,
    ): Boolean {
        val pm = context.packageManager
        val installed = signerDigests(pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
        val archive = signerDigests(pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES))
        return installed != null && archive != null && installed == archive
    }

    private fun signerDigests(info: PackageInfo?): Set<String>? =
        info
            ?.signingInfo
            ?.apkContentsSigners
            ?.map { sha256Hex(it.toByteArray()) }
            ?.toSet()

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
