package com.smartvision.navigation

import kotlin.math.abs

class SceneMemory(
    private val depthDeltaThreshold: Float = 0.35f,
    private val minSpeakGapMs: Long = 1200L
) {
    private var lastCommand: String = ""
    private var lastObjectId: Int = -1
    private var lastDepth: Float = Float.MAX_VALUE
    private var lastSpokenAt: Long = 0L

    @Synchronized
    fun shouldSpeak(command: String, objectId: Int, depth: Float, nowMs: Long): Boolean {
        if (nowMs - lastSpokenAt < minSpeakGapMs) return false

        val changedCommand = command != lastCommand
        val changedObject = objectId != lastObjectId
        val changedDepth = abs(depth - lastDepth) > depthDeltaThreshold
        val should = changedCommand || changedObject || changedDepth

        if (should) {
            lastCommand = command
            lastObjectId = objectId
            lastDepth = depth
            lastSpokenAt = nowMs
        }
        return should
    }
}
