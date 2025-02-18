package com.research.healthconnectplus.screen.settings

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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

    MyScaffold(
        title = SettingsScreen.title,
        showBackButton = true,
        navController = navController,
    ) {
        Column(Modifier.padding(it).fillMaxSize()) {
            Text(
                "Activate or deactivate data collection",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            )
            SettingsScreenContent(
                "Heart Rate",
                collectHeartData,
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
    updateStatus: (Boolean) -> Unit = {}
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
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