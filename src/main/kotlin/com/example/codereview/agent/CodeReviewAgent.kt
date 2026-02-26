package com.example.codereview.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import com.example.codereview.domain.ChunkFindings
import com.example.codereview.domain.FileFindings
import com.example.codereview.domain.ProjectFindings
import com.example.codereview.domain.ProjectPath
import com.example.codereview.domain.CodeReview
import com.example.codereview.domain.SourceFile
import com.example.codereview.domain.SourceFiles
import java.io.File

private val IGNORED_DIRS = setOf(".git", "build", "target", "node_modules", ".gradle", ".idea", "out", "dist")

// ~3000 chars fits comfortably inside a 4k-token context with prompt overhead
private const val CHUNK_SIZE = 3000

private fun extensionsFor(language: String): Set<String> = when (language.lowercase()) {
    "kotlin"     -> setOf("kt", "kts")
    "java"       -> setOf("java")
    "python"     -> setOf("py")
    "javascript" -> setOf("js", "mjs", "cjs")
    "typescript" -> setOf("ts", "tsx")
    else         -> setOf(language.lowercase())
}

private fun printStep(label: String, detail: String = "") {
    println("\n" + "=".repeat(80))
    println(">>> $label")
    if (detail.isNotEmpty()) println("    $detail")
    println("=".repeat(80) + "\n")
}

@Agent(description = "Reviews code for bugs, style issues, and security vulnerabilities")
class CodeReviewAgent {

    @Action(description = "Parse the user's request to extract the project path and language")
    fun parseUserRequest(userInput: UserInput, context: OperationContext): ProjectPath {
        val prompt = """
            Extract the file system path and programming language from this request:
            "${userInput.content}"

            Rules:
            - path: the absolute file system path mentioned by the user (keep it exactly as written)
            - language: one of: kotlin, java, python, javascript, typescript
              (infer from the request or the path if not stated explicitly)
        """.trimIndent()
        printStep("STEP 1/3  parseUserRequest")
        return context.ai().withDefaultLlm().createObject(prompt, ProjectPath::class.java)
    }

    @Action(description = "Read source files from disk, returning one SourceFile per file")
    fun readSourceFiles(projectPath: ProjectPath): SourceFiles {
        val root = File(projectPath.path)
        require(root.exists()) { "Path does not exist: ${projectPath.path}" }

        val extensions = extensionsFor(projectPath.language)
        val files = if (root.isFile) listOf(root)
        else root.walkTopDown()
            .onEnter { dir -> dir.name !in IGNORED_DIRS }
            .filter { it.isFile && it.extension in extensions }
            .filter { it.length() < 50_000 }
            .take(10)
            .toList()

        require(files.isNotEmpty()) { "No ${projectPath.language} files found in: ${projectPath.path}" }

        val sourceFiles = files.map { file ->
            SourceFile(
                path = file.relativeTo(root).path,
                content = file.readText(),
                language = projectPath.language,
            )
        }
        println("\n[readSourceFiles] Loaded ${sourceFiles.size} file(s): ${sourceFiles.map { it.path }}\n")
        return SourceFiles(files = sourceFiles, language = projectPath.language)
    }

    /**
     * Map/Reduce analysis:
     *   MAP      — split each file into CHUNK_SIZE chunks, call LLM once per chunk
     *   REDUCE 1 — LLM merges/dedupes chunk findings per file
     *   REDUCE 2 — LLM merges/dedupes file findings across the whole repo
     */
    @Action(description = "Map: analyze each file in 4k-context chunks. Reduce: merge per file, then per repo")
    fun mapReduceAnalysis(sourceFiles: SourceFiles, context: OperationContext): ProjectFindings {
        val llm = context.ai().withDefaultLlm()

        // ── MAP + REDUCE 1: per-file ──────────────────────────────────────────────
        val fileFindings: List<FileFindings> = sourceFiles.files.mapIndexed { fileIdx, file ->
            val chunks = file.content.chunked(CHUNK_SIZE)
            printStep(
                "MAP  file ${fileIdx + 1}/${sourceFiles.files.size}: ${file.path}",
                "${chunks.size} chunk(s) of up to $CHUNK_SIZE chars",
            )

            // MAP: analyze each chunk independently
            val chunkResults: List<ChunkFindings> = chunks.mapIndexed { chunkIdx, chunk ->
                val prompt = """
                    You are a code reviewer. Analyze this ${file.language} code snippet.
                    It is chunk ${chunkIdx + 1} of ${chunks.size} from file "${file.path}".
                    Identify bugs, style issues, and security vulnerabilities visible in this snippet.
                    Be concise — focus only on what is clearly wrong in these lines.

                    ```${file.language}
                    $chunk
                    ```
                """.trimIndent()
                printStep("  MAP  chunk ${chunkIdx + 1}/${chunks.size} of ${file.path}")
                llm.createObject(prompt, ChunkFindings::class.java)
            }

            // REDUCE 1: merge all chunk findings for this file
            if (chunkResults.size == 1) {
                FileFindings(
                    filePath = file.path,
                    bugs = chunkResults[0].bugs,
                    styleIssues = chunkResults[0].styleIssues,
                    securityIssues = chunkResults[0].securityIssues,
                )
            } else {
                val allBugs      = chunkResults.flatMap { it.bugs }
                val allStyle     = chunkResults.flatMap { it.styleIssues }
                val allSecurity  = chunkResults.flatMap { it.securityIssues }
                val mergePrompt = """
                    Merge and deduplicate these code review findings from ${chunkResults.size} chunks of "${file.path}".
                    Remove exact duplicates and near-duplicates. When issues overlap, keep the most descriptive version.

                    Bugs (${allBugs.size} total across chunks):
                    ${allBugs.joinToString("\n") { "- $it" }}

                    Style issues (${allStyle.size} total):
                    ${allStyle.joinToString("\n") { "- $it" }}

                    Security issues (${allSecurity.size} total):
                    ${allSecurity.joinToString("\n") { "- $it" }}
                """.trimIndent()
                printStep("  REDUCE 1  merge ${chunkResults.size} chunks → ${file.path}")
                val merged = llm.createObject(mergePrompt, ChunkFindings::class.java)
                FileFindings(
                    filePath = file.path,
                    bugs = merged.bugs,
                    styleIssues = merged.styleIssues,
                    securityIssues = merged.securityIssues,
                )
            }
        }

        // ── REDUCE 2: merge findings across all files ─────────────────────────────
        if (fileFindings.size == 1) {
            return ProjectFindings(
                language = sourceFiles.language,
                fileFindings = fileFindings,
                bugs = fileFindings[0].bugs,
                styleIssues = fileFindings[0].styleIssues,
                securityIssues = fileFindings[0].securityIssues,
            )
        }

        val allBugs     = fileFindings.flatMap { it.bugs }
        val allStyle    = fileFindings.flatMap { it.styleIssues }
        val allSecurity = fileFindings.flatMap { it.securityIssues }
        val repoPrompt = """
            Merge and deduplicate code review findings from ${fileFindings.size} ${sourceFiles.language} files.
            Cross-file patterns (same issue in multiple files) should become a single finding noting it is widespread.
            File-specific issues should be kept as-is.

            Bugs (${allBugs.size} across all files):
            ${allBugs.joinToString("\n") { "- $it" }}

            Style issues (${allStyle.size} across all files):
            ${allStyle.joinToString("\n") { "- $it" }}

            Security issues (${allSecurity.size} across all files):
            ${allSecurity.joinToString("\n") { "- $it" }}
        """.trimIndent()
        printStep("REDUCE 2  merge ${fileFindings.size} files → project-level findings")
        val projectLevel = llm.createObject(repoPrompt, ChunkFindings::class.java)

        return ProjectFindings(
            language = sourceFiles.language,
            fileFindings = fileFindings,
            bugs = projectLevel.bugs,
            styleIssues = projectLevel.styleIssues,
            securityIssues = projectLevel.securityIssues,
        )
    }

    @AchievesGoal(description = "Produce a complete, structured code review report")
    @Action(description = "Generate the final code review report from merged project findings")
    fun generateReview(findings: ProjectFindings, context: OperationContext): CodeReview {
        val prompt = """
            You are a senior code reviewer. Produce a final code review report for this ${findings.language} project.
            ${findings.fileFindings.size} file(s) were analysed using map/reduce analysis.

            Bugs found (${findings.bugs.size}):
            ${findings.bugs.joinToString("\n") { "- $it" }}

            Style issues found (${findings.styleIssues.size}):
            ${findings.styleIssues.joinToString("\n") { "- $it" }}

            Security issues found (${findings.securityIssues.size}):
            ${findings.securityIssues.joinToString("\n") { "- $it" }}

            Produce a CodeReview with:
            - summary: a concise paragraph describing the overall quality
            - bugs, styleIssues, securityIssues: copy from above
            - overallScore: 0–10 (10 = perfect, no issues)
            - recommendations: top 3–5 actionable improvements
        """.trimIndent()
        printStep("STEP 3/3  generateReview")
        return context.ai().withDefaultLlm().createObject(prompt, CodeReview::class.java)
    }
}
