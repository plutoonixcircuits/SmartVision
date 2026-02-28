package com.smartvision.navigation

data class SpatialCell(
    val row: Int,
    val col: Int,
    val zone: String
)

class SpatialMapper {
    fun mapTo3x3(centerX: Float, centerY: Float, frameWidth: Int, frameHeight: Int): SpatialCell {
        val col = ((centerX / frameWidth.toFloat()) * 3f).toInt().coerceIn(0, 2)
        val row = ((centerY / frameHeight.toFloat()) * 3f).toInt().coerceIn(0, 2)
        val zone = when (col) {
            0 -> "LEFT"
            1 -> "CENTER"
            else -> "RIGHT"
        }
        return SpatialCell(row = row, col = col, zone = zone)
    }
}
