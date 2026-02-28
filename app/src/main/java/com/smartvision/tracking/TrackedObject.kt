package com.smartvision.tracking

data class TrackedObject(
    val id: Int,
    val label: String,
    val centerX: Float,
    val centerY: Float,
    val depth: Float,
    val zone: String,
    val lastSeen: Long
)
