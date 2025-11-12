package com.research.healthconnectplus.screen.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.research.healthconnectplus.PreferencesManager
import com.research.healthconnectplus.screen.MyScaffold
import com.research.healthconnectplus.screen.RouteDestination
import com.research.healthconnectplus.screen.movesense.MovesenseScreen
import com.research.healthconnectplus.workers.HCReadHeartRate
import com.research.healthconnectplus.workers.HCReadSteps
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


    var collectStepsData by remember {
        mutableStateOf(preferencesManager.getCollectStepData())
    }

    var collectHeartData by remember {
        mutableStateOf(preferencesManager.getCollectHeartRateData())
    }

    var collectBloodPressureData by remember {
        mutableStateOf(preferencesManager.getCollectBloodPressureData())
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
                .verticalScroll(rememberScrollState())
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

            //  clickable to open specific screen
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
            )
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
                        true -> Log.d("SettingsScreen", "Blood Pressure data collection started")
                        false -> Log.d("SettingsScreen", "Blood Pressure data collection stopped")
                    }
                }
            )


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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ML Models Section
            Text(
                "Available ML Models",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            )

            // Example model cards
            ModelCard(
                modelName = "Heart Rate Anomaly Detection",
                description = "Detects irregular heart rate patterns during physical activity",
                result = "Not executed yet",
                isLocal = true,
                isEnabled = true,
                onExecutionModeChange = { isLocal ->
                    Log.d("SettingsScreen", "HR Anomaly: ${if (isLocal) "Local" else "Fog"}")
                },
                onEnabledChange = { enabled ->
                    Log.d("SettingsScreen", "HR Anomaly Detection ${if (enabled) "enabled" else "disabled"}")
                }
            )

            ModelCard(
                modelName = "Activity + HR Classification",
                description = "Combines heart rate and step count to classify workout intensity",
                result = "Not executed yet",
                isLocal = false,
                isEnabled = true,
                onExecutionModeChange = { isLocal ->
                    Log.d("SettingsScreen", "Activity+HR: ${if (isLocal) "Local" else "Fog"}")
                },
                onEnabledChange = { enabled ->
                    Log.d("SettingsScreen", "Activity+HR Classification ${if (enabled) "enabled" else "disabled"}")
                }
            )

            ModelCard(
                modelName = "Cardio Fitness Estimator",
                description = "Estimates cardiovascular fitness level based on HR response to activity",
                result = "Not executed yet",
                isLocal = true,
                isEnabled = false,
                onExecutionModeChange = { isLocal ->
                    Log.d("SettingsScreen", "Cardio Fitness: ${if (isLocal) "Local" else "Fog"}")
                },
                onEnabledChange = { enabled ->
                    Log.d("SettingsScreen", "Cardio Fitness Estimator ${if (enabled) "enabled" else "disabled"}")
                }
            )

            ModelCard(
                modelName = "Recovery Time Predictor",
                description = "Predicts recovery time after exercise based on HR and activity data",
                result = "Not executed yet",
                isLocal = false,
                isEnabled = false,
                onExecutionModeChange = { isLocal ->
                    Log.d("SettingsScreen", "Recovery: ${if (isLocal) "Local" else "Fog"}")
                },
                onEnabledChange = { enabled ->
                    Log.d("SettingsScreen", "Recovery Time Predictor ${if (enabled) "enabled" else "disabled"}")
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

@Preview
@Composable
fun SettingsScreenContentPreview() {
    SettingsScreenContent(
        "Heart Rate"
    )
}

@Composable
fun ModelCard(
    modelName: String = "",
    description: String = "",
    result: String = "Not executed yet",
    isLocal: Boolean = true,
    isEnabled: Boolean = false,
    onExecutionModeChange: (Boolean) -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {}
) {
    var executionMode by remember { mutableStateOf(isLocal) }
    var enabled by remember { mutableStateOf(isEnabled) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Model Name and Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .weight(1f)
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { newValue ->
                        enabled = newValue
                        onEnabledChange(newValue)
                    }
                )
            }

            // Description
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Divider
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Result
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Result:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = result,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Execution Mode Toggle
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Execution Mode:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            executionMode = true
                            onExecutionModeChange(true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (executionMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Local",
                            color = if (executionMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            executionMode = false
                            onExecutionModeChange(false)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!executionMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Fog",
                            color = if (!executionMode) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun ModelCardPreview() {
    ModelCard(
        modelName = "Heart Rate Anomaly Detection",
        description = "Detects irregular heart rate patterns during physical activity",
        result = "Last: Normal (98.5% confidence)",
        isLocal = true,
        isEnabled = true
    )
}