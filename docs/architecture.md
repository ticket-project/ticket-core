# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 위치를 선례로 삼지 말고 미완료된 구조 이전으로 판단한다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)·
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)·
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)·
[ADR 0012](adr/0012-separate-global-http-security-from-member.md)을 함께 본다. 실행과 검증은
[operations.md](operations.md)를 본다.

## 원칙

- Ticket Core는 **단일 Gradle Spring Boot 프로젝트**다. 모듈 경계는 Gradle subproject가 아니라
  **Spring Modulith의 Application Module**이 강제한다. `com.ticket`의 직접 하위 패키지가 닫힌
  모듈이고, `com.ticket.ModularityTests`가 경계 위반을 잡는다.
- **Business Application Module은 Bounded Context 또는 독립적으로 캡슐화할 가치가 있는 supporting
  business capability와 정렬한다**([ADR 0006](adr/0006-bounded-context-module-boundaries.md)).
  `shared`와 `security`는 BC가 아닌 기술 모듈이다. 공유 계약은 `shared.api`·`shared.web`·
  `shared.exception`, 공통 실행 배선은 `shared.infrastructure`, 인증·인가와 그 조립은 `security`에 둔다.
- **다른 모듈이 쓰는 공개 계약은 `<module>.api`에 두고 `@NamedInterface("api")`로 선언한다**
  ([ADR 0014](adr/0014-module-public-contracts-live-in-api-packages.md)). 작은 interface와 불변
  `record` snapshot, enum만 두고 구현은 `endpoint`/`application`/`domain`/`infrastructure`/
  `exception` 패키지(`security`는 기능별 패키지)에 둔다. 별도 `internal` 계층은 두지 않는다 —
  Modulith는 root 밖의 하위 패키지를 이름과 무관하게 내부로 취급한다. 어떤 모듈도 `Type.OPEN`으로
  선언하지 않는다. `payment`처럼 공개할 계약이 없는 모듈에는 `api`를 만들지 않고, `booking`의
  `OrderStarted`/`OrderTerminated`만 root에 남는다(FQCN이 `EVENT_PUBLICATION.event_type`에 저장된
  값이라 옮기면 미완료 publication이 재처리되지 않는다). 정확한 배치는
  [Module Structure](#module-structure)와 [오류 처리](#오류-처리) 절을 본다.
- **`allowedDependencies`는 모듈 전체가 아니라 named interface 단위로 적는다**
  (`"show :: api"`, `"shared :: web"`). 무엇을 실제로 여는지가 선언에 남는다.
- **cross-module JPA 연관관계와 DB FK는 금지한다.** 다른 모듈의 aggregate를 참조해야 하면 `long`
  같은 scalar ID 컬럼만 갖는다.
- **모듈을 넘는 조회·명령은 상대 모듈이 공개한 API로만 한다.** 다른 모듈의 하위 패키지, Repository,
  JPA entity를 직접 import하지 않는다. JPA entity, Redis/JWT/Spring Web 타입은 공개 계약에 두지
  않는다.
- 정확한 모듈 집합과 DAG assertion은 `com.ticket.ModularityTests`가 원본이다. 이 문서의 표는 그
  요약이다.

## Bounded Context

| BC | 소유 | 비고 |
| --- | --- | --- |
| Show | Show, Category, Genre, Performer, Performance(회차 일정만), Grade, PerformanceGrade | 옛 `catalog`. 가격 원본은 `PerformanceGrade.price`. Show의 판매 필드(`displaySaleType`/`displaySaleWindow`)는 화면 표시 전용이고 실제 판단은 Booking의 `PerformanceSalesPolicy`가 한다(ADR 0007) |
| Venue | Venue, Seat, Region | 물리 시설. show에서 분리됨 |
| Booking | Selection, Hold, Order, OrderSeat, Ticket, PerformanceSalesPolicy | 좌석 선점부터 주문·발권까지. admission token 검증도 소유 |
| Payment | Payment | 결제 시도. entity-only 단계 |
| Like | Like | 찜 데이터·불변식. 대상 종류는 `LikeType`으로 값화(지금은 SHOW뿐). 찜 생성·해제·상태 조회 endpoint는 like가 소유하고, 공연 표시값을 조합하는 "내 찜 목록"만 show가 소유한다(ADR 0009) |
| Member | Member, MemberSocialAccount | 회원 테이블과 인증 데이터(비밀번호 해시·이메일·역할·탈퇴 상태). 인증 흐름의 **조립**(가입·로그인·갱신·로그아웃·탈퇴 절차, JWT, OAuth2 provider)은 `security`가 갖고, member는 `MemberAccountApi` 공개 계약만 제공한다 |

`Ticket`·admission token 검증이 별도 module에서 booking으로 흡수된 이력은
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)·
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)을 본다.

## Context Dependencies

**이 문서는 의존 관계 목록을 복제하지 않는다.** 어떤 edge가 승인돼 있는지와 그 검증은
`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`가 원본이고, 지금 실제 구조가 어떤지는
`com.ticket.DocumentationTests`가 `build/spring-modulith-docs`에 생성하는 diagram·canvas가
원본이다(생성 방법은 아래 [생성 문서](#생성-문서) 절). 여기 표로 옮겨 적으면 코드가 바뀔 때
조용히 어긋나고, 어긋난 쪽을 사람이 먼저 믿는다.

아래는 **왜 그 edge가 허용되는가**만 적는다.

- `shared`는 `@Modulith(sharedModules = "shared")`로 선언한다. 업무 모듈은 `shared :: api`,
  `shared :: web`, `shared :: exception` 셋을 필요한 만큼 명시해 참조한다 — `shared :: *`
  와일드카드는 쓰지 않는다.
- `security -> member`는 Authorization header의 access token을 member의 공개 계약으로 검증하고
  `AuthenticatedMember`를 SecurityContext에 넣기 위한 단방향 의존이다. security는 전역 API URL
  접근 정책·401/403 변환·MVC argument resolver를 소유하고, member는 security를 참조하지 않는다.
- `show -> member`는 찜 use case의 회원 확인, `show -> venue`는 표시값 조립, `show -> like`는
  공연 상세의 찜 개수와 "내 찜 목록" 조회 위임 때문이다.
- `like -> member`가 있다. 찜하기·찜 해제·찜 상태 조회를 like가 소유하면서(ADR 0009) 탈퇴 회원을
  걸러내기 위해 `MemberLookupApi.requireActive`를 직접 부르기 때문이다. **like는 업무 모듈 의존이
  없는 leaf가 아니다** — ADR 0006 시점의 설명(당시 `favorite`가 leaf였다)은 그 ADR의 역사적
  기록이고 현재 구조가 아니다.
- `booking -> show`는 있지만 `booking -> venue`는 없다. booking이 쓰는
  `PerformanceSaleCatalogApi`/`PerformanceVenueLayoutCatalogApi`를 show가 façade로 유지하기
  때문이다.
- `payment`는 entity-only 단계라 `shared`도 참조하지 않는 완전한 leaf다 —
  controller가 없어 응답 봉투가, 자기 오류 타입을 던지지 않아 `error`도 필요 없다.
- 순환은 없다. 새 edge가 필요해 보이면 먼저 반대 방향으로 풀 수 있는지 본다.

## Aggregates

| Aggregate root | 함께 사는 것 | BC |
| --- | --- | --- |
| Venue | — | Venue |
| Seat | — | Venue |
| Show | — | Show |
| Performance | PerformanceGrade | Show |
| Grade / Category / Genre / Performer | — | Show |
| PerformanceSalesPolicy | OrderAcceptanceWindow · HoldPolicy · BookingEntryPolicy(값 객체) | Booking |
| PerformanceSeat | — | Booking |
| Order | OrderSeat | Booking |
| Ticket | — | Booking |
| Payment | — | Payment |
| Like | — | Like |
| Member | MemberSocialAccount | Member |

**Hold와 Selection은 DB aggregate가 아니다.** 둘 다 Redis에만 있는 짧은 수명 상태다. DB에 남는
것은 좌석 단위 이력인 `HoldHistory`뿐이고 `holdKey` 문자열로 Order와 이어진다.

## Aggregate Rules

나누는 기준은 두 가지다.

- **자식이 부모 없이 존재할 수 없으면 같은 aggregate다.** OrderSeat는 Order 없이, MemberSocialAccount는
  Member 없이, PerformanceGrade는 Performance 없이 의미가 없다.
- **수가 많거나 독립적으로 경합하면 분리한다.** Venue와 Seat, Performance와 PerformanceSeat가 그
  예다. 좌석 한 자리를 파는 데 회차 전체가 잠기지 않도록, PerformanceSeat는 좌석 단위 동시 확정을
  낙관적 락으로 막는다.

**aggregate 사이는 식별자로 참조한다.** 다른 aggregate를 객체로 붙잡고 있으면 한 트랜잭션에서 둘을
같이 고치는 코드가 쉽게 써지기 때문이다.

| 경계 | 참조 | Repository |
| --- | --- | --- |
| 같은 Aggregate 내부 | Entity 연관관계 가능(`@ManyToOne(LAZY, optional=false)`) | Root만 Repository |
| 같은 BC, 다른 Aggregate | scalar ID 참조. read model에서는 JOIN 가능(`*QueryRepository`) | 각 Root별 Repository |
| 다른 BC | scalar ID 참조(강제). 상대 모듈이 공개한 query API를 쓴다 | 각 BC가 자기 Repository 소유 |
| cross-BC DB JOIN | reporting/integration read model처럼 명시적으로 허용된 경우만. 현재 애플리케이션 안에는 예외가 없다 | — |

Querydsl `Q`-type이 각 BC의 `<bc>.domain`에 생성되므로, 다른 BC의 `Q`-type을 import하는 순간
`ModularityTests`가 이미 그 위반을 잡는다 — cross-BC JOIN은 구조 테스트가 구조적으로 막는
경로다. 여러 모듈의 테이블을 raw SQL로 함께 채우는 유일한 코드는 초기 데이터 적재이고, 그것은
애플리케이션이 아니라 저장소 최상위 `seed/`의 독립 실행 프로그램이다([seed/README.md](../seed/README.md))
— 서비스 `bootJar`에 들어가지 않고 module 탐지 대상도 아니므로 이 검사망 안에 둘 대상 자체가 없다.

부모 쪽 `@OneToMany` 컬렉션은 자식 수가 적고 lifecycle이 완전히 묶일 때만 둔다 — `Venue`→`Seat`,
`Performance`→`PerformanceSeat`처럼 자식이 수천 개인 관계는 컬렉션으로 두지 않는다. soft delete를
쓰는 자식(`Member`→`MemberSocialAccount`)에는 `orphanRemoval`을 붙이지 않는다. 자식 조건으로
root를 찾는 조회는 root Repository가 가진다(`MemberRepository.findActiveBySocialAccount`).

**객체 참조를 scalar로 바꿀 때 주의**: Spring Data 파생 쿼리가 런타임에 깨진다.
`findAllByCategory_CodeOrderByName`처럼 메서드 이름이 연관관계 경로를 타면 컴파일은 통과하고
컨텍스트 로딩 시점에 `PropertyReferenceException`이 난다. 명시적 join JPQL(`@Query`)로 바꾼다.

가격은 세 시점의 스냅샷 체인이다: `PerformanceGrade.price`(운영자 구성, 판매 오픈 전에만 변경) →
`PerformanceSeat.unitPrice`(판매 좌석 생성 시 snapshot, 오픈 후 불변) → `OrderSeat.unitPrice`(주문
생성 시 snapshot, 생성 후 불변). booking은 이 조립에 show의 공개 계약
`PerformanceSaleCatalogApi`/`PerformanceVenueLayoutCatalogApi`만 쓰고 show entity를 JPA로 참조하지 않는다.

**Like 조합 규칙**: 찜의 데이터·불변식은 `like`(옛 `favorite`)가 소유한다. HTTP endpoint·use
case는 무엇이 필요한지에 따라 갈린다 — **다른 BC의 예/아니오(존재)만 있으면 되는 것**(찜하기·
찜 해제·찜 상태 조회)은 like가 소유하고, **다른 BC의 실제 표시 데이터**가 필요한 것("내 찜
목록"의 공연 제목·이미지·공연장 이름)은 그 데이터를 가진 show가 소유한다. `show.domain.show`는
like를 모른다 — `show.usecase`의 조회 use case가 like의 공개 조회 API(`LikeQueryApi`)를 주입받아
조합한다(직접 데이터 JOIN 아님). 반대로 like는 존재 확인을 하지 않는다 — 존재하지
않는 대상을 찜해도 막지 않는다. 회원 활성 확인은 예외다 — `member`는 leaf라 `like -> member`가
순환을 만들지 않고, JWT 인증만으로는 탈퇴 회원을 걸러낼 수 없어 like가 직접
`MemberLookupApi.requireActive`를 부른다. 이 규칙은 `com.ticket.DomainIsolationTest`(ArchUnit,
6개 BC 전체의 모든 `domain` 계층)가 강제한다. 왜 catalog 흡수 대신 이 형태가 됐는지는
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)을, 찜 모듈 개명과 대상 일반화는
[ADR 0008](adr/0008-like-target-generalization.md)을, use case 소유권을 존재/표시 기준으로
나눈 결정은 [ADR 0009](adr/0009-like-owns-write-and-status-usecases.md)를 본다.

## Module Structure

물리적으로 별도 Gradle 모듈이 아니라 `<module>` 아래의 패키지일 뿐이다.

**배치의 기준은 하나다 — 작은 모듈은 역할을 바로 보여주고, 큰 모듈은 업무를 먼저 보여준다.**
모든 모듈을 같은 모양으로 만들지 않는다. 폴더 깊이는 코드 규모와 책임 복잡도에 비례한다.

### 역할 이름

| 역할 폴더 | 담는 것 |
| --- | --- |
| `api` | 다른 module에 공개하는 계약(interface + record snapshot + enum). `@NamedInterface("api")` |
| `endpoint` | Controller, 요청 DTO, OpenAPI 문서 interface, HTTP 커서 문자열 |
| `usecase` | 요청 단위 use case와 그 조립 서비스, 트랜잭션 경계 |
| `event` | 커밋 이후 후속 처리 조율(`booking`에만 있다) |
| `query` | 자기 module의 읽기 모델(`*Row`/`*View`/`*Param`)과 커서·정렬 타입 |
| `port` | 조회가 아닌 출력 계약(발행·외부 provider) |
| `domain` | 엔티티와 값 객체, 상태 enum, 정책과 검증기, Aggregate Repository 계약 |
| `persistence` | 저장 adapter와 local DB 조회 Repository(`*QueryRepository`), Spring Data 인터페이스, Redis/Redisson 저장 구현 |
| `exception` | `<Module>ErrorCode`, 예외 클래스, `handler` |

**역할 폴더는 템플릿이 아니다.** 실제 파일과 책임이 있을 때만 만든다 — `payment`에는 지금
`domain`과 `persistence`뿐이고, `venue`에는 `api`·`persistence`·`domain`뿐이라 `usecase`도 `endpoint`도 없다.

**`Repository`와 `persistence`는 다른 것을 뜻한다.** `Repository`는 Aggregate 저장·복원 *계약*이라
`domain`이 소유하고(`member.domain.MemberRepository`, `booking.order.domain.OrderRepository`),
`persistence`는 JPA·Spring Data·Redis 같은 실제 저장 *기술*이다
(`member.persistence.MemberRepositoryAdapter`). 계약을 `persistence`로 옮기지 않는다.

**조회 구현은 `persistence`가 갖고 `query`는 읽기 모델만 갖는다.** 자기 module DB를 읽는 조회는
`persistence`의 `*QueryRepository`가 Querydsl/JPA까지 직접 갖고
(`show.persistence.ShowQueryRepository`), `query`에는 그 결과 타입과 검색 조건만 남는다. 조회마다
port interface와 adapter를 한 쌍씩 만들지 않는다 — 구현이 하나뿐인 1:1 위임은 기능을 이해하는 데
아무것도 보태지 않는다. **use case는 같은 module의 조회 Repository만 `persistence`에서 직접 부를
수 있고, 저장 adapter·Spring Data 인터페이스·Redis 구현 직접 호출은 그대로 막는다.** 나머지
방향도 규칙으로 막는다(`query`는 `usecase`/`event`/`endpoint`/`persistence`를 모르고, DB를 읽는
클래스는 다른 업무 module을 조합하지 않는다 — `ArchitectureRulesTest`).

`XXXPort`는 **밖을 부르는 출력 계약**이고 `XXXAdapter`는 그 구현이다(`booking.seat.port`의
이벤트 발행 계약처럼). 이 둘은 다른 package에 둔다 — 같은 package에 있으면 use case가 구현을
직접 부르는 것을 규칙으로 막을 수 없다. interface는 실제 교체 지점·외부 시스템 계약·domain
보호처럼 근거가 있을 때 둔다.

### 작은/중간 모듈 — 역할을 모듈 바로 아래에 둔다

`member`·`like`·`venue`·`payment`·`show`는 모듈 root 바로 아래에 역할 폴더를 둔다.

```text
member                 like                venue           payment        show
├─ api                 ├─ api              ├─ api          ├─ domain      ├─ api
├─ usecase             ├─ usecase          ├─ domain       └─ persistence ├─ usecase
├─ domain              ├─ domain           └─ persistence                 ├─ query
├─ password            ├─ persistence                                     ├─ domain
├─ persistence         ├─ endpoint                                        ├─ persistence
├─ endpoint            └─ exception                                       ├─ endpoint
└─ exception                                                              └─ exception
```

`member.password`는 계약(`PasswordHasher`)과 Spring Security 구현, bean 설정을 한 묶음으로 둔
작은 기능 폴더다. 해싱은 저장 기술이 아니라 보안 기술이라 `persistence`가 받지 않는다.

`venue`는 공개 계약(`venue.api`)을 `venue.persistence.VenueQueryRepository implements
VenueLookupApi, VenueSeatLookupApi`가 직접 구현해서 `usecase`가 없다. 위임만 하는 service를
사이에 두지 않는다 — 다른 module은 여전히 `venue.api`의 interface만 본다.

`show`의 정렬·커서·판매 상태 조건 helper 셋(`QuerydslShowSortResolver`,
`QuerydslShowCursorConditionBuilder`, `SaleDisplayStatusPredicates`)은 별도 class가 아니라
`ShowQueryRepository`의 private 메서드다 — 쓰는 곳이 그 한 class뿐이라 Spring 빈으로 둘 이유가
없다. 두 조회 Repository가 함께 쓰는 `QuerydslTupleColumns`만 `show.persistence`에
package-private으로 남는다. package-private으로 유지하려면 쓰는 쪽과 같은 package에 있어야 한다.

### booking — 업무(capability)를 먼저 보여준다

`booking`은 하나의 기능이 아니라 여러 관련 업무의 묶음이다. 그래서 모듈 바로 아래가 capability다.

```text
booking
├─ OrderStarted / OrderTerminated   ← 공개 이벤트(FQCN이 DB에 저장된 값이라 root 고정)
├─ event          커밋 이후 후속 처리 조율 (+ event.persistence)
├─ exception      module 전체 error code와 handler
├─ domain         여러 capability가 함께 쓰는 감사 기반 타입과 요청 값
├─ concurrency    분산락 계약 (+ concurrency.redis 구현)
├─ redis          Redis 키 만료 수신 배선
├─ websocket      STOMP 배선과 좌석 상태 발행 구현
│
├─ order          domain · usecase · query · persistence · endpoint
├─ hold           domain · persistence
├─ selection      domain · usecase · persistence · endpoint
├─ seat           domain · usecase · query · port · persistence · endpoint
├─ salespolicy    domain · usecase · persistence · endpoint
├─ ticket         domain · persistence
└─ admission      (flat — 파일 일곱이 서로만 부른다)
```

package tree만 보고 답할 수 있어야 한다 — 주문은 `booking.order`, 선점은 `booking.hold`, 좌석
선택은 `booking.selection`, 대기열 입장 검증은 `booking.admission`, 판매 정책은
`booking.salespolicy`, 판매 좌석과 잔여는 `booking.seat`다.

**`booking` 하위 capability는 Spring Modulith Application Module이 아니다.** `@ApplicationModule`도
`@NamedInterface`도 붙이지 않고, capability 사이에 module API를 만들지 않는다. 최상위 Application
Module은 여덟 개(`booking`/`show`/`member`/`like`/`venue`/`payment`/`security`/`shared`) 그대로다.

**여러 capability를 조율하는 코드는 capability에 억지로 넣지 않는다.** 판단 기준은 "어떤 상태를
저장하는가"가 아니라 **"어떤 workflow의 결과를 책임지는가"**다.

- `StartBookingUseCase`는 정책·입장·회원·좌석·선점·보상을 조율하지만 책임지는 결과가 주문이라
  (`OrderStarted`를 발행한다) `booking.order.usecase`다. `HoldController`도 경로 이름만 hold이고
  실제로는 이 use case를 부르므로 `booking.order.endpoint`다.
- `HoldCreationCoordinator`/`HoldReleaseCoordinator`는 이름에 Hold가 있지만 책임지는 결과가
  "주문 이벤트가 끝까지 처리됐는가"라 `booking.hold`가 아니라 `booking.event`다.
- `SeatSelectionCoordinator`는 선점 충돌 확인과 좌석 상태 발행까지 하지만 책임지는 결과가 선택
  상태라 `booking.selection.usecase`다.
- `OrderHoldHistoryRecorder`는 "주문이 선점 이력을 어떻게 남기는가"의 조립이고 호출자가 모두 주문
  트랜잭션 안이라 `booking.order.usecase`다.

### 깊이와 금지

- 일반적인 최대 구조는 **모듈 → capability → 역할**이다(`booking.order.persistence`).
  `booking.order.persistence.jpa.repository.adapter`처럼 깊게 만들지 않는다. 기술 응집도가 높고
  파일이 많은 `booking.concurrency.redis` 정도가 허용 범위다.
- **모듈 안에 `common`·`util`·`helper`·`support`·`misc` 패키지를 만들지 않는다.** 갈 곳이 애매하면
  그 타입의 소유 capability나 실제 역할을 먼저 정한다. 여러 업무가 함께 쓰는 기반도 그 역할이
  받는다 — 락 계약은 `booking.concurrency`, 감사 기반 타입과 요청 좌석 값은 `booking.domain`이다.
- **모듈 root = cross-module 공개 계약.** 구현 클래스, JPA entity, Repository는 root에 두지 않는다.
  `booking.OrderStarted`/`OrderTerminated`만 예외이며 그 이유는 `booking` package-info가 원본이다.
- 여러 업무를 호출하는 Controller는 쪼개지 않는다. 폴더를 맞추려고 HTTP 계약을 바꾸지 않는다.
- `exception`은 모듈 바로 아래 하나다. 배치 기준은 [아래 오류 처리](#오류-처리) 절과
  [ADR 0010](adr/0010-exceptions-do-not-own-http-status.md)이 원본이다.

### security와 shared

**`security`만 역할 대신 기능으로 나눈다.** 여기 있는 것은 업무가 아니라 인증 기술이라 "무엇에
관한 코드인가"가 더 나은 탐색 단위다. `auth`(가입·로그인·갱신·로그아웃·탈퇴 조립과 인증
Controller), `jwt`(JWT 생성·검증·서명키·설정), `oauth`(filter chain·handler·provider 통신·응답
해석·인증 코드·외부 unlink), `token`(토큰 발급·검증 계약과 결과, refresh token 저장, UUID 생성
기반), `http`(API 보안 설정·필터·SecurityContext·MVC 인증 주체·401/403·쿠키) 다섯이며, **각 폴더
안에 역할 폴더를 다시 만들지 않는다.** 클래스가 많다는 이유만으로 기능마다 façade를 더하지 않고,
하나의 Controller가 여러 기능 폴더를 호출하는 것도 허용한다.

`shared`는 공개 계약을 `api`/`web`/`exception` 세 named interface에 나눠 두고, 실행 배선·설정 구현은
`shared.infrastructure`에 둔다. 업무 모듈에서 갈 곳이 애매한 타입을 `shared`로 보내지 않는다.

### domain 아래 묶음

| 모듈 | `domain` 아래 묶음 |
| --- | --- |
| `booking` | capability마다 자기 `domain`을 갖는다(`order`/`hold`/`selection`/`seat`/`salespolicy`/`ticket`). 여러 capability가 함께 쓰는 기반 타입만 `booking.domain` 직속이다 |
| `show` | `show`(Show와 판매 표시 규칙) · `performance`(Performance와 허용된 PerformanceGrade 연관) · 나머지(Grade·Category·Genre·Performer와 저장 계약)는 `domain` 직속 |
| `member` | 단일 Member Aggregate 중심이라 `domain` 직속 |
| `venue` · `like` · `payment` | `domain` 직속 |

`domain` 묶음은 **Aggregate와 일대일이 아니다.** `booking.hold.domain`과
`booking.selection.domain`은 Redis 상태와 그 규칙의 묶음이고, hold 이력처럼 같은 묶음에 있는
별도 영속 모델도 자기 Aggregate 경계를 그대로 유지한다. 작은 독립 모델마다 폴더를 더 만들지
않는다 — 실제 Aggregate 경계는 `com.ticket.AggregateAssociationTest`가 강제한다.

같은 모듈의 여러 도메인 모델이 함께 쓰는 기반 타입과 값은 모듈의 `domain` 바로 아래 둔다. 예를
들어 `show.domain.ShowAuditedEntity`, `booking.domain.BookingAuditedEntity`,
`booking.domain.RequestedSeatIds`가 그렇다. 이를 이유로 모든 BC의 감사 기반 타입을 `shared`로
합치지 않는다.

### 포트 소유

**`usecase`에는 `*UseCase`만 두지 않는다.** use case가 조립에 쓰는 서비스·헬퍼도 같은 package에
둔다 — 주문 생성과 그 조립 helper가 한 목록에서 읽혀야 트랜잭션 원자성이 어디서 보장되는지 보인다.
읽기 모델은 `query`, 그 조회 구현은 `persistence`의 `*QueryRepository`, 발행 같은 출력 계약은
`port`다.

포트 소유 기준은 **그 기능을 필요로 하고 의미를 정의하는 쪽**이 소유한다.

| 계약의 성격 | 소유 위치 |
| --- | --- |
| aggregate 저장·복원과 업무 명령에 필요한 조회 | `domain` |
| 화면 조회·검색·집계 결과의 읽기 모델 | `query`(그 조회 구현은 `persistence`) |
| 분산락 | `booking.concurrency` |
| 발행·외부 provider·client | `port`(없으면 그 기능을 정의하는 package) |
| HTTP 입력·출력 계약 | `endpoint` |
| JPA, Querydsl, Redis, Redisson, JWT 구현 | `persistence`(또는 그 기술을 소유한 기능 package) |

기능은 클래스 이름 접두사로 드러낸다(`ShowRepository`, `PerformanceGrade`, `PendingOrderCreator` 등).
`model`/`repository`/`store`/`command`는 하위 패키지가 아니라 **명명 관용**이다 —
Aggregate Repository 계약(옛 `repository`), 저장 기술 중립 상태 계약(옛 `store`), 상태 변경
use case(옛 `command`)가 어떤 성격인지는 클래스 이름과 위 "계약의 성격" 표로 판단한다. 조회는
아래 "Repository와 조회 Repository" 절이 별도로 다룬다.

자기 module DB 조회는 `persistence`의 `*QueryRepository`(`ShowQueryRepository`,
`PerformanceSeatQueryRepository`), Aggregate Repository 어댑터는 `*RepositoryAdapter`, 안에서 쓰는
Spring Data 인터페이스는 `SpringData*JpaRepository`로 구분한다. `View`는 조회 경계의 화면/응답용
projection, `Snapshot`은 특정 시점의 읽기 결과(모듈 공개 API에서는 cross-module 스냅샷), `Row`는
저장소 조회 한 행, `Output`은 use case 반환값, `Param`/`Criteria`/`Event`/`Request`는 각각 조회
조건 구성값/검색 조건/발생한 사실/외부 입력이다. **조회 전용 `...View` 타입에 비즈니스 로직을
두지 않는다** — 판정은 별도 validator/policy가 맡는다.

배경은 [ADR 0016](adr/0016-capability-first-layout-inside-modules.md)이다.

## Repository와 조회 Repository

조회 기능이라고 해서 모두 같은 길을 쓰지 않는다. **Aggregate를 저장·복원하기 위한 Domain
Repository**와 **화면/검색/목록 조회를 위한 조회 Repository**를 구분한다. 둘 다 `Repository`지만
계약은 `domain`, 조회 구현은 `persistence`에 있다.

판단 기준은 쿼리의 복잡도가 아니다. **바꾸기 위해 가져오면 Domain Repository를 쓰고, 보여주기
위해 가져오면 조회 Repository를 쓴다.**

### Domain Repository

Aggregate를 저장·복원하는 인터페이스다. 조회한 Aggregate로 업무 규칙을 수행한 뒤 다시 저장하는
흐름(조회 → 행동 → 저장)에 쓴다.

```java
Show show = showRepository.findById(showId)
        .orElseThrow(ShowNotFoundException::new);
show.changeTitle(newTitle);
showRepository.save(show);
```

`domain`에 두고 이름은 `*Repository`를 쓴다(`ShowRepository`, `OrderRepository`,
`MemberRepository`). Aggregate·Entity를 반환하며, `existsByEmail(Email)`처럼 업무 규칙 판단에
필요한 조회도 포함할 수 있다. 내부적으로 여러 테이블을 조인해야 해도(예: `Order`가
`ORDER_SEAT`까지 복원) Aggregate 복원이 목적이면 여전히 Domain Repository다 — **쿼리가
복잡한지는 판단 기준이 아니다.**

### 조회 Repository

화면·API·검색·목록·집계에 필요한 데이터를 조회하는 클래스다. Aggregate를 복원하는 게
목적이 아니라 **use case가 필요로 하는 조회 결과를 만드는 것**이 목적이다.

```java
ShowDetailView detail = showQueryRepository.findShowDetail(showId)
        .orElseThrow(() -> new NotFoundException(...));
```

module의 `persistence` package에 두고 이름은 `*QueryRepository`를 쓴다(`ShowQueryRepository`,
`PerformanceQueryRepository`, `PerformanceSeatQueryRepository`). **한 module의 관련 조회는 한
class로 모은다** — 조회 하나에 class 하나를 만들지 않는다.
**local DB 조회에는 1:1 port/adapter를 두지 않는다** — 조회 Repository가 `@Repository` + 생성자
주입으로 Querydsl/JPA를 직접 쓰는 구체 class다. 조회 대상이 자기 module DB가 아니거나(외부 API,
Redis, JWT 같은 외부 시스템), 실제로 교체 지점이 있거나, domain을 보호해야 할 때만 interface를
둔다. use case는 `persistence`에서 이 조회 Repository만 직접 부를 수 있고, 저장 adapter와
Spring Data 인터페이스·Redis 구현은 그대로 막혀 있다.

Aggregate를 여러 개 복원해 Java에서 조합하기보다, Querydsl로 필요한 read model을 직접 만든다 —
반환 타입은 Aggregate가 아니라 `View`/`Row`(위 "패키지 구조" 절의 명명 규칙)이고, 그 읽기 모델
타입은 `query` package가 소유한다.

단순한 local 조회는 그 module의 **공개 API interface를 직접 구현해도 된다**
(`venue.persistence.VenueQueryRepository implements VenueLookupApi, VenueSeatLookupApi`). 위임만
하는 service를 사이에 두지 않는다. 이때 읽기 전용 트랜잭션 같은 경계는 그 조회 Repository가
소유한다.

**Aggregate 경계와 API 응답 경계는 같을 필요가 없다.** 공연 상세는 Show/Performance/Grade/
Genre/Performer뿐 아니라 다른 BC의 표시값(venue 이름, 찜 개수)까지 한 응답에 담는다 — 그 조합은
조회 Repository 자신이 아니라 그것을 부르는 use case가 한다(`GetShowDetailUseCase`가
`ShowQueryRepository`로 show 자기 데이터를 얻고, `VenueLookupApi`/`LikeQueryApi`로 다른 BC의
표시값을 더한다). **DB를 읽는 클래스가 다른 module의 공개 계약을 직접 호출해 결과를 조합하지
않는다** — 조회 Repository의 역할은 자기 module DB를 읽는 것까지이고, 이 규칙은
`ArchitectureRulesTest`가 강제한다.

**같은 id로 두 계약이 동시에 존재해도 된다.**

```java
// Domain Repository — Aggregate 복원 → 업무 행동(`show.domain`)
Optional<Show> ShowRepository.findById(Long showId);

// 조회 Repository — 화면 표시값 → API 응답
Optional<ShowDetailView> ShowQueryRepository.findShowDetail(Long showId);
```

둘 다 같은 `SHOWS` 테이블을 볼 수 있지만 목적이 다르다. Read model(`View`/`Row`)은 Aggregate가
아니고, 상태 변경을 하지 않으며, 핵심 업무 불변식을 갖지 않는다 — "구매 가능한가" 같은 판정은
그 값을 정하는 domain(`PerformanceSalesPolicy` 등)이 하고, Read model은 이미 정해진 값을
전달하는 역할에 머문다(`docs/architecture.md`의 "조회 전용 `...View` 타입에 비즈니스 로직을
두지 않는다" 규칙과 같다).

## 계층별 검증 책임

판단 기준 한 문장 — **"이 검증이 사라지면 무엇이 먼저 깨지는가."** HTTP 응답 품질만 나빠지면
`endpoint`, 다른 adapter에서 호출해도 흐름이 깨지면 `application`, 어떤 호출 경로에서도 업무가 틀리면
`domain`, 기술 경계에서만 성립하면 `infrastructure`다.

| 계층 | 소유하는 검증 | 실패 표현 |
| --- | --- | --- |
| `endpoint` | JSON·HTTP 요청 형식, 필수 body field, blank/null, ID 양수 여부, path/query/header 형식 | Bean Validation → `InvalidRequestException`(400/`E400`) |
| `application` | adapter 공통 `UseCase.Input` 계약, 여러 입력 조합, 데이터 존재 여부, 요청 권한, 중복·멱등성, 다른 모듈 공개 API를 엮는 실행 선행조건 | 공통 예외 또는 소유 모듈 예외 |
| `domain` | 업무 불변식, 값 객체 유효성, 상태 전이, 예매 가능 시간, 좌석 소유권과 선점 한도 | 소유 모듈 `exception`의 업무 예외 |
| `infrastructure` | Redis·JWT·외부 API payload decode, DB constraint 번역 | 기술 예외를 상위 계층이 이해할 실패로 번역 |

**Bean Validation은 `endpoint`만 쓴다.** 파라미터 제약은 `*ControllerDocs` 인터페이스에만 선언한다 —
상위 타입과 구현체 양쪽에 선언하면 Jakarta Bean Validation이 `ConstraintDeclarationException`
(HV000151)을 던져 method validation 전체가 500으로 무너진다. 왜 그런지와 `@Validated`를
Controller에 붙이지 않는 이유는 `ControllerParameterConstraintTest`의 JavaDoc이 원본이다.

`application`은 필수 component를 record compact constructor 한곳에서 판정한다. 승인된 문구
형태는 `InvalidRequestMessageContractTest`가 원본이다. `execute(null)`은 사용자 입력 오류가
아니라 호출부 프로그래머 오류이므로 `NullPointerException`으로 드러낸다. **자기 모듈
Repository는 "없다"는 사실만 알려주고 오류는 유스케이스가 고른다** — 도메인 Repository는
`Optional`/`boolean`만 노출하고 예외를 던지는 `getXxx`/`requireXxx` 편의 메서드를 두지 않는다.
다른 모듈의 공개 API도 같은 원칙을 따른다 — 다른 모듈의 내부 예외 타입을 직접 잡지 않는다.

중복 허용/금지 기준:

- **허용**: 두 계층의 목적이 다를 때(예: `@NotEmpty`는 친절한 400, 도메인 `validateEmpty`는 업무
  불변식). 형태가 같아도 지우지 않는다.
- **금지**: 같은 목적의 같은 규칙을 같은 계층에서 두 번 실행. 업무 정책 값을 API나 application으로
  복사(최대 선점 좌석 수는 booking local aggregate `PerformanceSalesPolicy` 소유). 같은 값 변환
  규칙을 여러 곳에 두는 것.

## 오류 처리

**업무 예외는 HTTP를 모른다.** `TicketException`은 `errorCode`·`message`·`data`만 옮기는
그릇이고, `HttpStatus`도 Spring Web 타입 의존도 갖지 않는다. 어떤 상태 코드로 응답할지는
그 오류를 처리하는 웹 계층(module handler, 공통 오류는 `GlobalExceptionHandler`)이 안다 —
왜 이렇게 나눴는지는 [ADR 0010](adr/0010-exceptions-do-not-own-http-status.md)이 원본이다.

```text
com/ticket/<module>/exception/
  <Module>ErrorCode.java          enum implements com.ticket.shared.exception.ErrorCode
  <Module>Exception.java          abstract sealed extends com.ticket.shared.exception.TicketException
                                  (errorCode·message·data만) -- permits로 하위 타입을 닫는다
  <구체 예외>.java                 final. errorCode·메시지·data를 생성자에서 확정(상태는 없다)
  handler/<Module>ExceptionHandler.java   @Order(HIGHEST_PRECEDENCE), base 타입 하나만 잡고
                                          구체 타입 -> HTTP 상태를 exhaustive switch로 정한다
```

**module base 예외는 sealed다.** `permits` 목록이 곧 handler switch가 덮어야 할 집합이라, 새 예외를
추가하면서 HTTP 매핑을 빠뜨리면 runtime이 아니라 컴파일이 실패한다. 그래서 handler switch에
`default` 분기를 두지 않는다. 하위 타입은 전부 같은 package에 있어야 한다(JPMS named module이
아니므로). `TicketException` 자체는 sealed로 만들지 않는다 — 직접 하위 타입이 다섯 package에 흩어져
있고, 억지로 맞추면 module별 오류 소유권이 깨진다. 배경은
[ADR 0015](adr/0015-null-contracts-are-explicit-and-enforced.md)다.

**모듈의 예외는 `<module>.exception` 하나에 둔다.** base 타입, `<Module>ErrorCode`, 구체 예외,
`handler`가 모두 여기에 속한다. 공통 오류 계약은 `shared.exception`, 공통 HTTP 응답 봉투는
`shared.web`이 소유한다. 예외를 계층이나 업무별로 다시 쪼개지 않는다.

`member.exception`은 E1000/E1001 응답을 전역 security와 공유해야 하므로
`member :: exception` named interface로 최소 공개한다. handler 하위 구현은 공개 계약이 아니다.

오류 계약 소유 기준(모듈별 오류 vs `com.ticket.shared.exception`의 공통 오류), 응답 봉투가 `web`에 있는
이유, `ProblemDetail`을 채택하지 않은 이유는 [ADR 0002](adr/0002-module-owned-error-contracts.md)가
원본이다. module handler가 다른 module의 오류까지 삼키지 않는지는 `ExceptionHandlerScopeTest`가,
E-code(외부 계약, `gatling-test`가 하드코딩) 전역 유일성은 `ErrorCodeUniquenessTest`가 강제한다.

## 주요 흐름

코드만 봐서는 알기 어려운 정책·설계 결정만 다룬다. 엔드포인트 목록은 Swagger(`/api/api-docs`)가,
예매 실행 순서는 [core-booking-lifecycle.md](core-booking-lifecycle.md)가 원본이다.

**인증**: 인증·인가와 그 조립은 전부 `security`가 소유한다 — 가입·로그인·갱신·로그아웃·탈퇴
절차(`security.auth`), JWT 발급·검증(`security.jwt`), provider 응답 해석과 세션이 필요한
`@Order(1)` OAuth2 filter chain(`security.oauth`), 토큰 계약과 refresh token 저장
(`security.token`), stateless `@Order(2)` API filter chain·URL별 접근 정책·Authorization header
해석·SecurityContext·MVC argument resolver(`security.http`)가 그렇다.

**회원 테이블과 인증 데이터의 소유권은 member에 있다.** security는 `MemberAccountApi`
공개 계약으로만 계정을 만진다 — 등록·자격 증명 확인·활성 확인·소셜 계정 해석·탈퇴 다섯 가지이며,
**비밀번호 해시는 member 밖으로 나가지 않는다.** 해싱과 일치 확인을 member가 직접 수행하므로
`member -> security` 의존이 생기지 않는다. 의존 방향은 `security -> member -> shared`다.

다른 모듈의 controller는 `member.api.AuthenticatedMember`만 parameter로 받고 JWT나 `member` 내부의
`Member`를 보지 않는다. `booking`은 WebSocket 인증 하나 때문에 `security`를 참조한다 — STOMP
CONNECT는 HTTP filter chain을 타지 않아 좌석 상태 구독 인터셉터가
`security.api.AccessTokenAuthenticator`로 토큰을 직접 검증한다.

`GET /api/v1/members`는 member가, `DELETE /api/v1/members`는 security가 갖는다. 탈퇴는 DB
처리로 끝나지 않고 커밋 뒤 외부 provider 연결 해제와 SecurityContext 정리가 이어지는 인증
조립이기 때문이다. 같은 URL을 두 모듈이 메서드로 나눠 갖는다.

OAuth provider raw attribute는 `security.oauth.OAuth2UserInfoMapper`가 `member.api.SocialIdentity`로
정규화한 뒤 member의 공개 계약에 넘긴다. 기존 계정에 같은 이메일로 자동 연결하는 것은 provider가
이메일 검증을 명시한 경우에만 허용하고, 검증되지 않은 이메일은 provider ID 기반 대체 주소로
격리한다.

**좌석 조회는 performanceId 기준이다** — 같은 Show라도 회차마다 편성·가격이 다를 수 있어 `showId`
기준 조회 API는 만들지 않는다. `booking.seat.endpoint.PerformanceSeatQueryController`가 공개하는 3개 API
(정적 seat-map / 동적 상태 / 등급별 잔여석)는 회차당 고정된 query 수를 유지한다 — 무엇을 어떻게
고정하는지는 [testing.md의 performance 기준 API](testing.md#performance-기준-api와-가격-snapshot-회귀)가
원본이다. **정적 seat-map에 있는데 상태 응답에 없는 좌석을 클라이언트가 AVAILABLE로 추정하게 하지
않는다** — 데이터 불일치는 예외를 던지지 않고 조용히 그 좌석만 제외한다.
`show.endpoint.ShowVenueLayoutController`(showId 기반 물리 Venue 배치 전용, ADR 0006으로 booking에서 옮겨옴)는
별개의 show 기준 API다 — 새 기능은 여기 추가하지 않고 performance 기준 API 쪽에 추가한다.

**대기열**은 형제 저장소 `ticket-queue`가 담당한다. Core는 Queue Controller도 token 저장소도
갖지 않는다. 대기열 진입 정책(`PerformanceSalesPolicy`의 `BookingEntryPolicy`)은 Booking BC가
소유하며, 인증 없이 조회하는 `GET /api/v1/booking/performances/{performanceId}/booking-mode`가
접수 상태와 예매 방식(DIRECT/QUEUE/UNAVAILABLE)을 계산해 반환한다 — 안내용이라 실제 좌석
선택·주문 API는 실행 시점에 정책을 다시 검사한다. Queue Server hot path는 Core DB를 조회하지
않고 `join` 응답의 `shardId`/`localSeq`를 `/state`의 `serving[shardId]`와 비교해 판단하며,
Core는 Queue가 발급한 admission token의 서명·claim만 검증한다(**주문 생성 후 Queue Server에
session 완료 요청을 보내지 않는다**). 두 저장소가 공유하는 secret/issuer/audience 설정 값과
운영 절차는 [operations.md의 Admission token 검증](operations.md#admission-token-검증)을 본다.

## 저장소와 동시성

주 영속 저장소는 RDB다. 업무 상태 JPA entity는 각 모듈 `domain`, Spring Data/JPQL/Querydsl/
`EntityManager`/Repository adapter는 `infrastructure`에 둔다. Flyway는 module 소유권을 따른다
(폴더 구조와 절차는 [operations.md](operations.md#db-마이그레이션)가 원본).

Redis는 짧은 수명 상태와 동시성 제어, 토큰 저장에 쓴다. 대기열 상태는 `ticket-queue`가 별도
Redis에서 관리하고, Core Redis는 seat selection·seat hold(`booking`), refresh token·OAuth2
one-time auth code(`member`)만 담당한다. Redis 구현체는 소유 모듈의 `infrastructure`에 둔다.
key 조립·TTL·전환 절차 같은 Redis 작업 규칙은 [operations.md](operations.md#분산락과-redis-작업-규칙)가
원본이다.

분산락은 `com.ticket.booking.concurrency.LockManager` 같은 명시적 포트 호출로 처리한다.
어노테이션과 SpEL로 감추지 않는다. 포트·구현 클래스 목록은
[core-booking-lifecycle.md의 주요 코드](core-booking-lifecycle.md#주요-코드)가, 락 순서·임계
구역 같은 작업 규칙은 [operations.md](operations.md#분산락과-redis-작업-규칙)가 원본이다.

`payment`는 entity-only 단계다: controller, PG 연동, 결제 승인/실패/취소 API는 없다(ADR 0005
§3~4). 배경 전체는 [ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)를
본다.

## Enforcement

| 규칙 | 강제하는 테스트 |
| --- | --- |
| 모듈 경계·의존 DAG 위반 | `com.ticket.ModularityTests` |
| 모듈이 STANDALONE으로 부트스트랩되는지 | `<Module>ModuleTests` |
| 같은 module 안 aggregate를 객체 연관관계로 묶었는지 | `com.ticket.AggregateAssociationTest` |
| `<bc>`의 어느 `domain` 계층이든 다른 BC를 참조하는지 | `com.ticket.DomainIsolationTest` |
| `shared`에 bean을 등록했는지 | `com.ticket.shared.SharedModulePurityTest` |
| 파라미터 제약 선언 위치 | `ControllerParameterConstraintTest` |
| 필수 입력 오류 문구 | `com.ticket.shared.exception.InvalidRequestMessageContractTest` |
| E-code 전역 유일성 / handler 스코프 | `ErrorCodeUniquenessTest` / `ExceptionHandlerScopeTest` |
| 계층 방향, cross-module 구현 참조, `api` 공개면 오염 | `com.ticket.ArchitectureRulesTest` |
| 읽기 모델 package `query`가 조립·HTTP·저장 구현을 거꾸로 참조하는지 | `com.ticket.ArchitectureRulesTest` |
| use case가 저장 adapter를 직접 부르는지 / `persistence`에 조회 Repository만 열려 있는지 | `com.ticket.ArchitectureRulesTest` |
| DB를 읽는 클래스(`JPAQueryFactory` 보유)가 다른 업무 module을 조합하는지 | `com.ticket.ArchitectureRulesTest` |
| `endpoint`가 use case를 건너뛰고 Repository·조회 Repository를 직접 부르는지 | `com.ticket.ArchitectureRulesTest` |
| 공개된 `@NamedInterface` 목록이 늘거나 줄었는지 | `com.ticket.ArchitectureRulesTest` |
| production package의 `@NullMarked` 선언 누락 | `com.ticket.ArchitectureRulesTest` |
| null 계약 위반 | NullAway (`./gradlew compileJava`, 테스트가 아니라 컴파일에서 막힌다) |
| booking 락 계약이 Redis·HTTP를 모르는지 | `com.ticket.booking.BookingLayerDependencyTest` |
| module 구조 문서 생성 | `com.ticket.DocumentationTests` |

구조 검사만 빠르게 돌리는 조합은 `.github/workflows/architecture.yml`이 원본이다 — DB도 Redis도
쓰지 않아 전체 CI보다 먼저 끝난다. 무엇을 검증하는지 자세한 목록은
[testing.md의 구조 테스트](testing.md#구조-테스트), 실행 명령은 `/verify`가 원본이다. **규칙 본문은 테스트 코드가 원본이고 여기 옮겨 적지 않는다.** 규칙을
바꿔야 한다고 판단되면 테스트를 고쳐 통과시키지 말고, 규칙이 틀렸다는 사실을 먼저 밝힌다.

## 생성 문서

**현재 module 구조를 사람이 읽는 자료는 생성물이다.** `com.ticket.DocumentationTests`가 Spring
Modulith `Documenter`로 만든다.

```bash
./gradlew test --tests "com.ticket.DocumentationTests"
```

결과는 `build/spring-modulith-docs/`에 나온다.

| 파일 | 무엇인가 |
| --- | --- |
| `components.puml` | 전체 module dependency diagram(PlantUML) |
| `module-<module>.puml` | module 하나의 diagram |
| `module-<module>.adoc` | module canvas — 공개 API, 참조하는 bean, 발행·수신 이벤트 |
| `all-docs.adoc` | 위를 묶은 종합 문서 |

`build/`는 `.gitignore` 대상이라 commit되지 않는다. CI(`.github/workflows/ci.yml`)가
`spring-modulith-docs` artifact로 게시하므로 PR에서 내려받아 본다.

**생성된 diagram·목록을 이 문서(또는 다른 source 문서)에 다시 복사하지 않는다.** 복사본은
코드가 바뀌는 순간 틀리고, 틀린 쪽이 먼저 읽힌다. source 문서는 책임·허용 원칙·이유를 적고,
"지금 실제로 어떤가"는 생성물과 executable test를 가리킨다.

## Source of Truth

| 물음 | 원본 |
| --- | --- |
| 현재 실제 실행 상태 | code / `application*.yml` |
| 현재 module graph, 강제되는 규칙 | executable architecture test(위 Enforcement 표) |
| 현재 module 구조를 눈으로 읽기 | `DocumentationTests` 생성물 `build/spring-modulith-docs`(위 [생성 문서](#생성-문서)) |
| 원하는 architecture 원칙 | 이 문서 |
| 왜 그렇게 결정했는가 | `docs/adr/` |
| 예매·hold 실행 lifecycle | `docs/core-booking-lifecycle.md` |
| test 작성 관례 | `docs/testing.md` |
| 무엇을 돌리고 어떻게 보고할지 | `/verify` 스킬 |
| 로컬 초기 데이터 적재 | [seed/README.md](../seed/README.md) |
| 운영·Flyway·배포 | `docs/operations.md` |
| 미결 기술 부채·제품 결정 | `docs/technical-debt.md` |
| agent workflow | `AGENTS.md` + `.agents/skills/` |

같은 사실을 여러 문서에 반복해 적지 않는다 — 이 표가 가리키는 문서 하나에만 적고 나머지는
링크한다.

## 아키텍처 리뷰 질문

- 이 코드의 책임이 web, application, domain, infrastructure 중 어디에 속하는가
- 같은 검증이 두 계층에서 같은 목적으로 중복 실행되지 않는가
- 다른 모듈의 하위 패키지, Repository, JPA entity를 직접 참조하지 않는가
- 모듈을 넘는 JPA 연관관계나 DB FK가 새로 생기지 않았는가
- 새 공개 계약이 JPA entity, Redis/JWT/Spring Web 타입을 노출하지 않는가
- 새 클래스가 맞는 계층에 있는가 — 클래스 이름이 이미 업무를 말하는데 폴더로 또 나누지 않았는가
- `application`/`infrastructure`/`endpoint` 아래에 업무별 폴더를 새로 만들지 않았는가
- `domain` 아래 묶음이 실제 도메인 모델의 묶음인가, 폴더를 맞추려고 만든 것인가
- 갈 곳을 못 정한 코드를 `common`/`support` 같은 이름에 모으지 않았는가
- DB 상태와 Redis 상태를 합치는 규칙의 소유자가 한 곳인가
- 새 추상화가 실제 경계를 보호하는가, 사용하지 않는 계층을 늘리기만 하는가
