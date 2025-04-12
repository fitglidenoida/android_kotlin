package com.trailblazewellness.fitglide.data.utils

import java.time.Duration

object TimeUtils {
    fun formatDuration(duration: Duration?): String {
        duration ?: return "0h 0m"
        val totalMinutes = duration.toMinutes()
        val hours = duration.toHours()
        val minutes = (totalMinutes - hours * 60).toInt()
        return "${hours}h ${minutes}m"
    }
}