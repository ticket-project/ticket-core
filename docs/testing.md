# 테스트 기준

> [ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)의
> `payment` module 신설과 show(옛 catalog)/booking schema 재설계가 구현됐다(`ticketing`은 이후 booking으로
> 흡수)(entity-only 단계 — PG 연동, 결제 승인 API, `OrderConfirmed` listener는 아직 없다). [ADR 0006](adr/0006-bounded-context-module-boundaries.md)의
> BC 재편(`catalog` → `show` 개명, `venue`/`favorite` 신설)도 구현됐다. 아래 모듈 테스트·구조
> 테스트·module별 migration slice 테스트 표는 이 구현을 반영한다. `ShowGrade`/`ShowSeat` 제거(계획
> 문서 Phase 3 Task 8)가 끝나면서 그 둘을 대상으로 하던 Phase 1 baseline 테스트
> (`CurrentSeatVenueShowGradeSchemaTest`, `ShowGradePerformanceSeatPriceMismatchQueryTest`)도 함께
> 지워졌다 — 이 문서에서 더 이상 인용하지 않는다.

이 문서는 **새 테스트를 쓸 때의 관례와 각 테스트가 무엇을 고정하는지**를 정리한다. 모듈 경계는
[architecture.md](architecture.md), 예매 흐름은 [core-booking-lifecycle.md](core-booking-lifecycle.md),
실행 환경은 [operations.md](operations.md)를 함께 본다.

**무엇을 돌릴지 고르는 기준과 실행 명령, 결과 보고 규칙은 `/verify` 스킬이 원본이다**
(`.claude/skills/verify/SKILL.md`). 핵심 규칙 하나 — 전체를 돌리는 것은 기본값이 아니다.

## 단일 source set

별도 `integrationTest` Gradle source set과 subproject는 없다. 모든 테스트가 `src/test`에 있고,
**source set이 아니라 실행 특성**으로 종류를 나눈다. Spring 컨텍스트, `EntityManager`, 실제
DB/Redis가 필요하면 `@DataJpaTest`/`@SpringBootTest`/Testcontainers를 쓰고, 그렇지 않으면 순수
단위 테스트로 둔다. 클래스 이름에 `Integration`이나 `E2E`가 붙어 있어도 판정 기준은 실행
특성이다.

| 실행 특성 | 두는 것 | 두지 않는 것 |
| --- | --- | --- |
| Spring 컨텍스트 없는 단위 테스트 | 엔티티, 값 객체, 상태 전이, 정책, 불변식, use case(외부 port는 mock/fake) | Spring 컨텍스트, DB, Redis |
| `@ApplicationModuleTest(verifyAutomatically = false)` | 모듈 STANDALONE 부트스트랩 확인 | 전체 애플리케이션 구조 검증(그건 `ModularityTests`의 몫) |
| `@DataJpaTest` (+ Spring Modulith `@ModuleSlicing` 조합) | JPA/Querydsl/RepositoryAdapter, 해당 모듈 소유 migration만으로 schema가 만들어지는지 | 업무 규칙 단위 테스트 |
| `@SpringBootTest`(+ Testcontainers) | 전체 컨텍스트 기동, Redis/Redisson 실제 연동, 실제 HTTP로 스택을 관통하는 예매 E2E | 개별 클래스 단위 검증 |

Testcontainers를 쓰는 테스트는 **Docker가 실행 중이어야 한다.** Docker가 없으면 실패의 원인이
코드가 아니다.

## 모듈 테스트

각 Application Module에 `@ApplicationModuleTest(verifyAutomatically = false)` 기반 STANDALONE
테스트를 최소 하나씩 둔다(`BookingModuleTests`, `ShowModuleTests`, `VenueModuleTests`,
`FavoriteModuleTests`, `MemberModuleTests`, 그리고 ADR 0005로 신설된 `PaymentModuleTests`). 찜은
`favorite`가 데이터를 소유하고 `show`가 HTTP endpoint·use case를 갖는다(ADR 0006) — `ShowModuleTests`가
favorite의 공개 API를 `@MockitoBean`으로 대체해 그 조합을 검증한다. `verifyAutomatically = false`인
이유는 전체 애플리케이션 구조 검증이 각 모듈 테스트가 아니라 `com.ticket.ModularityTests` 한
곳의 책임이기 때문이다 — 모듈 테스트에서 구조 assertion을 중복하지 않는다.

외부 모듈의 공개 API는 `@MockitoBean`으로 대체하는 것이 기본이다. 의도적으로 실제 의존 모듈
조합이 필요한 소수의 contract test만 `DIRECT_DEPENDENCIES`를 쓰고, `ALL_DEPENDENCIES`는 전체
조합이 필요한 이유가 있는 경우로 제한한다.

## 구조 테스트

모듈 경계와 의존 방향을 **실제로 강제하는** 테스트다. 구조를 건드렸다면 이것부터 돌린다
(명령은 `/verify`).

| 테스트 | 고정하는 것 |
| --- | --- |
| `com.ticket.ModularityTests` | Application Module 경계 전체(`ApplicationModules.of(...).verify()`). 아직 이동하지 않은 legacy 패키지는 명시 predicate로 검증 대상에서 뺀다 |
| `com.ticket.*.*ModuleTests` (`BookingModuleTests`, `ShowModuleTests`, `VenueModuleTests`, `FavoriteModuleTests` 등) | 각 모듈이 STANDALONE으로 부트스트랩되는지 |
| `com.ticket.shared.SharedModulePurityTest` | `com.ticket.shared`에 bean을 등록하는 코드(`@Configuration`/`@Component` 메타 애노테이션)를 두지 않는 것. `sharedModules`인 shared는 모든 모듈 테스트에 함께 뜨므로 여기 배선이 있으면 모든 STANDALONE 테스트가 그것을 띄운다 |
| `com.ticket.DomainPurityTest` | 6개 BC 전부에서 `<bc>.domain`이 다른 BC를 참조하지 않는 것. 찜 데이터 조합은 `show.application`이 favorite의 공개 API로 한다(ADR 0006) |
| `com.ticket.AggregateAssociationTest` | 같은 module 안에서 다른 aggregate를 `@ManyToOne`/`@OneToOne`/`@OneToMany`/`@ManyToMany` 객체 연관관계로 새로 묶지 않는 것. 실측된 연관관계를 고정한다(`docs/architecture.md`의 "Bounded Context와 Aggregate") |
| `ControllerParameterConstraintTest` | 요청 파라미터 제약을 `controller.docs` 인터페이스에만 두는 것 |
| `com.ticket.DocumentationTests` | Spring Modulith `Documenter`로 module 구조 문서를 생성하는 것(`build/spring-modulith-docs`, `ModularityTests`와 같은 legacy 제외 predicate 사용) |

`com.ticket.bootstrap`을 검사하던 `BootstrapArchitectureTest`는 그 패키지가 완전히 비어(ADR 0003
§8·§9, 전역 기술 설정이 `shared`/`config`로 옮겨져) ArchUnit이 검사 대상 없는 rule을 실패로 보는
것을 실측 확인해 지웠다 — `com.ticket.bootstrap`이 다시 class를 가지면 그때 필요한 규칙을 다시
만든다.

새 코드의 위치가 의심스러우면 `ModularityTests`부터 돌린다. 무엇을 막는지는
[architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙)에 정리돼 있다.

## Modulith 이벤트 테스트

주문 생성/종료 후속 처리는 Spring Modulith의 `PublishedEvents`와 `Scenario`로 검증한다.

- `PublishedEvents`와 실제 publication 상태로 booking DB 트랜잭션 성공 시 이벤트/publication이
  함께 저장되고, rollback 시 둘 다 없는지 고정한다(예:
  `OrderStartedPublicationAtomicityTest`).
- `Scenario`로 listener 완료를 기다리고, 첫 시도 실패 후 publication FAILED, 재제출 성공 후
  COMPLETED/ARCHIVED, 재시도 상한 초과 시 자동 제외를 검증한다(예:
  `EventPublicationMaintenanceScenarioTest`). 고정 clock과 deterministic fake를 쓰고
  `Thread.sleep`을 쓰지 않는다.
- 동일 `eventId`가 여러 번 전달돼도 최종 상태와 WebSocket 의미가 한 번 처리한 것과 같은지
  고정한다(멱등성 테스트). 상세 흐름은
  [core-booking-lifecycle.md](core-booking-lifecycle.md)를 본다.

## 모듈별 migration slice 테스트

각 모듈이 자신의 Flyway 이력(`db/migration/__root` + `db/migration/{module}`)만으로 schema가
만들어지고 CRUD가 동작하는지 `@DataJpaTest`와 module slicing 조합으로 검증한다. 지금 존재하는
것은 `BookingModuleSlicingSchemaTest`, `ShowModuleSlicingSchemaTest`(SHOWS.venue_id scalar 매핑과
그 FK 제거), `VenueModuleSlicingSchemaTest`(Venue/Seat 매핑, ADR 0006으로 show에서 분리),
`FavoriteModuleMigrationTest`(SHOW_LIKES의 옛 member/show FK 제거), `PaymentModuleSlicingSchemaTest`,
`BookingTicketSlicingSchemaTest`(TICKETS는 booking V5)(`src/test/java/com/ticket/bootstrap/migration/`)다.
다른 모듈의 migration이 있어야만 통과하면 실패로 간주한다.

`BookingPerformanceSalesPolicyMigrationTest`(booking V6)는 ADR 0006 "Performance의 책임 혼재" A2
정책 소유권 이관 migration을 검증한다 — 옛 show/`__root` 소유 `PERFORMANCE_QUEUE_POLICIES`/
`PERFORMANCES` 정책 컬럼 4개를 `BOOKING_PERFORMANCE_SALES_POLICIES`로 손실 없이 backfill하는지,
접수 기간이 해석 불가한 데이터(한쪽만 null, opens>=closes)는 migration을 실패시키는지, hold_time이
null이면 600초 기본값을 적용하는지, 정책 컬럼이 아예 없는 최소 baseline에서는 no-op인지를
고정한다. `OracleMigrationCompatibilityTest`도 이 backfill을 실제 Oracle에서 확인한다(Docker 필요).

`payment`는 ADR 0005의 entity-only 단계라 `PaymentModuleSlicingSchemaTest`가 검증하는 범위도 그만큼
좁다 — payment migration만으로 `PAYMENTS` 테이블이 만들어지고 entity가 저장·조회되는지, 그리고 다른
업무 모듈(booking 등)의 migration 없이도 그 자체로 성립하는지만(`TICKETS`는 booking V5라
`BookingTicketSlicingSchemaTest`가 booking migration 안에서 검증한다)
고정한다. controller나 PG/QR 연동은 이 범위가 아니다.

H2와 Oracle 호환성은 각각의 migration 검증 테스트(`OracleMigrationCompatibilityTest` 등)로
확인한다. 상세는 [operations.md의 DB 마이그레이션](operations.md#db-마이그레이션)을 본다.

## performance 기준 API와 가격 snapshot 회귀

ADR 0005로 좌석·등급·가격 조회 기준이 showId에서 performanceId로 바뀌면서 추가된 세 API의 계약
테스트는 모두 `PerformanceSeatQueryControllerContractTest`
(`src/test/java/com/ticket/booking/web/`) 하나에 있다.

- `GET /api/v1/performances/{id}/seat-map` — 정적 좌석 배치·등급·가격
- `GET /api/v1/performances/{id}/seats/status` — 동적 판매 상태(`performanceSeatId` 기준)
- `GET /api/v1/performances/{id}/seats/availability` — 등급별 잔여석

**N+1 회귀**는 `GetPerformanceSeatMapUseCaseTest`가 고정한다. `GetPerformanceSeatMapUseCase`는
Venue 배치·물리 Seat 좌표·PerformanceGrade 표시값을 show `PerformanceVenueLayoutCatalog`에서
(내부적으로 venue의 `VenueSeatLookup`을 호출), 판매 편성된 좌석과 확정 가격을 booking
`PerformanceSeatMapReadRepository`에서 각각 정확히 한 번만 조회해 조합한다(N+1 없이 고정된 query
수). 테스트는 `verify(..., times(1))`로 두 조회가 각각 한 번만 호출되는지 확인한다 — 회차 좌석
수가 늘어나도 호출 횟수가 늘지 않는지가 회귀 지점이다.

**가격 snapshot 불변성**은 두 단계로 고정된다. `OrderCreatorTest`는 주문 금액이 오직
`PerformanceSeat.unitPrice` 합계로만 계산되고(`sumTotalAmount`), show snapshot
(`PerformanceSaleCatalog`)은 표시값(등급 코드/이름, 좌석 라벨, show/venue 이름)에만 쓰인다는 것을
고정한다. `GetOrderDetailUseCaseTest`는 주문 상세 조회가 Order/OrderSeat에 생성 시점에 남긴
snapshot만 쓰고 show를 다시 조회하지 않는다는 것을 고정한다 — show 쪽 가격·표시값이 나중에
바뀌어도 기존 주문 상세가 그대로임을 보장하는 지점이 이 테스트다.

## 통합 테스트와 E2E

실제 인프라나 전체 컨텍스트가 필요한 검증이 여기 온다. 실행 조건과 Docker 주의는 `/verify`를
본다.

- `com.ticket.booking.infrastructure.redis.CoreRedisIntegrationTest`: Redis key·TTL·expiration
  listener·분산락(Testcontainers)
- `com.ticket.bootstrap.ApplicationContextLoadTest`: 전체 컨텍스트가 실제로 조립되는지
- `com.ticket.bootstrap.booking.BookingHappyPathE2ETest`: 좌석 조회부터 주문 취소까지 실제
  HTTP로 관통
- `com.ticket.bootstrap.booking.SeatContentionE2ETest`: 같은 좌석 동시 주문에서 하나만 성공
- `com.ticket.PlatformCompatibilityTest`: Spring Boot/Modulith 플랫폼 조합의 context 기동 smoke test

### 예매 E2E를 쓸 때

`com.ticket.bootstrap.support.BookingE2ETestSupport`를 상속한다. Testcontainers Redis와 H2,
인증 헬퍼, 좌석 상태 조회, 커밋 후 처리를 기다리는 `pollUntil`이 여기 있다. 데이터는
`fixture/booking-e2e-*.sql`이 만들고 `@Sql`이 메서드마다 초기화한다.

**모듈 사이 연결을 보는 테스트다.** 응답 코드만 확인하면 단위 테스트와 다를 게 없다. 좌석 상태를
다시 조회해 DB와 Redis가 함께 맞는지 본다.

주의 두 가지.
- 회차당 같은 회원은 `PENDING` 주문을 하나만 가질 수 있다. 한 테스트에서 주문을 여러 번 만들면
  앞 주문을 취소해야 한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 요청 스레드 밖에서 비동기로 끝낸다. 고정 sleep
  대신 `pollUntil`을 쓴다.

Redis key, TTL, expiration listener, Redisson 관련 변경은 단위 테스트만으로 확인했다고 보지 않는다.
**Docker가 없어 Testcontainers 기반 테스트를 돌리지 못했다면 단위 테스트 통과로 대체하지 않고
미검증으로 보고한다.**

**모듈 사이로 빈을 옮기는 변경은 `ApplicationContextLoadTest`까지 돌린다.** 단위 테스트는 각
클래스를 직접 생성하므로 빈 배선이 깨져도 통과한다. 기동 실패는 컨텍스트를 통째로 띄워야
드러난다.

## 새 테스트를 추가할 때

이 저장소의 관례는 아래와 같다. 테스트 파일 대부분이 이 형태다.

- 테스트 클래스에 `@SuppressWarnings("NonAsciiCharacters")`를 붙이고 **메서드 이름을 한국어로** 쓴다.

  ```java
  @SuppressWarnings("NonAsciiCharacters")
  class CreateOrderUseCaseTest {

      @Test
      void 유효한_요청이면_hold와_주문을_생성한다() { }

      @Test
      void 진행중인_pending_주문이_있으면_예외를_던진다() { }
  }
  ```

- `@DisplayName`과 `@Nested`는 사용하지 않는다. 저장소 전체에서 사용 사례가 없다.
- 테스트 클래스 이름은 대상 클래스 이름 + `Test`로 맞춘다. Controller 계약 테스트는
  `...ContractTest`, 모듈 STANDALONE 테스트는 `...ModuleTests`, Modulith 시나리오 테스트는
  `...ScenarioTest`를 쓴다.
- 도메인 규칙과 use case는 Spring 컨텍스트 없이 검증한다. port는 fake나 mock으로 대체한다. 다른
  모듈의 공개 API도 마찬가지로 mock/fake로 대체하고, 실제 모듈 조합이 필요하면 그 사실을 테스트
  이름과 애노테이션(`DIRECT_DEPENDENCIES`)으로 드러낸다.
- Redis나 DB에 실제로 붙어야 하는 검증은 `@DataJpaTest`/Testcontainers로 분리한다. 단위 테스트에
  섞지 않는다.
- 검증 규칙을 고정할 때는 계층을 맞춘다. API DTO와 Controller 계약은 `web`,
  `UseCase.Input` 계약은 `application`, 업무 불변식은 `domain` 테스트다. 같은
  규칙을 두 계층에서 동시에 고정하지 않는다. 기준은 [architecture.md의 계층별 검증 책임](architecture.md#계층별-검증-책임)을 본다.
- 주문·hold 흐름을 바꿨다면 성공 경로만 두지 않고 **취소, 만료, 이벤트 재시도, 순서 역전**을
  함께 고정한다.
- 트랜잭션 경계 자체가 계약인 지점은 그 사실을 테스트로 고정한다. 기존 예시로
  `execute는_DB_트랜잭션을_직접_시작하지_않는다`, `주문_저장_메서드는_트랜잭션으로_실행된다`가
  있다.

## 무엇을 돌릴지

이 결정은 `/verify` 스킬이 원본이다.

## 결과를 보고할 때

`/verify` 스킬의 "결과를 보고할 때"를 따른다. 돌리지 않은 범위를 밝히는 것과, 검증 실패 상태로
커밋하지 않는 것이 핵심이다.
