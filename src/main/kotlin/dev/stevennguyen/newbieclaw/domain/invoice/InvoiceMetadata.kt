package dev.stevennguyen.newbieclaw.domain.invoice

data class InvoiceMetadata(
    val invoiceNumber: String,
    val invoiceDate: String,
    val dueDate: String? = null,
    val currency: String = "USD",
    val paymentTerms: String? = null,
    val documentType: String? = null,
    val salesRepresentative: String? = null,
    val customerOrderNumber: String? = null
)
