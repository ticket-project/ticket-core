# 아키텍처 기준

이 문서는 현재 모듈 구조와 패키지 경계를 정리한다. 개발 흐름은 [development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 구조

현재 프로젝트는 멀티 모듈 Gradle 구조이며, 실행/API, 도메인/application, 기술 구현을 점진적으로 분리하는 모듈러 모놀리스에 가깝다.

실제 포함 모듈은 다음 6개다.

- `core:core-api`
- `core:core-domain`
- `core:core-infra`
- `storage:redis-core`
- `support:logging`
- `support:security`

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

### `support:security`

JWT, Internal Auth, Admission Token 등 공통 보안 유틸을 제공하는 라이브러리 모듈이다. `maven-publish`로 발행되며, `core-api`가 이 모듈의 토큰 발급/검증 컴포넌트를 사용한다.

주요 책임:

- 사용자 Access Token 발급/검증 (`JwtAccessTokenIssuer`, `JwtTokenVerifier`)
- Internal Auth Token 검증과 Passport 복원 (`InternalAuthTokenService`, `InternalAuthPassportService`)
- Admission Token 발급/검증 (`AdmissionTokenService`)

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
  - Admission token 검증 설정
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

Redis는 짧은 수명 상태와 동시성 제어, 토큰 저장, 실시간 좌석 처리에 사용한다. 대기열 Redis는 `ticket-queue`에서 별도 Redis Cluster로 운영한다.

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

### 주문 종료 보정

- 즉시 이벤트 처리만으로 끝내지 않고, 보정용 scheduler를 함께 둔다.
- listener 누락이나 운영 중 일시 장애가 있어도 정합성을 다시 맞추는 것이 목적이다.

## 동시성 제어

분산락은 `@DistributedLock`과 AOP로 처리한다.

현재 구현 위치:

- 어노테이션: `com.ticket.core.support.lock.DistributedLock`
- 실행부: `com.ticket.core.infra.lock.DistributedLockAop`

적용 예:

- 동일 회원/공연 조합의 중복 주문 시작 방지
- 동일 좌석 동시 점유 방지

## 아키텍처 규칙

`core-domain`과 `core-api`에는 ArchUnit 기반 구조 테스트가 있다.

현재 강제하는 핵심 규칙:

- `infra` 바깥에서는 `org.redisson..`에 직접 의존하지 않는다.
- `infra` 바깥에서는 `org.springframework.messaging..`에 직접 의존하지 않는다.
- `infra` 바깥과 `core.config` 바깥에서는 HTTP interface client annotation에 직접 의존하지 않는다.
- `core-domain` 모듈은 `com.ticket.core.infra..`와 `domain.*.infra..` 구현 패키지를 포함하지 않는다.
- `domain.auth.command`와 `domain.auth.oauth2`는 auth infra 구현체에 직접 의존하지 않는다.
- `core-api`의 `config.security`는 auth infra 구현체에 직접 의존하지 않는다.

## 다음 구조 정리 방향

- JPA repository를 port/adapter로 나눌지 도메인별로 판단한다.
- query 전용 view/model과 application output을 분리 유지한다.
- ArchUnit 규칙을 추가해 의존 방향을 더 구체적으로 고정한다.
- 필요 시 이후에 Gradle 모듈 단위로 `application / domain / persistence-adapter`를 추가 분리한다.
