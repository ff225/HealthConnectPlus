package com.research.healthconnectplus.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.research.healthconnectplus.R


/**
 * MyScaffold is a composable function that creates a Scaffold with a TopAppBar.
 * It takes the following parameters:
 * @param title: String = stringResource(id = R.string.app_name) - the title of the TopAppBar
 * @param navController: NavController? = null - the NavController to use for navigating back
 * @param showBackButton: Boolean = false - whether to show the back button in the TopAppBar
 * @param content: @Composable (PaddingValues) -> Unit - the content of the Scaffold
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScaffold(
    title: String = stringResource(id = R.string.app_name),
    navController: NavController? = null,
    showBackButton: Boolean = false,
    actions:  @Composable() (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = title) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { navController?.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = actions
            )

        },
        content = content
    )
}

@Preview
@Composable
fun MyScaffoldPreview() {
    MyScaffold(showBackButton = true) {
        Text("Content", modifier = Modifier.padding(it))
    }
}