> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-15. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Domain Test Strategy Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `core/core-api` 에 도메인/유스케이스 단위 테스트와 QueryRepository 슬라이스 테스트의 1차 표준을 실제 코드로 시작한다.

**Architecture:** 명령 계열은 협력자를 격리한 단위 테스트로, 조회 계열은 H2 기반 `@DataJpaTest` 슬라이스 테스트로 분리한다. 첫 구현은 `StartOrderUseCase` 와 `ShowListQueryRepository` 를 대표 표본으로 삼아 이후 패턴 재사용이 가능하도록 한다.

**Tech Stack:** Java 25, Spring Boot, JUnit 5, Mockito, Spring Data JPA, Querydsl, H2

---

## Chunk 1: StartOrderUseCase 단위 테스트

### Task 1: 테스트 스캐폴딩 추가

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/domain/order/command/usecase/StartOrderUseCaseTest.java`

- [ ] **Step 1: 실패하는 테스트 초안 작성**

중복 좌석, 빈 좌석, 최대 수량 초과, 중복 주문, 정상 주문 시작 케이스를 개별 테스트로 선언한다.

- [ ] **Step 2: 대상 테스트만 실행해 실패를 확인**

Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.domain.order.command.usecase.StartOrderUseCaseTest"`

Expected: 컴파일 실패 또는 assertion 실패

- [ ] **Step 3: 최소 테스트 더블과 fixture 보강**

프로덕션 코드를 바꾸지 않고 테스트 생성이 가능하면 테스트 코드만 보강한다. 불가하면 가장 작은 범위로 생산 코드 의존성 노출을 조정한다.

- [ ] **Step 4: 대상 테스트 재실행**

Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.domain.order.command.usecase.StartOrderUseCaseTest"`

Expected: PASS

## Chunk 2: ShowListQueryRepository 슬라이스 테스트

### Task 2: 조회 슬라이스 테스트 작성

**Files:**
- Create: `core/core-api/src/test/java/com/ticket/core/domain/show/query/ShowListQueryRepositoryTest.java`

- [ ] **Step 1: 실패하는 조회 테스트 작성**

최신 공연 조회, 검색 카운트, 필터/정렬/커서 관련 최소 핵심 케이스를 선언한다.

- [ ] **Step 2: 대상 테스트 실행 후 실패 확인**

Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.domain.show.query.ShowListQueryRepositoryTest"`

Expected: 설정 누락, 빈 데이터셋, assertion 실패 중 하나

- [ ] **Step 3: 최소 fixture 와 테스트 설정 보강**

H2 + JPA + Querydsl 환경에서 필요한 엔티티를 직접 저장하고, 대상 저장소 빈을 생성한다.

- [ ] **Step 4: 대상 테스트 재실행**

Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.domain.show.query.ShowListQueryRepositoryTest"`

Expected: PASS

## Chunk 3: 관련 테스트 묶음 검증

### Task 3: 변경 범위 검증

**Files:**
- Modify: 없음

- [ ] **Step 1: 두 테스트 묶음을 함께 실행**

Run: `./gradlew.bat :core:core-api:test --tests "com.ticket.core.domain.order.command.usecase.StartOrderUseCaseTest" --tests "com.ticket.core.domain.show.query.ShowListQueryRepositoryTest"`

Expected: PASS

- [ ] **Step 2: 실패 시 원인 분리**

단위 테스트 실패인지 슬라이스 테스트 실패인지 구분해 각각 수정한다.

- [ ] **Step 3: 결과 기록**

검증 명령, 실패 원인 또는 성공 여부, 남은 리스크를 작업 결과에 남긴다.
