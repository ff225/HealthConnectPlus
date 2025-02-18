package com.research.healthconnectplus.workers

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.research.healthconnectplus.HealthConnectApp
import com.research.healthconnectplus.data.HeartRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class HCReadHeartRate(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private val healthConnectClient = HealthConnectClient.getOrCreate(context)
    private val heartRateRepo =
        (context.applicationContext as HealthConnectApp).appRepoContainer.heartRepository
    override suspend fun doWork(): Result {

        readHeartRate(
            healthConnectClient,
            Instant.now().minus(15, ChronoUnit.MINUTES),
            Instant.now(),
            heartRateRepo
        )

        return Result.success()
    }
}

suspend fun readHeartRate(
    healthConnectClient: HealthConnectClient,
    startTime: Instant,
    endTime: Instant,
    heartRateRepo: HeartRepository
) {
    try {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        for (heartRateRecord in response.records) {
            Log.d("HCReadHeartRate", "Heart rate: ${heartRateRecord.samples}")
            heartRateRepo.insert(
                com.research.healthconnectplus.data.HeartRecord(
                    recordId = heartRateRecord.metadata.id,
                    bpm = heartRateRecord.samples.toString(),
                    startTime = heartRateRecord.startTime.toEpochMilli(),
                    endTime = heartRateRecord.endTime.toEpochMilli()
                )
            )
        }
    } catch (e: Exception) {
        // Run error handling here
    }
}