package com.research.healthconnectplus

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.research.healthconnectplus.workers.HCReadHeartRate
import com.research.healthconnectplus.workers.HCReadSteps
import java.util.concurrent.TimeUnit

class HealthConnectPlusService : Service() {
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
    // In this service we activate the WorkManager about collect data from HC and store it in the database

    // for this service we need FOREGROUND_SERVICE_HEALTH permission
    // This permission need at least ACTIVITY RECOGNITIONS permission

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("HealthConnectPlusService", "Service started")
        val notification = NotificationCompat.Builder(this, "health_connect_plus")
            .setContentTitle("Health Connect Plus")
            .setContentText("Collecting data")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HCReadSteps",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HCReadSteps>(20, TimeUnit.MINUTES).build()
        )

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HCReadHeartRate",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HCReadHeartRate>(20, TimeUnit.MINUTES).build()
        )

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("HealthConnectPlusService", "Service destroyed")

        WorkManager.getInstance(this).cancelUniqueWork("HCReadSteps")
        WorkManager.getInstance(this).cancelUniqueWork("HCReadHeartRate")
        WorkManager.getInstance(this).cancelAllWorkByTag("MovesenseStoreDataWorker")
        // For movesense work, stop logging then stop store data worker

        stopSelf()
    }


}