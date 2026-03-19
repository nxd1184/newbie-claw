package dev.stevennguyen.newbieclaw.domain.appsense

data class AppointmentRequest(
    val firstName: String,
    val lastName: String,
    val dateTimeDescription: String
)

data class PatientSearchResponse(
    val data: PatientData,
    val message: String?,
    val statusCode: Int,
    val version: String
)

data class PatientData(
    val patients: List<PatientInfo>,
    val totalRecords: Int
)

data class PatientInfo(
    val documentId: String,
    val firstName: String,
    val lastName: String,
    val middleInitial: String?,
    val dob: String?,
    val gender: String?,
    val mrn: String?,
    val email: String?,
    val homePhone: String?,
    val cellPhone: String?
)

data class ScheduleAppointmentResponse(
    val message: String?,
    val statusCode: Int,
    val data: Any?
)

data class ParsedDateTime(
    val isoDateTime: String,
    val description: String
)
