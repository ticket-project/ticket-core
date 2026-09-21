# ADR 0019: 이름이 조회 기술을 말한다 — `*QuerydslRepository`와 3단 구조

## 상태

채택됨 (2026-09-21)

[ADR 0017](0017-query-implementations-live-in-persistence.md)의 결정 §1·§2·§5를 대체한다. "조회
구현은 `persistence`에 있다"는 뼈대는 그대로 두고, **어떤 이름을 어디에 붙이는가**만 바꾼다.

## 배경

ADR 0017은 조회 구현을 `persistence`의 `*QueryRepository`로 모으고, 그 판별을 "`persistence`
package + 이름이 `QueryRepository`로 끝남 + `JPAQueryFactory` 필드 보유"로 정했다. 그 뒤
`docs/readability-guidelines.md` §10이 "Querydsl은 기본 선택이 아니다"를 세우면서 고정 조회가
차례로 `@Query`와 파생 메서드로 내려갔다.

그러자 **이름이 내용과 어긋났다.** 다섯 개의 `*QueryRepository` 중 셋에
`JPAQueryFactory`가 아예 없었다.

- `PerformanceQueryRepository` — Querydsl을 걷어낸 뒤 다섯 메서드가 전부 한 줄 위임
- `VenueQueryRepository` — 같음. 게다가 Venue와 Seat 두 Aggregate를 한 클래스가 들고 있었다
- `PerformanceSeatQueryRepository` — 처음부터 Querydsl을 쓴 적이 없다

`ArchitectureRulesTest`는 이 어긋남을 술어 하나를 넓혀서 흡수하고 있었다. `READS_DB`는
"`JPAQueryFactory`이거나 Spring Data repository"였고, 그 javadoc은 "조회 Repository가 반드시
Querydsl을 쓰는 것은 아니다"라고 적고 있었다. 규칙이 이름을 믿지 못해 예외를 만든 상태다.

`*QueryRepository`는 또 use case에 열린 유일한 `persistence` 타입이라, 이름 하나가 접근 권한까지
정한다. 이름이 내용을 말하지 못하면 그 권한이 어디까지 열려 있는지도 읽어봐야 안다.

## 결정

1. **Querydsl로 조회하는 것만 `*QuerydslRepository`라는 이름을 쓴다.** 판별은 "`persistence`
   package + 이름이 `QuerydslRepository`로 끝남 + `JPAQueryFactory` 필드 보유"다. 남은 것은
   `ShowQuerydslRepository`와 `LikeQuerydslRepository` 둘이다 — 동적 조건 조합, 복합 정렬,
   커서 페이징, 집계가 그 자리다.

2. **Spring Data/JPQL로 끝나는 조회는 3단으로 내려간다** — 계약 `*Repository`(domain 또는 공개
   `api`) → `*RepositoryAdapter`(persistence) → `SpringData*JpaRepository`(package-private).
   저장과 같은 길이다. 고정 조회는 교체 지점이 아니라 그냥 SQL이고, 그 SQL은 adapter 안에 있다.

3. **계약을 새로 만들지 않고 이미 있는 Aggregate 계약에 메서드를 더한다.** 회차 표시값은
   `PerformanceRepository`, 좌석 조회는 `PerformanceSeatRepository`, show 조각은 `ShowRepository`와
   `GradeRepository`가 받았다. venue는 계약이 이미 `venue.api`에 있어 이름만 바뀌었다.

4. **한 adapter는 한 Aggregate만 든다.** `VenueQueryRepository`가 `VenueLookupApi`와
   `VenueSeatLookupApi`를 함께 구현하던 것을 `VenueRepositoryAdapter`와 `SeatRepositoryAdapter`로
   나눴다. Seat은 Venue와 다른 Aggregate이고, 그래서 `Seat.venueId`가 연관관계가 아니라 raw
   컬럼이다. 한 클래스가 둘을 들면 그 경계가 코드에서 사라진다.

5. **`READS_DB` 술어를 지운다.** Querydsl 없는 조회 Repository를 받아주려고 한 겹 넓혀 둔
   것인데, 그런 것이 이제 없다. `QUERY_REPOSITORY`는 `DB_QUERY`(JPAQueryFactory 보유)만 본다.

## 대안과 버린 이유

- **이름을 그대로 두고 내용만 맞춘다.** 즉 고정 조회에도 `JPAQueryFactory`를 다시 들인다.
  규칙을 만족시키려고 코드를 늘리는 일이고, §10이 세운 방침을 정면으로 거스른다.
- **`*QueryRepository`를 유지하고 Querydsl 여부는 묻지 않는다.** 지금까지의 상태다. 이름이
  기술을 말하지 않으니 `READS_DB` 같은 예외 술어가 계속 필요하고, use case에 열리는 범위가
  이름만으로는 읽히지 않는다.
- **읽기 전용 계약을 따로 만든다**(`*ReadRepository` 등). 계약이 두 벌이 되고, 같은 테이블을
  두 계약이 나눠 갖는 이유를 매번 설명해야 한다.

## 대가

**ADR 0017이 명시적으로 거절한 것을 일부 되살린다.** 0017의 결정 §3은 "조회를 위해 port를 한 겹
더 두지 않는다"였고, `ArchitectureRulesTest`의 javadoc도 "읽기 경로에는 계약을 한 겹 더 둘 이유가
없다 — port interface와 adapter를 한 쌍씩 만들면 use case에서 SQL까지 읽을 것 없는 경유 지점만
늘어난다"고 적고 있었다. 3단으로 내려간 조회는 use case에서 SQL까지 한 홉이 늘어난다.

그 대가를 받아들이는 이유는 **경유 지점이 실제로는 늘지 않기 때문**이다. 이 조회들은 이미 계약
없는 위임 클래스를 하나씩 거치고 있었다(`PerformanceQueryRepository` → `SpringData…`). 그 위임
클래스가 adapter가 되고, 이름 없는 경유 지점이 이름 있는 계약이 됐을 뿐이다. 홉 수는 같고,
use case가 보는 타입이 `persistence` 구체 클래스에서 domain 계약으로 바뀌었다.

**Querydsl 조회는 계약 없이 남는다.** 0017의 판단을 여기서는 유지한다 — 동적 조건 조립은
화면이 요구하는 SQL 그 자체라 교체 지점이 아니다.

## 결과

- use case에 열린 `persistence` 타입이 5개에서 2개로 줄었다
  (`ArchitectureRulesTest.APPROVED_QUERY_REPOSITORIES`).
- `ArchitectureRulesTest`에서 술어 하나(`READS_DB`)와 그 예외 설명이 사라졌다.
- venue module에 Aggregate당 adapter 하나라는 모양이 생겼다.
- 외부 계약은 바뀌지 않았다. HTTP 응답 JSON, DB schema, Redis key, 이벤트 payload 그대로다.
  projection·정렬·null 처리·join·query 개수도 그대로다(§14).
