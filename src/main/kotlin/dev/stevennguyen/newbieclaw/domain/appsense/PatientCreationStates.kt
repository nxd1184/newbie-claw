package dev.stevennguyen.newbieclaw.domain.appsense

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.State
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.domain.io.UserInput
import com.embabel.agent.core.hitl.WaitFor
import dev.stevennguyen.newbieclaw.service.appsense.AppSensePatientService

@State
sealed interface PatientCreationStage

@State
data class CollectingPatientData(
    val patientData: PatientFormData,
    val missingFields: List<String>
) : PatientCreationStage {
    
    @Action(description = "Prompt user for missing patient fields and wait for response")
    fun promptForFields(): UserInput {
        val message = """
            To create the patient, I need the following information:
            ${missingFields.joinToString("\n") { "- $it" }}
            
            Please provide these details.
        """.trimIndent()
        
        println("\n" + "=".repeat(80))
        println(">>> Prompting user for missing fields")
        println("=".repeat(80))
        println(message)
        println("=".repeat(80) + "\n")
        
        return WaitFor.formSubmission(message, UserInput::class.java)
    }
    
    @Action(description = "Collect additional patient information from user input")
    fun collectMoreFields(
        userInput: UserInput, 
        context: OperationContext
    ): PatientCreationStage {
        println("\n" + "=".repeat(80))
        println(">>> Collecting additional patient fields")
        println("    Current data: ${patientData.firstName} ${patientData.lastName}")
        println("    Still missing: ${missingFields.joinToString(", ")}")
        println("=".repeat(80) + "\n")
        
        val prompt = """
            Parse this additional patient information and extract any fields mentioned.
            The user is providing missing information for a patient creation request.
            
            User input: "${userInput.content}"
            
            Extract any of these fields if mentioned:
            - firstName, lastName, middleName, suffix
            - dob or dateOfBirth (date of birth in MM/DD/YYYY format)
            - phoneNumber
            - email
            - streetAddress, secondAddress, city, state, zipCode
            - gender, maritalStatus, employmentStatus
            - height, weight, allergiesRecord
            - ssn, mrn
            
            For state, convert abbreviations to full names (e.g., AZ -> Arizona).
            For dates, normalize to MM/DD/YYYY format.
            
            Return null for any fields NOT mentioned in this input.
        """.trimIndent()
        
        val newData = context.ai().withDefaultLlm().createObject(prompt, PatientFormData::class.java)
        
        val mergedData = PatientFormData(
            firstName = newData.firstName ?: patientData.firstName,
            lastName = newData.lastName ?: patientData.lastName,
            middleName = newData.middleName ?: patientData.middleName,
            suffix = newData.suffix ?: patientData.suffix,
            dob = newData.dob ?: patientData.dob,
            dateOfBirth = newData.dateOfBirth ?: patientData.dateOfBirth,
            gender = newData.gender ?: patientData.gender,
            maritalStatus = newData.maritalStatus ?: patientData.maritalStatus,
            employmentStatus = newData.employmentStatus ?: patientData.employmentStatus,
            height = newData.height ?: patientData.height,
            weight = newData.weight ?: patientData.weight,
            allergiesRecord = newData.allergiesRecord ?: patientData.allergiesRecord,
            phoneNumber = newData.phoneNumber ?: patientData.phoneNumber,
            email = newData.email ?: patientData.email,
            streetAddress = newData.streetAddress ?: patientData.streetAddress,
            secondAddress = newData.secondAddress ?: patientData.secondAddress,
            city = newData.city ?: patientData.city,
            state = newData.state ?: patientData.state,
            zipCode = newData.zipCode ?: patientData.zipCode,
            ssn = newData.ssn ?: patientData.ssn,
            mrn = newData.mrn ?: patientData.mrn
        )
        
        val updatedMissingFields = mutableListOf<String>()
        if (mergedData.firstName.isNullOrBlank()) updatedMissingFields.add("First Name")
        if (mergedData.lastName.isNullOrBlank()) updatedMissingFields.add("Last Name")
        if (mergedData.dob.isNullOrBlank() && mergedData.dateOfBirth.isNullOrBlank()) 
            updatedMissingFields.add("Date of Birth")
        if (mergedData.phoneNumber.isNullOrBlank()) updatedMissingFields.add("Phone Number")
        if (mergedData.streetAddress.isNullOrBlank()) updatedMissingFields.add("Street Address")
        if (mergedData.city.isNullOrBlank()) updatedMissingFields.add("City")
        if (mergedData.state.isNullOrBlank()) updatedMissingFields.add("State")
        if (mergedData.zipCode.isNullOrBlank()) updatedMissingFields.add("Zip Code")
        
        println("    ✓ Merged data")
        println("    Remaining missing fields: ${updatedMissingFields.size}")
        
        return if (updatedMissingFields.isEmpty()) {
            println("    ✓ All required fields collected! Moving to create state")
            CreatePatient(mergedData)
        } else {
            println("    ⚠️  Still missing ${updatedMissingFields.size} field(s)")
            CollectingPatientData(mergedData, updatedMissingFields)
        }
    }
}

@State
data class CreatePatient(
    val patientData: PatientFormData
) : PatientCreationStage

@State
data class Done(
    val success: Boolean,
    val message: String
) : PatientCreationStage {
    
    @AchievesGoal(description = "Patient creation process completed")
    @Action(description = "Return final confirmation message")
    fun complete(): String {
        return message
    }
}
