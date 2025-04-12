package com.trailblazewellness.fitglide.presentation.social

import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.presentation.viewmodel.CommonViewModel

@Composable
fun FriendsScreen(
    navController: NavController,
    commonViewModel: CommonViewModel,
    authRepository: AuthRepository
) {
    val authState by authRepository.authStateFlow.collectAsState()
    val friends by commonViewModel.friends.collectAsState(initial = emptyList())
    val packs by commonViewModel.packs.collectAsState(initial = emptyList())
    val challenges by commonViewModel.challenges.collectAsState(initial = emptyList())
    val posts by commonViewModel.posts.collectAsState(initial = emptyList())
    val cheers by commonViewModel.cheers.collectAsState(initial = emptyList())
    val comments by commonViewModel.comments.collectAsState(initial = emptyMap())
    val isTracking by commonViewModel.isTracking.collectAsState(initial = false)
    val uiMessage by commonViewModel.uiMessage.collectAsState()
    val userId = authState.getId() ?: "4"

    var inviteEmail by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(cheers) {
        Log.d("FriendsScreen", "Cheers fetched: ${cheers.size} items")
        cheers.forEach { cheer ->
            val senderId = cheer.sender?.id ?: "null"
            val receiverId = cheer.receiver?.id ?: "null"
            Log.d("FriendsScreen", "Cheer: sender=$senderId, receiver=$receiverId, message=${cheer.message}")
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF81C784))))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Friends & Community",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (inviteEmail.isNotEmpty()) {
                        commonViewModel.inviteFriend(inviteEmail)
                        inviteEmail = ""
                    }
                },
                shape = CircleShape,
                containerColor = Color(0xFF4CAF50),
                modifier = Modifier.shadow(8.dp, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF5F5F5), Color(0xFFE0F7FA))
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = { Text("Invite by Email") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (inviteEmail.isNotEmpty()) {
                            commonViewModel.inviteFriend(inviteEmail)
                            inviteEmail = ""
                        }
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        "Send",
                        color = Color(0xFF4CAF50),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        Text(
                            "Friends",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                items(friends) { friend -> FriendCard(friend, userId, commonViewModel) }
                if (friends.isEmpty()) {
                    item {
                        Text("No friends yet", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }

                item {
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        Text(
                            "Packs",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                items(packs) { pack -> PackCard(pack) }
                if (packs.isEmpty()) {
                    item {
                        Text("No packs yet", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }

                item {
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        Text(
                            "Challenges",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                items(challenges) { challenge ->
                    ChallengeCard(challenge, userId, commonViewModel) { challengeId ->
                        navController.navigate("challengeDetail/$challengeId")
                    }
                }
                if (challenges.isEmpty()) {
                    item {
                        Text("No challenges yet", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }

                item {
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        Text(
                            "Posts",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                items(posts) { post ->
                    PostCard(post, comments[post.id] ?: emptyList(), commonViewModel) {
                        navController.navigate("postDetail/${post.id}")
                    }
                }
                if (posts.isEmpty()) {
                    item {
                        Text("No posts yet", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }

                item {
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        Text(
                            "Cheers",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                items(cheers) { cheer -> CheerCard(cheer, userId) }
                if (cheers.isEmpty()) {
                    item {
                        Text("No cheers yet", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                }
                item { LiveCheerCard(userId, isTracking, commonViewModel) }

                item {
                    AnimatedVisibility(visible = true, enter = fadeIn()) {
                        Text(
                            "Pride Milestones",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Check out my 5-Day Streak on FitGlide!")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Streak"))
                            },
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFF9C4),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = "Trophy", tint = Color(0xFFFFA500))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "5-Day Streak",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                            }
                        }
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(post: StrapiApi.PostEntry, comments: List<StrapiApi.CommentEntry>, viewModel: CommonViewModel, onClick: () -> Unit) {
    var commentText by remember { mutableStateOf("") }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE8F5E9))))
                .padding(16.dp)
        ) {
            Text(text = "Post by User ${post.user?.id ?: "Unknown"}", fontSize = 14.sp, color = Color.Gray)
            Text(text = "Steps: ${post.data["steps"]?.toString() ?: "N/A"}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = "${comments.size} comments", fontSize = 12.sp, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Add a comment") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color.Gray
                    )
                )
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 1.1f else 1f)
                IconButton(
                    onClick = {
                        if (commentText.isNotEmpty()) {
                            viewModel.postComment(post.id, commentText)
                            commentText = ""
                        }
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier.scale(scale)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF4CAF50))
                }
            }
        }
    }
}

@Composable
fun FriendCard(friend: StrapiApi.FriendEntry, userId: String, viewModel: CommonViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE8F5E9))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 1.1f else 1f)
            IconButton(
                onClick = {},
                interactionSource = interactionSource,
                modifier = Modifier.scale(scale)
            ) {
                Icon(Icons.Default.Person, contentDescription = "Friend", tint = Color(0xFF4CAF50))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = friend.friendEmail.takeIf { it.isNotEmpty() } ?: "Unknown Friend",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            when (friend.friendsStatus) {
                "Accepted" -> Button(
                    onClick = { viewModel.updateFriend(friend.id, "Rejected") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("Remove", color = Color.White) }
                "Pending" -> {
                    if (friend.receiver?.data?.id == userId) {
                        Row {
                            Button(
                                onClick = { viewModel.updateFriend(friend.id, "Accepted") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) { Text("Accept") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.updateFriend(friend.id, "Rejected") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                            ) { Text("Reject") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PackCard(pack: StrapiApi.PackEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE8F5E9))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 1.1f else 1f)
            IconButton(
                onClick = {},
                interactionSource = interactionSource,
                modifier = Modifier.scale(scale)
            ) {
                Icon(Icons.Default.Group, contentDescription = "Pack", tint = Color(0xFF4CAF50)) // Fixed syntax
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${pack.name}: Captain ${pack.captain.id}, ${pack.gliders.size} members",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { /* TODO: Join/Leave pack */ }) {
                Text(if (pack.gliders.any { it.id == "1" }) "Leave" else "Join")
            }
        }
    }
}

@Composable
fun ChallengeCard(
    challenge: StrapiApi.ChallengeEntry,
    userId: String,
    viewModel: CommonViewModel,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clickable { onClick(challenge.id) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE8F5E9))))
                .padding(16.dp)
        ) {
            Text(
                text = when {
                    challenge.challengee?.id == userId -> "${challenge.challenger.id} challenged you: ${challenge.type}"
                    challenge.participants?.containsKey("pack") == true -> "${challenge.type} (Pack Challenge)"
                    else -> "${challenge.type} (Open to All)"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Goal: ${challenge.goal} steps",
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun CheerCard(cheer: StrapiApi.CheerEntry, userId: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE8F5E9))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 1.1f else 1f)
            IconButton(
                onClick = {},
                interactionSource = interactionSource,
                modifier = Modifier.scale(scale)
            ) {
                Icon(Icons.Default.Celebration, contentDescription = "Cheer", tint = Color(0xFF4CAF50))
            }
            Spacer(modifier = Modifier.width(8.dp))
            val senderId = cheer.sender?.id ?: "unknown"
            val receiverId = cheer.receiver?.id
            Text(
                text = "User $senderId cheered: ${cheer.message}",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            if (receiverId != null && receiverId == userId) {
                Button(
                    onClick = { /* TODO: Cheer back */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("Cheer Back") }
            }
        }
    }
}

@Composable
fun LiveCheerCard(userId: String, isTracking: Boolean, viewModel: CommonViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFFE8F5E9))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 1.1f else 1f)
            IconButton(
                onClick = {},
                interactionSource = interactionSource,
                modifier = Modifier.scale(scale)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Live", tint = Color(0xFF4CAF50))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "User $userId is walking now (5k steps)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.postCheer(userId, "Great job!") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Text("Cheer Now") }
        }
    }
}