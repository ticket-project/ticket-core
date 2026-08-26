> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-16. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Structural Refactors Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 테스트가 긴 구조적 원인을 순차적으로 분리하고 각 단계마다 검증 가능한 커밋을 남긴다.

**Architecture:** 주문 오케스트레이션, 시간/UUID 공급, Redis 저장소 경계, Show 조회 정책을 각각 독립적인 컴포넌트로 분리한다. 각 단계는 기존 공개 API를 유지하면서 내부 의존만 치환한다.

**Tech Stack:** Java, Spring Boot, JPA, Redis/Redisson, JUnit 5, Mockito, Querydsl

---

## Chunk 1: 주문 오케스트레이션

### Task 1: 주문 시작/종료 도메인 서비스 도입

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCase.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/order/command/usecase/TerminateOrderUseCase.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/domainservice/OrderStartDomainService.java`
- Create: `core/core-api/src/main/java/com/ticket/core/domain/order/domainservice/OrderTerminationDomainService.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/domainservice/OrderStartDomainServiceTest.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/order/domainservice/OrderTerminationDomainServiceTest.java`

- [ ] Step 1: 새 도메인 서비스 기대 동작을 검증하는 실패 테스트 작성
- [ ] Step 2: 관련 테스트 실행으로 실패 확인
- [ ] Step 3: 최소 구현과 usecase 위임 반영
- [ ] Step 4: 대상 테스트 재실행
- [ ] Step 5: 커밋

## Chunk 2: Clock/UuidSupplier

### Task 2: 시간/UUID 공급자 도입

**Files:**
- Create: `core/core-api/src/main/java/com/ticket/core/support/time/CoreClockConfig.java`
- Create: `core/core-api/src/main/java/com/ticket/core/support/random/UuidSupplier.java`
- Modify: 시간/UUID 직접 호출 파일들
- Test: 영향 테스트들

- [ ] Step 1: 실패 테스트 추가
- [ ] Step 2: 공급자 구현 및 치환
- [ ] Step 3: 영향 테스트 실행
- [ ] Step 4: 커밋

## Chunk 3: Redis adapter

### Task 3: Hold/SeatSelection 저장소 경계 분리

**Files:**
- Create: `HoldStore`, `SeatSelectionStore` 인터페이스 및 Redisson adapter
- Modify: `HoldManager.java`, `SeatSelectionService.java`, 관련 리스너/유스케이스
- Test: `HoldManagerTest.java`, `SeatSelectionServiceTest.java` 등

- [ ] Step 1: 실패 테스트 추가
- [ ] Step 2: adapter 도입 및 서비스 치환
- [ ] Step 3: 영향 테스트 실행
- [ ] Step 4: 커밋

## Chunk 4: Show 조회 정책

### Task 4: 조건/시간/커서 정책 분리

**Files:**
- Create: 정책 컴포넌트들
- Modify: `ShowListQueryRepository.java`, `ShowQueryHelper.java`, `ShowCursorSupport.java`
- Test: `ShowQueryHelperTest.java`, `ShowListQueryRepositoryTest.java`, 신규 정책 테스트

- [ ] Step 1: 실패 테스트 추가
- [ ] Step 2: 정책 컴포넌트 분리
- [ ] Step 3: 영향 테스트 실행
- [ ] Step 4: 커밋
