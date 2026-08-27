# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 위치를 선례로 삼지 말고, 미완료된 구조 이전으로 판단한다. 개발 흐름은
[development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 구조

프로젝트는 실행, HTTP 진입, 애플리케이션 흐름, 도메인 규칙, 기술 구현을 분리한 멀티 모듈
모듈러 모놀리스다. 아래는 구현이 따라야 할 확정 구조다.

확정 모듈은 다음 8개다.

- `bootstrap`
- `core:core-domain`
- `core:core-app`
- `core:core-infra`
- `core:core-api`
- `storage:redis-core`
- `support:error`
- `support:logging`

당장은 `core-worker`, `bootstrap-api`, `bootstrap-worker`를 만들지 않는다. `bootstrap` 한 프로세스에서
API와 background trigger를 함께 실행하고 `worker.enabled`로 background 실행 여부를 제어한다. API와
worker를 독립적으로 배포·확장해야 하는 시점에만 실행 모듈을 분리한다.

의존 방향은 아래와 같고 `CoreLayerArchitectureTest`가 강제한다.

```text
bootstrap ──→ core-api ──→ core-app ──→ core-domain
    │                          ↑            ↑
    └──────→ core-infra ───────┴────────────┘
                    │
                    └──→ storage:redis-core

bootstrap과 core 모듈 ──→ support:error / support:logging (필요한 모듈만)
```

`bootstrap`이 composition root다. API와 어댑터, background worker를 한 프로세스로 조립한다.
`core-api`는 더 이상 `core-infra`를 프로덕션 의존으로 두지 않는다.

`core-infra`가 `core-app`과 `core-domain`을 의존하는 것은 안쪽 계층이 선언한 포트와 Repository를
구현하기 위해서다. 런타임에는 app이 인터페이스를 호출하고 infra 구현체가 실행되지만, 컴파일
의존성은 구현체에서 계약 쪽을 향한다.

### 프로덕션 의존성 허용표

| 모듈 | 직접 의존할 수 있는 내부 모듈 | 직접 의존하면 안 되는 내부 모듈 |
| --- | --- | --- |
| `core-domain` | `support:error` | `core-app`, `core-infra`, `core-api`, `bootstrap` |
| `core-app` | `core-domain`, `support:error` | `core-infra`, `core-api`, `bootstrap` |
| `core-infra` | `core-app`, `core-domain`, `storage:redis-core`, support 모듈 | `core-api`, `bootstrap` |
| `core-api` | `core-app`, support 모듈 | `core-domain`, `core-infra`, `bootstrap` |
| `bootstrap` | `core-api`, `core-app`, `core-infra`, support 모듈 | 업무 코드에서 `core-domain` 직접 사용 |
| `storage:*`, `support:*` | 같은 계층의 명시적 기반 모듈만 | 모든 `core:*`, `bootstrap` |

테스트 fixture나 아키텍처 테스트의 `testImplementation`은 프로덕션 의존성과 구분한다. 테스트를 위한
의존성을 production 코드에서 사용하는 근거로 삼지 않는다.

## 모듈 책임

### `bootstrap`

실행 모듈이자 composition root다. Spring Boot main과 실행 환경 설정만 둔다.

주요 책임:

- `TicketApplication` (유일한 Spring Boot main)
- 모듈 조립과 bootJar 생성
- 프로파일별 설정과 Flyway 마이그레이션 리소스
- background worker 트리거(`@Scheduled`)와 `worker.enabled` 스위치

의존: `core:core-api`, `core:core-app`, `core:core-infra`, `support:error`, `support:logging`,
actuator, Flyway, DB 드라이버, micrometer, JWT 런타임 구현

트리거는 주기만 정하고 조회나 상태 판단을 하지 않는다. 업무 배치는 use case를,
순수 relay는 core-infra의 relay를 한 번 호출한다.

### `core:core-api`

HTTP adapter 모듈이다. 실행 진입점은 여기에 없다.

주요 책임:

- REST Controller
- 요청/응답 DTO와 HTTP 커서 문자열 인코딩·디코딩
- Spring Security filter chain, OAuth2 HTTP 처리
- WebSocket 진입 설정
- Admission token 검증 같은 예매 API 진입 제어
- 공통 응답 포맷과 HTTP 오류 변환

의존:

- `core:core-app`
- `support:error`
- `support:logging`

`core-domain`과 `core-infra`는 프로덕션 의존에서 뺐다. 실행 모듈은 use case를 거쳐 도메인에 닿는다.
계약 테스트와 계층 테스트가 필요로 하는 만큼만 `testImplementation`으로 남긴다.

### `core:core-app`

애플리케이션 계층이다. 도메인 규칙과 어댑터를 엮어 실제 서비스 흐름을 만든다.

주요 책임:

- 기능별 use case (`*UseCase`)
- 트랜잭션 경계와 오케스트레이션 (`*TxService`, `*Coordinator`, `*Processor`)
- 읽기 전용 조회 포트(`*ReadRepository`)와 조회 결과 view/param
- 커서 페이징 결과 타입 (`support.cursor.CursorPage`) — HTTP 커서 문자열은 다루지 않는다
- 분산락 포트 (`lock`의 `LockManager`, `LockKey`, `LockOptions`)
- 후속 처리 이벤트 포트 (`event`의 `IntegrationEventPublisher` 등)
- 인증 주체 값과 인증 흐름에 필요한 포트
  (`AuthenticatedMember`, `AccessTokenReader`, 토큰 발급, refresh token 저장, 비밀번호 검증 등)
- 외부 OAuth provider, 외부 publisher/client처럼 use case가 요구하는 비-Repository 출력 포트

의존:

- `core:core-domain`
- `support:error`
- Spring context/tx/core/beans와 slf4j만 쓴다. Spring Data와 Jackson은 두지 않는다.

### `core:core-domain`

핵심 비즈니스 규칙 모듈이다. 기술이나 전달 방식과 무관하게 성립하는 예매 도메인만 둔다.

주요 책임:

- JPA entity와 값 객체 (`Hold`, `Order`, `Show` 등)
- 도메인 상태 enum, 정책, 검증기와 도메인 서비스 (`BookingPolicyValidator` 등)
- 도메인 이벤트, 업무 불변식과 도메인 예외
- Aggregate Repository 인터페이스와 저장 기술에 중립적인 업무 상태 저장 계약
- 회원 역할·권한처럼 업무 규칙에 해당하는 인증/인가 개념

JPA entity를 도메인 모델로 쓰기로 했으므로 JPA mapping annotation은 허용한다. 현재 남은 Spring
stereotype과 `BaseEntity`의 Spring Data auditing annotation은 구조 이전 과정의 제한된 예외다.
새 도메인 코드는 이 예외를 넓히지 않고, 가능한 한 순수 Java 객체로 작성한다.

Repository 계약에는 도메인 타입과 Java 기본 타입만 노출한다. `JpaRepository`, `Pageable`,
`Slice`, `@Query`, `@Lock`은 두지 않는다. 이 기술들은 core-infra의 어댑터가 결정한다.

Redis/WebSocket/외부 HTTP/JWT/비밀번호 암호화처럼 기술이나 애플리케이션 흐름 때문에 필요한 포트는
`core-app`이 소유한다. outbox 엔티티와 상태, 분산락, 조회 최적화, use case도 이 모듈에 두지 않는다.

### `core:core-infra`

기술 구현 모듈이다. `core-app`과 `core-domain`이 선언한 계약을 기술로 구현하고,
Redis/WebSocket/외부 HTTP와 영속성 설정을 담당한다.

주요 책임:

- Aggregate Repository 어댑터와 Spring Data JPA 인터페이스
- Querydsl 읽기 전용 조회 구현과 조건·정렬·커서 헬퍼
- JWT 발급·검증, 비밀번호 암호화, OAuth provider client 같은 인증 포트 구현
- Redis 기반 store adapter와 분산락 구현 (`lock`의 `RedissonLockManager`)
- Redis key expiration listener와 handler
- WebSocket seat event publisher
- Kakao HTTP interface client
- 암호화와 대기열 입장 토큰 어댑터
- outbox 엔티티·상태·relay와 커밋 후 리스너 (`order.outbox`)
- 시드 러너 (`seed`)
- JPA auditing, Querydsl, P6Spy 설정

기술 라이브러리 예외를 그대로 밖으로 흘리지 않는다. 어댑터가 복구 가능한 경우 처리하고,
처리할 수 없는 경우에는 app/domain이 이해할 수 있는 실패 또는 API가 일관되게 처리할 기술 실패로
번역한다. 어떤 라이브러리를 썼는지가 안쪽 계층의 계약에 드러나면 경계가 잘못된 것이다.

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

프레임워크에 독립적인 **공통 오류 계약과 예외 전달 기반**만 제공한다. 이 모듈은 Spring Web,
`HttpStatus`, Jackson에 의존하지 않으며 업무별 오류 코드·메시지 카탈로그를 소유하지 않는다.

오류는 원인을 판단할 수 있는 모듈이 정의한다. 도메인 불변식 위반은 `core-domain`, use case 흐름의
실패는 `core-app`, HTTP 요청·인증·응답 변환 실패는 `core-api`, 기술 실패의 감지와 번역은
`core-infra`가 담당한다. 실제 `HttpStatus`, `ResponseEntity`, JSON 공개 응답 형식과 직렬화는
최외곽인 `core-api`만 소유한다.

현재 구체 계약과 구현 배경은 [ADR 0002](adr/0002-module-owned-error-contracts.md)를 따른다.
오류 코드 체계, 공개 메시지, 개발자용 진단 정보, 상태 분류와 예외 타입은 별도 오류 재설계에서
다시 확정할 예정이다. 새 ADR이 기존 결정을 명시적으로 대체하기 전까지 이 문서는 오류 타입의
구체 모양을 새로 결정하지 않는다. 다만 `CommonErrorCode`를 업무 오류 저장소처럼 확장하거나
`support:error`에 Spring Web 의존성을 추가해서는 안 된다.

### `support:logging`

로깅 관련 공통 설정 리소스를 제공한다.

## 대표 실행 흐름

컴파일 의존성은 안쪽의 계약을 향하고, 런타임 호출은 바깥에서 안쪽으로 들어갔다가 어댑터로
나온다. `core-infra`가 app/domain을 의존하는 이유는 안쪽 계층이 선언한 인터페이스를 구현하기
위해서이지, 안쪽 계층이 infra 구현을 직접 호출하기 위해서가 아니다.

### 상태 변경 요청

```text
bootstrap
  → core-api Controller
  → core-app Command UseCase / transaction boundary
      ├→ core-domain aggregate·policy / Aggregate Repository 계약
      └→ core-app 외부 능력 포트
             ↓
         core-infra adapter
             → Spring Data JPA / Redis / 외부 시스템
```

API는 요청을 해석하고 app 입력으로 변환한다. app은 트랜잭션과 작업 순서를 정하고, domain은
상태 변경 가능 여부와 불변식을 판단한다. infra는 그 결과를 실제 저장 기술에 반영한다.

### 조회 요청

```text
bootstrap
  → core-api Controller
  → core-app Query UseCase
  → core-app ReadRepository 계약
  → core-infra Querydsl 구현
  → core-app immutable view
  → core-api 응답 DTO
```

단순 조회도 API가 domain/infra를 직접 보지 않는다. API가 조회 구현과 결합되면 같은 조회를
worker나 다른 adapter에서 재사용하기 어렵고, HTTP 계층이 정렬·커서·조인 전략까지 소유하게 된다.
대신 app의 query use case를 얇게 유지하고, 복잡한 projection·집계·커서 최적화는 infra가 구현한다.

### background 작업

```text
업무 배치: bootstrap @Scheduled → core-app UseCase → core-domain → core-infra adapter
순수 relay: bootstrap @Scheduled → core-infra relay
```

`bootstrap`의 스케줄러는 실행 시각과 on/off만 책임진다. 주문 만료 판단이나 상태 전이 같은 업무는
app/domain에 있고, outbox 레코드 전송처럼 업무 판단 없이 기술 상태만 처리하는 relay는 infra에 있다.
현재는 API와 worker가 하나의 실행 파일을 공유하지만 `worker.enabled=false`로 트리거를 끌 수 있다.
독립 배포·독립 스케일링이 실제로 필요해질 때만 `bootstrap-api`와 `bootstrap-worker`로 분리한다.

### 포트 소유 기준

포트는 구현체가 아니라 **그 기능을 필요로 하고 의미를 정의하는 안쪽 계층**이 소유한다.
모든 포트를 domain에 모으지 않는다.

| 계약의 성격 | 소유 모듈 | 이유 |
| --- | --- | --- |
| aggregate 저장·복원과 업무 명령에 필요한 조회 | `core-domain` | domain이 필요한 저장 의미와 반환할 도메인 타입을 정의한다 |
| 화면 조회·검색·집계 결과 | `core-app` | 특정 use case의 읽기 요구이며 aggregate 복원 계약이 아니다 |
| 분산락, 토큰, 비밀번호, 외부 OAuth, publisher/client | `core-app` | 업무 규칙 자체가 아니라 use case를 실행하기 위한 외부 능력이다 |
| HTTP 입력·출력 계약 | `core-api` | 전달 방식이 HTTP일 때만 존재한다 |
| JPA, Querydsl, Redis, Redisson, JWT 라이브러리 구현 | `core-infra` | 교체 가능한 기술 선택이며 안쪽 계약을 구현한다 |

이 기준을 따르면 domain은 핵심 업무 언어에 집중하고, app은 실행 환경에 필요한 능력을 구체 기술명
없이 요청할 수 있다. 예를 들어 `LockManager`는 app에 있지만 `RedissonLockManager`와 Redis key
형식은 infra에 있다. domain은 “분산락을 얻었는가”가 아니라 락 안에서 실행된 상태 변경이 업무상
유효한지만 판단한다.

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
  - 웹, WebSocket, API 시스템 값 설정
- `com.ticket.core.config.security`
  - security filter chain, 토큰 추출, OAuth2 HTTP handler, 인증 주체 argument resolver
  - JWT 암호 연산과 provider 연동 구현은 `core-infra`에 둔다
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
- `repository`: Aggregate Repository 인터페이스(순수 계약)
- `store`: 저장 기술에 중립적인 업무 상태 저장 계약
- `query`: 정책 판정에 쓰는 도메인 read model
- `command`: 도메인 서비스
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
  - 인증 흐름 use case, 인증 주체 값과 토큰·비밀번호·외부 provider 포트
- `com.ticket.core.app.support.cursor`
  - 타입이 있는 커서 위치와 페이징 결과
  - HTTP 문자열 codec, Jackson, Spring Data `Slice`는 포함하지 않는다

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
예: `QuerydslShowListReadRepository implements ShowListReadRepository`

Aggregate Repository 어댑터는 `*RepositoryAdapter`, 그 안에서 쓰는 Spring Data 인터페이스는
`SpringData*JpaRepository`로 이름 짓는다.
예: `OrderRepositoryAdapter implements OrderRepository` → `SpringDataOrderJpaRepository`

```text
core-domain
  OrderRepository                 (순수 Java 계약)
          ↑ implements
core-infra
  OrderRepositoryAdapter          (계약과 Spring Data를 연결)
          ↓ delegates
  SpringDataOrderJpaRepository    (JpaRepository, @Query, @Lock)
```

중간 Adapter를 두는 이유는 이름을 하나 더 만들기 위해서가 아니다. domain 계약이 Spring Data의
상속 메서드와 annotation에 오염되지 않게 하고, `save`·lock 조회·예외 번역처럼 기술 동작을 한곳에
가두며, 나중에 영속 기술을 바꾸더라도 app/domain 호출부를 유지하기 위해서다. JPA entity가 domain에
있어도 Repository 프레임워크까지 domain에 둘 이유는 없다. entity는 업무 상태를 표현하지만
`JpaRepository`, JPQL, DB lock mode는 그 상태를 저장하는 방법이기 때문이다.

## 코드 위치 결정표

새 코드는 아래 기준으로 위치를 정한다. 판단이 갈리면 "이 코드가 사라졌을 때 무엇이 먼저 깨지는가"를 본다.

| 책임 | 위치 |
| --- | --- |
| HTTP endpoint, 요청 검증, 인증 주체 추출, 응답 포맷 | `core-api` |
| Swagger 문서 인터페이스 | `core-api` 의 `controller.docs` |
| OAuth2 설정, security filter chain, 인증 필터 | `core-api` 의 `config.security` |
| JWT 발급·검증 구현 | `core-infra` 의 `auth.token` |
| 인증 흐름의 포트와 인증 주체·토큰 값 | `core-app` 의 `auth` |
| 회원 역할·권한 같은 업무 개념 | `core-domain` 의 `auth` 또는 `member` |
| HTTP 헤더 이름 같은 API 계약 상수 | `core-api` 의 `api` |
| 상태를 바꾸는 use case | `core-app` 의 `<기능>.command` |
| 조회 use case | `core-app` 의 `<기능>.query` |
| 트랜잭션 경계 조립, 여러 도메인 서비스 오케스트레이션 | `core-app` |
| 조회 결과 view와 검색 param | `core-app` 의 `<기능>.query.model` |
| 읽기 전용 조회 port (`*ReadRepository`) | `core-app` 의 `<기능>.query` |
| 엔티티, 값 객체 | `core-domain` 의 `<기능>.model` |
| 도메인 정책과 검증기 | `core-domain` |
| Aggregate Repository 인터페이스(순수 계약) | `core-domain` 의 `<기능>.repository` |
| Repository 어댑터와 Spring Data 인터페이스 | `core-infra` |
| 저장 기술에 중립적인 업무 상태 저장 계약 | 필요 주체에 따라 `core-domain`의 `<기능>.store` 또는 `core-app`의 포트 |
| 분산락 port (`LockManager`, `LockKey`, `LockOptions`) | `core-app` 의 `lock` |
| 분산락 구현과 Redis key 형식 | `core-infra` 의 `lock` |
| 후속 처리 이벤트 port (`IntegrationEventPublisher`) | `core-app` 의 `event` |
| outbox 엔티티·상태·relay | `core-infra` 의 `order.outbox` |
| HTTP 커서 문자열 인코딩·디코딩 | `core-api` 의 `api.support.cursor` |
| 커서 위치 타입과 조회 결과 | `core-app` 의 `<기능>.query.model`, `support.cursor` |
| Querydsl 조회 구현과 조건·정렬·커서 헬퍼 | `core-infra` 의 `<기능>.query` |
| Redis adapter, expiration listener, WebSocket publisher, 외부 HTTP client | `core-infra` |
| 암호화, 대기열 입장 토큰 검증 같은 포트 구현 | `core-infra` |
| 시드 러너 | `core-infra` 의 `seed` |
| `@TransactionalEventListener`, background executor 설정 | `core-infra` |
| `@Scheduled` 트리거와 실행 주기 설정 | `bootstrap` 의 `worker` |
| Spring Boot main과 프로파일 설정 | `bootstrap` |
| Querydsl, P6Spy 같은 기술 설정 | `core-infra` 의 `config` |
| 프레임워크 중립 오류 계약과 예외 전달 기반 | `support:error` |
| 도메인 규칙이 판단하는 오류 | `core-domain` 의 `error` |
| 유스케이스가 판단하는 오류 | `core-app` 의 `error` |
| 오류 응답 형식과 Spring 예외 변환 | `core-api` 의 `api.error` |

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

Repository 한 번 호출한 뒤 not-found 예외만 던지는 `*Finder` 계층은 두지 않는다. command/query
use case가 해당 Repository를 직접 호출한다. 여러 use case에서 반복된다는 이유만으로 Finder를
만들지 않으며, 반복 코드가 실제 업무 규칙이면 domain 정책/서비스로, 애플리케이션 절차면 이름이
그 목적을 드러내는 app 서비스로 추출한다.

Repository는 두 종류로 나뉜다.

**Aggregate Repository** (`core-domain`): aggregate의 저장과 복원, 업무 명령에 필요한 조회를 맡는다.
도메인 타입만 반환하고, JPA 구현은 `core-infra`의 어댑터가 맡는다.

- `domain.order.repository.OrderRepository` ← `infra.order.OrderRepositoryAdapter`

**Read Repository** (`core-app`): 화면·검색·상세·집계·커서 페이징 같은 읽기 전용 조회를 맡는다.
app이 소유한 immutable read model이나 원시 타입을 반환하며 domain entity 반환을 강제하지 않는다.

- `app.show.query.ShowListReadRepository` ← `infra.show.query.QuerydslShowListReadRepository`
- `app.show.query.model.ShowListItemView`
- `app.performanceseat.query.SeatMapReadRepository` ← `infra.performanceseat.query.QuerydslSeatMapReadRepository`

읽기 경로는 domain entity를 거치지 않아도 된다. 상태를 바꾸지 않기 때문이다. 반대로 상태를 바꾸는
command는 반드시 domain aggregate와 Aggregate Repository를 거쳐 불변식을 다시 검증한다.

Read Repository 계약에는 app read model, `List`, `Optional`, `long`, `boolean`, 순수 Java/domain value,
타입 커서 위치, limit만 노출한다. API 응답 DTO, Spring `Slice`/`Page`/`Pageable`, Querydsl `Tuple`,
`EntityManager`, `Object[]`, HTTP 커서 문자열은 두지 않는다.

정책 판정에 쓰이는 조회는 Read Repository가 아니라 Aggregate Repository가 도메인 값으로 돌려준다.
예: `PerformanceRepository.findBookingPolicyById`, `PerformanceSeatRepository.findSelectableSeat`

## 저장소 구조

### RDB

주 영속 저장소는 RDB다.

주요 대상:

- 회원
- 공연/회차
- 좌석
- 주문
- hold 이력

업무 상태를 표현하는 JPA entity는 `core-domain`에 둔다. 반면 Spring Data 인터페이스, JPQL,
Querydsl, `EntityManager`, DB lock annotation과 Repository adapter는 `core-infra`에 둔다.
outbox처럼 업무 aggregate가 아니라 기술적 전달을 위한 테이블의 entity와 repository도
`core-infra`가 소유한다.

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
- `@Scheduled` 트리거와 실행 주기·활성화 설정은 `bootstrap`이 소유한다.
- 업무 판단과 상태 전이 오케스트레이션은 `core-app`, Redis listener·WebSocket publisher와
  기술 executor/relay 구현은 `core-infra`가 소유한다.

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

분산락은 명시적인 포트 호출로 처리한다. 어노테이션과 SpEL로 감추지 않는다.

현재 구현 위치:

- 포트: `com.ticket.core.app.lock.LockManager` (core-app)
- 잠글 대상: `LockKey`, `LockScope` — 업무 의미만 담고 key 문자열은 담지 않는다
- 획득 방식: `LockOptions` — 대기 시간, 임대 시간, 실패 로그 수준
- 구현: `com.ticket.core.infra.lock.RedissonLockManager` (core-infra)
- key 형식: `com.ticket.core.infra.lock.RedissonLockKeyFormatter` (core-infra)

적용 예:

- 동일 회원/공연 조합의 중복 주문 시작 방지 (`LockScope.ORDER_START`)
- 동일 좌석 동시 점유 방지 (`LockScope.SEAT`)
- outbox 단건 중복 실행 방지와 보정 배치 단일 실행

락을 먼저 잡고 그 안에서 트랜잭션을 시작한다. 커밋이 끝난 뒤에 락이 풀린다.
좌석 락은 Redis hold를 만드는 구간에만 건다. DB 트랜잭션 동안 좌석 락을 쥐고 있으면
connection 경합이 좌석 경합으로 번진다.

Redis key 형식은 `RedissonLockKeyFormatterTest`가, 실제 상호 배제는
`CoreRedisIntegrationTest`가 고정한다.

## 아키텍처 규칙

아래는 권고가 아니라 테스트가 실패시키는 규칙이다. 위반하면 다른 테스트의 통과 여부와 관계없이
완료가 아니다. 실행 명령은 [testing.md의 구조 테스트](testing.md#구조-테스트)를 따른다.

### `CoreLayerArchitectureTest` (ArchUnit, core-api)

계층 방향을 한곳에서 검사한다. `core-api`가 네 모듈을 모두 테스트 클래스패스에 두기 때문이다.

- `core-domain`은 app·infra·api를 참조하지 않는다.
- `core-app`은 infra·api를 참조하지 않는다.
- `core-api`는 `core-domain`을 참조하지 않는다. use case를 거친다.
- `core-infra`는 api를 참조하지 않는다.
- `core-domain`과 `core-app`은 Querydsl을 직접 쓰지 않는다. 생성된 Q 타입은 제외한다.
- `core-domain`은 `stereotype` 외의 Spring을 참조하지 않는다. `BaseEntity`의 감사 애노테이션만 예외다.
- `core-app`은 HTTP·보안·메시징·스케줄링을 참조하지 않는다. 트랜잭션은 소유하므로 허용한다.
- `core-app`은 Spring Data를 참조하지 않는다.
- `core-domain`과 `core-app`은 Hibernate, Redisson, `EntityManager`를 참조하지 않는다.
- 토큰 라이브러리(`io.jsonwebtoken`)는 `core-infra` 밖으로 새지 않는다.
- `@EnableScheduling`과 `@Scheduled`는 `bootstrap`에만 둔다.

### `BootstrapArchitectureTest` (ArchUnit, bootstrap)

- `bootstrap`은 `core-domain`에 직접 닿지 않는다. use case와 어댑터를 거친다.
- background 트리거는 `com.ticket.bootstrap.worker`에 모은다.

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
- JWT 발급·검증 구현(`JwtTokenService`, `JwtProperties`)은 `core-infra`에 둔다.
  `core-domain`과 `core-api`의 `build.gradle`에는 `jjwt`를 넣지 않는다.
  OAuth2 엔드포인트 상수는 filter chain 설정의 일부라 `core-api`에 남는다.
- `core-domain`의 `build.gradle`에 `springdoc-openapi`를 넣지 않고, 소스에 Swagger import를 두지 않는다.
- `CookieUtils` 같은 HTTP 유틸리티는 `core-api`에 둔다.
- 실행 모듈은 `bootstrap` 하나다. `bootJar`도 여기에서만 만든다.
- `@Scheduled` 트리거는 `bootstrap`, 업무 배치는 `core-app` 유스케이스,
  순수 relay는 `core-infra`에 둔다.
- 시드 러너는 `core-infra`에 둔다.
- `core:core-enum` 모듈은 부활시키지 않는다. enum은 `core-domain`에 둔다.

`core-api`의 `CoreApiArchitectureTest`도 같은 성격의 경계를 검사한다. `core-api`의 `config.security`는
auth infra 구현체에 직접 의존하지 않는다.

## 경계 판단에서 자주 틀리는 지점

판단 절차와 자주 틀리는 지점 11가지는 **`/place-code` 스킬**이 원본이다
(`.claude/skills/place-code/SKILL.md`). 코드를 새로 두거나 옮길 때, 구조 테스트가 실패했을 때
그 스킬이 로드된다.

이 문서는 각 모듈이 무엇을 담는지와 위의 [코드 위치 결정표](#코드-위치-결정표)를 원본으로 유지한다.


## 아키텍처 리뷰 질문

- 이 코드의 책임이 실행(api), 서비스 흐름(app), 업무 규칙(domain), 기술(infra) 중 어디에 속하는가
- 의존이 `core-api` → `core-app` → `core-domain` 방향을 지키는가
- `core-domain`과 `core-app`이 Redis, WebSocket, HTTP client, Querydsl, scheduler를 직접 알게 되지 않았는가
- 새 패키지가 기능 중심 축(`command`/`query`/`model`/`repository`/`store`)을 따르는가
- DB 상태와 Redis 상태를 합치는 규칙의 소유자가 한 곳인가
- 새 추상화가 실제 경계를 보호하는가, 사용하지 않는 계층을 늘리기만 하는가

## 다음 구조 정리 방향

- `BaseEntity`의 생성·수정 감사만 Spring Data auditing에 남아 있다. JPA 생명주기 콜백으로 바꾸려면
  감사 주체를 도메인에 포트로 노출해야 하므로, 감사 컬럼 요구가 바뀔 때 함께 판단한다.
- Querydsl Q 타입은 엔티티가 있는 `core-domain`에서 생성된다. 애노테이션 프로세서 특성상
  다른 모듈에서 생성할 수 없어 현재 배치를 유지한다.
- `core-domain`의 `@Service` 세 곳(`PerformanceSeatService`, `SeatStatusPublisher` 등)을
  `@Component`로 맞출지 판단한다. 나머지 도메인 서비스는 `@Component`를 쓴다.
- API와 worker를 다른 프로세스로 나눠야 하면 `bootstrap-api`/`bootstrap-worker`로 쪼갠다.
  지금은 `worker.enabled`로 한 프로세스 안에서 켜고 끈다.
