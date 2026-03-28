package dev.stevennguyen.newbieclaw.domain.invoice

data class OcrPassConfig(
    val name: String,
    val dpi: Float,
    val psmMode: Int,
    val preprocessingSteps: List<PreprocessingStep>
)
