---
name: place-code
description: >
  ticket 저장소에서 새 코드를 어느 Application Module에 둘지 판단하고, 모듈 경계 위반을
  진단한다. 새 클래스·포트·use case를 만들 때, 코드를 옮길 때, ModularityTests나 모듈 테스트가
  실패했을 때 쓴다.
paths: src/main/java/com/ticket/**, src/test/java/com/ticket/**
allowed-tools: Bash(rg:*) Bash(./gradlew:*)
---

# 새 코드를 어디에 둘까

단일 Gradle Spring Boot 프로젝트다. `com.ticket`의 직접 하위 패키지가 Spring Modulith의 닫힌
Application Module이고, 계층(web/application/domain/infrastructure)은 각 모듈 안의
`internal` 하위 패키지일 뿐이다. 결정 배경은
`docs/adr/0003-spring-modulith-application-module-boundaries.md`가 원본이다.

## 0. 먼저 모듈을 고른다

| 다루는 업무 | 모듈 |
| --- | --- |
| 좌석 판매 상태(`PerformanceSeat`), Selection, Hold, Order/OrderSeat, 주문 취소·만료, 좌석 분산락, WebSocket 좌석 발행 | `booking` |
| Show, Performance, Seat, 예매 가능 시간, Hold 한도, 대기열 정책(`PerformanceQueuePolicy`), 공연·회차·좌석 조회 | `catalog` |
| Member, 소셜 로그인, OAuth2, 비밀번호, access/refresh token, 전역 `SecurityFilterChain` | `identity` |
| admission token 설정·decode·검증 | `admission` |
| Show 좋아요 write 경로(추가/삭제/상태 조회) | `showlike`(read 경로는 아래 "showlike 예외" 참고) |
| catalog/booking/identity가 공개한 code/label 조합 | `metadata` |
| 둘 이상 독립 모듈이 의미 동일하게 공유하는 최소 계약(현재 비어 있음) | `shared` |

모듈을 잘못 고르면 그 다음 판단이 전부 무의미하다. 애매하면 "이 코드가 사라지면 무엇이 먼저
깨지는가"를 먼저 모듈 단위로 묻는다.

**아직 모듈로 옮기지 않은 legacy 코드**(`com.ticket.core`/`bootstrap`/`storage`(그리고 `core` 아래 nested된 `core.support`))가
있다. 새 코드를 여기 추가하지 않는다 — 새 기능은 해당하는 모듈로 바로 만든다. legacy 코드를
옮기는 작업 자체는 범위가 크므로 먼저 사용자와 범위를 정한다.

## 1. 모듈을 골랐으면 계층을 고른다

같은 판단축을 모듈 내부에도 그대로 쓴다.

| 쓰는 것 | 두는 곳 |
| --- | --- |
| HTTP 요청·응답, 쿠키, WebSocket 진입, controller.docs | `<module>.internal.web` |
| use case, 트랜잭션 경계, 여러 서비스 조립, 조회 포트와 결과 view | `<module>.internal.application` |
| 엔티티, 값 객체, 도메인 정책, `*Finder`, port 선언 | `<module>.internal.domain` |
| Querydsl, Redis, JWT, 암호화, 외부 HTTP, scheduler, AOP | `<module>.internal.infrastructure` |
| 다른 모듈이 쓸 공개 계약(작은 interface + 불변 record snapshot, 공개 이벤트) | 모듈 root(예: `booking.BookingMetadata`, `booking.OrderStarted`) |

**모듈 root에는 공개 계약만 둔다.** 구현 클래스, JPA entity, Repository는 root에 두지 않는다.
어떤 모듈도 `Type.OPEN`이 아니다.

## 2. 갈리면 이 둘을 본다

- **port는 그것을 쓰는 쪽에 둔다.** use case가 쓰면 `internal.application`, `*Finder`가 쓰면
  `internal.domain`. 구현은 어느 쪽이든 `internal.infrastructure`다.
- **엔티티를 다루면 domain, 순서를 정하면 application이다.** 규칙 판단은
  `internal.domain`, 그 규칙들을 순서대로 부르는 조립은 `internal.application`이다.

그래도 갈리면 **"이 코드가 사라졌을 때 무엇이 먼저 깨지는가"**를 보고 아래 표에서 찾는다.

## 3. 책임별 위치

| 책임 | 위치 |
| --- | --- |
| HTTP endpoint, 요청 검증, 인증 주체 추출, 응답 포맷 | 소유 모듈의 `internal.web` |
| Swagger 문서 인터페이스 | `internal.web`의 `controller.docs` |
| OAuth2 설정, 전역 security filter chain, 인증 필터 | `identity.internal`의 security 설정 |
| JWT 발급·검증 구현 | `identity.internal`의 token 구현 |
| 인증 흐름의 포트와 인증 주체·토큰 값 | `identity.internal.application` |
| 다른 모듈이 받는 인증 principal | `identity.AuthenticatedMember`(공개 계약) |
| 회원 역할·권한 같은 업무 개념 | `identity.internal.domain` |
| admission token 설정·claim decode | `admission.internal` |
| 다른 모듈이 부르는 admission 검증 API | `admission.AdmissionVerifier`/`AdmissionVerification`(공개 계약) |
| HTTP 헤더 이름 같은 API 계약 상수 | 소유 모듈의 `internal.web` |
| 상태를 바꾸는 use case | 소유 모듈의 `internal.application.<기능>.command` |
| 조회 use case | 소유 모듈의 `internal.application.<기능>.query` |
| 트랜잭션 경계 조립, 여러 도메인 서비스 오케스트레이션 | `internal.application` |
| 조회 결과 view와 검색 param | `internal.application.<기능>.query.model` |
| 읽기 전용 조회 port(`*ReadRepository`) | `internal.application.<기능>.query` |
| 엔티티, 값 객체 | `internal.domain.<기능>.model` |
| 도메인 정책과 검증기 | `internal.domain` |
| Aggregate Repository 인터페이스(순수 계약) | `internal.domain.<기능>.repository` |
| Repository 어댑터와 Spring Data 인터페이스 | `internal.infrastructure` |
| 저장 기술에 중립적인 업무 상태 저장 계약 | 필요 주체에 따라 `internal.domain`의 `<기능>.store` 또는 `internal.application`의 포트 |
| 분산락 port(`LockManager`, `LockKey`, `LockOptions`) | `booking.internal.application.lock` |
| 분산락 구현과 Redis key 형식 | `booking.internal.infrastructure.lock` |
| 커밋 후 후속 처리 이벤트 리스너 | `internal.application`의 `@ApplicationModuleListener`(예: `BookingEventListeners`) |
| 모듈이 다른 모듈에 공개하는 이벤트 | 모듈 root(예: `booking.OrderStarted`, `booking.OrderTerminated`) |
| event publication registry 운영(purge·재제출) | `com.ticket.bootstrap.config`(전역 설정, 특정 모듈 소유 아님, 예: `EventPublicationMaintenance`) |
| HTTP 커서 문자열 인코딩·디코딩 | `internal.web`의 cursor 유틸 |
| 커서 위치 타입과 조회 결과 | `internal.application.<기능>.query.model` |
| Querydsl 조회 구현과 조건·정렬·커서 헬퍼 | `internal.infrastructure.<기능>.query` |
| Redis adapter, expiration listener, WebSocket publisher, 외부 HTTP client | `internal.infrastructure` |
| 시드 러너 | 아직 legacy(`com.ticket.core.infra.seed`) |
| `@Scheduled` 트리거와 실행 주기 설정 | 아직 legacy(`com.ticket.bootstrap.worker`). booking으로 옮기는 것은 후속 작업이다 |
| Spring Boot main과 `@Modulith` 선언 | `com.ticket.TicketApplication` |
| 전역 기술 설정(CORS, Swagger, WebSocket 기본 설정) | `com.ticket.bootstrap.config` 또는 아직 legacy(`com.ticket.core.config`) |
| 프레임워크 중립 오류 계약과 예외 전달 기반 | 아직 legacy 전역 구조(`com.ticket.core.support.exception`, `com.ticket.core.support`) — 아래 "오류 처리" 참고 |
| 요청 파라미터 Bean Validation 제약 | `internal.web`의 `controller.docs` 인터페이스 |
| `UseCase.Input` 필수 component 계약 | `internal.application`의 UseCase record와 `support.validation` |
| 도메인 규칙이 판단하는 오류 | legacy 전역 `ErrorType`(모듈이 오류를 아직 소유하지 않는다) |

### 오류 처리는 아직 legacy다

`ProblemDetail` 기반 모듈별 오류 계약을 만들었다가 사용자 결정으로 되돌렸다. 지금은 모든 모듈이
`com.ticket.core.support.exception.ErrorType`/`CoreException`과
`com.ticket.core.support.ApiControllerAdvice`를 그대로 참조한다. 오류를 추가할 때 새 모듈별
카탈로그를 만들지 않는다. 배경은 `docs/adr/0002-module-owned-error-contracts.md`의 갱신된 상태
문단이 원본이다.

### showlike 예외

`showlike`의 write 경로(`AddShowLikeUseCase` 등)만 `showlike.internal`로 옮겨졌다. read 경로
(`GetMyShowLikesUseCase`, `ShowLike` entity 등)는 identity의 `/me/likes`와 catalog의
`likeCount` 조회가 직접 참조하고 있어 legacy(`com.ticket.core.*.showlike`)에 남아 있다. 이
gap을 조용히 옮기지 않는다 — 옮기려면 identity/catalog의 해당 참조를 먼저 끊어야 하고, 순환이
생기지 않는지 `ModularityTests`로 확인해야 한다. 상세는 `docs/architecture.md`의
"showlike 모듈의 경계"와 `src/main/java/com/ticket/showlike/package-info.java`를 본다.

## 4. 자주 틀리는 지점

- **다른 모듈의 `internal` 패키지를 import한다.** 어떤 이유로도 하지 않는다. 필요한 것은 그
  모듈의 공개 계약(interface + record snapshot)으로만 받는다.
- **모듈을 넘는 JPA 연관관계나 DB FK를 만든다.** `@ManyToOne`/`@OneToOne`/`@OneToMany`/
  `@ManyToMany`는 같은 모듈 안에서만 허용한다. 다른 모듈의 aggregate를 참조해야 하면 scalar
  ID(`long performanceId` 등) 컬럼만 갖는다.
- **주기 실행이 필요한 규칙.** 규칙은 `internal.application`의 use case에, `@Scheduled` 트리거는
  아직 legacy(`bootstrap.worker`)에 있다.
- **Redis 상태를 읽는 조회 로직.** 좌석 상태는 DB와 Redis 점유를 합쳐 계산한다. 합치는 규칙은
  booking의 `internal.application` query use case가 소유하고 Redis 조회 자체는 `store` port를
  통한다.
- **Querydsl 조건 생성기.** Querydsl 타입을 다루면 DB 연동 코드이므로 `internal.infrastructure`에
  둔다. use case가 조건을 조립하지 않는다.
- **도메인 타입이 다른 모듈이나 API에 새는 경우.** 요청 DTO는 문자열/scalar로 받고 변환은
  application 경계에서 한다. 다른 모듈에 넘기는 값도 entity가 아니라 scalar ID나 공개
  record다.
- **포트를 실행 모듈이 직접 부르는 경우.** 도메인 포트는 controller나 security 핸들러가 직접
  호출하지 않고 use case가 감싼다.
- **설정값을 두 곳에서 읽는 경우.** 토큰 만료처럼 한 값이 저장소 TTL과 응답에 함께 쓰이면
  발급한 쪽이 결과에 담아 알려준다. 각자 설정을 읽으면 어긋난다.
- **도메인이 이벤트를 발행하거나 트랜잭션을 여는 경우.** `ApplicationEventPublisher`와
  `@Transactional`은 흐름을 엮는 방법이다. 규칙은 `internal.domain`에, 경계와 발행은
  `internal.application`에.
- **다른 모듈이 필요한 경우.** 상대 모듈의 `internal` repository나 store를 직접 부르지 않고
  공개 API(예: `catalog.BookingPolicyLookup`, `identity.MemberLookup`)를 호출한다.
- **대기열.** 대기열 런타임은 형제 저장소 `../ticket-queue`가 소유한다. Core는 회차별
  `entryType` 계산(`catalog`)과 admission token 검증(`admission`)만 담당하며 queue token
  저장소나 만료 핸들러를 두지 않는다.
- **Core Redis의 용도.** seat selection, seat hold(`booking`), refresh token, OAuth2
  one-time auth code(`identity`)뿐이다. 대기열 상태를 Core Redis에 넣지 않는다.

## 5. 구조 테스트가 실패했을 때

| 실패한 테스트 | 먼저 볼 것 |
| --- | --- |
| `com.ticket.ModularityTests` | 모듈 경계 위반. 새 import가 다른 모듈의 `internal`을 향했는지, cross-module JPA 관계가 생겼는지 |
| `<Module>ModuleTests`(예: `BookingModuleTests`) | 해당 모듈이 STANDALONE으로 부트스트랩되는지. 외부 모듈 빈을 mock 없이 요구하지 않는지 |
| `ControllerParameterConstraintTest` | 파라미터 제약 선언 위치 |
| `CoreLayerArchitectureTest` 등 legacy ArchUnit 테스트 | 아직 옮기지 않은 `com.ticket.core`/`bootstrap` 코드의 계층 방향. 이 테스트들은 legacy 정리가 끝나기 전까지 유효하다 |

**테스트를 고쳐서 통과시키지 않는다.** 규칙이 틀렸다고 판단되면 먼저
[architecture.md](../../../docs/architecture.md#아키텍처-규칙)의 근거를 읽고, 규칙을 바꿔야
한다는 사실을 사용자에게 밝힌 뒤 진행한다.

실행 명령은 `/verify`를 본다.
