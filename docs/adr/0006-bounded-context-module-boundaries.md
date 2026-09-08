# Application Module을 Bounded Context 단위로 재편한다

## 상태(2026-09-07): 채택·구현됨. ADR 0003 §3·§11, ADR 0005 §4를 module set·DAG 범위에서 다시 supersede

**2026-09-07 갱신(Performance 책임 혼재 후속 결정)**: 아래 "결정하지 않는 것"이 미루었던 A1/A2
선택은 **A2(예매·대기열 정책을 Booking BC의 `PerformanceSalesPolicy`로 이관)로 확정·구현됐다.**
`Performance`(show)는 이제 회차 일정(startTime/endTime)만 소유하고,
`booking.domain.performancepolicy.model.PerformanceSalesPolicy`가 예매 접수 기간·Hold 좌석 수
한도·대기열 진입 정책의 원본과 모든 판정을 소유한다. `show.BookingPolicyLookup`/
`BookingPolicySnapshot`과 `PerformanceQueuePolicy`/`QueueActivation`/`BookingPolicyValidator`/
`BookingEntryResolver`는 제거됐다. `booking -> show` 의존은 좌석/가격 조합용
`PerformanceSaleCatalog`/`PerformanceVenueLayoutCatalog` 때문에 그대로 남아 있고, `show -> booking`
의존은 추가되지 않았다(순환 없음 유지). booking이 `GET /api/v1/booking/performances/{id}/booking-mode`로
회차 예매 방식을 인증 없이 공개한다. DB는 booking V6 migration이 옛 show/`__root` 소유
`PERFORMANCE_QUEUE_POLICIES`/`PERFORMANCES` 정책 컬럼 4개를 backfill 후 제거했다(정책 소유권 이관
예외, `docs/architecture.md`/`docs/operations.md`의 "DB 마이그레이션" 절 참고). 아래 "결정하지 않는
것" 절의 본문은 그 이전 결정 시점의 기록으로 남긴다.

`docs/agents/domain.md`는 "Application Module은 업무 기능 경계이지 BC(Bounded Context)가 아니다"를
전제로 삼아 왔다. 이 ADR은 그 전제를 뒤집는다 — **지금부터 Application Module(기술 모듈 제외)은 곧
BC다.** `catalog` 하나가 물리 시설(Venue/Seat), 작품·회차(Show/Performance/Grade), 찜(ShowLike)을
모두 소유하던 구조를 여섯 개 BC로 나눈다.

| BC | 소유 | module |
| --- | --- | --- |
| Venue | Venue, Seat, Region | `venue`(신설) |
| Show | Show, Category, Genre, ShowGenre, Performer, Performance, Grade, PerformanceGrade, PerformanceQueuePolicy, 예매 정책 | `show`(`catalog` 개명) |
| Booking | PerformanceSeat, Selection, Hold, Order, OrderSeat, Ticket, admission 검증, 분산락, WebSocket | `booking`(유지) |
| Payment | Payment | `payment`(유지) |
| Favorite | ShowLike | `favorite`(신설) |
| Member | Member, 인증 | `member`(유지) |

`shared`/`web`/`error`/`config`/`seed`는 BC가 아닌 기술 모듈로 그대로 둔다. module 수는 9개 →
11개(BC 6 + 기술 5)다.

## 배경

ADR 0003 §11은 찜(showlike)을 `catalog`가 흡수하게 했다 — 당시 근거는 `catalog -> showlike`(공연
상세의 찜 개수)와 `showlike -> catalog`(공연 존재 확인, 내 찜 목록 표시값) 두 방향이 만나 순환이
생긴다는 것이었고, 좋아요를 `Show.viewCount`와 같은 파생 지표로 보고 흡수를 택했다. 그 결과
`catalog`가 Venue/Seat/Show/Performance/ShowLike를 한 module에 모두 담게 됐다.

사용자가 이 구조를 여섯 개 BC로 다시 나누기로 했다. 순환을 없애는 핵심은 흡수가 아니라 **방향을
하나만 없애는 것**이다 — 아래 결정 2를 본다.

## 결정

### 1. 찜(Favorite) 조합 규칙: `<module>.domain`은 다른 BC를 모른다

`show.domain`은 찜을 알지 못한다. `Show`가 `List<ShowLike>`를 필드로 갖거나, show의 도메인
서비스가 `ShowLikeRepository`를 주입받는 것은 금지한다. 대신 **`show.application`이 favorite의
공개 API(interface + 불변 record)를 주입받아 응답을 조합한다.**

```java
// 금지 — show.domain이 favorite를 안다
class Show {
    private List<ShowLike> likes;   // ✗
}

// 허용 — show.application이 favorite의 공개 계약으로 조합한다
class GetShowDetailUseCase {
    private final ShowLikeQuery showLikeQuery;   // favorite의 공개 API
    ShowDetailView handle(long showId) {
        ShowDetailView local = showDetailReadRepository.findShowDetail(showId);
        long likeCount = showLikeQuery.countByShowId(showId);
        return local.withLikeCount(likeCount);
    }
}
```

`show -> favorite` 의존은 허용한다. 사용자의 표현을 그대로 옮기면: "이 정도 의존성은 괜찮다.
중요한 건 어디에서 의존하느냐다." 별도 `composition` module이나 별도 count API로 우회하는
안은 검토했으나 철회했다 — 계약이 늘어나는 비용에 비해 얻는 것이 없다. 이 규칙은 당시
`com.ticket.show.domain.ShowDomainPurityTest`(ArchUnit)가 강제했다:

```java
noClasses().that().resideInAPackage("com.ticket.show.domain..")
    .should().dependOnClassesThat().resideInAPackage("com.ticket.favorite..")
```

> **2026-09-08 갱신**: 이 원칙을 6개 BC 전체로 일반화한 `com.ticket.DomainPurityTest`가
> `ShowDomainPurityTest`를 대체했다 — `show.domain`뿐 아니라 `booking`/`venue`/`favorite`/
> `member`/`payment`의 domain도 같은 규칙을 받는다.

### 2. 순환은 흡수가 아니라 한 방향을 없애 해소한다

ADR 0003 §11이 만난 순환의 두 방향 중 **`favorite -> show`(공연 존재 확인, 표시값 조회) 방향만
show 쪽 로컬 조회로 되돌린다.**

- 찜 추가·삭제·상태 조회: `ShowLookup.requireExisting(showId)` 호출 대신 show의 use case가 자기
  `ShowRepository.existsById(showId)`로 직접 확인한다.
- 내 찜 목록: favorite가 `(likeId, showId, likedAt)`만 반환하고, show의 use case가 그 showId
  목록으로 자기 read repository를 다시 조회해 표시값(title/image/venue 등)을 합친다.

남는 것은 `show -> favorite`(찜 개수 조회, 찜 use case의 위임) 한 방향뿐이라 순환이 생기지 않는다.
그 결과 `favorite`는 **업무 module 의존이 하나도 없는 leaf**가 된다(`favorite`의
`allowedDependencies = {}`). 회원 활성 확인(`MemberLookup.requireActive`)은 여전히 show의 use
case가 하므로 `favorite -> member` 엣지도 생기지 않는다.

### 3. Venue BC 분리

물리 공연장·좌석(Venue/Seat/Region)을 `venue` module로 옮긴다. `venue`도 `favorite`와 같은 leaf다
(`allowedDependencies = {}`, payment와 같은 형태) — `show -> venue`(공연장 표시값 조립, region 검색
조건 해석, 좌석 주소·좌석 배치 조회) 한 방향만 있다.

**`Region` enum은 "module root에는 interface + record만" 규칙의 유일한 예외다.** 불변 값 타입이고
show가 검색 파라미터·응답 필드로 그대로 쓰는 공용 어휘라, 복제하면 원본이 둘이 된다. 대신 값
타입이라 module 결합을 늘리지 않는다.

**패턴 1 — 표시값 decorate.** `show`의 Querydsl read repository는 `show.venueId`(scalar 컬럼)만
select하고, 그 결과를 조립하던 클래스가 `VenueLookup.getSummaries(Set<Long>)`를 배치 호출해
공연장 이름·주소 같은 표시값을 채운다. `show/application/support/VenueDisplays`가 그 batch 결과와
null-safe 접근을 한 곳에 모은다 — dangling venueId(참조하는 Venue가 없는 경우)는 어디서든 "venue
없는 show"와 같은 결과(표시값 null, 좌석 빈 목록)를 낸다.

**패턴 2 — region 검색 조건.** `QuerydslShowPredicates.regionEq(Region)`을
`venueIdIn(Set<Long>)`으로 바꾸고, region → venueId 해석은 `QuerydslShowConditionBuilder` 한 곳이
`VenueLookup.findIdsByRegion(region)`으로 한다. 빈 집합이어도 Querydsl이 `1 = 2`로 안전하게
직렬화하므로 별도 null 분기가 필요 없다.

**booking은 영향받지 않는다.** booking이 쓰는 `PerformanceSaleCatalog`/`PerformanceVenueLayoutCatalog`
공개 계약은 show가 façade로 그대로 유지하고, 내부 구현만 venue의 공개 계약(`VenueLookup`/
`VenueSeatLookup`)을 호출하도록 바뀐다. `booking -> venue` 엣지는 생기지 않는다. `ShowVenueLayoutController`
(공연장 배치 조회, `/api/v1/shows/{showId}/venue-layout`)는 booking 데이터를 전혀 쓰지 않는
passthrough였으므로 show로 옮겼다 — 그 결과 `ShowLookup`이 완전히 사라진다(아래 §5).

### 4. Order와 Ticket은 Booking BC에 둔다 — 분리할 명확한 이유가 아직 없다

Order/OrderSeat/Ticket을 별도 BC로 나누는 안도 검토했지만, 나눌 근거(다른 팀이 소유한다, 다른
배포 주기를 갖는다, 다른 방향의 확장이 예상된다)가 지금은 없다. 미래에 경계가 갈라진다면 그 선은
Order/Ticket 사이보다 **좌석 재고(PerformanceSeat/Hold/Selection)와 주문 사이**일 가능성이 더
높다는 관측만 남긴다 — 지금 그 방향으로 미리 나누지 않는다.

### 5. `ShowLookup`은 사라진다

이 재편으로 `ShowLookup`의 네 메서드가 모두 없어진다 — 찜 use case의 `requireExisting`/
`getSummaries`는 각각 show 내부 `ShowRepository.existsById`/read repository 조회로 대체됐고
(§2), `getVenueLayout`은 그 컨트롤러(`ShowVenueLayoutController`)가 show로 이동하며 소멸했으며
(§3), `getPerformanceSummaries`는 소비자가 없어 `PerformanceSummary`와 함께 삭제됐다.

### 6. Member BC는 지금 분리하지 않는다

`member` module 파일의 다수가 회원(Member entity)이 아니라 인증·인가(oauth2/token/password/security
filter chain)다. `Member` entity 자체도 신원(email, name) + 자격증명(encodedPassword) +
인가(role) + 탈퇴 생명주기를 겸한다. 사용자 BC 목록이 "Member BC = member"이므로 **이번에는
나누지 않는다.** 다음 분리 후보는 `auth`(Identity/Access) module이며, 그때 `AuthenticatedMember`/
`AccessTokenAuthenticator`/`SecurityConfig`가 `auth`로, `MemberLookup`/`MemberProfile`만 `member`에
남는다. 코드 변경 없음 — 이 절은 관측 기록이다.

## 승인된 의존 DAG (2026-09-07)

순환 없음: `venue`·`favorite`가 leaf, `show`가 둘을 참조, `booking`이 그 위에 얹힌다. 의존 DAG의
원본은 `com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`와 `docs/architecture.md`다.

## 되돌린 것 (ADR 0003·0005와의 관계)

- **ADR 0003 §11 "showlike 흡수"를 되돌린다.** 흡수 자체가 틀린 결정은 아니었다 — 당시 순환을
  해소하는 유일한 실용적 방법이었다. 이번엔 순환의 두 방향 중 하나만 없애는 더 정밀한 해법이
  가능해져(§2) 다시 분리했다. §11 본문은 그 이전 결정의 기록으로 남긴다.
- **ADR 0003 §3 / ADR 0005 §4의 module set·DAG**를 이 ADR의 "승인된 의존 DAG"가 다시 supersede한다.
- **ADR 0003 §6**이 남겼던 gap("`CursorPage`를 `catalog` 하나만 쓴다")은 해소됐다 — 지금은
  `show`(목록·내 찜)와 `favorite`(`findLikedShows` 반환) 둘이 쓴다.
- `docs/agents/domain.md`의 "module ≠ BC" 전제를 "module = BC(기술 모듈 제외), 단일 CONTEXT.md
  유지"로 바꾼다.

## 비용

- 목록·상세·판매·좌석배치 조회 12개 경로가 venue 표시값을 배치로 다시 조회한다 — 목록·상세는
  페이지당 +1 쿼리, 주문 생성 경로는 +1 쿼리(3→4). 부하 기준선과 비교가 필요하다(`/loadtest`).
- Flyway 이력이 `catalog`/`show`(개명)·신설 `venue`/`favorite` 이름으로 재시작된다 — 옮기는
  migration 전부를 멱등화하고 W0~W3를 하나의 push로 배포해야 한다(아래 "Flyway 이력 재시작"
  참고).

## Flyway 이력 재시작과 멱등화 예외

module-aware Flyway(`SpringModulithFlywayMigrationStrategy`)는 module 식별자마다 독립된
`flyway_schema_history_{module}` 이력을 갖는다. `catalog` → `show` 개명, `venue`/`favorite` 신설은
모두 **새 이력 이름**이므로 그 폴더의 migration 전부가 처음부터 다시 실행된다.
`docs/operations.md`의 "이미 운영에 적용된 migration 파일은 수정하지 않는다" 원칙에 대한 예외를
여기 명문화한다: **module 개명·분리로 이력이 재시작될 때는, 새 이력으로 옮겨가는 파일에 한해
멱등화 수정(존재 확인 가드)을 허용한다.** 이미 적용이 끝나 그대로 남는 이력의 파일은 여전히
고치지 않는다.

이번에 멱등화한 파일: `venue/V1__add_seat_venue_relationship.sql`(옛 catalog/show V3),
`show/V4__create_grade_and_performance_grade.sql`. 신설한 FK 정리 migration: `favorite/V1`·`V2`
(SHOW_LIKES의 옛 member/show FK), `show/V8`(SHOWS의 옛 venue FK) — 전부 컬럼·제약을 이름과
무관하게 동적으로 찾아 없을 때는 no-op이다.

## 결정하지 않는 것 (별도 결정으로 미룸)

Performance 책임 혼재("Show가 예매·대기열 정책까지 겸한다")는 **A2로 결정·구현됐다** — 판매·
대기열 정책의 소유권을 Booking BC의 `PerformanceSalesPolicy`로 이관했다(이 문서 상단의
"2026-09-07 갱신" 문단 참고). 현재 배포된 조회 endpoint는
`GET /api/v1/booking/performances/{id}/booking-mode`다.

### 그 밖의 후속

- `auth` module 분리(Member BC에서 인증·인가 분리, §6).
- `shared.UuidSupplier`가 "둘 이상의 독립 module" 기준에 못 미침(member 하나만 사용).
- not-found가 전부 공통 E404로 뭉뚱그려져 있음(show 없음/회차 없음/회차의 Venue 없음이 같은
  코드), 도메인 불변식이 공통 `InvalidRequestException`을 던짐 — ADR 0002 의도대로 필요해질 때
  모듈 예외로 좁힌다.
- `PerformanceSaleSnapshot`/`PerformanceVenueLayout`은 Venue+Show 데이터가 섞인 show façade로
  남는다. booking이 두 BC를 직접 조합하는 방식은 후속 선택지다.
- `payment -> booking` 엣지는 PG 연동 구현 시점에 추가한다(ADR 0005 유지).
