package com.example.codereview.domain

data class SourceFile(
    val path: String,
    val content: String,
    val language: String,
)
