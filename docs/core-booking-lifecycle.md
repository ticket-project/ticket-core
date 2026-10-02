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
- Redis TTL 이벤트가 한꺼번에 들어와도 DB로 진입하는 작업 수는 제한한다.
- 주문 저장 또는 상태 변경과 그에 대응하는 이벤트 발행은 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 담당하고, 실패는 catch-and-log로 삼키지 않고
  throw해 Event Publication Registry가 FAILED로 기록하고 재시도하게 한다.
- 예매 정책 조회(booking-local `PerformanceSalesPolicy`), show 표시값 조회, member 회원 확인,
  admission token 검증은 booking DB 트랜잭션 밖에서 끝낸다.
- 주문 금액은 오직 `PerformanceSeat.unitPrice`로만 계산한다. 클라이언트가 보낸 가격도, show가 다시 계산한
  가격도 금액 계산 근거로 쓰지 않는다.
- Order/OrderSeat에 남긴 표시 snapshot(show/performance/venue 이름, 등급 코드·이름, 좌석 라벨,
  가격)은 생성 이후 다시 조회하지 않는다. show 쪽 표시값이나 가격이 나중에 바뀌어도 이미 만든
  주문 상세는 바뀌지 않는다.

## 주문 생성(예매 시작)

~~~text
StartBookingUseCase          (POST /api/v1/orders)
  -> LockScope.ORDER_START 락(같은 회원·회차 직렬화, 주문 DB 커밋 뒤 해제)
  -> admission BookingEntryGuard.check: 예매 정책 조회, 예매 기간 확인, 대기열 필요 회차만 token 검증 (밖)
     — 좌석 선택·좌석 상태 조회도 같은 진입 검사를 거친다
  -> PerformanceSalesPolicy.ensureWithinHoldLimit: 요청 좌석 수가 hold 상한 이내인지 확인
  -> member MemberLookupApi: active member 확인 (밖)
  -> BookingAvailabilityChecker: pending 주문 중복, 좌석 판매 상태 (짧은 read 트랜잭션)
  -> show PerformanceSaleCatalogApi: 요청 좌석의 표시 snapshot(등급 코드/이름, 좌석 라벨,
     show/venue 이름) 조회 (밖) — 가격 자체는 이 snapshot이 아니라 아래 PerformanceSeat에서 온다
  -> LockScope.SEAT 락 안에서 요청 좌석이 모두 본인 selection인지 확인(선택 시간 만료면 E4007,
     그 밖은 E4006)하고
     HoldRegistry로 Redis 좌석 hold 생성 후 락 해제 (밖)
  -> PendingOrderCreator
       -> 주문 aggregate 조립. 총액은 Order.addOrderSeat가 좌석
          단가를 누적해 만든다 = Σ PerformanceSeat.unitPrice (다른 값을 섞지 않는다)
       -> PENDING 주문·OrderSeat·hold history 저장     (booking DB 트랜잭션)
            Order에는 show/performance/venue 표시 snapshot을, OrderSeat에는 등급·좌석 라벨·unitPrice
            snapshot을 함께 저장한다 — 둘 다 생성 후 불변이다.
       -> 같은 트랜잭션 안에서 OrderStarted 이벤트 발행
       -> 트랜잭션이 돌려주는 것은 orderKey뿐이다(entity를 트랜잭션 밖으로 내보내지 않는다)
  -> (이 구간이 실패하면 StartBookingUseCase가 위에서 만든 Redis hold를 보상 해제한다.
      해제 실패는 원인 예외에 suppressed로 붙이고 다시 던지지 않는다)
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderStarted)   (@ApplicationModuleListener, 커밋 후, 트랜잭션 없음)
       -> OrderHoldSnapshotReader: orderId로 필요한 값만 짧은 읽기 트랜잭션에서 완성
          (OrderSeat의 좌석 식별자 매핑도 담는다. 이후 좌석 락 안의 Redis·WebSocket 작업은 DB를 다시 읽지 않는다)
       -> HoldCreationCoordinator
            -> 주문 회원 소유 selection만 해제 (Redis)
            -> HELD 상태 발행             (WebSocket)
~~~

`ORDER_START` 락은 `StartBookingUseCase.execute`의 전체 시작 흐름을 감싸므로 DB 주문 생성의
커밋이 끝난 뒤 풀린다. `SEAT` 락은 Redis hold 생성 구간에서만 잡고 DB 트랜잭션 전 해제한다.
DB 저장이 실패하면 `StartBookingUseCase`가 `SEAT` 락을 다시 잡고 이미 만든 Redis hold를 보상 해제한다.
좌석 선택과 알림도 별도의 `SEAT` 락 구간에서 처리한다. 락 안의 외부 I/O는 필요한 작업으로 제한하고,
락 키를 변경할 때는 보호 대상을 검증한다.

현재 동시성 방어는 `LockScope.SEAT` 분산락과 `BookingAvailabilityChecker`의 좌석 판매 상태 확인이다.
Redis hold 좌석 키도 `SET NX`로 저장해 다른 hold를 덮어쓰지 않는다. 충돌하면 앞서 쓴 이 hold의
좌석을 보상하고 `E6000`(409)으로 거절한다.
`PerformanceSeat`는 `@Version`(낙관적 락)과 `reserve()`/`release()`를 갖지만 현재 주문 생성 경로 어디에서도
호출되지 않는다 — 결제 승인 시점에 `PerformanceSeat`를 `RESERVED`로 전이하는 정산 흐름이 아직 없다. 지금은 Redis hold가 실제 점유의 기준이고, `PerformanceSeat.state`는 판매 좌석 편성(`AVAILABLE`)을
나타낼 뿐 주문 확정으로 바뀌지 않는다.

커밋 이후 리스너 실행이 실패하면 Order/OrderSeat/HoldHistory는 그대로 커밋된 상태로 남고,
`OrderStarted` publication은 Event Publication Registry에 FAILED로 남는다.
`EventPublicationMaintenance`가 실패 publication을 재처리한다(아래 "이벤트 재시도와 누락 복구").
따라서 프로세스가 재시작되거나 즉시 처리가 실패해도 후속 처리 입력은 DB(publication row)에
남는다. hold가 실제 점유의 기준이다. selection은 판매 정합성을 지키지 않지만 주문의 전제 조건이다 —
본인이 선택 중인 좌석으로만 주문을 시작할 수 있다.

주문 시작, 주문 상세, 주문 상태 응답의 시간 계약은 동일하다. `expiresAt`은 서버의 절대
만료 시각이고, `remainingSeconds`는 응답을 만드는 서버 시각부터 `expiresAt`까지 남은
완전한 초다. PENDING이 아니거나 이미 만료 경계를 지났으면 0이다. 클라이언트는 로컬
시계로 `expiresAt - now`를 다시 계산하지 않고 `remainingSeconds`로 카운트다운을 시작한 뒤,
상태 조회 응답으로 주기적으로 다시 맞춘다.

## 좌석 선택과 해제 알림 순서

좌석 선택 상태(Redis)와 좌석 상태 알림(WebSocket)은 `SeatSelectionCoordinator`가 **같은 좌석 락
안에서** 함께 처리한다. 상태만 락 안에서 바꾸고 발행을 락 밖에서 하면 이런 역전이 생긴다 — A의
선택이 TTL로 만료되고 B가 같은 좌석을 다시 선택한 뒤 A의 만료 처리가 뒤늦게 실행되면, B의
`SELECTED` 뒤에 A의 `DESELECTED`가 나가 이미 B가 잡은 좌석이 비어 보인다.

- `select` — 락 안에서 마감 시각과 hold를 다시 확인하고, 선택 기록과 `SELECTED` 발행을 함께 한다.
- `deselect` — 락 안에서 해제하고, **실제로 해제됐고 hold가 없는 경우에만** 알린다. 이미 만료된
  선택이나 주문 시작 후 HELD로 넘어간 좌석에 `DESELECTED`를 보내지 않는다.
- `notifyReleasedIfFree` — TTL 만료와 회원의 전체 선택 해제처럼 "이미 지난 사실"을 알리는 경로다.
  발행 직전에 락 안에서 현재 선택·선점 상태를 다시 확인해, 그 사이 남이 차지한 좌석은 알리지
  않는다. 상태를 한 번 더 읽는 것만으로는 부족하고 그 재확인이 락 안에 있어야 의미가 있다.

`SeatSelectionExpirationHandler`는 Redis key 해석과 호출만 한다. 대상 좌석 확인, 현재 상태 확인,
알림 필요 여부 판단은 모두 application이 한다.

선택은 Redis에 좌석 키와 인덱스 두 개(회차 전체, 회원별)로 기록된다. 인덱스 score는 만료 시각이라
좌석 키가 TTL로 사라지면 인덱스에서도 같은 시각에 빠진다. 선택할 때 회원별 인덱스로 선택 좌석 수를
세어, 회차 선점 한도(`max_hold_seat_count`)에 이미 닿았으면 `E6001`로 거절한다 — 좌석 확인·한도
확인·기록이 Lua 한 번이라 같은 회원의 동시 선택도 한도를 넘지 않는다. 한도가 없는(null) 회차는 제한하지
않는다. 전체 해제(`DELETE /seats/select`)도 회원별 인덱스로 그 회원의 좌석만 돈다. 회원별 인덱스는
만료된 선택을 10분 더 남겨 주문이 "선택 시간 만료(E4007)"를 알아보게 한다 — 한도와 활성 조회는 만료
시각이 지나지 않은 것만 센다.

**서버가 보장하는 것은 발행 순서까지다.** 클라이언트 수신 순서는 WebSocket 전송 계층의 문제이며 이
락의 범위가 아니다. 그래서 좌석 상태 이벤트는 계속 "그 좌석을 특정 상태로 맞추는" 멱등 알림으로
취급하고, 클라이언트는 필요하면 좌석 상태 조회로 다시 맞춘다. payload(`performanceSeatId` + 물리
`seatId`)는 바뀌지 않았다.

## 결제 시도와 Order 상태

`Order`의 상태 전이는 `PENDING -> CONFIRMED`, `PENDING -> EXPIRED`, `PENDING -> CANCELED` 세 가지뿐이다
(`booking.order.domain.OrderState`). 결제 실패 상태는 없다(`HoldReleaseReason.PAYMENT_FAILED`는 hold 해제 사유를
기록하는 별개의 enum이고 Order 상태가 아니다).

결제 실패를 Order 상태로 두지 않는 이유는 Payment가 결제 완료 건이 아니라 **결제 시도**이기 때문이다(`Order 1 : 0..N Payment`). 결제 시도 한 번이
실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있어야 하므로, 결제 실패는 Payment 자신의 상태
(`READY/PROCESSING -> FAILED`)로만 표현하고 Order를 끝내는 사건으로 취급하지 않는다.

## 주문 취소와 만료

취소는 소유권과 현재 상태를 검증하고, 만료는 orderId 또는 holdKey로 PENDING 주문을 잠근다.
이후 공통 절차는 `OrderTerminationService`가 담당한다. `CancelOrderUseCase.execute`가 짧은 쓰기
트랜잭션 안에서 주문 조회·소유권과 상태 검증·취소를 수행한다.

~~~text
CancelOrderUseCase / ExpireOrderUseCase
  -> PENDING 주문 row lock
  -> OrderTerminationService
       -> 주문 좌석 검증
       -> CANCELED 또는 EXPIRED 전이
       -> hold history 저장
       -> 같은 트랜잭션 안에서 OrderTerminated 이벤트 발행
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderTerminated) (@ApplicationModuleListener, 커밋 후, 트랜잭션 없음)
       -> OrderHoldSnapshotReader: orderId로 필요한 값만 짧은 읽기 트랜잭션에서 완성
          (seatId→performanceSeatId 매핑도 담아 좌석 락 안에서 DB를 재조회하지 않는다)
       -> HoldReleaseCoordinator
            -> 좌석별 현재 holdKey를 확인하고 일치하는 hold만 해제 (Redis)
            -> 현재 hold/selection이 없는 좌석만 RELEASED 발행 (WebSocket)
~~~

같은 `OrderTerminated` publication이 재시도로 다시 전달되면 Redis 해제도 다시 수행한다. 해제는
좌석별 현재 holdKey를 확인하고 일치할 때만 지우므로, 이미 풀린 좌석은 그대로 지나가고 그사이
다른 사용자가 잡은 선점도 건드리지 않는다. 그래서 해제 완료 여부를 따로 기록하지 않는다.
해제 호출자는 hold의 전체 좌석을 넘긴다. meta key(`hold:key:%s`)는 TTL 알림용으로 holdKey 문자열만
저장하고, 좌석 해제 후 해당 meta를 항상 삭제한다. 기존 JSON meta 값은 읽지 않으므로 그대로 남아 있어도 무해하다.
WebSocket 발행은 매번 현재 hold/selection 상태를 다시 확인해, 새 hold나 selection이 생긴
좌석에는 오래된 해제 알림을 보내지 않는다. 발행 자체가 다시 실패하면 같은 RELEASED가 다시
발행될 수 있다 — 이 이벤트는 좌석을 특정 상태로 맞추는 멱등 상태 알림으로 취급하며 전달
보장은 at-least-once다. 중복보다 누락을 피하되, 매 발행 직전 현재 상태 검증으로 더 최신
상태를 덮어쓰지 않는 것이 기준이다.

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

## TTL 폭주와 누락 복구

Redis hold meta key가 만료되면 `RedisKeyExpirationListener`가 등록된 핸들러 목록에 위임하고,
`booking.hold.persistence.HoldKeyExpirationHandler`가 키를 해석해
`ExpireOrderUseCase.expireByHoldKey`를 호출한다.

- 만료 이벤트 handler는 고정 worker 2개로 DB 동시 진입을 제한한다. 무제한 큐이므로 만료 폭주 때
  대기 작업이 메모리에 쌓인다. Redisson 수신 경로도 채널별 무제한 큐를 사용해 Redis까지 역압을 전달하지 못한다.
- `OrderExpirationTrigger` → `ExpirePendingOrdersUseCase`가 누락된 만료를 복구한다.
  **id 커서로 순회한다** — 커서는 조회한 페이지의 마지막 id이고 처리 성공 여부와
  무관하게 앞으로만 가므로, 앞의 주문이 계속 실패해도 뒤의 정상 만료 대상이 같은 순회에서
  처리된다. 실패 항목은 지우지도 처리 완료로 치지도 않고 PENDING으로 남아 다음 순회에서 다시
  시도되며, 그 건수는 `Output.failedCount`와 경고 로그로 드러난다.
- `Order.expire(now)`는 **만료 시각이 지나기 전에는 만료시키지 않는다.** 아직 유효한 주문이 만료
  경로로 들어오면 그대로 EXPIRED가 되던 문제를 막는다. "지금 만료 처리 대상인가"는
  `Order.isExpirable(now)`가 답한다.

`@Scheduled` 트리거(`OrderExpirationTrigger`)는 `worker.enabled=false`면 등록되지 않는다.
