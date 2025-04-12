package com.trailblazewellness.fitglide.utils

object MealFrequencyPredictor {

    fun predictMealFrequency(tdee: Int, goal: String?): Int {
        return when {
            tdee < 1800 -> 3
            tdee in 1800..2400 -> 5
            tdee > 2400 -> {
                when (goal?.lowercase()) {
                    "gain", "muscle gain", "bulk" -> 6
                    "loss", "fat loss", "cut" -> 5
                    else -> 5
                }
            }
            else -> 3
        }
    }
}
