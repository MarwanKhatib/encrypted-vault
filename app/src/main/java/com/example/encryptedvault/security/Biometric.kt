package com.example.encryptedvault.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** Thin wrapper over BiometricPrompt that binds the prompt to a Keystore cipher, so the key is only usable after a strong biometric. */
object Biometric {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** True when a strong biometric is enrolled and usable on this device. */
    fun available(ctx: Context) = BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** Unwraps ContextWrappers to find the hosting FragmentActivity, or null. */
    fun Context.findActivity(): FragmentActivity? {
        var c = this
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Shows the system prompt bound to [cipher] (CryptoObject). [onSuccess] receives the now-authorised cipher.
     * [onFail] gets a message for real errors; user cancel / "Use PIN" is reported as null.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFail: (String?) -> Unit,
    ) {
        val cb =
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                    r.cryptoObject?.cipher?.let(onSuccess) ?: onFail("No cipher")
                }

                override fun onAuthenticationError(
                    code: Int,
                    msg: CharSequence,
                ) {
                    val benign =
                        code == BiometricPrompt.ERROR_USER_CANCELED ||
                            code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            code == BiometricPrompt.ERROR_CANCELED
                    onFail(if (benign) null else msg.toString())
                }
            }
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTH)
                .setNegativeButtonText("Use PIN")
                .setConfirmationRequired(false)
                .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), cb)
            .authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
