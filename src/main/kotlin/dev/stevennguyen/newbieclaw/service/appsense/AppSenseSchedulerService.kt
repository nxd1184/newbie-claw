package dev.stevennguyen.newbieclaw.service.appsense

import dev.stevennguyen.newbieclaw.config.AppSenseProperties
import dev.stevennguyen.newbieclaw.domain.appsense.ScheduleAppointment
import dev.stevennguyen.newbieclaw.domain.appsense.ScheduleAppointmentResponse
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class AppSenseSchedulerService(private val props: AppSenseProperties) {
    
    private val restTemplate = RestTemplate()
    
    fun scheduleAppointment(request: ScheduleAppointment): ScheduleAppointmentResponse {
        val url = "${props.host}/pat/api/visits"
        
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer ${props.token}")
            set("x_appsense_correlationId", "test")
        }
        
        val entity = HttpEntity(request, headers)
        
        return try {
            println("    📅 Scheduling appointment...")
            println("    📡 POST $url")
            println("    📦 Patient ID: ${request.attributes.patientId}")
            println("    📦 Visit Date: ${request.attributes.visitDate}")
            
            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ScheduleAppointmentResponse::class.java
            )
            
            println("    ✓ Appointment scheduled successfully")
            response.body ?: throw IllegalStateException("Empty response from schedule appointment API")
        } catch (e: HttpClientErrorException) {
            println("    ❌ Client error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to schedule appointment: ${e.message}", e)
        } catch (e: HttpServerErrorException) {
            println("    ❌ Server error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to schedule appointment: ${e.message}", e)
        } catch (e: Exception) {
            println("    ❌ Error: ${e.message}")
            throw IllegalStateException("Failed to schedule appointment: ${e.message}", e)
        }
    }
}