# 아키텍처 기준

이 문서는 Ticket Core가 따라야 할 **모듈 책임과 의존성 방향의 단일 기준**이다. 현재 코드가 이 문서와
다르면 현재 위치를 선례로 삼지 말고 미완료된 구조 이전으로 판단한다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)·
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)·
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)을 함께 본다. 실행과 검증은
[operations.md](operations.md)를 본다.

## 원칙

- Ticket Core는 **단일 Gradle Spring Boot 프로젝트**다. 모듈 경계는 Gradle subproject가 아니라
  **Spring Modulith의 Application Module**이 강제한다. `com.ticket`의 직접 하위 패키지가 닫힌
  모듈이고, `com.ticket.ModularityTests`가 경계 위반을 잡는다.
- **Business Application Module은 Bounded Context 또는 독립적으로 캡슐화할 가치가 있는 supporting
  business capability와 정렬한다**([ADR 0006](adr/0006-bounded-context-module-boundaries.md)).
  `shared`/`web`/`error`/`config` 4개는 BC도 supporting capability도 아닌 기술 모듈이다 —
  앱 전역 배선이거나 여러 module이 공유하는 기술 계약일 뿐 업무 언어를 갖지 않는다.
- 각 모듈 root에는 다른 모듈이 쓰는 공개 계약(작은 interface + 불변 `record` snapshot, 이벤트)만
  두고, 실제 구현은 모듈 root 밖(업무 모듈은 capability 아래, 기술 모듈은 바로 아래)의
  `web`/`application`/`domain`/`infrastructure`/`exception` 패키지에 둔다. 별도 `internal` 계층은
  두지 않는다 — Modulith는 root 밖의 하위 패키지를 이름과 무관하게 내부로 취급한다. 어떤 모듈도
  `Type.OPEN`으로 선언하지 않는다. 정확한 배치는 [Module Structure](#module-structure)와
  [오류 처리](#오류-처리) 절을 본다.
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
| Member | Member, MemberSocialAccount | 회원·인증·전역 SecurityFilterChain |

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

- `shared`·`error`·`web`은 `@Modulith(sharedModules = ...)`로 선언해 어느 모듈에서든 참조할 수
  있다. 그래서 각 모듈 `@ApplicationModule(allowedDependencies = ...)`에는 **업무 모듈 의존
  상한만** 적는다 — 업무 모듈 의존이 하나도 없는 모듈은 상한을 `{}`로 명시한다.
- `show -> member`는 찜 use case의 회원 확인, `show -> venue`는 표시값 조립, `show -> like`는
  공연 상세의 찜 개수와 "내 찜 목록" 조회 위임 때문이다.
- `like -> member`가 있다. 찜하기·찜 해제·찜 상태 조회를 like가 소유하면서(ADR 0009) 탈퇴 회원을
  걸러내기 위해 `MemberLookup.requireActive`를 직접 부르기 때문이다. **like는 업무 모듈 의존이
  없는 leaf가 아니다** — ADR 0006 시점의 설명(당시 `favorite`가 leaf였다)은 그 ADR의 역사적
  기록이고 현재 구조가 아니다.
- `booking -> show`는 있지만 `booking -> venue`는 없다. booking이 쓰는
  `PerformanceSaleCatalog`/`PerformanceVenueLayoutCatalog`를 show가 façade로 유지하기 때문이다.
- `payment`는 entity-only 단계라 `shared`/`web`/`error`도 참조하지 않는 완전한 leaf다 —
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
| 같은 BC, 다른 Aggregate | scalar ID 참조. read model에서는 JOIN 가능(`*QueryPort`) | 각 Root별 Repository |
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
`PerformanceSaleCatalog`/`PerformanceVenueLayoutCatalog`만 쓰고 show entity를 JPA로 참조하지 않는다.

**Like 조합 규칙**: 찜의 데이터·불변식은 `like`(옛 `favorite`)가 소유한다. HTTP endpoint·use
case는 무엇이 필요한지에 따라 갈린다 — **다른 BC의 예/아니오(존재)만 있으면 되는 것**(찜하기·
찜 해제·찜 상태 조회)은 like가 소유하고, **다른 BC의 실제 표시 데이터**가 필요한 것("내 찜
목록"의 공연 제목·이미지·공연장 이름)은 그 데이터를 가진 show가 소유한다. `show.catalog.domain`은
like를 모른다 — `show.catalog.application`의 조회 service가 like의 공개 API(`LikeQuery`/`LikeCommand`)를
주입받아 조합한다(직접 데이터 JOIN 아님). 반대로 like는 존재 확인을 하지 않는다 — 존재하지
않는 대상을 찜해도 막지 않는다. 회원 활성 확인은 예외다 — `member`는 leaf라 `like -> member`가
순환을 만들지 않고, JWT 인증만으로는 탈퇴 회원을 걸러낼 수 없어 like가 직접
`MemberLookup.requireActive`를 부른다. 이 규칙은 `com.ticket.DomainPurityTest`(ArchUnit,
6개 BC 전체의 모든 `domain` 계층)가 강제한다. 왜 catalog 흡수 대신 이 형태가 됐는지는
[ADR 0006](adr/0006-bounded-context-module-boundaries.md)을, 찜 모듈 개명과 대상 일반화는
[ADR 0008](adr/0008-like-target-generalization.md)을, use case 소유권을 존재/표시 기준으로
나눈 결정은 [ADR 0009](adr/0009-like-owns-write-and-status-usecases.md)를 본다.

## Module Structure

물리적으로 별도 Gradle 모듈이 아니라 `<module>` 아래의 패키지일 뿐이다.

| 하위 패키지 | 담는 것 |
| --- | --- |
| `web` | Controller, 요청/응답 DTO, HTTP 커서 문자열 |
| `application` | use case, 트랜잭션 경계, 조회 포트와 결과 view, 분산락·이벤트 발행·외부 provider 등 출력 포트 |
| `domain` | 엔티티와 값 객체, 상태 enum, 정책과 검증기, Aggregate Repository 계약 |
| `infrastructure` | Repository 어댑터, Querydsl 조회, Redis/Redisson, WebSocket publisher, 외부 HTTP client |
| `exception` | `<Module>ErrorCode`, 예외 클래스, `handler` |

**모듈 root = cross-module 공개 계약.** 구현 클래스, JPA entity, Repository는 root에 두지 않는다.

**패키지 구조는 모듈 → capability → 계층이다.** 업무 모듈 여섯(`booking`/`member`/`show`/
`venue`/`like`/`payment`)은 모듈 root 바로 아래에 **capability** 패키지를 두고, 위 계층 표의
`web`/`application`/`domain`/`infrastructure`는 그 capability 안에 온다 —
`booking.order.domain.Order`, `show.catalog.infrastructure.QuerydslShowListQueryPort`처럼
읽는다. capability가 하나뿐인 모듈(`like`, `payment`)도 같은 형태를 유지한다: 지금 하나뿐이라는
사실이 앞으로도 하나라는 뜻은 아니고, 모듈마다 읽는 규칙이 갈리는 편이 더 비싸다.

capability는 **같은 업무 변경에 함께 고쳐지는 코드의 묶음**이다. Application Module이 아니다 —
`package-info.java`를 두지 않고 `@ApplicationModule`로 선언하지 않는다. 모듈 집합과 경계는
그대로 10개다(`com.ticket.ModularityTests`가 원본).

| 모듈 | capability |
| --- | --- |
| `booking` | `order` · `seat` · `hold` · `salespolicy` · `selection` · `admission` · `ticket` |
| `member` | `account` · `auth` · `oauth` · `security` |
| `show` | `catalog` · `performance` · `classification` · `performer` |
| `venue` | `facility` · `seat` |
| `like` | `preference` |
| `payment` | `attempt` |

배치 규칙:

- capability 아래에는 **실제로 필요한 계층만** 만든다. 빈 계층이나 미래 기능용 패키지는 두지
  않는다.
- `application.port`/`application.usecase`, `web`의 `request`/`docs`/`support`,
  `exception`의 `handler`는 그대로 유효한 예외다. 다만 앞의 셋은 이제 capability 안에 있다.
- **`exception`은 그대로 capability 축을 따르지 않는다** — 다만 "모듈 바로 아래 하나의
  평탄한 `<module>.exception`"도 아니다. 실제 배치 기준은 [아래 오류 처리](#오류-처리) 절과
  [ADR 0010](adr/0010-exceptions-do-not-own-http-status.md)이 원본이다(ADR 0002가 정한
  "모듈이 자기 오류를 소유한다"는 원칙 자체는 그대로다).
- 여러 capability가 **실제로** 공유하는 코드만 `<module>.support.<layer>`에 둔다 — 공통 감사
  base entity(`BookingAuditedEntity` 등), booking의 분산락 계약·구현, 공통 Redis 만료 배선이
  그 예다. 업무 use case를 support로 모으지 않는다.
- 여러 capability를 조립하는 코드는 **결과를 책임지는 capability**에 둔다. 주문 생성이 좌석·
  hold·정책을 엮어도 그것은 `order`다.
- Controller 하나가 여러 capability를 호출하는 것은 허용한다. 폴더를 맞추려고 endpoint나
  Controller를 쪼개지 않는다.
- capability마다 façade·interface·event를 의무적으로 만들지 않는다. 접근 제한이 걸리면 같은
  모듈 안에서 최소한으로 넓히고, 그걸 풀려고 구현을 모듈 root에 공개하지 않는다.

capability 안에서는 여전히 **계층 → 클래스**다. `domain`/`application`/`infrastructure` 아래에
또 기능별 하위 패키지를 두지 않는다. 클래스 이름 자체가 이미 기능을 드러낸다
(`ShowRepository`, `PerformanceSalesPolicy` 등).

**`application.usecase`에는 `*UseCase`로 끝나는 클래스만 둔다.** use case가 조립에 쓰는
서비스·헬퍼·view·port는 여기 두지 않고 `application` 바로 아래(또는 port는 `application.port`)에
둔다 — use case가 진입점이라는 것만 폴더로 드러내고, 그 진입점이 무엇을 조립해 쓰는지는 여전히
`application` 평평한 목록에서 바로 보이게 하기 위해서다. capability가 그 목록을 다시 업무
단위로 좁혀 준다.

포트 소유 기준은 **그 기능을 필요로 하고 의미를 정의하는 쪽**이 소유한다.

| 계약의 성격 | 소유 위치 |
| --- | --- |
| aggregate 저장·복원과 업무 명령에 필요한 조회 | `domain` |
| 화면 조회·검색·집계 결과 | `application` |
| 분산락, 토큰, 외부 provider, publisher/client | `application` |
| HTTP 입력·출력 계약 | `web` |
| JPA, Querydsl, Redis, Redisson, JWT 구현 | `infrastructure` |

기능은 클래스 이름 접두사로 드러낸다(`ShowRepository`, `PerformanceGrade`, `OrderCreator` 등).
`model`/`repository`/`store`/`command`는 더 이상 하위 패키지가 아니라 **명명 관용**이다 —
Aggregate Repository 계약(옛 `repository`), 저장 기술 중립 상태 계약(옛 `store`), 상태 변경
use case(옛 `command`)가 어떤 성격인지는 클래스 이름과 위 "계약의 성격" 표로 판단한다. 조회
포트(옛 `query`)는 아래 "Repository와 Query Port" 절이 별도로 다룬다.

Querydsl 조회 구현은 `Querydsl` 접두사(`QuerydslShowListQueryPort implements
ShowListQueryPort`), Aggregate Repository 어댑터는 `*RepositoryAdapter`, 안에서 쓰는 Spring
Data 인터페이스는 `SpringData*JpaRepository`로 구분한다. `View`는 조회 경계의 화면/응답용
projection, `Snapshot`은 특정 시점의 읽기 결과(모듈 공개 API에서는 cross-module 스냅샷), `Row`는
저장소 조회 한 행, `Output`은 use case 반환값, `Param`/`Criteria`/`Event`/`Request`는 각각 조회
조건 구성값/검색 조건/발생한 사실/외부 입력이다. **조회 전용 `...View` 타입에 비즈니스 로직을
두지 않는다** — 판정은 별도 validator/policy가 맡는다.

## Repository와 Query Port

조회 기능이라고 해서 모두 같은 조회 계약을 쓰지 않는다. **Aggregate를 저장·복원하기 위한 Domain
Repository**와 **화면/검색/목록 조회를 위한 Application Query Port**를 구분한다.

판단 기준은 쿼리의 복잡도가 아니다. **행동시키기 위해 Aggregate를 가져오면 Domain Repository를
쓰고, 보여주기 위해 데이터를 가져오면 Application Query Port를 쓴다.**

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

### Application Query Port

화면·API·검색·목록·집계에 필요한 데이터를 조회하는 인터페이스다. Aggregate를 복원하는 게
목적이 아니라 **use case가 필요로 하는 조회 결과를 만드는 것**이 목적이다.

```java
ShowDetailView detail = showDetailQueryPort.findShowDetail(showId)
        .orElseThrow(() -> new NotFoundException(...));
```

`application`의 `port` 하위 패키지에 두고(위 "패키지 구조는 모듈 → 계층 → 클래스다" 절의 세
번째 예외) 이름은 `*QueryPort`를 쓴다(`ShowDetailQueryPort`, `ShowListQueryPort`). 구현은
`infrastructure`에 `Querydsl*QueryPort`로 둔다. Aggregate를 여러 개 복원해 Java에서 조합하기
보다, Querydsl로 필요한 read model을 직접 만든다 — 반환 타입은 Aggregate가 아니라 `View`/`Row`
(위 "패키지 구조" 절의 명명 규칙)다.

**Aggregate 경계와 API 응답 경계는 같을 필요가 없다.** 공연 상세는 Show/Performance/Grade/
Genre/Performer뿐 아니라 다른 BC의 표시값(venue 이름, 찜 개수)까지 한 응답에 담는다 — 그 조합은
Query Port 자신이 아니라 그 Query Port를 부르는 use case가 한다(`GetShowDetailUseCase`가
`ShowDetailQueryPort`로 show 자기 데이터를 얻고, `VenueLookup`/`LikeQuery`로 다른 BC의 표시값을
더한다). Query Port 구현(`Querydsl*QueryPort`)이 다른 module의 공개 계약을 직접 호출하지
않는다 — persistence adapter의 역할은 자기 module DB를 읽는 것까지다.

**같은 id로 두 계약이 동시에 존재해도 된다.**

```java
// Domain Repository — Aggregate 복원 → 업무 행동
Optional<Show> ShowRepository.findById(Long showId);

// Application Query Port — 화면 표시값 → API 응답
Optional<ShowDetailView> ShowDetailQueryPort.findShowDetail(Long showId);
```

둘 다 같은 `SHOWS` 테이블을 볼 수 있지만 목적이 다르다. Read model(`View`/`Row`)은 Aggregate가
아니고, 상태 변경을 하지 않으며, 핵심 업무 불변식을 갖지 않는다 — "구매 가능한가" 같은 판정은
그 값을 정하는 domain(`PerformanceSalesPolicy` 등)이 하고, Read model은 이미 정해진 값을
전달하는 역할에 머문다(`docs/architecture.md`의 "조회 전용 `...View` 타입에 비즈니스 로직을
두지 않는다" 규칙과 같다).

## 계층별 검증 책임

판단 기준 한 문장 — **"이 검증이 사라지면 무엇이 먼저 깨지는가."** HTTP 응답 품질만 나빠지면
`web`, 다른 adapter에서 호출해도 흐름이 깨지면 `application`, 어떤 호출 경로에서도 업무가 틀리면
`domain`, 기술 경계에서만 성립하면 `infrastructure`다.

| 계층 | 소유하는 검증 | 실패 표현 |
| --- | --- | --- |
| `web` | JSON·HTTP 요청 형식, 필수 body field, blank/null, ID 양수 여부, path/query/header 형식 | Bean Validation → `InvalidRequestException`(400/`E400`) |
| `application` | adapter 공통 `UseCase.Input` 계약, 여러 입력 조합, 데이터 존재 여부, 요청 권한, 중복·멱등성, 다른 모듈 공개 API를 엮는 실행 선행조건 | 공통 예외 또는 소유 모듈 예외 |
| `domain` | 업무 불변식, 값 객체 유효성, 상태 전이, 예매 가능 시간, 좌석 소유권과 선점 한도 | 소유 모듈 `exception`의 업무 예외 |
| `infrastructure` | Redis·JWT·외부 API payload decode, DB constraint 번역 | 기술 예외를 상위 계층이 이해할 실패로 번역 |

**Bean Validation은 `web`만 쓴다.** 파라미터 제약은 `controller.docs` 인터페이스에만 선언한다 —
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
com/ticket/<module>/<capability 또는 support>/exception/
  <Module>ErrorCode.java          enum implements com.ticket.error.ErrorCode
  <Module>Exception.java          abstract extends com.ticket.error.TicketException(errorCode·message·data만)
  <구체 예외>.java                 errorCode·메시지·data를 생성자에서 확정(상태는 없다)
  handler/<Module>ExceptionHandler.java   @Order(HIGHEST_PRECEDENCE), base 타입 하나만 잡고
                                          구체 타입 -> HTTP 상태를 이 handler가 정한다
```

**`exception` 패키지 자체는 capability로 나누지 않는다.** 한 capability에서만 쓰는 구체
예외는 그 capability의 `exception`에 두고, 여러 capability가 실제로 던지는 예외와 module
handler·base 타입·`<Module>ErrorCode`는 `<module>.support.exception`에 둔다(어느 쪽인지는
grep으로 실제 throw 위치를 확인해서 정한다 — 이름만으로 추정하지 않는다). capability가
하나뿐인 module(Like)은 그 capability의 `exception`에 전부 둔다. admission처럼 자기 완결적인
capability는 `TicketException`을 직접 상속해 module base와 무관하게 자기 `exception`에 전부
둔다.

오류 계약 소유 기준(모듈별 오류 vs `com.ticket.error`의 공통 오류), 응답 봉투가 `web`에 있는
이유, `ProblemDetail`을 채택하지 않은 이유는 [ADR 0002](adr/0002-module-owned-error-contracts.md)가
원본이다. module handler가 다른 module의 오류까지 삼키지 않는지는 `ExceptionHandlerScopeTest`가,
E-code(외부 계약, `gatling-test`가 하드코딩) 전역 유일성은 `ErrorCodeUniquenessTest`가 강제한다.

## 주요 흐름

코드만 봐서는 알기 어려운 정책·설계 결정만 다룬다. 엔드포인트 목록은 Swagger(`/api/api-docs`)가,
예매 실행 순서는 [core-booking-lifecycle.md](core-booking-lifecycle.md)가 원본이다.

**인증**: JWT 기반 stateless 방식이다. OAuth2 인가 흐름은 별도 filter chain에서 처리하고 전역
`SecurityFilterChain`은 `member`가 제공한다. 다른 모듈의 controller는 `member.AuthenticatedMember`만
parameter로 받고 JWT나 `member` 내부의 `Member`를 보지 않는다.

**좌석 조회는 performanceId 기준이다** — 같은 Show라도 회차마다 편성·가격이 다를 수 있어 `showId`
기준 조회 API는 만들지 않는다. `booking.seat.web.PerformanceSeatQueryController`가 공개하는 3개 API
(정적 seat-map / 동적 상태 / 등급별 잔여석)는 회차당 고정된 query 수를 유지한다 — 무엇을 어떻게
고정하는지는 [testing.md의 performance 기준 API](testing.md#performance-기준-api와-가격-snapshot-회귀)가
원본이다. **정적 seat-map에 있는데 상태 응답에 없는 좌석을 클라이언트가 AVAILABLE로 추정하게 하지
않는다** — 데이터 불일치는 예외를 던지지 않고 조용히 그 좌석만 제외한다.
`show.performance.web.ShowVenueLayoutController`(물리 Venue 배치 전용, ADR 0006으로 booking에서 옮겨옴)는
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

분산락은 `com.ticket.booking.support.application.LockManager` 같은 명시적 포트 호출로 처리한다.
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
| `<bc>`의 어느 `domain` 계층이든 다른 BC를 참조하는지 | `com.ticket.DomainPurityTest` |
| `shared`에 bean을 등록했는지 | `com.ticket.shared.SharedModulePurityTest` |
| 파라미터 제약 선언 위치 | `ControllerParameterConstraintTest` |
| 필수 입력 오류 문구 | `com.ticket.error.InvalidRequestMessageContractTest` |
| E-code 전역 유일성 / handler 스코프 | `ErrorCodeUniquenessTest` / `ExceptionHandlerScopeTest` |
| module 구조 문서 생성 | `com.ticket.DocumentationTests` |

무엇을 검증하는지 자세한 목록은 [testing.md의 구조 테스트](testing.md#구조-테스트), 실행 명령은
`/verify`가 원본이다. **규칙 본문은 테스트 코드가 원본이고 여기 옮겨 적지 않는다.** 규칙을
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
- 새 클래스가 맞는 capability에 있는가 — 같은 업무 변경에 함께 고쳐질 코드 옆에 있는가
- capability 안에 계층 아래 또 기능별 패키지를 만들지 않았는가
- `support`에 둔 것이 실제로 여러 capability가 쓰는가, 아니면 갈 곳을 못 정한 것인가
- DB 상태와 Redis 상태를 합치는 규칙의 소유자가 한 곳인가
- 새 추상화가 실제 경계를 보호하는가, 사용하지 않는 계층을 늘리기만 하는가
