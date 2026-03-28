package dev.stevennguyen.newbieclaw.service

import dev.stevennguyen.newbieclaw.config.ZoneExtractionConfig
import dev.stevennguyen.newbieclaw.domain.invoice.OcrWord
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.Word
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage

@Service
class OcrService(
    private val config: ZoneExtractionConfig,
    private val preprocessingService: ImagePreprocessingService,
    private val multiPassStrategy: MultiPassOcrStrategy
) {

    fun extractOcrWords(document: PDDocument): List<OcrWord> {
        if (config.multiPassOcrEnabled) {
            return multiPassStrategy.executeMultiPassOcr(document)
        }
        
        return extractOcrWordsSinglePass(document)
    }
    
    private fun extractOcrWordsSinglePass(document: PDDocument): List<OcrWord> {
        println("    🔍 Starting OCR with bounding box extraction...")
        
        val tesseract = Tesseract()
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata")
        tesseract.setLanguage("eng")
        tesseract.setPageSegMode(config.ocrPsmMode)  // Configurable PSM mode for word detection
        
        val renderer = PDFRenderer(document)
        val allWords = mutableListOf<OcrWord>()
        val pageCount = document.numberOfPages
        
        for (pageIndex in 0 until pageCount) {
            println("    📄 Processing page ${pageIndex + 1}/$pageCount with OCR...")
            
            try {
                val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, config.ocrDpi)
                
                val preprocessedImage = if (config.preprocessingEnabled) {
                    println("    🔧 Applying preprocessing...")
                    preprocessingService.preprocessImage(
                        image,
                        config.preprocessingSteps,
                        config.denoiseStrength,
                        config.contrastFactor
                    )
                } else {
                    image
                }
                
                val words = extractWordsFromImage(tesseract, preprocessedImage, pageIndex)
                allWords.addAll(words)
                
                if (words.size < 10) {
                    println("    ⚠️  WARNING: Only ${words.size} words extracted - OCR may have failed")
                    println("    💡  Consider adjusting ocr-psm-mode in application.yml (try 1, 4, 6, or 12)")
                }
                
                println("    ✓ Page ${pageIndex + 1}: Extracted ${words.size} words")
            } catch (e: Exception) {
                println("    ⚠️  Error on page ${pageIndex + 1}: ${e.message}")
            }
        }
        
        println("    ✓ OCR completed: ${allWords.size} total words extracted")
        
        println("\n    🔍 DEBUG: Key OCR words detected:")
        val keyWords = allWords.filter { word ->
            word.text.uppercase().contains("SHIP") ||
            word.text.uppercase().contains("INVOICE") ||
            word.text.uppercase().contains("LINE") ||
            word.text.uppercase().contains("CUSTOMER") ||
            word.text.matches(Regex("\\d{4,}"))
        }
        keyWords.take(30).forEach { word ->
            println("      '${word.text}' at (${word.x}, ${word.y}) confidence=${String.format("%.2f", word.confidence)}")
        }
        
        return allWords
    }
    
    private fun extractWordsFromImage(tesseract: Tesseract, image: BufferedImage, pageNumber: Int): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        
        try {
            val tessWords: List<Word> = tesseract.getWords(image, 0)
            
            for (word in tessWords) {
                if (word.text.isNotBlank()) {
                    words.add(
                        OcrWord(
                            text = word.text.trim(),
                            x = word.boundingBox.x,
                            y = word.boundingBox.y,
                            width = word.boundingBox.width,
                            height = word.boundingBox.height,
                            confidence = word.confidence / 100.0f,
                            pageNumber = pageNumber
                        )
                    )
                }
            }
        } catch (e: Exception) {
            println("    ⚠️  Error extracting words from image: ${e.message}")
        }
        
        return words
    }
    
    fun groupWordsIntoLines(words: List<OcrWord>): List<List<OcrWord>> {
        if (words.isEmpty()) return emptyList()
        
        val sortedWords = words.sortedWith(compareBy({ it.y }, { it.x }))
        val lines = mutableListOf<MutableList<OcrWord>>()
        var currentLine = mutableListOf<OcrWord>()
        
        for (word in sortedWords) {
            if (currentLine.isEmpty()) {
                currentLine.add(word)
            } else {
                val lastWord = currentLine.last()
                val verticalDistance = kotlin.math.abs(word.centerY - lastWord.centerY)
                
                if (verticalDistance <= lastWord.height / 2) {
                    currentLine.add(word)
                } else {
                    lines.add(currentLine)
                    currentLine = mutableListOf(word)
                }
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        return lines.map { it.sortedBy { word -> word.x } }
    }
    
    fun findWordsByPattern(words: List<OcrWord>, pattern: Regex): List<OcrWord> {
        return words.filter { word ->
            pattern.containsMatchIn(word.text)
        }
    }
    
    fun findWordSequenceByPattern(words: List<OcrWord>, pattern: Regex): List<OcrWord>? {
        val lines = groupWordsIntoLines(words)
        
        for (line in lines) {
            val lineText = line.joinToString(" ") { it.text }
            if (pattern.containsMatchIn(lineText)) {
                val matchingWords = mutableListOf<OcrWord>()
                var currentText = ""
                
                for (word in line) {
                    currentText += " ${word.text}"
                    matchingWords.add(word)
                    
                    if (pattern.containsMatchIn(currentText.trim())) {
                        return matchingWords
                    }
                }
            }
        }
        
        return null
    }
    
    fun extractFullText(document: PDDocument): String {
        val tesseract = Tesseract()
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata")
        tesseract.setLanguage("eng")
        tesseract.setPageSegMode(config.ocrPsmMode)
        
        val renderer = PDFRenderer(document)
        val fullText = StringBuilder()
        
        for (pageIndex in 0 until document.numberOfPages) {
            try {
                val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, config.ocrDpi)
                val preprocessedImage = if (config.preprocessingEnabled) {
                    preprocessingService.preprocessImage(
                        image,
                        config.preprocessingSteps,
                        config.denoiseStrength,
                        config.contrastFactor
                    )
                } else {
                    image
                }
                
                val pageText = tesseract.doOCR(preprocessedImage)
                fullText.append(pageText).append("\n")
            } catch (e: Exception) {
                println("    ⚠️  Error extracting text from page ${pageIndex + 1}: ${e.message}")
            }
        }
        
        return fullText.toString()
    }
}
