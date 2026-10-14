package com.example.encryptedvault.update

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** A published release: its tag and the download URLs of the APK and its checksum. */
data class Release(
    val tag: String,
    val apkUrl: String,
    val shaUrl: String,
)

/**
 * Reads the latest GitHub release of this app over HTTPS (ADR-015). Only assets served from this repository's release
 * download path are accepted; the APK is then checked by checksum and signer before it is installed.
 */
object ReleaseFeed {
    const val REPO = "MarwanKhatib/encrypted-vault"
    const val APK_NAME = "app-release.apk"
    const val SHA_NAME = "app-release.apk.sha256"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"
    private const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"
    private const val TIMEOUT_MS = 20_000
    private const val BUFFER_BYTES = 64 * 1024

    /** Parses the GitHub "latest release" JSON. Null when the tag or the two assets are missing or point elsewhere. */
    @Suppress("ReturnCount") // one early exit per required field keeps the refusal reasons readable
    fun parseLatest(json: String): Release? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name")
        if (tag.isEmpty()) return null
        val assets = root.optJSONArray("assets") ?: return null
        val urls = mutableMapOf<String, String>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            urls[asset.optString("name")] = asset.optString("browser_download_url")
        }
        val apk = urls[APK_NAME]?.takeIf { it.startsWith(DOWNLOAD_PREFIX) } ?: return null
        val sha = urls[SHA_NAME]?.takeIf { it.startsWith(DOWNLOAD_PREFIX) } ?: return null
        return Release(tag, apk, sha)
    }

    /** The latest release, or null when offline, rate-limited, or the repo has no release. */
    fun fetchLatest(): Release? = httpGet(LATEST)?.let(::parseLatest)

    /** GET over HTTPS only. Null on any failure. */
    fun httpGet(url: String): String? =
        runCatching {
            val conn = open(url) ?: return null
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()

    /** Downloads [url] into [dest], reporting progress in 0..1 when the size is known. False on any failure. */
    fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ): Boolean =
        runCatching {
            val conn = open(url) ?: return false
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return false
                conn.inputStream.use { input -> dest.outputStream().use { copy(input, it, conn.contentLengthLong, onProgress) } }
                true
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

    /** Copies [input] to [out] in chunks, reporting progress when [total] is known. */
    private fun copy(
        input: InputStream,
        out: java.io.OutputStream,
        total: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var read = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            read += n
            if (total > 0) onProgress(read.toFloat() / total)
        }
    }

    private fun open(url: String): HttpURLConnection? {
        if (!url.startsWith("https://")) return null
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "encrypted-vault-updater")
        }
    }
}
