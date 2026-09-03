# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 위치를 선례로 삼지 말고, 미완료된 구조 이전으로 판단한다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md), 개발 흐름은
[development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 구조

Ticket Core는 **단일 Gradle Spring Boot 프로젝트**다. `bootstrap`/`core:core-api`/`core:core-app`/
`core:core-domain`/`core:core-infra`/`storage:redis-core`/`support:error`/`support:logging`
서브프로젝트는 폐지됐고, `settings.gradle`은 `rootProject.name = 'ticket'` 한 줄만 갖는다.
`integrationTest` source set과 별도 Gradle subproject는 없다 — 모든 테스트가 `src/test`에 있고
실행 특성(Spring 컨텍스트·DB·Redis 필요 여부)으로 구분한다. 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)을 본다.

모듈 경계는 **Gradle subproject가 아니라 Spring Modulith의 Application Module**이 강제한다.
`com.ticket`의 직접 하위 패키지가 닫힌 모듈이고, `com.ticket.ModularityTests`가 경계 위반을 잡는다.
확정 모듈 목록과 실제 `allowedDependencies`는 각 모듈 `package-info.java`가 원본이다.

```text
src/main/java/com/ticket
├── TicketApplication.java   # @Modulith root, main
├── booking/                 # 좌석 판매 상태·Selection·Hold·Order, 공개: BookingMetadata, OrderStarted/OrderTerminated
├── catalog/                 # Show·Performance·Seat·대기열 정책, 공개: BookingPolicyLookup, ShowLookup, CatalogMetadata
├── identity/                # 회원·인증·소셜 로그인·전역 SecurityFilterChain, 공개: AuthenticatedMember, MemberLookup, IdentityMetadata
├── admission/                # admission token 검증, 공개: AdmissionVerifier, AdmissionVerification
├── showlike/                 # Show 좋아요(write 경로만 이동, 아래 "showlike 모듈의 경계" 참고)
├── metadata/                 # catalog/booking/identity 공개 계약을 code/label로 조합
├── shared/                   # 범용 유틸리티(RequiredInput, CursorPage)와 domain-free 전역 기술 설정
├── config/                   # 여러 module의 internal을 참조하는 composition-root module(WebConfig,
│                                WebSocketConfig, HttpServiceConfig, JwtConfig, JpaAuditingConfig 등)
└── core/, bootstrap/, storage/   # core/storage는 아직 모듈로 이동하지 않은 legacy(아래 "레거시 잔존
    범위"). support는 독립 top-level이 아니라 core/support 아래 nested. bootstrap은 legacy가 아니라
    영구 composition-root 예외 자리이며 지금은 비어 있다.
```

각 모듈 root에는 다른 모듈이 쓰는 공개 계약(작은 interface + 불변 `record` snapshot, 이벤트)만
두고, 실제 구현(web/application/domain/infrastructure)은 모두 `<module>.internal` 아래에 둔다.
어떤 모듈도 `Type.OPEN`으로 선언하지 않는다.

## 승인된 의존 DAG

```text
booking   -> catalog, identity, admission
catalog   -> (없음)
identity  -> (없음)
admission -> (없음)
metadata  -> catalog, booking, identity
showlike  -> catalog, identity
shared    -> 모든 모듈이 참조할 수 있는 공유 자리(범용 유틸리티·domain-free 기술 설정)
config    -> identity :: security, identity :: oauth2, identity :: token, identity, booking :: websocket
```

`catalog`/`identity`/`admission`은 다른 업무 모듈에 의존하지 않는 leaf 모듈이다. `booking`과
`showlike`가 그 위에 얹히고, `metadata`는 세 모듈의 공개 계약만 조합한다. `shared`는 어떤 모듈도
참조하지 않고, `config`는 identity/booking의 특정 internal package(`@NamedInterface`로 좁혀 열림)와
shared를 참조하지만 `config`를 참조하는 모듈은 없다. 순환은 없다. 이 DAG를 바꾸려면 먼저
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)을 갱신한다.

**모듈 발견 전략은 기본값(`direct-sub-packages`)이다.** `explicitly-annotated`로 바꾸면
`@ApplicationModule`을 빼먹은 미래 모듈을 조용히 놓칠 수 있어 채택하지 않았다. 대신
`com.ticket.ModularityTests`가 `ApplicationModules.of(TicketApplication.class, <predicate>)`로
아직 이동하지 않은 legacy 패키지만 검증 대상에서 제외한다. 정확한 모듈 집합과 DAG assertion,
`Documenter`, actuator/insight 노출 범위는 모듈 테스트가 계속 다듬고 있는 영역이며, 무엇을
검증하는지는 이 문서가 아니라 `ModularityTests`와 `/verify` 스킬이 원본이다.

## 모듈 간 참조 규칙

- **cross-module JPA 연관관계와 DB FK는 금지한다.** 다른 모듈의 aggregate를 참조해야 하면
  `long` 같은 scalar ID 컬럼만 갖는다. 예: booking이 소유한 `PerformanceSeat`는
  `performanceId`/`seatId`를 scalar 컬럼으로 갖고 catalog의 `Performance`/`Seat` 엔티티를 JPA로
  참조하지 않는다.
- **모듈을 넘는 조회·명령은 상대 모듈이 공개한 API로만 한다.** 다른 모듈의 `internal` 패키지,
  Repository, JPA entity를 직접 import하지 않는다. 공개 API는 작은 단위 interface(예:
  `catalog.BookingPolicyLookup`, `identity.MemberLookup`, `admission.AdmissionVerifier`)와 그
  반환값인 불변 `record` snapshot(`BookingPolicySnapshot`, `MemberStatus`,
  `AdmissionVerification` 등)만 노출한다. JPA entity, Redis/JWT/Spring Web 타입은 공개 계약에
  두지 않는다. 컬렉션은 defensive copy한다.
- **모듈 후속 처리는 커밋 이후 이벤트로 한다.** booking이 발행하는 `OrderStarted`/
  `OrderTerminated`가 그 예다. 자세한 내용은 아래 [이벤트와 후속 처리](#이벤트와-후속-처리)를
  본다.
- **`metadata`는 어떤 모듈의 internal enum/entity/repository도 import하지 않는다.** 각 모듈이
  공개한 `*Metadata` 계약(`CatalogMetadata`, `BookingMetadata`, `IdentityMetadata`)만 주입받는다.

### showlike 모듈의 경계 — 완결되지 않은 상태를 그대로 기록한다

`showlike`는 write 경로(`AddShowLikeUseCase`/`RemoveShowLikeUseCase`/
`GetShowLikeStatusUseCase`와 이를 노출하는 controller)만 `com.ticket.showlike.internal`로
옮겼고, read 경로는 legacy에 남아 있다. identity의 `MemberController`(`GET /me/likes`)와
catalog의 `QuerydslShowDetailReadRepository`(공연 상세 `likeCount`)가 legacy
`com.ticket.core.domain.showlike.model.ShowLike`(entity, 여전히 `Member`/`Show`에
`@ManyToOne`)를 직접 참조하기 때문에, 이 부분을 옮기면 승인된 DAG를 벗어난
`identity ↔ showlike`, `catalog ↔ showlike` 순환이 생긴다. 그래서 의도적으로 legacy에 남겨 뒀고
`ModularityTests`의 legacy 제외 predicate가 검증에서 뺀다.

**이것은 버그가 아니라 기록된 후속 작업이다.** 정리하려면 identity의 `/me/likes`를 showlike로
옮기거나 catalog의 `likeCount` 조회 방식을 바꾼(예: 이벤트 기반 local projection) 뒤에야 `ShowLike`를
scalar ID로 바꾸고 나머지를 옮길 수 있다. 옮기지 못한 정확한 클래스 목록과 이유는
`src/main/java/com/ticket/showlike/package-info.java`(대칭적으로 `catalog`/`identity`의
package-info)에 있다. 이 gap을 해결된 것으로 서술하지 않는다.

## 이벤트와 후속 처리

주문 생성/종료 이후 처리(Redis selection 정리, hold 해제, WebSocket 발행)는 custom outbox가
아니라 Spring Modulith 이벤트와 JPA Event Publication Registry(`spring-modulith-starter-jpa`)로
한다. `OrderStarted`/`OrderTerminated`는 Order/OrderSeat/HoldHistory 저장과 같은 booking DB
transaction 안에서 `ApplicationEventPublisher.publishEvent(...)`로 발행되고,
`@ApplicationModuleListener`(`BookingEventListeners`)가 커밋 이후 처리한다. 정확한 운영
정책(archive, staleness, 재제출 주기와 횟수 상한, 수동 재처리)은
[core-booking-lifecycle.md](core-booking-lifecycle.md)가 원본이고, 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)을 본다. broker
externalization(`@Externalized`, 메시지 브로커)은 현재 범위가 아니다.

**추적 중인 운영 리스크**: registry의 `EVENT_PUBLICATION.serialized_event` 컬럼은 라이브러리
제약으로 `VARCHAR(255)`다. 다중 좌석 주문의 `OrderStarted` 직렬화 결과가 이를 넘으면 event
publication 저장 자체가 실패할 수 있다. 상세는 [ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md#5-spring-modulith-이벤트와-jpa-event-publication-registry)을 본다.

## 오류 처리

**오류는 그 업무를 소유한 모듈이 갖는다.** 각 모듈의 `internal.exception`에 `<Module>ErrorCode`
enum과 예외 클래스가 있고, 예외가 HTTP 상태·E-code·공개 메시지를 생성자에서 확정한다. 모듈마다
`internal.exception.handler`의 얇은 handler(`@Order(HIGHEST_PRECEDENCE)`)가 자기 base 예외 하나만
잡아 응답으로 옮긴다.

어느 모듈의 것도 아닌 오류만 `com.ticket.error`에 있다 — `InvalidRequestException`(E400),
`NotFoundException`(E404), `InternalErrorException`(E500), 예외 base 타입 `TicketException`,
`ErrorCode` interface, 그리고 프레임워크 예외와 fallback을 맡는
`GlobalExceptionHandler`(`@Order(LOWEST_PRECEDENCE)`)다.

응답 봉투(`ApiResponse`/`ErrorMessage`/`ResultType`/`SliceResponse`)는 `com.ticket.shared`에 있다.
`error`가 봉투를 만들어 반환하므로 `error -> shared` 단방향이며, `shared`가 `error`를 참조하면
곧바로 순환이 되어 `ModularityTests`가 실패한다 — `ApiResponse`가 오류 타입을 모른 채 완성된
문자열만 받는 이유이고, 오류를 던지던 `shared.RequiredInput`이 지워진 이유이기도 하다.

E-code 값은 외부 계약이다. `gatling-test`가 `E4001`·`E6000`·`E6003`을 하드코딩하므로 소유 모듈이
바뀌어도 재번호하지 않는다(그래서 E7001은 showlike, E7002는 catalog처럼 대역과 모듈 경계가
어긋난 곳이 있다). 전역 유일성은 `com.ticket.error.ErrorCodeUniquenessTest`가, 모듈 handler가
자기 오류만 잡는지는 `ExceptionHandlerScopeTest`가 강제한다.

배경은 [ADR 0002](adr/0002-module-owned-error-contracts.md)가 원본이다.

## 레거시 잔존 범위

`com.ticket.core`/`com.ticket.storage`/`com.ticket.support`는 아직 Application Module로 옮기지
않은 코드다. `ModularityTests`가 명시 predicate로 검증에서 제외한다. `com.ticket.bootstrap`도 같은
predicate로 제외되지만 성격은 다르다 — legacy가 아니라 영구적인 composition-root 예외 자리다(지금은
production class가 없다). 근거와 경계는
[ADR 0003 §8](adr/0003-spring-modulith-application-module-boundaries.md)이 원본이다.

레거시로 현재 남아 있는 것:

- showlike read 경로(`core.app.showlike`, `core.domain.showlike`, `core.infra.showlike`) — 위
  [showlike 모듈의 경계](#showlike-모듈의-경계--완결되지-않은-상태를-그대로-기록한다) 참고
- `core.infra.seed`의 시드 러너

module 결합이 없는 전역 기술 설정(Swagger, P6Spy, Querydsl, UUID 공급자, Redisson,
event-publication registry 유지보수, scheduling/clock 설정, `CorsProperties`)은 legacy가 아니라
`com.ticket.shared`(공개 계약)/`com.ticket.shared.internal.config`(구현)에 있다 —
[ADR 0003 §6](adr/0003-spring-modulith-application-module-boundaries.md) 참고. 반대로 특정
module의 internal을 직접 참조해야만 배선되는 전역 기술 설정(`WebConfig`/`WebSocketConfig`/
`HttpServiceConfig`/`JwtConfig`/`JpaAuditingConfig`/`SecurityContextAuditorAware`)은 legacy도
아니고 shared도 아니다 — 8번째 Application Module `com.ticket.config`에 있다. identity/booking의
필요한 internal package만 Spring Modulith의 `@NamedInterface`로 좁혀 열어 참조한다 —
[ADR 0003 §9](adr/0003-spring-modulith-application-module-boundaries.md) 참고.

새 코드를 legacy 패키지에 추가하지 않는다. 기존 legacy 코드를 옮기는 작업은 이 문서가 아니라
이후 정리 작업의 범위다.

## 모듈 내부 구조

각 모듈 내부는 계층형 프로젝트가 쓰던 것과 같은 축을 따른다. 물리적으로 별도 Gradle 모듈이
아니라 `<module>.internal` 아래의 패키지일 뿐이다.

| 하위 패키지 | 담는 것 |
| --- | --- |
| `internal.web` | Controller, 요청/응답 DTO, HTTP 커서 문자열 |
| `internal.application` | use case, 트랜잭션 경계, 조회 포트와 결과 view, 그 use case가 필요로 하는 출력 포트(분산락, 이벤트 발행, 외부 provider) |
| `internal.domain` | 엔티티와 값 객체, 상태 enum, 정책과 검증기, Aggregate Repository 계약 |
| `internal.infrastructure` | Repository 어댑터, Querydsl 조회, Redis/Redisson, WebSocket publisher, 외부 HTTP client, 기술 설정 |

작은 모듈(`admission`)은 이 네 하위 패키지를 모두 갖지 않고 `internal` 바로 아래에 평평하게 둘
수 있다. 무엇을 쪼갤지는 실제 복잡도가 결정한다.

포트 소유 기준은 계층형 시절과 같다 — **그 기능을 필요로 하고 의미를 정의하는 쪽**이 소유한다.

| 계약의 성격 | 소유 위치 | 이유 |
| --- | --- | --- |
| aggregate 저장·복원과 업무 명령에 필요한 조회 | `internal.domain` | domain이 필요한 저장 의미와 반환할 도메인 타입을 정의한다 |
| 화면 조회·검색·집계 결과 | `internal.application` | 특정 use case의 읽기 요구이며 aggregate 복원 계약이 아니다 |
| 분산락, 토큰, 외부 provider, publisher/client | `internal.application` | 업무 규칙 자체가 아니라 use case를 실행하기 위한 외부 능력이다 |
| HTTP 입력·출력 계약 | `internal.web` | 전달 방식이 HTTP일 때만 존재한다 |
| JPA, Querydsl, Redis, Redisson, JWT 라이브러리 구현 | `internal.infrastructure` | 교체 가능한 기술 선택이며 안쪽 계약을 구현한다 |

## 패키지와 이름 규칙

기능별 패키지를 기본 축으로 잡는다(`order`, `hold`, `show`, `performanceseat` 등). 기능 안의
하위 패키지는 아래 패턴을 쓴다.

| 하위 패키지 | 담는 것 |
| --- | --- |
| `model` | 엔티티와 값 객체 |
| `repository` | Aggregate Repository 인터페이스(순수 계약) |
| `store` | 저장 기술에 중립적인 업무 상태 저장 계약 |
| `query` | 정책 판정에 쓰는 도메인 read model, 또는 조회 use case·포트·결과 view |
| `command` | 상태를 바꾸는 use case와 트랜잭션 조립 |

### 이름 규칙

- Querydsl 조회 구현은 `Querydsl` 접두사를 붙여 포트와 구분한다.
  `QuerydslShowListReadRepository implements ShowListReadRepository`
- Aggregate Repository 어댑터는 `*RepositoryAdapter`.
- 어댑터가 안에서 쓰는 Spring Data 인터페이스는 `SpringData*JpaRepository`.

```text
internal.domain
  OrderRepository                 (순수 Java 계약)
          ↑ implements
internal.infrastructure
  OrderRepositoryAdapter          (계약과 Spring Data를 연결)
          ↓ delegates
  SpringDataOrderJpaRepository    (JpaRepository, @Query, @Lock)
```

- `View`는 조회 경계의 화면/응답용 projection, `Snapshot`은 특정 시점의 읽기 결과(모듈 공개
  API에서는 cross-module 스냅샷을 뜻한다)다.
- `Row`는 저장소 조회 한 행, `Output`은 use case가 반환하는 결과다.
- `Param`은 조회 조건 구성값, `Criteria`는 검색 조건, `Event`는 발생한 사실, `Request`는
  외부 입력이다.

## 코드 위치 결정표

책임별 위치 표는 **`/place-code` 스킬**이 원본이다(`.claude/skills/place-code/SKILL.md`).
판단 기준이 문서와 스킬로 갈려 있으면 한쪽만 자라고 어긋난다.

## 기능별 구조 원칙

### Controller

Controller는 가능한 한 얇게 유지한다.

- 요청 검증
- 인증 principal 추출(`identity.AuthenticatedMember`)
- use case 호출
- 응답 포맷 반환

비즈니스 규칙이나 저장소 접근은 Controller에 두지 않는다.

### Command / Query

- `command`: 상태를 변경하는 use case
- `query`: 조회 전용 use case와 조회 port

Repository 한 번 호출한 뒤 not-found 예외만 던지는 `*Finder` 계층은 두지 않는다. command/query
use case가 해당 Repository를 직접 호출한다.

Repository는 두 종류로 나뉜다.

**Aggregate Repository**(`internal.domain`): aggregate의 저장과 복원, 업무 명령에 필요한 조회를
맡는다. 도메인 타입만 반환하고, JPA 구현은 `internal.infrastructure`의 어댑터가 맡는다. 조회
실패는 `Optional`이나 `boolean`으로 돌려주고 오류는 호출하는 유스케이스가 고른다. 예외를 던지는
`getXxx`·`requireXxx` 편의 메서드를 두지 않는다([validation.md](validation.md)).

**Read Repository**(`internal.application`): 화면·검색·상세·집계·커서 페이징 같은 읽기 전용
조회를 맡는다. app이 소유한 immutable read model이나 원시 타입을 반환하며 domain entity 반환을
강제하지 않는다.

읽기 경로는 domain entity를 거치지 않아도 된다. 상태를 바꾸지 않기 때문이다. 반대로 상태를
바꾸는 command는 반드시 domain aggregate와 Aggregate Repository를 거쳐 불변식을 다시
검증한다.

## 저장소 구조

### RDB

주 영속 저장소는 RDB다. 업무 상태를 표현하는 JPA entity는 각 모듈의 `internal.domain`에 둔다.
Spring Data 인터페이스, JPQL, Querydsl, `EntityManager`, DB lock annotation과 Repository
adapter는 `internal.infrastructure`에 둔다.

Flyway migration은 module 소유권을 따른다. 기존 이력(V2~V8)은 내용 변경 없이
`db/migration/__root`, `db/migration-vendor/{h2,oracle}/__root`에 있고, 모듈이 소유하는 새
schema 변경은 `db/migration/{module}`, `db/migration-vendor/{h2,oracle}/{module}`에 module별로
독립 버전을 매겨 추가한다(`spring.modulith.runtime.flyway-enabled=true`). 상세 절차는
[operations.md](operations.md#db-마이그레이션)를 본다.

### Redis

Redis는 짧은 수명 상태와 동시성 제어, 토큰 저장, 실시간 좌석 처리에 사용한다. 대기열 상태는
`ticket-queue`가 별도 Redis에서 관리하며, 현재 애플리케이션과 배포 설정은 단일 Redis 서버를
사용한다.

주요 대상:

- seat selection, seat hold(`booking`)
- refresh token, OAuth2 one-time auth code(`identity`)

Redis 구현체는 소유 모듈의 `internal.infrastructure`에 위치한다.

## 실시간 처리

### 좌석 선택 / 홀드

- 좌석 선택과 홀드는 Redis TTL을 사용한다.
- 만료 시 listener 및 보정용 scheduler로 후속 정리를 수행한다.
- 좌석 상태 변경은 WebSocket 메시지로 전파한다.
- Redis 만료 listener는 worker 2개와 유한 queue를 사용해 TTL 폭주가 DB 동시성 폭주로 번지는
  것을 막는다.
- 주문 생성 커밋 이후 selection 정리와 HELD 발행은 `OrderStarted` 이벤트의
  `@ApplicationModuleListener`가 실행한다. 실패는 Event Publication Registry가 FAILED로 기록해
  재시도한다.
- `@Scheduled` 트리거와 실행 주기·활성화 설정은 `worker.*` 설정이 소유한다.
- 업무 판단과 상태 전이 오케스트레이션은 booking의 `internal.application`, Redis listener·
  WebSocket publisher와 기술 executor 구현은 `internal.infrastructure`가 소유한다.

### 주문 후처리 보정

- 즉시 이벤트 처리만으로 끝내지 않고, Event Publication Registry의 재제출·staleness 정책이
  보정 역할을 겸한다.
- 주문 생성/종료 시 상태 저장과 `OrderStarted`/`OrderTerminated` 발행은 같은 booking DB
  트랜잭션에서 일어난다.
- 커밋 후 처리(Redis selection/hold 변경, WebSocket 발행)는 DB connection을 점유하지 않는다.
- 상세 흐름은 [core-booking-lifecycle.md](core-booking-lifecycle.md)를 기준으로 한다.

## 동시성 제어

분산락은 명시적인 포트 호출로 처리한다. 어노테이션과 SpEL로 감추지 않는다.

현재 구현 위치:

- 포트: `com.ticket.booking.internal.application.lock.LockManager`
- 잠글 대상: `LockKey`, `LockScope` — 업무 의미만 담고 key 문자열은 담지 않는다
- 획득 방식: `LockOptions` — 대기 시간, 임대 시간, 실패 로그 수준
- 구현: `com.ticket.booking.internal.infrastructure.lock.RedissonLockManager`
- key 형식: `com.ticket.booking.internal.infrastructure.lock.RedissonLockKeyFormatter`

적용 예:

- 동일 회원/공연 조합의 중복 주문 시작 방지 (`LockScope.ORDER_START`)
- 동일 좌석 동시 점유 방지 (`LockScope.SEAT`)

락을 먼저 잡고 그 안에서 트랜잭션을 시작한다. 커밋이 끝난 뒤에 락이 풀린다. 좌석 락은 Redis
hold를 만드는 구간에만 건다. DB 트랜잭션 동안 좌석 락을 쥐고 있으면 connection 경합이 좌석
경합으로 번진다.

## 아키텍처 규칙

권고가 아니라 **테스트가 실패시키는 규칙**이다. 위반하면 다른 테스트의 통과 여부와 관계없이
완료가 아니다.

**규칙 본문은 테스트 코드가 원본이고 여기 옮겨 적지 않는다.** 옮겨 적는 순간 테스트와 어긋나기
시작하고, 어긋난 쪽을 사람이 먼저 믿는다. 모듈 구조 검증은 `com.ticket.ModularityTests`가 담당한다.
어떤 테스트가 무엇을 고정하는지는 [testing.md의 구조 테스트](testing.md#구조-테스트), 실행 명령은
`/verify`, 실패했을 때 볼 곳은 `/place-code`가 원본이다.

규칙을 바꿔야 한다고 판단되면 테스트를 고쳐 통과시키지 말고, 규칙이 틀렸다는 사실을 먼저 밝힌다.

## 경계 판단에서 자주 틀리는 지점

판단 절차, 책임별 위치, 자주 틀리는 지점은 **`/place-code` 스킬**이 원본이다
(`.claude/skills/place-code/SKILL.md`). 코드를 새로 두거나 옮길 때, 구조 테스트가 실패했을 때
그 스킬이 로드된다.

이 문서는 각 모듈이 **왜** 그렇게 나뉘었는지를 갖는다. 어디에 두는지는 스킬이 갖는다.

## 아키텍처 리뷰 질문

- 이 코드의 책임이 web, application, domain, infrastructure 중 어디에 속하는가
- 같은 검증이 두 계층에서 같은 목적으로 중복 실행되지 않는가([validation.md](validation.md))
- 다른 모듈의 `internal` 패키지, Repository, JPA entity를 직접 참조하지 않는가
- 모듈을 넘는 JPA 연관관계나 DB FK가 새로 생기지 않았는가
- 새 공개 계약이 JPA entity, Redis/JWT/Spring Web 타입을 노출하지 않는가
- 새 패키지가 기능 중심 축(`command`/`query`/`model`/`repository`/`store`)을 따르는가
- DB 상태와 Redis 상태를 합치는 규칙의 소유자가 한 곳인가
- 새 추상화가 실제 경계를 보호하는가, 사용하지 않는 계층을 늘리기만 하는가

## 세부적으로 아직 정리하지 않은 것

세부적으로 아직 정리하지 않은 이름과 구조는 [기술 부채 문서](technical-debt.md)에 기록한다. 각
항목이 가리키는 설계 문제(app이 provider 세부사항을 안다, 계층/책임 혼재 등)는 Spring Modulith
전환과 무관하게 여전히 유효하며, 경로는 전환 후 실제 module 위치로 갱신돼 있다.
