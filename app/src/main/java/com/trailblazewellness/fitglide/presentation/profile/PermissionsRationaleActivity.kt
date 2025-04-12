package com.trailblazewellness.fitglide.presentation.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.launch

class PermissionsRationaleActivity : ComponentActivity() {
    private lateinit var healthConnectClient: HealthConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthConnectClient = HealthConnectClient.getOrCreate(this)

        setContent {
            val scope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("FitGlide needs Health Connect permissions to track your steps, sleep, exercise, and heart rate.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Please go to Settings > Security and Privacy > Privacy Controls > Health Connect, tap 'App permissions', select FitGlide, and allow access.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    scope.launch {
                        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        try {
                            startActivity(intent)
                            println("Launched Health Connect settings from rationale")
                        } catch (e: Exception) {
                            println("Failed to launch settings: $e")
                        }
                        finish() // Return to WelcomeScreen
                    }
                }) {
                    Text("Open Health Connect Settings")
                }
            }
        }
    }
}