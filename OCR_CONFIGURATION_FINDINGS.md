# OCR Configuration Testing Results

## Summary

Comprehensive testing of different Tesseract PSM modes and multi-pass OCR strategies to improve detection of customer number (`0101431`) and line number (`0001`).

## Test Results

### PSM Mode Comparison

| PSM Mode | Words Extracted | Invoice # | Customer # | Line # | Status |
|----------|----------------|-----------|------------|--------|--------|
| **11 (Sparse Text)** | 407 | ✅ `01401825` | ❌ `null` | ❌ `null` | **BEST** |
| **3 (Auto)** | 20 | ❌ `null` | ❌ `null` | ❌ `null` | **FAILED** |
| **Multi-Pass (3,1,6)** | ~500 | ❌ `null` | ❌ `null` | ❌ `null` | **FAILED** |

### Detailed Findings

#### PSM Mode 11 (Sparse Text) - CURRENT CONFIGURATION ✅
```
Total OCR words: 407
7-8 digit numbers found: [01401825 at (2071, 104)]
4-digit numbers starting with 0: []
Words containing 0101431: []
Words containing 0001: []

Results:
- Invoice Number: 01401825 (confidence: 0.93) ✅
- Customer Number: null ❌
- Line Number: null ❌
```

**Analysis:**
- Extracts 407 individual words successfully
- Detects prominent text in boxes/headers (invoice number)
- **Misses smaller numbers in body text** (customer number, line number)
- Numbers `0101431` and `0001` are NOT in the OCR word list at all

#### PSM Mode 3 (Auto Page Segmentation) - TESTED ❌
```
Total OCR words: 20
Results: All fields null
```

**Analysis:**
- Drastically reduced word count (407 → 20)
- Merges text into large blocks
- Completely unsuitable for this PDF format

#### Multi-Pass OCR (PSM 3, 1, 6) - TESTED ❌
```
Pass 1 (PSM 3): Merged text blocks
Pass 2 (PSM 1): Merged text blocks  
Pass 3 (PSM 6): Merged text blocks
Results: All fields null
```

**Analysis:**
- All passes produced merged text blocks instead of individual words
- Merging strategy couldn't recover individual numbers
- Preprocessing with binarization made things worse

## Root Cause

**The customer number (`0101431`) and line number (`0001`) are NOT being detected as separate words by Tesseract OCR, regardless of PSM mode or preprocessing.**

### Why OCR Fails on These Numbers

1. **Font/Size**: Numbers may be in a smaller font that OCR struggles with
2. **Contrast**: Numbers may have low contrast against background
3. **Position**: Numbers in table cells or body text are harder to detect than boxed headers
4. **Image Quality**: The PDF may be a scan with quality issues in those specific areas

### Why Invoice Number Works

- Located in a prominent box at top-right
- Larger font size
- High contrast
- Clear boundaries
- PSM mode 11 handles this well

## Current Implementation Status

### What's Working ✅

1. **PSM Mode 11 Configuration** - Optimal for this PDF (407 words)
2. **Invoice Number Extraction** - Perfect (0.93 confidence)
3. **Validation Logic** - Correctly rejects invalid values
4. **Debug Logging** - Excellent diagnostics
5. **Integration Tests** - Comprehensive test suite
6. **Fallback Strategies** - Label patterns, position-based fallback

### What's Not Working ❌

1. **Customer Number** - OCR doesn't detect `0101431` as a word
2. **Line Number** - OCR doesn't detect `0001` as a word
3. **Alternative PSM Modes** - All perform worse than mode 11
4. **Multi-Pass OCR** - Causes text merging issues

## Recommendations

Since OCR cannot detect these specific numbers, we need alternative approaches:

### Option 1: Hybrid Regex Fallback (RECOMMENDED)

Add a fallback that parses the full OCR text with regex when zone detection fails:

```kotlin
fun extractWithRegexFallback(document: PDDocument): InvoiceDataV1 {
    // Try zone-based first
    val zoneResult = zoneDetectionEngine.detectAndExtractZones(ocrWords)
    
    // If customer number is null, try regex on full text
    if (zoneResult.getZoneValue("customer-number") == null) {
        val fullText = extractFullOcrText(document)
        val customerNumber = Regex("\\b(\\d{7})\\b")
            .findAll(fullText)
            .map { it.value }
            .firstOrNull { it.startsWith("01") || it.startsWith("91") }
        // Use regex result
    }
    
    // Similar for line number
    if (zoneResult.getZoneValue("line-number") == null) {
        val lineNumber = Regex("\\b(0\\d{3})\\b")
            .find(fullText)?.value
    }
}
```

### Option 2: Use Different OCR Engine

Try a different OCR engine that may handle this PDF better:
- Google Cloud Vision API
- AWS Textract
- Azure Computer Vision
- EasyOCR (Python-based)

### Option 3: Manual Coordinate-Based Extraction

If the numbers are always in the same position, extract by pixel coordinates:

```kotlin
fun extractByCoordinates(image: BufferedImage): String {
    // Crop specific region where customer number appears
    val customerNumberRegion = image.getSubimage(x, y, width, height)
    // Run OCR on just that region
    return tesseract.doOCR(customerNumberRegion)
}
```

### Option 4: Improve PDF Quality

Preprocess the PDF to enhance the specific regions:
- Increase resolution (try 600 DPI instead of 300)
- Apply localized contrast enhancement
- Sharpen specific regions before OCR

## Configuration Files

### Current Optimal Configuration

```yaml
zone-extraction:
  ocr-psm-mode: 11              # Sparse text - best for this PDF
  preprocessing-enabled: false   # Preprocessing causes issues
  multi-pass-ocr-enabled: false  # Multi-pass causes text merging
```

### Tested Configurations That Failed

```yaml
# PSM Mode 3 - Only 20 words extracted
ocr-psm-mode: 3

# Multi-pass with PSM 3, 1, 6 - Text merging issues
multi-pass-ocr-enabled: true
max-passes: 3
```

## Test Infrastructure

### Created Files
- ✅ `InvoiceExtractionIntegrationTest.kt` - 6 comprehensive tests
- ✅ `DiagnosticTest.kt` - Detailed OCR analysis
- ✅ Test dependencies in `build.gradle.kts`
- ✅ `INTEGRATION_TEST_RESULTS.md` - Initial findings
- ✅ `OCR_CONFIGURATION_FINDINGS.md` - This document

### Test Commands

```bash
# Run diagnostic test
.\gradlew test --tests "DiagnosticTest"

# Run full integration test suite
.\gradlew test --tests "InvoiceExtractionIntegrationTest"

# Clean and run all tests
.\gradlew clean test
```

## Conclusion

**PSM Mode 11 is optimal for this PDF format**, but it cannot detect the customer number and line number because they are not being recognized as separate words by Tesseract OCR.

**Next Steps:**
1. Implement regex fallback to parse full OCR text
2. Test with higher DPI (600) for those specific regions
3. Consider alternative OCR engines if regex fallback insufficient

The integration test suite is valuable for:
- ✅ Quick verification of OCR configuration changes
- ✅ Regression prevention
- ✅ Automated testing in CI/CD
- ✅ Documentation of expected behavior

**Current Status:** Zone-based extraction works perfectly for invoice number (1 out of 3 fields). Need hybrid approach for remaining fields.
