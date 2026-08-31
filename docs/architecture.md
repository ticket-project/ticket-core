# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 위치를 선례로 삼지 말고, 미완료된 구조 이전으로 판단한다. 개발 흐름은
[development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 구조

프로젝트는 실행, HTTP 진입, 애플리케이션 흐름, 도메인 규칙, 기술 구현을 분리한 멀티 모듈
모듈러 모놀리스다. 확정 모듈 목록은 `settings.gradle`이 원본이다.

`support:common`은 아직 만들지 않았다. 생성 조건과 금지 규칙은
[support:common](#supportcommon-아직-만들지-않았다)에 있다.

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

### 프로덕션 의존성

모듈별로 무엇을 직접 의존할 수 있는지는 `CoreLayerArchitectureTest`와 `CoreApiArchitectureTest`가
강제한다. 실제 의존은 각 모듈 `build.gradle`이 원본이다. 표로 옮겨 적지 않는다.

테스트 fixture나 아키텍처 테스트의 `testImplementation`은 프로덕션 의존성과 구분한다. 테스트를 위한
의존성을 production 코드에서 사용하는 근거로 삼지 않는다.


## 모듈 책임

각 모듈이 **무엇을 담는지는 코드가 원본이다.** 여기에는 왜 그렇게 나뉘었는지와, 코드만 봐서는
알 수 없는 경계 판단만 둔다. 실제 의존은 각 모듈 `build.gradle`이 원본이다.

### `bootstrap`

실행 모듈이자 composition root다. Spring Boot main(`TicketApplication`)과 실행 환경 설정,
bootJar, 프로파일별 설정과 Flyway 리소스, background 트리거를 둔다.

트리거는 주기만 정하고 조회나 상태 판단을 하지 않는다. 업무 배치는 use case를,
순수 relay는 core-infra의 relay를 한 번 호출한다. 실행 방식을 다른 모듈이 결정하지 않게 한다.

### `core:core-api`

HTTP adapter 모듈이다. 실행 진입점은 여기에 없다. Controller, 요청/응답 DTO, HTTP 커서 문자열,
security filter chain, WebSocket 진입, admission token 검증, 공통 응답과 HTTP 오류 변환을 맡는다.

`core-domain`과 `core-infra`는 프로덕션 의존에서 뺐다. 실행 모듈은 use case를 거쳐 도메인에 닿는다.
계약 테스트와 계층 테스트가 필요로 하는 만큼만 `testImplementation`으로 남긴다.

### `core:core-app`

애플리케이션 계층이다. 도메인 규칙과 어댑터를 엮어 서비스 흐름을 만든다. use case, 트랜잭션 경계,
조회 포트와 결과 view, 그리고 **use case가 필요로 하는 출력 포트**(분산락, 이벤트 발행, 인증 토큰,
외부 provider)를 소유한다.

포트를 여기 두는 기준은 "누가 그 계약의 의미를 정하는가"다. use case가 정하면 `core-app`,
도메인 규칙이 정하면 `core-domain`이다. 구현은 어느 쪽이든 `core-infra`다.

커서 페이징 결과(`support.cursor.CursorPage`)는 여기 있지만 HTTP 커서 문자열은 다루지 않는다.
Spring은 context/tx/core/beans와 slf4j까지만 쓰고 Spring Data와 Jackson은 두지 않는다.

### `core:core-domain`

기술이나 전달 방식과 무관하게 성립하는 예매 도메인만 둔다. 엔티티와 값 객체, 상태 enum, 정책과
검증기, 도메인 이벤트와 예외, Aggregate Repository 계약을 갖는다.

**JPA entity를 도메인 모델로 쓰기로 했으므로** JPA mapping annotation은 허용한다. 남은 Spring
stereotype과 `BaseEntity`의 auditing annotation은 구조 이전 과정의 제한된 예외다. 새 도메인 코드는
이 예외를 넓히지 않고 가능한 한 순수 Java 객체로 쓴다.

Repository 계약에는 도메인 타입과 Java 기본 타입만 노출한다. 저장 기술은 어댑터가 결정한다.
기술이나 애플리케이션 흐름 때문에 필요한 포트(Redis, WebSocket, 외부 HTTP, JWT, 암호화)는
`core-app`이 소유하고, outbox·분산락·조회 최적화·use case도 이 모듈에 두지 않는다.

### `core:core-infra`

`core-app`과 `core-domain`이 선언한 계약을 기술로 구현한다. Repository 어댑터, Querydsl 조회,
인증 포트 구현, Redis store와 분산락, expiration listener, WebSocket publisher, 외부 HTTP client,
outbox와 커밋 후 리스너, 시드 러너, 기술 설정이 여기 있다.

**기술 라이브러리 예외를 그대로 밖으로 흘리지 않는다.** 어댑터가 복구 가능한 경우 처리하고,
그렇지 않으면 app/domain이 이해할 수 있는 실패 또는 API가 일관되게 처리할 기술 실패로 번역한다.
어떤 라이브러리를 썼는지가 안쪽 계층의 계약에 드러나면 경계가 잘못된 것이다.

### `storage:redis-core`

Redis 공통 의존성만 제공한다. 실제 비즈니스 Redis 구현은 `core-infra`의 기능별 adapter에 둔다.

### `support:error`

프레임워크에 독립적인 **공통 오류 계약과 예외 전달 기반**만 제공한다. Spring Web, `HttpStatus`,
Jackson에 의존하지 않으며 업무별 오류 코드·메시지 카탈로그를 소유하지 않는다.

**오류는 원인을 판단할 수 있는 모듈이 정의한다.** 도메인 불변식 위반은 `core-domain`, use case 흐름의
실패는 `core-app`, HTTP 요청·인증·응답 변환 실패는 `core-api`, 기술 실패의 감지와 번역은
`core-infra`가 담당한다. `HttpStatus`, `ResponseEntity`, 공개 JSON 응답 형식과 직렬화는 `core-api`만
소유한다.

구체 계약과 배경은 [ADR 0002](adr/0002-module-owned-error-contracts.md)를 따른다. 오류 코드 체계와
상태 분류는 별도 재설계에서 다시 확정한다. 새 ADR이 기존 결정을 대체하기 전까지 이 문서는 오류
타입의 구체 모양을 새로 정하지 않는다. `CommonErrorCode`를 업무 오류 저장소처럼 확장하거나
`support:error`에 Spring Web 의존성을 추가하지 않는다.

### `support:common` (아직 만들지 않았다)

**공통 모듈 자체는 허용한다. 이름은 `core-common`이 아니라 `support:common`을 쓴다.** 다만 지금
조건을 만족하는 타입이 없어 **빈 모듈을 먼저 만들지 않는다.** 첫 적합한 공통 타입이 생길 때 만든다.

둘 수 있는 타입은 아래를 **모두** 만족한다.

- 둘 이상의 **독립** 모듈에서 실제로 쓰인다
- 모든 소비자에게 의미가 동일하고, 호출 모듈별로 다르게 발전할 가능성이 낮다
- 업무 용어(Show, Order, Hold, Seat)도 기술 용어(HTTP, DB, Redis, JWT)도 없다
- JDK만으로 성립하고 독립적인 단위 테스트가 가능하다
- 짧은 중복 코드를 줄이는 것이 목적이 아니다

production 의존성은 **원칙적으로 JDK 외 금지**다. 조건을 만족해도 Entity, Repository, UseCase,
DTO, `ApiResponse`, `HttpStatus`, 업무 오류 코드, `CookieUtils`, `CursorCodec`, `RedisKeyFormatter`,
`JwtUtils`, 그리고 `CommonUtils`처럼 무제한으로 커지는 클래스는 두지 않는다.

`support:error`와 `support:logging`은 각자 역할로 유지하고 여기 합치지 않는다.
**라이브러리 버전 전달용 모듈로 쓰지 않는다.** 버전은 `gradle/libs.versions.toml`이 소유한다.

#### 현재 후보 판정

공통처럼 보인다는 이유만으로 옮기지 않는다. 지금까지 검토한 후보는 전부 이동 불가다.

| 후보 | 현재 위치 | 근거 |
| --- | --- | --- |
| `CookieUtils` | `core-api` | Servlet·Spring에 의존하고 쿠키 이름·path·SameSite는 HTTP 계약이다 |
| `CursorPage` | `core-app` | 소비자가 계약 소유자와 그 구현자뿐이다. 독립 모듈 둘이 아니다 |
| `ShowCursorCodec` | `core-api` | Jackson과 Base64 wire 표현을 다루고 이름과 대상이 업무 용어다 |
| `ShowQueryHelper` | `core-infra` | Querydsl Q 타입에 직접 의존한다. 기술 용어와 업무 용어를 동시에 갖는다 |
| `UuidSupplier` | `core-infra` | 소비자가 `core-infra` 한 모듈뿐이다 |

후보가 조건을 만족하면 그때 모듈을 추가하고, 허용 타입과 테스트만 옮기며, 금지 의존성을 구조
테스트로 강제한다. 모든 모듈에 의존성을 붙이지 않고 **실제 소비 모듈만** 의존한다.

### `support:logging`

로깅 공통 설정 리소스만 제공한다.

## 대표 실행 흐름

컴파일 의존성은 안쪽의 계약을 향하고, 런타임 호출은 바깥에서 안쪽으로 들어갔다가 어댑터로
나온다. `core-infra`가 app/domain을 의존하는 이유는 안쪽 계층이 선언한 인터페이스를 구현하기
위해서이지, 안쪽 계층이 infra 구현을 직접 호출하기 위해서가 아니다.

### 세 가지 흐름

```text
상태 변경  core-api Controller → core-app Command UseCase(트랜잭션 경계)
             ├→ core-domain aggregate·policy / Aggregate Repository 계약
             └→ core-app 출력 포트 → core-infra adapter → JPA·Redis·외부

조회       core-api Controller → core-app Query UseCase → ReadRepository 계약
             → core-infra Querydsl 구현 → core-app view → core-api 응답 DTO

background 업무 배치: bootstrap @Scheduled → core-app UseCase → domain → infra
           순수 relay: bootstrap @Scheduled → core-infra relay
```

API는 요청을 해석해 app 입력으로 바꾼다. app은 트랜잭션과 순서를 정하고, domain은 상태 변경
가능 여부와 불변식을 판단하며, infra가 실제 저장 기술에 반영한다.

**단순 조회도 API가 domain/infra를 직접 보지 않는다.** API가 조회 구현과 결합되면 같은 조회를
worker나 다른 adapter에서 재사용하기 어렵고, HTTP 계층이 정렬·커서·조인 전략까지 소유하게 된다.
app의 query use case는 얇게 두고 projection·집계·커서 최적화는 infra가 구현한다.

**스케줄러는 실행 시각과 on/off만 책임진다.** 주문 만료 판단이나 상태 전이 같은 업무는 app/domain에
있고, outbox 전송처럼 업무 판단 없이 기술 상태만 처리하는 relay는 infra에 있다. 지금은 API와 worker가
한 실행 파일을 공유하되 `worker.enabled=false`로 트리거를 끌 수 있다. 독립 배포·스케일링이 실제로
필요해질 때만 실행 모듈을 나눈다.

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

## 패키지와 이름 규칙

패키지 목록은 코드가 원본이다. 여기에는 **새 코드를 만들 때 따라야 할 축과 이름 규칙**만 둔다.

기능별 패키지를 기본 축으로 잡는다(`auth`, `member`, `order`, `hold`, `show`, `performanceseat` 등).
기능 안의 하위 패키지는 아래 패턴을 쓴다.

| 하위 패키지 | 담는 것 | 두는 모듈 |
| --- | --- | --- |
| `model` | 엔티티와 값 객체 | `core-domain` |
| `repository` | Aggregate Repository 인터페이스(순수 계약) | `core-domain` |
| `store` | 저장 기술에 중립적인 업무 상태 저장 계약 | `core-domain` |
| `query` | 정책 판정에 쓰는 도메인 read model | `core-domain` |
| `command` | 상태를 바꾸는 use case와 트랜잭션 조립 | `core-app` |
| `query`, `query.model` | 조회 use case, 조회 포트, 결과 view와 param | `core-app` |

`core-domain`은 구현체 패키지로서의 `infra`를 두지 않는다. `core-infra`는 `core-domain`의 기능 축을
그대로 따라 어댑터를 배치하되 물리 위치는 별도 Gradle 모듈이다.

`core-app`의 `support.cursor`는 타입이 있는 커서 위치와 페이징 결과만 갖는다. HTTP 커서 문자열
codec은 `core-api`의 몫이다.

### 이름 규칙

- Querydsl 조회 구현은 `Querydsl` 접두사를 붙여 포트와 구분한다.
  `QuerydslShowListReadRepository implements ShowListReadRepository`
- Aggregate Repository 어댑터는 `*RepositoryAdapter`.
- 어댑터가 안에서 쓰는 Spring Data 인터페이스는 `SpringData*JpaRepository`.

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

책임별 위치 표는 **`/place-code` 스킬**이 원본이다(`.claude/skills/place-code/SKILL.md`).
판단 기준이 문서와 스킬로 갈려 있으면 한쪽만 자라고 어긋난다.


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

조회 실패는 `Optional`이나 `boolean`으로 돌려주고 Repository가 오류를 정하지 않는다. Repository가
아는 것은 "결과가 없다"는 사실뿐이고, 그것이 인증 실패인지 not-found인지 멱등 성공인지는 호출하는
유스케이스가 판단한다. 예외를 던지는 `getXxx`·`requireXxx` 편의 메서드를 두지 않는다
([validation.md](validation.md)).

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

권고가 아니라 **테스트가 실패시키는 규칙**이다. 위반하면 다른 테스트의 통과 여부와 관계없이
완료가 아니다.

**규칙 본문은 테스트 코드가 원본이고 여기 옮겨 적지 않는다.** 옮겨 적는 순간 테스트와 어긋나기
시작하고, 어긋난 쪽을 사람이 먼저 믿는다. 어떤 테스트가 무엇을 고정하는지는
[testing.md의 구조 테스트](testing.md#구조-테스트), 실행 명령은 `/verify`,
실패했을 때 볼 곳은 `/place-code`가 원본이다.

규칙을 바꿔야 한다고 판단되면 테스트를 고쳐 통과시키지 말고, 규칙이 틀렸다는 사실을 먼저 밝힌다.


## 경계 판단에서 자주 틀리는 지점

판단 절차, 책임별 위치, 자주 틀리는 지점은 **`/place-code` 스킬**이 원본이다
(`.claude/skills/place-code/SKILL.md`). 코드를 새로 두거나 옮길 때, 구조 테스트가 실패했을 때
그 스킬이 로드된다.

이 문서는 각 모듈이 **왜** 그렇게 나뉘었는지를 갖는다. 어디에 두는지는 스킬이 갖는다.


## 아키텍처 리뷰 질문

- 이 코드의 책임이 실행(api), 서비스 흐름(app), 업무 규칙(domain), 기술(infra) 중 어디에 속하는가
- 같은 검증이 두 계층에서 같은 목적으로 중복 실행되지 않는가 ([validation.md](validation.md))
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
## 이름 규칙

- `View`는 app 조회 경계의 화면/응답용 projection, `Snapshot`은 특정 시점의 읽기 결과다.
- `Row`는 저장소 조회 한 행, `Output`은 use case가 adapter에 반환하는 결과다.
- `Param`은 조회 조건 구성값, `Criteria`는 검색 조건, `Event`는 발생한 사실, `Request`는 외부 입력이다.
- 도메인 정책은 `policy`, 값 객체와 기능별 모델은 해당 feature의 `model`에 둔다. Querydsl/Redis/JWT/WebSocket 구현은 infra가 소유한다.

세부적으로 아직 정리하지 않은 이름과 구조는 [기술 부채 문서](technical-debt.md)에 기록한다.
