package dev.stevennguyen.newbieclaw.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import dev.stevennguyen.newbieclaw.domain.invoice.InvoiceQuery
import dev.stevennguyen.newbieclaw.domain.invoice.InvoiceAnswer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.rendering.PDFRenderer
import net.sourceforge.tess4j.Tesseract
import java.io.File
import java.awt.image.BufferedImage

@Agent(description = "Answers specific questions about invoice PDFs like 'what is the invoice number', 'what is the total', 'who is the vendor'. Use when user asks a specific question starting with 'what', 'who', 'when', etc.")
class InvoiceQueryAgent(private val props: InvoiceExtractionProperties) {

    @Action(description = "Parse user question and extract PDF path and the question being asked")
    fun parseQuery(userInput: UserInput, context: OperationContext): InvoiceQuery {
        println("\n" + "=".repeat(80))
        println(">>> Parsing invoice query")
        println("=".repeat(80) + "\n")

        val text = userInput.content
        
        // Try regex extraction for path
        val pathRegex = Regex("""([A-Z]:\\[^\s"]+\.pdf|/[^\s"]+\.pdf)""", RegexOption.IGNORE_CASE)
        val pathMatch = pathRegex.find(text)?.groupValues?.get(1)

        if (pathMatch != null) {
            val file = File(pathMatch)
            require(file.exists()) { "PDF file not found: $pathMatch" }
            
            // Extract the question part (everything before "from")
            val questionPart = text.substringBefore("from", text).trim()
            val question = questionPart.replace(Regex("^(what is|what's|tell me|show me|get|find)\\s+", RegexOption.IGNORE_CASE), "").trim()
            
            println("    ✓ PDF: $pathMatch")
            println("    ✓ Question: $question")
            
            return InvoiceQuery(pdfPath = pathMatch, question = question)
        }

        // Fallback to LLM
        val prompt = """
            Parse this request and extract:
            1. The PDF file path
            2. The specific question being asked about the invoice
            
            Request: "${userInput.content}"
        """.trimIndent()
        
        return context.ai().withDefaultLlm().createObject(prompt, InvoiceQuery::class.java)
    }

    @Action(description = "Extract text from the PDF file")
    fun extractText(query: InvoiceQuery): String {
        println("\n" + "=".repeat(80))
        println(">>> Extracting text from PDF")
        println("=".repeat(80) + "\n")

        val file = File(query.pdfPath)
        val document = Loader.loadPDF(file)
        
        try {
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(document)
            
            if (text.isBlank() || text.trim().length < 10) {
                println("    ⚠️  Using OCR for scanned PDF...")
                return extractTextUsingOCR(document)
            }
            
            println("    ✓ Extracted ${text.length} characters")
            return text
        } finally {
            document.close()
        }
    }
    
    private fun extractTextUsingOCR(document: org.apache.pdfbox.pdmodel.PDDocument): String {
        val tesseract = Tesseract()
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata")
        tesseract.setLanguage("eng")
        
        val renderer = PDFRenderer(document)
        val allText = StringBuilder()
        
        for (pageIndex in 0 until document.numberOfPages) {
            val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, 300f)
            val pageText = tesseract.doOCR(image)
            allText.append(pageText).append("\n\n")
        }
        
        val finalText = allText.toString().trim()
        println("    ✓ OCR extracted ${finalText.length} characters")
        return finalText
    }

    @AchievesGoal(description = "Answer the specific question about the invoice")
    @Action(description = "Use LLM to answer the question based on extracted text")
    fun answerQuestion(extractedText: String, query: InvoiceQuery, context: OperationContext): String {
        println("\n" + "=".repeat(80))
        println(">>> Answering question")
        println("=".repeat(80) + "\n")

        val prompt = """
            You are analyzing an invoice. Answer this specific question concisely and directly.
            
            Question: ${query.question}
            
            Invoice text:
            ```
            $extractedText
            ```
            
            Provide a direct, concise answer. If the information is not found, say "Not found in the invoice."
        """.trimIndent()

        val result = context.ai().withDefaultLlm().createObject(prompt, InvoiceAnswer::class.java)
        
        println("\n" + "=".repeat(80))
        println(">>> ANSWER:")
        println("=".repeat(80))
        println(result.answer)
        println("=".repeat(80) + "\n")
        
        return result.answer
    }
}
