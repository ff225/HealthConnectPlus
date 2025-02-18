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
import java.time.temporal.ChronoUnit

class HCReadSteps(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val healthConnectClient = HealthConnectClient.getOrCreate(context)
    private val stepRepo =
        (context.applicationContext as HealthConnectApp).appRepoContainer.stepRepository

    override suspend fun doWork(): Result {
        readStepsByTimeRange(
            healthConnectClient,
            Instant.now().minus(15, ChronoUnit.MINUTES),
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
            stepRepo.insert(
                StepRecord(
                    recordId = stepRecord.metadata.id,
                    count = stepRecord.count.toInt(),
                    startTime = stepRecord.startTime.toEpochMilli(),
                    endTime = stepRecord.endTime.toEpochMilli()
                )
            )

        }
    } catch (e: Exception) {
        // Run error handling here
    }
}