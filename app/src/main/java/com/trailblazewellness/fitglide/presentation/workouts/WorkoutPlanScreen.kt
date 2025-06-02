package com.trailblazewellness.fitglide.presentation.workouts

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlanScreen(
    navController: NavController,
    viewModel: WorkoutViewModel,
    strapiRepository: StrapiRepository,
    authRepository: AuthRepository
) {
    val sportTypes = listOf(
        "Running", "Cycling", "Swimming", "Hiking", "Strength", "Cardio", "Full-Body",
        "Lower Body", "Upper Body", "Core", "Hybrid (Strength + Cardio)", "Plyometric (Explosive)",
        "Functional Training", "Flexibility and Mobility", "Powerlifting", "Bodyweight Training",
        "High-Intensity Interval Training (HIIT)", "Pilates", "Yoga", "Circuit Training",
        "Isometric Training", "Endurance Training", "Agility and Speed Training",
        "Rehabilitation and Low-Impact", "Dance Fitness", "Rowing", "Badminton", "Tennis", "Jogging"
    )
    var planName by remember { mutableStateOf("") }
    var sportType by remember { mutableStateOf("Cardio") }
    var duration by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isTemplate by remember { mutableStateOf(false) }
    val exercises = remember { mutableStateListOf<ExerciseInput>() }
    val availableExercises = remember { mutableStateOf<List<StrapiApi.ExerciseEntry>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isCardio = sportType in listOf("Running", "Cycling", "Swimming", "Hiking", "Jogging", "Rowing", "Cardio", "Dance Fitness")

    LaunchedEffect(Unit) {
        try {
            val token = "Bearer ${authRepository.getAuthState().jwt}"
            val response = strapiRepository.getExercises(token)
            if (response.isSuccessful) {
                availableExercises.value = response.body()?.data ?: emptyList()
                Log.d("WorkoutPlanScreen", "Fetched ${availableExercises.value.size} exercises")
            } else {
                scope.launch { snackbarHostState.showSnackbar("Failed to load exercises") }
            }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Error loading exercises: ${e.message}") }
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Create Workout Plan", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Create a New Workout Plan",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                OutlinedTextField(
                    value = planName,
                    onValueChange = { planName = it },
                    label = { Text("Plan Name (e.g., Leg Day)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = sportType,
                        onValueChange = {},
                        label = { Text("Workout Type", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary, // This replaces focusedBorderColor for TextField
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant, // This replaces unfocusedBorderColor for TextField
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            // You might need to adjust other parameters like disabledContainerColor, errorContainerColor, etc.
                            // based on your exact needs.
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        sportTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    sportType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isTemplate,
                        onCheckedChange = { isTemplate = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        "Save as Template",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isCardio) {
                item {
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter { it.isDigit() } },
                        label = { Text("Duration (minutes)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                item {
                    OutlinedTextField(
                        value = distance,
                        onValueChange = { distance = it.filter { it.isDigit() || it == '.' } },
                        label = { Text("Distance (km)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } else {
                item {
                    Text(
                        "Exercises (in order of execution)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(exercises) { exercise ->
                    ExerciseInputCard(
                        exercise = exercise,
                        availableExercises = availableExercises.value,
                        onUpdate = { updatedExercise ->
                            val index = exercises.indexOf(exercise)
                            exercises[index] = updatedExercise
                        },
                        onRemove = { exercises.remove(exercise) }
                    )
                }
                item {
                    Button(
                        onClick = { exercises.add(ExerciseInput()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Exercise",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Add Exercise",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (planName.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("Plan name is required") }
                            return@Button
                        }
                        if (isCardio && (duration.isBlank() || duration.toIntOrNull() ?: 0 <= 0)) {
                            scope.launch { snackbarHostState.showSnackbar("Valid duration is required for cardio") }
                            return@Button
                        }
                        if (!isCardio && exercises.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar("At least one exercise is required for gym workouts") }
                            return@Button
                        }
                        if (!isCardio && exercises.any { exercise -> exercise.exerciseId.isBlank() || exercise.sets <= 0 || exercise.reps <= 0 }) {
                            scope.launch { snackbarHostState.showSnackbar("All exercises must have a selection, sets, and reps") }
                            return@Button
                        }

                        scope.launch {
                            try {
                                val token = "Bearer ${authRepository.getAuthState().jwt}"
                                val userId = authRepository.getAuthState().getId() ?: "unknown"
                                val exerciseIds = if (!isCardio) {
                                    exercises.map { exercise -> exercise.exerciseId }
                                } else {
                                    emptyList()
                                }
                                val exerciseOrder = exerciseIds
                                val workoutRequest = StrapiApi.WorkoutRequest(
                                    workoutId = "workout_${UUID.randomUUID()}",
                                    title = planName,
                                    description = description,
                                    distancePlanned = distance.toFloatOrNull() ?: 0f,
                                    totalTimePlanned = duration.toFloatOrNull() ?: 0f,
                                    caloriesPlanned = 0f,
                                    sportType = sportType,
                                    exercises = exerciseIds.map { id -> StrapiApi.ExerciseId(id) },
                                    exerciseOrder = exerciseOrder,
                                    isTemplate = isTemplate,
                                    usersPermissionsUser = StrapiApi.UserId(userId)
                                )
                                Log.d("WorkoutPlanScreen", "Saving workout plan: $workoutRequest")

                                val workoutResponse = strapiRepository.postWorkout(
                                    body = StrapiApi.WorkoutBody(data = workoutRequest),
                                    token = token
                                )

                                if (workoutResponse.isSuccessful) {
                                    scope.launch { snackbarHostState.showSnackbar("Workout plan saved successfully!") }
                                    Log.d("WorkoutPlanScreen", "Workout plan saved: ${workoutResponse.body()}")
                                    navController.popBackStack()
                                } else {
                                    val errorBody = workoutResponse.errorBody()?.string()
                                    Log.e("WorkoutPlanScreen", "Failed to save workout plan: ${workoutResponse.code()} - $errorBody")
                                    scope.launch { snackbarHostState.showSnackbar("Failed to save workout plan: $errorBody") }
                                }
                            } catch (e: Exception) {
                                Log.e("WorkoutPlanScreen", "Error saving workout plan: ${e.message}", e)
                                scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Save Workout Plan",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

data class ExerciseInput(
    var exerciseId: String = "",
    var exerciseName: String = "",
    var sets: Int = 0,
    var reps: Int = 0,
    var weight: Int = 0,
    var restBetweenSets: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseInputCard(
    exercise: ExerciseInput,
    availableExercises: List<StrapiApi.ExerciseEntry>,
    onUpdate: (ExerciseInput) -> Unit,
    onRemove: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(exercise.exerciseName) }
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val filteredExercises by remember(searchQuery) {
        derivedStateOf {
            availableExercises.filter { it.name?.contains(searchQuery, ignoreCase = true) ?: false }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Exercise",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove Exercise",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Exercise", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    placeholder = { Text("e.g., Squats", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    trailingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable { focusRequester.requestFocus() }
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary, // This replaces focusedBorderColor for TextField
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant, // This replaces unfocusedBorderColor for TextField
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        // You might need to adjust other parameters like disabledContainerColor, errorContainerColor, etc.
                        // based on your exact needs.
                    )
                )

                LaunchedEffect(expanded) {
                    if (expanded) focusRequester.requestFocus()
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    filteredExercises.forEach { ex ->
                        DropdownMenuItem(
                            text = { Text(ex.name ?: "Unknown", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                onUpdate(exercise.copy(exerciseId = ex.id, exerciseName = ex.name ?: ""))
                                searchQuery = ex.name ?: ""
                                expanded = false
                            }
                        )
                    }
                    if (searchQuery.isNotEmpty() && filteredExercises.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No results found", color = MaterialTheme.colorScheme.onSurface) },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    }
                }
            }

            OutlinedTextField(
                value = exercise.sets.toString(),
                onValueChange = { onUpdate(exercise.copy(sets = it.toIntOrNull() ?: 0)) },
                label = { Text("Sets", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = exercise.reps.toString(),
                onValueChange = { onUpdate(exercise.copy(reps = it.toIntOrNull() ?: 0)) },
                label = { Text("Reps", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = exercise.weight.toString(),
                onValueChange = { onUpdate(exercise.copy(weight = it.toIntOrNull() ?: 0)) },
                label = { Text("Weight (kg)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = exercise.restBetweenSets.toString(),
                onValueChange = { onUpdate(exercise.copy(restBetweenSets = it.toIntOrNull() ?: 0)) },
                label = { Text("Rest Between Sets (seconds)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors( // <--- Change this line
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}