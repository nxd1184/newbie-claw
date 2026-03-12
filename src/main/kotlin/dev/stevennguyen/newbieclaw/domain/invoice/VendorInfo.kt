package dev.stevennguyen.newbieclaw.domain.invoice

data class VendorInfo(
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val taxId: String? = null
)
