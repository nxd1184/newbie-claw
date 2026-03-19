package dev.stevennguyen.newbieclaw.domain.codereview

/** Deduplicated findings across all files in the project (Reduce 2 output). */
data class ProjectFindings(
    val language: String,
    val fileFindings: List<FileFindings>,
    val bugs: List<BugIssue>,
    val styleIssues: List<StyleIssue>,
    val securityIssues: List<SecurityIssue>,
)
