package com.trailblazewellness.fitglide.data.healthconnect

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.trailblazewellness.fitglide.data.workers.ReadExerciseWorker
import com.trailblazewellness.fitglide.data.workers.ReadSleepWorker
import com.trailblazewellness.fitglide.data.workers.ReadStepWorker
import java.util.concurrent.TimeUnit

object HealthConnectSync {
    fun enqueueWorkers(context: Context) {
        val stepRequest = OneTimeWorkRequestBuilder<ReadStepWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()
        val sleepRequest = OneTimeWorkRequestBuilder<ReadSleepWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()
        val exerciseRequest = OneTimeWorkRequestBuilder<ReadExerciseWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(listOf(stepRequest, sleepRequest, exerciseRequest))
    }
}