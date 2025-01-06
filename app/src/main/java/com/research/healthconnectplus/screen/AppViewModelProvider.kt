package com.research.healthconnectplus.screen

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.research.healthconnectplus.bluetooth.AndroidBluetoothController
import com.research.healthconnectplus.screen.movesense.MovesenseViewModel

object AppViewModelProvider {


    fun provideMovesenseViewModelFactory(context: Context) = viewModelFactory {
        initializer {
            MovesenseViewModel(AndroidBluetoothController(context.applicationContext))
        }
    }
}