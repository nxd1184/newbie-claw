package dev.stevennguyen.newbieclaw.domain.invoice

data class CustomerInfo(
    val name: String,
    val billToAddress: String? = null,
    val shipToAddress: String? = null,
    val phone: String? = null
)
