package dev.stevennguyen.newbieclaw.domain.codeview

data class SourceFile(
    val path: String,
    val content: String,
    val language: String,
)
