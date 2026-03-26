package dev.stevennguyen.newbieclaw.agent

import dev.stevennguyen.newbieclaw.service.OcrService
import dev.stevennguyen.newbieclaw.service.ZoneDetectionEngine
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class Problem0103164DiagnosticTest {

    @Autowired
    private lateinit var ocrService: OcrService

    @Autowired
    private lateinit var zoneDetectionEngine: ZoneDetectionEngine

    @Test
    fun `analyze problem PDF 0103164 OCR extraction`() {
        val pdfPath = "src/test/resources/0103164_01402175_0001_DELIVERYRECEIPT.pdf"
        val file = File(pdfPath)
        val document = Loader.loadPDF(file)

        try {
            println("\n" + "=".repeat(80))
            println("PROBLEM PDF 0103164 - Zone Extraction Analysis")
            println("Expected: customerNumber=0103164, invoiceNumber=01402175, lineNumber=0001")
            println("Actual: customerNumber=01462175, invoiceNumber=2739/20, lineNumber=null")
            println("=".repeat(80))
            
            val ocrWords = ocrService.extractOcrWords(document)
            
            println("\nTotal OCR words: ${ocrWords.size}")
            
            println("\nSearching for expected numbers:")
            println("  Looking for customer number 0103164:")
            val wordsWithCustomer = ocrWords.filter { it.text.contains("0103164") || it.text.contains("103164") }
            println("    Words containing 0103164: ${wordsWithCustomer.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("  Looking for invoice number 01402175:")
            val wordsWithInvoice = ocrWords.filter { it.text.contains("01402175") || it.text.contains("1402175") }
            println("    Words containing 01402175: ${wordsWithInvoice.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("  Looking for line number 0001:")
            val wordsWithLine = ocrWords.filter { it.text.contains("0001") }
            println("    Words containing 0001: ${wordsWithLine.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("\nSearching for actual (wrong) numbers:")
            println("  Looking for 01462175:")
            val wordsWithWrong1 = ocrWords.filter { it.text.contains("01462175") || it.text.contains("1462175") }
            println("    Words containing 01462175: ${wordsWithWrong1.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("  Looking for 2739/20:")
            val wordsWithWrong2 = ocrWords.filter { it.text.contains("2739") || it.text.contains("20") }
            println("    Words containing 2739 or 20: ${wordsWithWrong2.take(10).map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("\nAll 7-8 digit numbers found:")
            val sevenEightDigits = ocrWords.filter { it.text.matches(Regex("\\d{7,8}")) }
            sevenEightDigits.forEach { println("  '${it.text}' at (${it.x}, ${it.y})") }
            
            println("\nAll words in left section (x < 1500):")
            val leftWords = ocrWords.filter { it.x < 1500 }.sortedBy { it.y }.take(60)
            leftWords.forEach { println("  '${it.text}' at (${it.x}, ${it.y})") }
            
            println("\nExtracting full OCR text for regex analysis...")
            val fullText = ocrService.extractFullText(document)
            println("Full text length: ${fullText.length} characters")
            
            println("\nSearching in full text:")
            println("  Contains '0103164': ${fullText.contains("0103164")}")
            println("  Contains '01402175': ${fullText.contains("01402175")}")
            println("  Contains '0001': ${fullText.contains("0001")}")
            println("  Contains '01462175': ${fullText.contains("01462175")}")
            println("  Contains '2739': ${fullText.contains("2739")}")
            
            val allDigitSequences = Regex("\\d+").findAll(fullText).map { it.value }.toList()
            println("\nAll digit sequences (first 40): ${allDigitSequences.take(40)}")
            
            val sevenDigits = Regex("\\b(\\d{7})\\b").findAll(fullText).map { it.value }.toList()
            println("All 7-digit numbers: $sevenDigits")
            
            val eightDigits = Regex("\\b(\\d{8})\\b").findAll(fullText).map { it.value }.toList()
            println("All 8-digit numbers: $eightDigits")
            
            println("\nRunning zone detection...")
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            println("\n" + "=".repeat(80))
            println("ZONE DETECTION RESULTS:")
            println("=".repeat(80))
            println("Customer Number: ${zoneResult.getZoneValue("customer-number")} (expected: 0103164)")
            println("Invoice Number: ${zoneResult.getZoneValue("order-number")} (expected: 01402175)")
            println("Line Number: ${zoneResult.getZoneValue("line-number")} (expected: 0001)")
            println("Overall Confidence: ${zoneResult.overallConfidence}")
            println("=".repeat(80))
        } finally {
            document.close()
        }
    }
}
