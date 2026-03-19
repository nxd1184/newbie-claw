# AppSenseSchedulerAgent - Implementation Summary

## Overview

Successfully implemented the `AppSenseSchedulerAgent` that schedules patient appointments by querying the AppSense patient API and calling the scheduling endpoint.

## Files Created

1. **`domain/appsense/PatientQuery.kt`** - Domain models for:
   - `AppointmentRequest` - Parsed user input (patient name, date/time)
   - `PatientSearchResponse` - Patient search API response
   - `PatientInfo` - Individual patient data
   - `ScheduleAppointmentResponse` - Scheduling API response
   - `ParsedDateTime` - ISO 8601 formatted date/time

2. **`agent/AppSenseSchedulerAgent.kt`** - Main agent with 3 actions:
   - `parseAppointmentRequest()` - Extracts patient name and appointment time from user input
   - `findPatient()` - Searches for patient by name, handles multiple matches
   - `scheduleAppointment()` - Converts time to ISO format and schedules via API

## Files Updated

1. **`config/AppSenseProperties.kt`**
   - Added `@ConfigurationProperties(prefix = "appsense")`
   - Added hardcoded defaults for testing: providerId, clinicianId, facilityNPI, etc.

2. **`service/appsense/AppSenseSchedulerService.kt`**
   - Fixed syntax errors
   - Implemented `searchPatient()` - GET request to patient overview API
   - Implemented `scheduleAppointment()` - POST request to visits API
   - Added proper error handling and logging

3. **`resources/application.yml`**
   - Added `appsense` configuration section with all defaults

4. **`NewbieClawApplication.kt`**
   - Added `AppSenseProperties` to `@EnableConfigurationProperties`
   - Created Spring beans for service and agent

## Configuration

Set the AppSense API token via environment variable:

```bash
export APPSENSE_TOKEN="your-token-here"
```

Or update `application.yml`:

```yaml
appsense:
  token: "your-token-here"
```

## Usage Example

**User request:**
```
please schedule appointment for Stacy Morris on tomorrow 9AM
```

**Agent workflow:**

1. **Parse Request** (Action 1)
   - Extracts: firstName="Stacy", lastName="Morris", dateTime="tomorrow 9AM"

2. **Find Patient** (Action 2)
   - Searches API: `GET /pat/api/Patients/overview?searchValue=Morris`
   - Filters results by matching firstName AND lastName
   - Returns patient with documentId: `AlXtoA8EhFQ2Axo4Iqni`

3. **Schedule Appointment** (Action 3)
   - Converts "tomorrow 9AM" to ISO 8601: `2026-03-17T09:00:00.000Z`
   - Validates ISO format with regex
   - Builds payload with hardcoded defaults + patient ID + visit date
   - Posts to: `POST /pat/api/visits`
   - Returns confirmation message

## Hardcoded Defaults (for testing)

```yaml
provider-id: PR000002
clinician-id: 856d38a8-d240-4ffc-ba52-f0781aea1844
facility-npi: "1114535333"
service-type-id: "1"
place-of-service-id: "12"
visit-length: 30
visit-type: Consult
visit-status: ReadyForProvider
bill-to-facility: false
```

## Patient Matching Logic

- Searches by last name first (API limitation)
- Filters results by exact first name + last name match (case-insensitive)
- Handles three scenarios:
  - **No match**: Throws error with available patients
  - **Single match**: Uses that patient
  - **Multiple matches**: Uses LLM to select best match based on context

## Date/Time Parsing

- LLM converts natural language to ISO 8601 format
- Validates format with regex: `\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z`
- Throws error if format is invalid before making API call

## Error Handling

- Patient not found → Clear error message with available options
- Multiple ambiguous matches → LLM selects best match
- Invalid date format → Validation error before API call
- API errors → Detailed error messages with status codes

## Build Status

✅ Build successful - all files compile without errors

## Next Steps

1. Set `APPSENSE_TOKEN` environment variable
2. Run the application: `./gradlew bootRun`
3. Test with: "please schedule appointment for Stacy Morris on tomorrow 9AM"
4. Verify appointment is created in AppSense system
