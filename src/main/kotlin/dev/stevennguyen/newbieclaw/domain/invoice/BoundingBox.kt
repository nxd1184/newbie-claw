package dev.stevennguyen.newbieclaw.domain.invoice

data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    
    fun contains(word: OcrWord): Boolean {
        return word.x >= x && word.right <= right &&
               word.y >= y && word.bottom <= bottom
    }
    
    fun intersects(other: BoundingBox): Boolean {
        return !(right < other.x || x > other.right ||
                 bottom < other.y || y > other.bottom)
    }
    
    fun expand(margin: Int): BoundingBox {
        return BoundingBox(
            x = x - margin,
            y = y - margin,
            width = width + 2 * margin,
            height = height + 2 * margin
        )
    }
    
    companion object {
        fun fromWords(words: List<OcrWord>): BoundingBox? {
            if (words.isEmpty()) return null
            
            val minX = words.minOf { it.x }
            val minY = words.minOf { it.y }
            val maxX = words.maxOf { it.right }
            val maxY = words.maxOf { it.bottom }
            
            return BoundingBox(
                x = minX,
                y = minY,
                width = maxX - minX,
                height = maxY - minY
            )
        }
    }
}
