package dev.stevennguyen.newbieclaw.domain.invoice

data class ZonePattern(
    val name: String,
    val labels: List<String>,
    val strategy: ExtractionStrategy,
    val maxDistance: Int = 200
) {
    fun toZoneDefinition(): ZoneDefinition {
        val patterns = labels.map { label ->
            Regex(Regex.escape(label), RegexOption.IGNORE_CASE)
        }
        return ZoneDefinition(
            name = name,
            labelPatterns = patterns,
            strategy = strategy,
            maxDistance = maxDistance
        )
    }
}
