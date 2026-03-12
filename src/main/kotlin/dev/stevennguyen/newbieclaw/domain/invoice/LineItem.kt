package dev.stevennguyen.newbieclaw.domain.invoice

data class LineItem(
    val itemNumber: String? = null,
    val description: String,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val unitPrice: Double,
    val extendedQuantity: String? = null,
    val total: Double,
    val materialSpecs: String? = null
)
