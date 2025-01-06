package com.research.healthconnectplus.workers

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.research.healthconnectplus.HealthConnectApp
import com.research.healthconnectplus.data.StepRecord
import com.research.healthconnectplus.data.StepRepository
import java.time.Instant

class HCReadSteps(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val healthConnectClient = HealthConnectClient.getOrCreate(context)
    private val stepRepo = (context.applicationContext as HealthConnectApp).appRepoContainer.stepRepository

    override suspend fun doWork(): Result {
        readStepsByTimeRange(
            healthConnectClient,
            Instant.now().minusSeconds(60 * 20),
            Instant.now(),
            stepRepo
        )
        return Result.success()
    }
}

suspend fun readStepsByTimeRange(
    healthConnectClient: HealthConnectClient,
    startTime: Instant,
    endTime: Instant,
    stepRepo: StepRepository
) {
    try {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        for (stepRecord in response.records) {
            Log.d("HCReadSteps", "Step count: ${stepRecord.count}")
            // Store in database
            stepRepo.insert(
                StepRecord(
                    0,
                    stepRecord.count.toInt(),
                    stepRecord.startTime.toEpochMilli(),
                    stepRecord.endTime.toEpochMilli()
                )
            )

        }
    } catch (e: Exception) {
        // Run error handling here
    }
}