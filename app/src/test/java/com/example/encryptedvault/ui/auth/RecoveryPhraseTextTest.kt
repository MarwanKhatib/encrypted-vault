package com.example.encryptedvault.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryPhraseTextTest {
    @Test fun writesOneNumberedWordPerLine() {
        assertEquals("1. abandon\n2. ability\n", recoveryPhraseText(listOf("abandon", "ability")))
    }
}
