package com.research.healthconnectplus.screen

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.research.healthconnectplus.PreferencesManager
import com.research.healthconnectplus.bluetooth.AndroidBluetoothController
import com.research.healthconnectplus.data.AppRepoContainerImpl
import com.research.healthconnectplus.data.RepositoryProvider
import com.research.healthconnectplus.screen.home.HomeViewModel
import com.research.healthconnectplus.screen.movesense.MovesenseViewModel
import com.research.healthconnectplus.screen.profile.ProfileViewModel
import com.research.healthconnectplus.screen.settings.DataViewModel

object AppViewModelProvider {


    fun provideViewModelFactory(context: Context, dataType: String? = null) = viewModelFactory {
        initializer {
            MovesenseViewModel(AndroidBluetoothController(context.applicationContext))
        }
        initializer {
            ProfileViewModel(userRepository = AppRepoContainerImpl(context).userRepository)
        }

        initializer {
            HomeViewModel(
                healthConnectClient = HealthConnectClient.getOrCreate(context),
                //healthDataRepository = AppRepoContainerImpl(context).healthDataRepository,
                preferencesManager = PreferencesManager(context),
                //workManager = AppRepoContainerImpl(context).workManager,
            )
        }

        initializer {
            DataViewModel(
                dataType = dataType,
                repositoryProvider = RepositoryProvider(
                    heartRepository = AppRepoContainerImpl(context).heartRepository,
                    stepRepository = AppRepoContainerImpl(context).stepRepository,
                    movesenseRepository = AppRepoContainerImpl(context).movesenseRepository,
                )
            )
        }
    }
}