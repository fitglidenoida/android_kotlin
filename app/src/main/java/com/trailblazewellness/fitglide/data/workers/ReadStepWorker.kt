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
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime

/**
 * Background worker to read total steps from Health Connect for the last 24 hours.
 */
class ReadStepWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = coroutineScope {
        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)

        val endTime = LocalDateTime.now()
        val startTime = endTime.minusHours(24)

        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
        val response = healthConnectClient.aggregate(request)
        val stepsCount = (response[StepsRecord.COUNT_TOTAL] ?: 0).toInt()

        Log.i("ReadStepWorker", "Steps in last 24 hours: $stepsCount")

        Result.success()
    }
}