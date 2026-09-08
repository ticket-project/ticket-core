# Ticket

공연·전시 티켓 예매 서비스의 도메인 용어집이다. 좌석을 고르고 잡아두고 주문으로 넘기는 과정의
말을 여기서 정한다. 대기열 자체의 운영은 형제 저장소 `ticket-queue`가 맡는다.

Bounded Context·Aggregate 경계, 모듈 경계와 코드 배치 기준은 전부
[docs/architecture.md](docs/architecture.md)에 있다. 이 문서는 용어만 다룬다.

## Language

### 상품

**Venue**:
실제 좌석 배치 하나를 가진 개별 홀이다. 복합 문화시설 전체가 아니라 그 안의 특정 홀 하나를
가리킨다. Venue 1개는 Seat 여러 개를 가지며(`1:0..N`), Seat는 정확히 하나의 Venue에 속한다.
_Avoid_: 공연장(Show를 가리키는 말과 혼동), 복합 시설 전체를 가리키는 서술

**Show**:
판매 단위가 되는 작품이다. 공연 기간과 Venue, 여러 Genre를 가진다. Show 1개는 Performance
여러 개를 가지며(`1:0..N`), Performance는 정확히 하나의 Show에 속한다. 좌석 편성·등급·가격은
회차(Performance)마다 다를 수 있어 Show가 아니라 그 단위로 붙는다.
_Avoid_: 공연물, Event, Product, `ShowGrade`(쓰지 않는다 — Show 단위 가격이라는 개념 자체를
쓰지 않는다)

**Category / Genre**:
장르 분류 체계다. Category 1개는 Genre 여러 개를 가지며(`1:0..N`), Genre는 정확히 하나의
Category에 속한다. Show와 Genre는 서로 여러 개를 가질 수 있어 `N:M`이다.
_Avoid_: 태그, Tag

**Performance**:
Show의 특정 상영 회차다. 회차 번호와 시작 시각을 가지며, 좌석 편성·등급·가격은 이 단위로
붙는다. 예매 접수 기간·Hold 좌석 수 한도·대기열 진입 여부는 Performance 자신이 아니라
PerformanceSalesPolicy가 별도로 갖는다.
_Avoid_: 회차 공연, Schedule, Session

**Seat**:
Venue 안의 물리적 좌석이다. `(venue, floor, section, row, seatNo)`로 식별되며 회차와 무관하게
존재한다. 같은 Venue 안에서는 이 조합이 유일해야 하지만, 다른 Venue라면 같은 좌석 주소 표기가
허용된다.
_Avoid_: 자리, 회차와 무관하게 판매 가능한 단위(그 개념은 PerformanceSeat)

**Grade**:
`VIP`, `R`, `S`, `A` 같은 좌석 등급의 재사용 가능한 코드와 이름이다. 가격을 갖지 않는다 — 같은
Grade라도 회차마다 가격이 다를 수 있기 때문이다. Grade와 Performance는 `N:M`이다.
_Avoid_: 좌석 등급 자체에 가격이 고정된 것처럼 다루는 서술

**PerformanceGrade**:
특정 Performance에서 사용할 Grade다. 회차별 가격과 표시 순서를 가진 연결 개념이며, 가격의
원본(source of truth)이다. 판매 오픈 전에만 가격을 바꿀 수 있다는 것이 업무 규칙이다.
_Avoid_: `ShowGrade`(쓰지 않는다 — Show 단위 가격이라는 개념 자체를 쓰지 않는다)

**PerformanceSeat**:
특정 Performance에서 판매하는 특정 Seat다. Performance와 Seat는 `N:M`이며 PerformanceSeat가
그 연결을 담당한다. 그 회차에서의 판매 상태와, 정확히 하나의 PerformanceGrade에 속한다는 사실
(`1:0..N`), 생성 시점에 스냅샷되어 이후 불변인 확정 가격을 가진다. 클라이언트와 OrderSeat가
다루는 실제 판매 단위다.
_Avoid_: 회차석, SeatInstance, `ShowSeat`(쓰지 않는다 — Show 단위 좌석 편성 개념 자체를 쓰지
않는다)

### 예매

**Order**:
회원이 좌석을 고르고 결제 화면으로 넘어갈 때 만들어지는 예매 건이다. 결제 전에 생성되며,
정해진 시간 안에 결제되지 않으면 만료된다. Member 1명은 Order 여러 건을 가지며(`1:0..N`),
하나의 Order는 OrderSeat 1개 이상을 가진다(`1:1..N`, 빈 주문은 없다). 좌석·등급·가격 표시값은
OrderSeat가 주문 시점 스냅샷으로 보존하므로, 이후 원본이 바뀌어도 바뀌지 않는다.
_Avoid_: 예약, 구매, Reservation, Purchase, Booking

**OrderSeat**:
Order에 포함된 한 좌석이다. 정확히 하나의 PerformanceSeat를 가리키지만, 하나의 PerformanceSeat는
취소·만료된 주문도 이력으로 남기기 때문에 시간에 따라 여러 OrderSeat와 연결될 수 있다(`1:0..N`).
같은 PerformanceSeat를 두 Order가 동시에 확정할 수 없다는 보장은 PerformanceSeat의 상태 전이가 맡는다.
_Avoid_: TicketInfo(결제 전 좌석을 이 이름으로 부르지 않는다)

**Selection**:
회원이 좌석을 살펴보며 임시로 골라 둔 표시다. 짧은 시간만 유지되고 다른 회원 화면에는 점유로
보이지만, 예매를 보장하지 않는다. Hold와 독립이며 Selection 없이도 Order를 만들 수 있다.
_Avoid_: 선점, 임시 예약, Reservation, 찜(ShowLike와 혼동)

**Hold**:
진행 중인 Order가 좌석을 붙잡아 둔 상태다. 판매 정합성을 지키는 쪽은 Selection이 아니라 이것이다.
Order와 1:1이며 같은 holdKey로 이어지고, Order가 살아 있는 동안만 유지된다.
_Avoid_: 점유, Lock, Reservation

**PerformanceSalesPolicy**:
회차 하나의 예매 접수 기간·Hold 좌석 수 한도·대기열 진입 정책을 갖는 개념이다. 예매 가능 여부
(BEFORE_OPEN/OPEN/CLOSED)와 대기열 필요 여부를 스스로 판정하며, 없는 회차는 판매 정책이 아직
구성되지 않았다는 뜻이다.
_Avoid_: 이 정책을 Show가 갖는다는 서술, BookingPolicySnapshot

**Admission**:
대기열을 통과해 예매 API를 호출할 자격이다. `ticket-queue`가 토큰으로 발급하고 이 서비스가 검증한다.
_Avoid_: 입장권, Entry, Ticket

**Payment**:
Order에 대한 한 번의 결제 시도다. Order 하나에는 Payment 여러 건이 있을 수 있고(`1:0..N`), 한
건이 실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있다. Payment 실패는 Order를 끝내는
사건이 아니다 — Order 상태는 `PENDING -> CONFIRMED/EXPIRED/CANCELED`뿐이다.
_Avoid_: 결제(Payment 자체가 결제 완료가 아니라 시도라는 사실을 흐린다), 결제내역이 Order와 1:1이라는 전제

**Ticket**:
결제 성공으로 확정된 OrderSeat에 대해 발급되는 입장 권리다. OrderSeat는 결제 전에는 Ticket이
없고, 발급 후에는 최대 하나만 가진다(`1:0..1`). Member 1명은 Ticket 여러 장을 가질 수 있다
(`1:0..N`). Admission(대기열 통과 자격)과는 다른 개념이다.
_Avoid_: 입장권과 Admission을 같은 뜻으로 혼용

### 찜

**ShowLike**:
회원이 특정 Show를 찜한 기록이다. `(memberId, showId)` 쌍이 유일하며, 같은 회원이 같은 Show를
두 번 찜할 수 없다.
_Avoid_: 좋아요(도메인 용어는 찜으로 통일), Selection과 혼동

### 회원

**Member**:
서비스에 가입한 사용자다. 이메일 가입과 소셜 로그인 두 경로로 만들어진다. Member 1명은 Order
여러 건(`1:0..N`)과 Ticket 여러 장(`1:0..N`)을 가질 수 있으며, 탈퇴해도 Order/Ticket은 삭제하지
않는다.
_Avoid_: 사용자, 고객, User, Customer, Account
