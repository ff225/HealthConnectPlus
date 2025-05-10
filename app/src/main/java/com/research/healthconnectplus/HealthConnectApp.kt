package com.research.healthconnectplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.research.healthconnectplus.data.AppRepoContainer
import com.research.healthconnectplus.data.AppRepoContainerImpl
import com.research.healthconnectplus.workers.HCReadHeartRate
import com.research.healthconnectplus.workers.HCReadSteps
import java.util.concurrent.TimeUnit

class HealthConnectApp : Application() {


    lateinit var appRepoContainer: AppRepoContainer


    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(
            "health_connect_plus",
            "Health Connect Plus",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Health Connect Plus"
        }

        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        appRepoContainer = AppRepoContainerImpl(this)

        val preferencesManager = PreferencesManager(this)
        val workManager = WorkManager.getInstance(this)

        preferencesManager.getEnabledHealthDataTypes().forEach {
            when (it) {
                "steps" -> {
                    if (preferencesManager.getCollectStepData()) {
                        workManager.enqueueUniquePeriodicWork(
                            "HCReadSteps",
                            ExistingPeriodicWorkPolicy.KEEP,
                            PeriodicWorkRequestBuilder<HCReadSteps>(15, TimeUnit.MINUTES).build()
                        )
                    }
                }

                "heart_rate" -> {
                    if (preferencesManager.getCollectHeartRateData()) {
                        workManager.enqueueUniquePeriodicWork(
                            "HCReadHeartRate",
                            ExistingPeriodicWorkPolicy.KEEP,
                            PeriodicWorkRequestBuilder<HCReadHeartRate>(
                                15,
                                TimeUnit.MINUTES
                            ).build()
                        )
                    }
                }
            }
        }
    }
}