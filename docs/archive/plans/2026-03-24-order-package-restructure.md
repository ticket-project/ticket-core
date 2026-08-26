> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-24. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Package Restructure Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** order 패키지를 기능 기준으로 재구성하고 공통 finder를 제거해 흐름 중심으로 읽히게 만든다.

**Architecture:** create, cancel, expire, query, shared 패키지로 기능을 나누고 `OrderFinder`, `OrderSeatFinder`는 각 기능 내부 의존으로 흡수한다. model, repository, event만 공통 루트에 남긴다.

**Tech Stack:** Java, Spring Boot, JUnit 5, Mockito

---

### Task 1: 기능 기준 패키지 이동

**Files:**
- Move: `core/core-api/src/main/java/com/ticket/core/domain/order/application/*`
- Move: `core/core-api/src/main/java/com/ticket/core/domain/order/command/*`
- Move: `core/core-api/src/main/java/com/ticket/core/domain/order/query/*`

- [ ] Step 1: create/cancel/expire/query/shared 패키지로 파일을 이동한다.
- [ ] Step 2: package 선언과 import를 수정한다.

### Task 2: 공통 finder 제거

**Files:**
- Delete: `core/core-api/src/main/java/com/ticket/core/domain/order/finder/OrderFinder.java`
- Delete: `core/core-api/src/main/java/com/ticket/core/domain/order/finder/OrderSeatFinder.java`
- Modify: 기능별 usecase/loader

- [ ] Step 1: 기능별 코드가 repository를 직접 사용하도록 수정한다.
- [ ] Step 2: finder 의존 import를 모두 제거한다.

### Task 3: 테스트 패키지 정리

**Files:**
- Move: `core/core-api/src/test/java/com/ticket/core/domain/order/**`
- Delete: `core/core-api/src/test/java/com/ticket/core/domain/order/finder/*`

- [ ] Step 1: 테스트 package/import를 새 구조에 맞춘다.
- [ ] Step 2: finder 테스트는 제거하고 관련 검증을 기능 테스트에 남긴다.

### Task 4: 검증

**Files:**
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/**`
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/*Order*`
- Test: `core/core-api/src/test/java/com/ticket/core/api/controller/*Hold*`

- [ ] Step 1: order 관련 테스트를 먼저 실행한다.
- [ ] Step 2: `:core:core-api:test`로 모듈 전체 회귀를 확인한다.
