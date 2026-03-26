package dev.stevennguyen.newbieclaw.agent

import dev.stevennguyen.newbieclaw.service.OcrService
import dev.stevennguyen.newbieclaw.service.ZoneDetectionEngine
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class InvoiceExtractionIntegrationTest {

    @Autowired
    private lateinit var ocrService: OcrService

    @Autowired
    private lateinit var zoneDetectionEngine: ZoneDetectionEngine

    @Test
    fun `should extract all fields correctly from delivery receipt PDF`() {
        // Given
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        assertTrue(pdfFile.exists(), "Test PDF should exist at $pdfPath")

        val document = Loader.loadPDF(pdfFile)

        try {
            // When
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)

            // Then
            assertNotNull(zoneResult, "Zone result should not be null")

            // Verify customer number
            val customerNumber = zoneResult.getZoneValue("customer-number")
            assertEquals("0101431", customerNumber, "Customer number should be extracted correctly")
            assertTrue(
                zoneResult.getZoneConfidence("customer-number") > 0.3f,
                "Customer number confidence should be > 0.3, got ${zoneResult.getZoneConfidence("customer-number")}"
            )

            // Verify invoice number
            val invoiceNumber = zoneResult.getZoneValue("order-number")
            assertEquals("01401825", invoiceNumber, "Invoice number should be extracted correctly")
            assertTrue(
                zoneResult.getZoneConfidence("order-number") > 0.8f,
                "Invoice number confidence should be > 0.8, got ${zoneResult.getZoneConfidence("order-number")}"
            )

            // Verify first line number
            val lineNumber = zoneResult.getZoneValue("line-number")
            assertEquals("0001", lineNumber, "First line number should be extracted correctly")
            assertTrue(
                zoneResult.getZoneConfidence("line-number") > 0.3f,
                "Line number confidence should be > 0.3, got ${zoneResult.getZoneConfidence("line-number")}"
            )

            // Verify overall confidence
            assertTrue(
                zoneResult.overallConfidence > 0.5f,
                "Overall confidence should be > 0.5, got ${zoneResult.overallConfidence}"
            )

            println("\n✅ Integration Test Results:")
            println("   Customer Number: $customerNumber (confidence: ${zoneResult.getZoneConfidence("customer-number")})")
            println("   Invoice Number: $invoiceNumber (confidence: ${zoneResult.getZoneConfidence("order-number")})")
            println("   Line Number: $lineNumber (confidence: ${zoneResult.getZoneConfidence("line-number")})")
            println("   Overall Confidence: ${zoneResult.overallConfidence}")
        } finally {
            document.close()
        }
    }

    @Test
    fun `should extract multiple words from PDF with PSM mode 11`() {
        // Given
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        val document = Loader.loadPDF(pdfFile)

        try {
            // When
            val ocrWords = ocrService.extractOcrWords(document)

            // Then
            assertTrue(
                ocrWords.size > 100,
                "Should extract more than 100 words (not 1 giant word), got ${ocrWords.size} words"
            )

            // Verify key words are present
            val wordTexts = ocrWords.map { it.text }
            assertTrue(
                wordTexts.any { it.contains("INVOICE", ignoreCase = true) },
                "Should detect INVOICE text"
            )
            assertTrue(
                wordTexts.any { it.contains("01401825") },
                "Should detect invoice number 01401825"
            )
            assertTrue(
                wordTexts.any { it.contains("431") },
                "Should detect part of customer number (431)"
            )

            println("\n✅ OCR Word Extraction Test:")
            println("   Total words extracted: ${ocrWords.size}")
            println("   Contains INVOICE: ${wordTexts.any { it.contains("INVOICE", ignoreCase = true) }}")
            println("   Contains 01401825: ${wordTexts.any { it.contains("01401825") }}")
            println("   Contains customer number: ${wordTexts.any { it.contains("0101431") || it.contains("91471431") }}")
            println("   Contains 0001: ${wordTexts.any { it.contains("0001") }}")
        } finally {
            document.close()
        }
    }

    @Test
    fun `should detect customer number zone with fallback`() {
        // Given
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        val document = Loader.loadPDF(pdfFile)

        try {
            // When
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)

            // Then
            val customerNumber = zoneResult.getZoneValue("customer-number")
            assertNotNull(customerNumber, "Customer number should be detected")
            assertEquals("0101431", customerNumber, "Customer number should be 0101431")

            // Should not extract wrong values like "PP pA ||" or "60857"
            assertNotEquals("PP pA ||", customerNumber, "Should not extract invalid text")
            assertNotEquals("60857", customerNumber, "Should not extract order number as customer number")

            println("\n✅ Customer Number Zone Test:")
            println("   Extracted: $customerNumber")
            println("   Confidence: ${zoneResult.getZoneConfidence("customer-number")}")
        } finally {
            document.close()
        }
    }

    @Test
    fun `should detect invoice number zone`() {
        // Given
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        val document = Loader.loadPDF(pdfFile)

        try {
            // When
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)

            // Then
            val invoiceNumber = zoneResult.getZoneValue("order-number")
            assertNotNull(invoiceNumber, "Invoice number should be detected")
            assertEquals("01401825", invoiceNumber, "Invoice number should be 01401825")

            // Should have high confidence
            assertTrue(
                zoneResult.getZoneConfidence("order-number") > 0.8f,
                "Invoice number should have high confidence (>0.8), got ${zoneResult.getZoneConfidence("order-number")}"
            )

            println("\n✅ Invoice Number Zone Test:")
            println("   Extracted: $invoiceNumber")
            println("   Confidence: ${zoneResult.getZoneConfidence("order-number")}")
        } finally {
            document.close()
        }
    }

    @Test
    fun `should detect line number zone with fallback`() {
        // Given
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        val document = Loader.loadPDF(pdfFile)

        try {
            // When
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)

            // Then
            val lineNumber = zoneResult.getZoneValue("line-number")
            assertNotNull(lineNumber, "Line number should be detected")
            assertEquals("0001", lineNumber, "Should preserve leading zeros")

            // Should not extract "1" without leading zeros
            assertNotEquals("1", lineNumber, "Should not drop leading zeros")

            println("\n✅ Line Number Zone Test:")
            println("   Extracted: $lineNumber")
            println("   Confidence: ${zoneResult.getZoneConfidence("line-number")}")
        } finally {
            document.close()
        }
    }

    @Test
    fun `should reject invalid customer number values`() {
        // Given
        val pdfPath = "src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf"
        val pdfFile = File(pdfPath)
        val document = Loader.loadPDF(pdfFile)

        try {
            // When
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            val customerNumber = zoneResult.getZoneValue("customer-number")

            // Then
            assertNotNull(customerNumber, "Customer number should be extracted")

            // Should be numeric only
            assertTrue(
                customerNumber!!.matches(Regex("\\d+")),
                "Customer number should be numeric only, got: $customerNumber"
            )

            // Should not contain letters
            assertFalse(
                customerNumber.contains(Regex("[A-Za-z]")),
                "Customer number should not contain letters"
            )

            // Should be 7-8 digits
            assertTrue(
                customerNumber.length in 7..8,
                "Customer number should be 7-8 digits, got ${customerNumber.length} digits"
            )

            println("\n✅ Validation Test:")
            println("   Customer Number: $customerNumber")
            println("   Is numeric: ${customerNumber.matches(Regex("\\d+"))}")
            println("   Length: ${customerNumber.length} digits")
        } finally {
            document.close()
        }
    }
}
