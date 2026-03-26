package dev.stevennguyen.newbieclaw.domain.invoice

data class ZoneExtractionResult(
    val zones: Map<String, ZoneDefinition>,
    val overallConfidence: Float,
    val extractionMethod: String
) {
    fun getZoneValue(zoneName: String): String? {
        return zones[zoneName]?.extractedValue
    }
    
    fun getZoneConfidence(zoneName: String): Float {
        return zones[zoneName]?.confidence ?: 0.0f
    }
    
    fun isHighConfidence(): Boolean = overallConfidence >= 0.8f
    fun isMediumConfidence(): Boolean = overallConfidence >= 0.5f && overallConfidence < 0.8f
    fun isLowConfidence(): Boolean = overallConfidence < 0.5f
}
