package dev.stevennguyen.agent.domain

data class SourceFile(
    val path: String,
    val content: String,
    val language: String,
)
