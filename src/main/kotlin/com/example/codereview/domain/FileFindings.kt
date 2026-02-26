package com.example.codereview.domain

/** Deduplicated findings for a single source file (Reduce 1 output). */
data class FileFindings(
    val filePath: String,
    val bugs: List<BugIssue>,
    val styleIssues: List<StyleIssue>,
    val securityIssues: List<SecurityIssue>,
)
