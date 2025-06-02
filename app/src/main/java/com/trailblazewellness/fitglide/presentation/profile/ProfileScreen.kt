package com.trailblazewellness.fitglide.presentation.profile

import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.FitGlideTheme
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.presentation.home.HomeViewModel
import com.trailblazewellness.fitglide.presentation.strava.StravaAuthViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ActivityLogEntry(val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    authRepository: AuthRepository,
    navController: NavController,
    rootNavController: NavController,
    profileViewModel: ProfileViewModel,
    homeViewModel: HomeViewModel,
    stravaAuthViewModel: StravaAuthViewModel
) {
    var isPersonalDataExpanded by remember { mutableStateOf(false) }
    var isHealthVitalsExpanded by remember { mutableStateOf(true) }
    var isFitnessBridgeExpanded by remember { mutableStateOf(false) }
    var isSetGoalsExpanded by remember { mutableStateOf(true) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var showStrategyPopup by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val stravaAuthState by stravaAuthViewModel.authState.collectAsState()
    val isStravaConnected by stravaAuthViewModel.isStravaConnected.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
        yearRange = IntRange(1900, LocalDate.now().year)
    )

    LaunchedEffect(stravaAuthState) {
        when (val state = stravaAuthState) {
            is StravaAuthViewModel.StravaAuthState.AuthUrl -> {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.launchUrl(context, Uri.parse(state.url))
            }
            is StravaAuthViewModel.StravaAuthState.Success -> {
                snackbarHostState.showSnackbar("Strava connected!")
            }
            is StravaAuthViewModel.StravaAuthState.Synced -> {
                snackbarHostState.showSnackbar("Synced ${state.count} Strava activities")
            }
            is StravaAuthViewModel.StravaAuthState.Error -> {
                snackbarHostState.showSnackbar("Strava error: ${state.message}")
            }
            else -> {}
        }
    }

    val profileData = profileViewModel.profileData
    val homeData by homeViewModel.homeData.collectAsState()
    val authState = authRepository.getAuthState()
    val userName = authState.userName ?: "User"
    val totalSteps = homeData.watchSteps + homeData.manualSteps + homeData.trackedSteps
    val membershipStatus by remember { mutableStateOf("FitGlide Member") }
    val trackingStats = mapOf(
        "Steps" to totalSteps.toString(),
        "Sleep" to String.format("%.1f", homeData.sleepHours) + "h",
        "Calories" to homeData.caloriesBurned.toString()
    )
    val activityLog = listOf(
        ActivityLogEntry("Logged $totalSteps steps today"),
        ActivityLogEntry("Burned ${homeData.caloriesBurned} calories in workout")
    )

    // Validation for mandatory fields
    val areHealthVitalsValid = profileData.weight != null &&
            profileData.height != null &&
            !profileData.gender.isNullOrEmpty() &&
            !profileData.dob.isNullOrEmpty() &&
            !profileData.activityLevel.isNullOrEmpty()

    val areGoalsValid = profileData.weightLossGoal != null &&
            !profileData.weightLossStrategy.isNullOrEmpty()

    FitGlideTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(padding)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                                        .clickable { /* TODO: Implement avatar upload */ },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userName.first().toString(),
                                        fontSize = 40.sp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = userName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = membershipStatus,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = profileData.weight?.let { "$it kg" } ?: "N/A",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = "Weight",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = profileData.bmi?.let { String.format("%.2f", it) } ?: "N/A",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = "BMI",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = profileData.bmr?.let { String.format("%.2f", it) + " kcal" } ?: "N/A",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = "BMR",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Column {
                                Text(
                                    text = "Weight Loss Progress",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { homeData.weightLost / (profileData.weightLossGoal?.toFloat() ?: 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${homeData.weightLost}/${profileData.weightLossGoal ?: 0f} kg lost",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text(
                                    text = "Halfway there! Keep going!",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        trackingStats.forEach { (title, value) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(4.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = value,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Activity Log",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        activityLog.forEach { entry ->
                            Text(
                                text = entry.description,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                item {
                    ExpandableSection(
                        title = "Personal Data",
                        isExpanded = isPersonalDataExpanded,
                        onToggle = { isPersonalDataExpanded = !isPersonalDataExpanded }
                    ) {
                        EditableField(
                            label = "First Name",
                            value = profileData.firstName ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(firstName = it) },
                            isMandatory = true
                        )
                        EditableField(
                            label = "Last Name",
                            value = profileData.lastName ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(lastName = it) },
                            isMandatory = true
                        )
//                        EditableField(
//                            label = "Mobile",
//                            value = profileData.mobile?.toString() ?: "",
//                            onValueChange = { profileViewModel.profileData = profileData.copy(mobile = it.toLongOrNull()) },
//                            keyboardType = KeyboardType.Number,
//                            isNumeric = true,
//                            isMandatory = true
//                        )
                        EditableField(
                            label = "Email",
                            value = profileData.email ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(email = it) },
                            isMandatory = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                Log.d("ProfileScreen", "Save button clicked")
                                profileViewModel.savePersonalData()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Save")
                        }
                    }
                }
                item {
                    ExpandableSection(
                        title = "Health Vitals",
                        isExpanded = isHealthVitalsExpanded,
                        onToggle = { isHealthVitalsExpanded = !isHealthVitalsExpanded }
                    ) {
                        EditableField(
                            label = "Weight (kg)*",
                            value = profileData.weight?.toString() ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(weight = it.toDoubleOrNull()) },
                            keyboardType = KeyboardType.Decimal,
                            isNumeric = true,
                            isMandatory = true
                        )
                        EditableField(
                            label = "Height (cm)*",
                            value = profileData.height?.toString() ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(height = it.toDoubleOrNull()) },
                            keyboardType = KeyboardType.Decimal,
                            isNumeric = true,
                            isMandatory = true
                        )
                        DropdownField(
                            label = "Gender*",
                            value = profileData.gender ?: "",
                            options = listOf("", "Male", "Female"),
                            onValueChange = { profileViewModel.profileData = profileData.copy(gender = it) },
                            isMandatory = true
                        )
                        DatePickerField(
                            label = "DOB (YYYY-MM-DD)*",
                            value = profileData.dob ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(dob = it) },
                            showDatePicker = showDatePicker,
                            onShowDatePicker = { showDatePicker = true },
                            onDismissDatePicker = { showDatePicker = false },
                            datePickerState = datePickerState
                        )
                        DropdownField(
                            label = "Activity Level*",
                            value = profileData.activityLevel ?: "Sedentary (little/no exercise)",
                            options = listOf(
                                "Sedentary (little/no exercise)",
                                "Light exercise (1-3 days/week)",
                                "Moderate exercise (3-5 days/week)",
                                "Heavy exercise (6-7 days/week)",
                                "Very heavy exercise (Twice/day)"
                            ),
                            onValueChange = { profileViewModel.profileData = profileData.copy(activityLevel = it) },
                            isMandatory = true
                        )
                        Text(
                            text = "BMI: ${profileData.bmi?.let { String.format("%.2f", it) } ?: "N/A"}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BMR: ${profileData.bmr?.let { String.format("%.2f", it) + " kcal" } ?: "N/A"}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (areHealthVitalsValid) {
                                    profileViewModel.calculateMetrics()
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("All fields are required")
                                    }
                                }
                            },
                            enabled = areHealthVitalsValid,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Calculate and Save")
                        }
                    }
                }
                item {
                    ExpandableSection(
                        title = "Fitness Bridge",
                        isExpanded = isFitnessBridgeExpanded,
                        onToggle = { isFitnessBridgeExpanded = !isFitnessBridgeExpanded }
                    ) {
                        FitnessBridgeToggle(
                            label = "Strava",
                            isEnabled = isStravaConnected,
                            onToggle = { enabled ->
                                if (enabled) {
                                    val userId = authRepository.getAuthState().getId() ?: "unknown"
                                    stravaAuthViewModel.initiateStravaAuth()
                                } else {
                                    stravaAuthViewModel.disconnectStrava()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Strava disconnected")
                                    }
                                }
                            }
                        )
                    }
                }
                item {
                    ExpandableSection(
                        title = "Set Goals",
                        isExpanded = isSetGoalsExpanded,
                        onToggle = { isSetGoalsExpanded = !isSetGoalsExpanded }
                    ) {
                        EditableField(
                            label = "Weight Loss Goal (kg)*",
                            value = profileData.weightLossGoal?.toString() ?: "",
                            onValueChange = { profileViewModel.profileData = profileData.copy(weightLossGoal = it.toDoubleOrNull()) },
                            keyboardType = KeyboardType.Decimal,
                            isNumeric = true,
                            isMandatory = true
                        )
                        DropdownField(
                            label = "Weight Loss Strategy*",
                            value = profileData.weightLossStrategy ?: "",
                            options = listOf("", "Lean-(0.25 kg/week)", "Aggressive-(0.5 kg/week)", "Custom"),
                            onValueChange = { profileViewModel.profileData = profileData.copy(weightLossStrategy = it) },
                            isMandatory = true
                        )
                        Text(
                            text = "Step Goal: ${profileData.stepGoal ?: "N/A"}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Water Goal: ${profileData.waterGoal ?: "N/A"} L",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Calorie Goal: ${profileData.calorieGoal ?: "N/A"} cal",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (areGoalsValid && profileData.tdee != null) {
                                    profileViewModel.calculateMetrics()
                                    showStrategyPopup = true
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("All fields are required")
                                    }
                                }
                            },
                            enabled = areGoalsValid && profileData.tdee != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Save")
                        }
                    }
                }
                item {
                    ExpandableSection(
                        title = "Settings",
                        isExpanded = isSettingsExpanded,
                        onToggle = { isSettingsExpanded = !isSettingsExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Notifications",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = profileData.notificationsEnabled ?: true,
                                onCheckedChange = {
                                    profileViewModel.profileData = profileData.copy(notificationsEnabled = it)
                                    profileViewModel.savePersonalData()
                                }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Max Greetings",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = profileData.maxGreetingsEnabled ?: true,
                                onCheckedChange = {
                                    profileViewModel.profileData = profileData.copy(maxGreetingsEnabled = it)
                                    profileViewModel.savePersonalData()
                                }
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            authRepository.logout()
                            rootNavController.navigate("login") {
                                popUpTo(rootNavController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Logout")
                    }
                }
            }

            if (showStrategyPopup) {
                AlertDialog(
                    onDismissRequest = { showStrategyPopup = false },
                    title = { Text("Weight Loss Prediction") },
                    text = {
                        val weeks = when (profileData.weightLossStrategy) {
                            "Lean-(0.25 kg/week)" -> (profileData.weightLossGoal ?: 0.0) / 0.25
                            "Aggressive-(0.5 kg/week)" -> (profileData.weightLossGoal ?: 0.0) / 0.5
                            "Custom" -> (profileData.weightLossGoal ?: 0.0) / 0.25
                            else -> 0.0
                        }.toInt()
                        Text("$weeks weeks to lose ${profileData.weightLossGoal} kg with ${profileData.weightLossStrategy}")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                profileViewModel.saveProfileData(profileData.weightLossStrategy)
                                showStrategyPopup = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val date = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    val formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    profileViewModel.profileData = profileData.copy(dob = formattedDate)
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandableSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    isNumeric: Boolean = false,
    isMandatory: Boolean = false
) {
    var text by remember { mutableStateOf(value) }
    TextField(
        value = text,
        onValueChange = { newValue ->
            if (isNumeric) {
                if (newValue.matches(Regex(if (keyboardType == KeyboardType.Decimal) "^[0-9]*\\.?[0-9]*$" else "^[0-9]*$"))) {
                    text = newValue
                    onValueChange(newValue)
                }
            } else {
                text = newValue
                onValueChange(newValue)
            }
        },
        label = { Text(label + if (isMandatory) " *" else "") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    isMandatory: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label + if (isMandatory) " *" else "") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = true }
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    showDatePicker: Boolean,
    onShowDatePicker: () -> Unit,
    onDismissDatePicker: () -> Unit,
    datePickerState: DatePickerState
) {
    TextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDatePicker() },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "Select Date",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onShowDatePicker() }
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun FitnessBridgeToggle(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}