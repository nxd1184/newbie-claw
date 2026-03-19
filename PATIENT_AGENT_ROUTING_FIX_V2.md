# Patient Agent Routing Fix V2 - Goal Description Update

## Issue After First Fix

After removing `@AchievesGoal` from `searchPatient`, the routing issue persisted. The logs showed:

```
00:07:56.389 [main] INFO  Embabel - [trusting_snyder] formulated plan:
    dev.stevennguyen.newbieclaw.agent.AppSensePatientAgent.updatePatient
    goal: dev.stevennguyen.newbieclaw.agent.AppSensePatientAgent.updatePatient
```

**Problem:** When user typed `"Please create new patient, his name is Nguyen Van An"`, Embabel's GOAP planner chose `updatePatient` instead of `createPatient`.

## Root Cause

Both `createPatient` and `updatePatient` actions have:
- **Same input type**: `UserInput`
- **Same output type**: `String`
- **Same preconditions**: `it:com.embabel.agent.domain.io.UserInput: TRUE`
- **Same postconditions**: `it:java.lang.String: TRUE`

The GOAP planner couldn't distinguish between them based on signatures alone. The goal descriptions were too similar:
- Create: "Create a new patient in the system"
- Update: "Update existing patient information"

Both descriptions mention "patient" but don't clearly indicate the **intent keywords** (create/new vs update/modify/change).

## Solution V2

**Made goal descriptions more specific with clear intent keywords:**

### Before
```kotlin
@AchievesGoal(description = "Create a new patient in the system")
@Action(description = "Create patient after validation")
fun createPatient(userInput: UserInput, context: OperationContext): String

@AchievesGoal(description = "Update existing patient information")
@Action(description = "Search for patient by name, then update their information")
fun updatePatient(userInput: UserInput, context: OperationContext): String
```

### After
```kotlin
@AchievesGoal(description = "Create a new patient record when user wants to add or register a new patient to the system")
@Action(description = "Create patient after validation")
fun createPatient(userInput: UserInput, context: OperationContext): String

@AchievesGoal(description = "Modify or change existing patient information when user wants to update or edit a patient's details")
@Action(description = "Search for patient by name, then update their information")
fun updatePatient(userInput: UserInput, context: OperationContext): String
```

## Key Changes

### Create Patient Goal
**Added keywords:**
- "add" - matches "Please **create** new patient"
- "register" - alternative intent keyword
- "new patient" - emphasizes this is for NEW records
- "to the system" - clarifies this adds to database

**Intent matching:**
- "create new patient" → matches "Create a **new** patient"
- "add patient" → matches "**add** or register"
- "register patient" → matches "add or **register**"

### Update Patient Goal
**Added keywords:**
- "Modify" - primary action verb
- "change" - alternative action verb
- "update" - explicit keyword
- "edit" - another alternative
- "existing patient" - emphasizes this is for EXISTING records
- "patient's details" - clarifies this modifies data

**Intent matching:**
- "update patient" → matches "**update** or edit"
- "modify patient" → matches "**Modify** or change"
- "change patient info" → matches "modify or **change**"
- "edit patient" → matches "update or **edit**"

## Why This Works

Embabel's GOAP planner uses the goal descriptions to match user intent. By adding more specific keywords:

1. **"create new patient"** strongly matches the create goal with keywords: "Create", "new", "add", "register"
2. **"update patient"** strongly matches the update goal with keywords: "Modify", "change", "update", "edit", "existing"
3. The planner can now distinguish between the two based on the user's language

## Testing

### Test 1: Create Patient
```
Input: "Please create new patient, his name is Nguyen Van An"

Expected:
- Routes to createPatient (not updatePatient)
- Parses name
- Asks for missing required fields

Keywords matched: "create", "new"
```

### Test 2: Create with "add"
```
Input: "add patient John Doe"

Expected:
- Routes to createPatient

Keywords matched: "add"
```

### Test 3: Create with "register"
```
Input: "register new patient Mary Smith"

Expected:
- Routes to createPatient

Keywords matched: "register", "new"
```

### Test 4: Update Patient
```
Input: "update patient Stacy Morris with new phone 555-9999"

Expected:
- Routes to updatePatient
- Searches for patient
- Updates information

Keywords matched: "update"
```

### Test 5: Update with "modify"
```
Input: "modify patient John Doe's address"

Expected:
- Routes to updatePatient

Keywords matched: "modify"
```

### Test 6: Update with "change"
```
Input: "change patient email for Jane Smith"

Expected:
- Routes to updatePatient

Keywords matched: "change"
```

## Files Modified

### AppSensePatientAgent.kt

**Line 188:** Updated createPatient goal description
```diff
- @AchievesGoal(description = "Create a new patient in the system")
+ @AchievesGoal(description = "Create a new patient record when user wants to add or register a new patient to the system")
```

**Line 250:** Updated updatePatient goal description
```diff
- @AchievesGoal(description = "Update existing patient information")
+ @AchievesGoal(description = "Modify or change existing patient information when user wants to update or edit a patient's details")
```

## Build Status

✅ **Build successful** - All files compile without errors

## Summary of All Fixes

### Fix 1: Removed `@AchievesGoal` from searchPatient
- Made search action A2A-only
- Prevented direct user invocation of search

### Fix 2: Enhanced Goal Descriptions
- Added specific intent keywords to create and update goals
- Helps GOAP planner distinguish between similar actions
- Matches user's natural language more accurately

## Next Steps

1. **Restart the application:**
   ```bash
   ./gradlew bootRun
   ```

2. **Test create patient:**
   ```
   Please create new patient, his name is Nguyen Van An
   ```
   
   **Expected:** Routes to `createPatient`, asks for missing fields

3. **Provide additional info when prompted:**
   ```
   DOB 11/08/1990, phone 0794123412, address Address 1, Ho Chi Minh, Arizona 140102
   ```
   
   **Expected:** Creates patient successfully

The combination of both fixes should now correctly route create requests to the create action.
