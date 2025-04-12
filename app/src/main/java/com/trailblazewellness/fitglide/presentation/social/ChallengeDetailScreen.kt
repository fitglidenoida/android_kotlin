package com.trailblazewellness.fitglide.presentation.social

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeDetailScreen(
    challengeId: String,
    commonViewModel: CommonViewModel,
    navController: NavController,
    userId: String // Added as parameter
) {
    val challenges = commonViewModel.challenges.collectAsState().value
    val challenge = challenges.find { it.id == challengeId } ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Challenge Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "${challenge.type} Challenge",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Goal: ${challenge.goal} steps",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Text(
                text = "From: User ${challenge.challenger.id}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            if (challenge.challengee?.id == userId) {
                Text(
                    text = "To: You!",
                    fontSize = 14.sp,
                    color = Color(0xFF4CAF50)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { /* TODO: Join challenge via Strapi */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Join Challenge", color = Color.White)
            }
        }
    }
}