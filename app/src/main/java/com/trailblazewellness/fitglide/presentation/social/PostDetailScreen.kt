package com.trailblazewellness.fitglide.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun PostDetailScreen(
    postId: String,
    commonViewModel: CommonViewModel,
    navController: NavController
) {
    val posts by commonViewModel.posts.collectAsState()
    val post = posts.find { it.id == postId } ?: return
    val comments = commonViewModel.comments.collectAsState().value[postId] ?: emptyList()
    var commentText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post Details") },
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
                text = "Steps: ${post.data["steps"]?.toString() ?: "N/A"}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "by User ${post.user?.id ?: "Unknown"}",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Comments (${comments.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(comments) { comment ->
                    Text(
                        text = "- ${comment.text}",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                if (comments.isEmpty()) {
                    item { Text("No comments yet", fontSize = 14.sp, color = Color.Gray) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Add a comment") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        // TODO: Implement comment posting via StrapiRepository
                        commentText = ""
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF4CAF50))
                }
            }
        }
    }
}