package com.delizioso.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.delizioso.app.DeliziosoApplication
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DeliziosoApplication).container
        return when (container.syncEngine.sync()) {
            is SyncOutcome.Failed -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            SyncOutcome.Conflict -> {
                container.syncNotifier.showConflict()
                Result.success()
            }
            else -> {
                // The documents for the assistant follow a successful sync; they never decide its result.
                container.docsSync.run()
                Result.success()
            }
        }
    }

    companion object {
        private const val ONE_OFF = "snapsync"
        private const val DAILY = "snapsync-daily"
        private const val MAX_RETRIES = 3

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** App start/stop: queued behind any run in progress. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).build(),
            )
        }

        /** Safety net for a device that's rarely opened. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS).setConstraints(online).build(),
            )
        }
    }
}
