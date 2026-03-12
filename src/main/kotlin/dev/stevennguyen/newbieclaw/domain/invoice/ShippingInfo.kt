package dev.stevennguyen.newbieclaw.domain.invoice

data class ShippingInfo(
    val freightMethod: String? = null,
    val route: String? = null,
    val shipDate: String? = null,
    val shipToAddress: String? = null
)
