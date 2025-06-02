package com.trailblazewellness.fitglide

import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4285F4), // Blue
    secondary = Color(0xFF34A853), // Green
    tertiary = Color(0xFFFBBC05), // Orange
    background = Color(0xFFF5F5F5), // Light gray
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF212121), // Dark gray
    onSurface = Color(0xFF212121)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF82B1FF), // Lighter Blue
    secondary = Color(0xFF81C784), // Lighter Green
    tertiary = Color(0xFFFFD54F), // Lighter Orange
    background = Color(0xFF121212), // Dark gray
    surface = Color(0xFF1E1E1E), // Darker gray
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFE0E0E0), // Light gray
    onSurface = Color(0xFFE0E0E0)
)

@Composable
fun FitGlideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    Log.d("FitGlideTheme", "Applying theme: dark=$darkTheme, primary=${colorScheme.primary}, surface=${colorScheme.surface}, onSurface=${colorScheme.onSurface}")

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}