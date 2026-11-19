package br.com.lugedevelopment.notificationhistory.retention

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import br.com.lugedevelopment.notificationhistory.ingest.RetentionPolicy
import java.util.concurrent.TimeUnit

/**
 * Daily cleanup: only calls [Ingestor.applyRetention]; all rules live in the Ingestor.
 * The policy comes from input data (days; a value <= 0 means "keep forever"),
 * falling back to [RetentionPolicy] defaults.
 */
class RetentionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val policy = RetentionPolicy(
            generalDays = days(KEY_GENERAL_DAYS, RetentionPolicy.DEFAULT_GENERAL_DAYS),
            deletedDays = days(KEY_DELETED_DAYS, RetentionPolicy.DEFAULT_DELETED_DAYS),
        )
        val db = HistoryDatabase.build(applicationContext)
        return try {
            Ingestor(db).applyRetention(policy)
            Result.success()
        } finally {
            db.close()
        }
    }

    private fun days(key: String, default: Long): Long? =
        inputData.getLong(key, default).takeIf { it > 0 }

    companion object {
        const val UNIQUE_NAME = "retention-cleanup"
        const val KEY_GENERAL_DAYS = "generalDays"
        const val KEY_DELETED_DAYS = "deletedDays"

        /** Schedules (or updates) the daily cleanup with the given policy. */
        fun schedule(context: Context, policy: RetentionPolicy = RetentionPolicy()) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setInputData(
                    workDataOf(
                        KEY_GENERAL_DAYS to (policy.generalDays ?: 0L),
                        KEY_DELETED_DAYS to (policy.deletedDays ?: 0L),
                    ),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
