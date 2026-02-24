package com.example.codereview.domain

// ── Input ────────────────────────────────────────────────────────────────────

/**
 * Direct code input — user pastes code manually.
 */
data class CodeInput(
    val code: String,
    val language: String,
)

/**
 * Project path input — agent reads source files from disk.
 * [path] can be a single file or a directory.
 * [language] is used to filter by file extension and guide the LLM prompts.
 */
data class ProjectPath(
    val path: String,
    val language: String,
)

// ── Shared ───────────────────────────────────────────────────────────────────

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

// ── Intermediate analysis results ────────────────────────────────────────────

data class BugIssue(
    val description: String,
    val lineReference: String,   // e.g. "line 12" or "function foo()"
    val severity: Severity,
)

data class BugAnalysis(
    val issues: List<BugIssue>,
)

// ─────────────────────────────────────────────────────────────────────────────

data class StyleIssue(
    val description: String,
    val suggestion: String,
)

data class StyleAnalysis(
    val issues: List<StyleIssue>,
)

// ─────────────────────────────────────────────────────────────────────────────

data class SecurityIssue(
    val description: String,
    val severity: Severity,
    val recommendedFix: String,
)

data class SecurityAnalysis(
    val vulnerabilities: List<SecurityIssue>,
)

// ── Final output ─────────────────────────────────────────────────────────────

data class CodeReview(
    val summary: String,
    val bugs: List<BugIssue>,
    val styleIssues: List<StyleIssue>,
    val securityIssues: List<SecurityIssue>,
    val overallScore: Int,              // 0–10, 10 = perfect
    val recommendations: List<String>,
)
