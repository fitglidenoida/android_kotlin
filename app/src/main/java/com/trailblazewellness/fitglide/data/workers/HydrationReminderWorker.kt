package com.trailblazewellness.fitglide.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trailblazewellness.fitglide.MainActivity
import java.util.concurrent.TimeUnit


class HydrationReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hydration_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Hydration Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("LOG_WATER", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Time to Hydrate!")
            .setContentText("Log a 250mL glass of water?")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(NotificationCompat.Action(0, "Log 250mL", pendingIntent))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
        return Result.success()
    }
}

// Utility function to schedule (call from MainActivity or Application)
fun scheduleHydrationReminder(context: Context) {
    val workRequest = androidx.work.PeriodicWorkRequestBuilder<HydrationReminderWorker>(2, TimeUnit.HOURS)
        .build()
    androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "hydration_reminder",
        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}