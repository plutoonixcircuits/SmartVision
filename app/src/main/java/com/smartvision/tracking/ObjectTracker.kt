package com.smartvision.tracking

import com.smartvision.ml.DetectedObject
import com.smartvision.navigation.Kalman1D
import kotlin.math.hypot

class ObjectTracker(
    private val matchDistancePx: Float = 120f,
    private val staleMs: Long = 1200L
) {
    private var nextId = 0
    private val tracked = mutableMapOf<Int, TrackedObject>()
    private val xFilters = mutableMapOf<Int, Kalman1D>()
    private val yFilters = mutableMapOf<Int, Kalman1D>()
    private val depthFilters = mutableMapOf<Int, Kalman1D>()

    @Synchronized
    fun update(detections: List<DetectedObject>, nowMs: Long = System.currentTimeMillis()): List<TrackedObject> {
        val active = mutableListOf<TrackedObject>()

        detections.forEach { det ->
            val match = tracked.values
                .filter { it.label == det.label }
                .minByOrNull { old -> hypot(old.centerX - det.centerX, old.centerY - det.centerY) }

            val id = if (match != null && hypot(match.centerX - det.centerX, match.centerY - det.centerY) < matchDistancePx) {
                match.id
            } else {
                ++nextId
            }

            val x = xFilters.getOrPut(id) { Kalman1D(0.03f, 0.2f) }.update(det.centerX)
            val y = yFilters.getOrPut(id) { Kalman1D(0.03f, 0.2f) }.update(det.centerY)
            val depth = depthFilters.getOrPut(id) { Kalman1D(0.02f, 0.1f) }.update(det.depth)

            val trackedObject = TrackedObject(
                id = id,
                label = det.label,
                centerX = x,
                centerY = y,
                depth = depth,
                zone = det.zone,
                lastSeen = nowMs
            )
            tracked[id] = trackedObject
            active += trackedObject
        }

        val staleIds = tracked.values.filter { nowMs - it.lastSeen > staleMs }.map { it.id }
        staleIds.forEach { staleId ->
            tracked.remove(staleId)
            xFilters.remove(staleId)
            yFilters.remove(staleId)
            depthFilters.remove(staleId)
        }

        return active.sortedBy { it.depth }
    }
}
