package dev.stevennguyen.newbieclaw.domain.codeview

data class SourceFiles(
    val files: List<SourceFile>,
    val language: String,
)
