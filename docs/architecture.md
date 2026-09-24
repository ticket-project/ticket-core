# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 구현, 합의된 규칙, 미적용 결정과 문서 오류 가능성을 구분해 확인한다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)·
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)·
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)·
[ADR 0012](adr/0012-separate-global-http-security-from-member.md)을 함께 본다. 실행과 검증은
[testing.md](testing.md)를 본다.

## 원칙

- Ticket Core는 **단일 Gradle Spring Boot 프로젝트**다. 모듈 경계는 Gradle subproject가 아니라
  **Spring Modulith의 Application Module**이 강제한다. `com.ticket`의 직접 하위 패키지가 닫힌
  모듈이고, `com.ticket.ModularityTests`가 경계 위반을 잡는다.
- **Business Application Module은 Bounded Context 또는 독립적으로 캡슐화할 가치가 있는 supporting
  business capability와 정렬한다**([ADR 0006](adr/0006-bounded-context-module-boundaries.md)).
  `shared`와 `security`는 BC가 아닌 기술 모듈이다. 공유 계약은 `shared.api`·`shared.web`·
  `shared.exception`·`shared.jpa`, 공통 실행 배선은 `shared.config`, 인증·인가와 그 조립은
  `security`에 둔다.
- **다른 모듈이 쓰는 공개 계약은 `<module>.api`에 두고 `@NamedInterface("api")`로 선언한다**
  ([ADR 0014](adr/0014-module-public-contracts-live-in-api-packages.md)). 작은 interface와 불변
  `record` snapshot, enum만 두고 구현은 `endpoint`/`usecase`/`domain`/`persistence`/
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
| Show | Show, ShowGenre, Category, Genre, Performer, Performance(회차 일정만), Grade, PerformanceGrade | 옛 `catalog`. 가격 원본은 `PerformanceGrade.price`. Show의 판매 필드(`displaySaleType`/`displaySaleWindow`)는 화면 표시 전용이고 실제 판단은 Booking의 `PerformanceSalesPolicy`가 한다(ADR 0007) |
| Venue | Venue, Seat, Region | 물리 시설. show에서 분리됨 |
| Booking | Selection, Hold, Order, OrderSeat, Ticket, PerformanceSalesPolicy | 좌석 선점·주문과 발권 모델을 소유한다. 결제 승인·발권 실행 경로는 아직 없다. admission token 검증도 소유 |
| Payment | Payment | 결제 시도. entity-only 단계 |
| Like | Like | 찜 데이터·불변식. 대상 종류는 `LikeType`으로 값화(지금은 SHOW뿐). 찜 생성·해제·상태 조회 endpoint는 like가 소유하고, 공연 표시값을 조합하는 "내 찜 목록"만 show가 소유한다(ADR 0009) |
| Member | Member, MemberSocialAccount | 회원 테이블과 인증 데이터(비밀번호 해시·이메일·역할·탈퇴 상태), 회원가입. 로그인·갱신·로그아웃·탈퇴 조립과 JWT·OAuth2 provider 처리는 `security`가 소유한다 |

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
  `shared :: web`, `shared :: exception`, `shared :: jpa` 넷을 필요한 만큼 명시해 참조한다 — `shared :: *`
  와일드카드는 쓰지 않는다.
- `security -> member`는 Authorization header의 access token을 member의 공개 계약으로 검증하고
  `AuthenticatedMember`를 SecurityContext에 넣기 위한 단방향 의존이다. security는 전역 API URL
  접근 정책·401/403 변환·MVC argument resolver를 소유하고, member는 security를 참조하지 않는다.
- `show -> venue`는 표시값 조립, `show -> like`는
  공연 상세의 찜 개수와 "내 찜 목록" 조회 위임 때문이다.
- 요청 회원의 활성 여부는 security가 member의 공개 계약으로 인증 시 확인한다. like의 찜 연산과
  show의 찜 목록은 같은 확인을 반복하지 않는다.
- `booking -> show`는 있지만 `booking -> venue`는 없다. booking이 쓰는
  `PerformanceSaleCatalogApi`/`PerformanceVenueLayoutCatalogApi`를 show가 façade로 유지하기
  때문이다.
- `payment`는 entity-only 단계라 업무 모듈 의존이 없다. 다만 감사 컬럼 때문에 `shared :: jpa`
  하나를 참조한다(ADR 0018) — controller가 없어 `shared :: web`이, 자기 오류 타입을 던지지 않아
  `shared :: exception`이 필요 없다.
- 순환은 없다. 새 edge가 필요해 보이면 먼저 반대 방향으로 풀 수 있는지 본다.

## Aggregates

| Aggregate root | 함께 사는 것 | BC |
| --- | --- | --- |
| Venue | — | Venue |
| Seat | — | Venue |
| Show | ShowGenre(Show↔Genre 연결 entity) | Show |
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
  예다. 좌석 한 자리를 파는 데 회차 전체가 잠기지 않도록 PerformanceSeat는 독립 Aggregate다.
  `@Version`과 `reserve()`는 있으나 현재 주문 생성 경로는 이를 호출하지 않는다. 현재 점유 보장은
  Redis hold와 좌석 락·판매 상태 확인 범위에 한정된다([예매 수명주기](core-booking-lifecycle.md#주문-생성예매-시작)).

**aggregate 사이는 식별자로 참조한다.** 다른 aggregate를 객체로 붙잡고 있으면 한 트랜잭션에서 둘을
같이 고치는 코드가 쉽게 써지기 때문이다.

| 경계 | 참조 | Repository |
| --- | --- | --- |
| 같은 Aggregate 내부 | Entity 연관관계 가능(`@ManyToOne(LAZY, optional=false)`) | Root만 Repository |
| 같은 BC, 다른 Aggregate | scalar ID 참조. read model에서는 JOIN 가능 | 각 Root별 Repository |
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

가격은 세 시점의 스냅샷 체인이다: `PerformanceGrade.price`(운영자 구성, 판매 오픈 전 변경이라는 정책은 아직 코드에서 강제되지 않음: [TD-15](https://github.com/ticket-project/ticket-core/issues/236)) →
`PerformanceSeat.unitPrice`(판매 좌석 생성 시 snapshot, 오픈 후 불변) → `OrderSeat.unitPrice`(주문
생성 시 snapshot, 생성 후 불변). booking은 이 조립에 show의 공개 계약
`PerformanceSaleCatalogApi`/`PerformanceVenueLayoutCatalogApi`만 쓰고 show entity를 JPA로 참조하지 않는다.

**Like 조합 규칙**: 찜의 데이터·불변식은 `like`(옛 `favorite`)가 소유한다. HTTP endpoint·use
case는 무엇이 필요한지에 따라 갈린다 — **다른 BC의 예/아니오(존재)만 있으면 되는 것**(찜하기·
찜 해제·찜 상태 조회)은 like가 소유하고, **다른 BC의 실제 표시 데이터**가 필요한 것("내 찜
목록"의 공연 제목·이미지·공연장 이름)은 그 데이터를 가진 show가 소유한다. `show.domain.show`는
like를 모른다 — `show.usecase`의 조회 use case가 like의 공개 조회 API(`LikeQueryApi`)를 주입받아
조합한다(직접 데이터 JOIN 아님). 반대로 like는 존재 확인을 하지 않는다 — 존재하지
않는 대상을 찜해도 막지 않는다. 요청 회원의 활성 상태는 security가 공통 인증에서 확인한다.
이 규칙은 `com.ticket.DomainIsolationTest`(ArchUnit,
6개 BC 전체의 모든 `domain` 계층)가 강제한다. 왜 catalog 흡수 대신 이 형태가 됐는지는
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)을, 찜 모듈 개명과 대상 일반화는
[ADR 0008](adr/0008-like-target-generalization.md)을, use case 소유권을 존재/표시 기준으로
나눈 결정은 [ADR 0009](adr/0009-like-owns-write-and-status-usecases.md)를 본다.

## Module Structure

물리적으로 별도 Gradle 모듈이 아니라 `<module>` 아래 패키지다. 작은/중간 업무 모듈(`member`, `like`, `venue`, `payment`, `show`)은 `<module>.<role>`, 큰 `booking`은 `<module>.<capability>.<role>`로 둔다([ADR 0016](adr/0016-capability-first-layout-inside-modules.md)). `booking`의 capability는 별도 Application Module이 아니다. `security`는 `auth`/`jwt`/`oauth`/`token`/`http` 등 기능별로, `shared`는 공개 계약 `api`/`web`/`exception`/`jpa`와 실행 배선 `config`로 나눈다. 쓰지 않는 역할 폴더를 미리 만들지 않는다.

| 역할 | 책임 |
| --- | --- |
| `api` | 다른 모듈에 공개하는 계약과 snapshot. 구현 빈은 두지 않는다 |
| `endpoint` | HTTP Controller, 입력·응답 계약, API 문서화 |
| `usecase` | 요청 조립, 조회 파라미터·최종 응답, 트랜잭션 경계 |
| `domain` | 상태·업무 규칙·Aggregate 저장 계약 |
| `persistence` | JPA/Querydsl·Redis 저장과 자기 모듈 DB 조회 구현 |
| `port` | 발행·외부 provider 등 출력 계약 |
| `event` | booking의 커밋 후 후속 처리 조율 |
| `exception` | 모듈 오류 코드·예외·처리기 |

Repository는 Aggregate 저장·복원 계약이므로 `domain`이 소유하고 기술 구현은 `persistence`에 둔다. 자기 모듈 화면 조회는 `persistence`의 구체 조회 Repository가 담당하며, `usecase`가 검색 조건·커서·결과 타입을 소유한다. use case는 자기 모듈 조회 Repository를 직접 부를 수 있지만 저장 adapter·Spring Data 인터페이스·Redis 구현은 직접 부르지 않는다. 다른 모듈의 데이터 조합은 use case가 공개 API를 통해 한다. 조회 구현 선택과 중간 타입 기준은 [coding-guidelines.md](coding-guidelines.md)를 본다.

`booking.OrderStarted`와 `booking.OrderTerminated`는 DB publication에 FQCN이 저장되어 root에 유지한다. `booking.order`는 주문, `hold`는 선점, `selection`은 선택, `seat`는 회차 좌석, `salespolicy`는 판매 정책, `admission`은 대기열 자격 검증을 소유한다. 여러 capability를 조율하는 코드는 결과를 책임지는 곳에 둔다. `common`/`util`/`helper` 같은 소유권 없는 패키지는 만들지 않는다. 공통 감사 컬럼은 `shared.jpa.AuditedEntity`가 맡는다([ADR 0018](adr/0018-audit-base-entity-lives-in-shared.md)).

### HTTP와 공개 계약

`domain`·`usecase`·`port`·module `api`는 Spring Web/Swagger에 의존하지 않는다. 현재 일부 use case 중첩 record에 `@JsonProperty`가 있고 Jackson은 그 제한에서 빠져 있다. JSON 호환을 유지하며 DTO를 분리하는 제안은 미승인 기술 부채로 추적한다. 공개 interface는 `<module>.api`에 두고 `Api` 접미사를 붙인다([ADR 0014](adr/0014-module-public-contracts-live-in-api-packages.md)). 모듈 밖 조회 값은 `*Snapshot`으로 내보내며 엔티티를 노출하지 않는다.

## 저장소와 동시성

Aggregate를 변경하려고 읽으면 domain Repository, 화면 조회는 자기 모듈 조회 Repository를 쓴다. 하나의 모듈 안에서도 서로 다른 Aggregate는 scalar ID로 참조한다. DB 읽기는 짧은 트랜잭션에서 끝내고 다른 모듈 호출·Redis·WebSocket 작업 중 DB connection을 불필요하게 보유하지 않는다. 선점과 선택의 순서·실패 보상은 [예매 수명주기](core-booking-lifecycle.md)가 원본이다. Redis key·TTL·이벤트 publication·Flyway 데이터 전환의 운영 조건은 [operations.md](operations.md)를 본다.

## 오류 처리

모듈별 `*ErrorCode`와 예외가 업무 오류를 표현하고 HTTP 변환은 handler가 맡는다([ADR 0010](adr/0010-exceptions-do-not-own-http-status.md)). 오류 코드는 외부 계약이므로 이름 정리만을 이유로 공개 code·status·message를 바꾸지 않는다. 예외 체계 재설계는 승인된 현행 정책과 분리된 제안으로 추적한다.

## Enforcement

`ModularityTests`가 닫힌 모듈·승인 DAG를, `ArchitectureRulesTest`가 계층 의존·공개면·`@NullMarked`를, `DomainIsolationTest`가 BC domain 참조를, `AggregateAssociationTest`가 Aggregate 객체 연관을 검증한다. 변경별 명령과 환경은 [testing.md](testing.md#구조-테스트)를 본다. 테스트에 없는 정책도 자동으로 폐기하지 않는다.

## 생성 문서

`./gradlew test --tests "com.ticket.DocumentationTests"`가 현재 모듈 도표·canvas를 `build/spring-modulith-docs`에 만든다. 생성물은 현재 구현의 구조이고 ADR의 승인·미적용 여부를 대신하지 않는다.

## 문서와 결정

현재 구조는 이 문서, 업무 실행은 [core-booking-lifecycle.md](core-booking-lifecycle.md), 용어는 [glossary.md](glossary.md), 상세 결정 근거는 [ADR](adr/README.md)에 둔다. 코드·테스트·문서가 어긋나면 명확한 링크 오류는 고치고, 정책 충돌과 미확정 제안은 구분해 기록한다.
