package com.example.encryptedvault.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseFeedTest {
    private val base = "https://github.com/MarwanKhatib/encrypted-vault/releases/download/v0.3.0/"

    private fun json(
        apkUrl: String = base + "app-release.apk",
        shaUrl: String = base + "app-release.apk.sha256",
    ) = """
        {"tag_name":"v0.3.0","assets":[
          {"name":"app-release.apk","browser_download_url":"$apkUrl"},
          {"name":"app-release.apk.sha256","browser_download_url":"$shaUrl"}
        ]}
        """.trimIndent()

    @Test fun readsTagAndBothAssets() {
        val release = ReleaseFeed.parseLatest(json())!!
        assertEquals("v0.3.0", release.tag)
        assertEquals(base + "app-release.apk", release.apkUrl)
        assertEquals(base + "app-release.apk.sha256", release.shaUrl)
    }

    @Test fun assetFromAnotherHostIsRefused() {
        assertNull(ReleaseFeed.parseLatest(json(apkUrl = "https://evil.example/app-release.apk")))
    }

    @Test fun missingChecksumAssetIsRefused() {
        val noSha = """{"tag_name":"v0.3.0","assets":[{"name":"app-release.apk","browser_download_url":"$base/app-release.apk"}]}"""
        assertNull(ReleaseFeed.parseLatest(noSha))
    }
}
