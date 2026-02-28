package com.smartvision.navigation

import kotlin.math.abs

class SceneMemory(
    private val depthDeltaThreshold: Float = 0.4f
) {
    private var lastCommand: String = ""
    private var lastObjectId: Int = -1
    private var lastDepth: Float = Float.MAX_VALUE

    fun shouldSpeak(command: String, objectId: Int, depth: Float): Boolean {
        val changedCommand = command != lastCommand
        val changedObject = objectId != lastObjectId
        val changedDepth = abs(depth - lastDepth) > depthDeltaThreshold

        val should = changedCommand || changedObject || changedDepth
        if (should) {
            lastCommand = command
            lastObjectId = objectId
            lastDepth = depth
        }
        return should
    }
}
