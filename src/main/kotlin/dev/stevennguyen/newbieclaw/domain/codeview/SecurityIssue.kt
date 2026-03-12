package dev.stevennguyen.newbieclaw.domain.codeview

data class SecurityIssue(
    val description: String,
    val severity: Severity,
    val recommendedFix: String,
)
