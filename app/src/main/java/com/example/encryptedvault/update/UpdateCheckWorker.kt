package com.example.encryptedvault.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.encryptedvault.BuildConfig
import com.example.encryptedvault.MainActivity
import java.util.concurrent.TimeUnit

/**
 * Once a day, when online: asks GitHub for a newer release and posts a notification that opens Settings → Updates (ADR-015).
 * It only notifies. Downloading and installing start from the user's tap.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val release = ReleaseFeed.fetchLatest() ?: return Result.success()
        if (UpdateRules.isNewer(BuildConfig.VERSION_NAME, release.tag)) notify(applicationContext, release.tag)
        return Result.success()
    }

    companion object {
        private const val NAME = "update-check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 7001

        /** Schedules the daily check once; KEEP leaves an existing schedule alone on every app start. */
        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(
            context: Context,
            tag: String,
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT))
            }
            // Android 13+ needs the user's permission to show notifications; without it, the Settings badge still works.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val open =
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val note =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Vault $tag is available")
                    .setContentText("Open Settings → Updates to download and install it.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, note)
        }
    }
}
