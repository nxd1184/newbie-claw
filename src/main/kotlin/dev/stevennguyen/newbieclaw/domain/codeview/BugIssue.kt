package dev.stevennguyen.newbieclaw.domain.codeview

data class BugIssue(
    val description: String,
    val lineReference: String,
    val severity: Severity,
)
