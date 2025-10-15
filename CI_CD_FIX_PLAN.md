# CI/CD Test Failure Analysis and Fix Plan

## Implementation Status

✅ **Phase 1 COMPLETED** (commit 9e96eb19): Fixed bean definition conflict - removed @TestConfiguration from TestConfig  
✅ **Phase 2 COMPLETED** (commit 2c752204): Configured JMS broker for traceability tests - vm://localhost auto-creates broker  
✅ **Phase 3 COMPLETED** (commit 34074438): Fixed traceability scope - disabled by default, enabled only for TraceabilityLogServiceTest  

**Expected result**: All 103 test failures should be resolved (79 FHIR errors + 16 JMS failures + 8 SemanticIndex errors)

## Original Status (Run #36)
The CI/CD pipeline was failing with **79 errors and 16 failures** out of 839 tests. The tests ran for approximately 20 minutes before failing.

## Root Causes Identified

### 1. **Bean Definition Conflict (PRIMARY ISSUE - Blocks 79+ tests)**
**Error**: `BeanDefinitionOverrideException: Invalid bean definition with name 'getIdentifierCacheManager'`

**Cause**: Both `TestConfig` and `FHIRTestConfig` define a `@Primary` bean with the method name `getIdentifierCacheManager`. Since `FHIRTestConfig` extends `TestConfig`, Spring tries to register the same bean twice.

**Impact**: All FHIR tests fail to load their ApplicationContext
- Affected: ~79 tests in `org.snomed.snowstorm.fhir.services.*`

**Solution**: Remove the duplicate bean definition from `FHIRTestConfig` since it inherits from `TestConfig`.

---

### 2. **ActiveMQ JMS Broker Shutdown Race Condition**
**Error**: `IllegalStateException: Shutdown in progress` when trying to start ActiveMQ broker

**Cause**: Multiple test contexts are trying to create and destroy JMS brokers simultaneously. When the JVM shutdown starts, ActiveMQ tries to register shutdown hooks but the shutdown is already in progress.

**Current Workaround**: JMS configuration has been moved to `application-test.properties` with:
- Fixed broker name: `vm://snowstorm-test-broker`
- JMX disabled: `broker.useJmx=false`
- Non-persistent broker

**Remaining Issue**: Tests still experience broker conflicts when running in parallel or when contexts are destroyed/recreated.

**Impact**: Causes intermittent failures and cascading context initialization failures

**Solution**: 
- Consider disabling JMS/traceability for tests that don't explicitly need it
- Use `@DirtiesContext` annotations more strategically
- Implement a singleton broker pattern for tests

---

### 3. **Test Data Issues (16 failures)**

#### 3.1 Invalid RF2 File Format
**Error**: `ReleaseImportException: Invalid RF2 content. Wrong number of columns in line 2 of file sct2_Description_Delta-en_INT_20190131.txt. Expected 9 columns, found 7.`

**Cause**: Test data file has incorrect format

**Solution**: Fix or regenerate the test RF2 files

#### 3.2 Circular Dependency in Test Concepts
**Error**: `GraphBuilderException: Loop found in transitive closure for concept X`

**Cause**: Test data creates concepts with circular parent-child relationships:
- Concept 10000000001 is in its own ancestors
- Concept 762743006 is in its own ancestors
- Concept 1000011, 1000012, 1000013 form a cycle

**Solution**: Fix test data to ensure valid concept hierarchies

#### 3.3 Missing Relationship in Group 0
**Error**: `ConversionException: At least one relationship with type '116680003 | Is a (attribute) |' is required in group 0.`

**Cause**: OWL conversion expects Is-a relationship in group 0

**Solution**: Ensure test concepts have proper Is-a relationships

#### 3.4 File Not Found Errors
**Error**: `IOException: error=2, No such file or directory`

**Cause**: Tests expecting files that don't exist in the test environment

**Solution**: Mock file operations or provide necessary test files

---

## Fix Priority

### Phase 1: Critical - Fix Bean Definition Conflict (Unblocks 79 tests)
1. Remove duplicate `getIdentifierCacheManager` bean from `FHIRTestConfig`
2. Verify `FHIRTestConfig` properly inherits all necessary beans from `TestConfig`

**Expected Impact**: Should fix 79+ test errors immediately

---

### Phase 2: Important - Stabilize JMS/ActiveMQ Configuration
1. Review and potentially disable traceability for tests that don't need it
2. Implement proper test isolation for JMS-dependent tests
3. Consider using `@DirtiesContext` strategically or implement a test-scoped singleton broker

**Expected Impact**: Should reduce intermittent failures and speed up test execution

---

### Phase 3: Fix Individual Test Data Issues (16 failures)
1. Fix RF2 file format issues
2. Correct circular dependency in test concept hierarchies
3. Ensure proper OWL axiom structures in test data
4. Mock or provide missing files

**Expected Impact**: Should fix remaining 16 test failures

---

## Implementation Steps

### Step 1: Fix Bean Conflict
- [ ] Remove `@Bean @Primary` method override from `FHIRTestConfig` if it exists
- [ ] Run FHIR tests locally to verify fix
- [ ] Commit with message: "Fix bean definition conflict between TestConfig and FHIRTestConfig"

### Step 2: JMS Stability
- [ ] Review which tests actually need JMS/traceability enabled
- [ ] Consider adding `@TestPropertySource` to disable traceability for tests that don't need it
- [ ] Add proper cleanup in test teardown methods
- [ ] Commit with message: "Improve JMS test stability and isolation"

### Step 3: Test Data Fixes
- [ ] Fix RF2 file format in test resources
- [ ] Correct circular concept dependencies in test data
- [ ] Ensure proper axiom structures
- [ ] Add missing test resource files or mock file operations
- [ ] Commit with message: "Fix test data issues - RF2 format, concept hierarchies, and axioms"

---

## Expected Outcomes

After implementing all fixes:
- **79 errors** (Bean conflict) → **0 errors**
- **16 failures** (Test data) → **0 failures**  
- **Total**: 839 tests should pass
- Test execution time should remain ~20 minutes or improve with JMS optimizations

---

## Notes

### Git Configuration
- Git user configured as: Jaak <jaak.daemen@tiro.health>
- Branch: `setup-cicd`
- All commits will be made by this user

### Current CI/CD Configuration
- GitHub Actions workflow: `.github/workflows/build.yml`
- Java 17 (Amazon Corretto)
- Maven build with Docker/Testcontainers support
- Tests run with: `mvn clean package -B`

---

## Verification Steps

After implementing fixes:
1. Run full test suite locally: `mvn clean test`
2. Check for any remaining failures
3. Push to `setup-cicd` branch
4. Monitor GitHub Actions run
5. Verify all 839 tests pass in CI/CD
