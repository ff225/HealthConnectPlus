package com.research.healthconnectplus.screen.settings

import HealthConnectDataGenerator
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.navigation.NavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.research.healthconnectplus.PreferencesManager
import com.research.healthconnectplus.screen.MyScaffold
import com.research.healthconnectplus.screen.RouteDestination
import com.research.healthconnectplus.screen.movesense.MovesenseScreen
import com.research.healthconnectplus.workers.HCReadHeartRate
import com.research.healthconnectplus.workers.HCReadSteps
import com.research.healthconnectplus.workers.InferenceTimingWorker1Sensor
import com.research.healthconnectplus.workers.InferenceTimingWorker2Sensor
import com.research.healthconnectplus.workers.InferenceTimingWorker3Sensor
import com.research.healthconnectplus.workers.InferenceTimingWorker4Sensor
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit


object SettingsScreen : RouteDestination {
    override val route: String = "settings_screen"
    override val title: String = "Settings"
}


// TODO complete with other hc types

@Composable
fun SettingsScreen(navController: NavController? = null) {


    val context = LocalContext.current
    val preferencesManager = PreferencesManager(context)
    val healthConnectClient = HealthConnectClient.getOrCreate(context)
    val scope = rememberCoroutineScope()

    var benchmarkDataCount by remember { mutableStateOf(0) }
    var isGeneratingData by remember { mutableStateOf(false) }
    var isClearingData by remember { mutableStateOf(false) }
    var benchmarkStatus by remember { mutableStateOf("Ready") }


    var collectStepsData by remember {
        mutableStateOf(preferencesManager.getCollectStepData())
    }

    var collectHeartData by remember {
        mutableStateOf(preferencesManager.getCollectHeartRateData())
    }

    var collectBloodPressureData by remember {
        mutableStateOf(preferencesManager.getCollectBloodPressureData())
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val dataGenerator = HealthConnectDataGenerator(healthConnectClient)
                val stats = dataGenerator.getHealthConnectStats()
                benchmarkDataCount = stats.totalStepRecords
            } catch (e: Exception) {
                Log.e("SettingsScreen", "Error checking benchmark status: ${e.message}")
            }
        }
    }


    MyScaffold(
        title = SettingsScreen.title,
        showBackButton = true,
        navController = navController,
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            Text(
                "Activate or deactivate data collection",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            )
            // Create a button that execute a one time work request to inferencetimeworking
            /*Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequest.from(InferenceTimingWorker1Sensor::class.java)
                    )
                    Log.d(
                        "SettingsScreen",
                        "One time work request for inference timing started, 1 sensor"
                    )
                }
            ) {

                Text(
                    "Inference time 1 sensor",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequest.from(InferenceTimingWorker2Sensor::class.java)
                    )
                    Log.d(
                        "SettingsScreen",
                        "One time work request for inference timing started, 2 sensors"
                    )
                }
            ) {
                Text(
                    "Inference time 2 sensors",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Inference time 3 sensor
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequest.from(InferenceTimingWorker3Sensor::class.java)
                    )
                    Log.d(
                        "SettingsScreen",
                        "One time work request for inference timing started, 3 sensors"
                    )
                }
            ) {
                Text(
                    "Inference time 3 sensors",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Inference for 4 sensor
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = {
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequest.from(InferenceTimingWorker4Sensor::class.java)
                    )
                    Log.d(
                        "SettingsScreen",
                        "One time work request for inference timing started, 4 sensors"
                    )
                }
            ) {
                Text(
                    "Inference time 4 sensors",
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }

            SettingsScreenContent(
                "Heart Rate",
                collectHeartData,
                toDataSettings = {
                    navController?.navigate(DataSettingsScreen.createRoute("heart_rate"))
                },
                updateStatus = { newStatus ->
                    collectHeartData = newStatus
                    preferencesManager.setCollectHeartRateData(newStatus)

                    when (newStatus) {
                        true -> WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "storeHeartRateData",
                            ExistingPeriodicWorkPolicy.KEEP,
                            PeriodicWorkRequestBuilder<HCReadHeartRate>(
                                15,
                                TimeUnit.MINUTES
                            ).build()
                        )

                        false -> WorkManager.getInstance(context)
                            .cancelUniqueWork("storeHeartRateData")
                            .also {
                                Log.d(
                                    "SettingsScreen",
                                    "Heart rate data collection stopped"
                                )
                            }
                    }
                }
            )*/
            SettingsScreenContent(
                "Steps",
                collectStepsData,
                toDataSettings = {
                    navController?.navigate(DataSettingsScreen.createRoute("steps"))
                },
                updateStatus = { newStatus ->
                    collectStepsData = newStatus
                    preferencesManager.setCollectStepData(newStatus)

                    when (newStatus) {
                        true ->
                            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                "storeStepData",
                                ExistingPeriodicWorkPolicy.KEEP,
                                PeriodicWorkRequestBuilder<HCReadSteps>(
                                    15,
                                    TimeUnit.MINUTES
                                ).build()
                            )

                        false ->
                            WorkManager.getInstance(context)
                                .cancelUniqueWork("storeStepData")
                                .also {
                                    Log.d(
                                        "SettingsScreen",
                                        "Step data collection stopped"
                                    )
                                }
                    }
                }
            )
            SettingsScreenContent(
                "Blood Pressure",
                collectBloodPressureData,
                updateStatus = { newStatus ->
                    collectBloodPressureData = newStatus
                    preferencesManager.setCollectBloodPressureData(newStatus)

                    when (newStatus) {
                        true -> Log.d(
                            "SettingsScreen",
                            "Blood Pressure data collection started"
                        )

                        false -> Log.d(
                            "SettingsScreen",
                            "Blood Pressure data collection stopped"
                        )
                    }
                }
            )

/*
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        navController?.navigate(MovesenseScreen.route)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Movesense", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Movesense",

                        )
                }
            }*/
            Spacer(modifier = Modifier.height(16.dp))

            // NUOVO: Force Sync Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Benchmark Setup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Force sync data from HealthConnect to HC+ database",
                        fontSize = 14.sp,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            // NUOVO: Force sync step data
                            WorkManager.getInstance(context)
                                .enqueue(
                                    OneTimeWorkRequestBuilder<HCReadSteps>()
                                        .setInputData(
                                            androidx.work.workDataOf("benchmark_sync" to true)
                                        )
                                        .build()
                                )

                            // Optional: show toast
                            android.widget.Toast.makeText(
                                context,
                                "Sync triggered - check logs",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                            Log.d("SettingsScreen", "Force sync HCReadSteps triggered")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Force Sync Steps from HealthConnect")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Benchmark Setup",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            )

            BenchmarkSettingsSection(
                        dataCount = benchmarkDataCount,
                isGenerating = isGeneratingData,
                isClearing = isClearingData,
                status = benchmarkStatus,
                onGenerateData = {
                    scope.launch {
                        isGeneratingData = true
                        benchmarkStatus = "Generating data..."
                        try {
                            val dataGenerator = HealthConnectDataGenerator(healthConnectClient)
                            val info = dataGenerator.setupBenchmarkDataset(25_000)

                            benchmarkDataCount = info.recordsVerified
                            benchmarkStatus = if (info.isSuccessful) {
                                "Ready (${info.recordsVerified} records)"
                            } else {
                                "Generation failed"
                            }

                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Error generating data: ${e.message}")
                            benchmarkStatus = "Error: ${e.message}"
                        } finally {
                            isGeneratingData = false
                        }
                    }
                },
                onClearData = {
                    scope.launch {
                        isClearingData = true
                        benchmarkStatus = "Clearing data..."
                        try {
                            val dataGenerator = HealthConnectDataGenerator(healthConnectClient)
                            // ⭐ Use ownership-based clearing (only our app's records)
                            dataGenerator.clearMyAppStepRecords(daysAgo = 7) // Clear last week

                            // Refresh count after clearing
                            val stats = dataGenerator.getHealthConnectStats()
                            benchmarkDataCount = stats.totalStepRecords
                            benchmarkStatus = "My records cleared"

                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Error clearing data: ${e.message}")
                            benchmarkStatus = "Clear failed: ${e.message}"
                        } finally {
                            isClearingData = false
                        }
                    }
                },
                onRefreshStatus = {
                    scope.launch {
                        try {
                            val dataGenerator = HealthConnectDataGenerator(healthConnectClient)
                            val stats = dataGenerator.getHealthConnectStats()
                            benchmarkDataCount = stats.totalStepRecords
                            benchmarkStatus = "Ready (${stats.totalStepRecords} records)"
                        } catch (e: Exception) {
                            benchmarkStatus = "Status error: ${e.message}"
                        }
                    }
                }
            )
        }
    }
}


@Preview
@Composable
fun SettingsScreenPreview() {
    SettingsScreen()
}

@Composable
fun SettingsScreenContent(
    hcType: String = "",
    collect: Boolean = false,
    updateStatus: (Boolean) -> Unit = {},
    toDataSettings: () -> Unit = {},
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                toDataSettings()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(hcType, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Switch(
                checked = collect,
                onCheckedChange = { newValue ->
                    updateStatus(newValue)
                }
            )
        }
    }
}

@Composable
fun BenchmarkSettingsSection(
    dataCount: Int,
    isGenerating: Boolean,
    isClearing: Boolean,
    status: String,
    onGenerateData: () -> Unit,
    onClearData: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Status Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "HealthConnect Data",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    "$dataCount records",
                    fontSize = 14.sp,
                    color = if (dataCount >= 25_000)
                        androidx.compose.ui.graphics.Color.Green
                    else
                        androidx.compose.ui.graphics.Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Status: $status",
                fontSize = 12.sp,
                color = androidx.compose.ui.graphics.Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateData,
                    enabled = !isGenerating && !isClearing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Generate 25K")
                    }
                }

                OutlinedButton(
                    onClick = onClearData,
                    enabled = !isGenerating && !isClearing && dataCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Clear Data")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRefreshStatus,
                enabled = !isGenerating && !isClearing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Status")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Note: Generate synthetic step data in HealthConnect for benchmark testing. " +
                        "This data will be synced to HC+ database automatically.",
                fontSize = 11.sp,
                color = androidx.compose.ui.graphics.Color.Gray,
                lineHeight = 14.sp
            )
        }
    }
}


@Preview
@Composable
fun SettingsScreenContentPreview() {
    SettingsScreenContent(
        "Heart Rate"
    )
}