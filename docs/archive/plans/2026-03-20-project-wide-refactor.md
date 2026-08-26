> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-20. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Project-Wide Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** API 계약을 유지한 채 도메인별로 원시값 포장, 일급 컬렉션, 메서드 분리, `else` 제거를 순차 적용한다.

**Architecture:** 각 도메인을 독립적인 리팩토링 단위로 다루고, 매 단계마다 공개 API 계약 테스트를 먼저 고정한다. 이후 값 객체와 일급 컬렉션을 추가하고 유스케이스를 orchestration 중심으로 축소한 뒤, 관련 테스트 실행과 커밋으로 단계를 닫는다.

**Tech Stack:** Java 25, Spring Boot, MockMvc, JUnit 5, Mockito, Gradle

---

## Chunk 1: order/hold

### Task 1: HOLD 생성 계약과 주문 시작 입력 정리

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/api/controller/HoldControllerContractTest.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/SeatIds.java`
- Create: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/SeatIdsTest.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCase.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCaseTest.java`

- [ ] **Step 1: 계약 테스트와 값 객체 테스트를 작성한다**
- [ ] **Step 2: failing test를 확인한다**
- [ ] **Step 3: 최소 구현으로 테스트를 통과시킨다**
- [ ] **Step 4: 관련 테스트를 다시 실행한다**
- [ ] **Step 5: `refactor(order): hold 계약과 좌석 컬렉션 정리`로 커밋한다**

## Chunk 2: performanceseat

### Task 2: 좌석 선택 API 계약과 좌석 컬렉션 정리

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/SeatSelectionController.java`
- Create/Modify: `core/core-api/src/test/java/com/ticket/core/api/controller/*Seat*ContractTest.java`
- Create/Modify: `core/core-api/src/main/java/com/ticket/core/domain/performanceseat/**/*`
- Create/Modify: `core/core-api/src/test/java/com/ticket/core/domain/performanceseat/**/*`

- [ ] **Step 1: 선택/해제/상태조회 중 하나의 API 계약 테스트를 먼저 고정한다**
- [ ] **Step 2: 좌석 관련 primitive 컬렉션을 일급 컬렉션 후보로 추린다**
- [ ] **Step 3: failing test를 추가하고 최소 구현으로 통과시킨다**
- [ ] **Step 4: 관련 usecase 테스트와 계약 테스트를 실행한다**
- [ ] **Step 5: `refactor(performanceseat): ...`로 커밋한다**

## Chunk 3: queue

### Task 3: queue 계약과 정책 입력 정리

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/QueueController.java`
- Create/Modify: `core/core-api/src/test/java/com/ticket/core/api/controller/*Queue*ContractTest.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/queue/**/*`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/queue/**/*`

- [ ] **Step 1: queue 계약 테스트를 보강한다**
- [ ] **Step 2: token, entry id, 정책 입력 primitive를 값 객체 후보로 정리한다**
- [ ] **Step 3: failing test 후 최소 구현을 적용한다**
- [ ] **Step 4: 관련 테스트를 실행한다**
- [ ] **Step 5: `refactor(queue): ...`로 커밋한다**

## Chunk 4: show

### Task 4: show query 입력 정리

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/ShowController.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/**/*`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/show/**/*`

- [ ] **Step 1: 목록/검색 계약 테스트를 먼저 고정한다**
- [ ] **Step 2: 정렬/커서/검색 컬렉션을 값 객체로 나눈다**
- [ ] **Step 3: failing test 후 최소 구현을 적용한다**
- [ ] **Step 4: 관련 테스트를 실행한다**
- [ ] **Step 5: `refactor(show): ...`로 커밋한다**

## Chunk 5: auth/member

### Task 5: auth/member 입력 경계 정리

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/AuthController.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/MemberController.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/auth/**/*`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/member/**/*`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/auth/**/*`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/member/**/*`

- [ ] **Step 1: 로그인/회원 조회 계약을 먼저 고정한다**
- [ ] **Step 2: 토큰, principal, 식별자 전달 primitive를 줄인다**
- [ ] **Step 3: failing test 후 최소 구현을 적용한다**
- [ ] **Step 4: 관련 테스트를 실행한다**
- [ ] **Step 5: `refactor(auth): ...` 또는 `refactor(member): ...`로 커밋한다**
