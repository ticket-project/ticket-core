> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-25. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Hold Release Outbox Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문 취소/만료 후 hold release outbox를 커밋 직후 즉시 처리하고, 실패 시 scheduler가 재시도하게 만든다.

**Architecture:** 주문 종료 트랜잭션은 outbox 저장까지만 수행하고, `AFTER_COMMIT` 이벤트 리스너가 같은 processor를 즉시 호출한다. scheduler는 미완료 outbox를 재시도해서 최종적 일관성을 보장한다.

**Tech Stack:** Spring Boot, Spring Transaction Event, JPA, JUnit 5, Mockito

---

## Chunk 1: 즉시 처리 이벤트 추가

### Task 1: 실패 테스트 추가

**Files:**
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/release/HoldReleaseOutboxWriterTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/release/HoldReleaseAfterCommitListenerTest.java`

- [ ] `HoldReleaseOutboxWriter`가 저장 후 outbox id를 반환하는 테스트를 추가한다.
- [ ] `AFTER_COMMIT` 리스너가 outbox id로 processor를 호출하는 테스트를 추가한다.
- [ ] 관련 테스트만 실행해 실패를 확인한다.

### Task 2: 최소 구현

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseOutboxWriter.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseRequestedEvent.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseAfterCommitListener.java`

- [ ] outbox writer가 저장한 row id를 반환하도록 수정한다.
- [ ] outbox id를 담는 이벤트를 추가한다.
- [ ] `AFTER_COMMIT` 리스너가 processor를 즉시 호출하게 구현한다.
- [ ] 테스트를 다시 실행해 green을 확인한다.

## Chunk 2: 주문 종료 흐름 연결

### Task 3: 취소/만료 유스케이스에서 이벤트 발행

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/cancel/CancelOrderUseCase.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/expire/ExpireOrderUseCase.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/cancel/CancelOrderUseCaseTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/expire/ExpireOrderUseCaseTest.java`

- [ ] outbox 적재 후 이벤트를 발행하는 테스트를 추가한다.
- [ ] 취소/만료 유스케이스에 event publisher를 주입하고 이벤트 발행을 구현한다.
- [ ] 관련 테스트를 실행해 green을 확인한다.

## Chunk 3: 회귀 검증

### Task 4: outbox processor / scheduler 회귀 검증

**Files:**
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/release/HoldReleaseOutboxProcessorTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/release/HoldReleaseOutboxSchedulerTest.java`

- [ ] 즉시 처리와 scheduler 재시도가 같은 processor를 사용하는지 검증한다.
- [ ] 관련 테스트를 실행한다.
- [ ] 마지막으로 `./gradlew :core:core-api:test`를 실행한다.
