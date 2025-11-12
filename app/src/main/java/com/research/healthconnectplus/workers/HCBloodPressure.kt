package com.research.healthconnectplus.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class HCBloodPressure(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        // TODO: Implement the logic to read blood pressure data from Health Connect
        return Result.success()
    }
}