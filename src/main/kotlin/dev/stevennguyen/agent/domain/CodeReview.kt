package dev.stevennguyen.agent.domain

data class CodeReview(
    val summary: String,
    val bugs: List<BugIssue>,
    val styleIssues: List<StyleIssue>,
    val securityIssues: List<SecurityIssue>,
    val overallScore: Int,              // 0–10, 10 = perfect
    val recommendations: List<String>,
)
