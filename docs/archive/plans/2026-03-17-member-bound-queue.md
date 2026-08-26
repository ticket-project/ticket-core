> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-07-30. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Member-Bound Queue Implementation Plan

> 보관 문서: 당시의 구현 계획입니다. 현재 동작과 명령은 저장소 루트 `README.md`, `docs/development.md`, 형제 저장소 `../ticket-queue/README.md`, `../gatling-test/README.md`를 기준으로 확인하세요. 이 문서의 queue token/session/status 모델은 현재의 shard/local sequence/public state 구조와 다를 수 있습니다.


> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** queue API를 인증 사용자 전용으로 바꾸고, 대기열 엔트리를 `memberId` 귀속 구조로 전환한다.

**Architecture:** `QueueController`는 인증된 `MemberPrincipal`에서 `memberId`를 받아 유스케이스로 전달한다. Redis 런타임 저장소는 entry hash와 별도 회원 매핑 키를 함께 관리해 재진입 시 기존 엔트리를 정리하고 새 엔트리를 발급한다.

**Tech Stack:** Java 25, Spring Boot, Spring Security, Redisson, JUnit 5, Mockito, MockMvc

---

## Chunk 1: Queue 런타임 모델 확장

### Task 1: memberId 기반 런타임 저장 구조 테스트와 구현

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/runtime/QueueEntryRuntime.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/runtime/QueueRuntimeStore.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/runtime/QueueRedisKey.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/runtime/RedisQueueRuntimeStore.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/runtime/RedisQueueRuntimeStoreTest.java`

- [ ] **Step 1: memberId 저장과 회원 매핑 키 동작을 검증하는 failing test를 작성**
- [ ] **Step 2: 대상 테스트만 실행해 기대한 이유로 실패하는지 확인**
- [ ] **Step 3: `QueueEntryRuntime`, `QueueRuntimeStore`, `QueueRedisKey`, `RedisQueueRuntimeStore`를 최소 수정**
- [ ] **Step 4: 대상 테스트를 다시 실행해 통과 확인**

## Chunk 2: 유스케이스 소유권과 재진입 정책

### Task 2: enter/status/leave의 memberId 귀속 테스트와 구현

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/usecase/QueueEntryUseCase.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/usecase/GetQueueStatusUseCase.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/usecase/LeaveQueueUseCase.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/usecase/QueueEntryUseCaseTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/usecase/GetQueueStatusUseCaseTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/usecase/LeaveQueueUseCaseTest.java`

- [ ] **Step 1: 같은 회원 재진입 시 기존 엔트리가 정리되고 새 엔트리가 생성되는 failing test를 작성**
- [ ] **Step 2: 상태 조회와 이탈에서 다른 회원 엔트리를 거부하는 failing test를 작성**
- [ ] **Step 3: 해당 테스트들만 실행해 실패 원인을 확인**
- [ ] **Step 4: 유스케이스 입력과 로직을 최소 수정해 테스트를 통과시킴**
- [ ] **Step 5: 대상 테스트를 다시 실행해 모두 녹색인지 확인**

## Chunk 3: API와 보안 정책 전환

### Task 3: QueueController 인증 강제 테스트와 구현

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/QueueController.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/config/security/SecurityConfig.java`
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/QueueControllerTest.java`
- Create: `core/core-api/src/test/java/com/ticket/core/api/controller/QueueControllerSecurityTest.java`

- [ ] **Step 1: queue API 비인증 요청이 401을 반환하는 failing security test를 작성**
- [ ] **Step 2: 인증 요청 시 controller가 memberId를 유스케이스로 넘기는 failing test를 작성**
- [ ] **Step 3: 대상 테스트를 실행해 실패를 확인**
- [ ] **Step 4: `QueueController` 시그니처와 `SecurityConfig`를 최소 수정**
- [ ] **Step 5: controller/security 테스트를 다시 실행해 통과 확인**

## Chunk 4: 회귀 검증

### Task 4: queue 관련 테스트 묶음과 컴파일 검증

**Files:**
- Modify: 없음

- [ ] **Step 1: `QueueController`, `QueueEntryUseCase`, `GetQueueStatusUseCase`, `LeaveQueueUseCase`, `RedisQueueRuntimeStore` 관련 테스트를 실행**
- [ ] **Step 2: `./gradlew :core:core-api:test --tests \"*Queue*\"` 또는 동등 범위 명령으로 회귀 확인**
- [ ] **Step 3: `./gradlew :core:core-api:compileJava` 실행**
- [ ] **Step 4: 실패 없이 끝났는지 확인하고 남은 리스크를 요약**
