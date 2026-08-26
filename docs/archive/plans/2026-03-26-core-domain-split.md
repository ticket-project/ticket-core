> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-26. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Core Domain Split Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `core-api`를 web 전용 모듈로 축소하고 `core-domain` 모듈을 신설해 비즈니스/JPA/Redis 구현을 이동한다.

**Architecture:** `core-api`는 controller, web config, security, request/response만 남긴다. `core-domain`은 현재 `domain/*`와 비즈니스 실행에 필요한 `aop`, `support`, JPA/Redis 구현을 수용한다. 의존 방향은 `core-api -> core-domain -> core-enum`으로 고정한다.

**Tech Stack:** Gradle multi-module, Spring Boot, Spring MVC, Spring Security, Spring Data JPA, Querydsl, Redisson, JUnit 5

---

## Chunk 1: Module Skeleton

### Task 1: 새 모듈과 구조 보호 테스트 추가

**Files:**
- Create: `core/core-domain/build.gradle`
- Create: `core/core-domain/src/test/java/com/ticket/core/domain/CoreDomainModuleStructureTest.java`
- Modify: `settings.gradle`

- [ ] **Step 1: Write the failing test**

```java
assertThat(Files.exists(Path.of("../../core-domain/build.gradle"))).isTrue();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: FAIL because module or test source set is missing

- [ ] **Step 3: Write minimal implementation**

```gradle
include("core:core-domain")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: PASS

## Chunk 2: Dependency Rewire

### Task 2: build.gradle 의존성 재배치

**Files:**
- Modify: `core/core-api/build.gradle`
- Modify: `core/core-domain/build.gradle`

- [ ] **Step 1: Write the failing test**

Add structure assertions in `CoreDomainModuleStructureTest` for:
- `core-api` depends on `core-domain`
- `core-domain` depends on `core-enum`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: FAIL with missing dependency declaration text

- [ ] **Step 3: Write minimal implementation**

Move JPA/Querydsl/Redis-related dependencies from `core-api` to `core-domain`, keep web/security/swagger in `core-api`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: PASS

## Chunk 3: Source Relocation

### Task 3: 비즈니스 소스 이동

**Files:**
- Move: `core/core-api/src/main/java/com/ticket/core/domain/**`
- Move: `core/core-api/src/main/java/com/ticket/core/aop/**`
- Move: business-side files from `core/core-api/src/main/java/com/ticket/core/support/**`
- Modify: imports under `core/core-api/src/main/java/com/ticket/core/api/**`
- Modify: imports under `core/core-api/src/main/java/com/ticket/core/config/**`

- [ ] **Step 1: Write the failing test**

Add file-path assertions in `CoreDomainModuleStructureTest` for:
- `core-domain/src/main/java/com/ticket/core/domain/order/...`
- `core-domain/src/main/java/com/ticket/core/domain/queue/...`
- `core-api/src/main/java/com/ticket/core/domain/...` absence

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: FAIL because files have not moved

- [ ] **Step 3: Write minimal implementation**

Move sources and fix package/import references without changing behavior.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: PASS

## Chunk 4: Test Relocation and Verification

### Task 4: 테스트 재배치와 컴파일 검증

**Files:**
- Move: domain/usecase/repository/store tests from `core/core-api/src/test/java/com/ticket/core/domain/**`
- Keep: controller/security tests in `core/core-api/src/test/java/com/ticket/core/api/**`
- Modify: moved test imports

- [ ] **Step 1: Write the failing test**

Add assertions in `CoreDomainModuleStructureTest` for:
- `core-domain/src/test/java/com/ticket/core/domain/order/...`
- `core-domain/src/test/java/com/ticket/core/domain/queue/...`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: FAIL because tests have not moved

- [ ] **Step 3: Write minimal implementation**

Move tests and adjust references.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:core-domain:test --tests "com.ticket.core.domain.CoreDomainModuleStructureTest"`
Expected: PASS

- [ ] **Step 5: Run module compilation checks**

Run: `./gradlew :core:core-domain:compileJava :core:core-api:compileJava`
Expected: both PASS

- [ ] **Step 6: Run available module tests**

Run: `./gradlew :core:core-domain:test`
Expected: PASS if no unrelated cross-module compile issue remains
