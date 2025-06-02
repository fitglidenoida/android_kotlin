package com.trailblazewellness.fitglide.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.trailblazewellness.fitglide.presentation.successstory.SuccessStoryViewModel
import com.trailblazewellness.fitglide.presentation.successstory.WeightLossStory
import kotlinx.coroutines.launch

@Composable
fun WeightLossStories(
    viewModel: SuccessStoryViewModel,
    modifier: Modifier = Modifier
) {
    val stories by viewModel.stories.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Weight Loss Stories",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(stories) { story ->
                WeightLossStoryCard(story)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun WeightLossStoryCard(story: WeightLossStory) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = story.beforeImage ?: "https://via.placeholder.com/60",
                contentDescription = "Before",
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 8.dp)
            )
            AsyncImage(
                model = story.afterImage ?: "https://via.placeholder.com/60",
                contentDescription = "After",
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 8.dp)
            )
            Column {
                Text(
                    text = story.userName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lost ${story.weightLost}kg",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = story.storyText.take(50) + if (story.storyText.length > 50) "..." else "",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun CreateWeightLossStory(
    navController: NavController,
    viewModel: SuccessStoryViewModel
) {
    val context = LocalContext.current
    var userName by remember { mutableStateOf("") }
    var weightLost by remember { mutableStateOf("") }
    var timeTaken by remember { mutableStateOf("") }
    var beforeImageUri by remember { mutableStateOf<String?>(null) }
    var afterImageUri by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val beforeImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        beforeImageUri = uri?.toString()
    }
    val afterImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        afterImageUri = uri?.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Share Your Weight Loss Story",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = weightLost,
            onValueChange = { weightLost = it },
            label = { Text("Weight Lost (e.g., 10kg)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = timeTaken,
            onValueChange = { timeTaken = it },
            label = { Text("Time Taken (e.g., 3 months)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { beforeImageLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload Before Photo")
        }

        beforeImageUri?.let {
            AsyncImage(
                model = it,
                contentDescription = "Before Preview",
                modifier = Modifier
                    .size(100.dp)
                    .padding(top = 8.dp)
            )
        }

        Button(
            onClick = { afterImageLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Upload After Photo")
        }

        afterImageUri?.let {
            AsyncImage(
                model = it,
                contentDescription = "After Preview",
                modifier = Modifier
                    .size(100.dp)
                    .padding(top = 8.dp)
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (userName.isNotBlank() && weightLost.isNotBlank() && timeTaken.isNotBlank()) {
                        coroutineScope.launch {
                            viewModel.addStory(
                                userName = userName,
                                weightLost = weightLost,
                                timeTaken = timeTaken,
                                beforeImageUri = beforeImageUri,
                                afterImageUri = afterImageUri,
                                onSuccess = { navController.popBackStack() },
                                onError = { error -> errorMessage = error }
                            )
                        }
                    } else {
                        errorMessage = "Please fill all fields"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
        }
    }
}