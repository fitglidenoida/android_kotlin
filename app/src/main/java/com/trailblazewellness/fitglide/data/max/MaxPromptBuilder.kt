package com.trailblazewellness.fitglide.data.max

import com.trailblazewellness.fitglide.presentation.home.MaxMessage
import com.trailblazewellness.fitglide.auth.AuthState


object MaxPromptBuilder {

    fun getPrompt(userName: String?, steps: Float, sleep: Float, hydration: Float): String {
        val name = userName ?: "Yaar"
        val formattedSteps = steps.toInt()
        val formattedSleep = String.format("%.1f", sleep)
        val formattedHydration = String.format("%.1f", hydration)

        return """
You're Max, a cheerful Indian fitness buddy who speaks Hinglish...

User: $name
Steps: $steps
Sleep: $sleep
Hydration: $hydration

  Make it short and full desi swag:
1. Kal ka recap
2. Aaj ka motivation (in under 25 words each)
""${'"'}.trimIndent()

            Make it sound friendly and *Indian-style*:
            - Use phrases like "Boss", "Yaar", "Zabardast", "Full Josh", "Thoda aur mehnat karo"
            - Include emojis
            - Keep it under 25 words per line

            Response:
        """.trimIndent()
    }


    fun getFunGreeting(
        userName: String?,
        steps: Float,
        sleep: Float,
        hydration: Float
    ): MaxMessage {
        val name = userName ?: "FitHero"
        return MaxMessage(
            yesterday = "Arre $name, kal ke ${steps.toInt()} steps mein toh zameen hil gayi!",
            today = "Aaj full josh mein rehna—hydration aur sleep ka jugaad pakka! 💪",
            hasPlayed = false
        )
    }

    fun getPersonalizedPrompt(
        authState: AuthState,
        bmr: Int?,
        steps: Float,
        calories: Float,
        sleep: Float,
        hydration: Float
    ): String {
        val name = authState.userName ?: "FitHero"
        val goal = "stay fit" // Optional: You can pass it later when available
        val userBmr = bmr ?: 2000

        return """
        You are Max, the FitGlide assistant—fun, cheeky, Indian-style motivator.
        Speak in Hinglish. Use emojis and Indian-style punchlines.

        User: $name
        Goal: $goal
        BMR: $userBmr kcal
        Steps taken yesterday: ${steps.toInt()} steps
        Calories burned yesterday: ${calories.toInt()} kcal
        Sleep: ${"%.1f".format(sleep)} hrs
        Water: ${"%.1f".format(hydration)} L

        Write 2 lines only:
        Line 1: Acknowledge or tease about yesterday.
        Line 2: Fire up motivation for today.

        Example:
        Line 1: Arre $name bhai, kal ke $steps steps toh kya kamaal the! 💥  
        Line 2: Aaj ka target tod daalo, dal-roti power active karo! 🍛💪
    """.trimIndent()
    }
}

