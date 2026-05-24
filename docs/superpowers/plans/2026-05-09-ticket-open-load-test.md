# Ticket Open Load Test Implementation Plan

> 기준 변경: 이 문서는 과거 ticket-be 내장 queue/queueToken 설계 기록이다. 현재 실행 기준은 2026-05-24-separated-queue-server-design.md와 2026-05-24-separated-queue-server.md이다. 새 구조에서는 queueToken/X-Queue-Token 대신 queueSessionId/X-Queue-Session과 admissionToken/X-Admission-Token을 사용한다.


> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로컬에서 대기열 처리량, 대기열 토큰 강제, 좌석 홀드 정합성을 검증할 수 있는 Gatling Java DSL 테스트와 실행 자산을 추가한다.

**Architecture:** 대기열 토큰 강제는 기존 `QueueAdmissionInterceptor`와 `@RequireQueueAdmission`을 활성화해서 구현한다. 부하 특성은 루트 빌드와 분리된 `load-tests/gatling` 독립 Gradle 프로젝트의 Gatling Java DSL Simulation으로 보고, 핵심 정합성은 JUnit 테스트로 고정한다.

**Tech Stack:** Java 25, Spring Boot MVC, JUnit 5, Mockito, Redisson/Redis, Gatling Java DSL, Gradle

---

## File Map

- Modify: `core/core-api/src/main/java/com/ticket/core/config/WebConfig.java`
  - `QueueAdmissionInterceptor`를 MVC interceptor로 등록한다.
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/PerformanceController.java`
  - 좌석 상태/잔여 수 조회 메서드에 `@RequireQueueAdmission`을 적용한다.
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/HoldController.java`
  - 홀드/주문 시작 메서드에 `@RequireQueueAdmission`을 적용한다.
- Create: `core/core-api/src/test/java/com/ticket/core/config/QueueAdmissionInterceptorTest.java`
  - 토큰 필수, 토큰 유효성, 정책 비활성 통과, 어노테이션 없는 메서드 통과를 검증한다.
- Create: `core/core-api/src/test/java/com/ticket/core/config/WebConfigTest.java`
  - MVC interceptor 등록을 검증한다.
- Modify: `core/core-api/src/test/java/com/ticket/core/api/controller/HoldControllerContractTest.java`
  - 홀드 메서드에 대기열 어노테이션이 붙어 있는지 검증한다.
- Create: `core/core-api/src/test/java/com/ticket/core/api/controller/PerformanceControllerContractTest.java`
  - 좌석 조회 메서드에 대기열 어노테이션이 붙어 있는지 검증한다.
- Create: `load-tests/gatling/build.gradle`
  - Gatling Gradle 플러그인을 적용한 독립 부하 테스트 프로젝트.
- Create: `load-tests/gatling/src/main/java/com/ticket/loadtest/LoadTestConfig.java`
  - system property/environment 기반 실행 설정을 제공한다.
- Create: `load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/QueueEnterSimulation.java`
  - 대기열 진입 부하 시뮬레이션.
- Create: `load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/HoldRaceSimulation.java`
  - 홀드 경합 부하 시뮬레이션.
- Create: `load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/TicketOpenFlowSimulation.java`
  - 대기열 토큰을 받은 사용자만 좌석/홀드 흐름으로 진입하는 시뮬레이션.
- Create: `docs/load-test/ticket-open-local.md`
  - 로컬 실행 절차와 결과 해석 기준.

## Chunk 1: Queue Admission Gate

### Task 1: Interceptor behavior tests

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/config/QueueAdmissionInterceptorTest.java`
- Create: `core/core-api/src/test/java/com/ticket/core/config/WebConfigTest.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/config/WebConfig.java`

- [ ] **Step 1: Write failing tests**
  - `WebConfig`가 `QueueAdmissionInterceptor`를 `/api/v1/performances/*/**`에 등록하는지 검증한다.
  - `@RequireQueueAdmission`이 붙은 핸들러에서 토큰이 없으면 `CoreException(QUEUE_TOKEN_REQUIRED)`가 발생한다.
  - 잘못된 토큰이면 `CoreException(QUEUE_TOKEN_INVALID)`가 발생한다.
  - 정책이 비활성화된 회차는 토큰 없이 통과한다.
  - 어노테이션이 없는 핸들러는 검사하지 않고 통과한다.

- [ ] **Step 2: Run failing tests**
  - Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.config.QueueAdmissionInterceptorTest" --tests "com.ticket.core.config.WebConfigTest"`
  - Expected: `WebConfigTest` fails because the interceptor is not registered yet.

- [ ] **Step 3: Implement minimal gate activation**
  - `WebConfig.addInterceptors` 주석을 해제한다.
  - 경로는 `/api/v1/performances/*/**`로 제한한다.
  - 실제 검사는 `QueueAdmissionInterceptor.requiresQueueAdmission`이 담당하게 유지한다.

- [ ] **Step 4: Run passing tests**
  - Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.config.QueueAdmissionInterceptorTest" --tests "com.ticket.core.config.WebConfigTest"`
  - Expected: PASS.

### Task 2: Protected API annotations

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/PerformanceController.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/HoldController.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/api/controller/HoldControllerContractTest.java`
- Create: `core/core-api/src/test/java/com/ticket/core/api/controller/PerformanceControllerContractTest.java`

- [ ] **Step 1: Write failing annotation tests**
  - `HoldController.createHold`에 `@RequireQueueAdmission`이 있는지 reflection으로 검증한다.
  - `PerformanceController.getSeatAvailability`와 `getSeatStatus`에 `@RequireQueueAdmission`이 있는지 검증한다.

- [ ] **Step 2: Run failing tests**
  - Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.api.controller.HoldControllerContractTest" --tests "com.ticket.core.api.controller.PerformanceControllerContractTest"`
  - Expected: annotation assertion failure.

- [ ] **Step 3: Add annotations**
  - 보호 대상 메서드에 `@RequireQueueAdmission`을 추가한다.

- [ ] **Step 4: Run passing tests**
  - Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.api.controller.HoldControllerContractTest" --tests "com.ticket.core.api.controller.PerformanceControllerContractTest"`
  - Expected: PASS.

## Chunk 2: Gatling Simulations and Documentation

### Task 3: Add Gatling simulations

**Files:**
- Create: `load-tests/gatling/settings.gradle`
- Create: `load-tests/gatling/build.gradle`
- Create: `load-tests/gatling/src/main/java/com/ticket/loadtest/LoadTestConfig.java`
- Create: `load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/QueueEnterSimulation.java`
- Create: `load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/HoldRaceSimulation.java`
- Create: `load-tests/gatling/src/gatling/java/com/ticket/loadtest/simulation/TicketOpenFlowSimulation.java`

- [ ] **Step 1: Create queue enter simulation**
  - system property: `baseUrl`, `performanceId`, `accessTokens`, `users`, `durationSeconds`
  - 요청: `POST /api/v1/queue/performances/{performanceId}/enter`
  - Check: status 200, response result `SUCCESS`.

- [ ] **Step 2: Create hold race simulation**
  - system property: `baseUrl`, `performanceId`, `seatIds`, `accessTokens`, `queueTokens`, `users`, `durationSeconds`
  - 요청: `POST /api/v1/performances/{performanceId}/holds`
  - Header: `Authorization`, `X-Queue-Token`
  - Check: 201 또는 충돌/검증 실패를 허용하되 5xx는 실패로 본다.

- [ ] **Step 3: Create ticket open flow simulation**
  - enter 호출 후 `ADMITTED` 응답에서 `queueToken`을 추출한다.
  - token이 있으면 좌석 상태 조회와 hold를 시도한다.
  - `WAITING`이면 status polling을 제한 횟수만 수행한다.

### Task 4: Add local run guide

**Files:**
- Create: `docs/load-test/ticket-open-local.md`

- [ ] **Step 1: Document prerequisites**
  - Redis 실행, API 실행, 테스트 회원/JWT, 테스트 회차/좌석 확인 방법.

- [ ] **Step 2: Document commands**
  - Gradle 테스트 명령.
  - Gatling Simulation 실행 예시.

- [ ] **Step 3: Document interpretation**
  - 로컬 응답 시간은 참고값이다.
  - 정합성 기준은 admitted 한도, queue token gate, hold 성공 1건이다.

## Chunk 3: Verification

### Task 5: Run verification

**Files:**
- All changed files.

- [ ] **Step 1: Run focused tests**
  - `./gradlew.bat :core:core-api:test --tests "com.ticket.core.config.QueueAdmissionInterceptorTest" --tests "com.ticket.core.config.WebConfigTest"`
  - `./gradlew.bat :core:core-api:test --tests "com.ticket.core.api.controller.HoldControllerContractTest" --tests "com.ticket.core.api.controller.PerformanceControllerContractTest"`
  - `./gradlew.bat -p load-tests/gatling test`
  - `./gradlew.bat -p load-tests/gatling gatlingClasses`

- [ ] **Step 2: Run broad tests**
  - `./gradlew.bat test`

- [ ] **Step 3: Static review**
  - Confirm no production endpoint is hardcoded in Gatling simulations.
  - Confirm docs warn against direct production load tests.
