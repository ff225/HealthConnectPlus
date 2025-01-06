package com.research.healthconnectplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.research.healthconnectplus.data.AppRepoContainer
import com.research.healthconnectplus.data.AppRepoContainerImpl

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

    }
}