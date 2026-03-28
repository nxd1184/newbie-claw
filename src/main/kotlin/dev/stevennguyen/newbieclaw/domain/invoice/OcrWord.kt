package dev.stevennguyen.newbieclaw.domain.invoice

data class OcrWord(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
    val pageNumber: Int
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    
    fun distanceTo(other: OcrWord): Double {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
    }
    
    fun isRightOf(other: OcrWord, maxDistance: Int = 200): Boolean {
        return x > other.right && x - other.right <= maxDistance && 
               kotlin.math.abs(centerY - other.centerY) <= height
    }
    
    fun isBelow(other: OcrWord, maxDistance: Int = 100): Boolean {
        return y > other.bottom && y - other.bottom <= maxDistance &&
               kotlin.math.abs(centerX - other.centerX) <= width
    }
    
    fun overlapsVertically(other: OcrWord): Boolean {
        return !(bottom < other.y || y > other.bottom)
    }
    
    fun overlapsHorizontally(other: OcrWord): Boolean {
        return !(right < other.x || x > other.right)
    }
}
