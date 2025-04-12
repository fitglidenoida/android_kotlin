package com.trailblazewellness.fitglide.presentation.workout

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.width as composeWidth
import androidx.compose.foundation.layout.height as composeHeight

fun Modifier.dpWidth(width: Dp) = this.composeWidth(width)
fun Modifier.dpHeight(height: Dp) = this.composeHeight(height)