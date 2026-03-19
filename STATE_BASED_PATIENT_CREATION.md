# State-Based Patient Creation - Implementation Summary

## Overview

Implemented Embabel's `@State` pattern to enable multi-turn conversational patient creation. The agent now maintains context across user interactions, remembering collected data and progressively asking for missing required fields.

## Problem Solved

**Before:**
```
User: "create patient Nguyen Van An"
Agent: "I need: DOB, phone, address, city, state, zip"
User: "DOB 11/08/1990, phone 555-1234..."
Agent: "No patient found" ← treats as NEW request, no memory
```

**After:**
```
User: "create patient Nguyen Van An"
Agent: "I need: DOB, phone, address, city, state, zip"
User: "DOB 11/08/1990, phone 555-1234..."
Agent: "I still need: address, city, state, zip" ← remembers previous data
User: "address 123 Main St, Phoenix AZ 85001"
Agent: "Patient created successfully!" ← creates with all collected data
```

## Architecture

### State Flow

```
UserInput: "create patient..."
    ↓
createPatient() → PatientCreationStage
    ↓
    ├─→ If complete → CreatePatient state
    │       ↓
    │   createPatientRecord() → Done
    │       ↓
    │   complete() → confirmation message
    │
    └─→ If incomplete → CollectingPatientData state
            ↓
        collectMoreFields() ← LOOPS if still missing
            ↓
            ├─→ If complete → CreatePatient state
            └─→ If incomplete → CollectingPatientData state (loop)
```

### State Classes

#### 1. PatientCreationStage (Sealed Interface)
```kotlin
@State
sealed interface PatientCreationStage
```
- Base interface for all patient creation states
- `@State` annotation inherited by all implementations
- Enables polymorphic state transitions

#### 2. CollectingPatientData (State)
```kotlin
@State
data class CollectingPatientData(
    val patientData: PatientFormData,
    val missingFields: List<String>
) : PatientCreationStage
```

**Actions:**
- `collectMoreFields(UserInput)` - Parse new input, merge with existing data, validate
  - Returns `CreatePatient` if all fields collected
  - Returns `CollectingPatientData` if still missing fields (loop)

**Key Features:**
- Holds partial patient data
- Tracks which fields are still missing
- Merges new user input with existing data
- Can loop indefinitely until all fields collected

#### 3. CreatePatient (State)
```kotlin
@State
data class CreatePatient(
    val patientData: PatientFormData
) : PatientCreationStage
```

**Actions:**
- `createPatientRecord(AppSensePatientService)` - Call API to create patient
  - Returns `Done` with success message

**Key Features:**
- Only entered when all required fields are present
- Makes actual API call to create patient
- Transitions to Done state

#### 4. Done (State)
```kotlin
@State
data class Done(
    val success: Boolean,
    val message: String
) : PatientCreationStage
```

**Actions:**
- `complete()` - Return final confirmation message

**Key Features:**
- Terminal state
- Returns confirmation to user
- Achieves goal, ending the flow

## How State Pattern Works

### Key Mechanisms

1. **Blackboard Clearing**
   - When action returns `@State` object, blackboard is cleared
   - Only the state object remains
   - All other objects (including `hasRun` flags) are removed

2. **Re-planning**
   - Planner considers only actions available in the new state
   - Actions defined in the state class become available
   - Previous actions are no longer considered

3. **Looping**
   - Because blackboard is cleared, `hasRun` tracking resets
   - Same action can be called multiple times
   - Enables `CollectingPatientData.collectMoreFields()` to loop

### Execution Example

**Turn 1:**
```
User: "create patient Nguyen Van An"
→ createPatient(userInput) called
→ Parses: firstName="Nguyen Van", lastName="An"
→ Validates: missing DOB, phone, address, city, state, zip
→ Returns: CollectingPatientData(partial data, missing fields list)
→ Blackboard cleared, only CollectingPatientData remains
→ Planner sees: collectMoreFields() action available
→ Calls: collectMoreFields() to prompt user
→ User sees: "I need: DOB, phone, address, city, state, zip"
```

**Turn 2:**
```
User: "DOB 11/08/1990, phone 555-1234"
→ Agent starts with CollectingPatientData on blackboard
→ Planner calls: collectMoreFields(userInput)
→ Parses: dob="11/08/1990", phoneNumber="555-1234"
→ Merges with existing: firstName="Nguyen Van", lastName="An"
→ Validates: still missing address, city, state, zip
→ Returns: CollectingPatientData(merged data, updated missing list)
→ Blackboard cleared again, new CollectingPatientData remains
→ User sees: "I still need: address, city, state, zip"
```

**Turn 3:**
```
User: "address 123 Main St, Phoenix AZ 85001"
→ Agent starts with CollectingPatientData on blackboard
→ Planner calls: collectMoreFields(userInput)
→ Parses: streetAddress="123 Main St", city="Phoenix", state="Arizona", zipCode="85001"
→ Merges with existing data
→ Validates: all required fields present!
→ Returns: CreatePatient(complete data)
→ Blackboard cleared, only CreatePatient remains
→ Planner sees: createPatientRecord() action available
→ Calls: createPatientRecord(service)
→ API call to create patient
→ Returns: Done(success, message)
→ Planner calls: complete()
→ User sees: "Patient created successfully!"
```

## Files Created/Modified

### Created: PatientCreationStates.kt
**Location:** `src/main/kotlin/dev/stevennguyen/newbieclaw/domain/appsense/PatientCreationStates.kt`

**Contents:**
- `PatientCreationStage` sealed interface with `@State`
- `CollectingPatientData` state with `collectMoreFields()` action
- `CreatePatient` state with `createPatientRecord()` action
- `Done` state with `complete()` action

### Modified: AppSensePatientAgent.kt
**Changes:**
- `createPatient()` now returns `PatientCreationStage` instead of `String`
- Removed direct API call logic (moved to `CreatePatient` state)
- Added state transition logic based on validation

**Before:**
```kotlin
fun createPatient(userInput: UserInput, context: OperationContext): String {
    val patientData = parsePatientInput(userInput, context)
    val validation = validateRequiredFields(patientData)
    
    if (!validation.isValid) {
        return validation.message ?: "Missing required fields"
    }
    
    // Create patient via API...
    return confirmationMessage
}
```

**After:**
```kotlin
fun createPatient(userInput: UserInput, context: OperationContext): PatientCreationStage {
    val patientData = parsePatientInput(userInput, context)
    val validation = validateRequiredFields(patientData)
    
    return if (validation.isValid) {
        CreatePatient(patientData)
    } else {
        CollectingPatientData(patientData, validation.missingFields)
    }
}
```

## Benefits

### 1. True Conversation Memory
- Agent remembers what was already collected
- No need to repeat information
- Natural multi-turn conversation

### 2. Flexible User Input
- User can provide all fields at once (single-turn)
- Or provide fields incrementally (multi-turn)
- Agent adapts to both patterns

### 3. Progressive Data Collection
- Agent asks for specific missing fields
- User provides fields in any order
- Agent merges new data with existing

### 4. Clear State Transitions
- Explicit states make flow easy to understand
- Each state has specific responsibilities
- Debugging is straightforward

### 5. Looping Support
- `CollectingPatientData` can loop indefinitely
- No limit on number of turns
- User can take as many turns as needed

## Usage Examples

### Example 1: All Fields at Once (Single-Turn)
```
User: "create patient John Doe, DOB 11/08/1990, phone 555-1234, 
       address 123 Main St, Phoenix AZ 85001"

Flow:
1. createPatient() → CreatePatient (skip collecting)
2. createPatientRecord() → Done
3. complete() → "Patient created successfully!"

Result: Works exactly as before, no extra turns needed
```

### Example 2: Incremental Collection (Multi-Turn)
```
Turn 1:
User: "create patient Nguyen Van An"
Agent: "I need: Date of Birth, Phone Number, Street Address, City, State, Zip Code"

Turn 2:
User: "DOB 11/08/1990"
Agent: "I still need: Phone Number, Street Address, City, State, Zip Code"

Turn 3:
User: "phone 0794123412"
Agent: "I still need: Street Address, City, State, Zip Code"

Turn 4:
User: "address Address 1, Ho Chi Minh, Arizona 140102"
Agent: "Patient created successfully!"

Flow:
1. createPatient() → CollectingPatientData
2. collectMoreFields() → CollectingPatientData (loop)
3. collectMoreFields() → CollectingPatientData (loop)
4. collectMoreFields() → CreatePatient
5. createPatientRecord() → Done
6. complete() → confirmation
```

### Example 3: Multiple Fields Per Turn
```
Turn 1:
User: "create patient Mary Smith"
Agent: "I need: Date of Birth, Phone Number, Street Address, City, State, Zip Code"

Turn 2:
User: "DOB 05/15/1985, phone 555-9999, email mary@example.com"
Agent: "I still need: Street Address, City, State, Zip Code"

Turn 3:
User: "456 Oak Ave, Seattle WA 98101"
Agent: "Patient created successfully!"

Flow: Agent merges multiple fields per turn
```

## Compatibility

### Backward Compatible
- If user provides all fields in first message, works as before
- Single-turn flow still supported
- No breaking changes to existing functionality

### Other Actions Unchanged
- `updatePatient()` - Still single-turn (can be converted later)
- `searchPatient()` - Still A2A-only
- A2A communication - Unaffected by state changes

## Testing

### Build Status
✅ **Build successful** - All files compile without errors

### Test Scenarios

**Test 1: Multi-turn patient creation**
```bash
./gradlew bootRun
```
```
create patient Nguyen Van An
# Agent asks for missing fields
DOB 11/08/1990, phone 0794123412
# Agent asks for remaining fields
address Address 1, Ho Chi Minh, Arizona 140102
# Patient created
```

**Test 2: Single-turn patient creation**
```
create patient John Doe, DOB 11/08/1990, phone 555-1234, 
address 123 Main St, Phoenix AZ 85001
# Patient created immediately
```

**Test 3: A2A still works**
```
schedule appointment for Stacy Morris on tomorrow 9AM
# Scheduler invokes Patient Agent via A2A
# Appointment scheduled
```

## Technical Details

### State Inheritance
- `@State` annotation is inherited through class hierarchy
- Only need to annotate `PatientCreationStage` interface
- All implementing classes automatically become state types

### Action Discovery
- Framework discovers all actions in state classes
- Actions become available when state is on blackboard
- Previous state's actions are no longer available

### Type Safety
- State transitions are type-safe
- Compiler ensures correct state types
- No runtime type errors

### Blackboard Management
- Embabel automatically manages blackboard
- Clears blackboard when state is returned
- Binds state object as `it` on blackboard

## Next Steps

The state-based patient creation is now ready to use. The agent will:
1. Remember collected data across turns
2. Ask for specific missing fields
3. Merge new input with existing data
4. Create patient when all fields are collected

This provides a natural conversational experience for patient creation while maintaining backward compatibility with single-turn creation.
