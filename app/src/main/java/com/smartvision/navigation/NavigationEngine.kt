package com.smartvision.navigation

import com.smartvision.tracking.TrackedObject

class NavigationEngine {

    private val hazardPriority = listOf("staircase", "pothole", "wall")

    fun buildCommand(trackedObjects: List<TrackedObject>): String {
        if (trackedObjects.isEmpty()) return "Path unclear, move slowly"

        val nearest = trackedObjects.minByOrNull { it.depth } ?: return "Path unclear"
        if (nearest.depth < 0.5f) return "Stop. Obstacle very close"

        val hazard = trackedObjects
            .filter { it.label.lowercase() in hazardPriority }
            .minByOrNull { hazardPriority.indexOf(it.label.lowercase()) }
        if (hazard != null) {
            return when (hazard.zone) {
                "LEFT" -> "Hazard left, keep right"
                "RIGHT" -> "Hazard right, keep left"
                else -> "Hazard ahead, slow down"
            }
        }

        val leftSpace = trackedObjects.count { it.zone == "LEFT" && it.depth > 1.2f }
        val rightSpace = trackedObjects.count { it.zone == "RIGHT" && it.depth > 1.2f }

        return when {
            leftSpace > rightSpace -> "Slide left"
            rightSpace > leftSpace -> "Slide right"
            else -> "Go forward carefully"
        }
    }
}
