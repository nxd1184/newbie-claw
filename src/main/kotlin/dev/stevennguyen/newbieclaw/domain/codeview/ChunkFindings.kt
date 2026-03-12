package dev.stevennguyen.newbieclaw.domain.codeview

/** Raw findings from analyzing one 3k-char chunk of a source file. */
data class ChunkFindings(
    val bugs: List<BugIssue>,
    val styleIssues: List<StyleIssue>,
    val securityIssues: List<SecurityIssue>,
)
