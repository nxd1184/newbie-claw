package dev.stevennguyen.newbieclaw.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "appsense")
data class AppSenseProperties(
    val host: String = "https://api.dev.appsensesolutions.com",
    val token: String = "",
    val providerId: String = "PR000002",
    val clinicianId: String = "856d38a8-d240-4ffc-ba52-f0781aea1844",
    val facilityNPI: String = "1114535333",
    val serviceTypeId: String = "1",
    val placeOfServiceId: String = "12",
    val visitLength: Int = 30,
    val visitType: String = "Consult",
    val visitStatus: String = "ReadyForProvider",
    val billToFacility: Boolean = false
)
