package com.research.healthconnectplus.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.research.healthconnectplus.screen.AppViewModelProvider
import com.research.healthconnectplus.screen.MyScaffold
import com.research.healthconnectplus.screen.RouteDestination
import com.research.healthconnectplus.screen.profile.ProfileScreen
import com.research.healthconnectplus.screen.settings.SettingsScreen


object HomeScreen : RouteDestination {
    override val route: String = "home_screen"
    override val title: String = "Home"
}

@Composable
fun HomeScreen(navController: NavController? = null) {

    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = AppViewModelProvider.provideViewModelFactory(
            context
        )
    )

    val healthData by viewModel.healthData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    MyScaffold(
        actions = {
            Row {
                IconButton(onClick = {
                    navController?.navigate(ProfileScreen.route)
                }) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile"
                    )
                }

                IconButton(onClick = {
                    navController?.navigate(SettingsScreen.route)
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                healthData.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(it),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No data collected", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                else -> {

                    HealthDataList(healthDataList = healthData)

                }
            }
        }
    }
}

@Composable
fun HealthDataList(healthDataList: List<uiHealthData>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(healthDataList) { data ->
            HealthDataItem(data)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun HealthDataItem(data: uiHealthData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(6.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = data.type,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = data.value,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last update at: ${data.lastUpdate}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**/
@Preview
@Composable
fun PreviewHomeScreen() {
    HomeScreen()
}

/*
@Composable
fun TextButtonRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(16.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically

    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview
@Composable
fun PreviewTextButtonRow() {
    Surface(onClick = { }) {
        TextButtonRow("Heart Rate") {}
    }
}*/
/*
            //context.startService(Intent(context, HealthConnectPlusService::class.java))
            TextButtonRow(text = "Heart Rate") {
                navController?.navigate(HeartScreen.route)

            }
            TextButtonRow(text = "TEST") {
                //navController?.navigate(HeartScreen.route)
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<Classifier>().build())
            }
            TextButtonRow(text = "Steps") {
                navController?.navigate(StepScreen.route)

            }
            TextButtonRow(text = "Movesense") {
                navController?.navigate(MovesenseScreen.route)
            }

            TextButtonRow(text = "Send movesense") {
                WorkManager.getInstance(context)
                    .enqueue(OneTimeWorkRequestBuilder<SendDataToInflux>().build())
            }

            TextButton(
                onClick = {
                    context.stopService(Intent(context, HealthConnectPlusService::class.java))
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Stop Service")
            }
*/
