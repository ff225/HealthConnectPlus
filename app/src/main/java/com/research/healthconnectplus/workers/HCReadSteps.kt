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
        val isBenchmarkSync = inputData.getBoolean("benchmark_sync", false)

        if (isBenchmarkSync) {
            // Benchmark mode: read ALL data (last 100 days to catch all synthetic data)
            Log.d("HCReadSteps", "Benchmark mode: reading ALL step data")
            readStepsByTimeRange(
                healthConnectClient,
                Instant.now().minus(100, ChronoUnit.DAYS), // ⭐ 100 days back
                Instant.now(),
                stepRepo
            )
        } else {
            // Normal mode: read only last 15 minutes (production behavior)
            Log.d("HCReadSteps", "Normal mode: reading last 15 minutes")
            readStepsByTimeRange(
                healthConnectClient,
                Instant.now().minus(15, ChronoUnit.MINUTES),
                Instant.now(),
                stepRepo
            )
        }

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
        Log.d("HCReadSteps", "Reading steps from $startTime to $endTime")

        var insertedCount = 0
        var skippedCount = 0
        var pageToken: String? = null
        var pageNumber = 1
        var otherAppRecordsCount = 0

        // ⭐ FIX: Loop through ALL pages using pagination
        do {
            Log.d("HCReadSteps", "Reading page $pageNumber (token: ${pageToken?.take(10)}...)")

            try {
                val request = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    pageToken = pageToken
                )
                val response = healthConnectClient.readRecords(request)

                Log.d("HCReadSteps", "Page $pageNumber: Found ${response.records.size} step records")

                // ⭐ FIX: Filter only records from this app
                val myAppRecords = response.records.filter { record ->
                    val dataOrigin = record.metadata.dataOrigin
                    val isMyRecord = dataOrigin.packageName == "com.research.healthconnectplus"

                    if (!isMyRecord) {
                        otherAppRecordsCount++
                    }

                    isMyRecord
                }

                Log.d("HCReadSteps", "Page $pageNumber: ${myAppRecords.size} my records, ${response.records.size - myAppRecords.size} other app records")

                // Process only MY records
                for (stepRecord in myAppRecords) {
                    try {
                        stepRepo.insert(
                            StepRecord(
                                recordId = stepRecord.metadata.id,
                                count = stepRecord.count.toInt(),
                                startTime = stepRecord.startTime.toEpochMilli(),
                                endTime = stepRecord.endTime.toEpochMilli()
                            )
                        )
                        insertedCount++

                        // Log progress every 5000 records
                        if (insertedCount % 5000 == 0) {
                            Log.d("HCReadSteps", "Progress: $insertedCount records inserted so far...")
                        }

                    } catch (e: Exception) {
                        // Record probably already exists (duplicate), skip
                        skippedCount++
                    }
                }

                // ⭐ CRUCIAL: Get next page token
                pageToken = response.pageToken
                pageNumber++

                Log.d("HCReadSteps", "Page complete. Next token: ${pageToken?.take(10) ?: "null"}")

            } catch (securityException: SecurityException) {
                // ⭐ NEW: Handle permission errors gracefully
                Log.w("HCReadSteps", "Permission error on page $pageNumber, continuing: ${securityException.message}")
                break // Stop pagination if we hit permission issues
            } catch (e: Exception) {
                // ⭐ NEW: Handle other errors gracefully
                Log.w("HCReadSteps", "Error on page $pageNumber, continuing: ${e.message}")
                break
            }

        } while (pageToken != null)  // Continue until no more pages

        Log.d("HCReadSteps", "🎯 SYNC COMPLETE:")
        Log.d("HCReadSteps", "   - My records inserted: $insertedCount")
        Log.d("HCReadSteps", "   - My records skipped: $skippedCount")
        Log.d("HCReadSteps", "   - Other app records found: $otherAppRecordsCount")
        Log.d("HCReadSteps", "   - Pages processed: ${pageNumber-1}")

    } catch (e: Exception) {
        Log.e("HCReadSteps", "Fatal error reading steps: ${e.message}")
        throw e
    }
}