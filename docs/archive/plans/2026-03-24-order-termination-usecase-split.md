> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-24. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Termination UseCase Split Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문 종료 흐름을 취소와 만료 유스케이스로 분리하고 종료 구현을 책임별 컴포넌트로 재구성한다.

**Architecture:** `TerminateOrderUseCase`를 제거하고 `CancelOrderUseCase`, `ExpireOrderUseCase`가 오케스트레이션만 담당한다. 실제 종료 처리는 좌석 로더, 취소 처리기, 만료 처리기로 분리하고, 컨트롤러·리스너·스케줄러는 새 유스케이스를 호출한다.

**Tech Stack:** Java, Spring Boot, JUnit 5, Mockito

---

### Task 1: 종료 동작 고정 테스트

**Files:**
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/TerminateOrderUseCaseTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/domainservice/OrderTerminationDomainServiceTest.java`

- [ ] Step 1: 취소와 만료 테스트를 새 구조 기준으로 재배치할 케이스를 정리한다.
- [ ] Step 2: 새 유스케이스/컴포넌트 이름으로 failing test를 작성한다.
- [ ] Step 3: 관련 테스트를 실행해 실패를 확인한다.

### Task 2: 종료 구현 분리

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/CancelOrderUseCase.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/ExpireOrderUseCase.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/OrderTerminationContext.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/OrderTerminationContextLoader.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/OrderCancellationResult.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/OrderExpirationResult.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/OrderCanceler.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/OrderExpirer.java`
- Delete: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/TerminateOrderUseCase.java`
- Delete: `core/core-api/src/main/java/com/ticket/core/domain/order/domainservice/OrderTerminationDomainService.java`
- Delete: `core/core-api/src/main/java/com/ticket/core/domain/order/domainservice/OrderLifecycleDomainService.java`

- [ ] Step 1: 새 컴포넌트 테스트를 먼저 작성한다.
- [ ] Step 2: 최소 구현으로 테스트를 통과시킨다.
- [ ] Step 3: 기존 종료 서비스 클래스를 제거한다.

### Task 3: 호출부 연결

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/hold/event/RedisKeyExpirationListener.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/command/OrderExpirationScheduler.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/command/OrderExpirationSchedulerTest.java`

- [ ] Step 1: 취소 API는 `CancelOrderUseCase`를 호출하게 바꾼다.
- [ ] Step 2: 만료 리스너와 스케줄러는 `ExpireOrderUseCase`를 호출하게 바꾼다.
- [ ] Step 3: 관련 테스트를 갱신한다.

### Task 4: 검증

**Files:**
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/*`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/command/OrderExpirationSchedulerTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/hold/event/RedisKeyExpirationListenerTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/OrderControllerContractTest.java`

- [ ] Step 1: 종료 관련 테스트만 실행한다.
- [ ] Step 2: 호출부 테스트까지 포함해 회귀를 확인한다.
- [ ] Step 3: 검증 범위와 미검증 범위를 정리한다.
