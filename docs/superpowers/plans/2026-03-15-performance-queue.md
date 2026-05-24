# Performance Queue Implementation Plan

> 기준 변경: 이 문서는 과거 ticket-be 내장 queue/queueToken 설계 기록이다. 현재 실행 기준은 2026-05-24-separated-queue-server-design.md와 2026-05-24-separated-queue-server.md이다. 새 구조에서는 queueToken/X-Queue-Token 대신 queueSessionId/X-Queue-Session과 admissionToken/X-Admission-Token을 사용한다.


> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회차 좌석 페이지 진입 전단에 Redis 기반 대기열과 입장 토큰 검증을 추가한다.

**Architecture:** 회차별 운영 정책은 JPA 엔티티로 저장하고, 대기 순번과 토큰은 Redis 로 관리한다. 좌석 조회 API 앞단에서 입장 토큰을 검증하고, Redis 만료 이벤트를 이용해 다음 사용자를 승격한다.

**Tech Stack:** Java 25, Spring Boot, Spring Data JPA, RedisTemplate, Redisson, JUnit 5, Mockito

---

## Chunk 1: 정책 저장과 설정

### Task 1: Queue 정책 엔티티와 설정 추가

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/model/PerformanceQueuePolicy.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/model/QueueMode.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/model/QueueLevel.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/repository/PerformanceQueuePolicyRepository.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/support/QueueProperties.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/usecase/QueueEntryUseCaseTest.java`

- [ ] **Step 1: 대기열 진입 정책 테스트를 먼저 작성**
- [ ] **Step 2: 테스트만 실행해 실패를 확인**
- [ ] **Step 3: 정책 엔티티와 설정을 최소 구현**
- [ ] **Step 4: 테스트를 다시 실행해 통과 확인**

## Chunk 2: Redis 런타임과 유스케이스

### Task 2: 대기열 진입/상태/이탈 유스케이스 구현

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/runtime/QueueRuntimeStore.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/runtime/RedisQueueRuntimeStore.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/usecase/QueueEntryUseCase.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/usecase/GetQueueStatusUseCase.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/usecase/LeaveQueueUseCase.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/dto/QueueEntryResult.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/dto/QueueStatusResult.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/usecase/QueueEntryUseCaseTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/usecase/GetQueueStatusUseCaseTest.java`

- [ ] **Step 1: 즉시 입장과 대기 등록 테스트 작성**
- [ ] **Step 2: 테스트 실행 후 실패 확인**
- [ ] **Step 3: 최소 Redis 런타임 저장소와 유스케이스 구현**
- [ ] **Step 4: 대상 테스트 재실행**

## Chunk 3: 좌석 조회 게이트와 API

### Task 3: 토큰 검증과 컨트롤러 연결

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/QueueController.java`
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/docs/QueueControllerDocs.java`
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/response/QueueEntryResponse.java`
- Create: `core/core-api/src/main/java/com/ticket/core/api/controller/response/QueueStatusResponse.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/support/QueueTokenGatekeeper.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/PerformanceController.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/support/QueueTokenGatekeeperTest.java`

- [ ] **Step 1: 토큰 없거나 만료된 경우 거부하는 테스트 작성**
- [ ] **Step 2: 테스트 실행 후 실패 확인**
- [ ] **Step 3: 게이트키퍼와 컨트롤러를 최소 구현**
- [ ] **Step 4: 테스트 재실행**

## Chunk 4: 만료와 승격

### Task 4: 다음 대기자 승격 처리

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/queue/command/QueueAdvanceProcessor.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/hold/event/RedisKeyExpirationListener.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/queue/command/QueueAdvanceProcessorTest.java`

- [ ] **Step 1: 토큰 만료 시 다음 대기자가 승격되는 테스트 작성**
- [ ] **Step 2: 테스트 실행 후 실패 확인**
- [ ] **Step 3: 최소 승격 처리 구현**
- [ ] **Step 4: 테스트 재실행**

## Chunk 5: 검증

### Task 5: 변경 범위 테스트 실행

**Files:**
- Modify: 없음

- [ ] **Step 1: 관련 테스트 묶음을 실행**
- [ ] **Step 2: `:core:core-api:compileJava` 실행**
- [ ] **Step 3: 결과와 리스크를 기록**
