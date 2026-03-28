package dev.stevennguyen.newbieclaw.agent

import dev.stevennguyen.newbieclaw.service.OcrService
import dev.stevennguyen.newbieclaw.service.ZoneDetectionEngine
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class DiagnosticTest {

    @Autowired
    private lateinit var ocrService: OcrService

    @Autowired
    private lateinit var zoneDetectionEngine: ZoneDetectionEngine

    @Test
    fun `diagnostic - show what is being extracted`() {
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        val document = Loader.loadPDF(pdfFile)

        try {
            println("\n" + "=".repeat(80))
            println("DIAGNOSTIC TEST - Zone Extraction Analysis")
            println("=".repeat(80))
            
            val ocrWords = ocrService.extractOcrWords(document)
            println("\nTotal OCR words: ${ocrWords.size}")
            
            println("\nSearching for key numbers:")
            val customerNumbers = ocrWords.filter { it.text.matches(Regex("\\d{7,8}")) }
            println("  7-8 digit numbers found: ${customerNumbers.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            val lineNumbers = ocrWords.filter { it.text.matches(Regex("0\\d{3}")) }
            println("  4-digit numbers starting with 0: ${lineNumbers.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("\nSearching for words containing target numbers:")
            val wordsWithCustomerNum = ocrWords.filter { it.text.contains("0101431") || it.text.contains("91471431") }
            println("  Words containing 0101431 or 91471431: ${wordsWithCustomerNum.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            val wordsWithLineNum = ocrWords.filter { it.text.contains("0001") }
            println("  Words containing 0001: ${wordsWithLineNum.map { "${it.text} at (${it.x}, ${it.y})" }}")
            
            println("\nAll words in left section (x < 1500):")
            val leftWords = ocrWords.filter { it.x < 1500 }.sortedBy { it.y }.take(50)
            leftWords.forEach { println("  '${it.text}' at (${it.x}, ${it.y})") }
            
            println("\nExtracting full OCR text for regex fallback...")
            val fullText = ocrService.extractFullText(document)
            println("Full text length: ${fullText.length} characters")
            println("\nFirst 1000 characters of full text:")
            println(fullText.take(1000))
            
            println("\nSearching in full text with regex:")
            val allDigitSequences = Regex("\\d+").findAll(fullText).map { it.value }.toList()
            println("  All digit sequences: ${allDigitSequences.take(30)}")
            
            println("\nLooking for customer number components:")
            val has0101 = fullText.contains("0101")
            val has431 = fullText.contains("431")
            val has0101431 = fullText.contains("0101431")
            println("  Contains '0101': $has0101")
            println("  Contains '431': $has431")
            println("  Contains '0101431': $has0101431")
            
            if (has431) {
                val contextAround431 = Regex(".{0,50}431.{0,50}").findAll(fullText).map { it.value }.toList()
                println("  Context around '431': $contextAround431")
            }
            
            val customerNumMatches = Regex("\\b(\\d{7,8})\\b").findAll(fullText).map { it.value }.toList()
            println("  7-8 digit numbers in full text: $customerNumMatches")
            val lineNumMatches = Regex("\\b(0\\d{3})\\b").findAll(fullText).map { it.value }.toList()
            println("  4-digit numbers starting with 0 in full text: $lineNumMatches")
            
            println("\nRunning zone detection with regex fallback...")
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            println("\n" + "=".repeat(80))
            println("ZONE DETECTION RESULTS:")
            println("=".repeat(80))
            println("Customer Number: ${zoneResult.getZoneValue("customer-number")} (confidence: ${zoneResult.getZoneConfidence("customer-number")})")
            println("Invoice Number: ${zoneResult.getZoneValue("order-number")} (confidence: ${zoneResult.getZoneConfidence("order-number")})")
            println("Line Number: ${zoneResult.getZoneValue("line-number")} (confidence: ${zoneResult.getZoneConfidence("line-number")})")
            println("Overall Confidence: ${zoneResult.overallConfidence}")
            println("=".repeat(80))
        } finally {
            document.close()
        }
    }
}
