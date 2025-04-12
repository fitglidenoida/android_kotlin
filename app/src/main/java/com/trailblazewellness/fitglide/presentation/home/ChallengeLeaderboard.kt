package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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

data class ChallengeEntry(
    val userName: String,
    val steps: Int,
    val rank: Int
)

@Composable
fun ChallengeLeaderboard(
    challengeEntries: List<ChallengeEntry> = listOf(
        ChallengeEntry("Alex", 12000, 1),
        ChallengeEntry("Sam", 11000, 2),
        ChallengeEntry("You", 5000, 3)
    ),
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Step Challenge Leaderboard",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(challengeEntries) { entry ->
                LeaderboardCard(entry)
            }
        }
    }
}

@Composable
fun LeaderboardCard(entry: ChallengeEntry) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .width(120.dp)
            .padding(vertical = 4.dp),
        color = if (entry.userName == "You") MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "#${entry.rank}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.userName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (entry.userName == "You") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${entry.steps} steps",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}