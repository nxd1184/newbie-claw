package dev.stevennguyen.agent.domain

data class SecurityIssue(
    val description: String,
    val severity: Severity,
    val recommendedFix: String,
)
