package com.example.encryptedvault.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/** Offline BIP39: 256-bit entropy <-> 24 words. Wordlist from assets/bip39_english.txt. */
class Bip39(
    private val words: List<String>,
) {
    init {
        require(words.size == 2048) { "wordlist must have 2048 entries" }
    }

    /** 256 bits from the system CSPRNG (24 words). */
    fun newEntropy(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** Entropy plus an 8-bit SHA-256 checksum, split into 11-bit indexes into the word list. */
    fun encode(entropy: ByteArray): List<String> {
        require(entropy.size == 32)
        val checksum = MessageDigest.getInstance("SHA-256").digest(entropy)[0]
        val bits = StringBuilder()
        entropy.forEach { bits.append(it.toUByte().toString(2).padStart(8, '0')) }
        bits.append(checksum.toUByte().toString(2).padStart(8, '0')) // 256/32 = 8 checksum bits
        return bits.chunked(11).map { words[it.toInt(2)] }
    }

    /** Returns entropy, or null if any word is unknown or the checksum fails. */
    fun decode(phrase: List<String>): ByteArray? {
        if (phrase.size != 24) return null
        val bits = StringBuilder()
        for (w in phrase) {
            val i = words.indexOf(w.trim().lowercase())
            if (i < 0) return null
            bits.append(i.toString(2).padStart(11, '0'))
        }
        val entropy = ByteArray(32) { bits.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
        val expected =
            MessageDigest
                .getInstance("SHA-256")
                .digest(entropy)[0]
                .toUByte()
                .toString(2)
                .padStart(8, '0')
        return if (bits.substring(256) == expected) entropy else null
    }
}
