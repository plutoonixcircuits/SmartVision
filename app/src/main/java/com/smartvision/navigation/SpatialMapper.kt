package com.smartvision.navigation

class SpatialMapper {
    fun mapZone(centerX: Float, frameWidth: Int): String {
        val third = frameWidth / 3f
        return when {
            centerX < third -> "LEFT"
            centerX < third * 2f -> "CENTER"
            else -> "RIGHT"
        }
    }
}
