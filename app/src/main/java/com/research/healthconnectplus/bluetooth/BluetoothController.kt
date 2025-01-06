package com.research.healthconnectplus.bluetooth

import kotlinx.coroutines.flow.StateFlow


interface BluetoothController {
    // Flow representing the list of scanned Bluetooth devices
    val scannedDevices: StateFlow<List<MovesenseInfo>>

    /**
     * Starts the discovery process to find nearby Bluetooth devices.
     */
    fun startDiscovery()

    /**
     * Stops the ongoing discovery process.
     */
    fun stopDiscovery()

    /**
     * Releases any resources associated with the Bluetooth controller.
     */
    fun release()
}