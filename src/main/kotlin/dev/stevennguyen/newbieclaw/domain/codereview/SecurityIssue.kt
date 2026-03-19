package dev.stevennguyen.newbieclaw.domain.codereview

data class SecurityIssue(
    val description: String,
    val severity: Severity,
    val recommendedFix: String,
)
