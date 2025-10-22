# CI/CD Failure Investigation Report

**Date**: October 15, 2025  
**Branch**: `setup-cicd`  
**Last Run**: [Build #18525434909](https://github.com/Tiro-health/snowstorm/actions/runs/18525434909)  
**Status**: ❌ FAILED  
**Test Results**: 839 tests, 7 failures, 14 errors  
**Duration**: ~20 minutes

---

## Executive Summary

The CI/CD pipeline is currently failing with **21 total test issues** (7 failures + 14 errors). The issues fall into three main categories:

1. **Bean Initialization Failure** (13 errors) - Critical
2. **FHIR Test Assertion Failures** (7 failures) - Test logic issues
3. **JMS/ActiveMQ Shutdown Race Condition** (1 error) - Infrastructure issue

**Progress Since Last Investigation:**
- ✅ Bean definition conflicts have been resolved
- ✅ JMS broker configuration has been stabilized for most tests
- ✅ Traceability scope management has been fixed
- ❌ NEW CRITICAL ISSUE: ECLQueryServiceFilterTestConfig bean initialization failure
- ⚠️ JMS shutdown race condition persists during test teardown

---

## Current Failure Categories

### 1. ❗ CRITICAL: Bean Initialization Failure (13 Errors)

**Affected Test Class**: `org.snomed.snowstorm.ecl.ECLQueryServiceFilterTest`  
**Impact**: All 13 tests in this class cannot run  
**Status**: NEW ISSUE (introduced by recent commit 0f1fe2bd)

#### Root Cause Analysis

**Error Chain**:
```
Error creating bean with name 'ECLQueryServiceFilterTestConfig': Invocation of init method failed
  ↓
Caused by: java.lang.NullPointerException: 
  Cannot invoke "io.kaicode.elasticvc.api.BranchService.findBranchOrThrow(String, boolean)" 
  because "this.branchService" is null
```

**What Happened**:
The `@PostConstruct` method `beforeAll()` in `ECLQueryServiceFilterTestConfig` attempts to use `branchService` before Spring has injected all dependencies. This is because:

1. **Commit 0f1fe2bd** added `@TestConfiguration` annotation to `ECLQueryServiceFilterTestConfig`
2. `@TestConfiguration` changes the bean initialization order
3. The `@PostConstruct` method runs **before** the `@Autowired` fields in parent class `ECLQueryTestConfig` are injected
4. Result: `branchService` is null when `beforeAll()` tries to use it

**Why This Is Critical**:
- Blocks 13 tests from running at all
- Introduced by recent "fix" attempt (commit 0f1fe2bd: "Add @TestConfiguration to ECLQueryServiceFilterTestConfig to fix bean initialization")
- The fix actually made things worse by changing initialization order

#### Failed Tests:
1. `ECLQueryServiceFilterTest.historySupplement`
2. `ECLQueryServiceFilterTest.testDefinitionStatusFilter`
3. `ECLQueryServiceFilterTest.testAcceptabilityFilters`
4. `ECLQueryServiceFilterTest.testDescriptionTypeFilters`
5. `ECLQueryServiceFilterTest.testEffectiveTimeFilter`
6. `ECLQueryServiceFilterTest.testTermFilters`
7. `ECLQueryServiceFilterTest.testLanguageFilters`
8. `ECLQueryServiceFilterTest.testMemberActiveFilter`
9. `ECLQueryServiceFilterTest.testMemberFieldFilter`
10. `ECLQueryServiceFilterTest.testMemberSelectFields`
11. `ECLQueryServiceFilterTest.testModuleFilter`
12. `ECLQueryServiceFilterTest.testNotOverEagerCaching`
13. `ECLQueryServiceFilterTest.testPreferredInFilter`

#### Recommended Solution:

**Option A: Remove @TestConfiguration annotation** (Recommended)
- Revert commit 0f1fe2bd
- Remove `@TestConfiguration` from `ECLQueryServiceFilterTestConfig`
- The class already extends `ECLQueryTestConfig` which extends `TestConfig` (a proper `@SpringBootApplication`)
- No additional annotation is needed

**Option B: Change initialization method**
- Replace `@PostConstruct` with a manual initialization call from the test class
- Use `@BeforeAll` or `@BeforeEach` in the test class instead
- More invasive change

**Option C: Make initialization defensive**
- Add null checks in `beforeAll()` method
- But this doesn't solve the root cause - services won't be available

---

### 2. ⚠️ FHIR Test Assertion Failures (7 Failures)

These are test logic issues where the actual behavior doesn't match test expectations. These are **NOT** blocking other tests and may indicate either:
- Test expectations need updating
- Business logic needs fixing
- Test data setup issues

#### 2.1 FHIRCodeSystemProviderInstancesTest (2 failures)

**Test**: `testCodeSystemRecovery` and `testCodeSystemRecoverySorted`

**Issue**: Expected 4 code systems but got 5
```
Expected: <4> but was: <5>
Code systems found:
- device-status-reason
- hl7.org-fhir-sid-icd-10
- sct_11000003104_EXP
- sct_900000000000207008_20190131
- sct_1234000008_20190731  ← Extra code system
```

**Analysis**: An additional code system is being created/discovered that shouldn't be there. This could be:
- Test isolation issue (leftover from previous test)
- Code system creation logic changed
- Test setup creates extra code system

---

#### 2.2 FHIRCodeSystemServiceTest (1 failure)

**Test**: `createSupplementNotSnomed`

**Issue**: Wrong error code returned
```
Expected: <NOTSUPPORTED> but was: <INVARIANT>
```

**Analysis**: When creating a supplement for a non-SNOMED code system, the error type has changed. Either:
- Validation logic was updated
- Test expectation needs updating

---

#### 2.3 FHIRLoadPackageServiceTest (1 failure)

**Test**: `uploadPackageResources`

**Issue**: Upload operation failed
```
Expected: <true> but was: <false>
```

**Analysis**: Package resource upload is failing. Possible causes:
- File path issues in test environment
- Permission issues
- Missing test resource files
- Service logic change

---

#### 2.4 FHIRValueSetProviderExpandEclTest (1 failure)

**Test**: `testECLWithDesignationUseContextExpansion`

**Issue**: Wrong number of designations returned
```
Expected: <3> but was: <1>
```

**Analysis**: ECL expansion with designation context is not returning all expected results. Possible causes:
- ECL query logic changed
- Test data setup incomplete
- Designation filtering logic issue

---

#### 2.5 FHIRValueSetProviderValidateCodeEclTest (2 failures)

**Test 1**: `testECLWithSpecificCodingVersion`

**Issue**: Wrong HTTP status code
```
Expected: <400> but was: <200>
Status 200 with body showing: "result": true
```

**Analysis**: Validation should reject specific version but is accepting it. Version validation logic may have changed.

**Test 2**: `testImplicitValidate_Display`

**Issue**: Error message format changed
```
Expected: "The code '257751006' was found in the ValueSet, however the display 
          'Baked potato' did not match any designations."
Actual:   "Wrong Display Name 'Baked potato' for http://snomed.info/sct#257751006. 
          Valid display is one of 4 choices..."
```

**Analysis**: Error message format was improved to be more descriptive. Test assertion needs updating.

---

### 3. 🔥 JMS/ActiveMQ Shutdown Race Condition (Infrastructure Issue)

**Status**: Intermittent, occurs during test cleanup

**Error**:
```
java.lang.IllegalStateException: Shutdown in progress
  at java.base/java.lang.ApplicationShutdownHooks.add(ApplicationShutdownHooks.java:66)
  at org.apache.activemq.broker.BrokerService.addShutdownHook(BrokerService.java:2529)
```

**What Happens**:
1. Tests complete and JVM begins shutdown
2. Multiple Spring contexts start shutting down in parallel
3. Some contexts still have JMS listeners trying to reconnect
4. These listeners attempt to create new ActiveMQ brokers
5. Brokers try to register shutdown hooks while JVM is already shutting down
6. Result: Harmless but noisy errors during cleanup

**Impact**:
- Does NOT cause test failures
- Creates log noise making real issues harder to find
- Indicates suboptimal test cleanup
- Adds ~5 seconds to test execution during cleanup phase

**Why It Persists**:
- Multiple test contexts sharing JMS infrastructure
- Async JMS listeners continue running during shutdown
- ActiveMQ's default behavior is to register shutdown hooks

**Observed Behavior**:
```
10:32:26 Multiple JMS listeners logging:
  "Could not refresh JMS Connection for destination 'test-snowstorm.traceability' 
   - retrying using FixedBackOff{interval=5000, currentAttempts=1, maxAttempts=unlimited}"
```

---

## Test Execution Summary

| Category | Count | Status |
|----------|-------|--------|
| Total Tests | 839 | ❌ |
| Passed | 818 | ✅ |
| Failed | 7 | ❌ |
| Errors | 14 | ❌ |
| Skipped | 0 | - |
| Success Rate | 97.5% | - |

---

## Historical Context

### Previous Issues (Resolved)
✅ Bean definition conflict (79 FHIR tests) - **FIXED** in commit 9e96eb19  
✅ JMS broker configuration issues - **FIXED** in commit 2c752204  
✅ Traceability scope management - **FIXED** in commit 34074438  

### Recent Changes That Introduced New Issues
❌ Commit 0f1fe2bd: "Add @TestConfiguration to ECLQueryServiceFilterTestConfig to fix bean initialization"
- **Intended**: Fix bean initialization
- **Result**: Broke 13 tests by changing initialization order
- **Action**: Should be reverted

---

## Detailed Breakdown by Severity

### Priority 1: MUST FIX (Blocking)
1. **ECLQueryServiceFilterTest bean initialization** - Blocks 13 tests

### Priority 2: SHOULD FIX (Test Quality)
2. **FHIR test assertion failures** - 7 tests with wrong expectations or logic issues

### Priority 3: NICE TO HAVE (Cleanup)
3. **JMS shutdown warnings** - Cosmetic but makes logs messy

---

## Recommended Fix Order

### 1. Fix ECLQueryServiceFilterTest (CRITICAL - 15 minutes)

**Action**: Revert problematic commit
```bash
# Option A: Revert the specific change
git revert 0f1fe2bd

# Option B: Manual fix
# Remove @TestConfiguration from ECLQueryServiceFilterTestConfig.java
```

**File to modify**: `src/test/java/org/snomed/snowstorm/ecl/ECLQueryServiceFilterTestConfig.java`

**Change**:
```java
// REMOVE this line:
@TestConfiguration

// Keep the rest:
public class ECLQueryServiceFilterTestConfig extends ECLQueryTestConfig {
    // ... existing code
}
```

**Expected Impact**: 13 tests should start running again

---

### 2. Fix FHIR Test Assertions (MEDIUM - 1-2 hours)

#### 2.1 Fix FHIRCodeSystemProviderInstancesTest
**Investigation needed**: Why is an extra code system being created?
- Check test isolation
- Review code system creation in test setup
- May need `@DirtiesContext` annotation

#### 2.2 Update FHIRCodeSystemServiceTest
**Action**: Update test expectation from `NOTSUPPORTED` to `INVARIANT` if new behavior is correct

#### 2.3 Fix FHIRLoadPackageServiceTest
**Investigation needed**: Why is upload failing?
- Check file paths in test resources
- Verify test files exist
- Check service logs for actual error

#### 2.4 Fix FHIRValueSetProviderExpandEclTest
**Investigation needed**: Why are designations missing?
- Review ECL expansion logic
- Check test data setup
- Verify designation use context filtering

#### 2.5 Update FHIRValueSetProviderValidateCodeEclTest
**Action**: Update test expectations to match new behavior:
- Test 1: Update expected status code if new behavior is correct
- Test 2: Update expected error message format

---

### 3. Cleanup JMS Shutdown Warnings (LOW - 30 minutes)

**Options**:

**Option A**: Disable JMS auto-reconnect during shutdown
```properties
# Add to application-test.properties
spring.jms.listener.auto-startup=false
```

**Option B**: Use @DirtiesContext more aggressively
```java
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
```

**Option C**: Custom JMS configuration for tests
- Disable shutdown hooks in test broker
- Set shorter reconnect timeouts
- Stop listeners before context shutdown

---

## Environment Details

### CI/CD Configuration
- **Platform**: GitHub Actions
- **Java**: 17 (Amazon Corretto)
- **Maven**: 3.x
- **Build Command**: `mvn clean package -B`
- **Docker**: Available (for Testcontainers)
- **Elasticsearch**: 8.11.1 (via Testcontainers)

### Test Infrastructure
- **Test Framework**: JUnit 5
- **Spring Boot**: 3.2.11
- **Testcontainers**: Elasticsearch container
- **JMS**: ActiveMQ 6.0.1 (embedded, in-memory)

---

## Risk Assessment

### If Not Fixed

**Immediate Impact**:
- 13 tests not running (gaps in test coverage)
- 7 tests giving false failures (masking real issues)
- CI/CD cannot be trusted for deployment decisions

**Long-term Impact**:
- Technical debt accumulation
- Decreased confidence in test suite
- Potential bugs reaching production
- More difficult to identify new test failures

---

## Success Criteria

After implementing fixes, expect:
- ✅ All 839 tests execute (no initialization failures)
- ✅ All 839 tests pass (no failures or errors)
- ✅ Clean test logs (minimal warnings during shutdown)
- ✅ Test execution time under 20 minutes
- ✅ Green build status in GitHub Actions

---

## Next Steps

1. **Immediate** (Today):
   - Revert commit 0f1fe2bd or remove `@TestConfiguration` annotation
   - Run tests locally to verify fix
   - Push to `setup-cicd` branch
   - Verify CI/CD run

2. **Short-term** (This Week):
   - Investigate and fix 7 FHIR test assertion failures
   - Document expected behavior for each test
   - Update test assertions or fix code as needed

3. **Medium-term** (Next Sprint):
   - Cleanup JMS shutdown warnings
   - Add test isolation improvements
   - Document test infrastructure setup

---

## Additional Notes

### Testing Locally
To reproduce issues locally:
```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=ECLQueryServiceFilterTest

# Run with debug logging
mvn test -Dtest=ECLQueryServiceFilterTest -X
```

### Useful Resources
- GitHub Actions logs: https://github.com/Tiro-health/snowstorm/actions/runs/18525434909
- Maven Surefire reports: `target/surefire-reports/`
- Test configuration: `src/test/resources/application-test.properties`

---

## Appendix: Complete Error List

### Errors (14)
1. ECLQueryServiceFilterTest.historySupplement
2. ECLQueryServiceFilterTest.testDefinitionStatusFilter
3. ECLQueryServiceFilterTest.testAcceptabilityFilters
4. ECLQueryServiceFilterTest.testDescriptionTypeFilters
5. ECLQueryServiceFilterTest.testEffectiveTimeFilter
6. ECLQueryServiceFilterTest.testTermFilters
7. ECLQueryServiceFilterTest.testLanguageFilters
8. ECLQueryServiceFilterTest.testMemberActiveFilter
9. ECLQueryServiceFilterTest.testMemberFieldFilter
10. ECLQueryServiceFilterTest.testMemberSelectFields
11. ECLQueryServiceFilterTest.testModuleFilter
12. ECLQueryServiceFilterTest.testNotOverEagerCaching
13. ECLQueryServiceFilterTest.testPreferredInFilter
14. FHIRValueSetProviderExpandEclTest.testECLRecovery_Descriptions (NullPointerException)

### Failures (7)
1. FHIRCodeSystemProviderInstancesTest.testCodeSystemRecovery
2. FHIRCodeSystemProviderInstancesTest.testCodeSystemRecoverySorted
3. FHIRCodeSystemServiceTest.createSupplementNotSnomed
4. FHIRLoadPackageServiceTest.uploadPackageResources
5. FHIRValueSetProviderExpandEclTest.testECLWithDesignationUseContextExpansion
6. FHIRValueSetProviderValidateCodeEclTest.testECLWithSpecificCodingVersion
7. FHIRValueSetProviderValidateCodeEclTest.testImplicitValidate_Display

---

**Report Generated**: October 15, 2025  
**Investigator**: Development Team  
**Branch**: setup-cicd  
**Commit**: 152d644a
