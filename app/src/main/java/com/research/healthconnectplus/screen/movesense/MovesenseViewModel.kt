package com.research.healthconnectplus.screen.movesense

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.research.healthconnectplus.bluetooth.AndroidBluetoothController
import com.research.healthconnectplus.bluetooth.MovesenseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MovesenseViewModel(private val bluetoothController: AndroidBluetoothController) :
    ViewModel() {
    private val _state = MutableStateFlow(MovesenseScreenUIState())

    val state = combine(
        bluetoothController.scannedDevices,
        _state
    ) { scannedDevices, state ->
        state.copy(
            devices = scannedDevices,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        _state.value
    )

    fun startScan() {
        Log.d("MovesenseViewModel", "startScan")
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        Log.d("MovesenseViewModel", "stopScan")
        bluetoothController.stopDiscovery()
    }

    fun updateConnectionStatus(isConnected: Boolean) {
        _state.value = _state.value.copy(isConnected = isConnected)
    }
}

data class MovesenseScreenUIState(
    val isScanning: Boolean = false,
    val devices: List<MovesenseInfo> = emptyList(),
    val isConnected: Boolean = false
)