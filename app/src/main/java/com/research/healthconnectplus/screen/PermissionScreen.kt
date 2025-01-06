package com.research.healthconnectplus.screen

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.navigation.NavController


object PermissionScreen : RouteDestination {
    override val route: String = "permission_screen"
    override val title: String = "Grant Permission"
}

@Composable
fun PermissionScreen(navController: NavController? = null) {

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS

        )
    } else {
        arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
        )
    }

    var missingPermissions by remember { mutableStateOf(permissions.toList()) }
    var areHealthPermissionsGranted by remember { mutableStateOf(false) }


    val launcherStandardPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            missingPermissions = results.filter { !it.value }.map { it.key }
        }

    val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()
    val launcherHealthPermissions =
        rememberLauncherForActivityResult(requestPermissionActivityContract) { isGranted ->

            areHealthPermissionsGranted = isGranted.containsAll(
                setOf(
                    HealthPermission.getReadPermission(HeartRateRecord::class),
                    HealthPermission.getWritePermission(HeartRateRecord::class),
                    HealthPermission.getReadPermission(StepsRecord::class),
                    HealthPermission.getWritePermission(StepsRecord::class)
                )
            )
            Log.d("PermissionScreen", "Health permissions granted: $areHealthPermissionsGranted")
        }

    LaunchedEffect(Unit) {

        if (missingPermissions.isNotEmpty()) {
            launcherStandardPermissions.launch(missingPermissions.toTypedArray())
        }
    }

    LaunchedEffect(missingPermissions, areHealthPermissionsGranted) {
        if (missingPermissions.isEmpty() && areHealthPermissionsGranted) {
            navController?.navigate(HomeScreen.route) {
                popUpTo(PermissionScreen.route) { inclusive = true }
            }
        }
    }

    MyScaffold(navController = navController) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            Text(
                text = "Grant permission to allow the app to work properly",
                modifier = Modifier.padding(8.dp)
            )

            Button(onClick = {

                if (missingPermissions.isNotEmpty()) {
                    launcherStandardPermissions.launch(missingPermissions.toTypedArray())
                } else

                    launcherHealthPermissions.launch(
                        setOf(
                            HealthPermission.getReadPermission(HeartRateRecord::class),
                            HealthPermission.getWritePermission(HeartRateRecord::class),
                            HealthPermission.getReadPermission(StepsRecord::class),
                            HealthPermission.getWritePermission(StepsRecord::class)
                        )
                    )

            }) {
                Text(text = "Grant Permission")
            }


        }
    }
}

@Preview
@Composable
fun PermissionScreenPreview() {
    PermissionScreen(navController = null)
}