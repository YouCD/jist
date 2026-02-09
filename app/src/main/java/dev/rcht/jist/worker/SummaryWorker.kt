package dev.rcht.jist.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import dev.rcht.jist.JistApplication
import dev.rcht.jist.notification.SummaryNotificationManager
import dev.rcht.jist.engine.SummaryResult
import java.util.concurrent.TimeUnit

class SummaryWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val app = context.applicationContext as JistApplication
    private val notificationManager = SummaryNotificationManager(context)

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting periodic summarization work")

            // Get all pending conversations and summarize them
            val results = app.summaryEngine.summarizeAllPending()

            // Post notifications for summaries
            results.forEach { result ->
                when (result) {
                    is SummaryResult.Success -> {
                        notificationManager.postSummaryNotification(
                            summaryId = result.summaryId,
                            summaryText = result.summaryText
                        )
                    }
                    is SummaryResult.Error -> {
                        Log.w(TAG, "Summarization error: ${result.message}")
                    }
                }
            }

            Log.d(TAG, "Periodic summarization work completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic summarization", e)
            // Retry with exponential backoff
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SummaryWorker"
        const val WORK_NAME = "jist_summary_periodic"

        /**
         * Schedule periodic summarization work
         */
        fun schedule(context: Context) {
            val summarizationWork = PeriodicWorkRequestBuilder<SummaryWorker>(
                15, // interval
                TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                summarizationWork
            )

            Log.d(TAG, "Periodic summarization work scheduled (15 min interval)")
        }

        /**
         * Cancel periodic summarization work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Periodic summarization work cancelled")
        }

        /**
         * Trigger one-time immediate summarization
         */
        fun scheduleImmediate(context: Context) {
            val immediateWork = OneTimeWorkRequestBuilder<SummaryWorker>().build()
            WorkManager.getInstance(context).enqueue(immediateWork)
            Log.d(TAG, "Immediate summarization work enqueued")
        }
    }
}
