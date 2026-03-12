package dev.stevennguyen.agent.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import dev.stevennguyen.agent.domain.ChunkFindings
import dev.stevennguyen.agent.domain.FileFindings
import dev.stevennguyen.agent.domain.ProjectFindings
import dev.stevennguyen.agent.domain.ProjectPath
import dev.stevennguyen.agent.domain.CodeReview
import dev.stevennguyen.agent.domain.SourceFile
import dev.stevennguyen.agent.domain.SourceFiles
import java.io.File
import kotlin.time.measureTimedValue
import kotlin.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val IGNORED_DIRS = setOf(".git", "build", "target", "node_modules", ".gradle", ".idea", "out", "dist")

private data class WorkUnit(
    val label: String,
    val filePaths: List<String>,
    val promptBody: String,
)

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

private fun Duration.fmt(): String {
    val totalSecs = inWholeSeconds
    return if (totalSecs >= 60) "${totalSecs / 60}m ${totalSecs % 60}s" else "${totalSecs}s"
}

private fun <T> timed(label: String, block: () -> T): T {
    val (value, duration) = measureTimedValue(block)
    println("    ⏱  $label took ${duration.fmt()}")
    return value
}

@Agent(description = "Reviews code for bugs, style issues, and security vulnerabilities")
class CodeReviewAgent(private val props: CodeReviewProperties) {

    @Volatile
    private var reviewStartMs: Long = 0

    @Action(description = "Parse the user's request to extract the project path and language")
    fun parseUserRequest(userInput: UserInput, context: OperationContext): ProjectPath {
        reviewStartMs = System.currentTimeMillis()
        printStep("STEP 1/3  parseUserRequest")

        // Try fast regex extraction first — avoid an LLM round-trip
        val text = userInput.content
        val pathRegex = Regex("""([A-Z]:\\[^\s"]+|/[^\s"]+)""")
        val pathMatch = pathRegex.find(text)?.groupValues?.get(1)

        if (pathMatch != null) {
            val language = inferLanguage(text, pathMatch)
            println("    ⚡ Extracted path='$pathMatch', language='$language' (no LLM call)")
            return ProjectPath(path = pathMatch, language = language)
        }

        // Fallback to LLM if regex can't find a path
        val prompt = """
            Extract the file system path and programming language from this request:
            "${userInput.content}"
            Rules:
            - path: the absolute file system path mentioned by the user (keep it exactly as written)
            - language: one of: kotlin, java, python, javascript, typescript
              (infer from the request or the path if not stated explicitly)
        """.trimIndent()
        return timed("parseUserRequest (LLM fallback)") {
            context.ai().withDefaultLlm().createObject(prompt, ProjectPath::class.java)
        }
    }

    private fun inferLanguage(text: String, path: String): String {
        val lower = (text + " " + path).lowercase()
        return when {
            "kotlin" in lower || ".kt" in lower -> "kotlin"
            "java" in lower || ".java" in lower || "spring" in lower -> "java"
            "python" in lower || ".py" in lower -> "python"
            "typescript" in lower || ".ts" in lower || ".tsx" in lower -> "typescript"
            "javascript" in lower || ".js" in lower -> "javascript"
            else -> "java"
        }
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
            .filter { it.length() < props.maxFileSizeBytes }
            .take(props.maxFiles)
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
     * Map/Reduce analysis (optimized for local LLMs):
     *   - Small files (< batchThreshold) are batched into single LLM calls to reduce call count
     *   - Large files are chunked and each chunk gets its own LLM call
     *   - All work units run in parallel (concurrency controlled by code-review.concurrency)
     *   - REDUCE 1 merges chunks only when a file exceeds chunk-size
     *   - REDUCE 2 is deferred to generateReview to save one LLM call
     */
    @Action(description = "Parallel MAP with batching: group small files, analyze concurrently, merge chunks per file")
    fun mapReduceAnalysis(sourceFiles: SourceFiles, context: OperationContext): ProjectFindings {
        val llm = context.ai().withDefaultLlm()
        val analysisStart = System.currentTimeMillis()
        val lang = sourceFiles.language

        // ── Partition into small (batchable) and large (chunked individually) ────
        val smallFiles = sourceFiles.files.filter { it.content.length < props.batchThreshold }
        val largeFiles = sourceFiles.files.filter { it.content.length >= props.batchThreshold }

        // ── Build work units ─────────────────────────────────────────────────────
        //   A "batch" work unit groups several small files into one LLM call.
        //   A "chunk" work unit is a single chunk of a large file.
        val workUnits = mutableListOf<WorkUnit>()

        // Batch small files into groups that fit within chunkSize
        if (smallFiles.isNotEmpty()) {
            var batch = mutableListOf<SourceFile>()
            var batchLen = 0
            for (file in smallFiles) {
                if (batchLen + file.content.length > props.chunkSize && batch.isNotEmpty()) {
                    workUnits += buildBatchUnit(batch, lang)
                    batch = mutableListOf()
                    batchLen = 0
                }
                batch += file
                batchLen += file.content.length
            }
            if (batch.isNotEmpty()) {
                workUnits += buildBatchUnit(batch, lang)
            }
        }

        // Large files: one work unit per chunk
        for (file in largeFiles) {
            val chunks = file.content.chunked(props.chunkSize)
            chunks.forEachIndexed { idx, chunk ->
                val chunkLabel = if (chunks.size > 1) " (chunk ${idx + 1}/${chunks.size})" else ""
                workUnits += WorkUnit(
                    label = "${file.path}$chunkLabel",
                    filePaths = listOf(file.path),
                    promptBody = """
                        Review this $lang code for bugs, style issues, and security vulnerabilities. Be concise.
                        File: "${file.path}"$chunkLabel
                        ```$lang
                        $chunk
                        ```
                    """.trimIndent(),
                )
            }
        }

        printStep(
            "MAP phase",
            "${sourceFiles.files.size} file(s) → ${workUnits.size} work unit(s) " +
                "(${smallFiles.size} batched, ${largeFiles.size} large), concurrency=${props.concurrency}",
        )

        // ── Parallel MAP ─────────────────────────────────────────────────────────
        val semaphore = Semaphore(props.concurrency)
        val unitResults: List<Pair<List<String>, ChunkFindings>> = runBlocking {
            workUnits.mapIndexed { idx, unit ->
                async {
                    semaphore.withPermit {
                        printStep("  MAP  unit ${idx + 1}/${workUnits.size}: ${unit.label}")
                        val findings = timed("LLM unit ${idx + 1}") {
                            llm.createObject(unit.promptBody, ChunkFindings::class.java)
                        }
                        unit.filePaths to findings
                    }
                }
            }.awaitAll()
        }

        // ── Group results by file path ───────────────────────────────────────────
        val findingsByFile = mutableMapOf<String, MutableList<ChunkFindings>>()
        for ((paths, findings) in unitResults) {
            for (path in paths) {
                findingsByFile.getOrPut(path) { mutableListOf() } += findings
            }
        }

        // ── REDUCE 1: merge chunk findings per file (only when >1 chunk) ─────────
        val fileFindings: List<FileFindings> = sourceFiles.files.map { file ->
            val results = findingsByFile[file.path]
                ?: return@map FileFindings(file.path, emptyList(), emptyList(), emptyList())

            if (results.size == 1) {
                FileFindings(
                    filePath = file.path,
                    bugs = results[0].bugs,
                    styleIssues = results[0].styleIssues,
                    securityIssues = results[0].securityIssues,
                )
            } else {
                val allBugs     = results.flatMap { it.bugs }
                val allStyle    = results.flatMap { it.styleIssues }
                val allSecurity = results.flatMap { it.securityIssues }
                val mergePrompt = """
                    Merge and deduplicate findings from ${results.size} chunks of "${file.path}".
                    Keep the most descriptive version when issues overlap.
                    Bugs: ${allBugs.joinToString("\n") { "- $it" }}
                    Style: ${allStyle.joinToString("\n") { "- $it" }}
                    Security: ${allSecurity.joinToString("\n") { "- $it" }}
                """.trimIndent()
                printStep("  REDUCE 1  merge ${results.size} chunks → ${file.path}")
                val merged = timed("LLM reduce-1 ${file.path}") {
                    llm.createObject(mergePrompt, ChunkFindings::class.java)
                }
                FileFindings(
                    filePath = file.path,
                    bugs = merged.bugs,
                    styleIssues = merged.styleIssues,
                    securityIssues = merged.securityIssues,
                )
            }
        }

        val elapsed = (System.currentTimeMillis() - analysisStart) / 1000
        println("\n>>> mapReduceAnalysis total: ${elapsed / 60}m ${elapsed % 60}s\n")

        // Skip REDUCE-2 here — generateReview merges + produces the report in one LLM call
        return ProjectFindings(
            language = sourceFiles.language,
            fileFindings = fileFindings,
            bugs = fileFindings.flatMap { it.bugs },
            styleIssues = fileFindings.flatMap { it.styleIssues },
            securityIssues = fileFindings.flatMap { it.securityIssues },
        )
    }

    /** Build a single work unit from a batch of small files. */
    private fun buildBatchUnit(
        batch: List<SourceFile>,
        lang: String,
    ): WorkUnit {
        val filesBlock = batch.joinToString("\n\n") { f ->
            "// FILE: ${f.path}\n```$lang\n${f.content}\n```"
        }
        return WorkUnit(
            label = "batch[${batch.size} files: ${batch.joinToString(", ") { it.path.substringAfterLast('/').substringAfterLast('\\') }}]",
            filePaths = batch.map { it.path },
            promptBody = """
                Review these $lang files for bugs, style issues, and security vulnerabilities. Be concise.
                $filesBlock
            """.trimIndent(),
        )
    }

    @AchievesGoal(description = "Produce a complete, structured code review report")
    @Action(description = "Deduplicate cross-file findings and generate the final code review report")
    fun generateReview(findings: ProjectFindings, context: OperationContext): CodeReview {
        val perFileBlock = findings.fileFindings.joinToString("\n") { ff ->
            "FILE ${ff.filePath}: bugs=${ff.bugs.size}, style=${ff.styleIssues.size}, security=${ff.securityIssues.size}" +
                ff.bugs.joinToString("") { "\n  BUG [${it.severity}] ${it.lineReference}: ${it.description}" } +
                ff.styleIssues.joinToString("") { "\n  STYLE: ${it.description} → ${it.suggestion}" } +
                ff.securityIssues.joinToString("") { "\n  SEC [${it.severity}]: ${it.description} → ${it.recommendedFix}" }
        }
        val prompt = """
            You are a senior code reviewer. Produce a final CodeReview for this ${findings.language} project (${findings.fileFindings.size} files).
            Deduplicate cross-file issues (same issue in multiple files → single finding).
            Per-file findings:
            $perFileBlock
            Produce: summary, bugs, styleIssues, securityIssues, overallScore (0–10), recommendations (top 3–5).
        """.trimIndent()
        printStep("STEP 3/3  generateReview (merged with cross-file dedup)")
        val result = timed("generateReview") {
            context.ai().withDefaultLlm().createObject(prompt, CodeReview::class.java)
        }
        val totalSecs = (System.currentTimeMillis() - reviewStartMs) / 1000
        println("\n" + "=".repeat(80))
        println(">>> TOTAL code review time: ${totalSecs / 60}m ${totalSecs % 60}s")
        println("=".repeat(80) + "\n")
        return result
    }
}
