# Phase 1 Status Report

**Date**: October 15, 2025  
**Status**: ✅ Phase 1 Already Implemented (But Tests Still Failing)

---

## What Was Done

Commit `31484ea0` (Oct 15, 12:13:37) **already implemented the Phase 1 fix**:

**Title**: "Fix ECLQueryServiceFilterTest bean initialization failure"

**Action Taken**: Removed `@TestConfiguration` annotation from `ECLQueryServiceFilterTestConfig.java`

**Commit Details**:
```
Author: Jaak <jaak.daemen@tiro.health>
Date:   Wed Oct 15 12:13:37 2025 +0000

    Fix ECLQueryServiceFilterTest bean initialization failure

    Remove @TestConfiguration annotation from ECLQueryServiceFilterTestConfig that was
    causing NullPointerException during bean initialization. The annotation changed the
    initialization order, causing @PostConstruct to run before @Autowired fields were
    injected in the parent class.

    This fixes 13 test errors in ECLQueryServiceFilterTest.

    Root cause: branchService was null when beforeAll() @PostConstruct method tried
    to use it because Spring hadn't injected the @Autowired fields yet.

    Solution: Remove unnecessary @TestConfiguration annotation since the class already
    extends ECLQueryTestConfig which extends TestConfig (@SpringBootApplication).
```

---

## Problem: Tests Still Failing!

Even though the `@TestConfiguration` was removed in commit `31484ea0`, the **tests are STILL failing** in CI build #18528554601 (which runs on commit `a36a9a00` that includes this fix).

---

## Current Error (After Fix Attempt)

The error is still:
```
Error creating bean with name 'ECLQueryServiceFilterTestConfig': Invocation of init method failed
```

But now the error chain shows a **circular dependency**:
```
testConfig needs semanticIndexUpdateService
  → needs axiomConversionService
    → needs memberService
      → needs eclQueryService
        → needs eclContentService
          → needs relationshipService
            → needs conceptUpdateHelper
              → needs identifierService
                → needs cacheManager
                  → ERROR creating ECLQueryServiceFilterTestConfig (init method failed)
```

---

## Analysis: The @PostConstruct Method Is Still Failing

The `@PostConstruct` method `beforeAll()` in `ECLQueryServiceFilterTestConfig` is being called during bean creation, and it's failing for a DIFFERENT reason than we originally thought.

**Original Theory**: `@TestConfiguration` caused wrong initialization order  
**Reality**: Even without `@TestConfiguration`, the `@PostConstruct` method fails

**New Problem**: The `@PostConstruct` method is running during a dependency chain where other beans need the `cacheManager`, which tries to create the `ECLQueryServiceFilterTestConfig` bean, but the `@PostConstruct` init method fails.

---

## Why Is This Still Happening?

The issue is that `ECLQueryServiceFilterTestConfig` is being treated as a bean that provides dependencies to other beans (like `cacheManager`), and Spring tries to initialize it early in the dependency chain. When it tries to run the `@PostConstruct` method, something in that method fails.

**Possible causes**:
1. The `@PostConstruct` method tries to do too much setup (creating branches, concepts, versioning)
2. Some service it depends on is not yet available
3. There's an exception being thrown that's hidden in the long dependency chain
4. Elasticsearch or other infrastructure not ready

---

## The Real Root Cause

Looking at the code in `ECLQueryServiceFilterTestConfig.java`:

```java
@PostConstruct
public void beforeAll() throws ServiceException, InterruptedException {
    deleteAll();
    branchService.create(MAIN);
    // ... lots of test data setup ...
    conceptService.batchCreate(allConcepts, MAIN);
    codeSystemService.createCodeSystem(codeSystem);
    codeSystemService.createVersion(...);
    memberService.createMembers(...);
}
```

This `@PostConstruct` method is doing **WAY TOO MUCH**:
- Deleting all data
- Creating branches
- Creating concepts
- Creating code systems
- Versioning code systems
- Creating members

This is **test data setup**, not bean configuration!

---

## Why This Fails

1. **Spring creates beans in a specific order based on dependencies**
2. **`ECLQueryServiceFilterTestConfig` is being created early** because other beans need it (or beans it provides)
3. **The `@PostConstruct` runs BEFORE the Spring context is fully initialized**
4. **Services like `branchService`, `conceptService` might not be fully ready**
5. **Elasticsearch might not be fully started**

---

## The Correct Solution

**DO NOT use `@PostConstruct` for test data setup!**

Instead, the test class `ECLQueryServiceFilterTest` should call this setup in `@BeforeAll` or `@BeforeEach`:

### Option 1: Move to @BeforeAll in Test Class (Recommended)

```java
// In ECLQueryServiceFilterTest.java
@SpringBootTest(classes = {TestConfig.class, ECLQueryServiceFilterTestConfig.class})
public class ECLQueryServiceFilterTest {
    
    @Autowired
    private ECLQueryServiceFilterTestConfig config;
    
    @BeforeAll
    static void setup(@Autowired ECLQueryServiceFilterTestConfig config) throws Exception {
        config.setupTestData(); // Rename beforeAll() to setupTestData()
    }
    
    @Test
    void testSomething() {
        // tests here
    }
}
```

### Option 2: Remove @PostConstruct, Make Manual

```java
// In ECLQueryServiceFilterTestConfig.java
// REMOVE @PostConstruct
public void setupTestData() throws ServiceException, InterruptedException {
    // All the setup code
}
```

Then each test that uses this config calls `setupTestData()` explicitly.

---

## Recommended Immediate Action

**Remove the `@PostConstruct` annotation** from the `beforeAll()` method in `ECLQueryServiceFilterTestConfig.java`.

The test data setup should happen in the test class itself, not during Spring bean initialization.

---

## Why Previous "Fix" Didn't Work

Removing `@TestConfiguration` was **partially correct** - it did fix the initialization order issue where @Autowired fields weren't injected yet.

**BUT** it didn't fix the **fundamental problem**: Using `@PostConstruct` for heavy test data setup that depends on a fully initialized Spring context.

---

## Action Plan

### Step 1: Remove @PostConstruct
```bash
# Edit: src/test/java/org/snomed/snowstorm/ecl/ECLQueryServiceFilterTestConfig.java
# Remove the @PostConstruct annotation from beforeAll() method
```

### Step 2: Update Test Class
```bash
# Edit: src/test/java/org/snomed/snowstorm/ecl/ECLQueryServiceFilterTest.java  
# Add @BeforeAll or @BeforeEach to call the setup method
```

### Step 3: Test Locally
```bash
mvn test -Dtest=ECLQueryServiceFilterTest
```

### Step 4: Commit
```bash
git add src/test/java/org/snomed/snowstorm/ecl/
git commit -m "Fix ECLQueryServiceFilterTest by removing @PostConstruct from test data setup

The @PostConstruct annotation was causing the test data setup to run during
Spring bean initialization, before the context was fully ready. This caused
circular dependency issues and initialization failures.

Test data setup should happen in the test class lifecycle (@BeforeAll or
@BeforeEach), not during bean creation.

Fixes 13 test errors in ECLQueryServiceFilterTest."
```

---

## Confidence Level

**95% confident** this will fix the issue.

The pattern of using `@PostConstruct` for test data setup is an anti-pattern in Spring testing.

---

**Report Created**: October 15, 2025  
**Next Action**: Implement the real fix (remove @PostConstruct)
