# Ticket Core Spring Modulith-first 전환 설계

## 문서 상태

- 작성일: 2026-09-01
- 상태: 대화 설계 승인 완료, 작성 문서 검토 대기
- 대상: `ticket` 저장소의 Ticket Core
- 제외: 형제 저장소 `ticket-queue`, 결제·PG callback 같은 미구현 기능 추가

## 한 문장 결정

기존의 계층별 Gradle 멀티모듈을 하나의 Spring Boot 애플리케이션으로 통합하고,
`booking`, `catalog`, `identity`, `admission`, `showlike`, `metadata`를 Java 패키지 기반의 닫힌
Spring Modulith Application Module로 재구성한다.

## 목표

1. 하나의 기능을 변경할 때 API·application·domain·infrastructure Gradle 프로젝트를 오가는 문제를 없앤다.
2. 업무 기능별 공개 API와 내부 구현을 Spring Modulith 검증으로 강제한다.
3. 모듈 간 순환 의존과 내부 타입 참조를 빌드에서 차단한다.
4. 모듈 간 후속 처리는 Spring Modulith 이벤트와 Event Publication Registry를 기본으로 한다.
5. 모듈별 테스트, Flyway migration, 문서, runtime 관측을 Spring Modulith 방식으로 통일한다.
6. 장래에 한 모듈을 별도 서비스로 추출할 때 Java 호출과 이벤트 운송 수단을 바꾸는 데 집중할 수 있도록
   데이터·트랜잭션·계약 소유권을 분리한다.

## 비목표

- 이 구조 변경을 이유로 Selection과 Hold의 업무 의미를 바꾸지 않는다.
- 결제 도메인, 메시지 브로커, 새로운 외부 서비스는 이번 전환에서 만들지 않는다.
- 모든 작은 클래스 묶음을 별도 Application Module로 만들지 않는다.
- Spring Modulith가 정하지 않는 HTTP API 제품 계약을 프레임워크 권장이라고 간주하지 않는다.
- 기존 custom outbox를 검증 없이 즉시 삭제하지 않는다. 최종 목표는 대체이지만 전환 중 이중 부수효과를
  허용하지 않는다.

## 결정 배경

현재 구조는 다음 계층별 물리 모듈을 사용한다.

```text
bootstrap
core-api
core-app
core-domain
core-infra
storage:redis-core
support:error
support:logging
```

이 구조는 계층의 기술 의존 방향을 강하게 만들지만 `Order`, `Hold`, `Selection` 같은 하나의 업무 기능이
여러 Gradle 프로젝트에 흩어진다. 실제 import 관계에서도 `order`, `hold`, `performanceseat`는 서로의
타입과 포트를 반복해서 사용한다. 반면 Spring Modulith는 Spring Boot 애플리케이션의 Java 패키지에서
논리 Application Module을 찾고, 모듈 루트를 공개 API로, 하위 패키지를 내부 구현으로 취급한다.

이번 전환의 우선 목표는 물리 계층 격리가 아니라 기능 응집도다. 장기적으로 서비스 분리도 고려하므로
업무 모듈마다 공개 계약, 트랜잭션, 데이터, 이벤트를 소유하게 한다.

## 검토한 접근법

### 1. 기존 Gradle 모듈을 유지하고 Spring Modulith만 추가

장점은 이동량이 적고 기존 컴파일 경계를 유지한다는 것이다. 그러나 기능 하나가 네 계층 모듈에
흩어지는 핵심 문제를 해결하지 못하며 Gradle 경계와 Modulith 경계를 동시에 관리해야 한다. 선택하지 않는다.

### 2. `core:booking`, `core:catalog` 같은 기능별 Gradle 모듈로 교체

기능 응집과 컴파일 격리를 함께 얻지만, 단일 배포 애플리케이션에 두 종류의 모듈 경계를 중복해 관리한다.
초기 경계 조정 비용이 크고 아직 독립 배포 필요성이 증명되지 않았다. 선택하지 않는다.

### 3. 단일 Gradle 애플리케이션 안의 기능별 Spring Modulith 모듈

Spring Modulith의 기본 모델과 가장 가깝고 패키지 이동으로 경계를 조정할 수 있다. 모듈 공개 API,
이벤트, 테스트, 문서, 관측을 한 모델에서 파생할 수 있다. 이 접근법을 선택한다.

## 기술 기준선

2026-09-01 현재 안정 조합을 목표로 한다.

- Spring Boot 4.1.1
- Spring Modulith 2.1.1
- Java 25
- 단일 Gradle Spring Boot 프로젝트
- `org.springframework.modulith:spring-modulith-bom:2.1.1`
- `spring-modulith-starter-jpa`
- `spring-modulith-starter-test` — test scope
- `spring-modulith-starter-insight` — runtime scope

플랫폼 업그레이드와 구조 이동을 한 커밋에 섞지 않는다. 먼저 Spring Boot 4.1.1과 Spring Modulith
2.1.1의 빈 애플리케이션 기준선을 통과시킨 뒤 기능 이동을 시작한다.

## 물리 프로젝트 구조

최종적으로 하나의 실행 가능한 Gradle 프로젝트만 남긴다.

```text
ticket
├─ build.gradle
├─ settings.gradle
└─ src
   ├─ main
   │  ├─ java
   │  └─ resources
   └─ test
      ├─ java
      └─ resources
```

기존 서브프로젝트의 library jar와 계층별 `build.gradle`은 전환 완료 후 제거한다. Redis, Querydsl,
security, web 같은 의존성은 단일 애플리케이션의 build에 선언하되 해당 기술을 사용하는 코드는 소유
Application Module의 `internal`에 둔다.

## Application Module 탐지와 패키지 구조

`com.ticket.TicketApplication`을 애플리케이션 루트에 두고 `@Modulith`를 사용한다. Spring Modulith
2.1의 `@Modulith`는 `@SpringBootApplication`과 `@Modulithic`을 합친 composed annotation이므로 별도로
중복 선언하지 않는다. 기본 탐지 규칙인 direct subpackages를 사용하며 custom detection strategy를
만들지 않는다.

```text
com.ticket
├─ TicketApplication
├─ booking
├─ catalog
├─ identity
├─ admission
├─ showlike
├─ metadata
└─ shared
```

각 직접 하위 패키지는 하나의 Application Module이다. 각 모듈의 기본 형태는 다음과 같다.

```text
com.ticket.booking
├─ package-info.java
├─ BookingManagement.java
├─ BookingResult.java
├─ OrderStarted.java
└─ internal
   ├─ web
   ├─ application
   ├─ domain
   └─ infrastructure
```

- 모듈 루트에는 다른 모듈이 사용해도 되는 Spring component, 불변 계약 타입, 이벤트만 둔다.
- `internal`과 그 하위 패키지는 다른 모듈이 참조하지 못한다.
- Controller와 HTTP DTO는 inter-module API가 아니므로 `internal.web`에 둔다.
- JPA Entity, Repository, Querydsl, Redis, JWT, outbox 구현은 모두 `internal`에 둔다.
- 공개 타입이 많아 실제로 계약 묶음을 구분해야 할 때만 `@NamedInterface`를 추가한다.
- legacy 전환 편의를 위한 open module은 최종 상태에서 허용하지 않는다.

각 `package-info.java`는 `@ApplicationModule`로 display name과 허용 의존성을 명시한다.
`shared`는 `@Modulith(sharedModules = "shared")`로 모든 모듈 테스트에 포함되는 shared module로 선언한다.

## 업무 모듈

### `catalog`

소유 책임:

- Show
- Performance
- Seat
- 공연별 예매 가능 시간과 Hold 한도
- PerformanceQueuePolicy, QueueMode, QueueLevel
- 공연·회차·좌석 조회

`QueueMode`와 `QueueLevel`은 JWT 검증 기술이 아니라 Performance의 예매 진입 정책이므로 `catalog`가
소유한다. `PerformanceSeat`의 판매 상태는 `booking`이 소유한다.

공개 API 예시 책임:

- Performance 존재·예매 가능 여부 조회
- 좌석이 Performance에 속하는지 확인
- Booking이 사용할 불변 BookingPolicy snapshot 제공
- Catalog 공개 이벤트 제공

### `booking`

소유 책임:

- PerformanceSeat 판매 상태
- Selection
- Hold
- Order와 OrderSeat
- 주문 취소·만료
- 좌석 분산락
- Redis Selection·Hold
- 좌석 상태 WebSocket 발행
- module listener의 멱등 처리

Order, Hold, Selection, PerformanceSeat는 동일한 판매 정합성, 잠금, 보상, 만료 흐름을 공유하므로
하나의 `booking` 모듈에 둔다. 각각을 독립 모듈로 만들지 않는다.

### `identity`

소유 책임:

- Member
- 이메일·소셜 로그인
- OAuth2 provider adapter
- 비밀번호
- access·refresh token
- 회원 상태와 탈퇴

Auth와 Member는 현재 수명주기와 저장소 의존이 강하므로 하나의 모듈로 둔다.

### `admission`

소유 책임:

- admission token 설정
- claim decode와 검증
- 만료·위조 실패 해석
- Booking에 공개하는 Admission 검증 API

Booking은 공개 API만 호출하고 JWT 구현과 claim 타입을 참조하지 않는다.

### `showlike`

소유 책임:

- 회원의 Show 좋아요 추가·삭제·조회
- `(memberId, showId)` 중복 방지

`showlike`는 `identity`와 `catalog`에 단방향으로 의존하며 반대 의존을 허용하지 않는다.

### `metadata`

소유 책임:

- 여러 모듈이 공개한 메타데이터를 조합하는 HTTP endpoint

`metadata`는 공통 기반 모듈이 아니라 상위 조합 모듈이다. 다른 모듈의 internal enum을 import하지 않고
공개 metadata 계약만 사용한다. 현재 사용처가 불분명한 CommonCode JPA Entity를 자동으로 이 모듈에
포함하지 않는다.

### `shared`

`shared`는 실제로 생성하며 다음 두 공개 계약만 초기 구성으로 둔다.

- module-owned 문제 정보를 전달하는 `BusinessProblem` 계약
- 그 계약을 운반하는 `BusinessException`

각 업무 모듈의 internal 오류 정의가 `BusinessProblem`을 구현하고, 애플리케이션 루트의 전역 web
adapter가 `BusinessException`을 `ProblemDetail`로 변환한다. `shared`는 구체 업무 code나 메시지를
소유하지 않고 Spring Web 타입에도 의존하지 않는다.

새 타입을 추가하려면 다음을 모두 만족해야 한다.

- 여러 독립 모듈에서 의미가 동일하다.
- 특정 업무 Entity나 기술 adapter가 아니다.
- 모든 Application Module 테스트에 항상 포함돼도 된다.
- Repository, HTTP DTO, Redis key, Querydsl 타입을 포함하지 않는다.

공통 식별자나 시간 추상화는 실제로 둘 이상의 독립 모듈이 같은 의미로 요구하기 전에는 추가하지 않는다.

## 허용 의존성

업무 모듈 의존성은 다음 DAG로 고정한다.

```text
metadata ─────→ catalog / booking / identity

showlike ─────→ catalog
        └─────→ identity

booking ──────→ catalog
        ├─────→ identity
        └─────→ admission

catalog        → 업무 모듈 의존 없음
identity       → 업무 모듈 의존 없음
admission      → 업무 모듈 의존 없음
```

`shared`는 모든 모듈에서 암묵적으로 사용할 수 있다. 그 외 의존은 `allowedDependencies`에 선언하지
않으면 허용하지 않는다.

## 데이터 소유권

| 모듈 | 소유 데이터 |
| --- | --- |
| `catalog` | Show, Performance, Seat, PerformanceQueuePolicy |
| `booking` | PerformanceSeat, Selection, Hold, Order, OrderSeat |
| `identity` | Member, MemberSocialAccount, 인증·refresh token 상태 |
| `showlike` | ShowLike |
| `admission` | 영속 업무 데이터 없음 |
| `metadata` | 영속 업무 데이터 없음 |

한 테이블, Redis key, 상태 전이는 하나의 모듈만 소유한다. 다른 모듈의 Repository나 테이블을 직접
사용하지 않는다.

### 모듈 간 JPA 관계 제거

다음 관계는 scalar ID로 바꾼다.

```text
PerformanceSeat.performance → performanceId
PerformanceSeat.seat        → seatId
ShowLike.member             → memberId
ShowLike.show               → showId
```

같은 모듈 안의 `Performance → Show`, `OrderSeat → Order`, `MemberSocialAccount → Member` 관계는 유지할
수 있다. 모듈 경계를 넘는 DB foreign key도 최종 상태에서는 제거한다. 그래야 STANDALONE
`@ApplicationModuleTest`가 다른 모듈의 migration 없이 해당 모듈 schema를 만들 수 있다.

명령 경로의 교차 모듈 join은 허용하지 않는다. 화면 조합은 공개 Query API 또는 이벤트로 유지하는
local projection을 사용한다.

## 동기 API와 이벤트

Spring Modulith 권장에 따라 이벤트를 모듈 간 후속 반응의 기본 수단으로 사용한다. 그러나 현재 요청의
진행 여부를 즉시 판단해야 하는 경우에는 모듈 공개 API를 동기 호출한다.

동기 호출 대상:

- Booking이 Admission 자격을 즉시 확인한다.
- Booking이 Catalog 예매 정책과 좌석 소속을 즉시 확인한다.
- Booking 또는 ShowLike가 Identity 회원 상태를 즉시 확인한다.

이벤트 대상:

- 원 트랜잭션 성공 뒤 다른 모듈이 반응한다.
- 여러 소비자가 독립적으로 반응한다.
- 지연과 재시도를 허용한다.
- 장래에 broker로 externalize할 가능성이 있다.

공개 이벤트는 발행 모듈 루트 또는 명시적 Named Interface에 두며 과거형 사실로 이름 짓는다.
Entity, Lazy proxy, Repository, 기술 예외를 payload에 포함하지 않는다. 식별자, 발생 시각, 필요한
불변 값과 schema version만 포함한다. 실제 소비자가 없는 이벤트는 미리 만들지 않는다.

## Order 생성 트랜잭션

Order 생성은 다음 순서를 지킨다.

```text
Booking HTTP adapter
  → Admission 공개 API 검증
  → Identity 공개 API 검증
  → Catalog BookingPolicy snapshot 조회
  → Booking 분산락과 Redis Hold 생성
  → Booking 전용 DB transaction
       Order 저장
       OrderSeat 저장
       Hold history 저장
       application event 발행
  → commit
  → @ApplicationModuleListener 후속 처리
```

외부 모듈 호출과 Redis I/O 중 Booking DB transaction을 유지하지 않는다. Booking transaction은
Booking 소유 테이블만 변경한다. Redis Hold 뒤 DB 저장이 실패하면 Booking이 Hold를 보상 해제한다.
Catalog가 반환하는 정책은 Entity가 아니라 불변 snapshot이며, OrderSeat는 주문 시작 시점의 가격을
복사해 보존한다.

## Event Publication Registry

모듈 이벤트 소비는 `@ApplicationModuleListener`를 기본으로 한다. JPA 기반 Event Publication Registry가
발행 transaction 안에서 publication을 저장하고 listener의 처리 상태를 관리한다.

목표 상태:

- custom outbox의 범용 전달·재시도 책임은 Event Publication Registry로 대체한다.
- Redis Hold와 WebSocket의 현재 상태 재검증·멱등성은 Booking listener의 업무 로직으로 유지한다.
- 동일 부수효과를 custom outbox와 Modulith listener가 동시에 실행하지 않는다.
- 전환은 흐름별로 한 번에 하나의 전달 경로만 활성화한다.

운영 기준:

- publication completion mode는 `ARCHIVE`를 사용한다.
- 완료 archive는 매일 정리하고 완료 시각 기준 30일이 지난 항목을 삭제한다.
- `republish-outstanding-events-on-restart`는 다중 인스턴스 충돌을 피하기 위해 사용하지 않는다.
- staleness check interval은 1분, `PUBLISHED`는 5분, `PROCESSING`과 `RESUBMITTED`는 10분을 초기
  기준으로 삼는다.
- 실패 publication 재제출은 1분 주기, batch 100, 최대 in-flight 4로 제한한다.
- completion attempt가 10회를 넘은 publication은 자동 재제출에서 제외하고 경고와 수동 복구 대상으로
  전환한다.
- listener는 event ID 또는 업무 식별자로 멱등해야 한다.
- 실패·재시도 횟수·처리 지연을 metric과 trace로 관측한다.

프레임워크가 직접 제공하는 정책은 다음 설정으로 고정한다.

```yaml
spring:
  modulith:
    events:
      completion-mode: archive
      republish-outstanding-events-on-restart: false
      staleness:
        check-interval: 1m
        published: 5m
        processing: 10m
        resubmitted: 10m
```

archive 30일 정리와 실패 publication의 1분 주기 재제출은 자동 설정이 아니다. 각각
`CompletedEventPublications`와 `FailedEventPublications.resubmit(ResubmissionOptions)`를 호출하는 운영
component로 구현한다. `ResubmissionOptions`의 filter로 completion attempt 10회 이하만 선택하고 batch와
in-flight 한도를 적용한다.

이번 전환에는 외부 broker와 `@Externalized`를 포함하지 않는다. 외부 broker가 실제로 도입되는 시점에
별도 설계로 대상 이벤트와 broker를 정하고 Spring Modulith 2.1의 outbox externalization mode를
사용한다. 단순 module-listener externalization을 영속 outbox와 동일하다고 간주하지 않는다.

## Module-aware Flyway

`spring.modulith.runtime.flyway-enabled=true`를 사용하고 migration을 모듈별로 배치한다.

```text
src/main/resources/db/migration
├─ __root
├─ booking
├─ catalog
├─ identity
└─ showlike
```

- 기존 운영에 이미 적용된 migration은 checksum과 global history를 보존하기 위해 `__root`에 유지한다.
- 전환 이후 새 migration은 소유 모듈 폴더에 둔다.
- 각 모듈은 독립적인 Flyway history table과 모듈 내부 버전 순서를 사용한다.
- Event Publication Registry schema는 업무 모듈이 아니라 `__root` runtime infrastructure migration이
  소유한다.
- JPA registry table과 archive table은 Spring Modulith 2.1.1의 JPA mapping에 맞는 DDL을 Flyway로
  생성한다. 운영의 `spring.jpa.hibernate.ddl-auto`는 `validate`로 두어 ORM schema 생성·수정에 의존하지
  않는다. JDBC registry 전용 schema initialization 설정은 JPA 구성을 위한 해법으로 사용하지 않는다.
- STANDALONE module test는 해당 모듈과 root migration만 실행해도 성공해야 한다.

## 오류 처리

Spring Modulith는 HTTP 오류 wire format을 규정하지 않는다. 이 설계는 다음 경계만 고정한다.

- 업무 실패는 원인을 판단하는 Application Module이 소유한다.
- 다른 모듈은 구체 internal 오류 enum이나 예외 타입을 import하지 않는다.
- 호출자가 성공·부재·거부에 따라 분기해야 하면 공개 result type으로 표현한다.
- 예외는 요청 전체가 실패해야 하는 경우에만 공개 API 경계를 통과한다.
- web adapter는 Spring Framework `ProblemDetail`로 transport 오류를 표현한다.
- 예상하지 못한 기술 오류의 내부 메시지와 stack trace를 HTTP에 노출하지 않는다.
- listener 실패는 발행 transaction을 되돌리지 않고 Event Publication Registry의 실패·재시도 흐름으로
  처리한다.

`ProblemDetail` 계약은 다음으로 고정한다.

- `type`: `urn:ticket:problem:{module}:{code}`
- `title`: 로그나 stack trace를 포함하지 않는 안정적인 요약
- `status`: HTTP 상태 숫자
- `detail`: 클라이언트에 노출 가능한 설명
- extension `code`: `{MODULE}_{CODE}` 형식의 안정적인 업무 code

각 모듈의 internal 오류 정의가 이 값을 소유하고 전역 handler는 구체 오류 타입을 알지 않는다. 기존
E-CODE와의 호환이 필요하면 API 전환 단계에서 새 code로 매핑하되, 전역 shared 업무 카탈로그를 만들지
않는다.

## 테스트 설계

모든 테스트는 표준 `src/test/java`에 둔다. 별도 `integrationTest` source set은 최종 구조에서 사용하지
않는다.

```text
src/test/java/com/ticket
├─ ModularityTests
├─ DocumentationTests
├─ booking/BookingModuleTests
├─ catalog/CatalogModuleTests
├─ identity/IdentityModuleTests
├─ admission/AdmissionModuleTests
├─ showlike/ShowLikeModuleTests
└─ metadata/MetadataModuleTests
```

### 구조 검증

`ApplicationModules.of(TicketApplication.class).verify()`를 CI 필수 게이트로 둔다. 검증 대상은 다음이다.

- 모듈 순환 의존 없음
- internal 접근 없음
- `allowedDependencies` 위반 없음
- Named Interface 위반 없음
- open module 없음

Spring Modulith 검증이 맡지 않는 domain purity 규칙이 반드시 필요할 때만 작은 ArchUnit 규칙을 보조로
추가한다. 기존 계층 구조 전체를 복제한 custom ArchUnit suite는 유지하지 않는다.

### Application Module 테스트

각 모듈은 최소 하나의 `@ApplicationModuleTest`를 갖는다.

- 기본 `STANDALONE` mode를 사용한다.
- 다른 모듈의 공개 API는 `@MockitoBean`으로 대체한다.
- 의도적으로 의존 모듈까지 검증할 때만 `DIRECT_DEPENDENCIES`를 사용한다.
- `ALL_DEPENDENCIES`는 전체 조합을 검증할 명확한 이유가 있는 소수 테스트에만 사용한다.
- 외부 mock이 계속 늘어나면 직접 호출을 이벤트로 바꿀 후보인지 검토한다.

### Slice와 이벤트 테스트

- JPA adapter는 `@DataJpaTest`와 `@ModuleSlicing`을 조합한다.
- 비동기 event 흐름은 Spring Modulith `Scenario`를 사용한다.
- 이벤트 발행만 확인할 때는 `PublishedEvents`를 사용한다.
- event listener의 성공, 실패, 멱등 재처리, publication 상태 전이를 검증한다.
- module-aware Flyway가 STANDALONE test에 필요한 migration만 실행하는지 검증한다.

### 전체 애플리케이션 검증

모듈 테스트 외에 최소한의 `@SpringBootTest`와 실제 HTTP 핵심 흐름 테스트를 둔다. 이는 Spring
Modulith가 보장하지 않는 전체 Bean 조립, security filter, Redis 연결, Flyway, serialization을 검증한다.
기존 테스트 이름이나 source set 관례는 강제하지 않지만 Selection·Hold·Order의 정합성, 보상, 동시성은
업무 요구사항이므로 계속 검증한다.

### 문서 테스트

`Documenter`를 사용하는 `DocumentationTests`가 다음을 생성한다.

- 전체 module dependency diagram
- module canvas
- 공개 API와 Named Interface
- Spring component
- 이벤트 발행·소비 관계

생성물은 `build`와 CI artifact로 취급하며 사람이 직접 수정하는 원본 문서로 사용하지 않는다.

## Runtime과 관측

`spring-modulith-starter-insight`로 다음을 활성화한다.

- Modulith actuator
- 모듈 API 호출 trace
- 이벤트 publication metric과 trace
- runtime application module model

`/actuator/modulith`는 외부 공개 API가 아니며 관리망 또는 인증된 actuator 경로에서만 접근한다.
구조 검증의 권위 있는 게이트는 CI의 `verify()` 테스트다. runtime startup verification은 local, test,
staging에서 `spring.modulith.runtime.verification-enabled=true`로 활성화하고 production에서는 `false`로
둔다. production은 검증을 통과한 동일 artifact만 배포하며, startup 검증을 켜는 후속 변경은 이 설계
범위에 포함하지 않는다.

## 전환 순서 원칙

상세 파일별 작업은 후속 implementation plan에서 작성한다. 설계 수준의 순서는 다음과 같다.

1. Spring Boot 4.1.1과 Spring Modulith 2.1.1 기준선을 독립적으로 만든다.
2. 단일 Gradle 애플리케이션과 `@Modulith` 구조 검증을 만든다.
3. 의존성이 없는 `catalog`, `identity`, `admission`부터 vertical slice로 이동한다.
4. `booking`을 이동하면서 교차 모듈 JPA 관계와 Repository 접근을 공개 API로 바꾼다.
5. `showlike`, `metadata`를 이동하고 교차 모듈 조회를 제거한다.
6. module-aware Flyway로 전환하되 기존 migration은 `__root`에서 보존한다.
7. 후속 처리 흐름별로 custom outbox를 Event Publication Registry로 교체한다.
8. module tests, Scenario, DocumentationTests, Insight를 완성한다.
9. 기존 Gradle 서브프로젝트와 계층 패키지, 대체된 outbox 인프라를 제거한다.

각 단계는 구조 검증과 관련 module test가 통과하는 상태로 끝나야 한다. 부수효과 전달 경로는 어떤
단계에서도 둘 이상 동시에 활성화하지 않는다.

## 완료 조건

- 단일 실행 Gradle 프로젝트만 남는다.
- 모든 업무 코드는 합의된 Application Module 또는 `shared`에 속한다.
- `ApplicationModules.verify()` 위반이 0개다.
- open application module이 없다.
- 다른 모듈 internal 참조가 0개다.
- 허용하지 않은 의존과 순환 의존이 0개다.
- 교차 모듈 JPA 객체 관계와 DB foreign key가 0개다.
- 명령과 조회가 다른 모듈 Repository·테이블을 직접 사용하지 않는다.
- 각 모듈의 STANDALONE `@ApplicationModuleTest`가 통과한다.
- `@ModuleSlicing`, `Scenario`, publication 실패·재처리 테스트가 통과한다.
- module-aware Flyway가 root와 모듈 migration을 독립적으로 실행한다.
- custom outbox의 범용 전달·재시도 역할이 Modulith registry로 대체되고 중복 부수효과가 없다.
- module diagram과 canvas가 코드에서 생성된다.
- actuator와 tracing에서 합의된 모듈 의존과 이벤트 상호작용을 확인할 수 있다.
- Selection·Hold·Order의 기존 업무 불변식과 동시성 안전성이 유지된다.

## 주요 위험과 대응

### 플랫폼 업그레이드와 구조 변경의 원인 혼합

Boot 4.1.1 업그레이드를 먼저 독립 검증하고 기능 패키지 이동과 같은 변경 단위에 섞지 않는다.

### custom outbox 대체 중 전달 중복 또는 누락

흐름별 cutover와 feature switch를 사용한다. 동일 side effect를 두 경로에서 발행하는 shadow execution은
하지 않는다. 기존 멱등·순서 역전 테스트를 새 listener에 적용한 뒤 기존 경로를 제거한다.

### 기존 Flyway history 손상

이미 적용된 migration을 모듈 폴더로 재분배하지 않고 `__root`로만 이동한다. 새 migration부터 모듈별
history를 사용한다.

### shared module의 무제한 확장

모든 모듈 테스트에 항상 필요한지와 업무·기술 독립성을 함께 만족하지 않으면 shared에 넣지 않는다.
초기에는 `BusinessProblem`과 `BusinessException` 이외 타입을 넣지 않는다.

### 잘게 쪼갠 Entity 중심 모듈

모듈 이름을 Entity 수와 맞추지 않는다. 함께 원자적으로 변해야 하는 Order·Hold·Selection은 Booking에
남긴다. 새 모듈은 독립된 업무 책임과 단방향 의존이 증명될 때만 추가한다.

## Spring Modulith가 결정하는 것과 프로젝트가 결정하는 것

Spring Modulith가 제공하고 이 설계가 따르는 결정:

- package 기반 Application Module 탐지
- module root API와 internal 캡슐화
- 의존성 검증과 Named Interface
- application event 기반 결합 완화
- Event Publication Registry
- `@ApplicationModuleTest`, `@ModuleSlicing`, `Scenario`
- module-aware Flyway
- Documenter, actuator, observability

도메인과 프로젝트가 결정한 것:

- `booking`, `catalog`, `identity` 등의 실제 경계
- Order·Hold·Selection을 하나의 transaction boundary로 묶는 것
- 공개 API가 반환할 snapshot의 내용
- Redis Hold의 멱등성과 순서 방어
- HTTP `ProblemDetail`의 업무 code 체계
- 어떤 이벤트를 장래에 외부화할지

두 범주를 섞어 프로젝트 결정을 Spring Modulith의 공식 권장이라고 표현하지 않는다.

## 공식 근거

- [Spring Modulith 2.1.1 개요와 BOM](https://docs.spring.io/spring-modulith/reference/index.html)
- [`@Modulith` composed annotation API](https://docs.spring.io/spring-modulith/docs/current/api/org/springframework/modulith/Modulith.html)
- [Application Module, internal package, allowed dependency, Named Interface](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [Application event와 Event Publication Registry](https://docs.spring.io/spring-modulith/reference/events.html)
- [`@ApplicationModuleTest`, bootstrap mode, `@ModuleSlicing`, Scenario](https://docs.spring.io/spring-modulith/reference/testing.html)
- [runtime support와 module-aware Flyway](https://docs.spring.io/spring-modulith/reference/runtime.html)
- [Actuator와 module observability](https://docs.spring.io/spring-modulith/reference/production-ready.html)
- [Spring Modulith starter와 설정 속성](https://docs.spring.io/spring-modulith/reference/appendix.html)
