# Ticket

공연·전시 티켓 예매 서비스의 도메인 용어집이다. 좌석을 고르고 잡아두고 주문으로 넘기는 과정의
말을 여기서 정한다. 대기열 자체의 운영은 형제 저장소 `ticket-queue`가 맡는다.

모듈 경계와 코드 배치 기준은 `docs/architecture.md`에 있다. 이 문서는 용어만 다룬다.

## Language

### 상품

**Show**:
판매 단위가 되는 작품이다. 공연 기간과 장소, 장르, 등급별 가격을 가진다.
_Avoid_: 공연물, Event, Product

**Performance**:
Show의 특정 상영 회차다. 회차 번호와 시작 시각을 가지며, 좌석과 예매 정책은 이 단위로 붙는다.
_Avoid_: 회차 공연, Schedule, Session

**Seat**:
공연장의 물리적 좌석이다. 구역·열·번호로 식별되며 회차와 무관하게 존재한다.
_Avoid_: 자리

**PerformanceSeat**:
특정 Performance의 특정 Seat이다. 그 회차에서의 판매 상태와 가격을 가진다.
_Avoid_: 회차석, SeatInstance

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

### 회원

**Member**:
서비스에 가입한 사용자다. 이메일 가입과 소셜 로그인 두 경로로 만들어진다.
_Avoid_: 사용자, 고객, User, Customer, Account
