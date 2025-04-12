/*
 * Copyright 2025 Trailblaze Wellness
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.trailblazewellness.fitglide.data.workers

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant

/**
 * Background worker to read total exercise duration from Health Connect for the last 24 hours.
 */
class ReadExerciseWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = coroutineScope {
        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)

        val endTime = Instant.now()
        val startTime = endTime.minusSeconds(86_400) // Last 24 hours

        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(startTime, endTime)
        )
        val exerciseSessions = healthConnectClient.readRecords(request).records
        val exerciseDuration = exerciseSessions.fold(Duration.ZERO) { total, session ->
            total.plus(Duration.between(session.startTime, session.endTime))
        }

        Log.i("ReadExerciseWorker", "Exercise duration in last 24 hours: ${exerciseDuration.toHours()}h ${exerciseDuration.toMinutesPart()}m")

        Result.success()
    }
}