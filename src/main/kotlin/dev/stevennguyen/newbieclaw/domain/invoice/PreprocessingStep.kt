package dev.stevennguyen.newbieclaw.domain.invoice

enum class PreprocessingStep {
    DESKEW,
    DENOISE,
    ENHANCE_CONTRAST,
    BINARIZE,
    SCALE
}
