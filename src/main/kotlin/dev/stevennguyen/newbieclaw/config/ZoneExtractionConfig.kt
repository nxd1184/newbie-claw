package dev.stevennguyen.newbieclaw.config

import dev.stevennguyen.newbieclaw.domain.invoice.ExtractionStrategy
import dev.stevennguyen.newbieclaw.domain.invoice.PreprocessingStep
import dev.stevennguyen.newbieclaw.domain.invoice.ZonePattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "zone-extraction")
data class ZoneExtractionConfig(
    var confidenceThreshold: Float = 0.5f,
    var highConfidenceThreshold: Float = 0.8f,
    var ocrDpi: Float = 300f,
    var ocrPsmMode: Int = 11,
    
    var preprocessingEnabled: Boolean = true,
    var preprocessingSteps: List<PreprocessingStep> = listOf(
        PreprocessingStep.DESKEW,
        PreprocessingStep.DENOISE,
        PreprocessingStep.ENHANCE_CONTRAST,
        PreprocessingStep.BINARIZE
    ),
    var denoiseStrength: Int = 3,
    var contrastFactor: Double = 1.5,
    
    var fuzzyMatchingEnabled: Boolean = true,
    var similarityThreshold: Float = 0.8f,
    
    var multiPassOcrEnabled: Boolean = false,
    var maxPasses: Int = 2,
    
    var patterns: Map<String, ZonePatternConfig> = mapOf(
        "customer-number" to ZonePatternConfig(
            labels = listOf("Customer Number", "Cust No", "Customer #", "Cust#"),
            strategy = ExtractionStrategy.RIGHT_OF,
            maxDistance = 200
        ),
        "order-number" to ZonePatternConfig(
            labels = listOf("Order No", "Invoice No", "Doc No", "Order#", "Invoice#"),
            strategy = ExtractionStrategy.RIGHT_OF,
            maxDistance = 200
        ),
        "line-number" to ZonePatternConfig(
            labels = listOf("Line No", "Item No", "Line", "Line#"),
            strategy = ExtractionStrategy.TABLE_COLUMN,
            maxDistance = 100
        )
    )
) {
    fun getZonePatterns(): List<ZonePattern> {
        return patterns.map { (name, config) ->
            ZonePattern(
                name = name,
                labels = config.labels,
                strategy = config.strategy,
                maxDistance = config.maxDistance
            )
        }
    }
}

data class ZonePatternConfig(
    var labels: List<String> = emptyList(),
    var strategy: ExtractionStrategy = ExtractionStrategy.RIGHT_OF,
    var maxDistance: Int = 200
)
