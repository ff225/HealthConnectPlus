package com.research.healthconnectplus.screen.movesense

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.movesense.mds.Mds
import com.movesense.mds.MdsConnectionListener
import com.movesense.mds.MdsException
import com.research.healthconnectplus.PreferencesManager
import com.research.healthconnectplus.bluetooth.MovesenseInfo
import com.research.healthconnectplus.screen.AppViewModelProvider
import com.research.healthconnectplus.screen.MyScaffold
import com.research.healthconnectplus.screen.RouteDestination
import com.research.healthconnectplus.screen.home.HomeScreen
import com.research.healthconnectplus.workers.MovesenseConfigWorker
import com.research.healthconnectplus.workers.MovesenseStoreDataWorker
import com.research.healthconnectplus.workers.SendDataToInflux
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

object MovesenseScreen : RouteDestination {
    override val route: String = "movesense_screen"
    override val title: String = "Movesense"
}

@Composable
fun MovesenseScreen(navController: NavController? = null) {
    val context = LocalContext.current

    val preferencesManager = PreferencesManager(context)

    var collectMovesenseInfo by remember {
        mutableStateOf(preferencesManager.getMovesenseInfo())
    }

    val viewModel: MovesenseViewModel = viewModel(
        factory = AppViewModelProvider.provideMovesenseViewModelFactory(context)
    )

    var canEnableGeo by remember { mutableStateOf(false) }
    var isLocationEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }

    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter.isEnabled) }

    var canEnableBluetooth by remember {
        mutableStateOf(false)
    }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val enabler =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {}

    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.all { it.value }

        canEnableBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true
        } else
            permissions[Manifest.permission.BLUETOOTH] == true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            canEnableGeo =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            isLocationEnabled = isLocationEnabled(context)

            if (canEnableGeo && !isLocationEnabled)
                enabler.launch(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )

        }

        if (canEnableBluetooth && !isBluetoothEnabled)
            enabler.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            )

    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permission)
    }

    val state = viewModel.state.collectAsState()
    val mds by lazy {
        Mds.builder().build(context)
    }


    MyScaffold(
        title = MovesenseScreen.title,
        showBackButton = true,
        navController = navController,
        actions = {
            if (collectMovesenseInfo.first == null) {
                IconButton(onClick = {
                    Timer().schedule(20000) {
                        viewModel.stopScan()
                    }
                    viewModel.startScan()

                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }
    ) {

        if (collectMovesenseInfo.first != null) {
            MovesenseSettingsScreen(
                modifier = Modifier.padding(it),
                isConnected = state.value.isConnected,
                onConnect = { device ->
                    mds.connect(device.address, object : MdsConnectionListener {
                        override fun onConnect(p0: String?) {
                            Log.d("Movesense", "onConnect: $p0")
                            viewModel.stopScan()
                        }

                        override fun onConnectionComplete(p0: String?, p1: String?) {
                            Log.d("Movesense", "onConnectionComplete: $p0, $p1")
                            //viewModel.updateUi(false, true)
                            //Movesense.init(device.name!!, device.address, true)

                            preferencesManager.setMovesenseInfo(device.name, device.address)
                            collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                            viewModel.updateConnectionStatus(true)
                            viewModel.stopScan()
                        }

                        override fun onError(p0: MdsException?) {
                            Log.e("Movesense", "onError: ${p0?.message}")
                            //viewModel.updateUi()
                            viewModel.stopScan()
                            preferencesManager.resetMovesenseInfo()
                            collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                            //MovesensePair.resetMovesenseInfo()
                            viewModel.updateConnectionStatus(false)
                        }

                        override fun onDisconnect(p0: String?) {
                            Log.d("Movesense", "onDisconnect: $p0")
                            viewModel.stopScan()
                            preferencesManager.resetMovesenseInfo()
                            collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                            viewModel.updateConnectionStatus(false)
                            //Movesense.reset()
                            //viewModel.updateUi()
                        }


                    })
                },
                onStartLogging = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<MovesenseConfigWorker>()
                            .setInputData(workDataOf("startStopLogging" to true))
                            .build()
                    )

                    WorkManager.getInstance(context).enqueue(
                        PeriodicWorkRequestBuilder<MovesenseStoreDataWorker>(
                            repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES,
                        ).setInitialDelay(15, TimeUnit.MINUTES).addTag("MovesenseStoreDataWorker")
                            .build()
                    )


                },
                onStopLogging = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<MovesenseConfigWorker>()
                            .setInputData(workDataOf("startStopLogging" to false))
                            .build()
                    )
                    // stop logging
                    WorkManager.getInstance(context).cancelAllWorkByTag("MovesenseStoreDataWorker")
                },
                onFlushData = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<MovesenseStoreDataWorker>()
                            .build()
                    )
                },
                onDisconnect = {
                    // stop logging and
                    WorkManager.getInstance(context).cancelAllWorkByTag("MovesenseStoreDataWorker")
                    mds.disconnect(collectMovesenseInfo.second!!)
                    viewModel.updateConnectionStatus(false)
                    preferencesManager.resetMovesenseInfo()
                    collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                    navController?.navigate(HomeScreen.route)


                },
            )
        } else
            DeviceListScreen(
                modifier = Modifier.padding(it),
                scannedDevices = state.value.devices,
                connect = { device ->
                    mds.connect(device.address, object : MdsConnectionListener {
                        override fun onConnect(p0: String?) {
                            Log.d("Movesense", "onConnect: $p0")
                            viewModel.stopScan()
                        }

                        override fun onConnectionComplete(p0: String?, p1: String?) {
                            Log.d("Movesense", "onConnectionComplete: $p0, $p1")
                            //viewModel.updateUi(false, true)
                            //Movesense.init(device.name!!, device.address, true)

                            preferencesManager.setMovesenseInfo(device.name, device.address)
                            collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                            //MovesensePair.initMovesenseInfo(device.name, device.address, true)
                            viewModel.updateConnectionStatus(true)
                            viewModel.stopScan()
                        }

                        override fun onError(p0: MdsException?) {
                            Log.e("Movesense", "onError: ${p0?.message}")
                            //viewModel.updateUi()
                            viewModel.stopScan()
                            preferencesManager.resetMovesenseInfo()
                            collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                            //MovesensePair.resetMovesenseInfo()
                            viewModel.updateConnectionStatus(false)
                        }

                        override fun onDisconnect(p0: String?) {
                            Log.d("Movesense", "onDisconnect: $p0")
                            viewModel.stopScan()
                            preferencesManager.resetMovesenseInfo()
                            collectMovesenseInfo = preferencesManager.getMovesenseInfo()
                            //MovesensePair.resetMovesenseInfo()
                            viewModel.updateConnectionStatus(false)
                            //Movesense.reset()
                            //viewModel.updateUi()
                        }


                    })
                }
            )

    }
}


@Composable
fun MovesenseSettingsScreen(
    modifier: Modifier = Modifier,
    isConnected: Boolean = false,
    onConnect: (MovesenseInfo) -> Unit,
    onStartLogging: () -> Unit = {},
    onStopLogging: () -> Unit = {},
    onFlushData: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    context: Context = LocalContext.current,
) {

    val preferencesManager = PreferencesManager(context)

    var sendMovesenseData by remember {
        mutableStateOf(preferencesManager.getCollectMovesenseData())
    }

    val movesenseInfo by remember {
        mutableStateOf(preferencesManager.getMovesenseInfo())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (!isConnected) {
            Button(
                onClick = {
                    onConnect(
                        MovesenseInfo(
                            movesenseInfo.first!!,
                            movesenseInfo.second!!
                        )
                    )
                },
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text("Connect")
            }
        }
        Button(
            onClick = onStartLogging,
            enabled = isConnected,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text("Start logging")
        }
        Button(
            onClick = onStopLogging,
            enabled = isConnected,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text("Stop logging")

        }
        Button(
            onClick = onFlushData,
            enabled = isConnected,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text("Flush data")
        }
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically

        ) {
            Text("Send periodically data to the server", Modifier.padding(8.dp))
            Switch(
                checked = sendMovesenseData,
                enabled = isConnected,
                onCheckedChange = { newValue ->
                    sendMovesenseData = newValue
                    preferencesManager.setCollectMovesenseData(newValue)

                    if (newValue) {
                        WorkManager.getInstance(context)
                            .enqueue(
                                PeriodicWorkRequestBuilder<SendDataToInflux>(
                                    repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES,
                                ).setInputData(workDataOf("data" to 1))
                                    .setInitialDelay(15, TimeUnit.MINUTES)
                                    .addTag("SendMovesenseDataToInflux")
                                    .build()
                            )
                    } else {
                        WorkManager.getInstance(context)
                            .cancelAllWorkByTag("SendMovesenseDataToInflux")
                    }

                },
                modifier = Modifier.padding(8.dp)
            )
        }

        Button(
            onClick = onDisconnect,
            Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text("Forget device")
        }
    }
}


@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    scannedDevices: List<MovesenseInfo> = emptyList(),
    connect: (MovesenseInfo) -> Unit = {},

    ) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(scannedDevices) { device ->
            DeviceListItem(device = device, connect = connect)
        }

    }
}

@Composable
fun DeviceListItem(device: MovesenseInfo, connect: (MovesenseInfo) -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = device.name)
            Text(text = device.address)
        }
        Button(onClick = { connect(device) }) {
            Text("Connect")
        }
    }
}

@Preview
@Composable
fun PreviewMovesenseScreen() {
    MovesenseScreen()
}

@Preview
@Composable
fun PreviewDeviceListScreen() {
    DeviceListScreen(
        scannedDevices = listOf(
            MovesenseInfo("Movesense 1", "00:00:00:00:00:00"),
            MovesenseInfo("Movesense 2", "00:00:00:00:00:01"),
            MovesenseInfo("Movesense 3", "00:00:00:00:00:02"),
            MovesenseInfo("Movesense 4", "00:00:00:00:00:03"),
            MovesenseInfo("Movesense 5", "00:00:00:00:00:04"),
            MovesenseInfo("Movesense 6", "00:00:00:00:00:05"),
            MovesenseInfo("Movesense 7", "00:00:00:00:00:06"),
            MovesenseInfo("Movesense 8", "00:00:00:00:00:07"),
            MovesenseInfo("Movesense 9", "00:00:00:00:00:08"),
            MovesenseInfo("Movesense 10", "00:00:00:00:00:09"),
        ),
    )
}


/**
 * Function to check if location services are enabled.
 *
 * @param context The Context.
 * @return Boolean indicating if location services are enabled.
 */
fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}