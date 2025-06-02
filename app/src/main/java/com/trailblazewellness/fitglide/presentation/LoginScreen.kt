package com.trailblazewellness.fitglide.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
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
    googleAuthManager: GoogleAuthManager?,
    authRepository: AuthRepository?
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    // Check if user is already logged in
    LaunchedEffect(authRepository) {
        if (authRepository?.isLoggedIn() == true) {
            Log.d("LoginScreen", "User is logged in, navigating to splash")
            navController.navigate("splash") {
                popUpTo("login") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Launcher for Google Sign-In
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (googleAuthManager == null || authRepository == null) {
            Log.e("LoginScreen", "Google sign-in failed: googleAuthManager or authRepository is null")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Login failed: Dependencies not ready")
            }
            return@rememberLauncherForActivityResult
        }
        Log.d("LoginScreen", "Sign-in result received: resultCode=${result.resultCode}, data=${result.data?.extras}")
        val account = googleAuthManager.handleSignInResult(result.data)
        coroutineScope.launch {
            val success = authRepository.loginWithGoogle(account?.idToken)
            if (success && authRepository.isLoggedIn()) {
                Log.d("LoginScreen", "Google sign-in successful")
                snackbarHostState.showSnackbar("Login successful!")
                navController.navigate("splash") {
                    popUpTo("login") { inclusive = true }
                    launchSingleTop = true
                }
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
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.fitglide_logo_tweaked1),
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

                    Button(
                        onClick = {
                            if (googleAuthManager == null) {
                                Log.e("LoginScreen", "Google sign-in failed: googleAuthManager is null")
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Login failed: Dependencies not ready")
                                }
                                return@Button
                            }
                            Log.d("LoginScreen", "Launching Google Sign-In")
                            launcher.launch(googleAuthManager.startSignIn())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkTheme) Color.Black else Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(
                                    id = try {
                                        context.resources.getIdentifier("google_color_icon", "drawable", context.packageName)
                                        R.drawable.google_color_icon
                                    } catch (e: Exception) {
                                        Log.e("LoginScreen", "Google icon not found: ${e.message}")
                                        android.R.drawable.ic_dialog_info
                                    }
                                ),
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

                    // Commented out email/password login for Google Sign-In only
                    /*
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
                                    .clickable { },
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
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isEmailPasswordExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                TextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        if (authRepository == null) {
                                            Log.e("LoginScreen", "Email login failed: authRepository is null")
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Login failed: Dependencies not ready")
                                            }
                                            return@Button
                                        }
                                        coroutineScope.launch {
                                            try {
                                                authRepository.loginWithCredentials(email, password)
                                                if (authRepository.isLoggedIn()) {
                                                    Log.d("LoginScreen", "Email login successful")
                                                    snackbarHostState.showSnackbar("Login successful!")
                                                    navController.navigate("splash") {
                                                        popUpTo("login") { inclusive = true }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    Log.e("LoginScreen", "Email login failed")
                                                    snackbarHostState.showSnackbar("Login failed.")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("LoginScreen", "Email login failed: ${e.message}", e)
                                                snackbarHostState.showSnackbar("Login failed: ${e.message}")
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

                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com/signin/v2/recoveryidentifier"))
                                    context.startActivity(intent)
                                }) {
                                    Text("Forgot Password?", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    */

                    TextButton(onClick = { navController.navigate("onboarding") }) {
                        Text("Need an account? Sign up", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}