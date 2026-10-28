# Core 예매 수명주기

이 문서는 주문 생성, 취소, 만료와 Redis hold 후처리의 실행 순서를 설명한다. 핵심 목적은 DB
트랜잭션과 외부 I/O의 경계, 그리고 Spring Modulith 이벤트가 어떻게 이어지는지 한눈에 확인하는
것이다.

**구현 범위**: `payment` module과 booking의 `Ticket`은 entity/schema/repository까지만 있는 entity-only
단계다. PG 승인, `OrderConfirmed` listener, 결제 정산 서비스는 아직 없고 `Order.confirm()`을 호출하는 곳도
없다. 그래서 `PENDING` 주문은 만료(`ExpireOrderUseCase`) 또는 취소(`CancelOrderUseCase`)로만 종료된다. 아래
수명주기는 지금 실제로 동작하는 PENDING 생성·취소·만료 경로만 설명한다. 결제 승인·재시도·Hold 만료 경쟁 정책은
아직 설계되지 않았다.

## 지켜야 할 원칙

- Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다. HTTP 요청에서는
  `spring.jpa.open-in-view: false`가 이를 보장한다 — 켜 두면 요청이 첫 DB 접근부터 응답 끝까지
  connection을 쥔다.
- 좌석 상태(선택·선점)를 바꾸는 일은 Redis Lua 스크립트 하나에서 확인·변경·알림 발행까지 한다. 좌석 분산 락을
  쓰지 않는다(2026-10-28, [좌석 분산 락 다시 설계하기](plans/seat-lock-redesign.md) C안).
- 주문이 살아 있는지는 상태(`status`)가 아니라 만료 시각(`expires_at`)으로 정한다. DB 상태를 `EXPIRED`로
  바꾸는 일은 청소가 하고, 주문을 쓰는 곳은 만료 시각을 직접 비교한다(아래 "주문 만료와 좌석 선택 만료").
- 주문 저장 또는 상태 변경과 그에 대응하는 이벤트 발행은 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 담당하고, 실패는 catch-and-log로 삼키지 않고
  throw해 Event Publication Registry가 FAILED로 기록하고 재시도하게 한다.
- 예매 정책 조회(booking-local `PerformanceSalesPolicy`), show 표시값 조회, member 회원 확인,
  admission token 검증은 주문 쓰기 트랜잭션 밖, `BookingPreflightReader`의 읽기 전용 트랜잭션 하나 안에서 끝낸다.
- 주문 금액은 오직 `PerformanceSeat.unitPrice`로만 계산한다. 클라이언트가 보낸 가격도, show가 다시 계산한
  가격도 금액 계산 근거로 쓰지 않는다.
- Order/OrderSeat에 남긴 표시 snapshot(공연명·회차 시작 시각·공연장 이름, 등급 코드·이름, 좌석 라벨,
  가격)은 생성 이후 다시 조회하지 않는다. show 쪽 표시값이나 가격이 나중에 바뀌어도 이미 만든
  주문 상세는 바뀌지 않는다.
- 판매 정책은 회차별로 1초, show의 회차 단위 표시값(공연명·회차 시작 시각·공연장 이름·등급)은 회차별로
  10분 재사용한다(`shared.config.CacheConfig`). 좌석 라벨과 금액은 매번 계산한다. 그래서 정책 변경은 서버마다
  최대 1초 늦게 반영되고, 주문에 남기는 회차 단위 표시값은 최대 10분 지난 값일 수 있다. 등급별 잔여석
  조회(`GetSeatAvailabilityUseCase`)의 등급 코드·이름·가격·순서도 같은 10분 캐시 값이다.

## 주문 생성

~~~text
CreateOrderUseCase          (POST /api/v1/orders)
  -> BookingPreflightReader: 아래 확인과 조회를 짧은 read 트랜잭션 하나(connection 한 번)로 끝낸다
     -> admission BookingEntryGuard.check: 예매 정책 조회, 예매 기간 확인, 대기열 필요 회차만 token 검증
        (`security.admission.enforcement-enabled`가 켜진 경우에만, [운영](operations.md#admission-token-검증))
        — 좌석 선택·좌석 상태 조회도 같은 진입 검사를 거친다
     -> PerformanceSalesPolicy.ensureWithinHoldLimit: 요청 좌석 수가 hold 상한 이내인지 확인
     -> member MemberLookupApi: active member 확인
     -> pending 주문 중복, 좌석 판매 상태 확인
     -> show PerformanceSaleInfoApi: 요청 좌석의 표시 snapshot(등급 코드/이름, 좌석 라벨,
        공연명·회차 시작 시각·공연장 이름) 조회 — 가격 자체는 이 snapshot이 아니라 아래 PerformanceSeat에서 온다
     (같은 회원·회차의 PENDING 때문에 E5004로 막혔는데 그 주문이 만료 시각을 지났으면, 그 자리에서 EXPIRED로
      바꾸고 이 확인을 한 번 더 한다)
  -> HoldRegistry로 Redis 좌석 hold 생성. Lua 한 번으로 요청 좌석이 모두 본인 selection인지 확인하고, 좌석 키가
     하나도 없을 때만 좌석 키·점유 인덱스·메타를 쓴다. 본인 selection이 아니면 E4006이고, 선택 시간이 지나 풀린
     것인지(E4007)는 실패했을 때만 선택 기록을 따로 읽어 가른다
  -> PendingOrderCreator
       -> 주문 aggregate 조립. 총액은 Order.addOrderSeat가 좌석
          단가를 누적해 만든다 = Σ PerformanceSeat.unitPrice (다른 값을 섞지 않는다)
       -> PENDING 주문·OrderSeat·hold history 저장     (booking DB 트랜잭션)
            Order에는 show/performance/venue 표시 snapshot을, OrderSeat에는 등급·좌석 라벨·unitPrice
            snapshot을 함께 저장한다 — 둘 다 생성 후 불변이다.
       -> 같은 트랜잭션 안에서 OrderCreated 이벤트 발행
       -> 트랜잭션이 돌려주는 것은 orderKey뿐이다(entity를 트랜잭션 밖으로 내보내지 않는다)
  -> (이 구간이 실패하면 CreateOrderUseCase가 위에서 만든 Redis hold를 보상 해제한다.
      해제 실패는 원인 예외에 suppressed로 붙이고 다시 던지지 않는다)
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderCreated)   (@ApplicationModuleListener, 커밋 후, 트랜잭션 없음)
       -> OrderHoldSnapshotReader: orderId로 필요한 값만 짧은 읽기 트랜잭션에서 완성
          (OrderSeat의 좌석 식별자 매핑도 담는다. 이후 Redis 작업은 DB를 다시 읽지 않는다)
       -> HeldSeatPublisher → Lua 한 번:
            -> 모든 좌석이 아직 이 holdKey로 잡혀 있을 때만 아래를 진행(아니면 건너뜀)
            -> 주문 회원 소유 selection만 해제
            -> 좌석마다 HELD를 relay 채널에 PUBLISH → 각 인스턴스가 받아 WebSocket으로 보낸다
~~~

같은 회원·회차의 주문 생성은 락으로 줄 세우지 않는다(2026-10-19까지는 `ORDER_CREATE` 락이 흐름 전체를
감쌌다). "회원·회차당 PENDING 주문 하나"는 DB 유일 인덱스 `uk_orders_pending_member_performance`가 지키고,
같은 회원이 동시에 보낸 두 번째 요청은 어디서 부딪히든 `E5004`를 받는다 — 진행 중 주문 확인, 같은 좌석
hold(선점 메타에 회원 ID를 두어 같은 회원의 hold를 알아본다), 앞 주문 후처리가 지운 selection(커밋 뒤에만
지우므로 진행 중 주문을 다시 확인한다), 다른 좌석이면 주문 저장의 유일 제약(hold를 보상 해제한다).
앞 요청이 hold 뒤 주문 저장에서 실패하는 드문 경우, 그 사이의 두 번째 요청도 `E5004`를 받는다.
자세한 근거는 `CreateOrderUseCase` 주석에 있다.

좌석 분산 락은 없다. 본인 selection 확인과 hold 생성, 선택과 선점 확인, 해제의 소유권 확인은 모두 Redis Lua
스크립트 하나 안에서 한다. Redis가 스크립트를 하나씩 실행하므로 그 사이에 다른 상태 변경이 끼지 못한다.
DB 저장이 실패하면 `CreateOrderUseCase`가 이미 만든 Redis hold를 알림 없이 보상 해제한다(좌석마다 값이 이
holdKey일 때만 지운다).

현재 동시성 방어는 Redis 스크립트의 원자성과 `BookingPreflightReader`의 좌석 판매 상태 확인이다.
Redis hold는 Lua 스크립트 한 번으로 좌석 키가 하나도 없을 때만 모두 저장해 다른 hold를 덮어쓰지 않는다.
충돌하면 아무것도 쓰지 않고 `E6000`(409)으로 거절한다(충돌한 hold가 같은 회원 것이면 `E5004`).
`PerformanceSeat`는 `@Version`(낙관적 락)만 갖고 상태를 바꾸는 메서드가 없다 — 결제 승인 시점에
`PerformanceSeat`를 `RESERVED`로 전이하는 흐름이 아직 없다([#219](https://github.com/ticket-project/ticket-core/issues/219)). 지금은 Redis hold가 실제 점유의 기준이고, `PerformanceSeat.state`는 판매 좌석 편성(`AVAILABLE`)을
나타낼 뿐 주문 확정으로 바뀌지 않는다.

커밋 이후 리스너 실행이 실패하면 Order/OrderSeat/HoldHistory는 그대로 커밋된 상태로 남고,
`OrderCreated` publication은 Event Publication Registry에 FAILED로 남는다.
`EventPublicationMaintenance`가 실패 publication을 재처리한다(아래 "이벤트 재시도와 누락 복구").
따라서 프로세스가 재시작되거나 즉시 처리가 실패해도 후속 처리 입력은 DB(publication row)에
남는다. hold가 실제 점유의 기준이다. selection은 판매 정합성을 지키지 않지만 주문의 전제 조건이다 —
본인이 선택 중인 좌석으로만 주문을 시작할 수 있다.

주문 생성, 주문 상세, 주문 상태 응답의 시간 계약은 동일하다. `expiresAt`은 서버의 절대
만료 시각이고, `remainingSeconds`는 응답을 만드는 서버 시각부터 `expiresAt`까지 남은
완전한 초다. PENDING이 아니거나 이미 만료 경계를 지났으면 0이다. 만료 시각이 지난 PENDING은 청소가 DB를
바꾸기 전이어도 상태를 `EXPIRED`로 응답한다. 클라이언트는 로컬
시계로 `expiresAt - now`를 다시 계산하지 않고 `remainingSeconds`로 카운트다운을 시작한 뒤,
상태 조회 응답으로 주기적으로 다시 맞춘다.

## 좌석 선택과 해제 알림 순서

좌석 상태(Redis)를 바꾸는 일과 좌석 상태 알림은 **같은 Lua 스크립트 안에서** 함께 한다. 알림은 relay
채널(`seat:status:relay`) 하나에 `PUBLISH`하고, 모든 인스턴스가 그 채널을 구독해 자기 STOMP 사용자에게
보낸다(`SeatStatusRelay`, 단일 스레드 전달). 발행한 인스턴스도 직접 보내지 않고 구독으로 받는다. Redis가
스크립트를 하나씩 실행하고 `PUBLISH`를 처리한 순서대로 보내므로, 서버 대수와 무관하게 알림 순서가 상태가 바뀐
순서와 같다. 확인과 발행이 따로면 이런 역전이 생긴다 — A의 선택이 TTL로 만료되고 B가 같은 좌석을 다시 선택한
뒤 A의 만료 알림이 뒤늦게 실행되면, B의 `SELECTED` 뒤에 A의 `DESELECTED`가 나가 이미 B가 잡은 좌석이 비어
보인다.

- 선택 — 선점 키가 있으면 `E6000`, 남이 선택 중이면 `E4001`. 선택 기록·만료 장부 등록·`SELECTED` 발행을 함께 한다.
- 해제 — 본인 선택을 지우고, **실제로 지웠고 hold가 없는 경우에만** `DESELECTED`를 알린다. 이미 만료된
  선택이나 주문 생성 후 HELD로 넘어간 좌석에 `DESELECTED`를 보내지 않는다.
- 선택 만료 알림 — 선택도 선점도 없는 좌석일 때만 `DESELECTED`를 보낸다. 그 사이 남이 다시 고르거나 선점한
  좌석은 알리지 않는다(아래 "주문 만료와 좌석 선택 만료").

선택은 Redis에 좌석 키와 인덱스 두 개(회차 전체, 회원별)로 기록된다. 인덱스 score는 만료 시각이라
좌석 키가 TTL로 사라지면 인덱스에서도 같은 시각에 빠진다. 선택할 때 회원별 인덱스로 선택 좌석 수를
세어, 회차 선점 한도(`max_hold_seat_count`)에 이미 닿았으면 `E6001`로 거절한다 — 좌석 확인·한도
확인·기록이 Lua 한 번이라 같은 회원의 동시 선택도 한도를 넘지 않는다. 한도가 없는(null) 회차는 제한하지
않는다. 전체 해제(`DELETE /seats/select`)도 회원별 인덱스로 그 회원의 좌석만 돈다. 회원별 인덱스는
만료된 선택을 10분 더 남겨 주문이 "선택 시간 만료(E4007)"를 알아보게 한다 — 한도와 활성 조회는 만료
시각이 지나지 않은 것만 센다.

**서버가 보장하는 것은 발행 순서까지다.** 클라이언트 수신 순서는 WebSocket 전송 계층의 문제다. Redis
pub/sub은 최대 한 번 전달이라 구독이 끊긴 동안의 알림은 다시 오지 않는다. 그래서 좌석 상태 이벤트는 계속 "그 좌석을 특정 상태로 맞추는" 멱등 알림으로
취급하고, 클라이언트는 필요하면 좌석 상태 조회로 다시 맞춘다. payload(`performanceSeatId` + 물리
`seatId`)는 바뀌지 않았다.

## 결제 시도와 Order 상태

`Order`의 상태 전이는 `PENDING -> CONFIRMED`, `PENDING -> EXPIRED`, `PENDING -> CANCELED` 세 가지뿐이다
(`booking.order.domain.OrderState`). 결제 실패 상태는 없다(hold 해제 사유를 기록하는 별개의 enum
`HoldReleaseReason`도 `TTL_EXPIRED`·`USER_CANCELED` 두 가지뿐이다).

결제 실패를 Order 상태로 두지 않는 이유는 Payment가 결제 완료 건이 아니라 **결제 시도**이기 때문이다(`Order 1 : 0..N Payment`). 결제 시도 한 번이
실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있어야 하므로, 결제 실패는 Payment 자신의 상태
(`READY/PROCESSING -> FAILED`)로만 표현하고 Order를 끝내는 사건으로 취급하지 않는다.

## 주문 취소와 만료

취소는 소유권과 현재 상태를 검증한다. 만료 시각이 지난 PENDING은 이미 끝난 주문이라 취소하지 않고 `E5002`로
거절한다. 만료는 청소가 PENDING 주문을 하나씩 잠근다(아래).
이후 공통 절차는 `OrderTerminationService`가 담당한다. `CancelOrderUseCase.execute`가 짧은 쓰기
트랜잭션 안에서 주문 조회·소유권과 상태 검증·취소를 수행한다.

~~~text
CancelOrderUseCase / ExpireOrderUseCase
  -> PENDING 주문 row lock
  -> OrderTerminationService
       -> CANCELED 또는 EXPIRED 전이
       -> hold history 저장
       -> 같은 트랜잭션 안에서 OrderTerminated 이벤트 발행
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderTerminated) (@ApplicationModuleListener, 커밋 후, 트랜잭션 없음)
       -> OrderHoldSnapshotReader: orderId로 필요한 값만 짧은 읽기 트랜잭션에서 완성
          (seatId→performanceSeatId 매핑도 담아 Redis 작업에서 DB를 재조회하지 않는다)
       -> HoldReleaser → Lua 한 번:
            -> 좌석별 현재 holdKey가 일치하는 hold만 해제(compare-and-delete), meta 삭제
            -> 현재 hold/selection이 없는 좌석만 RELEASED를 relay 채널에 PUBLISH
~~~

같은 `OrderTerminated` publication이 재시도로 다시 전달되면 Redis 해제도 다시 수행한다. 해제는
좌석별 현재 holdKey를 확인하고 일치할 때만 지우므로, 이미 풀린 좌석은 그대로 지나가고 그사이
다른 사용자가 잡은 선점도 건드리지 않는다. 그래서 해제 완료 여부를 따로 기록하지 않는다.
해제 호출자는 hold의 전체 좌석을 넘긴다. meta key(`hold:key:%s`)는 값으로 선점한 회원 ID를 저장한다. 선점 Lua가 이 값을 읽어 충돌한 hold가 같은 회원 것인지(`E5004`) 판정한다. 좌석 해제 후 해당 meta를 항상 삭제한다.
발행은 같은 스크립트 안에서 현재 hold/selection 상태를 확인해, 새 hold나 selection이 생긴 좌석에는 오래된
해제 알림을 보내지 않는다. hold 키가 이미 TTL로 풀린 좌석(주문 만료)도 비어 있으면 알린다. 재전달되면 같은
RELEASED가 다시 나갈 수 있다 — 이 이벤트는 좌석을 특정 상태로 맞추는 멱등 상태 알림으로 취급한다.

## 이벤트 재시도와 누락 복구

`BookingEventListeners`의 실패는 Spring Modulith의 JPA Event Publication Registry가 관리한다.
`EventPublicationMaintenance`가 실패 publication을 제한된 횟수로 재처리한다. 상한을 넘긴 건은 자동 처리에서 빠져 수동
조사가 필요하다. 동일 이벤트 재전달은 예상 경로이고, 그래서 후속 처리는 멱등하게 짠다(위 "주문 취소와 만료"의
재전달 설명).

탈퇴 회원 WebSocket 강제 종료와 `MemberWithdrawn` 이벤트는 제거했다. access token 인증은 회원 DB를
보지 않으므로 탈퇴 후에도 토큰 만료 전 재접속이 가능하며, 주문 생성은 별도로 활성 회원을 확인한다.
과거 `com.ticket.member.api.MemberWithdrawn` publication이 남아 있다면 배포 전 확인이 필요하다.
일반 `AFTER_COMMIT` 리스너도 registry 저장 대상이며, JPA publication의 `eventType`은 `Class<?>`로
읽힌다. 삭제된 클래스의 미완료 행을 조회하면 Hibernate 클래스 로딩에서 실패해 다른 이벤트의 재처리도
막힐 수 있다. 운영 행의 존재는 별도 DB 확인이 필요하며, 이 정리 작업에서는 migration을 추가하지 않는다.

## 주문 만료와 좌석 선택 만료

Redis 키 만료 알림(keyspace notification)은 쓰지 않는다(2026-10-28, [스케일 아웃 결정 목록](plans/scale-out-decisions.md)
D2-2). 각 만료는 자기 정보가 있는 저장소에서 처리한다.

**주문 만료 — DB 청소.** `OrderExpirationTrigger`(기본 5초, `worker.order-expiration.fixed-delay`) →
`ExpirePendingOrdersUseCase`가 만료 시각이 지난 PENDING 주문을 `EXPIRED`로 바꾼다. 주문 만료의 유일한 경로다.

- 모든 인스턴스가 함께 돈다. 주문 한 건마다 트랜잭션을 열어 `FOR UPDATE SKIP LOCKED`로 다른 인스턴스가 잠그지
  않은 만료 주문 하나를 잡는다(`OrderRepository.findNextExpirableForUpdateSkipLocked`). 같은 주문을 두 번 처리하지
  않고 서로 기다리지 않으며, 인스턴스 하나가 죽으면 DB가 롤백하며 잠금을 풀어 남은 인스턴스가 잡는다.
- 실패한 주문은 롤백으로 다시 첫 대상이 되므로 id 커서로 지나가 뒤의 주문을 처리하고, 다음 순회에서 다시
  시도한다. 어느 주문인지 모르는 실패(커밋 실패 등)는 그 순회를 멈춘다. 순회당 상한은 두지 않는다.
- `Order.expire(now)`는 **만료 시각이 지나기 전에는 만료시키지 않는다.**
- 좌석은 hold 키 TTL로 만료 시각에 풀린다. 청소가 늦어 생기는 것은 `EXPIRED` 반영과 `RELEASED` 알림의 지연뿐이다.
  주문이 살아 있는지는 만료 시각으로 판단하므로(주문 취소·상태 조회, 그리고 같은 회원의 새 주문이 막혔을 때의
  즉시 만료) 판매 정합성은 청소 주기와 무관하다. 결제를 붙이면 결제 확정도 만료 시각을 비교한다.
- 지표: `booking_order_expiration_delay_seconds`(만료 시각 뒤 청소가 잡기까지), `booking_order_expiration_failures_total`.

**좌석 선택 만료 — Redis 장부.** 선택권은 선택 키 TTL(5분)이 푼다. 다른 사용자 화면에 "좌석 비었음"을 알리는
일만 따로 한다.

- 좌석 선택 스크립트가 선택과 함께 전역 장부 `seat:select:expiry`(값 "회차:좌석", 점수 Redis 시각 기준 만료
  시각)에 적는다. 같은 좌석을 다시 고르면 점수만 바뀐다.
- `SeatSelectionExpirationTrigger`(기본 1초, `worker.seat-selection-expiration.fixed-delay`) →
  `NotifyExpiredSelectionsUseCase`가 시각이 지난 항목을 스크립트 한 번으로 최대 100개 꺼내며 지우고(꽉 차면
  바로 다시), 좌석마다 "선택도 선점도 없으면 `DESELECTED`" 스크립트를 부른다. 모든 인스턴스가 돌지만 같은 항목은
  한 인스턴스만 꺼낸다.
- 해제·주문 생성 때 장부 항목은 지우지 않는다. 이미 해제된 좌석은 만료 시각에 `DESELECTED`가 한 번 더 나가고
  (화면은 같다) 선점으로 넘어갔거나 다시 선택된 좌석은 알리지 않는다. 꺼낸 뒤 알리기 전에 인스턴스가 죽으면 그
  알림은 빠진다.
- 지표: `booking_seat_selection_expiration_delay_seconds`(만료 시각 뒤 장부에서 꺼내기까지).

두 `@Scheduled` 트리거는 `worker.enabled=false`면 등록되지 않는다.
