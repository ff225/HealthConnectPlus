package com.research.healthconnectplus.screen.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.research.healthconnectplus.screen.AppViewModelProvider
import com.research.healthconnectplus.screen.MyScaffold
import com.research.healthconnectplus.screen.RouteDestination
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


object ProfileScreen : RouteDestination {
    override val route: String = "profile_screen"
    override val title: String = "Profile"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isRegistration: Boolean = false,
    navController: NavController? = null,
    navigateTo: () -> Unit = {}
) {
    // viewmodel
    val viewModel: ProfileViewModel = viewModel(
        factory = AppViewModelProvider.provideViewModelFactory(
            LocalContext.current
        )
    )

    val profileUiState by viewModel.uiProfileState.collectAsState()
    val context = LocalContext.current

    MyScaffold(
        title = ProfileScreen.title,
        showBackButton = !isRegistration,
        navController = navController
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // https://developer.android.com/develop/ui/compose/text/user-input#state-practices
            // https://developer.android.com/topic/libraries/architecture/coroutines#dependencies
            //  https://developer.android.com/develop/ui/compose/state
            TextField(
                value = profileUiState.user.name,
                singleLine = true,
                onValueChange = { name ->
                    if (name.all { letter -> letter.isLetter() || letter.isWhitespace() })
                        viewModel.updateName(name)
                    viewModel.updateName(name)
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                label = { Text("Name") }
            )
            TextField(
                value = profileUiState.user.lastName,
                singleLine = true,
                onValueChange = { lastName ->
                    if (lastName.all { letter -> letter.isLetter() || letter.isWhitespace() })
                        viewModel.updateLastName(lastName)
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                label = { Text("Last name") }
            )
            // On click of the text field, show a date picker dialog to select the date
            val datePickerState =
                rememberDatePickerState(
                    initialDisplayMode = DisplayMode.Input,
                    yearRange = Calendar.getInstance()
                        .get(Calendar.YEAR) - 100..Calendar.getInstance().get(Calendar.YEAR) - 18
                )
            var showDatePicker by remember { mutableStateOf(false) }

            var selectedDate by remember {
                mutableStateOf(
                    datePickerState.selectedDateMillis?.let { date ->
                        convertMillisToDate(date)
                    })
            }
            TextField(
                value = if (profileUiState.user.dob.toInt() == 0) "" else convertMillisToDate(
                    profileUiState.user.dob
                ),
                onValueChange = {
                },
                label = { Text("DOB") },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = !showDatePicker }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select date"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )

            if (showDatePicker)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {

                            selectedDate = datePickerState.selectedDateMillis?.let {
                                convertMillisToDate(it)
                            } ?: ""
                            viewModel.updateDob(dob = selectedDate!!)
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }

            Button(
                enabled = viewModel.validateInput(),
                onClick = {
                    if (isRegistration) {

                        navigateTo()
                    } else {
                        //viewModel.updateUserInfo()
                        viewModel.insertUserInfo()
                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()

                    }
                },
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.End)
            ) {
                Text(if (isRegistration) "Save" else "Update")
            }
        }
    }
}


fun convertMillisToDate(millis: Long): String {
    val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}

@Preview
@Composable
fun ProfileScreenPreview() {
    ProfileScreen()
}