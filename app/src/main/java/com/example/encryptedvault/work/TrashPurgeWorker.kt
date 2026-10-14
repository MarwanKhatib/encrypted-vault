package com.example.encryptedvault.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.encryptedvault.data.VaultRepository
import java.util.concurrent.TimeUnit

/**
 * Daily purge of Recently Deleted items older than the retention window, so they go even when the app is never opened.
 * Needs no session key: the purge only reads and removes rows and encrypted files.
 */
class TrashPurgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        VaultRepository.get(applicationContext).purgeExpiredTrash()
        return Result.success()
    }

    companion object {
        private const val NAME = "trash-purge"

        /** Schedules the daily purge once; KEEP leaves an existing schedule untouched on every app start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
