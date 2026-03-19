package dev.stevennguyen.newbieclaw.domain.codereview

data class BugIssue(
    val description: String,
    val lineReference: String,
    val severity: Severity,
)
