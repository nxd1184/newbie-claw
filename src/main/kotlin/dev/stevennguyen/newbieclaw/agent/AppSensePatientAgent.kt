package dev.stevennguyen.newbieclaw.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import dev.stevennguyen.newbieclaw.domain.appsense.*
import dev.stevennguyen.newbieclaw.service.appsense.AppSensePatientService

@Agent(description = "Manages patient records in AppSense: create new patients, update existing patient information, or search for patients by name. Use when user wants to create, update, find, or search for patient information.")
class AppSensePatientAgent(
    private val service: AppSensePatientService
) {

    @AchievesGoal(description = "Search for patient by name and return patient information")
    @Action(description = "Search for patient and return structured result for A2A invocation")
    fun searchPatient(request: PatientSearchRequest, context: OperationContext): PatientSearchResult {
        println("\n" + "=".repeat(80))
        println(">>> AppSensePatientAgent: Searching for patient")
        println("=".repeat(80) + "\n")
        
        return try {
            val searchResponse = service.searchPatient(request.searchValue)
            val patientData = searchResponse.data
            
            if (patientData.totalRecords == 0) {
                PatientSearchResult(
                    success = false,
                    patient = null,
                    errorMessage = "No patient found with search value: ${request.searchValue}",
                    multipleMatches = null
                )
            } else {
                val matchingPatients = if (request.firstName != null && request.lastName != null) {
                    patientData.patients.filter { patient ->
                        patient.firstName.equals(request.firstName, ignoreCase = true) &&
                        patient.lastName.equals(request.lastName, ignoreCase = true)
                    }
                } else {
                    patientData.patients
                }
                
                when {
                    matchingPatients.isEmpty() -> {
                        println("    ⚠️  No exact match for: ${request.firstName} ${request.lastName}")
                        PatientSearchResult(
                            success = false,
                            patient = null,
                            errorMessage = "No patient found matching '${request.firstName} ${request.lastName}'. Found ${patientData.totalRecords} patient(s) with search value '${request.searchValue}'",
                            multipleMatches = patientData.patients
                        )
                    }
                    matchingPatients.size == 1 -> {
                        val patient = matchingPatients[0]
                        println("    ✓ Found patient: ${patient.firstName} ${patient.lastName}")
                        println("    ✓ Document ID: ${patient.documentId}")
                        PatientSearchResult(
                            success = true,
                            patient = patient,
                            errorMessage = null,
                            multipleMatches = null
                        )
                    }
                    else -> {
                        println("    ⚠️  Multiple patients found: ${matchingPatients.size}")
                        
                        val selectionPrompt = """
                            Multiple patients found with the name "${request.firstName} ${request.lastName}".
                            Select the most appropriate patient based on the context.
                            
                            Patients:
                            ${matchingPatients.mapIndexed { idx, p -> 
                                "${idx + 1}. ${p.firstName} ${p.lastName} - DOB: ${p.dob}, MRN: ${p.mrn}, ID: ${p.documentId}"
                            }.joinToString("\n")}
                            
                            Return the documentId of the selected patient.
                        """.trimIndent()
                        
                        val selected = context.ai().withDefaultLlm().createObject(
                            selectionPrompt, 
                            PatientInfo::class.java
                        )
                        
                        val patient = matchingPatients.find { it.documentId == selected.documentId }
                            ?: matchingPatients[0]
                        
                        println("    ✓ Selected patient: ${patient.firstName} ${patient.lastName} (${patient.documentId})")
                        PatientSearchResult(
                            success = true,
                            patient = patient,
                            errorMessage = null,
                            multipleMatches = matchingPatients
                        )
                    }
                }
            }
        } catch (e: Exception) {
            println("    ❌ Error searching for patient: ${e.message}")
            PatientSearchResult(
                success = false,
                patient = null,
                errorMessage = "Error searching for patient: ${e.message}",
                multipleMatches = null
            )
        }
    }

    private fun parsePatientInput(userInput: UserInput, context: OperationContext): PatientFormData {
        println("\n" + "=".repeat(80))
        println(">>> Parsing patient input")
        println("=".repeat(80) + "\n")

        val prompt = """
            Parse this patient management request and extract all available patient information.
            
            Request: "${userInput.content}"
            
            Extract any of the following fields that are mentioned:
            - firstName, lastName, middleName, suffix
            - dob (date of birth) - normalize to MM/DD/YYYY format
            - dateOfBirth (same as dob)
            - gender, maritalStatus, employmentStatus
            - height (in cm), weight (in kg)
            - allergiesRecord
            - phoneNumber, email
            - streetAddress, secondAddress, city, state, zipCode
            - ssn, mrn
            
            For state, convert abbreviations to full names (e.g., AZ -> Arizona).
            For dates, accept various formats and normalize to MM/DD/YYYY.
            
            Return null for any fields not mentioned in the request.
        """.trimIndent()

        val result = context.ai().withDefaultLlm().createObject(prompt, PatientFormData::class.java)
        
        println("    ✓ Extracted patient data:")
        println("      - Name: ${result.firstName} ${result.lastName}")
        println("      - DOB: ${result.dob ?: result.dateOfBirth}")
        println("      - Phone: ${result.phoneNumber}")
        println("      - Address: ${result.streetAddress}, ${result.city}, ${result.state} ${result.zipCode}")
        
        return result
    }

    private fun validateRequiredFields(patientData: PatientFormData): ValidationResult {
        println("\n" + "=".repeat(80))
        println(">>> Validating required fields")
        println("=".repeat(80) + "\n")

        val requiredFields = mutableListOf<String>()
        
        if (patientData.firstName.isNullOrBlank()) requiredFields.add("First Name")
        if (patientData.lastName.isNullOrBlank()) requiredFields.add("Last Name")
        if (patientData.dob.isNullOrBlank() && patientData.dateOfBirth.isNullOrBlank()) requiredFields.add("Date of Birth")
        if (patientData.phoneNumber.isNullOrBlank()) requiredFields.add("Phone Number")
        if (patientData.streetAddress.isNullOrBlank()) requiredFields.add("Street Address")
        if (patientData.city.isNullOrBlank()) requiredFields.add("City")
        if (patientData.state.isNullOrBlank()) requiredFields.add("State")
        if (patientData.zipCode.isNullOrBlank()) requiredFields.add("Zip Code")

        return if (requiredFields.isEmpty()) {
            println("    ✓ All required fields present")
            ValidationResult(
                isValid = true,
                missingFields = emptyList(),
                message = null
            )
        } else {
            println("    ⚠️  Missing required fields: ${requiredFields.joinToString(", ")}")
            ValidationResult(
                isValid = false,
                missingFields = requiredFields,
                message = """
                    To create/update a patient, I need the following required fields:
                    ${requiredFields.joinToString("\n") { "- $it" }}
                    
                    Please provide these details.
                """.trimIndent()
            )
        }
    }

    @Action(description = "Initiate patient creation flow with state-based conversation")
    fun createPatient(userInput: UserInput, context: OperationContext): PatientCreationStage {
        println("\n" + "=".repeat(80))
        println(">>> Initiating patient creation")
        println("=".repeat(80) + "\n")

        val patientData = parsePatientInput(userInput, context)
        val validation = validateRequiredFields(patientData)
        
        println("    Parsed data: ${patientData.firstName} ${patientData.lastName}")
        println("    Validation: ${if (validation.isValid) "✓ Complete" else "⚠️  Missing ${validation.missingFields.size} field(s)"}")


        return if (validation.isValid) {
            println("    → All required fields provided, moving to create state")
            CreatePatient(patientData)
        } else {
            println("    → Missing fields, entering collecting state")
            println("    Missing: ${validation.missingFields.joinToString(", ")}")
            CollectingPatientData(patientData, validation.missingFields)
        }
    }
    
    @Action(description = "Execute patient creation API call when all data is collected")
    fun createPatientRecord(createState: CreatePatient): Done {
        println("\n" + "=".repeat(80))
        println(">>> Creating patient record via API")
        println("=".repeat(80) + "\n")
        
        val patientData = createState.patientData
        
        val createRequest = CreatePatientRequest(
            mrn = patientData.mrn,
            firstName = patientData.firstName!!,
            suffix = patientData.suffix,
            middleName = patientData.middleName,
            lastName = patientData.lastName!!,
            dob = patientData.dob ?: patientData.dateOfBirth!!,
            gender = patientData.gender,
            maritalStatus = patientData.maritalStatus,
            employmentStatus = patientData.employmentStatus,
            height = patientData.height,
            weight = patientData.weight,
            allergiesRecord = patientData.allergiesRecord,
            markedFromReferral = false,
            phoneNumber = patientData.phoneNumber!!,
            email = patientData.email,
            state = patientData.state!!,
            city = patientData.city!!,
            zipCode = patientData.zipCode!!,
            streetAddress = patientData.streetAddress!!,
            secondAddress = patientData.secondAddress,
            ssn = patientData.ssn,
            dateOfBirth = patientData.dob ?: patientData.dateOfBirth!!
        )
        
        val response = service.createPatient(createRequest)
        
        val confirmationMessage = """
            ✅ Patient created successfully!
            
            Name: ${createRequest.firstName} ${createRequest.lastName}
            DOB: ${createRequest.dob}
            Phone: ${createRequest.phoneNumber}
            Address: ${createRequest.streetAddress}, ${createRequest.city}, ${createRequest.state} ${createRequest.zipCode}
            
            Response: ${response.message ?: "Success"}
            Status Code: ${response.statusCode}
        """.trimIndent()
        
        println("\n" + "=".repeat(80))
        println(">>> PATIENT CREATED:")
        println("=".repeat(80))
        println(confirmationMessage)
        println("=".repeat(80) + "\n")
        
        return Done(
            success = true,
            message = confirmationMessage
        )
    }

    @AchievesGoal(description = "Modify or change existing patient information when user wants to update or edit a patient's details")
    @Action(description = "Search for patient by name, then update their information")
    fun updatePatient(userInput: UserInput, context: OperationContext): String {
        println("\n" + "=".repeat(80))
        println(">>> Updating patient")
        println("=".repeat(80) + "\n")

        val parsePrompt = """
            Parse this patient update request and extract:
            1. The patient's name (to search for them)
            2. The fields to update
            
            Request: "${userInput.content}"
            
            Extract the patient name and all fields that should be updated.
        """.trimIndent()
        
        val patientData = context.ai().withDefaultLlm().createObject(parsePrompt, PatientFormData::class.java)
        
        if (patientData.firstName.isNullOrBlank() || patientData.lastName.isNullOrBlank()) {
            return "Please provide the patient's first and last name to update their information."
        }

        val searchRequest = PatientSearchRequest(
            searchValue = patientData.lastName,
            firstName = patientData.firstName,
            lastName = patientData.lastName
        )
        
        val searchResult = searchPatient(searchRequest, context)
        
        if (!searchResult.success || searchResult.patient == null) {
            return searchResult.errorMessage ?: "Patient not found"
        }
        
        val patient = searchResult.patient

        val updateRequest = UpdatePatientRequest(
            mrn = patientData.mrn ?: patient.mrn,
            firstName = patientData.firstName ?: patient.firstName,
            suffix = patientData.suffix,
            middleName = patientData.middleName,
            lastName = patientData.lastName ?: patient.lastName,
            dob = patientData.dob ?: patientData.dateOfBirth ?: patient.dob ?: "",
            gender = patientData.gender ?: patient.gender,
            maritalStatus = patientData.maritalStatus,
            employmentStatus = patientData.employmentStatus,
            height = patientData.height,
            weight = patientData.weight,
            allergiesRecord = patientData.allergiesRecord,
            markedFromReferral = false,
            phoneNumber = patientData.phoneNumber ?: patient.homePhone ?: patient.cellPhone ?: "",
            email = patientData.email ?: patient.email,
            state = patientData.state ?: "",
            city = patientData.city ?: "",
            zipCode = patientData.zipCode ?: "",
            streetAddress = patientData.streetAddress ?: "",
            secondAddress = patientData.secondAddress,
            ssn = patientData.ssn,
            dateOfBirth = patientData.dob ?: patientData.dateOfBirth ?: patient.dob ?: ""
        )

        val response = service.updatePatient(patient.documentId, updateRequest)
        
        val confirmationMessage = """
            ✅ Patient updated successfully!
            
            Patient: ${patient.firstName} ${patient.lastName}
            Patient ID: ${patient.documentId}
            
            Response: ${response.message ?: "Success"}
            Status Code: ${response.statusCode}
        """.trimIndent()
        
        println("\n" + "=".repeat(80))
        println(">>> PATIENT UPDATED:")
        println("=".repeat(80))
        println(confirmationMessage)
        println("=".repeat(80) + "\n")
        
        return confirmationMessage
    }
}
