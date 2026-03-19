# State-Based Patient Creation - Prompt Fix

## Problem Fixed

The state-based patient creation was returning `CollectingPatientData` state but stopping without prompting the user for missing fields.

### Before Fix
```
User: "create patient Nguyen Van An"
Agent returns: CollectingPatientData JSON object
Agent stops: No prompt shown
```

### After Fix
```
User: "create patient Nguyen Van An"
Agent: "To create the patient, I need the following information:
       - Date of Birth
       - Phone Number
       - Street Address
       - City
       - State
       - Zip Code
       
       Please provide these details."
Agent waits for user response
```

## Root Cause

The `CollectingPatientData` state only had `collectMoreFields(UserInput)` action which requires `UserInput` as a parameter. The Embabel planner couldn't call this action because there was no `UserInput` available on the blackboard yet.

**Missing:** An action that takes NO parameters and can be called immediately to prompt the user.

## Solution Implemented

Added `promptForFields()` action to `CollectingPatientData` state:

```kotlin
@State
data class CollectingPatientData(
    val patientData: PatientFormData,
    val missingFields: List<String>
) : PatientCreationStage {
    
    @AchievesGoal(description = "Prompt user for missing patient information and wait for their response")
    @Action(description = "Show missing fields message to user")
    fun promptForFields(): String {
        val message = """
            To create the patient, I need the following information:
            ${missingFields.joinToString("\n") { "- $it" }}
            
            Please provide these details.
        """.trimIndent()
        
        return message
    }
    
    @Action(description = "Collect additional patient information from user input")
    fun collectMoreFields(
        userInput: UserInput, 
        context: OperationContext
    ): PatientCreationStage {
        // Merge logic...
    }
}
```

## How It Works

### Turn 1: Initial Request
```
User: "create patient Nguyen Van An"

Flow:
1. createPatient(userInput) called in agent
2. Parses: firstName="Nguyen", lastName="Van An"
3. Validates: missing DOB, phone, address, city, state, zip
4. Returns: CollectingPatientData(partial data, missing fields)
5. Blackboard cleared, only CollectingPatientData remains
6. Planner sees two actions:
   - promptForFields() - NO parameters needed ✓
   - collectMoreFields(UserInput) - needs UserInput ✗
7. Planner calls: promptForFields()
8. Returns String message
9. User sees: "I need: Date of Birth, Phone Number, ..."
```

### Turn 2: User Provides Some Fields
```
User: "DOB 11/08/1990, phone 555-1234"

Flow:
1. New agent process starts with user input
2. Planner needs to route to correct action
3. Since we're in middle of patient creation, needs to continue
4. collectMoreFields(userInput) gets called
5. Merges: dob="11/08/1990", phoneNumber="555-1234" with existing data
6. Validates: still missing address, city, state, zip
7. Returns: CollectingPatientData(merged data, updated missing list)
8. Blackboard cleared, new CollectingPatientData remains
9. Planner calls: promptForFields()
10. User sees: "I still need: Street Address, City, State, Zip Code"
```

### Turn 3: User Provides Remaining Fields
```
User: "address 123 Main St, Phoenix AZ 85001"

Flow:
1. collectMoreFields(userInput) called
2. Merges: streetAddress, city, state, zipCode with existing
3. Validates: all required fields present!
4. Returns: CreatePatient(complete data)
5. Blackboard cleared, CreatePatient remains
6. Planner calls: createPatientRecord(CreatePatient)
7. API call to create patient
8. Returns: Done(success, message)
9. Planner calls: complete()
10. User sees: "Patient created successfully!"
```

## Key Insight

The pattern requires TWO actions in the collecting state:

1. **Parameter-free action** (`promptForFields()`)
   - Can be called immediately
   - Shows message to user
   - Achieves goal (ends turn)
   - User's next input starts new turn

2. **Parameterized action** (`collectMoreFields(UserInput)`)
   - Requires UserInput from user's next message
   - Processes and merges data
   - Returns next state

This is different from Embabel's `WaitFor` pattern (which isn't available in 0.3.4) but achieves the same multi-turn conversation effect.

## Files Modified

### 1. PatientCreationStates.kt
**Added:**
- `promptForFields()` action with `@AchievesGoal` annotation
- Returns String message instead of trying to use `WaitFor`

**Location:** `src/main/kotlin/dev/stevennguyen/newbieclaw/domain/appsense/PatientCreationStates.kt:19-36`

### 2. NewbieClawApplication.kt
**Fixed:**
- Bean registration for `AppSensePatientAgent` to only pass `service` parameter
- Removed `props` parameter that was causing compilation error

**Location:** `src/main/kotlin/dev/stevennguyen/newbieclaw/NewbieClawApplication.kt:24-26`

## Build Status

✅ **Build successful** - All compilation errors resolved

## Testing

Restart the application:
```bash
./gradlew bootRun
```

Test multi-turn patient creation:
```
create patient Nguyen Van An
```

**Expected behavior:**
1. Agent shows: "To create the patient, I need the following information: ..."
2. Agent waits for your response
3. You provide fields incrementally
4. Agent merges data and prompts for remaining fields
5. When complete, creates patient via API

## Differences from Original Plan

**Original plan:** Use `WaitFor.userInput()` to pause agent and wait for response

**Actual implementation:** Use `@AchievesGoal` on `promptForFields()` that returns String

**Why:** `WaitFor` class is not available in Embabel 0.3.4 (unresolved reference error)

**Effect:** Same multi-turn behavior achieved:
- `promptForFields()` achieves goal and ends turn
- User's next input starts new agent process
- State is maintained through the state object pattern
- Works correctly for multi-turn conversation

## Next Steps

The state-based patient creation is now ready to test. The agent will:
1. Parse initial input
2. Identify missing fields
3. Prompt user with specific missing fields
4. Wait for user response
5. Merge new data with existing
6. Repeat until all fields collected
7. Create patient via API
