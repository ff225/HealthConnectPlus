package com.research.healthconnectplus.screen

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.research.healthconnectplus.HealthConnectPlusService
import com.research.healthconnectplus.classifier.Classifier
import com.research.healthconnectplus.screen.movesense.MovesenseScreen
import com.research.healthconnectplus.workers.SendDataToInflux


object HomeScreen : RouteDestination {
    override val route: String = "home_screen"
    override val title: String = "Home"
}

@Composable
fun HomeScreen(navController: NavController? = null) {

    val context = LocalContext.current

    MyScaffold {
        Column(
            modifier = Modifier
                .padding(it),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            context.startService(Intent(context, HealthConnectPlusService::class.java))
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

        }
    }
}

@Preview
@Composable
fun PreviewHomeScreen() {
    HomeScreen()
}

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

}