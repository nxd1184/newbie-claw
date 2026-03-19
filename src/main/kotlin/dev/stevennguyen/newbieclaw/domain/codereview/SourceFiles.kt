package dev.stevennguyen.newbieclaw.domain.codereview

data class SourceFiles(
    val files: List<SourceFile>,
    val language: String,
)
