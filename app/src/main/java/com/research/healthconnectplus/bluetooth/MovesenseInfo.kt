package com.research.healthconnectplus.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

data class MovesenseInfo(
    val name: String,
    val address: String,
    var isConnected: Boolean = false
)

@SuppressLint("MissingPermission")
fun BluetoothDevice.toMovesenseInfo(): MovesenseInfo {
    return MovesenseInfo(name ?: "No Name", address)
}

object MovesensePair {
    var movesenseInfo: MovesenseInfo? = null
        private set


    fun getSerialId() = movesenseInfo?.name?.removePrefix("Movesense ")

    fun initMovesenseInfo(name: String, address: String, isConnected: Boolean = false) {
        movesenseInfo = MovesenseInfo(name, address, isConnected)
    }

    fun resetMovesenseInfo() {
        movesenseInfo = null
    }
}