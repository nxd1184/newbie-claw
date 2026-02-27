package com.example.codereview.agent

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "code-review")
data class CodeReviewProperties(
    val chunkSize: Int = 10_000,
    val maxFiles: Int = 10,
    val maxFileSizeBytes: Long = 50_000,
)
