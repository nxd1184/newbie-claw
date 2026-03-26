package dev.stevennguyen.newbieclaw.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import dev.stevennguyen.newbieclaw.config.InvoiceExtractionProperties
import dev.stevennguyen.newbieclaw.config.ZoneExtractionConfig
import dev.stevennguyen.newbieclaw.domain.invoice.PdfPath
import dev.stevennguyen.newbieclaw.domain.invoice.InvoiceData
import dev.stevennguyen.newbieclaw.domain.invoice.InvoiceDataV1
import dev.stevennguyen.newbieclaw.service.OcrService
import dev.stevennguyen.newbieclaw.service.ZoneDetectionEngine
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.pdmodel.PDDocument
import net.sourceforge.tess4j.Tesseract
import java.io.File
import java.awt.image.BufferedImage

@Agent(description = "Extracts complete structured invoice data from PDF files. Use when user wants full invoice extraction with all fields.")
class InvoiceExtractionAgent(
    private val props: InvoiceExtractionProperties,
    private val zoneConfig: ZoneExtractionConfig,
    private val ocrService: OcrService,
    private val zoneDetectionEngine: ZoneDetectionEngine
) {

    private fun cleanExtractedText(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun extractRelevantSections(text: String): String {
        val keywords = listOf("customer", "order", "invoice", "line", "item", "qty", "quantity")
        val lines = text.lines()

        val relevant = lines.filter { line ->
            val lower = line.lowercase()
            keywords.any { lower.contains(it) }
        }

        return if (relevant.isNotEmpty()) relevant.joinToString("\n") else text
    }

    @Action(description = "Parse user input to extract PDF file path and validate it exists")
    fun parsePdfPath(userInput: UserInput, context: OperationContext): PdfPath {
        println("\n" + "=".repeat(80))
        println(">>> STEP 1/2: Parsing PDF path")
        println("=".repeat(80) + "\n")

        val text = userInput.content
        
        // Try regex extraction first for common path patterns
        val pathRegex = Regex("""([A-Z]:\\[^\s"]+\.pdf|/[^\s"]+\.pdf|[^\s"]+\.pdf)""", RegexOption.IGNORE_CASE)
        val pathMatch = pathRegex.find(text)?.groupValues?.get(1)

        if (pathMatch != null) {
            val file = File(pathMatch)
            require(file.exists()) { "PDF file not found: $pathMatch" }
            require(file.extension.lowercase() == "pdf") { "File must be a PDF: $pathMatch" }
            require(file.length() <= props.maxPdfSizeBytes) { 
                "PDF file too large: ${file.length()} bytes (max: ${props.maxPdfSizeBytes})" 
            }
            println("    ✓ Found PDF: $pathMatch (${file.length()} bytes)")
            return PdfPath(path = pathMatch)
        }

        // Fallback to LLM if regex fails
        val prompt = """
            Extract the PDF file path from this request:
            "${userInput.content}"
            Return only the file system path to the PDF file.
        """.trimIndent()
        
        val result = context.ai().withDefaultLlm().createObject(prompt, PdfPath::class.java)
        val file = File(result.path)
        require(file.exists()) { "PDF file not found: ${result.path}" }
        require(file.extension.lowercase() == "pdf") { "File must be a PDF: ${result.path}" }
        require(file.length() <= props.maxPdfSizeBytes) { 
            "PDF file too large: ${file.length()} bytes (max: ${props.maxPdfSizeBytes})" 
        }
        
        println("    ✓ Found PDF: ${result.path} (${file.length()} bytes)")
        return result
    }

    @Action(description = "Extract text content from the PDF file using Apache PDFBox or OCR")
    fun extractTextFromPdf(pdfPath: PdfPath): String {
        println("\n" + "=".repeat(80))
        println(">>> STEP 2/3: Extracting text from PDF")
        println("=".repeat(80) + "\n")

        val file = File(pdfPath.path)
        val document = Loader.loadPDF(file)
        
        try {
            val pageCount = document.numberOfPages
            println("    📄 PDF has $pageCount page(s)")
            
            // Try standard text extraction first
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val text = stripper.getText(document)
            
            println("    ✓ Extracted ${text.length} characters using standard extraction")
            
            if (text.isBlank() || text.trim().length < 10) {
                println("\n    ⚠️  Minimal or no text extracted - attempting OCR...")
                
                // Use OCR for scanned PDFs
                val ocrText = extractTextUsingOCR(document, pageCount)
                
                println("\n" + "=".repeat(80))
                println(">>> EXTRACTED PDF CONTENT (via OCR):")
                println("=".repeat(80))
                println(ocrText)
                println("=".repeat(80))
                println()
                
                return ocrText
            }
            
            println("\n" + "=".repeat(80))
            println(">>> EXTRACTED PDF CONTENT:")
            println("=".repeat(80))
            println(text)
            println("=".repeat(80))
            println()

            val cleanedText = cleanExtractedText(text)
            val focusedText = extractRelevantSections(cleanedText)
            return focusedText
        } catch (e: Exception) {
            println("\n    ❌ Error extracting text from PDF: ${e.message}")
            throw e
        } finally {
            document.close()
        }
    }
    
    private fun extractTextUsingOCR(document: org.apache.pdfbox.pdmodel.PDDocument, pageCount: Int): String {
        println("    🔍 Starting OCR extraction...")
        
        val tesseract = Tesseract()
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata")
        tesseract.setLanguage("eng")
        
        val renderer = PDFRenderer(document)
        val allText = StringBuilder()
        
        for (pageIndex in 0 until pageCount) {
            println("    📄 Processing page ${pageIndex + 1}/$pageCount with OCR...")
            
            try {
                val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, 300f)
                val pageText = tesseract.doOCR(image)
                allText.append(pageText).append("\n\n")
                
                println("    ✓ Page ${pageIndex + 1}: Extracted ${pageText.length} characters")
            } catch (e: Exception) {
                println("    ⚠️  Error on page ${pageIndex + 1}: ${e.message}")
            }
        }
        
        val finalText = allText.toString().trim()
        
        if (finalText.isEmpty()) {
            throw IllegalStateException(
                "OCR failed to extract any text. Please ensure Tesseract is installed at C:/Program Files/Tesseract-OCR/"
            )
        }
        
        println("    ✓ OCR completed: ${finalText.length} total characters extracted")
        return finalText
    }

//    @AchievesGoal(description = "Extract structured invoice data from the PDF text using LLM")
//    @Action(description = "Analyze extracted text and identify invoice fields to produce structured InvoiceData")
//    fun extractInvoiceData(extractedText: String, context: OperationContext): InvoiceData {
//        println("\n" + "=".repeat(80))
//        println(">>> STEP 3/3: Extracting structured invoice data")
//        println("=".repeat(80) + "\n")
//
//        val prompt = """
//            You are an expert invoice data extractor. Analyze the following text extracted from an invoice/delivery receipt PDF
//            and extract all relevant information into a structured format.
//
//            Extract the following information:
//
//            VENDOR INFORMATION:
//            - name, address, phone, fax/email, tax ID
//
//            CUSTOMER INFORMATION:
//            - name, billToAddress, shipToAddress, phone
//
//            INVOICE METADATA:
//            - invoiceNumber (document number)
//            - invoiceDate
//            - dueDate (if specified)
//            - currency (default "USD" if not specified)
//            - paymentTerms (e.g., "NET 30 DAYS")
//            - documentType (e.g., "DELIVERY RECEIPT", "SALES ORDER", "INVOICE")
//            - salesRepresentative (inside salesperson name)
//            - customerOrderNumber (if available)
//
//            LINE ITEMS (for each item):
//            - itemNumber (item/part number)
//            - description (product description)
//            - quantity (numeric value)
//            - unit (e.g., "EA", "LBS", "PC")
//            - unitPrice (price per unit)
//            - extendedQuantity (additional quantity info like "12 pieces")
//            - total (line total amount)
//            - materialSpecs (chemical composition or material specifications if present)
//
//            SHIPPING INFORMATION:
//            - freightMethod (e.g., "OUR TRUCK", "UPS", "FEDEX")
//            - route (delivery route code)
//            - shipDate (if different from invoice date)
//            - shipToAddress (if different from customer address)
//
//            FINANCIAL AMOUNTS:
//            - subtotal (sum before tax)
//            - taxAmount (tax charged)
//            - taxRate (tax percentage if shown)
//            - discount (any discounts applied)
//            - total (final total amount)
//
//            NOTES:
//            - Any special instructions, terms, or additional information
//
//            Be precise with numbers and dates. If a field is not found, use null.
//            Extract chemical composition data if present in the invoice.
//
//            Invoice Text:
//            ```
//            $extractedText
//            ```
//        """.trimIndent()
//
//        println("    ⏱  Sending to LLM for structured extraction...")
//        val invoiceData = context.ai().withDefaultLlm().createObject(prompt, InvoiceData::class.java)
//
//        println("\n    ✓ Invoice data extracted successfully:")
//        println("      - Document Type: ${invoiceData.metadata.documentType ?: "Invoice"}")
//        println("      - Invoice #: ${invoiceData.metadata.invoiceNumber}")
//        println("      - Date: ${invoiceData.metadata.invoiceDate}")
//        println("      - Vendor: ${invoiceData.vendor.name}")
//        println("      - Customer: ${invoiceData.customer.name}")
//        println("      - Sales Rep: ${invoiceData.metadata.salesRepresentative ?: "N/A"}")
//        println("      - Line Items: ${invoiceData.lineItems.size}")
//        println("      - Total: ${invoiceData.amounts.total} ${invoiceData.metadata.currency}")
//
//        return invoiceData
//    }

    @AchievesGoal(description = "Extract minimal invoice fields from the PDF using zone-based extraction with LLM fallback")
    @Action(description = "Analyze PDF and identify customer number, invoice number, and first line number using zones")
    fun extractInvoiceDataV1(pdfPath: PdfPath, context: OperationContext): InvoiceDataV1 {
        println("\n" + "=".repeat(80))
        println(">>> STEP 2/2: Extracting minimal invoice data using zone-based extraction")
        println("=".repeat(80) + "\n")

        val file = File(pdfPath.path)
        val document = Loader.loadPDF(file)
        
        try {
            val ocrWords = ocrService.extractOcrWords(document)
            
            if (ocrWords.isEmpty()) {
                println("    ⚠️  No OCR words extracted, falling back to text-based LLM extraction...")
                return fallbackToLlmExtraction(pdfPath, context)
            }
            
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            if (zoneResult.isHighConfidence()) {
                println("\n    ✅ High confidence zone extraction (${zoneResult.overallConfidence})")
                return buildInvoiceDataFromZones(zoneResult)
            } else if (zoneResult.isMediumConfidence()) {
                println("\n    ⚠️  Medium confidence zone extraction (${zoneResult.overallConfidence})")
                println("    🔄 Validating with LLM...")
                
                val zoneBasedData = buildInvoiceDataFromZones(zoneResult)
                val llmData = fallbackToLlmExtraction(pdfPath, context)
                
                return mergeResults(zoneBasedData, llmData, zoneResult.overallConfidence)
            } else {
                println("\n    ❌ Low confidence zone extraction (${zoneResult.overallConfidence})")
                println("    🔄 Falling back to LLM extraction...")
                return fallbackToLlmExtraction(pdfPath, context)
            }
        } catch (e: Exception) {
            println("\n    ❌ Error in zone extraction: ${e.message}")
            println("    🔄 Falling back to LLM extraction...")
            return fallbackToLlmExtraction(pdfPath, context)
        } finally {
            document.close()
        }
    }
    
    private fun buildInvoiceDataFromZones(zoneResult: dev.stevennguyen.newbieclaw.domain.invoice.ZoneExtractionResult): InvoiceDataV1 {
        val customerNumber = zoneResult.getZoneValue("customer-number")
        val invoiceNumber = zoneResult.getZoneValue("order-number")
        val firstLineNumber = zoneResult.getZoneValue("line-number")
        
        println("\n    📋 Zone-based extraction results:")
        println("      - Customer #: $customerNumber (confidence: ${zoneResult.getZoneConfidence("customer-number")})")
        println("      - Invoice #: $invoiceNumber (confidence: ${zoneResult.getZoneConfidence("order-number")})")
        println("      - First Line #: $firstLineNumber (confidence: ${zoneResult.getZoneConfidence("line-number")})")
        
        return InvoiceDataV1(
            customerNumber = customerNumber,
            invoiceNumber = invoiceNumber,
            firstLineNumber = firstLineNumber
        )
    }
    
    private fun fallbackToLlmExtraction(pdfPath: PdfPath, context: OperationContext): InvoiceDataV1 {
        val extractedText = extractTextFromPdf(pdfPath)
        
        val prompt = """
        You are an expert invoice data extractor.
        
        From the following invoice or delivery receipt text, extract only these fields:

        - customerNumber
        - invoiceNumber
        - firstLineNumber

        Rules:
        - Return only these 3 fields.
        - If a field is not found, use null.
        - Extract numeric values only.
        - Do not guess.
        - firstLineNumber means the first line item number appearing in the line-item section of the document.

        Invoice Text:
        ```
        $extractedText
        ```
    """.trimIndent()

        println("    ⏱  Sending to LLM for extraction...")
        val invoiceData = context.ai()
            .withDefaultLlm()
            .createObject(prompt, InvoiceDataV1::class.java)

        println("\n    ✓ LLM extraction completed:")
        println("      - Customer #: ${invoiceData.customerNumber}")
        println("      - Invoice #: ${invoiceData.invoiceNumber}")
        println("      - First Line #: ${invoiceData.firstLineNumber}")

        return invoiceData
    }
    
    private fun mergeResults(zoneData: InvoiceDataV1, llmData: InvoiceDataV1, zoneConfidence: Float): InvoiceDataV1 {
        println("\n    🔀 Merging zone-based and LLM results...")
        
        val customerNumber = if (!zoneData.customerNumber.isNullOrBlank()) zoneData.customerNumber else llmData.customerNumber
        val invoiceNumber = if (!zoneData.invoiceNumber.isNullOrBlank()) zoneData.invoiceNumber else llmData.invoiceNumber
        val firstLineNumber = if (!zoneData.firstLineNumber.isNullOrBlank()) zoneData.firstLineNumber else llmData.firstLineNumber
        
        println("    ✓ Merged results:")
        println("      - Customer #: $customerNumber")
        println("      - Invoice #: $invoiceNumber")
        println("      - First Line #: $firstLineNumber")
        
        return InvoiceDataV1(
            customerNumber = customerNumber,
            invoiceNumber = invoiceNumber,
            firstLineNumber = firstLineNumber
        )
    }
}
