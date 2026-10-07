# 용어집

공연·전시 티켓 예매 서비스의 도메인 용어집이다. 좌석을 고르고 잡아두고 주문으로 넘기는 과정의
말을 여기서 정한다. 대기열 자체의 운영은 형제 저장소 `ticket-queue`가 맡는다. 용어 정의는 구현 완료를
뜻하지 않는다. 이 문서는 용어만 다룬다.

## Language

### 상품

**Venue**:
실제 좌석 배치 하나를 가진 개별 홀이다. 한글로는 공연장이라 부른다. 복합 문화시설 전체가 아니라 그 안의
특정 홀 하나를 가리킨다. Venue 1개는 Seat 여러 개를 가지며(`1:0..N`), Seat는 정확히 하나의 Venue에 속한다.
_Avoid_: 복합 시설 전체를 가리키는 서술

**Show**:
판매 단위가 되는 작품이다. 공연 기간과 Venue, 여러 Genre를 가진다. Show 1개는 Performance
여러 개를 가지며(`1:0..N`), Performance는 정확히 하나의 Show에 속한다. 좌석 편성·등급·가격은
회차(Performance)마다 다를 수 있어 Show가 아니라 그 단위로 붙는다.

Show의 판매 표시는 화면용이며 실제 주문 접수 판단은 회차별 `PerformanceSalesPolicy`가 한다.
_Avoid_: 공연물, Event, Product, `ShowGrade`, Show가 판매 가능 여부를 판단한다는 서술

**Category / Genre**:
장르 분류 체계다. Category 1개는 Genre 여러 개를 가지며(`1:0..N`), Genre는 정확히 하나의
Category에 속한다. Show와 Genre는 서로 여러 개를 가질 수 있어 `N:M`이다.
_Avoid_: 태그, Tag

**Performance**:
Show의 특정 상영 회차다. 회차 번호와 시작 시각을 가지며, 좌석 편성·등급·가격은 이 단위로
붙는다. 예매 기간·Hold 좌석 수 한도·대기열 진입 여부는 Performance 자신이 아니라
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
원본(source of truth)이다.
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
주문 시점의 좌석·등급·가격 snapshot을 보존한다.
_Avoid_: 결제 전 좌석을 Ticket이라 부르는 서술(주문 상세 응답의 `TicketResponse`는 OrderSeat 목록을 담는 응답 항목
이름일 뿐 Ticket과 다른 것이다)

**Selection**:
회원이 좌석을 살펴보며 임시로 골라 둔 표시다. 짧은 시간만 유지되고 다른 회원 화면에는 점유로
보이지만, 예매를 보장하지 않는다. Hold와 따로 저장되지만 Order는 본인이 선택 중인 좌석으로만 만들 수
있다. 한 회원이 동시에 선택할 수 있는 좌석 수는
회차의 선점 한도를 따른다.
_Avoid_: 선점, 임시 예약, Reservation, 찜(Like와 혼동)

**Hold**:
진행 중인 Order가 좌석을 붙잡아 둔 상태다. 판매 정합성을 지키는 쪽은 Selection이 아니라 이것이다.
Order와 1:1이며 같은 holdKey로 이어지고, Order가 살아 있는 동안만 유지된다. 한글로는 선점이라 부른다.
_Avoid_: Lock, Reservation

**점유(Occupancy)**:
선택 중이거나 선점된 좌석을 합쳐 부르는 말이다. 화면에서 막히는 좌석이고 좌석 상태 응답의 `OCCUPIED`다. DB의
판매 상태(`PerformanceSeat.state`)는 포함하지 않는다. 무엇을 점유로 볼지는 `SeatOccupancy` 한 곳이 정한다.

**holdKey / orderKey**:
holdKey는 선점 한 건의 식별자로 주문과 선점을 잇는다. 선점이 TTL로 만료되면 holdKey로 주문을 찾아 만료시킨다.
orderKey는 외부에 보이는 주문 번호로 URL에 쓰이고, 내부 숫자 id를 드러내지 않는다.

**선점 이력(HoldHistory)**:
선점은 Redis에서 사라지므로 좌석마다 언제 선점됐고 왜 풀렸는지를 DB에 남기는 기록이다.

**선점 한도**:
한 회원이 한 회차에서 동시에 잡을 수 있는 좌석 수 상한(`max_hold_seat_count`)이다. 이름은 선점 한도지만 좌석
선택 개수에도 같은 값을 적용한다. null이면 제한하지 않는다.

**PerformanceSalesPolicy**:
회차 하나의 예매 기간·선점 한도·대기열 정책을 갖는 개념이다. 예매 가능 여부
(BEFORE_OPEN/OPEN/CLOSED)와 대기열 필요 여부를 스스로 판정하며, 없는 회차는 판매 정책이 아직
구성되지 않았다는 뜻이다.
_Avoid_: 이 정책을 Show가 갖는다는 서술, BookingPolicySnapshot

**예매 기간(BookingWindow)**:
새 좌석 선택·주문 생성을 시작할 수 있는 기간(예매 오픈~마감)이다. 이미 만든 주문의 결제 기한(`expiresAt`)과는
다르다. 마감 직전에 만든 주문의 `expiresAt`을 예매 마감으로 잘라내지 않는다.
_Avoid_: 접수 기간, Show의 표시용 판매 기간과 같은 것처럼 다루는 서술

**대기열 정책(QueuePolicy)**:
회차가 대기열을 거쳐야 하는지 정하는 규칙이다. `QueueMode`가 `FORCE_ON`이면 항상, `FORCE_OFF`면 절대 쓰지
않고, `AUTO`면 사전 대기열 시작 시각부터 예매 마감까지만 쓴다.
_Avoid_: 대기열 진입 정책

**진입 방식(EntryMode)**:
프론트에 회차가 바로 예매(`DIRECT`)인지 대기열을 거치는지(`QUEUE`) 알려 주는 안내용 값이다. 실제 검사는 좌석 상태·
선택·주문 생성 앞의 예매 진입 검사(`BookingEntryGuard`)가 매번 다시 한다. 응답 JSON 필드는 호환을 위해 `bookingMode`다.
_Avoid_: 예매 방식

**표시용 판매 정보**:
Show의 `DisplaySaleWindow`·`SaleDisplayStatus`처럼 목록·상세 화면에만 보여 주는 판매 기간·상태다. 실제 예매 가능
여부와 다를 수 있고 그것은 결함이 아니다. 실제 판단은 PerformanceSalesPolicy가 한다.

**Admission**:
대기열을 통과해 예매 API를 호출할 자격이다. `ticket-queue`가 토큰으로 발급하고 이 서비스가 검증한다.
진입 방식(EntryMode)·예매 진입 검사는 이 자격을 요구할지 정하고 확인하는 쪽이고, Admission 자체가 아니다.
_Avoid_: 입장권, Admission을 Entry로 부르는 것, Ticket

**Payment**:
Order에 대한 한 번의 결제 시도다. Order 하나에는 Payment 여러 건이 있을 수 있고(`1:0..N`), 한
건이 실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있다. Payment 실패는 Order를 끝내는
사건이 아니다.
_Avoid_: 결제(Payment 자체가 결제 완료가 아니라 시도라는 사실을 흐린다), 결제내역이 Order와 1:1이라는 전제

**Ticket**:
결제 성공으로 확정된 OrderSeat에 대해 발급되는 입장 권리다. OrderSeat는 결제 전에는 Ticket이
없고, 발급 후에는 최대 하나만 가진다(`1:0..1`). Member 1명은 Ticket 여러 장을 가질 수 있다
(`1:0..N`). Admission(대기열 통과 자격)과는 다른 개념이다.
_Avoid_: 입장권과 Admission을 같은 뜻으로 혼용

### 예매 흐름

**주문 생성**:
고른 좌석으로 PENDING 주문을 만들고 좌석을 선점하는 한 번의 요청(`POST /orders`, `CreateOrderUseCase`)이다.
커밋되면 `OrderCreated` 이벤트가 나간다.
_Avoid_: 예매 시작, 주문 시작

**PENDING 주문(결제 대기 주문)**:
만들어졌지만 아직 결제되지 않은 주문이다. 결제 기한(`expiresAt`)이 지나면 만료되고, 회원은 회차당 하나만 가질 수 있다.

**주문 종료**:
취소와 만료를 합쳐 부르는 말이다. 둘 다 PENDING 주문을 끝내고 선점을 푸는 같은 뒤처리를 하며
`OrderTerminated` 이벤트로 이어진다. 결제 확정은 포함하지 않는다.

**보상 해제**:
주문 생성 중 Redis 선점은 만들었는데 DB 저장이 실패하면, 만든 선점을 되돌려 푸는 것이다.

**커밋 후 처리**:
DB 커밋 뒤에 따로 하는 Redis·WebSocket 작업(선택 정리, 선점 해제, 좌석 상태 알림)이다. DB 연결을 쥔 채 외부
I/O를 하지 않으려고 커밋 앞뒤를 나눈다.

**누락 복구 / 재처리**:
누락 복구는 원래 경로(Redis TTL 알림, 커밋 후 리스너)가 놓친 일을 주기 작업이 뒤늦게 다시 하는 것이다(만료되지 않은
PENDING 주문 만료 등). 재처리는 실패로 남은 이벤트 publication을 다시 처리하도록 던지는 것이다.
_Avoid_: 보정, 재제출

**멱등 상태 알림**:
좌석 상태 WebSocket 알림(`SELECTED`/`DESELECTED`/`HELD`/`RELEASED`)은 "좌석을 이 상태로 맞춰라"로 읽는다. 같은
알림이 두 번 와도 결과가 같으므로 중복은 허용하고 누락만 피한다.

**Snapshot**:
어느 시점의 값을 복사해 고정한 것이다. 네 가지로 쓴다. (1) 가격 체인 `PerformanceGrade.price` →
`PerformanceSeat.unitPrice` → `OrderSeat.unitPrice`, (2) 주문에 저장한 공연·공연장 이름과 좌석 라벨 같은 표시 snapshot,
(3) 다른 모듈에 entity 대신 내보내는 `*Snapshot` 값, (4) 트랜잭션 안에서 읽어 밖으로 들고 나가는 값(`OrderHoldSnapshot`).

**entity-only 단계**:
테이블·entity·Repository만 있고 그걸 쓰는 업무 흐름은 아직 없는 상태다. 지금 해당하는 모듈은 없다. `payment`와 Ticket은 entity 없이 테이블(`PAYMENTS`, `TICKETS`)만 남겼다.

### 찜

**Like**:
회원이 어떤 대상을 찜한 기록이다. `(memberId, likeType, targetId)` 조합이 유일하며, 같은 회원이
같은 대상을 두 번 찜할 수 없다. 대상 종류는 `LikeType`으로 값화돼 있고 지금은 SHOW(공연) 하나뿐
이다. Selection(예매 중 임시 좌석 선택)과는 다르다.
화면과 API 문서에서는 좋아요라고도 쓴다.
_Avoid_: Selection과 혼동, ShowLike(현재 명칭은 Like)

### 회원

**Member**:
서비스에 가입한 사용자다. 소셜 로그인(Google·Kakao)으로만 만들어진다. Member 1명은 Order
여러 건(`1:0..N`)과 Ticket 여러 장(`1:0..N`)을 가질 수 있으며, 탈퇴해도 Order/Ticket은 삭제하지
않는다.

_Avoid_: 사용자, 고객, User, Customer, Account

**SocialIdentity**:
외부 provider의 응답 형식을 제거한 정규화된 소셜 신원이다. provider, provider 사용자 ID, 이메일,
이메일 검증 여부, 이름으로 이루어진다. provider 응답을 이 형태로 해석하는 일은 security가 하고,
이 값으로 계정을 찾거나 만드는 일은 member가 한다. 연결된 계정 기록인
`MemberSocialAccount`와 구분한다. 기존 계정 연결에는 검증된 이메일만 쓴다.
_Avoid_: provider 응답 타입이나 연결된 소셜 계정 기록과 혼동
