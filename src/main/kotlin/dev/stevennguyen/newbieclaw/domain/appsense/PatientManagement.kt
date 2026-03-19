package dev.stevennguyen.newbieclaw.domain.appsense

data class PatientSearchRequest(
    val searchValue: String,
    val firstName: String? = null,
    val lastName: String? = null
)

data class PatientSearchResult(
    val success: Boolean,
    val patient: PatientInfo?,
    val errorMessage: String?,
    val multipleMatches: List<PatientInfo>? = null
)

data class CreatePatientRequest(
    val mrn: String?,
    val firstName: String,
    val suffix: String?,
    val middleName: String?,
    val lastName: String,
    val dob: String,
    val gender: String?,
    val maritalStatus: String?,
    val employmentStatus: String?,
    val height: Int?,
    val weight: Int?,
    val allergiesRecord: String?,
    val markedFromReferral: Boolean = false,
    val phoneNumber: String,
    val email: String?,
    val state: String,
    val city: String,
    val zipCode: String,
    val streetAddress: String,
    val secondAddress: String?,
    val ssn: String?,
    val dateOfBirth: String
)

data class UpdatePatientRequest(
    val mrn: String?,
    val firstName: String,
    val suffix: String?,
    val middleName: String?,
    val lastName: String,
    val dob: String,
    val gender: String?,
    val maritalStatus: String?,
    val employmentStatus: String?,
    val height: Int?,
    val weight: Int?,
    val allergiesRecord: String?,
    val markedFromReferral: Boolean = false,
    val phoneNumber: String,
    val email: String?,
    val state: String,
    val city: String,
    val zipCode: String,
    val streetAddress: String,
    val secondAddress: String?,
    val ssn: String?,
    val dateOfBirth: String
)

data class CreatePatientResponse(
    val message: String?,
    val statusCode: Int,
    val data: Any?
)

data class UpdatePatientResponse(
    val message: String?,
    val statusCode: Int,
    val data: Any?
)

data class PatientFormData(
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val suffix: String?,
    val dob: String?,
    val dateOfBirth: String?,
    val gender: String?,
    val maritalStatus: String?,
    val employmentStatus: String?,
    val height: Int?,
    val weight: Int?,
    val allergiesRecord: String?,
    val phoneNumber: String?,
    val email: String?,
    val streetAddress: String?,
    val secondAddress: String?,
    val city: String?,
    val state: String?,
    val zipCode: String?,
    val ssn: String?,
    val mrn: String?
)

data class ValidationResult(
    val isValid: Boolean,
    val missingFields: List<String>,
    val message: String?
)
