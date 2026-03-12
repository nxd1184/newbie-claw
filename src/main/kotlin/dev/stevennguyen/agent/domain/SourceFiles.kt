package dev.stevennguyen.agent.domain

data class SourceFiles(
    val files: List<SourceFile>,
    val language: String,
)
