package com.research.healthconnectplus.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementation of the BluetoothController interface for controlling Bluetooth functionality on Android devices.
 *
 * @param context The application context.
 */
@SuppressLint("MissingPermission")
class AndroidBluetoothController(private val context: Context) : BluetoothController {

    // BluetoothManager instance
    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }

    // BluetoothAdapter instance
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toMovesenseInfo()
            println("device $newDevice")

            if (newDevice.name.contains(
                    "Movesense",
                    ignoreCase = true
                ) && newDevice !in devices
            ) devices + newDevice else devices
        }

    }

    // Mutable state flow for scanned Bluetooth devices
    private val _scannedDevices = MutableStateFlow<List<MovesenseInfo>>(emptyList())
    override val scannedDevices: StateFlow<List<MovesenseInfo>>
        get() = _scannedDevices.asStateFlow()


    /**
     * Starts the discovery process to find nearby Bluetooth devices.
     */
    override fun startDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                Log.d("AndroidBluetoothController", "no permission")
                return
            }
        } else if (!hasPermission(Manifest.permission.BLUETOOTH) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && !hasPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
            return
        Log.d("AndroidBluetoothController", "startDiscovery")
        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )
        //updatePairedDevices()
        println(bluetoothAdapter.startDiscovery())
    }

    /**
     * Stops the ongoing discovery process.
     */
    override fun stopDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                println("no permission")
                return
            }
        } else if (!hasPermission(Manifest.permission.BLUETOOTH) && !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && !hasPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
            return

        bluetoothAdapter.cancelDiscovery()
    }

    /**
     * Releases any resources associated with the Bluetooth controller.
     */
    override fun release() {
        context.unregisterReceiver(foundDeviceReceiver)
    }

    fun hasPermission(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}