package com.example.encryptedvault.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Receives the outcome of [UpdateController]'s install session. Not exported: only the system installer sends to it. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION") // getParcelableExtra(name, class) needs API 33; minSdk is 28
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android's own "Install this update?" dialog. The user taps Install; nothing installs before that.
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateController.finished(context, null)
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateController.finished(context, "Install failed (status $status): ${detail ?: "no detail"}")
            }
        }
    }
}
