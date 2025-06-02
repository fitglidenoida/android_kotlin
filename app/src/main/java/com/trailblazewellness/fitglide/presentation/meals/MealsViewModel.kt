package com.trailblazewellness.fitglide.presentation.meals

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import com.trailblazewellness.fitglide.auth.AuthRepository
import com.trailblazewellness.fitglide.data.api.StrapiApi
import com.trailblazewellness.fitglide.data.api.StrapiRepository
import com.trailblazewellness.fitglide.data.healthconnect.HealthConnectRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MealsViewModel(
    private val strapiRepository: StrapiRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _mealsData = MutableStateFlow(
        MealsData(
            bmr = 1800f,
            caloriesLogged = 0f,
            protein = 0f,
            carbs = 0f,
            fat = 0f,
            fiber = 0f,
            schedule = emptyList(),
            streak = 0,
            questActive = false,
            questGoal = "Protein",
            questProgress = 0f,
            questTarget = 80f, // Default until calculated
            selectedDate = LocalDate.now(),
            mealType = "Veg",
            favoriteFood = "",
            customMealRequested = false,
            customMealMessage = "",
            hasDietPlan = false
        )
    )
    val mealsData: StateFlow<MealsData> = _mealsData.asStateFlow()

    private val _favoriteFoods = MutableStateFlow<List<String>>(emptyList())
    val favoriteFoods: StateFlow<List<String>> = _favoriteFoods.asStateFlow()

    private val _searchComponents = MutableStateFlow<List<StrapiApi.DietComponentEntry>>(emptyList())
    val searchComponents: StateFlow<List<StrapiApi.DietComponentEntry>> = _searchComponents.asStateFlow()

    private val componentsCache = mutableMapOf<String, StrapiApi.DietComponentEntry>()
    private val dailyLogIds = mutableMapOf<LocalDate, String?>()

    init {
        fetchMealsData(LocalDate.now())
        fetchAllDietComponents()
        calculateStreak()
    }

    private fun parseMacro(value: String?): Float = value?.replace("g", "")?.toFloatOrNull() ?: 0f

    fun fetchMealsData(date: LocalDate) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()

                val nutrition = healthConnectRepository.getNutrition(date)
                val healthVitalsResponse = strapiRepository.getHealthVitals(userId, token)
                val healthVitals = healthVitalsResponse.body()?.data?.first()
                val bmr = healthVitals?.calorieGoal?.toFloat() ?: 1800f
                val strategy = healthVitals?.weight_loss_strategy ?: "Maintain"
                val calorieAdjustment = when (strategy) {
                    "Lean-(0.25 kg/week)" -> -250f
                    "Aggressive-(0.5 kg/week)" -> -500f
                    else -> 0f
                }
                val targetCalories = bmr + calorieAdjustment

                val dietPlanResponse = strapiRepository.getDietPlan(userId, date, token)
                val dietPlans = dietPlanResponse.body()?.data
                val activePlan = dietPlans?.filter { it.active }?.maxByOrNull { it.planId }
                var schedule: List<MealSlot> = emptyList()

                val dietLogResponse = strapiRepository.getDietLogs(userId, date, token)
                val dietLogs = dietLogResponse.body()?.data ?: emptyList()
                val existingLog = dietLogs.maxByOrNull { it.documentId ?: "" }
                dailyLogIds[date] = existingLog?.documentId

                val mealLogMap = mutableMapOf<String, List<StrapiApi.ComponentLogEntry>>()
                if (existingLog != null && existingLog.meals != null) {
                    existingLog.meals.forEach { mealJson ->
                        val mealId = mealJson["mealId"] as? String ?: return@forEach
                        val componentsJson = mealJson["components"] as? List<Map<String, Any>> ?: emptyList()
                        val components = componentsJson.map { comp ->
                            StrapiApi.ComponentLogEntry(
                                componentId = comp["componentId"] as? String ?: "",
                                consumed = comp["consumed"] as? Boolean ?: false
                            )
                        }
                        mealLogMap[mealId] = components
                    }
                }

                var totalProteinGoal = 0f
                var totalCarbsGoal = 0f
                var totalFatGoal = 0f
                var totalFiberGoal = 0f

                if (activePlan != null && activePlan.meals?.isNotEmpty() == true) {
                    schedule = activePlan.meals.filter { it.mealDate == date.toString() }.map { meal ->
                        val displayTime = meal.mealTime?.let {
                            LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm:ss[.SSS]"))
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        } ?: "00:00"
                        val loggedComponents = mealLogMap[meal.documentId] ?: emptyList()
                        val components = meal.dietComponents?.mapNotNull { component ->
                            componentsCache[component.documentId] = component
                            val isConsumed = loggedComponents.find { it.componentId == component.documentId }?.consumed ?: false
                            MealItem(
                                id = component.documentId,
                                name = component.name ?: "Unknown",
                                servingSize = component.calories?.toFloat() ?: 0f,
                                calories = component.calories?.toFloat() ?: 0f,
                                isConsumed = isConsumed
                            )
                        } ?: listOf(MealItem(
                            meal.documentId ?: "unknown",
                            meal.name,
                            meal.totalCalories.toFloat(),
                            meal.totalCalories.toFloat(),
                            false
                        ))

                        // Calculate macronutrient goals for the meal
                        val mealProtein = components.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }.toFloat()
                        val mealCarbs = components.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }.toFloat()
                        val mealFat = components.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }.toFloat()
                        val mealFiber = components.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }.toFloat()

                        totalProteinGoal += mealProtein
                        totalCarbsGoal += mealCarbs
                        totalFatGoal += mealFat
                        totalFiberGoal += mealFiber

                        val missed = LocalTime.parse(displayTime, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now()) && components.any { !it.isConsumed }
                        MealSlot(
                            id = meal.documentId ?: "unknown_${System.currentTimeMillis()}",
                            type = meal.name.removeSuffix(" Meal"),
                            time = displayTime,
                            items = components,
                            calories = components.filter { it.isConsumed }.sumOf { it.calories.toDouble() }.toFloat(),
                            protein = mealProtein,
                            carbs = mealCarbs,
                            fat = mealFat,
                            fiber = mealFiber,
                            date = date,
                            isMissed = missed,
                            targetCalories = meal.totalCalories.toFloat()
                        )
                    }

                    if (schedule.isEmpty()) {
                        schedule = activePlan.meals.map { meal ->
                            val displayTime = meal.mealTime?.let {
                                LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm:ss[.SSS]"))
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            } ?: "00:00"
                            val loggedComponents = mealLogMap[meal.documentId] ?: emptyList()
                            val components = meal.dietComponents?.mapNotNull { component ->
                                componentsCache[component.documentId] = component
                                val isConsumed = loggedComponents.find { it.componentId == component.documentId }?.consumed ?: false
                                MealItem(
                                    id = component.documentId,
                                    name = component.name ?: "Unknown",
                                    servingSize = component.calories?.toFloat() ?: 0f,
                                    calories = component.calories?.toFloat() ?: 0f,
                                    isConsumed = isConsumed
                                )
                            } ?: listOf(MealItem(
                                meal.documentId ?: "unknown",
                                meal.name,
                                meal.totalCalories.toFloat(),
                                meal.totalCalories.toFloat(),
                                false
                            ))

                            // Calculate macronutrient goals for the meal
                            val mealProtein = components.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }.toFloat()
                            val mealCarbs = components.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }.toFloat()
                            val mealFat = components.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }.toFloat()
                            val mealFiber = components.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }.toFloat()

                            totalProteinGoal += mealProtein
                            totalCarbsGoal += mealCarbs
                            totalFatGoal += mealFat
                            totalFiberGoal += mealFiber

                            val missed = LocalTime.parse(displayTime, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now()) && components.any { !it.isConsumed }
                            MealSlot(
                                id = meal.documentId ?: "unknown_${System.currentTimeMillis()}",
                                type = meal.name.removeSuffix(" Meal"),
                                time = displayTime,
                                items = components,
                                calories = components.filter { it.isConsumed }.sumOf { it.calories.toDouble() }.toFloat(),
                                protein = mealProtein,
                                carbs = mealCarbs,
                                fat = mealFat,
                                fiber = mealFiber,
                                date = date,
                                isMissed = missed,
                                targetCalories = meal.totalCalories.toFloat()
                            )
                        }
                    }

                    if (dailyLogIds[date] == null) {
                        val dietLogRequest = StrapiApi.DietLogRequest(
                            date = date.toString(),
                            usersPermissionsUser = StrapiApi.UserId("1"),
                            meals = schedule.map { meal ->
                                StrapiApi.MealLogEntry(
                                    mealId = meal.id,
                                    components = meal.items.map { StrapiApi.ComponentLogEntry(it.id, it.isConsumed) }
                                )
                            }
                        )
                        val logResponse = strapiRepository.postDietLog(dietLogRequest, token)
                        if (logResponse.isSuccessful) {
                            dailyLogIds[date] = logResponse.body()?.data?.documentId
                            Log.d("MealsViewModel", "Initial daily diet log created for $date with ID: ${dailyLogIds[date]}")
                        } else {
                            Log.e("MealsViewModel", "Failed to create initial diet log: ${logResponse.code()} - ${logResponse.errorBody()?.string()}")
                        }
                    }
                }

                val sortedMeals = schedule.sortedBy { LocalTime.parse(it.time, DateTimeFormatter.ofPattern("HH:mm")) }
                updateCurrentMeal(sortedMeals)

                val caloriesLogged = sortedMeals.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { it.calories.toDouble() }
                }.toFloat() + nutrition.calories
                val protein = sortedMeals.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }
                }.toFloat() + nutrition.protein
                val carbs = sortedMeals.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }
                }.toFloat() + nutrition.carbs
                val fat = sortedMeals.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }
                }.toFloat() + nutrition.fat
                val fiber = sortedMeals.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }
                }.toFloat()

                _mealsData.value = _mealsData.value.copy(
                    bmr = bmr,
                    caloriesLogged = caloriesLogged,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    fiber = fiber,
                    schedule = sortedMeals,
                    questActive = totalProteinGoal > 0, // Quest is active if there are macronutrient goals
                    questGoal = "Protein", // Default to protein; can be changed dynamically
                    questProgress = protein,
                    questTarget = totalProteinGoal,
                    selectedDate = date,
                    hasDietPlan = activePlan != null,
                    proteinGoal = totalProteinGoal,
                    carbsGoal = totalCarbsGoal,
                    fatGoal = totalFatGoal,
                    fiberGoal = totalFiberGoal
                )
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error fetching meals data: ${e.message}", e)
            }
        }
    }

    fun fetchAllDietComponents() {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                var attempts = 0
                val maxAttempts = 3
                while (attempts < maxAttempts) {
                    try {
                        val response = strapiRepository.getDietComponents("Veg", token)
                        if (response.isSuccessful) {
                            val components = response.body()?.data ?: emptyList()
                            components.forEach { componentsCache[it.documentId] = it }
                            _searchComponents.value = components
                            _favoriteFoods.value = components.mapNotNull { it.name }
                            Log.d("MealsViewModel", "Fetched all ${_searchComponents.value.size} diet components")
                            break
                        } else {
                            Log.w("MealsViewModel", "Fetch attempt ${attempts + 1} failed: ${response.code()} - ${response.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.w("MealsViewModel", "Fetch attempt ${attempts + 1} failed with exception: ${e.message}")
                    }
                    attempts++
                    if (attempts < maxAttempts) delay(2000)
                }
                if (attempts == maxAttempts) {
                    Log.e("MealsViewModel", "Failed to fetch diet components after $maxAttempts attempts")
                }
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error fetching diet components: ${e.message}", e)
            }
        }
    }

    private fun calculateStreak() {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                var streak = 0
                var currentDate = LocalDate.now()
                while (true) {
                    val dietLogResponse = strapiRepository.getDietLogs(userId, currentDate, token)
                    val dietLogs = dietLogResponse.body()?.data ?: emptyList()
                    if (dietLogs.isEmpty()) break
                    val loggedMeals = dietLogs.any { log -> log.meals?.any { meal -> meal["components"]?.let { comps -> (comps as List<*>).any { (it as Map<*, *>)["consumed"] == true } } == true } == true }
                    if (!loggedMeals) break
                    streak++
                    currentDate = currentDate.minusDays(1)
                }
                _mealsData.value = _mealsData.value.copy(streak = streak)
                Log.d("MealsViewModel", "Calculated streak: $streak days")
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error calculating streak: ${e.message}", e)
                _mealsData.value = _mealsData.value.copy(streak = 0)
            }
        }
    }

    fun updateCurrentMeal(schedule: List<MealSlot>) {
        val currentTime = LocalTime.now()
        val firstUnconsumed = schedule.firstOrNull { meal -> meal.items.any { !it.isConsumed } }
        val currentMeal = firstUnconsumed ?: schedule.firstOrNull()
        _mealsData.update { it.copy(currentMeal = currentMeal) }
        updateMealColors(currentTime, schedule)
    }

    private fun updateMealColors(currentTime: LocalTime, schedule: List<MealSlot>) {
        val updatedSchedule = schedule.map { meal ->
            val mealTime = LocalTime.parse(meal.time, DateTimeFormatter.ofPattern("HH:mm"))
            val missed = mealTime.isBefore(currentTime) && meal.items.any { !it.isConsumed }
            meal.copy(isMissed = missed)
        }
        _mealsData.update { it.copy(schedule = updatedSchedule) }
    }

    fun setDate(date: LocalDate) {
        _mealsData.value = _mealsData.value.copy(selectedDate = date)
        fetchMealsData(date)
    }

    fun setMealType(type: String) {
        _mealsData.value = _mealsData.value.copy(mealType = type)
        fetchAllDietComponents()
    }

    fun setFavoriteFood(food: String) {
        _mealsData.value = _mealsData.value.copy(favoriteFood = food)
    }

    fun createDietPlan(
        breakfastFav: String,
        lunchFav: String,
        dinnerFav: String,
        snackFav: String,
        mealCount: Int,
        customFavs: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                val currentDate = _mealsData.value.selectedDate

                // Check for existing active diet plan for today
                val dietPlanResponse = strapiRepository.getDietPlan(userId, currentDate, token)
                val activePlan = dietPlanResponse.body()?.data?.find { it.active && it.meals?.any { meal -> meal.mealDate == currentDate.toString() } == true }

                val healthVitalsResponse = strapiRepository.getHealthVitals(userId, token)
                val healthVitals = healthVitalsResponse.body()?.data?.first()
                val bmr = healthVitals?.calorieGoal?.toFloat() ?: 1800f
                val strategy = healthVitals?.weight_loss_strategy ?: "Maintain"
                val calorieAdjustment = when (strategy) {
                    "Lean-(0.25 kg/week)" -> -250f
                    "Aggressive-(0.5 kg/week)" -> -500f
                    else -> 0f
                }
                val targetCalories = bmr + calorieAdjustment
                val perMealCalories = targetCalories / mealCount

                val components = _searchComponents.value
                val meals = mutableListOf<MealSlot>()
                val mealIds = mutableListOf<String>()

                val mealSlots = mutableListOf<Triple<String, String, String>>()
                when (mealCount) {
                    3 -> mealSlots.addAll(listOf(
                        Triple("Breakfast", breakfastFav, "08:00:00.000"),
                        Triple("Lunch", lunchFav, "13:00:00.000"),
                        Triple("Dinner", dinnerFav, "19:00:00.000")
                    ))
                    4 -> mealSlots.addAll(listOf(
                        Triple("Breakfast", breakfastFav, "08:00:00.000"),
                        Triple("Lunch", lunchFav, "13:00:00.000"),
                        Triple("Snack", snackFav, "16:00:00.000"),
                        Triple("Dinner", dinnerFav, "19:00:00.000")
                    ))
                    else -> {
                        mealSlots.add(Triple("Breakfast", breakfastFav, "08:00:00.000"))
                        mealSlots.add(Triple("Lunch", lunchFav, "13:00:00.000"))
                        mealSlots.add(Triple("Dinner", dinnerFav, "19:00:00.000"))
                        customFavs.forEachIndexed { index, fav ->
                            mealSlots.add(Triple("Meal ${index + 4}", fav, "${10 + index * 2}:00:00.000"))
                        }
                    }
                }

                var totalProteinGoal = 0f
                var totalCarbsGoal = 0f
                var totalFatGoal = 0f
                var totalFiberGoal = 0f

                mealSlots.forEach { (type, fav, time) ->
                    val favComponent = components.firstOrNull { it.name == fav }
                    val favCalories = favComponent?.calories?.toFloat() ?: 200f
                    val remainingCalories = perMealCalories - favCalories
                    val filler = components.filter { it.name != fav }.shuffled().firstOrNull()
                    val fillerCalories = filler?.calories?.toFloat() ?: 0f
                    val scale = if (fillerCalories > 0) remainingCalories / fillerCalories else 1f

                    val mealRequest = MealRequest(
                        name = "$type Meal",
                        meal_time = time,
                        base_portion = (favCalories + (fillerCalories * scale)).toInt(),
                        basePortionUnit = "Serving",
                        totalCalories = perMealCalories.toInt(),
                        totalProtein = (parseMacro(favComponent?.protein) + parseMacro(filler?.protein) * scale),
                        totalCarbs = (parseMacro(favComponent?.carbs) + parseMacro(filler?.carbs) * scale),
                        totalFat = (parseMacro(favComponent?.fat) + parseMacro(filler?.fat) * scale),
                        meal_date = currentDate.toString(),
                        diet_components = listOfNotNull(favComponent?.documentId, filler?.documentId),
                        diet_plan = activePlan?.documentId,
                        usersPermissionsUser = StrapiApi.UserId(userId)
                    )

                    val mealResponse = if (activePlan != null) {
                        // Update existing meal or add new one
                        val existingMeal = activePlan.meals?.find { it.name == "$type Meal" && it.mealDate == currentDate.toString() }
                        if (existingMeal != null) {
                            strapiRepository.updateMeal(existingMeal.documentId, mealRequest, token)
                        } else {
                            strapiRepository.postMeal(mealRequest, token)
                        }
                    } else {
                        strapiRepository.postMeal(mealRequest, token)
                    }

                    if (mealResponse.isSuccessful) {
                        val mealId = mealResponse.body()?.data?.documentId ?: "meal_${System.currentTimeMillis()}_${meals.size}"
                        mealIds.add(mealId)
                        val displayTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss.SSS")).format(DateTimeFormatter.ofPattern("HH:mm"))
                        val missed = LocalTime.parse(displayTime, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now())

                        val mealProtein = (parseMacro(favComponent?.protein) + parseMacro(filler?.protein) * scale)
                        val mealCarbs = (parseMacro(favComponent?.carbs) + parseMacro(filler?.carbs) * scale)
                        val mealFat = (parseMacro(favComponent?.fat) + parseMacro(filler?.fat) * scale)
                        val mealFiber = (parseMacro(favComponent?.fiber) + parseMacro(filler?.fiber) * scale)

                        totalProteinGoal += mealProtein
                        totalCarbsGoal += mealCarbs
                        totalFatGoal += mealFat
                        totalFiberGoal += mealFiber

                        meals.add(
                            MealSlot(
                                id = mealId,
                                type = type,
                                time = displayTime,
                                items = listOf(
                                    MealItem(favComponent?.documentId ?: "unknown", fav, favCalories, favCalories, false),
                                    MealItem(filler?.documentId ?: "extra", filler?.name ?: "Extra", fillerCalories * scale, fillerCalories * scale, false)
                                ),
                                calories = 0f,
                                protein = mealProtein,
                                carbs = mealCarbs,
                                fat = mealFat,
                                fiber = mealFiber,
                                date = currentDate,
                                isMissed = missed,
                                targetCalories = perMealCalories
                            )
                        )
                    }
                }

                val sortedMeals = meals.sortedBy { LocalTime.parse(it.time, DateTimeFormatter.ofPattern("HH:mm")) }

                // Create or update diet plan
                val dietPlanRequest = DietPlanRequest(
                    name = activePlan?.planId ?: "diet_plan_${System.currentTimeMillis()}",
                    totalCalories = targetCalories.toInt(),
                    dietPreference = _mealsData.value.mealType,
                    active = true,
                    pointsEarned = activePlan?.pointsEarned ?: 0,
                    dietGoal = strategy,
                    meals = mealIds,
                    usersPermissionsUser = StrapiApi.UserId(userId)
                )

                val dietPlanId = if (activePlan != null) {
                    val response = strapiRepository.updateDietPlan(activePlan.documentId, dietPlanRequest, token)
                    if (response.isSuccessful) activePlan.documentId else null
                } else {
                    val response = strapiRepository.postDietPlan(dietPlanRequest, token)
                    if (response.isSuccessful) response.body()?.data?.documentId else null
                } ?: return@launch

                // Update meals with the diet plan ID if new plan was created
                if (activePlan == null) {
                    mealIds.forEach { mealId ->
                        val meal = meals.find { it.id == mealId }
                        if (meal != null) {
                            val mealRequest = MealRequest(
                                name = "${meal.type} Meal",
                                meal_time = meal.time + ":00.000",
                                base_portion = meal.items.sumOf { it.servingSize.toDouble() }.toInt(),
                                basePortionUnit = "Serving",
                                totalCalories = meal.items.sumOf { it.calories.toDouble() }.toInt(),
                                totalProtein = meal.protein,
                                totalCarbs = meal.carbs,
                                totalFat = meal.fat,
                                meal_date = currentDate.toString(),
                                diet_components = meal.items.map { it.id },
                                diet_plan = dietPlanId,
                                usersPermissionsUser = StrapiApi.UserId(userId)
                            )
                            strapiRepository.updateMeal(mealId, mealRequest, token)
                        }
                    }
                }

                val dietLogRequest = StrapiApi.DietLogRequest(
                    date = currentDate.toString(),
                    usersPermissionsUser = StrapiApi.UserId(userId),
                    meals = sortedMeals.map { meal ->
                        StrapiApi.MealLogEntry(
                            mealId = meal.id,
                            components = meal.items.map { StrapiApi.ComponentLogEntry(it.id, it.isConsumed) }
                        )
                    }
                )
                val logResponse = strapiRepository.postDietLog(dietLogRequest, token)
                if (logResponse.isSuccessful) {
                    dailyLogIds[currentDate] = logResponse.body()?.data?.documentId
                    Log.d("MealsViewModel", "Initial daily diet log created for $currentDate")
                }

                updateCurrentMeal(sortedMeals)
                _mealsData.value = _mealsData.value.copy(
                    schedule = sortedMeals,
                    hasDietPlan = true,
                    questActive = totalProteinGoal > 0,
                    questGoal = "Protein",
                    questProgress = _mealsData.value.protein,
                    questTarget = totalProteinGoal,
                    proteinGoal = totalProteinGoal,
                    carbsGoal = totalCarbsGoal,
                    fatGoal = totalFatGoal,
                    fiberGoal = totalFiberGoal
                )
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error creating diet plan: ${e.message}", e)
            }
        }
    }

    fun replaceMealComponent(mealIndex: Int, itemIndex: Int, newComponentId: String) {
        viewModelScope.launch {
            val schedule = _mealsData.value.schedule.toMutableList()
            if (mealIndex < 0 || mealIndex >= schedule.size) {
                Log.e("MealsViewModel", "Invalid mealIndex: $mealIndex, schedule size: ${schedule.size}")
                return@launch
            }
            val meal = schedule[mealIndex]
            val items = meal.items.toMutableList()
            if (itemIndex < 0 || itemIndex >= items.size) {
                Log.e("MealsViewModel", "Invalid itemIndex: $itemIndex, items size: ${items.size}")
                return@launch
            }
            val newComponent = componentsCache[newComponentId]
            if (newComponent == null) {
                Log.e("MealsViewModel", "Component not found for ID: $newComponentId, cache size: ${componentsCache.size}")
                return@launch
            }
            val oldItem = items[itemIndex]
            val baseCalories = newComponent.calories?.toFloat() ?: 0f
            val scale = if (baseCalories > 0) meal.targetCalories / baseCalories else 1f
            items[itemIndex] = MealItem(
                id = newComponent.documentId,
                name = newComponent.name ?: "Unknown",
                servingSize = scale * (newComponent.calories?.toFloat() ?: 0f),
                calories = meal.targetCalories,
                isConsumed = oldItem.isConsumed
            )
            schedule[mealIndex] = meal.copy(
                items = items,
                calories = items.filter { it.isConsumed }.sumOf { it.calories.toDouble() }.toFloat(),
                protein = items.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() * scale }.toFloat(),
                carbs = items.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() * scale }.toFloat(),
                fat = items.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() * scale }.toFloat(),
                fiber = items.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() * scale }.toFloat()
            )
            _mealsData.value = _mealsData.value.copy(
                schedule = schedule,
                caloriesLogged = schedule.sumOf { it.items.filter { it.isConsumed }.sumOf { it.calories.toDouble() } }.toFloat(),
                protein = schedule.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }
                }.toFloat(),
                carbs = schedule.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }
                }.toFloat(),
                fat = schedule.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }
                }.toFloat(),
                fiber = schedule.sumOf { slot ->
                    slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }
                }.toFloat(),
                questProgress = _mealsData.value.protein
            )
            Log.d("MealsViewModel", "Replaced component at mealIndex=$mealIndex, itemIndex=$itemIndex with $newComponentId, scaled to $scale")

            val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
            val userId = authRepository.getAuthState().getId().toString()

            // Post feedback
            val feedbackRequest = FeedbackRequest(
                userId = userId,
                mealId = meal.id,
                oldComponentId = oldItem.id,
                newComponentId = newComponentId,
                timestamp = LocalDateTime.now().toString()
            )
            strapiRepository.postFeedback(feedbackRequest, token)

            // Update the meal in Strapi
            val dietPlanResponse = strapiRepository.getDietPlan(userId, _mealsData.value.selectedDate, token)
            val activePlan = dietPlanResponse.body()?.data?.filter { it.active }?.maxByOrNull { it.planId }
            if (activePlan != null) {
                val mealToUpdate = activePlan.meals?.find { it.documentId == meal.id }
                if (mealToUpdate != null) {
                    val updatedComponents = mealToUpdate.dietComponents?.mapIndexed { idx, component ->
                        if (idx == itemIndex) newComponent.documentId else component.documentId
                    } ?: emptyList()
                    val mealRequest = MealRequest(
                        name = mealToUpdate.name,
                        meal_time = mealToUpdate.mealTime,
                        base_portion = mealToUpdate.basePortion,
                        basePortionUnit = "Serving",
                        totalCalories = mealToUpdate.totalCalories,
                        meal_date = mealToUpdate.mealDate,
                        diet_components = updatedComponents,
                        diet_plan = activePlan.documentId,
                        usersPermissionsUser = StrapiApi.UserId(userId)
                    )
                    val updateResponse = strapiRepository.updateMeal(mealToUpdate.documentId, mealRequest, token)
                    if (updateResponse.isSuccessful) {
                        Log.d("MealsViewModel", "Meal updated successfully: ${mealToUpdate.documentId}")
                    } else {
                        Log.e("MealsViewModel", "Failed to update meal: ${updateResponse.code()} - ${updateResponse.errorBody()?.string()}")
                    }
                } else {
                    Log.e("MealsViewModel", "Meal ${meal.id} not found in diet plan")
                }
            } else {
                Log.e("MealsViewModel", "No active diet plan found")
            }

            // Update diet log
            updateDailyLog()
            updateCurrentMeal(schedule)
        }
    }

    fun toggleConsumption(mealIndex: Int, itemIndex: Int) {
        val schedule = _mealsData.value.schedule
        if (mealIndex < 0 || mealIndex >= schedule.size) {
            Log.e("MealsViewModel", "Invalid mealIndex: $mealIndex, schedule size: ${schedule.size}")
            return
        }
        val meal = schedule[mealIndex]
        if (itemIndex < 0 || itemIndex >= meal.items.size) {
            Log.e("MealsViewModel", "Invalid itemIndex: $itemIndex, items size: ${meal.items.size}")
            return
        }

        val updatedSchedule = schedule.toMutableList()
        val items = meal.items.toMutableList()
        val item = items[itemIndex]
        items[itemIndex] = item.copy(isConsumed = !item.isConsumed)
        val missed = LocalTime.parse(meal.time, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now()) && items.any { !it.isConsumed }
        updatedSchedule[mealIndex] = meal.copy(
            items = items,
            calories = items.filter { it.isConsumed }.sumOf { it.calories.toDouble() }.toFloat(),
            isMissed = missed
        )

        val caloriesLogged = updatedSchedule.sumOf { slot ->
            slot.items.filter { it.isConsumed }.sumOf { it.calories.toDouble() }
        }.toFloat()
        val protein = updatedSchedule.sumOf { slot ->
            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }
        }.toFloat()
        val carbs = updatedSchedule.sumOf { slot ->
            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }
        }.toFloat()
        val fat = updatedSchedule.sumOf { slot ->
            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }
        }.toFloat()
        val fiber = updatedSchedule.sumOf { slot ->
            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }
        }.toFloat()

        _mealsData.value = _mealsData.value.copy(
            schedule = updatedSchedule,
            caloriesLogged = caloriesLogged,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            questProgress = protein // Update quest progress based on protein for now
        )
        updateDailyLog()
        updateCurrentMeal(updatedSchedule)
    }

    private fun updateDailyLog() {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                val date = _mealsData.value.selectedDate
                val schedule = _mealsData.value.schedule

                val dietLogUpdateRequest = StrapiApi.DietLogUpdateRequest(
                    date = date.toString(),
                    meals = schedule.map { meal ->
                        StrapiApi.MealLogEntry(
                            mealId = meal.id,
                            components = meal.items.map { StrapiApi.ComponentLogEntry(it.id, it.isConsumed) }
                        )
                    }
                )

                val logId = dailyLogIds[date]
                if (logId != null) {
                    val putResponse = strapiRepository.putDietLog(logId, dietLogUpdateRequest, token)
                    if (putResponse.isSuccessful) {
                        Log.d("MealsViewModel", "Daily diet log updated for $date with PUT (logId: $logId)")
                    } else {
                        Log.e("MealsViewModel", "Failed to update diet log with PUT: ${putResponse.code()} - ${putResponse.errorBody()?.string()}")
                        val dietLogRequest = StrapiApi.DietLogRequest(
                            date = date.toString(),
                            usersPermissionsUser = StrapiApi.UserId(userId),
                            meals = schedule.map { meal ->
                                StrapiApi.MealLogEntry(
                                    mealId = meal.id,
                                    components = meal.items.map { StrapiApi.ComponentLogEntry(it.id, it.isConsumed) }
                                )
                            }
                        )
                        val postResponse = strapiRepository.postDietLog(dietLogRequest, token)
                        if (postResponse.isSuccessful) {
                            dailyLogIds[date] = postResponse.body()?.data?.documentId
                            Log.d("MealsViewModel", "New daily diet log created for $date after PUT failed")
                        }
                    }
                } else {
                    val dietLogRequest = StrapiApi.DietLogRequest(
                        date = date.toString(),
                        usersPermissionsUser = StrapiApi.UserId(userId),
                        meals = schedule.map { meal ->
                            StrapiApi.MealLogEntry(
                                mealId = meal.id,
                                components = meal.items.map { StrapiApi.ComponentLogEntry(it.id, it.isConsumed) }
                            )
                        }
                    )
                    val postResponse = strapiRepository.postDietLog(dietLogRequest, token)
                    if (postResponse.isSuccessful) {
                        dailyLogIds[date] = postResponse.body()?.data?.documentId
                        Log.d("MealsViewModel", "Initial daily diet log created for $date with ID: ${dailyLogIds[date]}")
                    } else {
                        Log.e("MealsViewModel", "Failed to create initial diet log: ${postResponse.code()} - ${postResponse.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error updating diet log: ${e.message}", e)
            }
        }
    }

    fun requestCustomMeal(food: String) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                strapiRepository.postCustomMealRequest(
                    CustomMealRequest(userId = userId, food = food),
                    token
                )
                val currentSchedule = _mealsData.value.schedule
                val newSchedule = currentSchedule + MealSlot(
                    id = "custom_${System.currentTimeMillis()}",
                    type = "Custom",
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    items = listOf(MealItem("custom_${System.currentTimeMillis()}", food, 500f, 500f, false)),
                    calories = 0f,
                    protein = 0f,
                    carbs = 0f,
                    fat = 0f,
                    fiber = 0f,
                    date = _mealsData.value.selectedDate,
                    isMissed = false,
                    targetCalories = 500f
                )
                _mealsData.value = _mealsData.value.copy(
                    customMealRequested = true,
                    customMealMessage = "Wait while we cook a great plan for you!",
                    schedule = newSchedule
                )
                updateDailyLog()
                updateCurrentMeal(newSchedule)
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error requesting custom meal: ${e.message}", e)
            }
        }
    }

    fun sendToCookingBuddy(slot: MealSlot) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                strapiRepository.postMealGoal(
                    MealGoalRequest(
                        userId = userId,
                        meal = slot.items.joinToString { it.name },
                        calories = slot.calories,
                        time = slot.time
                    ),
                    token
                )
                Log.d("MealsViewModel", "Sent to Cooking Buddy: ${slot.items.joinToString { it.name }}")
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error sending to Cooking Buddy: ${e.message}", e)
            }
        }
    }

    fun fetchRecipes() {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                val currentDate = _mealsData.value.selectedDate

                val dietPlanResponse = strapiRepository.getDietPlan(userId, currentDate, token)
                if (!dietPlanResponse.isSuccessful) {
                    Log.e("MealsViewModel", "Failed to fetch diet plan: ${dietPlanResponse.code()} - ${dietPlanResponse.errorBody()?.string()}")
                    _mealsData.update { it.copy(recipes = emptyList()) }
                    return@launch
                }

                val activePlan = dietPlanResponse.body()?.data?.find {
                    it.active && it.meals?.any { meal -> meal.mealDate == currentDate.toString() } == true
                }
                if (activePlan == null) {
                    Log.w("MealsViewModel", "No active diet plan found for $currentDate")
                    _mealsData.update { it.copy(recipes = emptyList()) }
                    return@launch
                }

                val components = activePlan.meals
                    ?.filter { it.mealDate == currentDate.toString() }
                    ?.flatMap { it.dietComponents ?: emptyList() }
                    ?.distinctBy { it.documentId }
                    ?.map {
                        Log.d(
                            "MealsViewModel",
                            "DietComponentEntry: id=${it.documentId}, name=${it.name}, " +
                                    "calories=${it.calories}, protein=${it.protein}, " +
                                    "carbohydrate=${it.carbs}, total_fat=${it.fat}, fiber=${it.fiber}"
                        )
                        DietComponentCard(
                            id = it.documentId,
                            name = it.name ?: "Unknown",
                            calories = it.calories?.toString() ?: "0",
                            protein = it.protein?.takeIf { it.isNotBlank() } ?: "0g",
                            carbs = it.carbs?.takeIf { it.isNotBlank() } ?: "0g",
                            fat = it.fat?.takeIf { it.isNotBlank() } ?: "0g",
                            fiber = it.fiber?.takeIf { it.isNotBlank() } ?: "0g"
                        )
                    } ?: emptyList()

                Log.d("MealsViewModel", "Fetched ${components.size} recipe components: ${components.map { "${it.name}: P=${it.protein}, C=${it.carbs}, F=${it.fat}, Fib=${it.fiber}" }}")
                _mealsData.update { it.copy(recipes = components) }
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error fetching recipes: ${e.message}", e)
                _mealsData.update { it.copy(recipes = emptyList()) }
            }
        }
    }

    fun logRecipe(recipe: String) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
                val recipeName = recipe.split(" - ")[0]
                val component = _searchComponents.value.firstOrNull { it.name == recipeName }
                if (component != null) {
                    val currentSchedule = _mealsData.value.schedule
                    val newSchedule = currentSchedule + MealSlot(
                        id = "recipe_${System.currentTimeMillis()}",
                        type = "Recipe",
                        time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        items = listOf(
                            MealItem(
                                id = component.documentId,
                                name = component.name ?: "Unknown",
                                servingSize = component.calories?.toFloat() ?: 0f,
                                calories = component.calories?.toFloat() ?: 0f,
                                isConsumed = true
                            )
                        ),
                        calories = component.calories?.toFloat() ?: 0f,
                        protein = parseMacro(component.protein),
                        carbs = parseMacro(component.carbs),
                        fat = parseMacro(component.fat),
                        fiber = parseMacro(component.fiber),
                        date = _mealsData.value.selectedDate,
                        isMissed = false,
                        targetCalories = component.calories?.toFloat() ?: 0f
                    )
                    _mealsData.value = _mealsData.value.copy(
                        schedule = newSchedule,
                        caloriesLogged = newSchedule.sumOf { it.items.filter { it.isConsumed }.sumOf { it.calories.toDouble() } }.toFloat(),
                        protein = newSchedule.sumOf { slot ->
                            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }
                        }.toFloat(),
                        carbs = newSchedule.sumOf { slot ->
                            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }
                        }.toFloat(),
                        fat = newSchedule.sumOf { slot ->
                            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }
                        }.toFloat(),
                        fiber = newSchedule.sumOf { slot ->
                            slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }
                        }.toFloat(),
                        questProgress = _mealsData.value.protein
                    )
                    updateDailyLog()
                    updateCurrentMeal(newSchedule)
                    Log.d("MealsViewModel", "Logged recipe: $recipe")
                }
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error logging recipe: ${e.message}", e)
            }
        }
    }

    fun logPhotoMeal(mealName: String, calories: Float, protein: Float, carbs: Float, fat: Float, fiber: Float) {
        viewModelScope.launch {
            try {
                val currentSchedule = _mealsData.value.schedule
                val newSchedule = currentSchedule + MealSlot(
                    id = "photo_${System.currentTimeMillis()}",
                    type = "Photo Meal",
                    time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    items = listOf(
                        MealItem(
                            id = "photo_${System.currentTimeMillis()}",
                            name = mealName,
                            servingSize = calories,
                            calories = calories,
                            isConsumed = true
                        )
                    ),
                    calories = calories,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    fiber = fiber,
                    date = _mealsData.value.selectedDate,
                    isMissed = false,
                    targetCalories = calories
                )
                _mealsData.value = _mealsData.value.copy(
                    schedule = newSchedule,
                    caloriesLogged = newSchedule.sumOf { it.items.filter { it.isConsumed }.sumOf { it.calories.toDouble() } }.toFloat(),
                    protein = newSchedule.sumOf { slot ->
                        slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }
                    }.toFloat() + protein,
                    carbs = newSchedule.sumOf { slot ->
                        slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }
                    }.toFloat() + carbs,
                    fat = newSchedule.sumOf { slot ->
                        slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }
                    }.toFloat() + fat,
                    fiber = newSchedule.sumOf { slot ->
                        slot.items.filter { it.isConsumed }.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }
                    }.toFloat() + fiber,
                    questProgress = _mealsData.value.protein + protein
                )
                updateDailyLog()
                updateCurrentMeal(newSchedule)
                Log.d("MealsViewModel", "Logged photo meal: $mealName, $calories Kcal")
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error logging photo meal: ${e.message}", e)
            }
        }
    }

    fun hasDietPlanForDate(date: LocalDate): Boolean {
        return _mealsData.value.hasDietPlan && _mealsData.value.schedule.any { it.date == date }
    }
}

data class MealsData(
    val bmr: Float,
    val caloriesLogged: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val schedule: List<MealSlot>,
    val currentMeal: MealSlot? = null,
    val streak: Int,
    val questActive: Boolean,
    val questGoal: String,
    val questProgress: Float,
    val questTarget: Float,
    val selectedDate: LocalDate,
    val mealType: String,
    val favoriteFood: String,
    val customMealRequested: Boolean,
    val customMealMessage: String,
    val hasDietPlan: Boolean,
    val recipes: List<DietComponentCard> = emptyList(), // Changed from List<String>
    val proteinGoal: Float = 0f,
    val carbsGoal: Float = 0f,
    val fatGoal: Float = 0f,
    val fiberGoal: Float = 0f
)

data class MealSlot(
    val id: String,
    val type: String,
    val time: String,
    val items: List<MealItem>,
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val date: LocalDate,
    val isMissed: Boolean = false,
    val targetCalories: Float = 631f
)

data class MealItem(
    val id: String,
    val name: String,
    val servingSize: Float,
    val calories: Float,
    val isConsumed: Boolean
)

data class MealRequest(
    val name: String,
    val meal_time: String,
    val base_portion: Int,
    @SerializedName("base_portion_unit") val basePortionUnit: String,
    val totalCalories: Int,
    val totalProtein: Float? = null, // Optional, set in createDietPlan
    val totalCarbs: Float? = null,  // Optional
    val totalFat: Float? = null,    // Optional
    val meal_date: String,
    val diet_components: List<String>? = null,
    val diet_plan: String? = null,
    @SerializedName("users_permissions_user") val usersPermissionsUser: StrapiApi.UserId? = null
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "name" to name,
            "meal_time" to meal_time,
            "base_portion" to base_portion,
            "base_portion_unit" to basePortionUnit,
            "totalCalories" to totalCalories
        ).plus(totalProtein?.let { mapOf("totalProtein" to it) } ?: emptyMap())
            .plus(totalCarbs?.let { mapOf("totalCarbs" to it) } ?: emptyMap())
            .plus(totalFat?.let { mapOf("totalFat" to it) } ?: emptyMap())
            .plus(mapOf("meal_date" to meal_date))
            .plus(diet_components?.let { mapOf("diet_components" to it) } ?: emptyMap())
            .plus(diet_plan?.let { mapOf("diet_plan" to it) } ?: emptyMap())
            .plus(usersPermissionsUser?.let { mapOf("users_permissions_user" to it.id) } ?: emptyMap())
    )
}

data class DietPlanRequest(
    @SerializedName("plan_id") val name: String,
    @SerializedName("total_calories") val totalCalories: Int,
    @SerializedName("diet_preference") val dietPreference: String,
    val active: Boolean,
    @SerializedName("points_earned") val pointsEarned: Int,
    @SerializedName("diet_goal") val dietGoal: String,
    val meals: List<String>,
    @SerializedName("users_permissions_user") val usersPermissionsUser: StrapiApi.UserId // Fixed field name
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "plan_id" to name,
            "total_calories" to totalCalories,
            "diet_preference" to dietPreference,
            "active" to active,
            "points_earned" to pointsEarned,
            "diet_goal" to dietGoal,
            "meals" to meals,
            "users_permissions_user" to usersPermissionsUser.id
        )
    )
}

data class CustomMealRequest(
    val userId: String,
    val food: String
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "userId" to userId,
            "food" to food
        )
    )
}

data class MealGoalRequest(
    val userId: String,
    val meal: String,
    val calories: Float,
    val time: String
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "userId" to userId,
            "meal" to meal,
            "calories" to calories,
            "time" to time
        )
    )
}

data class FeedbackRequest(
    val userId: String,
    val mealId: String,
    val oldComponentId: String,
    val newComponentId: String,
    val timestamp: String
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "userId" to userId,
            "mealId" to mealId,
            "oldComponentId" to oldComponentId,
            "newComponentId" to newComponentId,
            "timestamp" to timestamp
        )
    )
}

data class DietComponentCard(
    val id: String,
    val name: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val fiber: String
)