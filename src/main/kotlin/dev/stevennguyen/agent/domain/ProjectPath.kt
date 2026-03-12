package dev.stevennguyen.agent.domain

/**
 * Project path input — agent reads source files from disk.
 * [path] can be a single file or a directory.
 * [language] is used to filter by file extension and guide the LLM prompts.
 */
data class ProjectPath(
    val path: String,
    val language: String,
)
