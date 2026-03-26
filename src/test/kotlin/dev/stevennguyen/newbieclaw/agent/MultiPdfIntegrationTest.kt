package dev.stevennguyen.newbieclaw.agent

import dev.stevennguyen.newbieclaw.service.OcrService
import dev.stevennguyen.newbieclaw.service.ZoneDetectionEngine
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File

@SpringBootTest
class MultiPdfIntegrationTest {

    @Autowired
    private lateinit var ocrService: OcrService

    @Autowired
    private lateinit var zoneDetectionEngine: ZoneDetectionEngine

    @ParameterizedTest(name = "{0} - should extract all three fields correctly")
    @CsvSource(
        "DELIVERYRECEIPT, src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf, 0101431, 01401825, 0001",
        "WORKORDER, src/test/resources/0101431_01401825_0001_WORKORDER.pdf, 0101431, 01401825, 0001",
        "DELIVERYRECEIPT_0103164, src/test/resources/0103164_01402175_0001_DELIVERYRECEIPT.pdf, 0103164, 01402175, 0001"
    )
    fun `should extract customer number, invoice number, and line number from PDF`(
        pdfType: String,
        pdfPath: String,
        expectedCustomerNumber: String,
        expectedInvoiceNumber: String,
        expectedLineNumber: String
    ) {
        val file = File(pdfPath)
        assertTrue(file.exists(), "PDF file should exist: $pdfPath")
        
        val document = Loader.loadPDF(file)

        try {
            println("\n" + "=".repeat(80))
            println("Testing $pdfType PDF")
            println("=".repeat(80))
            
            val ocrWords = ocrService.extractOcrWords(document)
            println("✓ Extracted ${ocrWords.size} OCR words")
            
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            val customerNumber = zoneResult.getZoneValue("customer-number")
            val invoiceNumber = zoneResult.getZoneValue("order-number")
            val lineNumber = zoneResult.getZoneValue("line-number")
            
            println("\n📊 Extraction Results:")
            println("  Customer Number: $customerNumber (expected: $expectedCustomerNumber)")
            println("  Invoice Number: $invoiceNumber (expected: $expectedInvoiceNumber)")
            println("  Line Number: $lineNumber (expected: $expectedLineNumber)")
            println("  Overall Confidence: ${zoneResult.overallConfidence}")
            
            assertEquals(expectedCustomerNumber, customerNumber, 
                "Customer number should match for $pdfType")
            assertEquals(expectedInvoiceNumber, invoiceNumber, 
                "Invoice number should match for $pdfType")
            assertEquals(expectedLineNumber, lineNumber, 
                "Line number should match for $pdfType")
            
            println("✅ All fields extracted correctly for $pdfType PDF")
            println("=".repeat(80))
        } finally {
            document.close()
        }
    }

    @ParameterizedTest(name = "{0} - should extract correct number of OCR words")
    @CsvSource(
        "DELIVERYRECEIPT, src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf, 400",
        "WORKORDER, src/test/resources/0101431_01401825_0001_WORKORDER.pdf, 150",
        "DELIVERYRECEIPT_0103164, src/test/resources/0103164_01402175_0001_DELIVERYRECEIPT.pdf, 480"
    )
    fun `should extract multiple OCR words from PDF`(
        pdfType: String,
        pdfPath: String,
        minExpectedWords: Int
    ) {
        val file = File(pdfPath)
        val document = Loader.loadPDF(file)

        try {
            val ocrWords = ocrService.extractOcrWords(document)
            
            println("\n📝 OCR Word Count for $pdfType:")
            println("  Total words: ${ocrWords.size}")
            println("  Minimum expected: $minExpectedWords")
            
            assertTrue(
                ocrWords.size >= minExpectedWords,
                "$pdfType should extract at least $minExpectedWords words, got ${ocrWords.size}"
            )
            
            val wordTexts = ocrWords.map { it.text }
            assertTrue(
                wordTexts.any { it.contains("INVOICE", ignoreCase = true) },
                "$pdfType should detect INVOICE text"
            )
            
            println("✅ OCR word extraction successful for $pdfType")
        } finally {
            document.close()
        }
    }

    @ParameterizedTest(name = "{0} - should validate customer number format")
    @CsvSource(
        "DELIVERYRECEIPT, src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf",
        "WORKORDER, src/test/resources/0101431_01401825_0001_WORKORDER.pdf",
        "DELIVERYRECEIPT_0103164, src/test/resources/0103164_01402175_0001_DELIVERYRECEIPT.pdf"
    )
    fun `should extract customer number with correct format`(
        pdfType: String,
        pdfPath: String
    ) {
        val file = File(pdfPath)
        val document = Loader.loadPDF(file)

        try {
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            val customerNumber = zoneResult.getZoneValue("customer-number")
            
            assertNotNull(customerNumber, "$pdfType should extract customer number")
            assertTrue(
                customerNumber!!.matches(Regex("\\d{7}")),
                "$pdfType customer number should be 7 digits, got: $customerNumber"
            )
            assertTrue(
                customerNumber.startsWith("01") || customerNumber.startsWith("91"),
                "$pdfType customer number should start with 01 or 91, got: $customerNumber"
            )
            
            println("✅ Customer number validation passed for $pdfType: $customerNumber")
        } finally {
            document.close()
        }
    }

    @ParameterizedTest(name = "{0} - should validate invoice number format")
    @CsvSource(
        "DELIVERYRECEIPT, src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf",
        "WORKORDER, src/test/resources/0101431_01401825_0001_WORKORDER.pdf",
        "DELIVERYRECEIPT_0103164, src/test/resources/0103164_01402175_0001_DELIVERYRECEIPT.pdf"
    )
    fun `should extract invoice number with correct format`(
        pdfType: String,
        pdfPath: String
    ) {
        val file = File(pdfPath)
        val document = Loader.loadPDF(file)

        try {
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            val invoiceNumber = zoneResult.getZoneValue("order-number")
            
            assertNotNull(invoiceNumber, "$pdfType should extract invoice number")
            assertTrue(
                invoiceNumber!!.matches(Regex("\\d{8}")),
                "$pdfType invoice number should be 8 digits, got: $invoiceNumber"
            )
            
            println("✅ Invoice number validation passed for $pdfType: $invoiceNumber")
        } finally {
            document.close()
        }
    }

    @ParameterizedTest(name = "{0} - should validate line number format with leading zeros")
    @CsvSource(
        "DELIVERYRECEIPT, src/test/resources/0101431_01401825_0001_DELIVERYRECEIPT.pdf",
        "WORKORDER, src/test/resources/0101431_01401825_0001_WORKORDER.pdf",
        "DELIVERYRECEIPT_0103164, src/test/resources/0103164_01402175_0001_DELIVERYRECEIPT.pdf"
    )
    fun `should extract line number with leading zeros`(
        pdfType: String,
        pdfPath: String
    ) {
        val file = File(pdfPath)
        val document = Loader.loadPDF(file)

        try {
            val ocrWords = ocrService.extractOcrWords(document)
            val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords, document)
            
            val lineNumber = zoneResult.getZoneValue("line-number")
            
            assertNotNull(lineNumber, "$pdfType should extract line number")
            assertEquals(4, lineNumber!!.length, 
                "$pdfType line number should be 4 characters, got: $lineNumber")
            assertTrue(
                lineNumber.startsWith("0"),
                "$pdfType line number should start with 0, got: $lineNumber"
            )
            assertTrue(
                lineNumber.matches(Regex("\\d{4}")),
                "$pdfType line number should be 4 digits, got: $lineNumber"
            )
            
            println("✅ Line number validation passed for $pdfType: $lineNumber")
        } finally {
            document.close()
        }
    }
}
