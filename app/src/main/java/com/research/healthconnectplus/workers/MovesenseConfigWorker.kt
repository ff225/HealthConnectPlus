package com.research.healthconnectplus.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.movesense.mds.Mds
import com.movesense.mds.MdsException
import com.movesense.mds.MdsHeader
import com.movesense.mds.MdsResponseListener
import com.research.healthconnectplus.bluetooth.MovesensePair

class MovesenseConfigWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private var mds: Mds = Mds.builder().build(ctx)

    override suspend fun doWork(): Result {


        // parameter to start and stop logging.
        val startStopLogging = inputData.getBoolean("startStopLogging", false)

        //start logging
        // if parameter is true, start logging
        // configure the logging parameters
        // configure data logger
        if (startStopLogging) {

            mds.delete(
                "suunto://${MovesensePair.getSerialId()}/Mem/Logbook/Entries/",
                null,
                object : MdsResponseListener {
                    override fun onSuccess(data: String?, header: MdsHeader?) {
                        Log.d("MovesenseConfigWorker", "Data deleted: $data")
                    }

                    override fun onError(error: MdsException) {
                        Log.d("MovesenseConfigWorker", "Error: ${error.message}")
                    }
                })


            mds.put("suunto://${MovesensePair.getSerialId()}/Time",
                """{"value": ${System.currentTimeMillis() * 1_000}}""",
                object : MdsResponseListener {
                    override fun onSuccess(data: String?, header: MdsHeader?) {
                        Log.d("MovesenseConfigWorker", "Time set: $data")
                    }

                    override fun onError(error: MdsException) {
                        Log.d("MovesenseConfigWorker", "Error: ${error.message}")
                    }
                })

            val jsonConfig = """{
                            "config": {
                                "dataEntries": {
                                    "dataEntry": [
                                        {
                                            "path": "/Meas/IMU9/13"
                                        }
                                    ]
                                }
                            }
                        }"""
            mds.put(
                "suunto://${MovesensePair.getSerialId()}/Mem/DataLogger/Config/",
                jsonConfig
            ) { p0 -> Log.d("MovesenseConfigWorker", "Error: ${p0?.message}") }
        }
        val jsonStartStopLogging = """{"newState": ${if (startStopLogging) "3" else "2"}}"""
        mds.put(
            "suunto://${MovesensePair.getSerialId()}/Mem/DataLogger/State",
            jsonStartStopLogging
        ) { p0 -> Log.d("MovesenseConfigWorker", "Error: ${p0?.message}") }


        // start the logging

        // if parameter is false, stop logging
        // stop the logging


        return Result.success()
    }
}