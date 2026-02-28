package com.smartvision.tracking

import com.smartvision.ml.DetectedObject
import kotlin.math.hypot

class ObjectTracker(
    private val matchDistancePx: Float = 140f,
    private val staleMs: Long = 1500L
) {
    private var nextId = 1
    private val tracked = mutableMapOf<Int, TrackedObject>()

    fun update(detections: List<DetectedObject>, nowMs: Long = System.currentTimeMillis()): List<TrackedObject> {
        val active = mutableListOf<TrackedObject>()

        detections.forEach { det ->
            val best = tracked.values
                .filter { it.label == det.label }
                .minByOrNull { old -> hypot(old.centerX - det.centerX, old.centerY - det.centerY) }

            val id = if (best != null && hypot(best.centerX - det.centerX, best.centerY - det.centerY) < matchDistancePx) {
                best.id
            } else {
                nextId++
            }

            val obj = TrackedObject(
                id = id,
                label = det.label,
                centerX = det.centerX,
                centerY = det.centerY,
                depth = det.depth,
                zone = det.zone,
                lastSeen = nowMs
            )
            tracked[id] = obj
            active += obj
        }

        tracked.entries.removeIf { nowMs - it.value.lastSeen > staleMs }
        return active.sortedBy { it.id }
    }
}
