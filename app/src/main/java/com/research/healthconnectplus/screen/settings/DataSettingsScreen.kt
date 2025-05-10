package com.research.healthconnectplus.screen.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.research.healthconnectplus.PreferencesManager
import com.research.healthconnectplus.screen.AppViewModelProvider
import com.research.healthconnectplus.screen.MyScaffold
import com.research.healthconnectplus.screen.RouteDestination
import com.research.healthconnectplus.screen.home.HealthDataItem
import com.research.healthconnectplus.workers.SendDataToInflux
import java.util.concurrent.TimeUnit


object DataSettingsScreen : RouteDestination {
    override val route: String = "settings/{dataType}"
    override val title: String = "Specific data settings"

    fun createRoute(dataType: String): String = "settings/$dataType"
}

@Composable
fun DataSettingsScreen(
    dataType: String? = null,
    info: String? = null,
    navController: NavController? = null
) {

    val viewModel: DataViewModel = viewModel(
        factory = AppViewModelProvider.provideViewModelFactory(
            context = LocalContext.current,
            dataType = dataType
        )
    )

    val context = LocalContext.current

    val preferencesManager = PreferencesManager(context)

    val data by viewModel.uiData.collectAsState()

    // TODO add more data
    var sendData by remember {
        when (dataType) {
            "steps" -> mutableStateOf(preferencesManager.getCollectStepData())
            "heart_rate" -> mutableStateOf(preferencesManager.getCollectHeartRateData())
            else -> mutableStateOf(false)
        }
    }
    // Show why we need to send heart rate data
    MyScaffold(
        title = DataSettingsScreen.title,
        navController = navController,
        showBackButton = true,
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top

        ) {
            Text(
                info ?: "Send data to server",
                modifier = Modifier.padding(16.dp)
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Button(
                onClick = {
                    //Workmanager unique work
                    WorkManager.getInstance(context)
                        .enqueue(
                            OneTimeWorkRequestBuilder<SendDataToInflux>(
                            ).setInputData(workDataOf("data" to 3))
                                .addTag("SendOneShotHeartDataToInflux")
                                .build()
                        )
                },
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text("Flush data")
            }
            Spacer(modifier = Modifier.padding(8.dp))
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically

            ) {
                Text("Send data periodically to the server", Modifier.padding(8.dp))
                Switch(checked = sendData, onCheckedChange = { newValue ->
                    sendData = newValue
                    var inputDataForWorkManager: Int = -1
                    var workManagerTag = ""
                    when (dataType) {
                        "steps" -> {
                            preferencesManager.setCollectStepData(newValue)
                            inputDataForWorkManager = 2
                            workManagerTag = "SendStepDataToInflux"
                        }

                        "heart_rate" -> {
                            preferencesManager.setCollectHeartRateData(newValue)
                            inputDataForWorkManager = 3
                            workManagerTag = "SendHeartRateDataToInflux"
                        }
                    }

                    Log.d("DataSettingsScreen", "inputDataForWorkManager: $inputDataForWorkManager")
                    Log.d("DataSettingsScreen", "WorkManagerTag: $workManagerTag")

                    if (newValue) {
                        WorkManager.getInstance(context)
                            .enqueue(
                                PeriodicWorkRequestBuilder<SendDataToInflux>(
                                    repeatInterval = 15,
                                    TimeUnit.MINUTES
                                ).setInputData(workDataOf("data" to inputDataForWorkManager))
                                    .addTag(workManagerTag)
                                    .build()
                            )
                    } else {
                        WorkManager.getInstance(context)
                            .cancelAllWorkByTag("SendHeartRateDataToInflux")
                    }
                }, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.padding(8.dp))
            // delete button
            Button(
                onClick = {
                   // TODO
                },
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                Text("Delete data")
            }
            Spacer(modifier = Modifier.padding(8.dp))
            LazyColumn {
                items(data.size) { index ->
                    val item = data[index]
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HealthDataItem(
                            item
                        )
                    }
                }
            }

        }
    }
}

@Preview
@Composable
fun HeartScreenPreview() {
    DataSettingsScreen()
}