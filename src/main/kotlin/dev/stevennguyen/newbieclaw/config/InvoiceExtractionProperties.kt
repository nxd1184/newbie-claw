package dev.stevennguyen.newbieclaw.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "invoice-extraction")
data class InvoiceExtractionProperties(
    val maxPdfSizeBytes: Long = 10_000_000,
    val extractionTimeout: Long = 300,
    val enableOcr: Boolean = false
)
