package dev.stevennguyen.newbieclaw.domain.invoice

data class ZoneDefinition(
    val name: String,
    val labelPatterns: List<Regex>,
    val strategy: ExtractionStrategy,
    val maxDistance: Int = 200,
    val boundingBox: BoundingBox? = null,
    val extractedValue: String? = null,
    val confidence: Float = 0.0f
) {
    fun withExtractedValue(value: String, confidence: Float): ZoneDefinition {
        return copy(extractedValue = value, confidence = confidence)
    }
    
    fun withBoundingBox(box: BoundingBox): ZoneDefinition {
        return copy(boundingBox = box)
    }
}
