package dev.stevennguyen.newbieclaw.agent

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "code-review")
data class CodeReviewProperties(
    val chunkSize: Int = 40_000,
    val maxFiles: Int = 50,
    val maxFileSizeBytes: Long = 50_000,
    val concurrency: Int = 4,
    val batchThreshold: Int = 20_000,
)
