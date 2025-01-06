package com.research.healthconnectplus.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.research.healthconnectplus.HealthConnectApp
import com.research.healthconnectplus.InfluxApi.retrofitService
import com.research.healthconnectplus.InfluxDB
import com.research.healthconnectplus.data.toInfluxFormat

class SendDataToInflux(ctx: Context, workParams: WorkerParameters) :
    CoroutineWorker(ctx, workParams) {

    private val movesenseRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.movesenseRepository
    private val stepRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.stepRepository
    private val heartRateRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.heartRepository

    private val influx = InfluxDB.client.makeWriteApi()

    override suspend fun doWork(): Result {

        val inputData = inputData.getInt("data", 0)
        Log.d("SendDataToInflux", "Data: $inputData")


        when (inputData) {
            1 -> movesenseRepo.getUnsyncedRecords().forEach {
                val data = it.toInfluxFormat()
                try {
                    retrofitService.writeData(data = data)
                    Log.d("SendDataToInflux", "Data: $data")
                    //influx.writeRecord(WritePrecision.MS, data)
                } catch (e: Exception) {
                    Log.e("SendDataToInflux", "Error: ${e.message}")
                    return Result.failure()
                }

                movesenseRepo.update(it.copy(isSynced = true))
            }

            2 -> stepRepo.getUnsyncedStepRecords().forEach {
                val data = it.toInfluxFormat()
                try {
                    retrofitService.writeData(data = data)
                    Log.d("SendDataToInflux", "Data: $data")
                    //influx.writeRecord(WritePrecision.MS, data)
                } catch (e: Exception) {
                    Log.e("SendDataToInflux", "Error: ${e.message}")
                    return Result.failure()
                }

                stepRepo.update(it.copy(isSynced = true))
            }

            3 -> heartRateRepo.getUnsyncedHeartRecords().forEach {
                val data = it.toInfluxFormat()
                try {
                    retrofitService.writeData(data = data)
                    Log.d("SendDataToInflux", "Data: $data")
                    //influx.writeRecord(WritePrecision.MS, data)
                } catch (e: Exception) {
                    Log.e("SendDataToInflux", "Error: ${e.message}")
                    return Result.failure()
                }

                heartRateRepo.update(it.copy(isSynced = true))
            }

            else -> {}
        }



        return Result.success()
    }
}