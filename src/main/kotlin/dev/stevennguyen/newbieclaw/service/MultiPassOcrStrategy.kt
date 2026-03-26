package dev.stevennguyen.newbieclaw.service

import dev.stevennguyen.newbieclaw.config.ZoneExtractionConfig
import dev.stevennguyen.newbieclaw.domain.invoice.OcrPassConfig
import dev.stevennguyen.newbieclaw.domain.invoice.OcrWord
import dev.stevennguyen.newbieclaw.domain.invoice.PreprocessingStep
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.Word
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage

@Service
class MultiPassOcrStrategy(
    private val config: ZoneExtractionConfig,
    private val preprocessingService: ImagePreprocessingService
) {

    fun executeMultiPassOcr(document: PDDocument): List<OcrWord> {
        println("    🔄 Starting multi-pass OCR strategy...")
        
        val passes = getOcrPasses()
        val allResults = mutableListOf<List<OcrWord>>()
        
        for (pass in passes) {
            println("    📋 Executing OCR pass: ${pass.name} (DPI: ${pass.dpi}, PSM: ${pass.psmMode})")
            
            try {
                val words = executeOcrPass(document, pass)
                allResults.add(words)
                println("    ✓ Pass '${pass.name}': Extracted ${words.size} words")
            } catch (e: Exception) {
                println("    ⚠️  Pass '${pass.name}' failed: ${e.message}")
            }
        }
        
        if (allResults.isEmpty()) {
            println("    ❌ All OCR passes failed")
            return emptyList()
        }
        
        val mergedResults = mergeOcrResults(allResults)
        println("    ✅ Multi-pass OCR completed: ${mergedResults.size} words (merged from ${allResults.size} passes)")
        
        return mergedResults
    }
    
    private fun executeOcrPass(document: PDDocument, passConfig: OcrPassConfig): List<OcrWord> {
        val tesseract = Tesseract()
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata")
        tesseract.setLanguage("eng")
        tesseract.setPageSegMode(passConfig.psmMode)
        
        val renderer = PDFRenderer(document)
        val allWords = mutableListOf<OcrWord>()
        val pageCount = document.numberOfPages
        
        for (pageIndex in 0 until pageCount) {
            try {
                val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, passConfig.dpi)
                
                val preprocessedImage = if (passConfig.preprocessingSteps.isNotEmpty()) {
                    preprocessingService.preprocessImage(
                        image,
                        passConfig.preprocessingSteps,
                        config.denoiseStrength,
                        config.contrastFactor
                    )
                } else {
                    image
                }
                
                val words = extractWordsFromImage(tesseract, preprocessedImage, pageIndex)
                allWords.addAll(words)
            } catch (e: Exception) {
                println("    ⚠️  Error on page ${pageIndex + 1}: ${e.message}")
            }
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
    
    private fun mergeOcrResults(passes: List<List<OcrWord>>): List<OcrWord> {
        if (passes.isEmpty()) return emptyList()
        if (passes.size == 1) return passes[0]
        
        val wordMap = mutableMapOf<String, MutableList<OcrWord>>()
        
        for (passWords in passes) {
            for (word in passWords) {
                val key = "${word.pageNumber}_${word.x}_${word.y}"
                wordMap.getOrPut(key) { mutableListOf() }.add(word)
            }
        }
        
        val mergedWords = mutableListOf<OcrWord>()
        
        for ((_, candidates) in wordMap) {
            val bestWord = selectBestWord(candidates)
            mergedWords.add(bestWord)
        }
        
        return mergedWords.sortedWith(compareBy({ it.pageNumber }, { it.y }, { it.x }))
    }
    
    private fun selectBestWord(candidates: List<OcrWord>): OcrWord {
        if (candidates.size == 1) return candidates[0]
        
        return candidates.maxByOrNull { word ->
            var score = word.confidence
            
            if (word.text.all { it.isLetterOrDigit() || it.isWhitespace() }) {
                score += 0.1f
            }
            
            if (word.text.length > 1) {
                score += 0.05f
            }
            
            score
        } ?: candidates[0]
    }
    
    private fun getOcrPasses(): List<OcrPassConfig> {
        val passes = mutableListOf<OcrPassConfig>()
        
        passes.add(
            OcrPassConfig(
                name = "standard",
                dpi = 300f,
                psmMode = 3,
                preprocessingSteps = listOf(
                    PreprocessingStep.DESKEW,
                    PreprocessingStep.DENOISE,
                    PreprocessingStep.BINARIZE
                )
            )
        )
        
        if (config.maxPasses >= 2) {
            passes.add(
                OcrPassConfig(
                    name = "high-quality",
                    dpi = 400f,
                    psmMode = 1,
                    preprocessingSteps = listOf(
                        PreprocessingStep.DESKEW,
                        PreprocessingStep.DENOISE,
                        PreprocessingStep.ENHANCE_CONTRAST,
                        PreprocessingStep.BINARIZE
                    )
                )
            )
        }
        
        if (config.maxPasses >= 3) {
            passes.add(
                OcrPassConfig(
                    name = "sparse-text",
                    dpi = 300f,
                    psmMode = 6,
                    preprocessingSteps = listOf(
                        PreprocessingStep.DENOISE,
                        PreprocessingStep.BINARIZE
                    )
                )
            )
        }
        
        return passes
    }
}
