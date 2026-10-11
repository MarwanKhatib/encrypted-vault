package com.example.encryptedvault.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug

/**
 * Runtime tamper checks for hardened (release) builds.
 *
 * What this does and does not do: these checks raise the cost of casual reverse engineering (a repackaged APK that was flipped to
 * `debuggable`, or a debugger attached to read memory). They cannot stop a determined attacker with root or a hooking framework,
 * because such an attacker controls the very code that runs the check. The real protection of the photos is cryptographic: the
 * master key is never stored in the clear, the PIN layer is wrapped by a hardware-backed Keystore key, and the code that does this
 * is not secret (Kerckhoffs's principle). Obfuscation and these checks are defence in depth, not the lock.
 *
 * Root detection is deliberately not part of this (design decision, Gap 7): root-hiding tools defeat it, and StrongBox
 * protects the key material even on a rooted device.
 */
object Integrity {

    /** Why the app refuses to run. */
    enum class Violation { DEBUGGABLE_RELEASE, DEBUGGER_ATTACHED }

    /**
     * Pure decision so it can be unit-tested: a hardened build must not be debuggable and must not have a debugger attached.
     * Development (non-hardened) builds are never refused.
     */
    fun evaluate(hardenedBuild: Boolean, debuggable: Boolean, debuggerAttached: Boolean): Violation? = when {
        !hardenedBuild -> null
        debuggable -> Violation.DEBUGGABLE_RELEASE
        debuggerAttached -> Violation.DEBUGGER_ATTACHED
        else -> null
    }

    /** Reads the live state of this process and applies [evaluate]. */
    fun check(context: Context, hardenedBuild: Boolean): Violation? = evaluate(
        hardenedBuild = hardenedBuild,
        debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
    )
}
