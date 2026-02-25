package com.example.codereview.domain

data class SecurityIssue(
    val description: String,
    val severity: Severity,
    val recommendedFix: String,
)
