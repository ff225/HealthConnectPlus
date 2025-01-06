package com.research.healthconnectplus.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.research.healthconnectplus.PreferencesManager
import com.research.healthconnectplus.workers.SendDataToInflux
import java.util.concurrent.TimeUnit


object HeartScreen : RouteDestination {
    override val route: String = "heart_screen"
    override val title: String = "Collect heart rate"
}

@Composable
fun HeartScreen(navController: NavController? = null) {

    val context = LocalContext.current

    val preferencesManager = PreferencesManager(context)

    var sendHeartRateData by remember {
        mutableStateOf(preferencesManager.getSendHeartRateData())
    }
    // Show why we need to send heart rate data
    MyScaffold(
        title = HeartScreen.title,
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
                "This screen is used to collect heart rate data from the user",
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
                Switch(checked = sendHeartRateData, onCheckedChange = { newValue ->
                    sendHeartRateData = newValue
                    preferencesManager.setSendStepData(newValue)

                    if (newValue) {
                        WorkManager.getInstance(context)
                            .enqueue(
                                PeriodicWorkRequestBuilder<SendDataToInflux>(
                                    repeatInterval = 15,
                                    TimeUnit.MINUTES
                                ).setInputData(workDataOf("data" to 3))
                                    .addTag("SendHeartRateDataToInflux")
                                    .build()
                            )
                    } else {
                        WorkManager.getInstance(context)
                            .cancelAllWorkByTag("SendHeartRateDataToInflux")
                    }
                }, modifier = Modifier.padding(8.dp))
            }


        }
    }
}

@Preview
@Composable
fun HeartScreenPreview() {
    HeartScreen()
}