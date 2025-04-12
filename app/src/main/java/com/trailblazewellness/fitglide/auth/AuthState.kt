package com.trailblazewellness.fitglide.auth

data class AuthState(
    val jwt: String? = null,
    private val id: String? = null, // Changed from Int? to String?
    val userName: String? = null // Added userName
) {
    fun getId(): String? = id // No conversion needed, already String?
}