package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Greeting(userName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary // Blue from theme
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "M",
                    color = MaterialTheme.colorScheme.onPrimary, // White on blue
                    fontSize = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Hey $userName!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, // Blue from theme
            style = MaterialTheme.typography.headlineMedium
        )
    }
}