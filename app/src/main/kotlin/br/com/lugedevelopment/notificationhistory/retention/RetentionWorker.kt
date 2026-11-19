package br.com.lugedevelopment.notificationhistory.retention

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.lugedevelopment.notificationhistory.NotificationHistoryApp
import br.com.lugedevelopment.notificationhistory.ingest.RetentionPolicy
import br.com.lugedevelopment.notificationhistory.settings.RetentionSettings
import java.util.concurrent.TimeUnit

/**
 * Daily cleanup: only calls [br.com.lugedevelopment.notificationhistory.ingest.Ingestor.applyRetention];
 * all rules live in the Ingestor. Uses the app's shared Ingestor (encrypted DB) and the
 * user's [RetentionSettings] read at run time (UI 0 = never expire = null in the policy).
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = RetentionSettings.from(applicationContext)
        val policy = RetentionPolicy(
            generalDays = settings.generalDays.toDaysOrNull(),
            deletedDays = settings.deletedDays.toDaysOrNull(),
        )
        val app = applicationContext as NotificationHistoryApp
        app.ingestor.applyRetention(policy)
        return Result.success()
    }

    private fun Int.toDaysOrNull(): Long? = takeIf { it > 0 }?.toLong()

    companion object {
        const val UNIQUE_NAME = "retention-cleanup"

        /** Schedules the daily cleanup (idempotent; keeps an existing schedule). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
