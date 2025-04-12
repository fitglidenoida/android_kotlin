package com.trailblazewellness.fitglide.presentation.meals

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.trailblazewellness.fitglide.FitGlideTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    viewModel: MealsViewModel,
    navController: NavController,
    userName: String
) {
    val mealsData by viewModel.mealsData.collectAsState()
    val favoriteFoods by viewModel.favoriteFoods.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(mealsData.selectedDate) }
    var showMealPicker by remember { mutableStateOf(!mealsData.hasDietPlan) }
    val scrollState = rememberScrollState()
    val mealTypes = listOf("Veg", "Non-Veg", "Mixed")
    val allMealsDone = mealsData.schedule.all { it.items.all { item -> item.isConsumed } }

    LaunchedEffect(selectedDate) {
        viewModel.setDate(selectedDate)
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAllDietComponents()
        viewModel.fetchRecipes()
    }

    FitGlideTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFFFFF))
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hey $userName, Fuel Up!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                    }
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        fontSize = 18.sp,
                        color = Color(0xFF212121)
                    )
                    IconButton(onClick = { selectedDate = selectedDate.plusDays(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                CalorieArc(
                    bmr = mealsData.bmr,
                    caloriesLogged = mealsData.caloriesLogged,
                    onClick = { showDetails = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                MacroArcs(
                    protein = mealsData.protein,
                    carbs = mealsData.carbs,
                    fat = mealsData.fat,
                    fiber = mealsData.fiber
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (mealsData.streak > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFFA500))))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Streak",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Streak: ${mealsData.streak} days",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                BMRCardCarousel(bmr = mealsData.bmr)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Current Meal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                if (allMealsDone) {
                    Text(
                        text = "All Meals Done for Today!",
                        fontSize = 16.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    mealsData.currentMeal?.let { currentMeal ->
                        val index = mealsData.schedule.indexOf(currentMeal)
                        MealCard(
                            slot = currentMeal,
                            viewModel = viewModel,
                            mealIndex = if (index >= 0) index else 0,
                            isCurrent = true
                        )
                    } ?: Text(
                        text = "No Upcoming Meals Today",
                        fontSize = 16.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Day’s Schedule",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                DailySchedule(
                    schedule = mealsData.schedule.filter { it.date == selectedDate },
                    viewModel = viewModel,
                    currentMeal = mealsData.currentMeal,
                    onMealClick = { slot ->
                        viewModel.updateCurrentMeal(mealsData.schedule.map { if (it == slot) it.copy() else it })
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Daily Quest",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                QuestCard(mealsData.questGoal, mealsData.questProgress, 100f)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Recipes",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                RecipeCarousel(viewModel)
                Spacer(modifier = Modifier.height(16.dp))
            }

            FloatingActionButton(
                onClick = { /* TODO: Open camera, process photo, log to active meal */ },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp),
                containerColor = Color(0xFF4CAF50),
                shape = CircleShape
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Snap Meal Photo", tint = Color.White)
            }

            FloatingActionButton(
                onClick = { showMealPicker = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
                containerColor = Color(0xFFFF5722),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = "Pick Meal", tint = Color.White)
            }

            if (showDetails) {
                MealsDetailsOverlay(
                    mealsData = mealsData,
                    onDismiss = { showDetails = false }
                )
            }

            if (showMealPicker) {
                MealPickerDialog(
                    viewModel = viewModel,
                    mealTypes = mealTypes,
                    favoriteFoods = favoriteFoods,
                    onDismiss = { showMealPicker = false }
                )
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPickerDialog(
    viewModel: MealsViewModel,
    mealTypes: List<String>,
    favoriteFoods: List<String>,
    onDismiss: () -> Unit
) {
    var mealTypeExpanded by remember { mutableStateOf(false) }
    var breakfastFav by remember { mutableStateOf("") }
    var breakfastSearch by remember { mutableStateOf("") }
    var breakfastExpanded by remember { mutableStateOf(false) }
    var lunchFav by remember { mutableStateOf("") }
    var lunchSearch by remember { mutableStateOf("") }
    var lunchExpanded by remember { mutableStateOf(false) }
    var dinnerFav by remember { mutableStateOf("") }
    var dinnerSearch by remember { mutableStateOf("") }
    var dinnerExpanded by remember { mutableStateOf(false) }
    var snackFav by remember { mutableStateOf("") }
    var snackSearch by remember { mutableStateOf("") }
    var snackExpanded by remember { mutableStateOf(false) }
    var mealCount by remember { mutableStateOf(3) }

    val filteredBreakfast = favoriteFoods.filter { it.contains(breakfastSearch, ignoreCase = true) }
    val filteredLunch = favoriteFoods.filter { it.contains(lunchSearch, ignoreCase = true) }
    val filteredDinner = favoriteFoods.filter { it.contains(dinnerSearch, ignoreCase = true) }
    val filteredSnack = favoriteFoods.filter { it.contains(snackSearch, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Up Your Diet Plan") },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = mealTypeExpanded,
                    onExpandedChange = { mealTypeExpanded = !mealTypeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = viewModel.mealsData.value.mealType,
                        onValueChange = { viewModel.setMealType(it) },
                        label = { Text("Diet Preference") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealTypeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = mealTypeExpanded,
                        onDismissRequest = { mealTypeExpanded = false }
                    ) {
                        mealTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    viewModel.setMealType(type)
                                    mealTypeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = breakfastExpanded,
                    onExpandedChange = { breakfastExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = breakfastSearch,
                        onValueChange = { breakfastSearch = it },
                        label = { Text("Breakfast Favorite") },
                        placeholder = { Text("e.g., Dosa") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .focusRequester(focusRequester)
                            .focusable(),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.clickable {
                                    breakfastExpanded = true
                                    focusRequester.requestFocus()
                                }
                            )
                        }
                    )
                    LaunchedEffect(breakfastExpanded) {
                        if (breakfastExpanded) focusRequester.requestFocus()
                    }
                    ExposedDropdownMenu(
                        expanded = breakfastExpanded,
                        onDismissRequest = { breakfastExpanded = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        filteredBreakfast.forEach { food ->
                            DropdownMenuItem(
                                text = { Text(food) },
                                onClick = {
                                    breakfastFav = food
                                    breakfastSearch = food
                                    breakfastExpanded = false
                                }
                            )
                        }
                    }
                }
                if (breakfastSearch.isNotEmpty() && filteredBreakfast.isEmpty()) {
                    Text(
                        text = "Can’t find it? We’ll add it!",
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    breakfastFav = breakfastSearch
                }
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = lunchExpanded,
                    onExpandedChange = { lunchExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = lunchSearch,
                        onValueChange = { lunchSearch = it },
                        label = { Text("Lunch Favorite") },
                        placeholder = { Text("e.g., Chole") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .focusRequester(focusRequester)
                            .focusable(),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.clickable {
                                    lunchExpanded = true
                                    focusRequester.requestFocus()
                                }
                            )
                        }
                    )
                    LaunchedEffect(lunchExpanded) {
                        if (lunchExpanded) focusRequester.requestFocus()
                    }
                    ExposedDropdownMenu(
                        expanded = lunchExpanded,
                        onDismissRequest = { lunchExpanded = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        filteredLunch.forEach { food ->
                            DropdownMenuItem(
                                text = { Text(food) },
                                onClick = {
                                    lunchFav = food
                                    lunchSearch = food
                                    lunchExpanded = false
                                }
                            )
                        }
                    }
                }
                if (lunchSearch.isNotEmpty() && filteredLunch.isEmpty()) {
                    Text(
                        text = "Can’t find it? We’ll add it!",
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    lunchFav = lunchSearch
                }
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = dinnerExpanded,
                    onExpandedChange = { dinnerExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = dinnerSearch,
                        onValueChange = { dinnerSearch = it },
                        label = { Text("Dinner Favorite") },
                        placeholder = { Text("e.g., Roti") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .focusRequester(focusRequester)
                            .focusable(),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.clickable {
                                    dinnerExpanded = true
                                    focusRequester.requestFocus()
                                }
                            )
                        }
                    )
                    LaunchedEffect(dinnerExpanded) {
                        if (dinnerExpanded) focusRequester.requestFocus()
                    }
                    ExposedDropdownMenu(
                        expanded = dinnerExpanded,
                        onDismissRequest = { dinnerExpanded = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        filteredDinner.forEach { food ->
                            DropdownMenuItem(
                                text = { Text(food) },
                                onClick = {
                                    dinnerFav = food
                                    dinnerSearch = food
                                    dinnerExpanded = false
                                }
                            )
                        }
                    }
                }
                if (dinnerSearch.isNotEmpty() && filteredDinner.isEmpty()) {
                    Text(
                        text = "Can’t find it? We’ll add it!",
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    dinnerFav = dinnerSearch
                }
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = snackExpanded,
                    onExpandedChange = { snackExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = snackSearch,
                        onValueChange = { snackSearch = it },
                        label = { Text("Snack Favorite (Optional)") },
                        placeholder = { Text("e.g., Fruit") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .focusRequester(focusRequester)
                            .focusable(),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.clickable {
                                    snackExpanded = true
                                    focusRequester.requestFocus()
                                }
                            )
                        }
                    )
                    LaunchedEffect(snackExpanded) {
                        if (snackExpanded) focusRequester.requestFocus()
                    }
                    ExposedDropdownMenu(
                        expanded = snackExpanded,
                        onDismissRequest = { snackExpanded = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        filteredSnack.forEach { food ->
                            DropdownMenuItem(
                                text = { Text(food) },
                                onClick = {
                                    snackFav = food
                                    snackSearch = food
                                    snackExpanded = false
                                }
                            )
                        }
                    }
                }
                if (snackSearch.isNotEmpty() && filteredSnack.isEmpty()) {
                    Text(
                        text = "Can’t find it? We’ll add it!",
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    snackFav = snackSearch
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text("Number of Meals (Suggested: 3-5)", fontSize = 14.sp, color = Color(0xFF212121))
                val mealCounts = (3..6).toList()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mealCounts) { count ->
                        FilterChip(
                            selected = mealCount == count,
                            onClick = { mealCount = count },
                            label = { Text("$count") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.createDietPlan(
                    breakfastFav = breakfastFav,
                    lunchFav = lunchFav,
                    dinnerFav = dinnerFav,
                    snackFav = snackFav,
                    mealCount = mealCount
                )
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CalorieArc(bmr: Float, caloriesLogged: Float, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val radius = size.width / 2 - 10.dp.toPx()
                drawArc(
                    color = Color(0xFF4CAF50),
                    startAngle = -90f,
                    sweepAngle = 360f * (caloriesLogged / bmr),
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = "${caloriesLogged.toInt()}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121)
            )
        }
        Text("Intake (Kcal)", fontSize = 14.sp, color = Color(0xFF757575))
    }
}

@Composable
fun MacroArcs(protein: Float, carbs: Float, fat: Float, fiber: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MacroArc("Protein", protein, 80f, Color(0xFF4CAF50))
        MacroArc("Carbs", carbs, 200f, Color(0xFFFF5722))
        MacroArc("Fat", fat, 60f, Color(0xFF9C27B0))
        MacroArc("Fiber", fiber, 30f, Color(0xFF2196F3))
    }
}

@Composable
fun MacroArc(label: String, value: Float, max: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(70.dp)) {
                val radius = size.width / 2 - 5.dp.toPx()
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * (value / max),
                    useCenter = false,
                    topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                    size = Size(radius * 2, radius * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = "${value.toInt()}g",
                fontSize = 16.sp,
                color = Color(0xFF212121)
            )
        }
        Text(label, fontSize = 12.sp, color = Color(0xFF757575))
    }
}

@Composable
fun BMRCardCarousel(bmr: Float) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            listOf(
                Pair("Maintain", bmr),
                Pair("Lose @ 0.25 kg/week", bmr - 250),
                Pair("Lose @ 0.5 kg/week", bmr - 500),
                Pair("Gain @ 0.25 kg/week", bmr + 250),
                Pair("Gain @ 0.5 kg/week", bmr + 500)
            )
        ) { (label, value) ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, Color(0xFF757575)),
                modifier = Modifier.width(200.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${value.toInt()} Kcal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    Text(label, fontSize = 12.sp, color = Color(0xFF757575))
                }
            }
        }
    }
}

@Composable
fun DailySchedule(
    schedule: List<MealSlot>,
    viewModel: MealsViewModel,
    currentMeal: MealSlot?,
    onMealClick: (MealSlot) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        items(schedule) { slot ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (slot == currentMeal) Color(0xFFE8F5E9) else if (slot.items.all { it.isConsumed }) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onMealClick(slot) }
            ) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Icon(
                            imageVector = when (slot.type) {
                                "Breakfast" -> Icons.Default.WbSunny
                                "Lunch" -> Icons.Default.WbSunny
                                "Dinner" -> Icons.Default.NightlightRound
                                else -> Icons.Default.Restaurant
                            },
                            contentDescription = "Meal Time",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${slot.type} - ${slot.time}", fontSize = 14.sp, color = Color(0xFF212121))
                    }
                    Text(
                        if (slot.items.all { it.isConsumed }) "Consumed" else "Pending",
                        fontSize = 12.sp,
                        color = if (slot.items.all { it.isConsumed }) Color(0xFF4CAF50) else Color(0xFFFF5722)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealCard(slot: MealSlot, viewModel: MealsViewModel, mealIndex: Int, isCurrent: Boolean = false) {
    val favoriteFoods by viewModel.favoriteFoods.collectAsState()
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, if (isCurrent) Color(0xFFFF5722) else Color(0xFF4CAF50)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(if (slot.isMissed) Color(0xFFFFCDD2) else if (isCurrent) Color(0xFFFFF3E0) else Color.White)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (slot.type) {
                            "Breakfast" -> Icons.Default.WbSunny
                            "Lunch" -> Icons.Default.WbSunny
                            "Dinner" -> Icons.Default.NightlightRound
                            else -> Icons.Default.Restaurant
                        },
                        contentDescription = "Meal Time",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${slot.type} - ${slot.time}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                slot.items.forEachIndexed { index, item ->
                    var expanded by remember { mutableStateOf(false) }
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredFoods by remember(searchQuery) {
                        derivedStateOf {
                            favoriteFoods.filter { it.contains(searchQuery, ignoreCase = true) }
                                .also { Log.d("MealCard", "Filtered ${it.size} foods for query: $searchQuery") }
                        }
                    }
                    val focusRequester = remember { FocusRequester() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.name,
                            fontSize = 14.sp,
                            color = Color(0xFF424242),
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .wrapContentHeight()
                        )
                        Text(
                            text = "${item.calories.toInt()} Kcal",
                            fontSize = 12.sp,
                            color = Color(0xFF424242),
                            modifier = Modifier
                                .weight(0.3f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "${item.servingSize.toInt()}g",
                            fontSize = 12.sp,
                            color = Color(0xFF424242),
                            modifier = Modifier
                                .weight(0.3f)
                                .padding(horizontal = 8.dp)
                        )
                        Checkbox(
                            checked = item.isConsumed,
                            onCheckedChange = { viewModel.toggleConsumption(mealIndex, index) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF4CAF50),
                                uncheckedColor = Color.Gray
                            ),
                            modifier = Modifier
                                .weight(0.15f)
                                .size(24.dp)
                                .padding(end = 12.dp)
                        )
                        Box(modifier = Modifier.weight(0.15f)) {
                            IconButton(
                                onClick = { expanded = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pending,
                                    contentDescription = "Change Component",
                                    tint = Color(0xFFFF5722),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .width(250.dp)
                                    .heightIn(max = 200.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Search") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .focusRequester(focusRequester),
                                    keyboardOptions = KeyboardOptions.Default.copy(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Search
                                    ),
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search",
                                            modifier = Modifier.clickable {
                                                focusRequester.requestFocus()
                                            }
                                        )
                                    }
                                )
                                LaunchedEffect(expanded) {
                                    if (expanded) focusRequester.requestFocus()
                                }
                                filteredFoods.forEach { food ->
                                    DropdownMenuItem(
                                        text = { Text(food) },
                                        onClick = {
                                            val component = viewModel.searchComponents.value.firstOrNull { it.name == food }
                                            if (component != null) {
                                                viewModel.replaceMealComponent(mealIndex, index, component.documentId)
                                                Log.d("MealCard", "Selected: $food")
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("${slot.calories.toInt()} Kcal", fontSize = 14.sp, color = Color(0xFF757575))
            Text("P: ${slot.protein}g, C: ${slot.carbs}g, F: ${slot.fat}g, Fib: ${slot.fiber}g", fontSize = 12.sp, color = Color(0xFF757575))
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.sendToCookingBuddy(slot) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Send to Your Cooking Buddy", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun QuestCard(goal: String, progress: Float, max: Float) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFF3E0),
        modifier = Modifier
            .width(200.dp)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(goal, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
            LinearProgressIndicator(
                progress = { progress / max },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFFEEEEEE)
            )
            Text("${progress.toInt()}g / ${max.toInt()}g", fontSize = 12.sp, color = Color(0xFF757575))
        }
    }
}

@Composable
fun RecipeCarousel(viewModel: MealsViewModel) {
    val mealsData by viewModel.mealsData.collectAsState()
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(mealsData.recipes) { recipe ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9),
                modifier = Modifier.width(200.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(recipe, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.logRecipe(recipe) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Log", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MealsDetailsOverlay(mealsData: MealsData, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Meal Details", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("BMR: ${mealsData.bmr.toInt()} Kcal", fontSize = 16.sp)
                Text("Logged: ${mealsData.caloriesLogged.toInt()} Kcal", fontSize = 16.sp)
                Text("Protein: ${mealsData.protein.toInt()}g", fontSize = 16.sp)
                Text("Carbs: ${mealsData.carbs.toInt()}g", fontSize = 16.sp)
                Text("Fat: ${mealsData.fat.toInt()}g", fontSize = 16.sp)
                Text("Fiber: ${mealsData.fiber.toInt()}g", fontSize = 16.sp)
            }
        }
    }
}