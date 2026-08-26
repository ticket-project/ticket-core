> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-25. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Hold Release Outbox Status Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `HoldReleaseOutbox` 조회와 상태 해석을 `status` 기준으로 바꾼다.

**Architecture:** outbox 엔티티에 `PENDING`, `FAILED`, `COMPLETED` 상태를 추가하고, scheduler는 재시도 가능 상태만 조회한다. `completedAt`은 완료 시각 기록으로 유지한다.

**Tech Stack:** Spring Data JPA, JUnit 5, Mockito

---

### Task 1: outbox 상태 테스트 추가

**Files:**
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/release/HoldReleaseOutboxExecutorTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/release/HoldReleaseOutboxSchedulerTest.java`

- [ ] 성공 시 `COMPLETED`, 실패 시 `FAILED`를 검증하는 테스트를 추가한다.
- [ ] scheduler가 `PENDING`, `FAILED`만 조회하는 테스트를 추가한다.
- [ ] 관련 테스트를 실행해 실패를 확인한다.

### Task 2: status 필드와 쿼리 구현

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseOutboxStatus.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseOutbox.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseOutboxRepository.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/release/HoldReleaseOutboxScheduler.java`

- [ ] outbox status enum을 추가한다.
- [ ] 엔티티 생성/성공/실패 전이를 status 기준으로 정리한다.
- [ ] scheduler 조회 조건을 status 기반으로 바꾼다.
- [ ] 관련 테스트를 다시 실행해 green을 확인한다.

### Task 3: 전체 회귀 검증

**Files:**
- Test only

- [ ] `./gradlew :core:core-api:test --tests "com.ticket.core.domain.order.release.*"` 실행
- [ ] `./gradlew :core:core-api:test` 실행
