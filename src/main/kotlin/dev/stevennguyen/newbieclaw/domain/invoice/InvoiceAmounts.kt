package dev.stevennguyen.newbieclaw.domain.invoice

data class InvoiceAmounts(
    val subtotal: Double,
    val taxAmount: Double = 0.0,
    val taxRate: Double? = null,
    val discount: Double = 0.0,
    val total: Double
)
