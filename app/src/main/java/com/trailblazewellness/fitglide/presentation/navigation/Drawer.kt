package com.trailblazewellness.fitglide.presentation.navigation

import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text // Added missing import
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope

@Composable
fun Drawer(
    scope: CoroutineScope,
    drawerState: DrawerState,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Text("Drawer Content - TBD") // Placeholder
    }
}