# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 위치를 선례로 삼지 말고, 미완료된 구조 이전으로 판단한다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)과
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md), 개발 흐름은
[development.md](development.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

**ADR 0005 반영 완료**: Venue/Seat/Grade/PerformanceGrade/PerformanceSeat 도메인 재설계와
`payment` module 신설은 구현이 끝났다. `ShowGrade`/`ShowSeat`는 entity·schema 모두 제거됐고, 아래
모듈 목록·DAG는 이 시점의 실제 코드(`ModularityTests`) 기준이다. `payment`는 이번 범위에서
entity/schema/repository만 있는 entity-only 모듈이며, PG 연동 흐름은 후속 ADR/계획으로 별도 승인한다
(ADR 0005 §3, §4). ADR 0005가 별도 module로 신설했던 `ticketing`은 2026-09-06에 booking으로 흡수됐다 —
`Ticket` entity/repository는 `booking.internal.{domain,infrastructure}.ticket`에 있고 `TICKETS` migration은
booking V5다.

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
├── booking/                 # 좌석 판매 상태(PerformanceSeat)·Selection·Hold·Order/OrderSeat·Ticket(entity-only), 공개: OrderStarted/OrderTerminated. admission token 검증(원래 admission module)도 소유
├── catalog/                 # Venue·Seat·Show·Performance·Grade/PerformanceGrade·대기열 정책·찜(showlike), 공개: BookingPolicyLookup, ShowLookup, PerformanceSaleCatalog, PerformanceVenueLayoutCatalog
├── member/                # 회원·인증·소셜 로그인·전역 SecurityFilterChain, 공개: AuthenticatedMember, MemberLookup
├── shared/                   # 다른 모듈이 호출하는 공유 계약만(UuidSupplier, CorsProperties, CursorPage)
├── web/                      # 이 앱이 HTTP로 말하는 방식(ApiResponse·ErrorMessage·ResultType·SliceResponse)
├── config/                   # 업무 모듈을 모르는 전역 배선(Swagger/Querydsl/Redisson/P6Spy/
│                                scheduling/clock/UUID/event publication + JpaAuditingConfig)
├── seed/                     # 여러 모듈의 테이블을 raw SQL로 적재하는 시드 러너
└── payment/                  # Payment(결제 시도) entity/schema/repository만 갖는 entity-only 모듈
```

`com.ticket`의 직접 하위 패키지는 9개다(`booking`, `catalog`, `member`,
`shared`, `web`, `config`, `error`, `seed`, `payment`) — `showlike`는 없다(찜을 catalog가
흡수했다. [찜(showlike)은 catalog가 흡수한다](#찜showlike은-catalog가-흡수한다) 참고).

`payment`는 ADR 0005로 신설됐다. **이번 구현 범위는 entity/schema/repository와 구조·schema 검증
테스트까지다** — controller, PG client, 결제 승인/실패/취소 API, callback/webhook은 만들지 않았다. 그래서
`ModularityTests.APPROVED_DEPENDENCY_DAG`에서 다른 어떤 모듈(`shared`/`web`/`error` 포함)도 참조하지 않는
완전한 leaf다(cross-module 의존 0). `payment.Payment`는 `orderId`를 scalar 컬럼으로만 갖고 booking의
entity를 JPA로 참조하지 않는다. 실제 PG 정산(`payment -> booking`) edge는 그 공개 계약을 구현하는 후속
단계에서만 추가한다 — 지금 빈 public contract로 미리 만들지 않는다(ADR 0005 §3, §4).

`Ticket`(확정된 OrderSeat에 발급되는 입장 권리)은 ADR 0005가 별도 `ticketing` module로 신설했지만
booking으로 흡수됐다. entity-only 상태에서 module 하나를 더 유지할 이유가 없었고, 발급 트리거가 되는
`OrderConfirmed`가 booking 안의 사건이라 같은 module에 두는 편이 단순하다. `Ticket.ownerMemberId`는
member에 대한 scalar 컬럼이고, `orderSeatId`는 같은 module의 OrderSeat를 가리키지만 기존 schema 관례대로
scalar 컬럼으로 둔다. 자동 발급 listener, QR/입장/사용/양도는 여전히 미구현이다.

`com.ticket.core`는 완전히 비었다 — 남았던 찜(showlike) 관련 코드가 모두 catalog로 옮겨졌다.
`com.ticket.bootstrap`은 legacy가 아니라 영구 composition-root 예외 자리이며 지금 production
class가 없다. `com.ticket.storage`도 없다. 다만 여러 module의 테스트가 함께 쓰는 test-support
기반 클래스(`ReadRepositoryTestSupport`/`InfraReadRepositoryTestSupport`)는 아직
`src/test/java/com/ticket/core/infra/support`에 남아 있다 — 이건 legacy 정리 대상이 아니라
별도로 결정할 test 인프라 소유권 문제라 이번 범위에서 건드리지 않았다.

각 모듈 root에는 다른 모듈이 쓰는 공개 계약(작은 interface + 불변 `record` snapshot, 이벤트)만
두고, 실제 구현(web/application/domain/infrastructure)은 모두 `<module>.internal` 아래에 둔다.
어떤 모듈도 `Type.OPEN`으로 선언하지 않는다.

## 승인된 의존 DAG

`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`가 고정한 실제 값이다(`shared`/`error`/`web`을
포함해 각 모듈이 실제로 참조하는 모듈 전부를 담는다).

```text
booking   -> catalog, member, shared, web, error
catalog   -> member, shared, web, error
member  -> shared, web, error
shared    -> (없음)
web       -> (없음)
config    -> member, shared
error     -> web
seed      -> member
payment   -> (없음)
```

`shared`·`error`·`web`은 `@Modulith(sharedModules = ...)`로 선언해 어느 모듈에서든 참조할 수 있다.
그래서 각 모듈 `@ApplicationModule(allowedDependencies = ...)`에는 **업무 모듈 의존 상한만** 적는다
(`catalog`/`member`는 상한이 비어 있다 — 업무 모듈 의존이 없다는 뜻이고,
`sharedModules`인 shared·error·web은 상한과 무관하게 항상 허용된다). 대신 어느 모듈이 실제로 이
셋을 참조하는지는 `ModularityTests.APPROVED_DEPENDENCY_DAG`가 모듈별로 고정하므로, HTTP를
노출하지 않던 모듈에 응답 봉투가 새로 들어오면 그 테스트가 실패한다.

`member`는 다른 업무 모듈에 의존하지 않는 기반 모듈이다(`error`/`web`은 예외). admission token 검증은
원래 별도 `admission` module이었으나 booking만 쓰는 능력이라 booking으로 흡수됐다
(`booking.internal.{application,infrastructure}.admission`, E8xxx 오류 코드 유지). `catalog`는
찜(showlike) 흡수로 회원 존재 확인을 위해 member를 참조한다(`MemberLookup`) — booking이
`Order.memberId`를 위해 member를 참조하는 것과 같은 패턴이다. `booking`이 그 위에 얹힌다. `shared`와
`web`은 어떤 모듈도 참조하지 않는 leaf고, `config`는 member의 공개 계약(`AuthenticatedMember`)과
shared(`UuidSupplier`)를 참조하지만 `config`를 참조하는 모듈은 없다. `seed`는 여러 모듈의 테이블을
raw SQL로 적재하고, 부하 테스트 회원만 member가 좁혀 연 `@NamedInterface("seed")`를 통해 만든다.

`payment`는 **이번 entity-only 단계에서 완전한 leaf다** — 업무 모듈은 물론 `shared`/`web`/`error`도
참조하지 않는다. controller가 없어 응답 봉투(`web`)가 필요 없고, 자기 오류 타입을 아직 던지지 않아
`error`도 필요 없다. 실제 PG 정산 단계에서 `payment -> booking` edge가 추가되면 그 시점에 `web`/`error` 참조도 함께 늘어날 수 있다(ADR 0005
§4). 순환은 없다. 이 DAG를 바꾸려면 먼저
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)과
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)를 갱신한다.

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
  `catalog.BookingPolicyLookup`, `member.MemberLookup`)와 그
  반환값인 불변 `record` snapshot(`BookingPolicySnapshot`, `MemberStatus` 등)만 노출한다. JPA entity, Redis/JWT/Spring Web 타입은 공개 계약에
  두지 않는다. 컬렉션은 defensive copy한다.
- **모듈 후속 처리는 커밋 이후 이벤트로 한다.** booking이 발행하는 `OrderStarted`/
  `OrderTerminated`가 그 예다. 자세한 내용은 아래 [이벤트와 후속 처리](#이벤트와-후속-처리)를
  본다.

### Show/Performance/Grade/PerformanceGrade/PerformanceSeat와 catalog-booking 공개 계약

`Grade`(catalog)는 `VIP`/`R`/`S`/`A` 같은 재사용 가능한 코드·이름만 갖고 가격을 갖지 않는다.
`PerformanceGrade`(catalog, `Grade N:M Performance`의 연결 entity)가 특정 Performance에서 쓸 Grade
선택·회차별 가격·표시 순서를 갖는다 — 가격의 원본은 여기다. `PerformanceSeat`(booking)는
`performanceId`/`seatId`/`performanceGradeId`를 scalar 컬럼으로만 갖고 catalog entity를 JPA로
참조하지 않으며, 판매 좌석 생성 시 `PerformanceGrade.price`를 `unitPrice`로 snapshot하고
`@Version`으로 동시 확정을 방어한다. 가격은 세 시점의 사실로 나뉜다.

```text
PerformanceGrade.price   운영자가 구성한 회차 등급 가격 (판매 오픈 전에만 변경 가능)
  -> PerformanceSeat.unitPrice   판매 좌석 생성 시 snapshot (판매 오픈 후 불변)
    -> OrderSeat.unitPrice       주문 생성 시 snapshot (생성 후 불변)
```

booking이 이 판매 편성·주문 표시 snapshot을 만들 때 쓰는 catalog 공개 계약은 두 개다. JPA entity는
어느 쪽도 노출하지 않는다.

- `catalog.PerformanceSaleCatalog#getSaleSnapshot(performanceId, seatIds)` — `PerformanceSaleSnapshot`
  (요청 seat 중 그 회차 Venue에 실제로 속한 좌석의 표시값 + 그 회차에 배정된 모든
  PerformanceGrade 표시값·가격)을 반환한다. `PerformanceSeat` 생성과 `GetSeatAvailabilityUseCase`
  (등급별 잔여석)가 쓴다.
- `catalog.PerformanceVenueLayoutCatalog#getVenueLayout(performanceId)` — `PerformanceVenueLayout`
  (Venue 배치·그 Venue의 모든 물리 Seat 좌표 + 그 회차에 배정된 PerformanceGrade 표시값, 가격은
  담지 않음)을 반환한다. 정적 seat-map API(`GetPerformanceSeatMapUseCase`)가 쓰고, 판매 편성되지
  않은 물리 Seat는 booking local 조회로 걸러낸다.

**`ShowGrade`/`ShowSeat`는 폐기됐다 — entity·schema 모두 제거됐고 참조하지 않는다.** Show 단위
공통 가격표가 필요하면 `PerformanceGrade`에서 `minPrice`/`maxPrice`를 파생한다
(`catalog.GetShowDetailUseCase.PriceSummary`). Show 전체 회차에 적용할 좌석 템플릿이 실제로
필요해지면 그때 별도 개념(`ShowSeatTemplate` 등)을 추가한다 — 지금 이름만 바꿔 남기지 않는다.
`booking.internal.web.ShowVenueLayoutController`(`/api/v1/shows/{showId}/venue-layout`)는 물리
Venue 배치만 반환하는 별개의 show 기준 API이고,
회차 기준 `/api/v1/performances/{performanceId}/seat-map`과는 다른 용도다(둘 다 참고
[개발 기준](development.md#쇼회차좌석-조회)).

### 찜(showlike)은 catalog가 흡수한다

찜 개수·추가·삭제·내 찜 목록은 모두 catalog가 소유한다. 원래는 별도 module
(`com.ticket.showlike`)이었지만, catalog의 공연 상세가 좋아요 개수를 얻으려면 찜 데이터를
참조해야 하고(catalog → showlike) showlike의 write 경로는 공연 존재 확인을 위해 catalog를
참조해야 해서(showlike → catalog) 두 module 사이에 순환이 생겼다. "내 찜 목록"
(`/api/v1/members/me/likes`)까지 member에 남기면 회원 관점 조회 때문에 member와 같은
순환이 재발하므로, 찜에 관한 모든 것을 catalog 하나로 흡수해 순환의 여지 자체를 없앴다.

개수는 `Show.viewCount`와 같은 성격의 파생 지표이지 독자적인 업무가 아니라는 판단이 근거다.
`ShowLike.member`는 member Member에 대한 `@ManyToOne` 대신 scalar `memberId` column이고
(module을 넘나드는 JPA 연관관계는 금지), `ShowLike.show`는 같은 module 안이라 `@ManyToOne` 그대로
쓴다. catalog는 회원 존재 확인을 위해 member의 `MemberLookup`을 참조한다(단방향, 순환 없음).
URL·JSON 계약(`/api/v1/likes/**`, `/api/v1/members/me/likes`)은 흡수 전과 동일하다.

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

응답 봉투(`ApiResponse`/`ErrorMessage`/`ResultType`/`SliceResponse`)는 `com.ticket.web`에 있다 —
Jackson·Swagger에 결합된 REST 표현 계약이고 이 앱의 모든 채널이 쓰는 것도 아니라서(booking의 좌석
상태 WebSocket payload는 쓰지 않는다) `shared`가 아니라 소유 모듈을 둔다. `error`가 봉투를 만들어
반환하므로 `error -> web` 단방향이며, `web`이 `error`를 참조하면 곧바로 순환이 되어
`ModularityTests`가 실패한다 — `ApiResponse`가 오류 타입을 모른 채 완성된 문자열만 받는 이유이고,
오류를 던지던 `shared.RequiredInput`이 지워진 이유이기도 하다.

E-code 값은 외부 계약이다. `gatling-test`가 `E4001`·`E6000`·`E6003`을 하드코딩하므로 소유 모듈이
바뀌어도 재번호하지 않는다. 전역 유일성은 `com.ticket.error.ErrorCodeUniquenessTest`가, 모듈
handler가 자기 오류만 잡는지는 `ExceptionHandlerScopeTest`가 강제한다.

배경은 [ADR 0002](adr/0002-module-owned-error-contracts.md)가 원본이다.

## 레거시 잔존 범위

`com.ticket.core`/`com.ticket.storage`/`com.ticket.support`는 Application Module로 옮기지 않은
legacy 패키지를 위한 자리이고, `ModularityTests`가 명시 predicate로 검증에서 제외한다.
`com.ticket.core`는 지금 production class가 없다 — 마지막까지 남아 있던 찜(showlike) 관련
코드가 catalog로 흡수되며 완전히 비었다. `com.ticket.bootstrap`도 같은 predicate로 제외되지만
성격은 다르다 — legacy가 아니라 영구적인 composition-root 예외 자리다(지금은 production
class가 없다). 근거와 경계는
[ADR 0003 §8](adr/0003-spring-modulith-application-module-boundaries.md)이 원본이다.

전역 기술 설정은 legacy가 아니다 — **어떤 업무 모듈도 참조하지 않는 `@Configuration`은
`com.ticket.config`가 소유한다**(Swagger, P6Spy, Querydsl, UUID 공급자, Redisson,
event-publication registry 유지보수, scheduling/clock, 그리고 member의 공개 계약만 쓰는
`JpaAuditingConfig`/`SecurityContextAuditorAware`).

**특정 모듈의 물건을 Spring에 등록하는 배선은 그 모듈이 자기 안에서 한다.** Spring이
`WebMvcConfigurer`·`WebSocketMessageBrokerConfigurer` 구현을 여러 개 모아 적용하므로 모듈마다
하나씩 둘 수 있고, 새 모듈이 자기 확장점을 추가할 때 전역 설정을 고칠 필요가 없다.

| 배선 | 소유 |
| --- | --- |
| `AuthenticatedMemberArgumentResolver` 등록 | `member.internal.infrastructure.security.MemberWebMvcConfig` |
| `JwtProperties` 등록 | `member.internal.infrastructure.auth.token.JwtConfig` |
| 카카오 HTTP client 등록 | `member.internal.infrastructure.auth.oauth2.HttpServiceConfig` |
| STOMP 브로커·endpoint·인증 인터셉터 | `booking.internal.infrastructure.websocket.WebSocketConfig` |

그 결과 `config`가 다른 모듈의 `internal`을 참조할 일이 없어 **`@NamedInterface`는 하나도 남지
않았다**(`seed`가 member의 `member.command`를 참조하는 것은 별개다).
`com.ticket.shared`에는 다른 모듈이 **호출하는 계약**만 두고 bean 등록은 두지 않으며, 이 규칙은
`com.ticket.shared.SharedModulePurityTest`가 강제한다. 근거는
[ADR 0003 §6](adr/0003-spring-modulith-application-module-boundaries.md)과
[§9](adr/0003-spring-modulith-application-module-boundaries.md)를 본다.

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

작은 모듈은 이 네 하위 패키지를 모두 갖지 않고 `internal` 바로 아래에 평평하게 둘 수 있다
(`payment`가 그 예다). 무엇을 쪼갤지는 실제 복잡도가 결정한다.

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
- 인증 principal 추출(`member.AuthenticatedMember`)
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
- refresh token, OAuth2 one-time auth code(`member`)

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
