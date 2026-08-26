> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-20. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Hold Contract Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HOLD 생성 공개 API 계약을 테스트로 고정하고 `StartOrderUseCase`를 API 변경 없이 리팩토링한다.

**Architecture:** `HoldController`는 MockMvc 기반 계약 테스트로 상태코드, 헤더, 응답 바디를 고정한다. 내부는 `StartOrderUseCase`에 좌석 ID 컬렉션 원시값 포장 객체를 도입해 검증과 정규화를 캡슐화하고, 유스케이스 메서드는 조합 책임만 남긴다.

**Tech Stack:** Java 25, Spring Boot, MockMvc, JUnit 5, Mockito, Gradle

---

## Chunk 1: 계약 테스트와 리팩토링 안전망

### Task 1: HOLD 생성 API 계약 테스트 추가

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/api/controller/HoldControllerContractTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/HoldControllerContractTest.java`

- [ ] **Step 1: 기존 컨트롤러 계약을 표현하는 테스트를 작성한다**
- [ ] **Step 2: 테스트를 실행해 현재 구현 계약과 일치하는지 확인한다**

### Task 2: 좌석 ID 원시값 포장 객체 테스트 추가

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/SeatIdsTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/SeatIdsTest.java`

- [ ] **Step 1: 빈 좌석 목록, 중복 좌석, 정렬 정규화를 검증하는 failing test를 작성한다**
- [ ] **Step 2: 테스트를 실행해 타입 부재로 실패하는지 확인한다**

## Chunk 2: StartOrderUseCase 리팩토링

### Task 3: 원시값 포장 도입과 유스케이스 단순화

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/SeatIds.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCase.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCaseTest.java`

- [ ] **Step 1: `SeatIds` 객체에 검증과 정렬 책임을 옮긴다**
- [ ] **Step 2: `StartOrderUseCase`는 입력 검증, 성능 검증, 주문 시작 orchestration 만 남긴다**
- [ ] **Step 3: 기존 테스트를 새 타입에 맞게 최소 수정한다**

### Task 4: 회귀 검증

**Files:**
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/HoldControllerContractTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/SeatIdsTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCaseTest.java`

- [ ] **Step 1: 관련 테스트 묶음을 실행한다**
- [ ] **Step 2: 실패가 있으면 수정 후 동일 명령으로 재검증한다**
