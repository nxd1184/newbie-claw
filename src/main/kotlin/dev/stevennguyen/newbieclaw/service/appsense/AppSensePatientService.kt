package dev.stevennguyen.newbieclaw.service.appsense

import dev.stevennguyen.newbieclaw.config.AppSenseProperties
import dev.stevennguyen.newbieclaw.domain.appsense.*
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class AppSensePatientService(private val props: AppSenseProperties) {
    
    private val restTemplate = RestTemplate()
    
    fun searchPatient(searchValue: String): PatientSearchResponse {
        val url = "${props.host}/pat/api/Patients/overview" +
                "?pageSize=15" +
                "&pageIndex=1" +
                "&patientSortCriteria=LastName" +
                "&sortOrder=ASC" +
                "&searchValue=$searchValue"
        
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer ${props.token}")
            set("x_appsense_correlationId", "test")
        }
        
        val entity = HttpEntity<String>(headers)
        
        return try {
            println("    🔍 Searching for patient: $searchValue")
            println("    📡 GET $url")
            
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                PatientSearchResponse::class.java
            )
            
            println("    ✓ Found ${response.body?.data?.totalRecords ?: 0} patient(s)")
            response.body ?: throw IllegalStateException("Empty response from patient search API")
        } catch (e: HttpClientErrorException) {
            println("    ❌ Client error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to search patient: ${e.message}", e)
        } catch (e: HttpServerErrorException) {
            println("    ❌ Server error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to search patient: ${e.message}", e)
        } catch (e: Exception) {
            println("    ❌ Error: ${e.message}")
            throw IllegalStateException("Failed to search patient: ${e.message}", e)
        }
    }
    
    fun createPatient(request: CreatePatientRequest): CreatePatientResponse {
        val url = "${props.host}/pat/api/Patients"
        
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer ${props.token}")
            set("x_appsense_correlationId", "test")
        }
        
        val entity = HttpEntity(request, headers)
        
        return try {
            println("    👤 Creating patient...")
            println("    📡 POST $url")
            println("    📦 Patient: ${request.firstName} ${request.lastName}")
            
            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                CreatePatientResponse::class.java
            )
            
            println("    ✓ Patient created successfully")
            response.body ?: throw IllegalStateException("Empty response from create patient API")
        } catch (e: HttpClientErrorException) {
            println("    ❌ Client error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to create patient: ${e.message}", e)
        } catch (e: HttpServerErrorException) {
            println("    ❌ Server error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to create patient: ${e.message}", e)
        } catch (e: Exception) {
            println("    ❌ Error: ${e.message}")
            throw IllegalStateException("Failed to create patient: ${e.message}", e)
        }
    }
    
    fun updatePatient(patientId: String, request: UpdatePatientRequest): UpdatePatientResponse {
        val url = "${props.host}/pat/api/Patients/$patientId"
        
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer ${props.token}")
            set("x_appsense_correlationId", "test")
        }
        
        val entity = HttpEntity(request, headers)
        
        return try {
            println("    📝 Updating patient...")
            println("    📡 PATCH $url")
            println("    📦 Patient ID: $patientId")
            
            val response = restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                entity,
                UpdatePatientResponse::class.java
            )
            
            println("    ✓ Patient updated successfully")
            response.body ?: throw IllegalStateException("Empty response from update patient API")
        } catch (e: HttpClientErrorException) {
            println("    ❌ Client error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to update patient: ${e.message}", e)
        } catch (e: HttpServerErrorException) {
            println("    ❌ Server error: ${e.statusCode} - ${e.responseBodyAsString}")
            throw IllegalStateException("Failed to update patient: ${e.message}", e)
        } catch (e: Exception) {
            println("    ❌ Error: ${e.message}")
            throw IllegalStateException("Failed to update patient: ${e.message}", e)
        }
    }
}
