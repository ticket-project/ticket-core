# ADR 0017: 조회 구현은 `persistence`의 조회 Repository가 갖는다

## 상태

채택됨 (2026-09-18)

> 2026-09-19 갱신: **이 ADR이 남긴다고 한 `query` package는 그 뒤 전부 없어졌다.** 읽기 모델과
> 검색 조건, 커서·정렬 타입(`ShowCursor`/`ShowSort`)은 `<module>.usecase`가 갖고 응답 항목은 그
> use case의 중첩 record다. `QuerydslTupleColumns`는 `ShowQueryRepository`가 흡수했고,
> `APPROVED_QUERY_REPOSITORIES`는 여섯이 아니라 다섯이며 `query는_조립과_HTTP를_모른다` 규칙은
> 없다(`com.ticket.ArchitectureRulesTest`가 원본). 아래 후속 메모가 적은 "남은 query package 둘"도
> 지금은 없다. 조회 구현이 `persistence`에 있다는 결정 자체는 그대로 유효하다.

[ADR 0016](0016-capability-first-layout-inside-modules.md)이 `query`에 준 두 역할 중 **조회 구현**
부분만 `persistence`로 옮긴다. `query`는 읽기 모델(`*Row`/`*View`/`*Info`)과 검색 조건
(`*Param`/`*Criteria`), 커서·정렬 타입(`ShowCursor`/`ShowSort`)을 계속 소유한다. 0016의 나머지
결정(작은 모듈은 역할, `booking`은 capability 우선, `Repository`는 계약이고 `persistence`는 기술)은
그대로 유효하고, Application Module 경계([ADR 0003](0003-spring-modulith-application-module-boundaries.md),
[ADR 0006](0006-bounded-context-module-boundaries.md))와 공개 계약 위치
([ADR 0014](0014-module-public-contracts-live-in-api-packages.md))도 바뀌지 않는다.

## 배경

`query` package는 두 가지를 동시에 담고 있었다. 하나는 화면에 내보낼 **읽기 모델**이고, 다른
하나는 그 값을 만드는 **DB 조회 구현**이다. "읽기 모델을 담는 곳"과 "DB를 읽는 곳"이 한 package에
섞이면서 두 가지 비용이 생겼다.

첫째, package 이름이 무엇을 담는지 말하지 못한다. `show.query`를 열면 `ShowDetailView`(데이터)와
`ShowDetailQuery`(Querydsl·`EntityManager`·트랜잭션 경계)가 나란히 있었다. 저장 기술은 원래
`persistence`가 담기로 되어 있었는데(ADR 0016 §5), 조회만 예외였다.

둘째, 조회 하나마다 class가 하나씩 늘었다. show 하나에 `ShowListQuery`·`ShowDetailQuery`·
`ShowSummaryBatchQuery`가 따로 있었고, 세 class가 정렬·커서·판매 상태 조건을 공유하려면
package-private helper 세 개(`QuerydslShowSortResolver`, `QuerydslShowCursorConditionBuilder`,
`SaleDisplayStatusPredicates`)를 또 만들어야 했다. `RegionVenueIds`처럼 값 하나를 감싼 타입,
`SeatStateSnapshotRow`처럼 `SeatStateView`와 필드가 같은 중복 row 타입도 그 부산물이다. 앞선
정리(OE-04)로 `*QueryPort`/`*QueryAdapter` 쌍은 이미 없앴지만, 남은 구체 class 자체는 14개였다.

## 결정

1. **조회 구현은 각 module `persistence`의 `*QueryRepository`가 갖는다.** `@Repository` + 생성자
   주입으로 Querydsl/JPA를 직접 쓴다. 조회 결과 타입만 `query`에 남는다.

2. **관련 조회는 한 class로 모은다.** 14개 구현이 6개가 됐다 —
   `show.persistence.ShowQueryRepository`, `show.persistence.PerformanceQueryRepository`,
   `venue.persistence.VenueQueryRepository`,
   `booking.seat.persistence.PerformanceSeatQueryRepository`,
   `booking.order.persistence.OrderQueryRepository`, `like.persistence.LikeQueryRepository`다.
   `venue.query`와 `like.query` package는 통째로 사라졌다.

3. **use case는 같은 module의 조회 Repository를 직접 부를 수 있다.** 조회를 위해 port를 한 겹
   더 두지 않는다. **저장 adapter·Spring Data 인터페이스·Redis 구현에 대한 use case 직접 접근
   금지는 그대로다** — `persistence` 전체를 연 것이 아니라 조회 Repository만 열었다.

4. **한 조회에서만 쓰는 helper는 private 메서드로 내린다.** 정렬·커서·판매 상태 helper 셋은
   `ShowQueryRepository`의 private 메서드가 되어 더 이상 Spring 빈이 아니다. 두 조회 Repository가
   함께 쓰는 `QuerydslTupleColumns`만 `show.persistence`에 package-private으로 남는다.

5. **규칙은 이름이 아니라 구현 사실로 판별한다.** "`persistence` package + 이름이
   `QueryRepository`로 끝남 + `JPAQueryFactory` 필드 보유"를 조회 Repository로 본다. 지금 열려 있는
   목록은 `ArchitectureRulesTest.APPROVED_QUERY_REPOSITORIES` 6개로 고정해서, 새 조회 Repository가
   PR에서 조용히 늘면 테스트가 실패한다.

## 대안과 버린 이유

- **조회마다 port + adapter를 유지한다.** 구현이 하나뿐인 1:1 위임이다. 이미 OE-04에서 걷어낸
  경유 지점을 되살릴 이유가 없다.
- **`Finder`/`Reader` 계층을 하나 더 둔다.** use case와 조회 사이에 이름만 다른 층을 넣는 것이라,
  조회가 어디서 실행되는지만 한 단계 더 멀어진다.
- **범용 조회 프레임워크(`BaseQuerydslRepository`, `QueryExecutor`)를 만든다.** 지금 문제는
  abstraction 부족이 아니라 경유 지점 과잉이다
  ([readability-guidelines.md](../readability-guidelines.md) §11).

세 대안 모두 **경유 지점만 늘리고 "이 화면 값이 어느 query에서 나오는가"라는 물음에는 답을 더하지
않는다.**

## 하지 않은 것

- **읽기 모델을 `persistence`로 옮기지 않았다.** `*Row`/`*View`는 조회 경계의 데이터라
  `query`가 계속 소유하고, `query는_조립과_HTTP를_모른다` 규칙이 usecase/event/endpoint/persistence
  역참조를 그대로 막는다. 남은 `query`는 `show.query`, `booking.seat.query`,
  `booking.order.query`다.
- **`persistence`를 use case에 전면 개방하지 않았다.** 열린 것은 조회 Repository 6개뿐이다.
- **외부 계약을 바꾸지 않았다.** HTTP 응답 JSON, DB schema, Redis key, 이벤트 payload는 그대로다.

## 결과

**얻은 것.** 최상위 타입이 줄었다 — 조회 구현 14 → 6, helper 3개와 `RegionVenueIds`,
`SeatStateSnapshotRow` 소멸, `venue.query`·`like.query` package 소멸. `venue`는 이제
`api`·`domain`·`persistence` 셋뿐이고, 공개 계약은 `VenueQueryRepository implements VenueLookupApi,
VenueSeatLookupApi`가 직접 구현한다. "DB를 읽는 코드는 `persistence`에 있다"가 예외 없는 문장이 됐다.

**잃은 것.** `*QueryRepository` 한 class가 커졌다. Domain Repository와 이름이 비슷해져, 둘의 차이는
package(`domain` vs `persistence`)와 접미사로만 드러난다 —
[architecture.md의 "Repository와 조회 Repository"](../architecture.md)가 그 구분의 원본이다.

**규칙의 보호 범위는 줄지 않았다.** "DB를 읽는 구현이 다른 업무 module을 조합하지 않는다"는 검사
대상이 `..query..`에서 **module 전체 + `JPAQueryFactory` 보유 클래스**로 넓어져, 옮겨진 조회
Repository에도 그대로 걸린다. `endpoint`가 use case를 건너뛰고 Repository·조회 Repository를 직접
부르지 못하는 규칙도 그대로다. 전부 `com.ticket.ArchitectureRulesTest`가 원본이다.

## 후속 메모 (2026-09-19)

위 결정은 그대로 유효하다. 다만 그 뒤 조회 방침이 바뀌면서(Querydsl을 기본 선택으로 두지 않고,
엔티티로 충분한 조회는 엔티티를 반환한다 — `docs/readability-guidelines.md` §10) 본문이 든 예시
중 일부는 현재 코드에 없다.

- **`ShowDetailView`(§배경)가 사라졌다.** `ShowQueryRepository`는 `findShow`(`Show` 엔티티)·
  `findGenreNames`·`findGrades`·`findPriceSummary`·`findPerformanceDates`·`findPerformer` 조각만
  주고 `GetShowDetailUseCase`가 Output을 직접 만든다. query 개수는 그대로다.
- **`booking.order.persistence.OrderQueryRepository`(결정 §2)와 `booking.order.query` package
  (§하지 않은 것)가 사라졌다.** 주문 상세·상태는 `OrderRepository`의 Spring Data `@Query`
  (`join fetch o.orderSeats`)로 `Order` 엔티티를 받아 use case가 Output을 만든다. 조회 Repository는
  6개에서 5개가 됐다(`ArchitectureRulesTest.APPROVED_QUERY_REPOSITORIES`도 그에 맞는다).
- **남은 `query` package는 `show.query`와 `booking.seat.query` 둘이다.**
- `PerformanceQueryRepository`에는 grade projection 2개만 Querydsl로 남았고, 나머지 고정 조회는
  `@Query` 생성자 표현식으로 옮겼다. `PerformanceSeatRepositoryAdapter`도 Querydsl을 쓰지 않아,
  저장 adapter 중 Querydsl을 쓰는 것은 없다.
