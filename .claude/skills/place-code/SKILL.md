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
| Show, Performance, Venue, Seat, Grade(등급 코드·이름), PerformanceGrade(회차별 등급 가격·표시 순서), 예매 가능 시간, Hold 한도, 대기열 정책(`PerformanceQueuePolicy`), 공연·회차·좌석 조회 | `catalog` |
| Order에 대한 결제 시도(Payment)의 생명주기 | `payment` |
| 결제 확정으로 발급되는 Ticket(입장 권리)의 생명주기 | `ticketing` |
| Member, 소셜 로그인, OAuth2, 비밀번호, access/refresh token, 전역 `SecurityFilterChain` | `member` |
| admission token 설정·decode·검증 | `admission` |
| Show 좋아요(찜) 개수·추가·삭제·내 찜 목록 | `catalog`(Show의 부가 속성으로 취급, "showlike 흡수" 참고) |
| 둘 이상 독립 모듈이 의미 동일하게 공유하고 프로토콜·프레임워크 결합이 없는 호출 대상 계약(`UuidSupplier`, `CorsProperties`, `CursorPage`) — **bean을 등록하는 코드는 두지 않는다** | `shared` |
| REST 응답 표현 계약(응답 봉투 `ApiResponse`/`ErrorMessage`/`ResultType`, 무한스크롤 `SliceResponse`) — bean은 두지 않는다 | `web` |
| 어느 모듈의 것도 아닌 공통 오류(E400·E404·E500), 예외 base 타입, 전역 handler | `error` |
| 전역 `@Configuration` 전부(domain-free 기술 설정 + 여러 module의 internal을 참조해야만 배선되는 설정) | `config` |
| 여러 module의 테이블을 raw SQL로 적재하는 시드 러너 | `seed` |

모듈을 잘못 고르면 그 다음 판단이 전부 무의미하다. 애매하면 "이 코드가 사라지면 무엇이 먼저
깨지는가"를 먼저 모듈 단위로 묻는다.

**아직 모듈로 옮기지 않은 legacy 코드**(`com.ticket.core`/`storage`(그리고 `core` 아래 nested된
`core.support`))가 있다. 새 코드를 여기 추가하지 않는다 — 새 기능은 해당하는 모듈로 바로 만든다.
legacy 코드를 옮기는 작업 자체는 범위가 크므로 먼저 사용자와 범위를 정한다.

`com.ticket.bootstrap`은 legacy가 아니다 — 여러 module의 internal을 동시에 참조해야만 배선할 수
있는 코드를 위한 영구 composition-root 예외 자리이고, 지금은 production class가 하나도 없다.
업무 module을 모르는 전역 배선은 `config`가 맡고, **특정 module의 물건을 등록하는 배선은 그
module이 자기 안에서 한다**(아래 3절). 둘 다로 감당할 수 없을 만큼 결합이 크거나 임시적인 배선이
생길 때만 이 자리를 쓴다.

## 1. 모듈을 골랐으면 계층을 고른다

같은 판단축을 모듈 내부에도 그대로 쓴다.

| 쓰는 것 | 두는 곳 |
| --- | --- |
| HTTP 요청·응답, 쿠키, WebSocket 진입, controller.docs | `<module>.internal.web` |
| use case, 트랜잭션 경계, 여러 서비스 조립, 조회 포트와 결과 view | `<module>.internal.application` |
| 엔티티, 값 객체, 도메인 정책, `*Finder`, port 선언 | `<module>.internal.domain` |
| Querydsl, Redis, JWT, 암호화, 외부 HTTP, scheduler, AOP | `<module>.internal.infrastructure` |
| 다른 모듈이 쓸 공개 계약(작은 interface + 불변 record snapshot, 공개 이벤트) | 모듈 root(예: `catalog.ShowLookup`, `booking.OrderStarted`) |

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
| OAuth2 설정, 전역 security filter chain, 인증 필터 | `member.internal`의 security 설정 |
| JWT 발급·검증 구현 | `member.internal`의 token 구현 |
| 인증 흐름의 포트와 인증 주체·토큰 값 | `member.internal.application` |
| 다른 모듈이 받는 인증 principal | `member.AuthenticatedMember`(공개 계약) |
| 회원 역할·권한 같은 업무 개념 | `member.internal.domain` |
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
| event publication registry 운영(purge·재제출) | `com.ticket.config.internal`(예: `EventPublicationMaintenance`) |
| HTTP 커서 문자열 인코딩·디코딩 | `internal.web`의 cursor 유틸 |
| 커서 위치 타입과 조회 결과 | `internal.application.<기능>.query.model` |
| Querydsl 조회 구현과 조건·정렬·커서 헬퍼 | `internal.infrastructure.<기능>.query` |
| Redis adapter, expiration listener, WebSocket publisher, 외부 HTTP client | `internal.infrastructure` |
| 시드 러너 | `seed.internal` |
| `@Scheduled` 트리거와 실행 주기 설정 | 소유 모듈의 `internal.infrastructure.worker`(예: `booking.internal.infrastructure.worker.OrderExpirationTrigger`) |
| Spring Boot main과 `@Modulith` 선언 | `com.ticket.TicketApplication` |
| module 결합 없는 전역 기술 설정(Swagger, P6Spy, Querydsl, UUID 공급자, Redisson, JPA auditing 등록, scheduling/clock) | `com.ticket.config.internal`, 그 공개 계약(예: `UuidSupplier`)은 `com.ticket.shared` |
| member의 공개 계약만 쓰는 전역 배선(JpaAuditingConfig 등) | `com.ticket.config`(`allowedDependencies = {"member"}`) |
| **특정 module의 물건을 Spring에 등록하는 배선**(argument resolver, `@ConfigurationProperties`, HTTP client, STOMP 인터셉터) | **그 module의 `internal`에 자기 `@Configuration`을 둔다** — Spring이 `WebMvcConfigurer`·`WebSocketMessageBrokerConfigurer`를 여러 개 모아 적용하므로 module마다 하나씩 둘 수 있다. 예: `member.internal.infrastructure.security.MemberWebMvcConfig`, `booking.internal.infrastructure.websocket.WebSocketConfig`. 전역 설정 module이 대신 등록해 주면 `@NamedInterface`로 internal을 열어야 하므로 하지 않는다 |
| `shared`에 `@Configuration`을 두려는 판단 | 하지 않는다 — `com.ticket.shared.SharedModulePurityTest`가 막는다. bean 등록은 `config`가 소유한다 |
| 프레임워크 중립 오류 계약과 예외 전달 기반 | `com.ticket.error`(`ErrorCode`, `TicketException`, 공통 예외, `handler`) — 아래 "오류 처리" 참고 |
| 요청 파라미터 Bean Validation 제약 | `internal.web`의 `controller.docs` 인터페이스 |
| `UseCase.Input` 필수 component 계약 | `internal.application`의 UseCase record compact constructor(공통 유틸 없이 직접 판정) |
| 도메인 규칙이 판단하는 오류 | 소유 모듈의 `internal.exception`(`<Module>ErrorCode` + 예외 클래스) |
| Grade(재사용 가능한 등급 코드·이름, 가격 없음) | `catalog.internal.domain.grade` |
| PerformanceGrade(회차별 등급 가격·표시 순서, 가격의 원본) | `catalog.internal.domain.performance` |
| PerformanceSeat의 `performanceGradeId`/`unitPrice`/`version`(catalog `PerformanceGrade.price`의 snapshot, 낙관적 락) | `booking.internal.domain.performanceseat.model` |
| Payment entity·상태(`READY`/`PROCESSING`/`FAILED`), Order 참조는 scalar `orderId` | `payment.internal.domain.payment` |
| Ticket entity·상태, OrderSeat/Member 참조는 scalar `orderSeatId`/`ownerMemberId` | `ticketing.internal.domain.ticket` |

### 오류는 그 업무를 소유한 모듈이 갖는다

**오류를 추가할 때 전역 카탈로그를 찾지 않는다.** 그 업무를 소유한 모듈의
`<module>/internal/exception/`에 예외 클래스를 만들고, 코드는 같은 패키지의 `<Module>ErrorCode`
enum에 추가한다. 예외가 HTTP 상태·E-code·공개 메시지를 생성자에서 확정하므로 handler는 고치지
않는다.

```
com/ticket/<module>/internal/exception/
  <Module>ErrorCode.java          enum implements com.ticket.error.ErrorCode
  <Module>Exception.java          abstract extends com.ticket.error.TicketException
  <구체 예외>.java                 상태·코드·메시지를 생성자에서 확정
  handler/<Module>ExceptionHandler.java   @Order(HIGHEST_PRECEDENCE), base 타입 하나만 잡는다
```

어느 모듈의 것도 아닌 오류(E400 잘못된 요청, E404 없음, E500 내부 오류)만 `com.ticket.error`에
있고, 전역 `GlobalExceptionHandler`(`@Order(LOWEST_PRECEDENCE)`)가 프레임워크 예외와 fallback을
맡는다. 응답 봉투(`ApiResponse`)는 `com.ticket.web`에 있다 — `error`가 봉투를 만들므로
`web`이 `error`를 참조하면 순환이 된다.

**메시지와 data를 바꿔 담지 않는다.** `message`는 오류마다 고정된 공개 문구이고, 어느 요청이
막혔는지를 좁히는 값은 `data`에 넣는다. 각각 응답의 `error.message`와 `error.data`가 된다.

**자주 틀리는 것**: 모듈 handler가 `RuntimeException` 같은 넓은 타입을 잡으면 다른 모듈의 오류까지
삼킨다(Spring은 order 순으로 매칭되는 첫 advice에서 멈춘다). E-code 값은 외부 계약이라
`gatling-test`가 하드코딩하므로 모듈이 바뀌어도 재번호하지 않는다. 두 규칙 모두
`com.ticket.error.ExceptionHandlerScopeTest`와 `ErrorCodeUniquenessTest`가 강제한다.

배경은 `docs/adr/0002-module-owned-error-contracts.md`가 원본이다.

### payment/ticketing은 entity-only 모듈이다

`payment`, `ticketing`은 ADR 0005로 신설된 닫힌 Application Module이지만, 지금은 entity/schema/
repository와 구조·중복 방지 테스트까지만 있다. 그래서 실제로 쓰이는 계층은 `internal.domain`
(entity, 상태 enum, Repository 인터페이스)과 `internal.infrastructure`(Repository 어댑터,
Spring Data JPA 인터페이스)뿐이다. `internal.application`(use case)과 `internal.web`
(controller)은 아직 없다 — PG client, 결제 승인/실패/취소 API, callback/webhook, 자동 티켓
발급, QR/입장/사용/양도 API가 추가되는 후속 단계에서 채워진다. 두 모듈 모두
`allowedDependencies = {}`인 leaf module이다(`payment -> 없음`, `ticketing -> 없음`) — 다른
업무 모듈을 import하지 않는다. `booking`의 Order/OrderSeat, `member`의 Member를 참조할 때도
scalar `orderId`/`orderSeatId`/`ownerMemberId` 컬럼일 뿐 JPA 연관관계가 아니다. 실제 PG 정산
(`payment -> booking`)이나 `OrderConfirmed` 구독(`ticketing -> booking`) 같은 공개 계약 의존은
그 기능을 구현하는 후속 단계에서만 추가한다 — 지금 빈 인터페이스나 가짜 호출로 미리 만들지
않는다. 배경은 `docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md`
§3~4가 원본이다.

### showlike 흡수

찜(개수·추가/삭제·내 찜 목록)은 `showlike`라는 별도 module이 아니라 `catalog.internal`에
있다. `AddShowLikeUseCase`/`RemoveShowLikeUseCase`/`GetShowLikeStatusUseCase`/
`GetMyShowLikesUseCase`와 `ShowLike` entity 모두 catalog 소유다. 좋아요 개수는
`Show.viewCount`와 같은 성격의 파생 지표라는 판단으로, catalog가 회원 존재 확인을 위해
member의 `MemberLookup`을 참조한다(단방향). 상세 배경은
`docs/adr/0003-spring-modulith-application-module-boundaries.md` §11을 본다.

## 4. 자주 틀리는 지점

- **다른 모듈의 `internal` 패키지를 import한다.** 어떤 이유로도 하지 않는다. 필요한 것은 그
  모듈의 공개 계약(interface + record snapshot)으로만 받는다.
- **모듈을 넘는 JPA 연관관계나 DB FK를 만든다.** `@ManyToOne`/`@OneToOne`/`@OneToMany`/
  `@ManyToMany`는 같은 모듈 안에서만 허용한다. 다른 모듈의 aggregate를 참조해야 하면 scalar
  ID(`long performanceId` 등) 컬럼만 갖는다.
- **주기 실행이 필요한 규칙.** 규칙은 `internal.application`의 use case에, `@Scheduled` 트리거는
  소유 모듈의 `internal.infrastructure.worker`에 둔다(예: `booking.internal.infrastructure.worker.OrderExpirationTrigger`).
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
  공개 API(예: `catalog.BookingPolicyLookup`, `member.MemberLookup`)를 호출한다.
- **대기열.** 대기열 런타임은 형제 저장소 `../ticket-queue`가 소유한다. Core는 회차별
  `entryType` 계산(`catalog`)과 admission token 검증(`admission`)만 담당하며 queue token
  저장소나 만료 핸들러를 두지 않는다.
- **Core Redis의 용도.** seat selection, seat hold(`booking`), refresh token, OAuth2
  one-time auth code(`member`)뿐이다. 대기열 상태를 Core Redis에 넣지 않는다.

## 5. 구조 테스트가 실패했을 때

| 실패한 테스트 | 먼저 볼 것 |
| --- | --- |
| `com.ticket.ModularityTests` | 모듈 경계 위반. 새 import가 다른 모듈의 `internal`을 향했는지, cross-module JPA 관계가 생겼는지 |
| `<Module>ModuleTests`(예: `BookingModuleTests`) | 해당 모듈이 STANDALONE으로 부트스트랩되는지. 외부 모듈 빈을 mock 없이 요구하지 않는지 |
| `ControllerParameterConstraintTest` | 파라미터 제약 선언 위치 |
| `CoreLayerArchitectureTest` 등 legacy ArchUnit 테스트 | 아직 옮기지 않은 `com.ticket.core` 코드의 계층 방향. 이 테스트들은 legacy 정리가 끝나기 전까지 유효하다 |

**테스트를 고쳐서 통과시키지 않는다.** 규칙이 틀렸다고 판단되면 먼저
[architecture.md](../../../docs/architecture.md#아키텍처-규칙)의 근거를 읽고, 규칙을 바꿔야
한다는 사실을 사용자에게 밝힌 뒤 진행한다.

실행 명령은 `/verify`를 본다.
