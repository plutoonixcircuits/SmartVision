package com.smartvision.navigation

import com.smartvision.tracking.TrackedObject

class NavigationEngine {
    private val hazardPriority = listOf("staircase", "pothole", "wall", "pole", "ramp")

    fun buildCommand(trackedObjects: List<TrackedObject>): String {
        if (trackedObjects.isEmpty()) {
            return "Clear path. Continue forward."
        }

        val centerObjects = trackedObjects.filter { it.zone == "CENTER" }
        val nearestCenter = centerObjects.minByOrNull { it.depth }

        if (nearestCenter != null && nearestCenter.depth < 0.5f) {
            return "STOP. Object very close ahead."
        }

        val prioritizedHazard = trackedObjects
            .filter { it.label.lowercase() in hazardPriority }
            .sortedBy { hazardPriority.indexOf(it.label.lowercase()) }
            .firstOrNull()

        if (prioritizedHazard != null) {
            return when (prioritizedHazard.label.lowercase()) {
                "staircase" -> "Staircase detected."
                "pothole" -> "Pothole ahead. Step carefully."
                else -> when (prioritizedHazard.zone) {
                    "LEFT" -> "Obstacle left. Move slightly right."
                    "RIGHT" -> "Obstacle right. Move slightly left."
                    else -> "Obstacle ahead. Slow down."
                }
            }
        }

        val leftMinDepth = trackedObjects.filter { it.zone == "LEFT" }.minOfOrNull { it.depth } ?: 10f
        val rightMinDepth = trackedObjects.filter { it.zone == "RIGHT" }.minOfOrNull { it.depth } ?: 10f

        return when {
            nearestCenter == null -> "Clear path. Continue forward."
            leftMinDepth > rightMinDepth + 0.25f -> "Move slightly left."
            rightMinDepth > leftMinDepth + 0.25f -> "Move slightly right."
            else -> "Proceed slowly, obstacle ahead."
        }
    }
}
