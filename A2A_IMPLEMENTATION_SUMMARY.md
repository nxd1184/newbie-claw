# Agent-to-Agent (A2A) Implementation Summary

## Overview

Successfully implemented Agent-to-Agent (A2A) communication where **AppSenseSchedulerAgent** programmatically invokes **AppSensePatientAgent** to search for patients using Embabel's `AgentInvocation` API.

## Architecture

### A2A Communication Flow

```
User: "schedule appointment for Stacy Morris on tomorrow 9AM"
    ↓
AppSenseSchedulerAgent.parseAppointmentRequest()
    ↓ (extracts patient name and date/time)
    ↓
AppSenseSchedulerAgent.findPatient()
    ↓
    ├─→ Creates AgentInvocation<PatientSearchResult>
    ├─→ Creates PatientSearchRequest(searchValue="Morris", firstName="Stacy", lastName="Morris")
    ├─→ Invokes AppSensePatientAgent.searchPatient() ← **A2A CALL**
    │       ↓
    │   AppSensePatientAgent.searchPatient()
    │       ├─→ Calls AppSensePatientService.searchPatient("Morris")
    │       ├─→ Filters by firstName="Stacy"
    │       ├─→ Returns PatientSearchResult(success=true, patient=PatientInfo(...))
    │       ↓
    ├─→ Receives PatientSearchResult
    ├─→ Extracts PatientInfo
    ↓
AppSenseSchedulerAgent.scheduleAppointment()
    ├─→ Converts date/time to ISO format
    ├─→ Calls AppSenseSchedulerService.scheduleAppointment()
    ↓
User receives confirmation
```

## Files Created

### 1. Domain Models (`PatientManagement.kt`)
- `PatientSearchRequest` - Input for A2A patient search
- `PatientSearchResult` - Output from A2A patient search (success flag, patient data, error message)
- `CreatePatientRequest` - API request for creating patient
- `UpdatePatientRequest` - API request for updating patient
- `CreatePatientResponse` - API response for create
- `UpdatePatientResponse` - API response for update
- `PatientFormData` - Parsed patient input data
- `ValidationResult` - Required fields validation result

### 2. AppSensePatientService
HTTP client for patient operations:
- `searchPatient(searchValue: String): PatientSearchResponse`
- `createPatient(request: CreatePatientRequest): CreatePatientResponse`
- `updatePatient(patientId: String, request: UpdatePatientRequest): UpdatePatientResponse`

All methods include:
- `Authorization: Bearer {token}` header
- `x_appsense_correlationId: test` header
- Proper error handling and logging

### 3. AppSensePatientAgent
Unified agent with three goals:

**Goal 1: Search Patient (A2A-enabled)**
```kotlin
@AchievesGoal
@Action
fun searchPatient(request: PatientSearchRequest, context: OperationContext): PatientSearchResult
```
- Called by AppSenseSchedulerAgent via A2A
- Searches by name, filters results, handles multiple matches
- Returns structured result with success flag

**Goal 2: Create Patient**
```kotlin
@AchievesGoal
@Action
fun createPatient(userInput: UserInput, context: OperationContext): String
```
- Parses user input to extract patient data
- Validates required fields (interactive prompting if missing)
- Creates patient via API

**Goal 3: Update Patient**
```kotlin
@AchievesGoal
@Action
fun updatePatient(userInput: UserInput, context: OperationContext): String
```
- Searches for patient by name
- Updates patient information via PATCH

## Files Updated

### 1. AppSenseSchedulerService
**Removed**: `searchPatient()` method (moved to AppSensePatientService)
**Kept**: `scheduleAppointment()` method only

### 2. AppSenseSchedulerAgent
**Added**: `AgentPlatform` dependency for A2A
**Updated**: `findPatient()` action to use A2A invocation

```kotlin
@Action(description = "Search for patient by name using Patient Agent via A2A")
fun findPatient(request: AppointmentRequest, context: OperationContext): PatientInfo {
    val invocation: AgentInvocation<PatientSearchResult> = 
        AgentInvocation.create(agentPlatform, PatientSearchResult::class.java)
    
    val searchRequest = PatientSearchRequest(
        searchValue = request.lastName,
        firstName = request.firstName,
        lastName = request.lastName
    )
    
    println("    🔗 A2A: Invoking AppSensePatientAgent...")
    val searchResult = invocation.invoke(searchRequest)
    
    if (searchResult.success && searchResult.patient != null) {
        return searchResult.patient
    } else {
        throw IllegalStateException(searchResult.errorMessage ?: "Patient not found")
    }
}
```

### 3. NewbieClawApplication
**Added beans**:
- `appSensePatientService(props: AppSenseProperties)`
- `appSensePatientAgent(service: AppSensePatientService, props: AppSenseProperties)`

**Updated bean**:
- `appSenseSchedulerAgent` now injects `AgentPlatform` for A2A

## Key Features

### 1. Type-Safe A2A Communication
```kotlin
val invocation: AgentInvocation<PatientSearchResult> = 
    AgentInvocation.create(agentPlatform, PatientSearchResult::class.java)
```
- Compile-time type checking
- Automatic agent discovery based on return type
- Clean, readable code

### 2. Structured A2A Contract
```kotlin
// Input
data class PatientSearchRequest(
    val searchValue: String,
    val firstName: String? = null,
    val lastName: String? = null
)

// Output
data class PatientSearchResult(
    val success: Boolean,
    val patient: PatientInfo?,
    val errorMessage: String?,
    val multipleMatches: List<PatientInfo>? = null
)
```

### 3. Clear Separation of Concerns
- **AppSensePatientAgent**: Owns all patient data operations
- **AppSenseSchedulerAgent**: Owns appointment scheduling
- **AppSensePatientService**: HTTP client for patient API
- **AppSenseSchedulerService**: HTTP client for scheduling API

### 4. Interactive Prompting (Patient Agent)
When creating patients, if required fields are missing:
```
User: "create patient John Doe born 11/08/1990"
Agent: "To create a patient, I need:
        - Phone number
        - Street address
        - City
        - State
        - Zip code"
```

### 5. A2A Logging
Clear logging for debugging A2A calls:
```
🔗 A2A: Invoking AppSensePatientAgent...
📤 A2A Request: searchValue='Morris', firstName='Stacy', lastName='Morris'
📥 A2A Response: success=true, patient=Stacy Morris
✓ Patient found via A2A: Stacy Morris
```

## Usage Examples

### Schedule Appointment (with A2A)
```
User: "schedule appointment for Stacy Morris on tomorrow 9AM"

Flow:
1. Scheduler parses request
2. Scheduler invokes Patient Agent via A2A ← KEY FEATURE
3. Patient Agent searches and returns patient info
4. Scheduler schedules appointment
5. User gets confirmation
```

### Create Patient
```
User: "create patient Nguyen Xuan Dung, DOB 11/08/1990, male, phone 0794123412, 
       address Address 1, Ho Chi Minh, Arizona 140102"

Flow:
1. Patient Agent parses input
2. Validates required fields
3. Creates patient via API
4. Returns confirmation
```

### Update Patient
```
User: "update patient Stacy Morris with new phone number 555-9999"

Flow:
1. Patient Agent parses update request
2. Searches for patient by name (uses own searchPatient method)
3. Updates patient via PATCH API
4. Returns confirmation
```

## Benefits of A2A Architecture

### 1. True Agent Autonomy
- Each agent is self-contained
- Patient agent owns all patient operations
- Scheduler agent owns appointment scheduling

### 2. Composability
- Agents can be composed to build complex workflows
- Future: Billing agent could invoke Patient agent
- Future: Reporting agent could invoke both

### 3. Testability
- Mock `AgentInvocation` in tests
- Test agents independently
- Integration tests verify A2A communication

### 4. Scalability
- Agents can be distributed across services
- Use A2A protocol for cross-service communication
- Horizontal scaling of individual agents

## Build Status

✅ **Build successful** - All files compile without errors

## Testing

To test the A2A implementation:

1. Set API token:
   ```bash
   # Token is already set in application.yml
   ```

2. Run the application:
   ```bash
   ./gradlew bootRun
   ```

3. Test appointment scheduling (triggers A2A):
   ```
   schedule appointment for Stacy Morris on tomorrow 9AM
   ```

4. Test patient creation:
   ```
   create patient John Doe, DOB 11/08/1990, phone 555-1234, 
   address 123 Main St, Phoenix AZ 85001
   ```

5. Test patient update:
   ```
   update patient Stacy Morris with new phone 555-9999
   ```

## Next Steps

The A2A architecture is now ready for:
- Additional patient operations (delete, search by criteria)
- More complex agent workflows
- Integration with other agents (billing, insurance verification)
- Distributed agent deployment
