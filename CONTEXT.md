# Ticket

공연·전시 티켓 예매 서비스의 도메인 용어집이다. 좌석을 고르고 잡아두고 주문으로 넘기는 과정의
말을 여기서 정한다. 대기열 자체의 운영은 형제 저장소 `ticket-queue`가 맡는다.

모듈 경계와 코드 배치 기준은 `docs/architecture.md`에 있다. 이 문서는 용어만 다룬다.

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
Show의 특정 상영 회차다. 회차 번호와 시작 시각을 가지며, 좌석 편성·등급·가격·예매 정책은 이
단위로 붙는다.
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
연결 entity이며, 가격의 원본(source of truth)이다. 판매 오픈 전에만 가격을 바꿀 수 있다.
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
좌석·등급·가격 표시값은 OrderSeat가 주문 시점 snapshot으로 보존하므로, catalog 쪽 표시 이름이나
가격이 나중에 바뀌어도 이미 만들어진 Order/OrderSeat 상세는 바뀌지 않는다.
_Avoid_: 예약, 구매, Reservation, Purchase, Booking

**OrderSeat**:
Order에 포함된 한 좌석이다. 정확히 하나의 PerformanceSeat를 가리키지만, 하나의 PerformanceSeat는
시간에 따라 여러 OrderSeat와 연결될 수 있다(`1:0..N`) — 취소·만료된 주문도 지우지 않고 이력으로
남기기 때문이다. 최종 판매 확정(같은 PerformanceSeat를 두 Order가 동시에 확정할 수 없음)은
PerformanceSeat의 상태 전이가 보장하지, OrderSeat row 존재만으로는 보장하지 않는다.
_Avoid_: TicketInfo(결제 전 좌석을 이 이름으로 부르지 않는다)

**Selection**:
회원이 좌석을 살펴보며 임시로 찜해 둔 표시다. 짧은 시간만 유지되고 다른 회원 화면에는 점유로
보이지만, **예매를 보장하지 않는다.** Hold와 독립이며 Selection 없이도 Order를 만들 수 있다.
_Avoid_: 선점, 임시 예약, Reservation

**Hold**:
진행 중인 Order가 좌석을 붙잡아 둔 상태다. 판매 정합성을 지키는 쪽은 Selection이 아니라 이것이다.
Order와 1:1이며 같은 `holdKey`로 이어지고, Order가 살아 있는 동안만 유지된다.
_Avoid_: 점유, Lock, Reservation

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

### 회원

**Member**:
서비스에 가입한 사용자다. 이메일 가입과 소셜 로그인 두 경로로 만들어진다. Member 1명은 Order
여러 건(`1:0..N`)과 Ticket 여러 장(`1:0..N`)을 가질 수 있으며, 탈퇴해도 Order/Ticket은 삭제하지
않는다.
_Avoid_: 사용자, 고객, User, Customer, Account
