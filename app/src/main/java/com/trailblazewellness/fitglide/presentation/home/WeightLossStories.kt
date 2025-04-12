package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class WeightLossStory(
    val userName: String,
    val weightLost: String,
    val timeTaken: String,
    val beforeThumbnail: String,
    val afterThumbnail: String
)

@Composable
fun WeightLossStories(
    stories: List<WeightLossStory> = listOf(
        WeightLossStory("Alex", "10kg", "3 months", "before_alex.jpg", "after_alex.jpg"),
        WeightLossStory("Sam", "8kg", "4 months", "before_sam.jpg", "after_sam.jpg"),
        WeightLossStory("Taylor", "12kg", "5 months", "before_taylor.jpg", "after_taylor.jpg")
    ),
    modifier: Modifier = Modifier
) {
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
            Surface(
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Before", fontSize = 12.sp)
                }
            }
            Surface(
                modifier = Modifier
                    .size(60.dp)
                    .padding(end = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("After", fontSize = 12.sp)
                }
            }
            Column {
                Text(
                    text = story.userName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lost ${story.weightLost} in ${story.timeTaken}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}