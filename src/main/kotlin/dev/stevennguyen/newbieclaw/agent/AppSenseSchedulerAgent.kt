package dev.stevennguyen.newbieclaw.agent

import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.domain.io.UserInput
import dev.stevennguyen.newbieclaw.config.AppSenseProperties
import dev.stevennguyen.newbieclaw.domain.appsense.*
import dev.stevennguyen.newbieclaw.service.appsense.AppSenseSchedulerService

@Agent(description = "Schedules patient appointments by querying patient information and calling the AppSense scheduling API. Use when user asks to schedule, book, or create an appointment.")
class AppSenseSchedulerAgent(
    private val agentPlatform: AgentPlatform,
    private val service: AppSenseSchedulerService,
    private val props: AppSenseProperties
) {

    @Action(description = "Parse user input to extract patient name and appointment date/time")
    fun parseAppointmentRequest(userInput: UserInput, context: OperationContext): AppointmentRequest {
        println("\n" + "=".repeat(80))
        println(">>> STEP 1/3: Parsing appointment request")
        println("=".repeat(80) + "\n")

        val prompt = """
            Parse this appointment scheduling request and extract:
            1. Patient's first name
            2. Patient's last name
            3. The appointment date/time description (keep as-is, e.g., "tomorrow 9AM", "March 17 at 2PM")
            
            Request: "${userInput.content}"
            
            Extract the information accurately. If the patient name is not clear, make your best guess.
        """.trimIndent()

        val result = context.ai().withDefaultLlm().createObject(prompt, AppointmentRequest::class.java)
        
        println("    ✓ Patient: ${result.firstName} ${result.lastName}")
        println("    ✓ Date/Time: ${result.dateTimeDescription}")
        
        return result
    }

    @Action(description = "Search for patient by name using Patient Agent via A2A")
    fun findPatient(request: AppointmentRequest, context: OperationContext): PatientInfo {
        println("\n" + "=".repeat(80))
        println(">>> STEP 2/3: Finding patient via Patient Agent (A2A)")
        println("=".repeat(80) + "\n")

        val invocation: AgentInvocation<PatientSearchResult> = 
            AgentInvocation.create(agentPlatform, PatientSearchResult::class.java)
        
        val searchRequest = PatientSearchRequest(
            searchValue = request.lastName,
            firstName = request.firstName,
            lastName = request.lastName
        )
        
        println("    🔗 A2A: Invoking AppSensePatientAgent...")
        println("    📤 A2A Request: searchValue='${request.lastName}', firstName='${request.firstName}', lastName='${request.lastName}'")
        
        val searchResult = invocation.invoke(searchRequest)
        
        println("    📥 A2A Response: success=${searchResult.success}, patient=${searchResult.patient?.firstName} ${searchResult.patient?.lastName}")
        
        if (searchResult.success && searchResult.patient != null) {
            println("    ✓ Patient found via A2A: ${searchResult.patient.firstName} ${searchResult.patient.lastName}")
            println("    ✓ Document ID: ${searchResult.patient.documentId}")
            return searchResult.patient
        } else {
            throw IllegalStateException(searchResult.errorMessage ?: "Patient not found")
        }
    }

    @AchievesGoal(description = "Schedule the appointment and return confirmation")
    @Action(description = "Convert date/time to ISO format, build appointment payload, and schedule via API")
    fun scheduleAppointment(
        patient: PatientInfo,
        request: AppointmentRequest,
        context: OperationContext
    ): String {
        println("\n" + "=".repeat(80))
        println(">>> STEP 3/3: Scheduling appointment")
        println("=".repeat(80) + "\n")

        val dateTimePrompt = """
            Convert this date/time description to ISO 8601 format (UTC timezone).
            
            Description: "${request.dateTimeDescription}"
            Current date/time: ${java.time.ZonedDateTime.now()}
            
            Return the ISO 8601 formatted string (e.g., "2026-03-17T09:00:00.000Z").
            Also provide a human-readable description of the appointment time.
        """.trimIndent()

        val parsedDateTime = context.ai().withDefaultLlm().createObject(
            dateTimePrompt,
            ParsedDateTime::class.java
        )
        
        val isoDateTimeRegex = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
        if (!isoDateTimeRegex.matches(parsedDateTime.isoDateTime)) {
            throw IllegalStateException(
                "Invalid ISO 8601 format: ${parsedDateTime.isoDateTime}. " +
                "Expected format: YYYY-MM-DDTHH:mm:ss.sssZ"
            )
        }
        
        println("    ✓ Parsed date/time: ${parsedDateTime.description}")
        println("    ✓ ISO format: ${parsedDateTime.isoDateTime}")

        val visitTypeObject = VisitTypeObject(
            color = "#C729FF",
            name = props.visitType,
            durationOfVisit = props.visitLength,
            note = null,
            modifier = null,
            procedureCode = null,
            placeOfServiceId = null,
            isHPIVisitType = false,
            isActive = true,
            label = props.visitType,
            value = props.visitType
        )

        val attributes = VisitAttributes(
            insuranceChecks = emptyList(),
            visitProcess = emptyList(),
            patientId = patient.documentId,
            providerId = props.providerId,
            clinicianId = props.clinicianId,
            facilityNPI = props.facilityNPI,
            serviceTypeId = props.serviceTypeId,
            placeOfServiceId = props.placeOfServiceId,
            visitLength = props.visitLength,
            visitType = props.visitType,
            visitTypeObject = visitTypeObject,
            visitDate = parsedDateTime.isoDateTime,
            visitStatus = props.visitStatus,
            note = null,
            billToFacility = props.billToFacility
        )

        val scheduleRequest = ScheduleAppointment(
            type = "Visit",
            attributes = attributes
        )

        val response = service.scheduleAppointment(scheduleRequest)
        
        val confirmationMessage = """
            ✅ Appointment scheduled successfully!
            
            Patient: ${patient.firstName} ${patient.lastName}
            Date/Time: ${parsedDateTime.description}
            Visit Type: ${props.visitType}
            Duration: ${props.visitLength} minutes
            Status: ${props.visitStatus}
            
            Response: ${response.message ?: "Success"}
            Status Code: ${response.statusCode}
        """.trimIndent()
        
        println("\n" + "=".repeat(80))
        println(">>> APPOINTMENT CONFIRMATION:")
        println("=".repeat(80))
        println(confirmationMessage)
        println("=".repeat(80) + "\n")
        
        return confirmationMessage
    }
}
