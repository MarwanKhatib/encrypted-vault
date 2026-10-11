package com.example.encryptedvault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntegrityTest {
    @Test fun developmentBuildsAreNeverRefused() {
        assertNull(Integrity.evaluate(hardenedBuild = false, debuggable = true, debuggerAttached = true))
    }

    @Test fun cleanHardenedBuildRuns() = assertNull(Integrity.evaluate(hardenedBuild = true, debuggable = false, debuggerAttached = false))

    @Test fun hardenedBuildFlippedToDebuggableIsRefused() =
        assertEquals(Integrity.Violation.DEBUGGABLE_RELEASE, Integrity.evaluate(true, debuggable = true, debuggerAttached = false))

    @Test fun hardenedBuildWithADebuggerIsRefused() =
        assertEquals(Integrity.Violation.DEBUGGER_ATTACHED, Integrity.evaluate(true, debuggable = false, debuggerAttached = true))

    @Test fun debuggableTakesPrecedenceWhenBothApply() =
        assertEquals(Integrity.Violation.DEBUGGABLE_RELEASE, Integrity.evaluate(true, debuggable = true, debuggerAttached = true))
}
