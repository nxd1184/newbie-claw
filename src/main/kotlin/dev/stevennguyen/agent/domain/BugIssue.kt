package dev.stevennguyen.agent.domain

data class BugIssue(
    val description: String,
    val lineReference: String,
    val severity: Severity,
)
