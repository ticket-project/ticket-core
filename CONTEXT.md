# Ticket

공연·전시 티켓 예매 서비스의 도메인 용어집이다. 좌석을 고르고 잡아두고 주문으로 넘기는 과정의
말을 여기서 정한다. 대기열 자체의 운영은 형제 저장소 `ticket-queue`가 맡는다.

모듈 경계와 코드 배치 기준은 `docs/architecture.md`에 있다. 이 문서는 용어만 다룬다.

## 컨텍스트 맵

Application Module(기술 모듈 제외)은 곧 Bounded Context(BC)다([ADR 0006](docs/adr/0006-bounded-context-module-boundaries.md)).
여섯 개 BC와 그 의존 방향:

```text
Show -> Venue, Favorite, Member
Booking -> Show, Member
Payment -> (없음, 후속에서 Booking을 참조 예정)
Venue / Favorite / Member -> (없음, leaf)
```

- **Venue**: Venue, Seat. 물리 시설.
- **Show**: Show, Category, Genre, Performer, Performance, Grade, PerformanceGrade. 작품·회차.
- **Booking**: Selection, Hold, Order, OrderSeat, Ticket, PerformanceSalesPolicy(회차 예매 접수
  기간·Hold 한도·대기열 진입 정책). 좌석 선점부터 주문·발권까지.
- **Payment**: Payment. 결제 시도.
- **Favorite**: ShowLike. 찜.
- **Member**: Member. 회원과 인증.

## Aggregate 경계

BC 안에서 **함께 저장되고 함께 불변식을 지키는 단위**가 Aggregate다. 각 root와 그 안에 사는 것은
아래와 같다.

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
| ShowLike | — | Favorite |
| Member | MemberSocialAccount | Member |

나누는 기준은 두 가지다.

- **자식이 부모 없이 존재할 수 없으면 같은 aggregate다.** OrderSeat는 Order 없이, MemberSocialAccount는
  Member 없이, PerformanceGrade는 Performance 없이 의미가 없다.
- **수가 많거나 독립적으로 경합하면 분리한다.** Venue와 Seat, Performance와 PerformanceSeat가 그
  예다. 공연장 하나에 좌석이 수천 개라 한 aggregate로 묶으면 로딩과 락 범위가 함께 커지고, 좌석 한
  자리를 파는 데 회차 전체가 잠긴다. PerformanceSeat가 좌석 단위 동시 확정을 낙관적 락으로 막을 수
  있는 것도 분리돼 있기 때문이다.

**aggregate 사이는 식별자로 참조한다.** 다른 aggregate를 객체로 붙잡고 있으면 한 트랜잭션에서 둘을
같이 고치는 코드가 쉽게 써지기 때문이다. 어떤 경계에서 어떤 매핑을 쓰는지(같은 aggregate 안, 같은
BC 다른 aggregate, 다른 BC, 조회 전용)는 구현 규칙이라
[architecture.md](docs/architecture.md)의 "모듈 간 참조 규칙"이 원본이다.

**Hold와 Selection은 DB aggregate가 아니다.** 둘 다 Redis에만 있는 짧은 수명 상태이고, `Hold`는 JPA
매핑이 없는 값이며 Selection은 도메인 클래스조차 없다. DB에 남는 것은 좌석 단위 이력인
`HoldHistory`뿐이고 `holdKey` 문자열로 Order와 이어진다. 그래서 좌석의 현재 상태는 한 곳에서 읽을
수 없고 `PerformanceSeat`(DB)와 Selection·Hold(Redis)를 합쳐 계산한다.

## Language

### 상품

**Venue**:
실제 좌석 배치 하나를 가진 개별 홀이다. 복합 문화시설 전체가 아니라 그 안의 특정 홀 하나를
가리킨다(예: 예술의전당이라는 시설 안의 콘서트홀과 CJ 토월극장은 서로 다른 Venue row다).
좌석 지도 렌더링에 쓰는 배치 메타데이터(`viewBoxWidth`/`viewBoxHeight`/`seatDiameter` 등)를
직접 갖는다. Venue 1개는 Seat 여러 개를 가지며(`1:0..N`), Seat는 정확히 하나의 Venue에 속한다 —
Seat.venue는 필수 관계다.
_Avoid_: 공연장(Show를 가리키는 말과 혼동), 복합 시설 전체를 가리키는 서술

**Show**:
판매 단위가 되는 작품이다. 공연 기간과 Venue, 장르(Genre, `ShowGenre`로 다대다 연결)를 가진다.
Show 1개는 Performance 여러 개를 가지며(`1:0..N`), Performance는 정확히 하나의 Show에 속한다.
**등급별 가격은 Show가 직접 갖지 않는다** — 좌석 편성·등급·가격은 회차(Performance)마다 다를 수
있어 Grade/PerformanceGrade가 회차 단위로 소유한다(설계 배경은
[ADR 0005](docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)). Show
상세의 가격 표시는 PerformanceGrade에서 파생한 요약값(minPrice/maxPrice)이며, 이 요약을 만들던
원본 테이블 `ShowGrade`는 제거됐다.
_Avoid_: 공연물, Event, Product, `ShowGrade`(제거됨 — Show 단위 가격이라는 개념 자체를 쓰지 않는다)

**Category / Genre**:
장르 분류 체계다. Category 1개는 Genre 여러 개를 가지며(`1:0..N`), Genre는 정확히 하나의
Category에 속한다. Show는 여러 Genre를 가질 수 있고 Genre도 여러 Show에 쓰이므로 Show-Genre는
`N:M`이며 `ShowGenre`가 그 연결을 담당한다.
_Avoid_: 태그, Tag

**Performance**:
Show의 특정 상영 회차다. 회차 번호와 시작 시각을 가지며, 좌석 편성·등급·가격은 이 단위로 붙는다.
Performance 자신은 회차 정체성과 일정(startTime/endTime)만 소유한다. 예매 접수 기간·Hold
한도·대기열 진입 정책은 **Booking BC의 `PerformanceSalesPolicy`**가 소유한다 — 실제 소비자가
거의 전부 Booking(admission 검사, hold 시간 계산)이었기 때문에 원본과 판단을 그쪽으로 이관했다
([ADR 0006](docs/adr/0006-bounded-context-module-boundaries.md) "Performance의 책임 혼재" A2,
2026-09-07 이후 구현). `PerformanceSalesPolicy`는 `performanceId` scalar로만 Performance와
연결되고 cross-module JPA 연관관계나 DB FK는 없다.
_Avoid_: 회차 공연, Schedule, Session

**Seat**:
Venue 안의 물리적 좌석이다. `(venue, floor, section, row, seatNo)`로 식별되며 회차와 무관하게
존재한다. 같은 Venue 안에서는 이 조합이 유일해야 하지만, 다른 Venue라면 같은 좌석 주소 표기가
허용된다.
_Avoid_: 자리, 회차와 무관하게 판매 가능한 단위(그 개념은 PerformanceSeat)

**Grade**:
`VIP`, `R`, `S`, `A` 같은 좌석 등급의 재사용 가능한 코드와 이름이다. 가격을 갖지 않는다 — 같은
Grade라도 회차마다 가격이 다를 수 있기 때문이다. Grade와 Performance는 `N:M`이며
`PerformanceGrade`가 그 연결을 담당한다.
_Avoid_: 좌석 등급 자체에 가격이 고정된 것처럼 다루는 서술

**PerformanceGrade**:
특정 Performance에서 사용할 Grade다. 회차별 가격과 표시 순서를 가진다. Grade와 Performance의
다대다 관계를 표현하는 단순 join table이 아니라 그 자체로 업무 속성(가격, 표시 순서)을 가진
연결 entity이며, 가격의 원본(source of truth)이다. "판매 오픈 전에만 가격을 바꿀 수 있다"가 업무
규칙이지만 **지금 코드가 강제하지는 않는다** — 가격을 바꾸는 메서드 자체가 없어 어길 수단이 없을
뿐이고, 강제하려 해도 판단 근거(접수 시각·좌석 편성 여부)가 Booking BC에 있어 Show가 혼자 판정할 수
없다. 상세와 선택지는 [기술 부채 TD-15](docs/technical-debt.md)를 본다.
_Avoid_: `ShowGrade`(제거됨 — Show 단위 가격 개념 자체를 쓰지 않는다)

**PerformanceSeat**:
특정 Performance에서 판매하는 특정 Seat다. Performance와 Seat는 `N:M`이며 PerformanceSeat가 그
연결을 담당한다. 그 회차에서의 판매 상태와, 정확히 하나의 PerformanceGrade에 속한다는 사실
(`1:0..N`, PerformanceGrade 1개에 PerformanceSeat 여러 개), 그 좌석의 확정 가격(`unitPrice`,
판매 좌석 생성 시 PerformanceGrade.price를 snapshot한 값이며 생성 후 불변)을 가진다. 클라이언트와
OrderSeat가 다루는 실제 판매 단위다.
_Avoid_: 회차석, SeatInstance, `ShowSeat`(제거됨 — Show 단위 좌석 편성 개념 자체를 쓰지 않는다)

### 예매

**Order**:
회원이 좌석을 고르고 결제 화면으로 넘어갈 때 만들어지는 예매 건이다. **결제 전에 생성되며**,
정해진 시간 안에 결제되지 않으면 만료된다. Member 1명은 Order 여러 건을 가지며(`1:0..N`), 하나의
Order는 OrderSeat 1개 이상을 가진다(`1:1..N`, 빈 주문은 없다). show/performance/venue 표시값과
좌석·등급·가격 표시값은 OrderSeat가 주문 시점 snapshot으로 보존하므로, Show 쪽 표시 이름이나
가격이 나중에 바뀌어도 이미 만들어진 Order/OrderSeat 상세는 바뀌지 않는다. Order와 Ticket을 별도
BC로 나눌 명확한 이유가 아직 없어 Ticket과 함께 Booking BC에 둔다 — 미래에 경계가 갈라진다면 그
선은 Order/Ticket 사이보다 좌석 재고(PerformanceSeat/Hold/Selection)와 주문 사이일 가능성이 더
높다는 관측만 남긴다([ADR 0006](docs/adr/0006-bounded-context-module-boundaries.md)).
_Avoid_: 예약, 구매, Reservation, Purchase, Booking

**OrderSeat**:
Order에 포함된 한 좌석이다. 정확히 하나의 PerformanceSeat를 가리키지만, 하나의 PerformanceSeat는
시간에 따라 여러 OrderSeat와 연결될 수 있다(`1:0..N`) — 취소·만료된 주문도 지우지 않고 이력으로
남기기 때문이다. 최종 판매 확정(같은 PerformanceSeat를 두 Order가 동시에 확정할 수 없음)은
PerformanceSeat의 상태 전이가 보장하지, OrderSeat row 존재만으로는 보장하지 않는다.
_Avoid_: TicketInfo(결제 전 좌석을 이 이름으로 부르지 않는다)

**Selection**:
회원이 좌석을 살펴보며 임시로 골라 둔 표시다. 짧은 시간만 유지되고 다른 회원 화면에는 점유로
보이지만, **예매를 보장하지 않는다.** Hold와 독립이며 Selection 없이도 Order를 만들 수 있다.
_Avoid_: 선점, 임시 예약, Reservation, 찜(Favorite BC의 ShowLike와 혼동)

**Hold**:
진행 중인 Order가 좌석을 붙잡아 둔 상태다. 판매 정합성을 지키는 쪽은 Selection이 아니라 이것이다.
Order와 1:1이며 같은 `holdKey`로 이어지고, Order가 살아 있는 동안만 유지된다.
_Avoid_: 점유, Lock, Reservation

**PerformanceSalesPolicy**:
회차 하나의 예매 접수 기간(OrderAcceptanceWindow)·Hold 좌석 수 한도(HoldPolicy)·대기열 진입
정책(BookingEntryPolicy)을 소유하는 Booking BC aggregate다. Performance(Show BC)에 대해
`performanceId` scalar 식별자로만 연결되며, 예매 가능 여부(BEFORE_OPEN/OPEN/CLOSED)와 대기열
필요 여부를 스스로 판정한다. 이 정책이 없는 회차는 "회차는 있지만 Booking 판매 정책이 아직
구성되지 않음"을 뜻한다.
_Avoid_: 이 정책을 Show가 소유한다는 서술(과거 구조), BookingPolicySnapshot(제거됨)

**Admission**:
대기열을 통과해 예매 API를 호출할 자격이다. `ticket-queue`가 토큰으로 발급하고 이 서비스가 검증한다.
_Avoid_: 입장권, Entry, Ticket

**Payment**:
Order에 대한 한 번의 결제 시도다. Order 하나에는 Payment 여러 건이 있을 수 있고(`1:0..N`), Payment
한 건이 실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있다. **Payment 실패는 Order를 끝내는
사건이 아니다** — Order 상태는 `PENDING -> CONFIRMED/EXPIRED/CANCELED`뿐이고, 결제 실패로 Order를
끝내던 `PAYMENT_FAILED` 상태는 제거됐다. `payment`는 다른 업무 모듈을 참조하지 않는 독립
Application Module이며, 이번 구현 범위는 결제 시도 entity/schema/repository까지다 — PG 연동,
승인/실패/취소 API, callback/webhook은 아직 없다. 설계 배경은
[ADR 0005](docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)를 본다.
_Avoid_: 결제(Payment 자체가 결제 완료가 아니라 시도라는 사실을 흐린다), 결제내역이 Order와 1:1이라는 전제

**Ticket**:
결제 성공으로 확정된 OrderSeat에 대해 발급되는 입장 권리다. OrderSeat는 결제 전에는 Ticket이
없고, 발급 후에는 최대 하나만 가진다(`1:0..1`). Member 1명은 Ticket 여러 장을 가질 수 있다
(`1:0..N`). Admission(대기열 통과 자격)과는 다른 개념이다 — 혼동해 같은 말로 쓰지 않는다.
Ticket은 `booking` module이 소유한다(ADR 0005가 신설한 별도 `ticketing` module은 booking으로 흡수됐다).
이번 구현 범위는 entity/schema/repository까지다 — 자동 발급 listener, QR, 입장, 사용, 취소, 양도는 아직 없다.
_Avoid_: 입장권과 Admission을 같은 뜻으로 혼용

### 찜

**ShowLike**:
회원이 특정 Show를 찜한 기록이다. `(memberId, showId)` 쌍이 유일하며, 같은 회원이 같은 Show를 두
번 찜할 수 없다. 좋아요 개수·찜 추가/해제·내 찜 목록 전부 Favorite BC(`favorite` module)가
소유하고, Show 존재 확인이나 표시값(제목·이미지 등) 조립은 하지 않는다 — 그 조합은 Show BC의
조회 서비스가 Favorite의 공개 API를 호출해 한다([ADR 0006](docs/adr/0006-bounded-context-module-boundaries.md)).
찜 HTTP endpoint(`/api/v1/likes`, `/api/v1/members/me/likes`)는 Show BC에 그대로 있다.
_Avoid_: 좋아요(API 필드명 `likeCount`와는 별개로 도메인 용어는 찜으로 통일), Selection과 혼동

### 회원

**Member**:
서비스에 가입한 사용자다. 이메일 가입과 소셜 로그인 두 경로로 만들어진다. Member 1명은 Order
여러 건(`1:0..N`)과 Ticket 여러 장(`1:0..N`)을 가질 수 있으며, 탈퇴해도 Order/Ticket은 삭제하지
않는다.
_Avoid_: 사용자, 고객, User, Customer, Account
