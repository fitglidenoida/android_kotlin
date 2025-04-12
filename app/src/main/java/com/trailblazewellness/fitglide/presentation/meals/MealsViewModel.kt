package com.trailblazewellness.fitglide.presentation.meals

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            questGoal = "",
            questProgress = 0f,
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
                        val missed = LocalTime.parse(displayTime, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now()) && components.any { !it.isConsumed }
                        MealSlot(
                            id = meal.documentId ?: "unknown_${System.currentTimeMillis()}",
                            type = meal.name.removeSuffix(" Meal"),
                            time = displayTime,
                            items = components,
                            calories = components.filter { it.isConsumed }.sumOf { it.calories.toDouble() }.toFloat(),
                            protein = components.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }.toFloat(),
                            carbs = components.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }.toFloat(),
                            fat = components.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }.toFloat(),
                            fiber = components.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }.toFloat(),
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
                            val missed = LocalTime.parse(displayTime, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now()) && components.any { !it.isConsumed }
                            MealSlot(
                                id = meal.documentId ?: "unknown_${System.currentTimeMillis()}",
                                type = meal.name.removeSuffix(" Meal"),
                                time = displayTime,
                                items = components,
                                calories = components.filter { it.isConsumed }.sumOf { it.calories.toDouble() }.toFloat(),
                                protein = components.sumOf { parseMacro(componentsCache[it.id]?.protein).toDouble() }.toFloat(),
                                carbs = components.sumOf { parseMacro(componentsCache[it.id]?.carbs).toDouble() }.toFloat(),
                                fat = components.sumOf { parseMacro(componentsCache[it.id]?.fat).toDouble() }.toFloat(),
                                fiber = components.sumOf { parseMacro(componentsCache[it.id]?.fiber).toDouble() }.toFloat(),
                                date = date,
                                isMissed = missed,
                                targetCalories = meal.totalCalories.toFloat()
                            )
                        }
                    }

                    if (dailyLogIds[date] == null) {
                        val dietLogRequest = StrapiApi.DietLogRequest(
                            date = date.toString(),
                            usersPermissionsUser = StrapiApi.UserId("1"), // Updated field name
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
                    streak = 3,
                    selectedDate = date,
                    hasDietPlan = activePlan != null
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
        mealCount: Int
    ) {
        viewModelScope.launch {
            try {
                val token = "Bearer ${authRepository.getAuthState().jwt ?: return@launch}"
                val userId = authRepository.getAuthState().getId().toString()
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

                val dietPlanRequest = DietPlanRequest(
                    name = "diet_plan_${System.currentTimeMillis()}",
                    totalCalories = targetCalories.toInt(),
                    dietPreference = _mealsData.value.mealType,
                    active = true,
                    pointsEarned = 0,
                    dietGoal = strategy,
                    mealIds = emptyList(),
                    userId = StrapiApi.UserId(userId)
                )
                val dietPlanResponse = strapiRepository.postDietPlan(dietPlanRequest, token)
                if (!dietPlanResponse.isSuccessful) {
                    Log.e("MealsViewModel", "Failed to create diet plan: ${dietPlanResponse.code()} - ${dietPlanResponse.errorBody()?.string()}")
                    return@launch
                }
                val dietPlanId = dietPlanResponse.body()?.data?.documentId ?: return@launch

                val components = _searchComponents.value
                val meals = mutableListOf<MealSlot>()
                val mealIds = mutableListOf<String>()

                val mealSlots = when (mealCount) {
                    3 -> listOf(
                        Triple("Breakfast", breakfastFav, "08:00:00.000"),
                        Triple("Lunch", lunchFav, "13:00:00.000"),
                        Triple("Dinner", dinnerFav, "19:00:00.000")
                    )
                    else -> return@launch
                }

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
                        totalCalories = perMealCalories.toInt(),
                        meal_date = _mealsData.value.selectedDate.toString(),
                        diet_components = listOfNotNull(favComponent?.documentId, filler?.documentId),
                        diet_plan = dietPlanId
                    )
                    val mealResponse = strapiRepository.postMeal(mealRequest, token)
                    if (mealResponse.isSuccessful) {
                        val mealId = mealResponse.body()?.data?.documentId ?: "meal_${System.currentTimeMillis()}_${meals.size}"
                        mealIds.add(mealId)
                        val displayTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss.SSS")).format(DateTimeFormatter.ofPattern("HH:mm"))
                        val missed = LocalTime.parse(displayTime, DateTimeFormatter.ofPattern("HH:mm")).isBefore(LocalTime.now())
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
                                protein = parseMacro(favComponent?.protein) + parseMacro(filler?.protein) * scale,
                                carbs = parseMacro(favComponent?.carbs) + parseMacro(filler?.carbs) * scale,
                                fat = parseMacro(favComponent?.fat) + parseMacro(filler?.fat) * scale,
                                fiber = parseMacro(favComponent?.protein) + parseMacro(filler?.fiber) * scale,
                                date = _mealsData.value.selectedDate,
                                isMissed = missed,
                                targetCalories = perMealCalories
                            )
                        )
                    }
                }

                val sortedMeals = meals.sortedBy { LocalTime.parse(it.time, DateTimeFormatter.ofPattern("HH:mm")) }
                val dietLogRequest = StrapiApi.DietLogRequest(
                    date = _mealsData.value.selectedDate.toString(),
                    usersPermissionsUser = StrapiApi.UserId(userId), // Updated field name
                    meals = sortedMeals.map { meal ->
                        StrapiApi.MealLogEntry(
                            mealId = meal.id,
                            components = meal.items.map { StrapiApi.ComponentLogEntry(it.id, it.isConsumed) }
                        )
                    }
                )
                val logResponse = strapiRepository.postDietLog(dietLogRequest, token)
                if (logResponse.isSuccessful) {
                    dailyLogIds[_mealsData.value.selectedDate] = logResponse.body()?.data?.documentId
                    Log.d("MealsViewModel", "Initial daily diet log created for ${_mealsData.value.selectedDate}")
                }

                updateCurrentMeal(sortedMeals)
                _mealsData.value = _mealsData.value.copy(
                    schedule = sortedMeals,
                    hasDietPlan = true
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
                caloriesLogged = schedule.sumOf { it.items.filter { it.isConsumed }.sumOf { it.calories.toDouble() } }.toFloat()
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
                        totalCalories = mealToUpdate.totalCalories,
                        meal_date = mealToUpdate.mealDate,
                        diet_components = updatedComponents,
                        diet_plan = activePlan.documentId
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
            fiber = fiber
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

                val dietLogUpdateRequest = StrapiApi.DietLogUpdateRequest( // Changed to UpdateRequest
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
                    val putResponse = strapiRepository.putDietLog(logId, dietLogUpdateRequest, token) // Fixed type
                    if (putResponse.isSuccessful) {
                        Log.d("MealsViewModel", "Daily diet log updated for $date with PUT (logId: $logId)")
                    } else {
                        Log.e("MealsViewModel", "Failed to update diet log with PUT: ${putResponse.code()} - ${putResponse.errorBody()?.string()}")
                        val dietLogRequest = StrapiApi.DietLogRequest( // Fallback still uses full request
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
                    time = "TBD",
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
                val response = strapiRepository.getDietComponents("Veg", token)
                if (response.isSuccessful) {
                    val recipes = response.body()?.data?.take(10)?.map { "${it.name} - ${it.calories} Kcal" } ?: emptyList()
                    _mealsData.update { it.copy(recipes = recipes) }
                }
            } catch (e: Exception) {
                Log.e("MealsViewModel", "Error fetching recipes: ${e.message}", e)
            }
        }
    }

    fun logRecipe(recipe: String) {
        Log.d("MealsViewModel", "Logged recipe: $recipe")
        // TODO: Implement actual logging to Strapi
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
    val selectedDate: LocalDate,
    val mealType: String,
    val favoriteFood: String,
    val customMealRequested: Boolean,
    val customMealMessage: String,
    val hasDietPlan: Boolean,
    val recipes: List<String> = emptyList()
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
    val totalCalories: Int,
    val meal_date: String,
    val diet_components: List<String>? = null,
    val diet_plan: String? = null
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "name" to name,
            "meal_time" to meal_time,
            "base_portion" to base_portion,
            "totalCalories" to totalCalories,
            "meal_date" to meal_date
        ).plus(diet_components?.let { mapOf("diet_components" to it) } ?: emptyMap())
            .plus(diet_plan?.let { mapOf("diet_plan" to it) } ?: emptyMap())
    )
}

data class DietPlanRequest(
    val name: String,
    val totalCalories: Int,
    val dietPreference: String,
    val active: Boolean,
    val pointsEarned: Int,
    val dietGoal: String,
    val mealIds: List<String>,
    val userId: StrapiApi.UserId
) {
    fun toMapNonNull(): Map<String, Any> = mapOf(
        "data" to mapOf(
            "plan_id" to name,
            "total_calories" to totalCalories,
            "diet_preference" to dietPreference,
            "Active" to active,
            "points_earned" to pointsEarned,
            "diet_goal" to dietGoal,
            "meals" to mealIds,
            "users_permissions_user" to userId.id
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