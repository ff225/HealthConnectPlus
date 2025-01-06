package com.research.healthconnectplus.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movesense.mds.Mds
import com.movesense.mds.MdsException
import com.movesense.mds.MdsHeader
import com.movesense.mds.MdsResponseListener
import com.research.healthconnectplus.HealthConnectApp
import com.research.healthconnectplus.bluetooth.MovesensePair
import com.research.healthconnectplus.data.MovesenseRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class MovesenseStoreDataWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    private val mds = Mds.builder().build(ctx)
    private val movesenseRepo =
        (ctx.applicationContext as HealthConnectApp).appRepoContainer.movesenseRepository

    private val workManager = WorkManager.getInstance(ctx)

    override suspend fun doWork(): Result {
        /*
        This worker is used to store data from Movesense device to the database.
         1) stop logging,
         2) get data from Movesense device
         3) store data in the database
         4) delete data from Movesense device
         5) restart logging
        */

        var instant = Instant.now()

        workManager.enqueue(
            OneTimeWorkRequestBuilder<MovesenseConfigWorker>()
                .setInputData(workDataOf("startStopLogging" to false))
                .build()
        ).await()

        mds.get(
            "suunto://${MovesensePair.getSerialId()}/Mem/Logbook/Entries/",
            null,
            object : MdsResponseListener {
                override fun onSuccess(data: String?, header: MdsHeader?) {
                    println("Data: $data")
                }

                override fun onError(p0: MdsException?) {
                    Log.e("MovesenseStoreDataWorker", "Error: ${p0?.message}")
                }

            })

        mds.get(
            "suunto://MDS/Logbook/${MovesensePair.getSerialId()}/byId/1/Data",
            null,
            object : MdsResponseListener {
                override fun onSuccess(data: String?, header: MdsHeader?) {
                    println("Data: $data")

                    val jsonElement: JsonElement =
                        JsonParser.parseString(data)
                    val jsonObject: JsonObject = jsonElement.asJsonObject
                    val samples = jsonObject
                        .getAsJsonObject("Meas")
                        .getAsJsonArray("IMU9")


                    val movesenseRecords = mutableListOf<MovesenseRecord>()
                    val duration = Duration.ofNanos(((1.0 / 13) * 1_000_000_000).toLong())
                    for (sample in samples.reversed()) {
                        val accObject = sample.asJsonObject
                        println("AccObject: $accObject")
                        movesenseRecords.add(
                            MovesenseRecord(
                                0, instant.toEpochMilli().toString(),
                                accObject.get("ArrayAcc").asJsonArray[0].asJsonObject.get("x").asFloat.toString(),
                                accObject.get("ArrayAcc").asJsonArray[0].asJsonObject.get("y").asFloat.toString(),
                                accObject.get("ArrayAcc").asJsonArray[0].asJsonObject.get("z").asFloat.toString(),
                                accObject.get("ArrayGyro").asJsonArray[0].asJsonObject.get("x").asFloat.toString(),
                                accObject.get("ArrayGyro").asJsonArray[0].asJsonObject.get("y").asFloat.toString(),
                                accObject.get("ArrayGyro").asJsonArray[0].asJsonObject.get("z").asFloat.toString(),
                                accObject.get("ArrayMagn").asJsonArray[0].asJsonObject.get("x").asFloat.toString(),
                                accObject.get("ArrayMagn").asJsonArray[0].asJsonObject.get("y").asFloat.toString(),
                                accObject.get("ArrayMagn").asJsonArray[0].asJsonObject.get("z").asFloat.toString(),
                                false, isProcessed = false
                            )
                        )
                        instant = instant.minus(duration)
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        movesenseRecords.reversed().forEach {
                            movesenseRepo.insert(
                                it
                            )
                        }
                    }
                    /*
                    mds.delete(
                        "suunto://${MovesensePair.getSerialId()}/Mem/Logbook/Entries/",
                        null,
                        object : MdsResponseListener {
                            override fun onSuccess(data: String?, header: MdsHeader?) {
                                Log.d("MovesenseStoreDataWorker", "Data deleted: $data")
                            }

                            override fun onError(p0: MdsException?) {
                                Log.e("MovesenseStoreDataWorker", "Error: ${p0?.message}")
                            }

                        })

                     */
                }


                override fun onError(p0: MdsException?) {
                    Log.e("MovesenseStoreDataWorker", "Error: ${p0?.message}")
                }

            })

        workManager.enqueue(
            OneTimeWorkRequestBuilder<MovesenseConfigWorker>()
                .setInputData(workDataOf("startStopLogging" to true))
                .build()
        ).await()


        return Result.success()
    }
}