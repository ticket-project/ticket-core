# 아키텍처 기준

이 문서는 현재 모듈 구조와 패키지 경계를 정리한다. 개발 흐름은 [development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 구조

현재 프로젝트는 멀티 모듈 Gradle 구조이며, 실행/API, 애플리케이션, 도메인, 기술 구현을 계층으로 나눈
모듈러 모놀리스다.

실제 포함 모듈은 다음 7개다.

- `core:core-domain`
- `core:core-app`
- `core:core-infra`
- `core:core-api`
- `storage:redis-core`
- `support:error`
- `support:logging`

의존 방향은 아래와 같고 `CoreLayerArchitectureTest`가 강제한다.

```text
core-api ──→ core-app ──→ core-domain
   │             ↑             ↑
   └→ core-infra ┴─────────────┴──→ storage:redis-core
전 모듈 ──→ support:error
```

`core-infra`가 `core-app`을 향하는 것은 스케줄러와 커밋 후 리스너가 use case를 호출하기 때문이며,
어댑터가 애플리케이션을 구동하는 정상 방향이다.

## 모듈 책임

### `core:core-api`

Spring Boot 실행 모듈이다.

주요 책임:

- REST Controller
- 요청/응답 DTO
- Spring Security, JWT, OAuth2 설정
- WebSocket 진입 설정
- Admission token 검증 같은 예매 API 진입 제어
- 공통 응답 포맷

의존:

- `core:core-app`
- `core:core-infra`
- `support:error`
- `support:logging`

`core-domain`은 프로덕션 의존에서 뺐다. 실행 모듈은 use case를 거쳐 도메인에 닿는다. 컨트롤러 계약
테스트가 도메인 픽스처를 쓰므로 `testImplementation`으로만 남긴다.

### `core:core-app`

애플리케이션 계층이다. 도메인 규칙과 어댑터를 엮어 실제 서비스 흐름을 만든다.

주요 책임:

- 기능별 use case (`*UseCase`)
- 트랜잭션 경계와 오케스트레이션 (`*TxService`, `*TransactionService`, `*Coordinator`,
  `*Executor`, `*Processor`)
- 조회 포트와 조회 결과 view/param
- 커서 페이징 유틸 (`support.cursor`)
- 인증 주체 값과 액세스 토큰 읽기 포트 (`auth.token`의 `AuthenticatedMember`, `AccessTokenReader`)

의존:

- `core:core-domain`
- `support:error`

### `core:core-domain`

핵심 비즈니스 규칙 모듈이다. 예매 도메인의 엔티티, 값 객체, 정책, 포트만 둔다.

주요 책임:

- JPA entity와 값 객체
- 도메인 정책과 검증기 (`BookingPolicyValidator`, `ShowCursorPolicy` 등)
- 도메인 조회 서비스 (`*Finder`)
- repository 인터페이스와 Redis/WebSocket/외부 HTTP port
- 인증 포트와 값 (`auth`의 `AuthTokenManager`, `RefreshTokenStore`,
  `PasswordService`, `AuthRefreshToken`, `IssuedAuthTokens`, `OAuth2UserInfo`)

허용된 Spring은 `data`(JPA repository, auditing)와 `stereotype`(빈 선언)뿐이다.
트랜잭션 경계, 이벤트 발행, 표현식 해석은 흐름을 엮는 방법이므로 여기에 두지 않는다.
- outbox 엔티티, 기록기, 상태 enum
- 분산락 어노테이션

엔티티는 JPA 애노테이션을 갖는다. 이것이 유일하게 허용된 기술 의존이며, 그 밖의 Spring 타입은
`CoreLayerArchitectureTest`가 막는다. use case는 이 모듈에 두지 않는다.

### `core:core-infra`

기술 구현 모듈이다. `core-domain`의 port를 구현하고, Redis/WebSocket/외부 HTTP/AOP/기술 설정을 담당한다.

주요 책임:

- Querydsl 조회 구현과 조건·정렬·커서 헬퍼
- JWT 발급·검증 (`auth.token`)
- Redis 기반 store adapter
- Redis key expiration listener와 handler
- WebSocket seat event publisher
- Kakao HTTP interface client
- 암호화와 대기열 입장 토큰 어댑터
- scheduler와 커밋 후 리스너
- 분산락 AOP
- Querydsl, P6Spy 설정

의존:

- `core:core-app`
- `core:core-domain`
- `storage:redis-core`
- `support:error`
- Spring Web / WebSocket / Security / Data JPA / Querydsl / P6Spy / JJWT

### `storage:redis-core`

Redis 관련 공통 의존성을 제공한다.

주요 책임:

- `spring-boot-starter-data-redis`
- `redisson-spring-boot-starter`

실제 비즈니스 Redis 구현은 `core-infra`의 기능별 adapter에 둔다.

### `support:error`

모든 모듈이 쓰는 공통 예외를 제공한다. `com.ticket.support.error` 패키지에
`CoreException`, `ErrorType`, `ErrorCode`, `ErrorMessage`, `AuthException`, `NotFoundException`을 둔다.

`ErrorType`이 `HttpStatus`를 갖고 있어 `spring-web`에 의존한다. 도메인 예외와 HTTP 표현의 분리는
후속 과제로 남아 있다.

### `support:logging`

로깅 관련 공통 설정 리소스를 제공한다.

## 패키지 구조

### `core-api`

- `com.ticket.core.api.controller`
  - HTTP endpoint
- `com.ticket.core.api.controller.docs`
  - Swagger 문서 인터페이스
- `com.ticket.core.api.controller.request`
  - 요청 DTO
- `com.ticket.core.api`
  - HTTP 계약 상수 (`AdmissionHeaders`)
- `com.ticket.core.config`
  - 웹, JPA Auditing, WebSocket, 시스템 값 설정
- `com.ticket.core.config.security`
  - JWT 발급·검증, OAuth2, 인증/인가 구성, 인증 주체 `AuthenticatedMember`
- `com.ticket.core.support.response`
  - 공통 응답 래퍼

### `core-domain`

기능별 패키지를 기본 축으로 잡는다.

- `auth`
- `member`
- `order`
- `hold`
- `queue` (mode/level 값)
- `show`
- `showlike`
- `performance`
- `performanceseat`
- `seat`
- `commoncode`

기능 내부 하위 패키지 패턴:

- `model`: 엔티티와 값 객체
- `repository`: JPA repository 인터페이스
- `store`: Redis 등 임시 상태 저장 port
- `query`: 도메인 조회 서비스(`*Finder`)와 조회 port
- `command`: 도메인 서비스와 outbox
- `support`: 도메인 보조 컴포넌트

`core-domain`은 구현체 패키지로서의 `infra`를 두지 않는다. 기술 구현은 `core-infra`에 둔다.
use case는 `core-app`에 둔다.

### `core-app`

`core-domain`과 같은 기능 축을 쓰되 애플리케이션 관심사만 담는다.

- `com.ticket.core.app.<기능>.command`
  - 상태를 바꾸는 use case, 트랜잭션 조립
- `com.ticket.core.app.<기능>.query`
  - 조회 use case와 조회 port
- `com.ticket.core.app.<기능>.query.model`
  - 조회 결과 view와 검색 param
- `com.ticket.core.app.auth`
  - 인증 흐름 use case, 인증 주체 값(`AuthenticatedMember`)과 `AccessTokenReader` 포트
- `com.ticket.core.app.support.cursor`
  - 커서 인코딩과 슬라이스

### `core-infra`

`core-domain`의 패키지 구조를 따라 adapter를 배치하되, 물리 위치는 별도 Gradle 모듈이다.

예시:

- `com.ticket.core.infra.lock`
- `com.ticket.core.infra.config`
- `com.ticket.core.infra.redis`
- `com.ticket.core.infra.support`
- `com.ticket.core.infra.auth`, `.auth.oauth2`, `.auth.token`
- `com.ticket.core.infra.queue`
- `com.ticket.core.infra.hold`
- `com.ticket.core.infra.order`, `.order.query`
- `com.ticket.core.infra.show.query`
- `com.ticket.core.infra.showlike.query`
- `com.ticket.core.infra.performance.query`
- `com.ticket.core.infra.performanceseat`, `.performanceseat.query`

Querydsl 조회 구현은 `Querydsl` 접두사를 붙여 포트와 구분한다.
예: `QuerydslShowListQueryRepository implements ShowListQueryRepository`

## 코드 위치 결정표

새 코드는 아래 기준으로 위치를 정한다. 판단이 갈리면 "이 코드가 사라졌을 때 무엇이 먼저 깨지는가"를 본다.

| 책임 | 위치 |
| --- | --- |
| HTTP endpoint, 요청 검증, 인증 주체 추출, 응답 포맷 | `core-api` |
| Swagger 문서 인터페이스 | `core-api` 의 `controller.docs` |
| OAuth2 설정, security filter chain, 인증 필터 | `core-api` 의 `config.security` |
| JWT 발급·검증 구현 | `core-infra` 의 `auth.token` |
| 인증 포트와 값 객체 | `core-domain` 의 `auth` |
| HTTP 헤더 이름 같은 API 계약 상수 | `core-api` 의 `api` |
| 상태를 바꾸는 use case | `core-app` 의 `<기능>.command` |
| 조회 use case | `core-app` 의 `<기능>.query` |
| 트랜잭션 경계 조립, 여러 도메인 서비스 오케스트레이션 | `core-app` |
| 조회 결과 view와 검색 param | `core-app` 의 `<기능>.query.model` |
| 조회 port | 그것을 쓰는 쪽. use case가 쓰면 `core-app`, `*Finder`가 쓰면 `core-domain` |
| 엔티티, 값 객체 | `core-domain` 의 `<기능>.model` |
| 도메인 정책과 검증기, `*Finder` | `core-domain` |
| JPA repository 인터페이스 | `core-domain` 의 `<기능>.repository` |
| Redis 같은 임시 상태 저장 port | `core-domain` 의 `<기능>.store` |
| outbox 엔티티·기록기·상태 enum | `core-domain` 의 `<기능>.command` |
| outbox 트랜잭션 경계와 실행 조립 | `core-app` 의 `<기능>.command` |
| Querydsl 조회 구현과 조건·정렬·커서 헬퍼 | `core-infra` 의 `<기능>.query` |
| Redis adapter, expiration listener, WebSocket publisher, 외부 HTTP client | `core-infra` |
| 암호화, 대기열 입장 토큰 검증 같은 포트 구현 | `core-infra` |
| scheduler, `@TransactionalEventListener`, background executor 설정 | `core-infra` |
| 분산락 AOP 실행부 | `core-infra` 의 `lock` |
| Querydsl, P6Spy 같은 기술 설정 | `core-infra` 의 `config` |
| 공통 예외 | `support:error` |

## 기능별 구조 원칙

### Controller

Controller는 가능한 한 얇게 유지한다.

- 요청 검증
- 인증 principal 추출
- use case 호출
- 응답 포맷 반환

비즈니스 규칙이나 저장소 접근은 Controller에 두지 않는다.

### Command / Query

- `command`: 상태를 변경하는 use case (`core-app`)
- `query`: 조회 전용 use case와 조회 port (`core-app`)

조회는 포트와 구현을 나눈다. 포트와 반환 view는 `core-app`에, Querydsl 구현은 `core-infra`에 둔다.

예시:

- `app.show.query.ShowListQueryRepository` ← `infra.show.query.QuerydslShowListQueryRepository`
- `app.show.query.model.ShowListItemView`
- `app.performanceseat.query.SeatMapQueryRepository` ← `infra.performanceseat.query.QuerydslSeatMapQueryRepository`
- `app.performanceseat.query.model.SeatInfoView`

`*Finder`가 쓰는 조회 port는 예외다. `PerformanceBookingPolicyQueryRepository`처럼 도메인 정책 판정에
쓰이면 포트를 `core-domain`에 둔다.

## 저장소 구조

### RDB

주 영속 저장소는 RDB다.

주요 대상:

- 회원
- 공연/회차
- 좌석
- 주문
- hold 이력

JPA entity와 Spring Data repository는 대부분 `core-domain`에 존재한다.

### Redis

Redis는 짧은 수명 상태와 동시성 제어, 토큰 저장, 실시간 좌석 처리에 사용한다. 대기열 상태는 `ticket-queue`가 별도 Redis에서 관리하며, 현재 애플리케이션과 배포 설정은 단일 Redis 서버를 사용한다.

주요 대상:

- seat selection
- seat hold
- refresh token
- OAuth2 one-time auth code

Redis 구현체는 `core-infra`의 기능별 adapter에 위치한다.

## 실시간 처리

### 좌석 선택 / 홀드

- 좌석 선택과 홀드는 Redis TTL을 사용한다.
- 만료 시 listener 및 보정용 scheduler로 후속 정리를 수행한다.
- 좌석 상태 변경은 WebSocket 메시지로 전파한다.
- Redis 만료 listener는 worker 2개와 유한 queue를 사용해 TTL 폭주가 DB 동시성 폭주로 번지는 것을 막는다.
- 주문 생성 후 selection 정리와 HELD 발행은 DB 커밋과 connection 반환 뒤에 실행하고, 실패 입력은 creation outbox로 보존한다.
- background 실행 설정과 scheduler는 core-infra가 소유한다.

### 주문 후처리 보정

- 즉시 이벤트 처리만으로 끝내지 않고, 보정용 scheduler를 함께 둔다.
- listener 누락이나 운영 중 일시 장애가 있어도 정합성을 다시 맞추는 것이 목적이다.
- 주문 생성 시에는 PENDING 주문, hold history, hold creation outbox를 하나의 짧은 DB 트랜잭션에 저장한다.
- 주문 종료 시에는 상태 전이, hold history, hold release outbox를 하나의 짧은 DB 트랜잭션에 저장한다.
- 커밋 후 트리거는 outbox ID를 제한된 queue에 제출만 한다.
- outbox 조회와 완료/실패 기록은 각각 짧은 트랜잭션으로 실행한다.
- hold 해제 완료 단계는 WebSocket 발행 전에 기록해 발행 실패 재시도에서 Redis 해제를 반복하지 않는다.
- Redis selection/hold 변경과 WebSocket 발행 중에는 DB connection을 점유하지 않는다.
- 상세 흐름은 docs/core-booking-lifecycle.md를 기준으로 한다.

## 동시성 제어

분산락은 `@DistributedLock`과 AOP로 처리한다.

현재 구현 위치:

- 어노테이션: `com.ticket.core.support.lock.DistributedLock`
- 실행부: `com.ticket.core.infra.lock.DistributedLockAop`

적용 예:

- 동일 회원/공연 조합의 중복 주문 시작 방지
- 동일 좌석 동시 점유 방지

## 아키텍처 규칙

아래는 권고가 아니라 테스트가 실패시키는 규칙이다. 위반하면 다른 테스트의 통과 여부와 관계없이
완료가 아니다. 실행 명령은 [testing.md의 구조 테스트](testing.md#구조-테스트)를 따른다.

### `CoreLayerArchitectureTest` (ArchUnit, core-api)

계층 방향을 한곳에서 검사한다. `core-api`만 네 모듈을 모두 클래스패스에 두기 때문이다.

- `core-domain`은 app·infra·api를 참조하지 않는다.
- `core-app`은 infra·api를 참조하지 않는다.
- `core-api`는 `core-domain`을 참조하지 않는다. use case를 거친다.
- `core-infra`는 api를 참조하지 않는다.
- `core-domain`과 `core-app`은 Querydsl을 직접 쓰지 않는다. 생성된 Q 타입은 제외한다.
- `core-domain`은 `data`와 `stereotype` 외의 Spring을 참조하지 않는다.
  HTTP·보안·메시징뿐 아니라 `transaction`, `context`, `expression`, `scheduling`, `dao`도 막는다.
- `core-app`은 HTTP·보안·메시징·스케줄링을 참조하지 않는다. 트랜잭션은 소유하므로 허용한다.

### `CoreDomainArchitectureTest` (ArchUnit)

- `com.ticket.core.infra..`와 `..domain.*.infra..` 패키지는 존재할 수 없다.
- `infra` 바깥에서는 `org.redisson..`에 직접 의존하지 않는다.
- `infra` 바깥에서는 `org.springframework.data.redis..`에 직접 의존하지 않는다.
- `infra` 바깥에서는 `org.springframework.messaging..`에 직접 의존하지 않는다.
- `infra` 바깥과 `com.ticket.core.config..` 바깥에서는 HTTP interface client annotation에 직접 의존하지 않는다.
- `..domain..`의 메서드에는 `@Scheduled`를 붙일 수 없다.
- `..domain..`의 메서드에는 `@TransactionalEventListener`를 붙일 수 없다.

### `CoreDomainModuleStructureTest` (파일 배치 검증)

- `order`와 `queue` 비즈니스는 `core-domain`이 소유하고 `core-api`에 같은 패키지를 두지 않는다.
- `core-api`는 `core-app`을 의존하고 `core-domain`은 `testImplementation`으로만 둔다.
- JWT 보안 구현(`JwtTokenService`, `JwtProperties`, `OAuth2EndpointConstants`)은 `core-api`에만 둔다.
- `core-domain`의 `build.gradle`에 `springdoc-openapi`와 `jjwt`를 넣지 않고, 소스에 Swagger import를 두지 않는다.
- `CookieUtils` 같은 HTTP 유틸리티는 `core-api`에 둔다.
- `OrderExpirationScheduler`, `HoldReleaseOutboxScheduler`는 `core-infra`에 둔다.
- `core:core-enum` 모듈은 부활시키지 않는다. enum은 `core-domain`에 둔다.

`core-api`의 `CoreApiArchitectureTest`도 같은 성격의 경계를 검사한다. `core-api`의 `config.security`는
auth infra 구현체에 직접 의존하지 않는다.

## 경계 판단에서 자주 틀리는 지점

- **주기 실행이 필요한 규칙.** 규칙은 `core-app`의 use case에 두고 `@Scheduled` 트리거만
  `core-infra`에 둔다. 도메인에 애노테이션을 붙이는 순간 ArchUnit이 막는다.
- **Redis 상태를 읽는 조회 로직.** 좌석 상태는 DB와 Redis 점유 상태를 합쳐 계산한다. 합치는 규칙은
  `core-app`의 query use case가 소유하고 Redis 조회 자체는 `store` port를 통한다.
- **조회 포트를 어디 둘지.** 그것을 쓰는 쪽에 둔다. use case가 쓰면 `core-app`, 도메인 정책 판정에
  쓰이면 `core-domain`이다. 구현은 어느 쪽이든 `core-infra`다.
- **Querydsl 조건 생성기.** `ShowConditionFactory`처럼 Querydsl 타입을 다루면 DB 연동 코드이므로
  `core-infra`에 둔다. use case가 조건을 조립하지 않는다.
- **도메인 타입이 API에 새는 경우.** 요청 DTO는 문자열로 받고 변환은 `core-app` 경계에서 한다.
  enum은 `ShowSearchCriteria.of(...)`, 값 객체는 use case `Input.of(...)`가 맡는다.
  포트 시그니처도 엔티티가 아니라 식별 값을 받는다.
- **포트를 실행 모듈이 직접 부르는 경우.** `OAuth2AuthCodeStore` 같은 도메인 포트는 컨트롤러나
  security 핸들러가 직접 호출하지 않고 use case가 감싼다.
- **설정값을 두 곳에서 읽는 경우.** 토큰 만료처럼 한 값이 저장소 TTL과 응답에 함께 쓰이면
  발급한 쪽이 결과에 담아 알려준다. 각자 설정을 읽으면 어긋날 수 있다.
- **도메인이 이벤트를 발행하거나 트랜잭션을 여는 경우.** `ApplicationEventPublisher`와
  `@Transactional`은 흐름을 엮는 방법이다. 규칙은 `core-domain`에, 경계와 발행은 `core-app`에 둔다.
- **한 기능의 짝이 다른 층에 있는 경우.** outbox 생성과 해제처럼 같은 일을 하는 코드가 갈려 있으면
  둘 중 하나가 잘못 놓인 것이다. 이름이 달라도 하는 일로 판단한다.
- **다른 도메인이 필요한 경우.** 상대 도메인의 `repository`나 `store`를 직접 부르지 않고 공개 use case를 호출한다.
- **대기열.** 대기열 런타임은 형제 저장소 `../ticket-queue`가 소유한다. Core는 회차별 `entryType` 계산과
  admission token 검증만 담당하며 queue token 저장소나 만료 핸들러를 두지 않는다.
- **Core Redis의 용도.** seat selection, seat hold, refresh token, OAuth2 one-time auth code뿐이다.
  대기열 상태를 Core Redis에 넣지 않는다.

## 아키텍처 리뷰 질문

- 이 코드의 책임이 실행(api), 서비스 흐름(app), 업무 규칙(domain), 기술(infra) 중 어디에 속하는가
- 의존이 `core-api` → `core-app` → `core-domain` 방향을 지키는가
- `core-domain`과 `core-app`이 Redis, WebSocket, HTTP client, Querydsl, scheduler를 직접 알게 되지 않았는가
- 새 패키지가 기능 중심 축(`command`/`query`/`model`/`repository`/`store`)을 따르는가
- DB 상태와 Redis 상태를 합치는 규칙의 소유자가 한 곳인가
- 새 추상화가 실제 경계를 보호하는가, 사용하지 않는 계층을 늘리기만 하는가

## 다음 구조 정리 방향

- `ErrorType`이 `HttpStatus`를 갖고 있어 `support:error`가 `spring-web`에 의존한다. 도메인 예외와
  HTTP 표현을 분리할지 판단한다.
- JPA repository 인터페이스를 `core-domain` 포트와 `core-infra`의 Spring Data 인터페이스로 나눌지
  도메인별로 판단한다.
- `support:lock` 분리를 판단한다. 지금 `@DistributedLock` 애노테이션은 `core-domain`,
  AOP 실행부와 SpEL 파서는 `core-infra`에 갈라져 있다.
- `core-domain`의 `@Service` 세 곳(`PerformanceSeatService`, `SeatStatusPublisher` 등)을
  `@Component`로 맞출지 판단한다. 나머지 도메인 서비스는 `@Component`를 쓴다.
- `OrderFinder`는 프로덕션에서 쓰이지 않는다. 비관적 락 조회가 필요해질 때까지 둘지 판단한다.
