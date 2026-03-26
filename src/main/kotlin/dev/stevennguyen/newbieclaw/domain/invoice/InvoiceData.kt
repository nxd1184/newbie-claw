package dev.stevennguyen.newbieclaw.domain.invoice

data class InvoiceData(
    val vendor: VendorInfo,
    val customer: CustomerInfo,
    val metadata: InvoiceMetadata,
    val lineItems: List<LineItem>,
    val amounts: InvoiceAmounts,
    val shipping: ShippingInfo? = null,
    val notes: String? = null
)

data class InvoiceDataV1(
    val customerNumber: String?,
    val invoiceNumber: String?,
    val firstLineNumber: String?,
)