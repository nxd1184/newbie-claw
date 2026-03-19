package dev.stevennguyen.newbieclaw.domain.appsense

data class ScheduleAppointment(
    val type: String,
    val attributes: VisitAttributes
)

data class VisitAttributes(
    val insuranceChecks: List<Any> = emptyList(),
    val visitProcess: List<Any> = emptyList(),
    val patientId: String,
    val providerId: String,
    val clinicianId: String,
    val facilityNPI: String,
    val serviceTypeId: String,
    val placeOfServiceId: String,
    val visitLength: Int,
    val visitType: String,
    val visitTypeObject: VisitTypeObject,
    val visitDate: String,
    val visitStatus: String,
    val note: String? = null,
    val billToFacility: Boolean
)

data class VisitTypeObject(
    val color: String,
    val name: String,
    val durationOfVisit: Int,
    val note: String? = null,
    val modifier: String? = null,
    val procedureCode: String? = null,
    val placeOfServiceId: String? = null,
    val isHPIVisitType: Boolean,
    val isActive: Boolean,
    val label: String,
    val value: String
)