package com.research.healthconnectplus.screen

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.research.healthconnectplus.bluetooth.AndroidBluetoothController
import com.research.healthconnectplus.data.HCPlusDatabase
import com.research.healthconnectplus.data.UserRepository
import com.research.healthconnectplus.screen.movesense.MovesenseViewModel
import com.research.healthconnectplus.screen.profile.ProfileViewModel

object AppViewModelProvider {


    fun provideViewModelFactory(context: Context) = viewModelFactory {
        initializer {
            MovesenseViewModel(AndroidBluetoothController(context.applicationContext))
        }
        initializer {
            ProfileViewModel(userRepository = UserRepository(userDao = HCPlusDatabase.getDatabase(context).userDAO()))
        }
    }
}