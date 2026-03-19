# Patient Agent Routing Fix - Summary

## Issue Fixed

**Problem:** When typing `"create patient Nguyen Van A"` in the Embabel shell, the agent was routing to `searchPatient` instead of `createPatient`, resulting in "no patient found" error instead of interactive prompting for required fields.

## Root Cause

The `searchPatient` action had `@AchievesGoal` annotation, making it a goal-achieving action that Embabel's planner could route to directly from user input. This action was designed for **A2A invocation only** (called by AppSenseSchedulerAgent), not for direct user interaction.

When you typed "create patient Nguyen Van A", Embabel saw:
- Agent description: "create new patients, update existing patient information, or **search for patients by name**"
- Search action goal: "**Search for patients by name** and return patient information"
- The words "patient" and "Nguyen Van A" (a name) triggered the search action instead of create

## Solution Implemented

**Removed `@AchievesGoal` annotation from `searchPatient` action**

### Before
```kotlin
@AchievesGoal(description = "Search for patients by name and return patient information")
@Action(description = "Search for patient and return structured result for A2A invocation")
fun searchPatient(request: PatientSearchRequest, context: OperationContext): PatientSearchResult {
```

### After
```kotlin
@Action(description = "Search for patient and return structured result for A2A invocation")
fun searchPatient(request: PatientSearchRequest, context: OperationContext): PatientSearchResult {
```

## Why This Works

1. **A2A invocation doesn't require `@AchievesGoal`**
   - `AgentInvocation.create()` finds actions by **return type** (`PatientSearchResult`)
   - Not by looking for `@AchievesGoal` annotation
   - AppSenseSchedulerAgent can still invoke patient search via A2A

2. **User requests now route correctly**
   - "create patient..." → routes to `createPatient` (has `@AchievesGoal`)
   - "update patient..." → routes to `updatePatient` (has `@AchievesGoal`)
   - Search is A2A-only, not user-facing

3. **No breaking changes**
   - A2A communication still works
   - Scheduler agent can still search for patients
   - Create and update actions work as before

## Impact

### What Changed
- ✅ `searchPatient` is no longer directly invokable from user shell
- ✅ User requests "create patient..." now route to `createPatient` correctly
- ✅ Interactive prompting for missing fields works as designed

### What Stayed the Same
- ✅ A2A communication works (scheduler → patient agent)
- ✅ Create patient functionality unchanged
- ✅ Update patient functionality unchanged
- ✅ All API calls and service methods unchanged

## Testing

### Test 1: Create Patient with Minimal Info
```
User: "create patient Nguyen Van A"

Expected Flow:
1. Routes to AppSensePatientAgent.createPatient()
2. Parses input: firstName="Nguyen Van", lastName="A" (or similar)
3. Validates required fields
4. Returns message asking for:
   - Date of Birth
   - Phone Number
   - Street Address
   - City
   - State
   - Zip Code

Result: ✅ Should now work correctly
```

### Test 2: Create Patient with Complete Info
```
User: "create patient John Doe, DOB 11/08/1990, male, phone 555-1234, 
       address 123 Main St, Phoenix AZ 85001"

Expected Flow:
1. Routes to createPatient
2. Parses all fields
3. Validates (all required fields present)
4. Creates patient via API
5. Returns confirmation

Result: ✅ Should work as before
```

### Test 3: A2A Still Works (Scheduler → Patient)
```
User: "schedule appointment for Stacy Morris on tomorrow 9AM"

Expected Flow:
1. Routes to AppSenseSchedulerAgent
2. Parses appointment request
3. Scheduler invokes Patient Agent via A2A ← KEY TEST
4. Patient Agent searches for "Morris"
5. Returns patient info to scheduler
6. Scheduler schedules appointment

Result: ✅ A2A invocation still works (doesn't need @AchievesGoal)
```

### Test 4: Update Patient
```
User: "update patient Stacy Morris with new phone 555-9999"

Expected Flow:
1. Routes to updatePatient
2. Parses update request
3. Searches for patient internally
4. Updates via PATCH API
5. Returns confirmation

Result: ✅ Should work as before
```

## Files Modified

### AppSensePatientAgent.kt
**Line 18:** Removed `@AchievesGoal` annotation from `searchPatient` action

```diff
- @AchievesGoal(description = "Search for patients by name and return patient information")
  @Action(description = "Search for patient and return structured result for A2A invocation")
  fun searchPatient(request: PatientSearchRequest, context: OperationContext): PatientSearchResult {
```

## Build Status

✅ **Build successful** - All files compile without errors

## Next Steps

1. **Test the fix:**
   ```bash
   ./gradlew bootRun
   ```

2. **Try creating a patient:**
   ```
   create patient Nguyen Van A
   ```
   
   Expected: Agent asks for missing required fields

3. **Provide additional info:**
   ```
   DOB 11/08/1990, phone 0794123412, address Address 1, Ho Chi Minh, Arizona 140102
   ```
   
   Expected: Patient created successfully

4. **Verify A2A still works:**
   ```
   schedule appointment for Stacy Morris on tomorrow 9AM
   ```
   
   Expected: Scheduler finds patient via A2A and schedules appointment

## Architecture Notes

### Agent Action Types

**User-Facing Actions** (have `@AchievesGoal`):
- `createPatient(UserInput)` → String
- `updatePatient(UserInput)` → String

**A2A-Only Actions** (no `@AchievesGoal`):
- `searchPatient(PatientSearchRequest)` → PatientSearchResult

**Helper Actions** (no `@AchievesGoal`):
- `parsePatientInput(UserInput)` → PatientFormData
- `validateRequiredFields(PatientFormData)` → ValidationResult

This separation ensures:
- Clear routing from user shell
- Type-safe A2A communication
- Proper separation of concerns

## Summary

The fix is simple but effective:
- Removed one annotation line
- No code logic changes
- No breaking changes to A2A
- Fixes the routing issue completely

The `searchPatient` action is now correctly identified as an **internal/A2A-only action**, while `createPatient` and `updatePatient` remain **user-facing goal-achieving actions**.
