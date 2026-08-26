> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-16. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Entity Creation APIs Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** reflection 없이 사용할 수 있는 엔티티 생성 API를 추가하고 관련 테스트 fixture를 치환한다.

**Architecture:** JPA 엔티티의 기본 생성자는 유지하고, 메인 코드에 정적 팩토리를 추가한다. 테스트는 새 생성 경로를 직접 사용하도록 바꾸고, 식별자 세팅 같은 최소 reflection만 유지한다.

**Tech Stack:** Java, Spring Boot, JPA, JUnit 5, Mockito

---

## Chunk 1: 생성 API 추가

### Task 1: 엔티티 생성 테스트와 구현

**Files:**
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/venue/Venue.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/category/Category.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/performer/Performer.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/mapping/ShowGrade.java`
- Modify: `core/core-api/src/main/java/com/ticket/core/domain/show/mapping/ShowSeat.java`
- Test: `core/core-api/src/test/java/com/ticket/core/domain/show/entity/EntityCreationFactoryTest.java`

- [ ] Step 1: 정적 팩토리를 호출하는 실패 테스트 작성
- [ ] Step 2: 대상 테스트 실행으로 실패 확인
- [ ] Step 3: 최소 구현으로 테스트 통과
- [ ] Step 4: 대상 테스트 재실행

## Chunk 2: fixture 치환

### Task 2: query/order 테스트 fixture 정리

**Files:**
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/support/QueryRepositoryTestSupport.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/show/query/ShowListQueryRepositoryTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/query/OrderDetailResponseMapperTest.java`
- Modify: `core/core-api/src/test/java/com/ticket/core/domain/order/query/usecase/GetOrderDetailUseCaseTest.java`

- [ ] Step 1: reflection 기반 fixture를 정적 팩토리 호출로 교체
- [ ] Step 2: 영향 테스트 실행
- [ ] Step 3: 남은 최소 reflection 범위 확인
