// ========================================
// HEALTHCONNECT SYNTHETIC DATA GENERATOR
// ========================================

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import kotlin.system.measureTimeMillis

class HealthConnectDataGenerator(
    private val healthConnectClient: HealthConnectClient
) {

    companion object {
        private const val TAG = "HCDataGenerator"

        // Step generation parameters
        private const val MIN_STEPS_PER_RECORD = 50
        private const val MAX_STEPS_PER_RECORD = 2000
        private const val RECORD_INTERVAL_MINUTES = 1L // 1 minute intervals

        // Batch size for inserting records (HealthConnect has limits)
        private const val BATCH_SIZE = 1000
    }

    // ========================================
    // MAIN GENERATION METHOD
    // ========================================

    suspend fun populateHealthConnectWithSteps(
        recordCount: Int = 25_000,  // ⭐ CHANGED: Default to 25k instead of 100k
        startFromDaysAgo: Long = 30
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Generating $recordCount synthetic step records in HealthConnect...")

        val totalTime = measureTimeMillis {

            // Clear existing data first (optional - commented out for safety)
            // clearAllStepRecords(startFromDaysAgo)

            // ⭐ FIX: Parti da ora e vai indietro nel tempo
            val baseTime = Instant.now()

            // Generate and insert in batches
            var insertedCount = 0
            for (batchStart in 0 until recordCount step BATCH_SIZE) {
                val batchEnd = minOf(batchStart + BATCH_SIZE, recordCount)
                val batchSize = batchEnd - batchStart

                val batch = generateStepRecordBatch(
                    batchStart,
                    batchEnd,
                    baseTime
                )

                try {
                    healthConnectClient.insertRecords(batch)
                    insertedCount += batchSize
                    Log.d(TAG, "Inserted batch: $insertedCount/$recordCount records")

                } catch (e: Exception) {
                    Log.e(TAG, "Error inserting batch $batchStart-$batchEnd: ${e.message}")
                    throw e
                }
            }
        }

        Log.d(TAG, "Completed HealthConnect generation: $recordCount records in ${totalTime}ms")

        // Verify insertion
        val verifyCount = countHealthConnectStepRecords(startFromDaysAgo)
        Log.d(TAG, "Verification: $verifyCount step records found in HealthConnect")

        return@withContext HealthConnectDatasetInfo(
            recordsGenerated = recordCount,
            recordsVerified = verifyCount,
            generationTimeMs = totalTime,
            timestamp = System.currentTimeMillis()
        )
    }

    // ========================================
    // BATCH GENERATION
    // ========================================

    private fun generateStepRecordBatch(
        startIndex: Int,
        endIndex: Int,
        baseTime: Instant
    ): List<StepsRecord> {

        return (startIndex until endIndex).map { i ->
            // ⭐ LOGIC: Start from now and go backwards in time
            // Record 0: now-1min to now
            // Record 1: now-2min to now-1min
            // Record 2: now-3min to now-2min
            // etc.
            val recordEndTime = baseTime.minusSeconds(i * RECORD_INTERVAL_MINUTES * 60)
            val recordStartTime = recordEndTime.minusSeconds(RECORD_INTERVAL_MINUTES * 60)

            // ⭐ VALIDATION: Ensure no future timestamps
            val now = Instant.now()
            if (recordStartTime.isAfter(now) || recordEndTime.isAfter(now)) {
                Log.w(TAG, "Warning: Generated future timestamp for record $i")
            }

            StepsRecord(
                count = (MIN_STEPS_PER_RECORD..MAX_STEPS_PER_RECORD).random().toLong(),
                startTime = recordStartTime,
                endTime = recordEndTime,
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC
            )
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    suspend fun countHealthConnectStepRecords(
        daysAgo: Long = 30
    ): Int = withContext(Dispatchers.IO) {

        return@withContext try {
            var totalCount = 0
            var pageToken: String? = null

            // ⭐ FIX: Use pagination and filter by ownership
            do {
                val request = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.now().minusSeconds(daysAgo * 24 * 3600),
                        Instant.now()
                    ),
                    pageToken = pageToken
                )

                try {
                    val response = healthConnectClient.readRecords(request)

                    // ⭐ FIX: Count only records from this app
                    val myRecords = response.records.filter { record ->
                        record.metadata.dataOrigin.packageName == "com.research.healthconnectplus"
                    }

                    totalCount += myRecords.size
                    pageToken = response.pageToken

                    Log.d(TAG, "Count page: ${myRecords.size} my records, ${response.records.size - myRecords.size} other app records")

                } catch (securityException: SecurityException) {
                    // Permission error, stop counting
                    Log.w(TAG, "Permission error while counting, stopping")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Error while counting: ${e.message}")
                    break
                }

            } while (pageToken != null)

            Log.d(TAG, "Total count of my records: $totalCount")
            totalCount

        } catch (e: Exception) {
            Log.e(TAG, "Error counting HealthConnect records: ${e.message}")
            0
        }
    }

    suspend fun clearAllStepRecords(
        daysAgo: Long = 30
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Clearing step records owned by this app from HealthConnect...")

        try {
            // Read all records first
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.now().minusSeconds(daysAgo * 24 * 3600),
                    Instant.now()
                )
            )
            val response = healthConnectClient.readRecords(request)

            // ⭐ FIX: Filter only records that this app can delete
            // We'll try to delete all records, but catch ownership errors
            val recordIds = response.records.map { it.metadata.id }
            var deletedCount = 0
            var skippedCount = 0

            if (recordIds.isNotEmpty()) {
                // Try to delete in smaller batches to handle ownership issues
                val batchSize = 100
                for (batch in recordIds.chunked(batchSize)) {
                    try {
                        healthConnectClient.deleteRecords(StepsRecord::class, batch, emptyList())
                        deletedCount += batch.size
                        Log.d(TAG, "Deleted batch of ${batch.size} records")

                    } catch (e: Exception) {
                        // Skip records we don't own
                        skippedCount += batch.size
                        Log.w(TAG, "Skipped batch of ${batch.size} records (not owned by this app)")
                    }
                }

                Log.d(TAG, "Deletion complete: $deletedCount deleted, $skippedCount skipped")
            } else {
                Log.d(TAG, "No records found to delete")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing HealthConnect records: ${e.message}")
            throw e
        }
    }

    suspend fun clearMyAppStepRecords(
        daysAgo: Long = 7  // ⭐ REVERT: Keep original 7 days since SettingsScreen uses this
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Clearing step records owned by this app from HealthConnect...")

        try {
            // Read all records from last week
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.now().minusSeconds(daysAgo * 24 * 3600),
                    Instant.now()
                )
            )
            val response = healthConnectClient.readRecords(request)

            Log.d(TAG, "Found ${response.records.size} total step records")

            // ⭐ FIX: Filter only records owned by this app
            val myAppRecords = response.records.filter { record ->
                // Check if this record was inserted by our app
                val dataOrigin = record.metadata.dataOrigin
                val isMyRecord = dataOrigin.packageName == "com.research.healthconnectplus"

                if (isMyRecord) {
                    Log.d(TAG, "Found my record: ${record.metadata.id} from ${dataOrigin.packageName}")
                }

                isMyRecord
            }

            Log.d(TAG, "Found ${myAppRecords.size} records owned by this app")

            if (myAppRecords.isNotEmpty()) {
                val recordIds = myAppRecords.map { it.metadata.id }

                // Delete in batches
                val batchSize = 100
                var deletedCount = 0

                for (batch in recordIds.chunked(batchSize)) {
                    try {
                        healthConnectClient.deleteRecords(StepsRecord::class, batch, emptyList())
                        deletedCount += batch.size
                        Log.d(TAG, "Deleted batch of ${batch.size} my records")

                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting batch: ${e.message}")
                        // Continue with next batch
                    }
                }

                Log.d(TAG, "Deletion complete: $deletedCount of ${myAppRecords.size} records deleted")

            } else {
                Log.d(TAG, "No records owned by this app found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing my app records: ${e.message}")
            throw e
        }
    }

    suspend fun debugRecordOwnership(
        daysAgo: Long = 1
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Debug: Checking record ownership for last $daysAgo days...")

        try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.now().minusSeconds(daysAgo * 24 * 3600),
                    Instant.now()
                )
            )
            val response = healthConnectClient.readRecords(request)

            // Group records by package name
            val recordsByApp = response.records.groupBy { record ->
                record.metadata.dataOrigin.packageName
            }

            Log.d(TAG, "=== RECORD OWNERSHIP DEBUG ===")
            recordsByApp.forEach { (packageName, records) ->
                Log.d(TAG, "App: $packageName - ${records.size} records")
                records.take(3).forEach { record ->
                    Log.d(TAG, "  Sample: ${record.count} steps, ${record.startTime}")
                }
            }
            Log.d(TAG, "=== END DEBUG ===")

        } catch (e: Exception) {
            Log.e(TAG, "Error debugging ownership: ${e.message}")
        }
    }

    suspend fun getHealthConnectStats(
        daysAgo: Long = 30
    ): HealthConnectStats = withContext(Dispatchers.IO) {

        val statsTime = measureTimeMillis {
            val count = countHealthConnectStepRecords(daysAgo)

            return@withContext HealthConnectStats(
                totalStepRecords = count,
                queryTimeMs = 0, // Will be set below
                timestamp = System.currentTimeMillis()
            )
        }

        return@withContext HealthConnectStats(
            totalStepRecords = countHealthConnectStepRecords(daysAgo),
            queryTimeMs = statsTime,
            timestamp = System.currentTimeMillis()
        )
    }

    // ========================================
    // BENCHMARK SETUP HELPER
    // ========================================

    suspend fun setupBenchmarkDataset(
        recordCount: Int = 25_000  // ⭐ CHANGED: Default to 25k
    ): HealthConnectDatasetInfo = withContext(Dispatchers.IO) {

        Log.d(TAG, "Setting up HealthConnect benchmark dataset...")

        // Check if data already exists
        val existingCount = countHealthConnectStepRecords()

        if (existingCount >= recordCount) {
            Log.d(TAG, "Sufficient data already exists: $existingCount records")
            return@withContext HealthConnectDatasetInfo(
                recordsGenerated = 0,
                recordsVerified = existingCount,
                generationTimeMs = 0,
                timestamp = System.currentTimeMillis()
            )
        }

        // Generate missing data
        val toGenerate = recordCount - existingCount
        Log.d(TAG, "Generating $toGenerate additional records...")

        return@withContext populateHealthConnectWithSteps(toGenerate)
    }
}

// ========================================
// DATA CLASSES
// ========================================

data class HealthConnectDatasetInfo(
    val recordsGenerated: Int,
    val recordsVerified: Int,
    val generationTimeMs: Long,
    val timestamp: Long
) {
    val isSuccessful: Boolean get() = recordsVerified >= recordsGenerated
}

data class HealthConnectStats(
    val totalStepRecords: Int,
    val queryTimeMs: Long,
    val timestamp: Long
)

// ========================================
// USAGE EXAMPLE
// ========================================

/*
// In HC+ or any app with HealthConnect access
class BenchmarkSetupActivity {

    private val healthConnectClient = HealthConnectClient.getOrCreate(this)
    private val dataGenerator = HealthConnectDataGenerator(healthConnectClient)

    fun setupBenchmarkData() {
        lifecycleScope.launch {
            try {
                // Setup 25k records for benchmark
                val info = dataGenerator.setupBenchmarkDataset(25_000)  // ⭐ UPDATED: 25k instead of 100k
                
                if (info.isSuccessful) {
                    Log.d("Setup", "Benchmark ready: ${info.recordsVerified} records")
                    showSuccess("Benchmark data ready!")
                } else {
                    Log.e("Setup", "Setup failed: ${info.recordsGenerated} vs ${info.recordsVerified}")
                    showError("Setup failed")
                }
                
            } catch (e: Exception) {
                Log.e("Setup", "Error setting up benchmark: ${e.message}")
                showError("Setup error: ${e.message}")
            }
        }
    }
    
    fun checkDataStatus() {
        lifecycleScope.launch {
            val stats = dataGenerator.getHealthConnectStats()
            Log.d("Status", "HealthConnect has ${stats.totalStepRecords} step records")
        }
    }
    
    fun clearBenchmarkData() {
        lifecycleScope.launch {
            dataGenerator.clearAllStepRecords()
            Log.d("Clear", "Benchmark data cleared")
        }
    }
}
*/