package dev.stevennguyen.newbieclaw.service

import dev.stevennguyen.newbieclaw.domain.invoice.OcrWord
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

@Service
class FuzzyMatcher {

    fun calculateSimilarity(text1: String, text2: String): Float {
        val normalized1 = normalizeOcrText(text1)
        val normalized2 = normalizeOcrText(text2)
        
        if (normalized1 == normalized2) return 1.0f
        if (normalized1.isEmpty() || normalized2.isEmpty()) return 0.0f
        
        val distance = levenshteinDistance(normalized1, normalized2)
        val maxLength = max(normalized1.length, normalized2.length)
        
        return 1.0f - (distance.toFloat() / maxLength)
    }
    
    fun findBestMatch(text: String, candidates: List<String>, threshold: Float): String? {
        var bestMatch: String? = null
        var bestSimilarity = 0.0f
        
        for (candidate in candidates) {
            val similarity = calculateSimilarity(text, candidate)
            if (similarity > bestSimilarity && similarity >= threshold) {
                bestSimilarity = similarity
                bestMatch = candidate
            }
        }
        
        return bestMatch
    }
    
    fun findFuzzyLabelWords(
        ocrWords: List<OcrWord>,
        labelTexts: List<String>,
        threshold: Float
    ): List<OcrWord> {
        val lines = groupWordsIntoLines(ocrWords)
        
        for (labelText in labelTexts) {
            val labelWords = labelText.split(" ")
            
            for (line in lines) {
                for (startIdx in 0..line.size - labelWords.size) {
                    val candidateWords = line.subList(startIdx, startIdx + labelWords.size)
                    val candidateText = candidateWords.joinToString(" ") { it.text }
                    
                    val similarity = calculateSimilarity(candidateText, labelText)
                    
                    if (similarity >= threshold) {
                        println("    🔍 Fuzzy match found: '$candidateText' ≈ '$labelText' (${String.format("%.2f", similarity)})")
                        return candidateWords
                    }
                }
            }
        }
        
        return emptyList()
    }
    
    fun normalizeOcrText(text: String): String {
        var normalized = text.uppercase()
        
        normalized = normalized
            .replace("0", "O")
            .replace("1", "I")
            .replace("5", "S")
            .replace("8", "B")
            .replace(Regex("[^A-Z0-9]"), "")
        
        return normalized
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) {
            dp[i][0] = i
        }
        
        for (j in 0..len2) {
            dp[0][j] = j
        }
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                
                dp[i][j] = min(
                    min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1
                    ),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    private fun groupWordsIntoLines(words: List<OcrWord>): List<List<OcrWord>> {
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
}
