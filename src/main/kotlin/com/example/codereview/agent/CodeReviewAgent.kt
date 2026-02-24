package com.example.codereview.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import com.example.codereview.domain.BugAnalysis
import com.example.codereview.domain.CodeInput
import com.example.codereview.domain.CodeReview
import com.example.codereview.domain.ProjectPath
import com.example.codereview.domain.SecurityAnalysis
import com.example.codereview.domain.StyleAnalysis
import java.io.File

// Directories to skip when walking a project tree
private val IGNORED_DIRS = setOf(".git", "build", "target", "node_modules", ".gradle", ".idea", "out", "dist")

// Map from language name to its file extensions
private fun extensionsFor(language: String): Set<String> = when (language.lowercase()) {
    "kotlin"     -> setOf("kt", "kts")
    "java"       -> setOf("java")
    "python"     -> setOf("py")
    "javascript" -> setOf("js", "mjs", "cjs")
    "typescript" -> setOf("ts", "tsx")
    else         -> setOf(language.lowercase())
}

@Agent(description = "Reviews code for bugs, style issues, and security vulnerabilities")
class CodeReviewAgent {

    /**
     * Entry point: parses the user's natural language request into a ProjectPath.
     * This lets the `x "..."` shell command kick off the full review pipeline.
     */
    @Action(description = "Parse the user's request to extract the project path and language")
    fun parseUserRequest(userInput: UserInput, context: OperationContext): ProjectPath =
        context.ai()
            .withDefaultLlm()
            .createObject(
                """
                Extract the file system path and programming language from this request:
                "${userInput.content}"

                Rules:
                - path: the absolute file system path mentioned by the user (keep it exactly as written)
                - language: one of: kotlin, java, python, javascript, typescript
                  (infer from the request or the path if not stated explicitly)
                """.trimIndent(),
                ProjectPath::class.java,
            )

    /**
     * Reads source files from a local path and produces a CodeInput.
     * Accepts either a single file or a directory.
     * Embabel will call this automatically when the input is a ProjectPath.
     */
    @Action(description = "Read source code files from a local path and prepare them for review")
    fun readSourceFiles(projectPath: ProjectPath): CodeInput {
        val root = File(projectPath.path)
        require(root.exists()) { "Path does not exist: ${projectPath.path}" }

        val extensions = extensionsFor(projectPath.language)

        val files = if (root.isFile) {
            listOf(root)
        } else {
            root.walkTopDown()
                .onEnter { dir -> dir.name !in IGNORED_DIRS }
                .filter { it.isFile && it.extension in extensions }
                .filter { it.length() < 100_000 }   // skip files larger than 100 KB
                .take(15)                             // cap at 15 files to stay within token limits
                .toList()
        }

        require(files.isNotEmpty()) {
            "No ${projectPath.language} source files found in: ${projectPath.path}"
        }

        val combined = files.joinToString("\n\n") { file ->
            "// ═══ File: ${file.relativeTo(root)} ═══\n${file.readText()}"
        }

        return CodeInput(code = combined, language = projectPath.language)
    }

    @Action(description = "Analyze the code for bugs and logic errors")
    fun analyzeForBugs(input: CodeInput, context: OperationContext): BugAnalysis =
        context.ai()
            .withDefaultLlm()
            .createObject(
                """
                You are an expert code reviewer specializing in bug detection.
                Analyze the following ${input.language} code for bugs, logic errors,
                null pointer issues, off-by-one errors, and incorrect assumptions.

                Code:
                ```${input.language}
                ${input.code}
                ```

                Return a structured list of bugs found. If no bugs are found, return an empty list.
                """.trimIndent(),
                BugAnalysis::class.java,
            )

    @Action(description = "Check the code for style and readability issues")
    fun checkCodeStyle(input: CodeInput, context: OperationContext): StyleAnalysis =
        context.ai()
            .withDefaultLlm()
            .createObject(
                """
                You are an expert code reviewer specializing in code style and best practices.
                Analyze the following ${input.language} code for style issues such as:
                - Poor naming conventions
                - Functions that are too long or complex
                - Missing or unclear comments
                - Duplicated code
                - Poor structure or readability

                Code:
                ```${input.language}
                ${input.code}
                ```

                Return a structured list of style issues. If the code is clean, return an empty list.
                """.trimIndent(),
                StyleAnalysis::class.java,
            )

    @Action(description = "Scan the code for security vulnerabilities")
    fun scanForSecurity(input: CodeInput, context: OperationContext): SecurityAnalysis =
        context.ai()
            .withDefaultLlm()
            .createObject(
                """
                You are a security expert specializing in code vulnerability analysis.
                Scan the following ${input.language} code for security issues such as:
                - SQL injection
                - XSS vulnerabilities
                - Insecure deserialization
                - Hardcoded credentials or secrets
                - Improper input validation
                - Sensitive data exposure

                Code:
                ```${input.language}
                ${input.code}
                ```

                Return a structured list of vulnerabilities found. If no issues are found, return an empty list.
                """.trimIndent(),
                SecurityAnalysis::class.java,
            )

    @AchievesGoal(description = "Produce a complete, structured code review report")
    @Action(description = "Combine all analyses into a final code review")
    fun generateReview(
        input: CodeInput,
        bugs: BugAnalysis,
        style: StyleAnalysis,
        security: SecurityAnalysis,
        context: OperationContext,
    ): CodeReview =
        context.ai()
            .withDefaultLlm()
            .createObject(
                """
                You are a senior code reviewer. Combine the following analysis results into
                a final, comprehensive code review report.

                Language: ${input.language}

                Bug analysis found ${bugs.issues.size} issue(s): ${bugs.issues}
                Style analysis found ${style.issues.size} issue(s): ${style.issues}
                Security analysis found ${security.vulnerabilities.size} issue(s): ${security.vulnerabilities}

                Produce a final CodeReview with:
                - A concise summary paragraph
                - The bugs, styleIssues, and securityIssues lists (copy them from the analysis above)
                - An overallScore from 0 to 10 (10 = perfect, no issues)
                - A recommendations list with the top 3–5 actionable improvements
                """.trimIndent(),
                CodeReview::class.java,
            )
}
