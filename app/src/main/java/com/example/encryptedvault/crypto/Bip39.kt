package com.example.encryptedvault.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Offline BIP-39: 256 bits of entropy plus an 8-bit checksum, written as 24 words of 11 bits each.
 * The 2048-word list comes from assets/bip39_english.txt.
 */
class Bip39(
    private val words: List<String>,
) {
    init {
        require(words.size == WORD_LIST_SIZE) { "wordlist must have $WORD_LIST_SIZE entries" }
    }

    /** 256 bits from the system CSPRNG (24 words). */
    fun newEntropy(): ByteArray = ByteArray(ENTROPY_BYTES).also { SecureRandom().nextBytes(it) }

    /** Entropy plus its SHA-256 checksum, split into 11-bit indexes into the word list. */
    fun encode(entropy: ByteArray): List<String> {
        require(entropy.size == ENTROPY_BYTES)
        val checksum = MessageDigest.getInstance(HASH).digest(entropy)[0]
        val bits = StringBuilder()
        entropy.forEach { bits.append(it.toUByte().toString(2).padStart(BYTE_BITS, '0')) }
        bits.append(checksum.toUByte().toString(2).padStart(BYTE_BITS, '0'))
        return bits.chunked(WORD_BITS).map { words[it.toInt(2)] }
    }

    /** Returns the entropy, or null if the phrase has the wrong length, an unknown word, or a bad checksum. */
    fun decode(phrase: List<String>): ByteArray? {
        if (phrase.size != WORD_COUNT) return null
        val bits = StringBuilder()
        for (w in phrase) {
            val index = words.indexOf(w.trim().lowercase())
            if (index < 0) return null
            bits.append(index.toString(2).padStart(WORD_BITS, '0'))
        }
        val entropyBits = ENTROPY_BYTES * BYTE_BITS
        val entropy = ByteArray(ENTROPY_BYTES) { bits.substring(it * BYTE_BITS, it * BYTE_BITS + BYTE_BITS).toInt(2).toByte() }
        val expected =
            MessageDigest
                .getInstance(HASH)
                .digest(entropy)[0]
                .toUByte()
                .toString(2)
                .padStart(BYTE_BITS, '0')
        return if (bits.substring(entropyBits) == expected) entropy else null
    }

    private companion object {
        const val WORD_LIST_SIZE = 2048
        const val WORD_BITS = 11
        const val WORD_COUNT = 24
        const val ENTROPY_BYTES = 32
        const val BYTE_BITS = 8
        const val HASH = "SHA-256"
    }
}
