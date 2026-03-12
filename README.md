# NewbieClaw

A Kotlin/Spring Boot multi-agent application powered by the [Embabel](https://embabel.com) agent framework. NewbieClaw provides intelligent automation for code review and invoice data extraction, using LLM-powered agents to analyze code quality and extract structured information from PDF documents.

## Features

### Code Review Agent
Performs automated code reviews using a map-reduce strategy:
1. **Parse** — Extracts the project path and language from the user's shell input
2. **Map** — Reads source files, batches small files together, chunks large files, and runs all work units in parallel
3. **Reduce + Report** — Deduplicates cross-file findings and generates a final `CodeReview` with summary, scored issues, and recommendations

### Invoice Extraction Agents
- **Full Extraction**: Extracts complete structured invoice data (vendor, customer, line items, amounts, shipping info)
- **Query Mode**: Answers specific questions about invoices ("what is the invoice number?", "who is the vendor?", etc.)
- **OCR Support**: Handles scanned/image-based PDFs using Tesseract OCR

## Requirements

- JDK 21 (set via `gradle.properties` — JDK 25 is incompatible with Kotlin 2.1.0)
- At least one LLM provider configured (see below)

## Getting Started

```bash
./gradlew bootRun
```

The app starts an interactive Spring Shell. Type your review request at the prompt:

```
review /path/to/project as java
review C:/Users/me/my-app as kotlin
```

Or pipe input for non-interactive use:

```bash
echo "review /path/to/project as java" | ./gradlew bootRun --quiet
```

> Use forward slashes in paths when piping (backslashes are stripped by the shell).

## LLM Providers

All four providers are loaded at startup. Switch between them by changing `embabel.models.default-llm` in `application.yml` or by setting the `LOCAL_LLM_MODEL` environment variable.

| Provider  | Example models                                   | Env var required      |
|-----------|--------------------------------------------------|-----------------------|
| LM Studio | `qwen/qwen3-coder-next`, `openai/gpt-oss-120b`  | `LMSTUDIO_BASE_URL`   |
| OpenAI    | `gpt-4o-mini`, `gpt-4.1`                        | `OPENAI_API_KEY`      |
| Google    | `gemini-2.0-flash`, `gemini-2.5-flash`          | `GOOGLE_API_KEY`      |
| Anthropic | `claude-haiku-4-5`, `claude-sonnet-4-5`         | `ANTHROPIC_API_KEY`   |

The default is LM Studio (`qwen/qwen3-coder-next`) pointing to `http://192.168.1.147:1234`. Change `lmstudio.base-url` in `application.yml` if your server is on a different host.

## Configuration

All tunables are in `src/main/resources/application.yml` under the `code-review:` prefix:

| Property              | Default    | Description                                           |
|-----------------------|------------|-------------------------------------------------------|
| `chunk-size`          | `40000`    | Max characters per LLM call (larger = fewer calls)    |
| `max-files`           | `50`       | Max source files ingested per run                     |
| `max-file-size-bytes` | `50000`    | Files larger than this are skipped                    |
| `concurrency`         | `4`        | Max parallel LLM requests during the MAP phase        |
| `batch-threshold`     | `20000`    | Files smaller than this (chars) are batched together  |

LLM timeout and retry behaviour:

```yaml
embabel:
  agent:
    platform:
      llm-operations:
        prompts:
          default-timeout: 300s   # 5 minutes — needed for slow local models
        data-binding:
          max-attempts: 2
          fixed-backoff-millis: 2000
```

## Project Structure

```
src/main/kotlin/dev/stevennguyen/newbieclaw/
├── NewbieClawApplication.kt           # Spring Boot entry point
├── agent/
│   ├── CodeReviewAgent.kt             # Code review agent (parse → map → reduce)
│   ├── CodeReviewProperties.kt        # @ConfigurationProperties for code-review.*
│   ├── InvoiceExtractionAgent.kt      # Full invoice extraction agent
│   ├── InvoiceQueryAgent.kt           # Invoice question-answering agent
│   └── InvoiceExtractionProperties.kt # @ConfigurationProperties for invoice-extraction.*
└── domain/
    ├── BugIssue.kt
    ├── ChunkFindings.kt
    ├── CodeReview.kt                  # Final review output
    ├── FileFindings.kt
    ├── ProjectFindings.kt
    ├── ProjectPath.kt
    ├── SecurityIssue.kt
    ├── Severity.kt
    ├── SourceFile.kt
    ├── SourceFiles.kt
    ├── StyleIssue.kt
    └── invoice/                       # Invoice domain models
        ├── CustomerInfo.kt
        ├── InvoiceAmounts.kt
        ├── InvoiceAnswer.kt
        ├── InvoiceData.kt
        ├── InvoiceMetadata.kt
        ├── InvoiceQuery.kt
        ├── LineItem.kt
        ├── PdfPath.kt
        ├── ShippingInfo.kt
        └── VendorInfo.kt
```

## Tech Stack

- **Kotlin 2.1.0** + **Spring Boot 3.4.5**
- **Embabel 0.3.4** — agent framework (shell, LM Studio, OpenAI, Google GenAI, Anthropic starters)
- **Kotlin Coroutines 1.9.0** — parallel MAP phase for code review
- **Apache PDFBox 3.0.3** — PDF text extraction
- **Tesseract OCR 5.13.0** — OCR for scanned PDFs
- **Java 21** toolchain
