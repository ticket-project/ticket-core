# 아키텍처 기준

이 문서는 현재 모듈 구조와 패키지 경계를 정리한다. 개발 흐름은 [development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 구조

현재 프로젝트는 멀티 모듈 Gradle 구조이며, 실행/API, 도메인/application, 기술 구현을 점진적으로 분리하는 모듈러 모놀리스에 가깝다.

실제 포함 모듈은 다음 5개다.

- `core:core-api`
- `core:core-domain`
- `core:core-infra`
- `storage:redis-core`
- `support:logging`

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

- `core:core-domain`
- `core:core-infra`
- `support:logging`

### `core:core-domain`

비즈니스 기능의 중심 모듈이다. 순수 도메인 모델만 담는 모듈은 아니며, use case, 도메인 모델, JPA repository, query repository, port 인터페이스를 함께 둔다.

주요 책임:

- 기능별 use case
- JPA entity / repository
- query repository
- 도메인 규칙
- Redis/WebSocket/외부 HTTP port
- 분산락 어노테이션

### `core:core-infra`

기술 구현 모듈이다. `core-domain`의 port를 구현하고, Redis/WebSocket/외부 HTTP/AOP/기술 설정을 담당한다.

주요 책임:

- Redis 기반 store adapter
- Redis key expiration listener와 handler
- WebSocket seat event publisher
- Kakao HTTP interface client
- 분산락 AOP
- Querydsl, P6Spy 설정

의존:

- `core:core-domain`
- `storage:redis-core`
- Spring Web / WebSocket / Data JPA / Querydsl / P6Spy

### `storage:redis-core`

Redis 관련 공통 의존성을 제공한다.

주요 책임:

- `spring-boot-starter-data-redis`
- `redisson-spring-boot-starter`

실제 비즈니스 Redis 구현은 `core-infra`의 기능별 adapter에 둔다.

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
- `com.ticket.core.config`
  - 웹, JPA Auditing, HTTP service, WebSocket 설정
- `com.ticket.core.config.security`
  - JWT, OAuth2, 인증/인가 구성
- `com.ticket.core.config.admission`
  - Queue Server가 발급한 admission token의 설정·검증
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

- `command`: 상태 변경 흐름
- `query`: 조회 흐름
- `model`: 엔티티, 도메인 모델, 조회 모델
- `repository`: JPA/RDB 접근
- `store`: Redis 등 임시 상태 저장 port
- `support`: 도메인 보조 컴포넌트
- `event`: 도메인 이벤트

`core-domain`은 구현체 패키지로서의 `infra`를 두지 않는다. 기술 구현은 `core-infra`에 둔다.

### `core-infra`

`core-domain`의 패키지 구조를 따라 adapter를 배치하되, 물리 위치는 별도 Gradle 모듈이다.

예시:

- `com.ticket.core.infra.lock`
- `com.ticket.core.infra.config`
- `com.ticket.core.infra.redis`
- `com.ticket.core.infra.auth.oauth2`
- `com.ticket.core.infra.auth.token`
- `com.ticket.core.infra.hold`
- `com.ticket.core.infra.performanceseat`

## 코드 위치 결정표

새 코드는 아래 기준으로 위치를 정한다. 판단이 갈리면 "이 코드가 사라졌을 때 무엇이 먼저 깨지는가"를 본다.

| 책임 | 위치 |
| --- | --- |
| HTTP endpoint, 요청 검증, 인증 principal 추출, 응답 포맷 | `core-api` |
| Swagger 문서 인터페이스 | `core-api` 의 `controller.docs` |
| JWT, OAuth2, security filter chain, admission token 설정과 검증 | `core-api` 의 `config.security`, `config.admission` |
| 상태를 바꾸는 use case | `core-domain` 의 `<domain>.command` |
| 조회 use case와 조회 저장소 | `core-domain` 의 `<domain>.query` |
| 엔티티, 도메인 모델, 조회 model view | `core-domain` 의 `<domain>.model`, `<domain>.query.model` |
| JPA / RDB 접근 | `core-domain` 의 `<domain>.repository` |
| Redis 같은 임시 상태 저장 port | `core-domain` 의 `<domain>.store` |
| 도메인 이벤트, 도메인 보조 컴포넌트 | `core-domain` 의 `<domain>.event`, `<domain>.support` |
| Redis adapter, expiration listener, WebSocket publisher, 외부 HTTP client | `core-infra` |
| scheduler, `@TransactionalEventListener`, background executor 설정 | `core-infra` |
| 분산락 AOP 실행부 | `core-infra` 의 `lock` |
| Querydsl, P6Spy 같은 기술 설정 | `core-infra` 의 `config` |

## 기능별 구조 원칙

### Controller

Controller는 가능한 한 얇게 유지한다.

- 요청 검증
- 인증 principal 추출
- use case 호출
- 응답 포맷 반환

비즈니스 규칙이나 저장소 접근은 Controller에 두지 않는다.

### Command / Query

- `command`: 상태를 변경하는 use case
- `query`: 조회 전용 use case와 조회 저장소

query repository는 use case 내부 DTO에 직접 의존하지 않고, 기능별 query model view를 반환한다.

예시:

- `show.query.model.ShowListItemView`
- `show.query.model.ShowSearchItemView`
- `performanceseat.query.model.SeatInfoView`
- `performanceseat.query.model.SeatStateView`

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

### `CoreDomainArchitectureTest` (ArchUnit)

- `com.ticket.core.infra..`와 `..domain.*.infra..` 패키지는 존재할 수 없다.
- `infra` 바깥에서는 `org.redisson..`에 직접 의존하지 않는다.
- `infra` 바깥에서는 `org.springframework.data.redis..`에 직접 의존하지 않는다.
- `infra` 바깥에서는 `org.springframework.messaging..`에 직접 의존하지 않는다.
- `infra` 바깥과 `com.ticket.core.config..` 바깥에서는 HTTP interface client annotation에 직접 의존하지 않는다.
- `..domain.auth.command..`와 `..domain.auth.oauth2..`는 `com.ticket.core.infra.auth..`에 의존하지 않는다.
- `..domain..`의 메서드에는 `@Scheduled`를 붙일 수 없다.
- `..domain..`의 메서드에는 `@TransactionalEventListener`를 붙일 수 없다.

### `CoreDomainModuleStructureTest` (파일 배치 검증)

- `order`와 `queue` 비즈니스는 `core-domain`이 소유하고 `core-api`에 같은 패키지를 두지 않는다.
- JWT 보안 구현(`JwtTokenService`, `JwtProperties`, `OAuth2EndpointConstants`)은 `core-api`에만 둔다.
- `core-domain`의 `build.gradle`에 `springdoc-openapi`와 `jjwt`를 넣지 않고, 소스에 Swagger import를 두지 않는다.
- `CookieUtils` 같은 HTTP 유틸리티는 `core-api`에 둔다.
- `OrderExpirationScheduler`, `HoldReleaseOutboxScheduler`는 `core-infra`에 둔다.
- `core:core-enum` 모듈은 부활시키지 않는다. enum은 `core-domain`에 둔다.

`core-api`의 `CoreApiArchitectureTest`도 같은 성격의 경계를 검사한다. `core-api`의 `config.security`는
auth infra 구현체에 직접 의존하지 않는다.

## 경계 판단에서 자주 틀리는 지점

- **주기 실행이 필요한 도메인 규칙.** 규칙은 `core-domain`의 use case에 두고 `@Scheduled` 트리거만
  `core-infra`에 둔다. 도메인에 애노테이션을 붙이는 순간 ArchUnit이 막는다.
- **Redis 상태를 읽는 조회 로직.** 좌석 상태는 DB와 Redis 점유 상태를 합쳐 계산한다. 합치는 규칙은
  `core-domain`의 query use case가 소유하고 Redis 조회 자체는 `store` port를 통한다.
- **query model과 응답 DTO.** query repository는 use case 내부 DTO를 반환하지 않고 기능별 query model
  view를 반환한다. 응답 DTO는 `core-api`가 만든다.
- **다른 도메인이 필요한 경우.** 상대 도메인의 `repository`나 `store`를 직접 부르지 않고 공개 use case를 호출한다.
- **대기열.** 대기열 런타임은 형제 저장소 `../ticket-queue`가 소유한다. Core는 회차별 `entryType` 계산과
  admission token 검증만 담당하며 queue token 저장소나 만료 핸들러를 두지 않는다.
- **Core Redis의 용도.** seat selection, seat hold, refresh token, OAuth2 one-time auth code뿐이다.
  대기열 상태를 Core Redis에 넣지 않는다.

## 아키텍처 리뷰 질문

- 이 코드의 책임이 실행(api), 업무(domain), 기술(infra) 중 어디에 속하는가
- 의존이 `core-api`/`core-infra` → `core-domain` 방향을 지키는가
- `core-domain`이 Redis, WebSocket, HTTP client, scheduler를 직접 알게 되지 않았는가
- 새 패키지가 기능 중심 축(`command`/`query`/`model`/`repository`/`store`)을 따르는가
- DB 상태와 Redis 상태를 합치는 규칙의 소유자가 한 곳인가
- 새 추상화가 실제 경계를 보호하는가, 사용하지 않는 계층을 늘리기만 하는가

## 다음 구조 정리 방향

- JPA repository를 port/adapter로 나눌지 도메인별로 판단한다.
- query 전용 view/model과 application output을 분리 유지한다.
- ArchUnit 규칙을 추가해 의존 방향을 더 구체적으로 고정한다.
- 필요 시 이후에 Gradle 모듈 단위로 `application / domain / persistence-adapter`를 추가 분리한다.
