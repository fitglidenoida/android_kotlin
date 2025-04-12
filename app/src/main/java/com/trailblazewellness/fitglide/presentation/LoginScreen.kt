package com.trailblazewellness.fitglide.presentation

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.R
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.auth.GoogleAuthManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    googleAuthManager: GoogleAuthManager,
    authRepository: AuthRepository,
    onLoginSuccess: (() -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isEmailPasswordExpanded by remember { mutableStateOf(false) }

    // Check for persistent login
    LaunchedEffect(Unit) {
        if (authRepository.isLoggedIn()) {
            Log.d("LoginScreen", "User is already logged in, triggering onLoginSuccess")
            snackbarHostState.showSnackbar("Welcome back!")
            onLoginSuccess?.invoke()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("LoginScreen", "Sign-in result received: resultCode=${result.resultCode}, data=${result.data?.extras}")
        val account = googleAuthManager.handleSignInResult(result.data)
        coroutineScope.launch {
            val success = authRepository.loginWithGoogle(account?.idToken)
            if (success && authRepository.isLoggedIn()) {
                Log.d("LoginScreen", "Google sign-in successful, triggering onLoginSuccess")
                snackbarHostState.showSnackbar("Login successful!")
                onLoginSuccess?.invoke()
            } else {
                Log.e("LoginScreen", "Google sign-in failed")
                snackbarHostState.showSnackbar("Google login failed.")
            }
        }
    }

    FitGlideTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5).copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.fitglide_logo_tweaked),
                        contentDescription = "FitGlide Logo",
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Login to FitGlide",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Google Sign-In Button with White Background
                    Button(
                        onClick = {
                            Log.d("LoginScreen", "Launching Google Sign-In")
                            launcher.launch(googleAuthManager.startSignIn())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White, // White background
                            contentColor = Color.Black // Black text/icon
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.google_color_icon),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Collapsible Email/Password Section
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEmailPasswordExpanded = !isEmailPasswordExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Login with Email/Password",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand/Collapse",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (isEmailPasswordExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                TextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            authRepository.loginWithCredentials(email, password)
                                            if (authRepository.isLoggedIn()) {
                                                snackbarHostState.showSnackbar("Login successful!")
                                                onLoginSuccess?.invoke()
                                            } else {
                                                snackbarHostState.showSnackbar("Login failed.")
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Login")
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(onClick = { /* TODO: Forgot Password */ }) {
                                    Text("Forgot Password?", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { navController.navigate("onboarding") }) {
                        Text("Need an account? Sign up", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}