package com.household.finance.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.household.finance.MainActivity
import com.household.finance.data.AppSettings
import com.household.finance.data.FirestoreFinanceRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "loan_reminders"
private const val WORK_NAME = "loan_reminder_daily"

/** Once-a-day check for unsettled IOUs due today or overdue, posting a local notification per one. */
class LoanReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val nameMe = settings.currentProfileFlow.first().orEmpty()
        if (nameMe.isBlank()) return Result.success()
        val config = settings.currentFirebaseConfig()
        if (!config.isComplete) return Result.success()

        val repository = FirestoreFinanceRepository(applicationContext)
        repository.configure(config)
        val loans = repository.observeLoans().first()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

        val due = loans.filter {
            !it.settled && it.dueDate != null && it.dueDate <= today && (it.lender == nameMe || it.borrower == nameMe)
        }
        if (due.isNotEmpty()) notify(due.size, nameMe)
        return Result.success()
    }

    private fun notify(count: Int, nameMe: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "IOU due-date reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("start_route", "dashboard")
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (count == 1) "An IOU is due" else "$count IOUs are due")
            .setContentText("Open Household Finance to review and settle up.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }
}

object LoanReminderScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<LoanReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
