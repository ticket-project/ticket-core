# Ticket

공연·전시 티켓 예매 서비스의 도메인 용어집이다. 좌석을 고르고 잡아두고 주문으로 넘기는 과정의
말을 여기서 정한다. 대기열 자체의 운영은 형제 저장소 `ticket-queue`가 맡는다.

모듈 경계와 코드 배치 기준은 `docs/architecture.md`에 있다. 이 문서는 용어만 다룬다.

## Language

### 상품

**Show**:
판매 단위가 되는 작품이다. 공연 기간과 장소, 장르를 가진다. **등급별 가격은 Show가 직접 갖지
않는다** — 좌석 편성·등급·가격은 회차(Performance)마다 다를 수 있어 Grade/PerformanceGrade가
회차 단위로 소유한다(설계 배경은 [ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)).
Show 상세의 가격 표시는 PerformanceGrade에서 파생한 요약값(예: minPrice/maxPrice)이며, 이 요약을
만드는 원본 테이블(과거 `ShowGrade`)은 폐기 대상이다 — 아직 코드가 이 폐기를 반영하지 않았다면
그것은 진행 중인 재설계 구현의 미완료 상태이지 현재 사실이 아니다.
_Avoid_: 공연물, Event, Product, ShowGrade(폐기 대상, Show 단위 가격이라는 개념 자체를 쓰지 않는다)

**Performance**:
Show의 특정 상영 회차다. 회차 번호와 시작 시각을 가지며, 좌석과 예매 정책은 이 단위로 붙는다.
_Avoid_: 회차 공연, Schedule, Session

**Seat**:
공연장의 물리적 좌석이다. 구역·열·번호로 식별되며 회차와 무관하게 존재한다.
_Avoid_: 자리

**Grade**:
`VIP`, `R`, `S`, `A` 같은 좌석 등급의 재사용 가능한 코드와 이름이다. 가격을 갖지 않는다 — 같은
Grade라도 회차마다 가격이 다를 수 있기 때문이다.
_Avoid_: 좌석 등급 자체에 가격이 고정된 것처럼 다루는 서술

**PerformanceGrade**:
특정 Performance에서 사용할 Grade다. 회차별 가격과 표시 순서를 가진다. Grade와 Performance의
다대다 관계를 표현하는 단순 join table이 아니라 그 자체로 업무 속성(가격, 표시 순서)을 가진
연결 entity다.
_Avoid_: ShowGrade(Show 단위 가격 개념은 폐기 대상)

**PerformanceSeat**:
특정 Performance의 특정 Seat이다. 그 회차에서의 판매 상태와, 어느 PerformanceGrade에 속하는지,
그 좌석의 확정 가격(`unitPrice`, 판매 좌석 생성 시 PerformanceGrade.price를 snapshot한 값)을
가진다.
_Avoid_: 회차석, SeatInstance, ShowSeat(Show 단위 좌석 편성 개념은 폐기 대상)

### 예매

**Order**:
회원이 좌석을 고르고 결제 화면으로 넘어갈 때 만들어지는 예매 건이다. **결제 전에 생성되며**,
정해진 시간 안에 결제되지 않으면 만료된다.
_Avoid_: 예약, 구매, Reservation, Purchase, Booking

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
Order에 대한 한 번의 결제 시도다. Order 하나에는 여러 Payment가 있을 수 있고(`1:0..N`), Payment
한 건이 실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있다. **Payment 실패는 Order를 끝내는
사건이 아니다** — Order의 결제 실패 종결 상태(`PAYMENT_FAILED`)는 이 이유로 폐기 대상이다. 설계
배경은 [ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)를
본다.
_Avoid_: 결제, 결제내역이 Order와 1:1이라는 전제

**Ticket**:
결제 성공으로 확정된 OrderSeat에 대해 발급되는 입장 권리다. OrderSeat는 결제 전에는 Ticket이
없고, 발급 후에는 최대 하나만 가진다(`1:0..1`). Admission(대기열 통과 자격)과는 다른 개념이다 —
혼동해 같은 말로 쓰지 않는다.
_Avoid_: 입장권과 Admission을 같은 뜻으로 혼용

### 회원

**Member**:
서비스에 가입한 사용자다. 이메일 가입과 소셜 로그인 두 경로로 만들어진다.
_Avoid_: 사용자, 고객, User, Customer, Account
