package dev.stevennguyen.newbieclaw.domain.codereview

data class SourceFile(
    val path: String,
    val content: String,
    val language: String,
)
