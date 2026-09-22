# Core 예매 수명주기

이 문서는 주문 생성, 취소, 만료와 Redis hold 후처리의 실행 순서를 설명한다. 핵심 목적은 DB
트랜잭션과 외부 I/O의 경계, 그리고 Spring Modulith 이벤트가 어떻게 이어지는지 한눈에 확인하는
것이다. 모듈 경계 결정 배경은 [ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)을,
가격 책임과 Payment/Order 관계의 결정 배경은
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)를 본다.

**ADR 0005 적용 범위**: `payment` module과 booking의 `Ticket`은 entity/schema/repository까지만 존재하는 entity-only
단계다(ADR 0005의 `ticketing` module은 booking으로 흡수됐다). PG 승인, `OrderConfirmed` listener, 실제 결제 정산 서비스는 아직 구현되지 않았다
— 현재 코드에서 `Order.confirm()`을 호출하는 곳은 없다(`rg -n "\.confirm\(" src/main`로 확인 가능).
아래 수명주기는 지금 실제로 동작하는 PENDING 생성·취소·만료 경로를 설명하고, 결제 확정 흐름은
아직 존재하지 않는 후속 작업임을 명시한다.

이 문서가 설명하는 코드는 대부분 `booking` Application Module 소유다(`com.ticket.booking.**`).
커밋 후 처리를 관리하는 `EventPublicationMaintenance`만 전역 배선 module
(`com.ticket.shared.config`)에 있다.

## 지켜야 할 원칙

- Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다.
- Redis TTL 이벤트가 한꺼번에 들어와도 DB로 진입하는 작업 수는 제한한다.
- 주문 저장 또는 상태 변경과 그에 대응하는 이벤트 발행은 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 담당하고, 실패는 catch-and-log로 삼키지 않고
  throw해 Event Publication Registry가 FAILED로 기록하고 재시도하게 한다.
- 예매 정책 조회(booking-local `PerformanceSalesPolicy`), show 표시값 조회, member 회원 확인,
  admission token 검증은 booking DB 트랜잭션 밖에서 끝낸다.
- 주문 금액은 오직 `PerformanceSeat.unitPrice`로만 계산한다(ADR 0005). 클라이언트가 보낸 가격도,
  show가 다시 계산한 가격도 금액 계산 근거로 쓰지 않는다.
- Order/OrderSeat에 남긴 표시 snapshot(show/performance/venue 이름, 등급 코드·이름, 좌석 라벨,
  가격)은 생성 이후 다시 조회하지 않는다. show 쪽 표시값이나 가격이 나중에 바뀌어도 이미 만든
  주문 상세는 바뀌지 않는다.

## 주문 생성(예매 시작)

~~~text
StartBookingUseCase          (POST /api/v1/orders)
  -> LockScope.ORDER_START 락(같은 회원·회차 직렬화)
  -> salespolicy PerformanceSaleFinder.requirePolicy: 예매 정책(오픈 여부, hold 상한, 대기열 필요 여부) 조회 (밖)
  -> admission AdmissionGuard.verifyIfRequired: 대기열 필요 회차만 token 검증 (밖)
  -> member MemberLookupApi: active member 확인 (밖)
  -> BookingAvailabilityChecker: pending 주문 중복, 좌석 판매 상태 (짧은 read 트랜잭션)
  -> show PerformanceSaleCatalogApi: 요청 좌석의 표시 snapshot(등급 코드/이름, 좌석 라벨,
     show/venue 이름) 조회 (밖) — 가격 자체는 이 snapshot이 아니라 아래 PerformanceSeat에서 온다
  -> LockScope.SEAT 락 안에서 HoldManager로 Redis 좌석 hold 생성 (밖)
  -> PendingOrderCreator
       -> 주문 aggregate 조립. 총액은 Order.addOrderSeat가 좌석
          단가를 누적해 만든다 = Σ PerformanceSeat.unitPrice (ADR 0005, 다른 값을 섞지 않는다)
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
          (payload를 신뢰하지 않는다. 이후 Redis·WebSocket 작업은 DB connection을 쥐지 않는다)
       -> HoldCreationCoordinator
            -> 주문 회원 소유 selection만 해제 (Redis)
            -> HELD 상태 발행             (WebSocket)
~~~

DB 저장이 실패하면 `StartBookingUseCase`가 이미 만든 Redis hold를 보상 해제한다(같은 스레드에서
`LockScope.SEAT` 락을 다시 잡고 해제).

이 시점의 동시성 방어는 여전히 `LockScope.SEAT` 분산락과 `BookingAvailabilityChecker`의 좌석 판매
상태 확인이다. `PerformanceSeat`는 `@Version`(낙관적 락)과 `reserve()`/`release()`를 갖지만, 현재
주문 생성 경로 어디에서도 호출되지 않는다 — 결제 승인 시점에 `PerformanceSeat`를 `RESERVED`로
전이하는 정산 흐름은 아직 구현되지 않은 후속 작업이다(ADR 0005 "이 ADR이 결정하지 않는 것" 참고).
지금은 Redis hold가 실제 점유의 기준이고, `PerformanceSeat.state`는 판매 좌석 편성(`AVAILABLE`)을
나타낼 뿐 주문 확정으로 바뀌지 않는다.

커밋 이후 리스너 실행이 실패하면 Order/OrderSeat/HoldHistory는 그대로 커밋된 상태로 남고,
`OrderStarted` publication은 Event Publication Registry에 FAILED로 남는다.
`EventPublicationMaintenance`가 1분마다 재제출한다(아래 [이벤트 재시도와 보정](#이벤트-재시도와-보정)).
따라서 프로세스가 재시작되거나 즉시 처리가 실패해도 후속 처리 입력은 DB(publication row)에
남는다. hold가 실제 점유의 기준이며 selection은 UX 보조 상태다.

주문 시작, 주문 상세, 주문 상태 응답의 시간 계약은 동일하다. `expiresAt`은 서버의 절대
만료 시각이고, `remainingSeconds`는 응답을 만드는 서버 시각부터 `expiresAt`까지 남은
완전한 초다. PENDING이 아니거나 이미 만료 경계를 지났으면 0이다. 클라이언트는 로컬
시계로 `expiresAt - now`를 다시 계산하지 않고 `remainingSeconds`로 카운트다운을 시작한 뒤,
상태 조회 응답으로 주기적으로 보정한다.

## 좌석 선택과 해제 알림 순서

좌석 선택 상태(Redis)와 좌석 상태 알림(WebSocket)은 `SeatSelectionCoordinator`가 **같은 좌석 락
안에서** 함께 처리한다. 상태만 락 안에서 바꾸고 발행을 락 밖에서 하면 이런 역전이 생긴다 — A의
선택이 TTL로 만료되고 B가 같은 좌석을 다시 선택한 뒤 A의 만료 처리가 뒤늦게 실행되면, B의
`SELECTED` 뒤에 A의 `DESELECTED`가 나가 이미 B가 잡은 좌석이 비어 보인다.

- `select` — 락 안에서 마감 시각과 hold를 다시 확인하고, 선택 기록과 `SELECTED` 발행을 함께 한다.
- `deselect` — 락 안에서 해제하고, **실제로 해제된 경우에만** 알린다. 이미 만료된 선택을 해제
  요청했다고 해서 알림을 내보내지 않는다.
- `notifyReleasedIfFree` — TTL 만료와 회원의 전체 선택 해제처럼 "이미 지난 사실"을 알리는 경로다.
  발행 직전에 락 안에서 현재 선택·선점 상태를 다시 확인해, 그 사이 남이 차지한 좌석은 알리지
  않는다. 상태를 한 번 더 읽는 것만으로는 부족하고 그 재확인이 락 안에 있어야 의미가 있다.

`SeatSelectionExpirationHandler`는 Redis key 해석과 호출만 한다. 대상 좌석 확인, 현재 상태 확인,
알림 필요 여부 판단은 모두 application이 한다.

**서버가 보장하는 것은 발행 순서까지다.** 클라이언트 수신 순서는 WebSocket 전송 계층의 문제이며 이
락의 범위가 아니다. 그래서 좌석 상태 이벤트는 계속 "그 좌석을 특정 상태로 맞추는" 멱등 알림으로
취급하고, 클라이언트는 필요하면 좌석 상태 조회로 보정한다. payload(`performanceSeatId` + 물리
`seatId`)는 바뀌지 않았다.

## 결제 시도와 Order 상태

`Order`의 상태 전이는 `PENDING -> CONFIRMED`, `PENDING -> EXPIRED`, `PENDING -> CANCELED` 세 가지뿐이다
(`booking.order.domain.OrderState`). 과거 있었던 `PAYMENT_FAILED`는 ADR 0005로
제거됐다 — `rg -n "PAYMENT_FAILED|failPayment" --type java`로 확인해도 `Order`/`OrderState`에는
남아 있지 않다(`HoldReleaseReason.PAYMENT_FAILED`는 hold 해제 사유를 기록하는 별개의 enum이고
Order 상태가 아니다).

이렇게 바뀐 이유는 Payment가 결제 완료 건이 아니라 **결제 시도**이기 때문이다(`Order 1 : 0..N
Payment`, ADR 0005). 결제 시도 한 번이 실패해도 Order는 만료 전까지 다시 결제를 시도할 수 있어야
하므로, 결제 실패는 Payment 자신의 상태(`READY/PROCESSING -> FAILED`)로만 표현하고 Order를 끝내는
사건으로 취급하지 않는다.

**현재 구현 범위**: `payment` Application Module은 지금 entity/schema/repository까지만 있는
entity-only 단계다(ADR 0005 §3). PG 연동, 결제 승인/실패 API, `Order.confirm()`을 호출하는 정산
서비스, `OrderConfirmed` listener는 아직 코드에 없다 — `rg -n "\.confirm\("`로 확인해도 main
소스에서 `Order.confirm()`을 호출하는 곳이 없다. 즉 지금은 Order가 결제 승인으로 `CONFIRMED`가
되는 실제 경로 자체가 아직 배선되지 않았고, `PENDING` 주문은 만료(`ExpireOrderUseCase`) 또는
취소(`CancelOrderUseCase`)로만 종료된다. 결제 승인·재시도·Hold 만료 경쟁 정책은 ADR 0005가 명시적으로
범위 밖으로 남긴 별도 설계 대상이다.

## 주문 취소와 만료

취소는 소유권과 현재 상태를 검증하고, 만료는 orderId 또는 holdKey로 PENDING 주문을 잠근다.
이후 공통 절차는 `OrderTerminationService`가 담당한다. 취소는 짧은 쓰기 트랜잭션을
`CancelOrderTransactionService`가 따로 갖는다 — `CancelOrderUseCase`는 트랜잭션을 직접 열지 않는다.

~~~text
CancelOrderUseCase -> CancelOrderTransactionService / ExpireOrderUseCase
  -> PENDING 주문 row lock
  -> OrderTerminationService
       -> 주문 좌석 검증
       -> CANCELED 또는 EXPIRED 전이
       -> hold history 저장
       -> 같은 트랜잭션 안에서 OrderTerminated 이벤트 발행
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderTerminated) (@ApplicationModuleListener, 커밋 후, 트랜잭션 없음)
       -> OrderHoldSnapshotReader: orderId로 필요한 값만 짧은 읽기 트랜잭션에서 완성
       -> HoldReleaseProgressRecorder로 이미 Redis 해제가 끝난 event인지 확인
       -> HoldReleaseCoordinator
            -> (아직이면) 좌석별 현재 holdKey를 확인하고 일치하는 hold만 해제 (Redis)
            -> Redis 해제 완료를 eventId 기준으로 기록      (HoldReleaseProgressRecorder,
               자기 트랜잭션에서 곧바로 커밋한다 — 뒤이은 발행이 실패해도 되돌아가지 않는다)
            -> 현재 hold/selection이 없는 좌석만 RELEASED 발행 (WebSocket)
~~~

같은 `OrderTerminated` publication이 재시도로 다시 전달돼도 `HoldReleaseProgressRecorder`가
`eventId` 단위로 Redis 해제 완료 여부를 기억하므로 Redis 해제 자체는 반복되지 않는다. 이 기록은
listener의 트랜잭션에 묶이지 않고 `REQUIRES_NEW`로 곧바로 커밋된다 — 예전에는 Redis 해제 뒤
WebSocket 발행이 실패하면 완료 기록까지 함께 롤백돼 재시도에서 Redis 해제를 다시 수행했다. 반대로
Redis 해제와 완료 기록 사이에서 실패하면 기록이 남지 않아 다음 재시도가 해제를 다시 시도하는데,
해제는 좌석별 현재 holdKey를 확인하고 일치할 때만 지우므로 남의 선점을 건드리지 않는다. 다만
WebSocket 발행은 매번 현재 hold/selection 상태를 다시 확인해, 새 hold나 selection이 생긴
좌석에는 오래된 해제 알림을 보내지 않는다. 발행 자체가 다시 실패하면 같은 RELEASED가 다시
발행될 수 있다 — 이 이벤트는 좌석을 특정 상태로 맞추는 멱등 상태 알림으로 취급하며 전달
보장은 at-least-once다. 중복보다 누락을 피하되, 매 발행 직전 현재 상태 검증으로 더 최신
상태를 덮어쓰지 않는 것이 기준이다.

## 이벤트 재시도와 보정

`BookingEventListeners`의 실패는 Spring Modulith의 JPA Event Publication Registry가 관리한다.
운영 정책(모든 profile 공통, `application.yml`):

```yaml
spring:
  modulith:
    events:
      completion-mode: archive
      republish-outstanding-events-on-restart: false
      staleness:
        check-interval: 1m
        published: 5m
        processing: 10m
        resubmitted: 10m
```

`EventPublicationMaintenance`(`com.ticket.shared.config`)가 두 가지 주기 작업을 한다.

| 작업 | 주기 | 동작 |
| --- | --- | --- |
| `resubmitFailed` | 1분(`fixedDelayString = "PT1M"`) | `FailedEventPublications.resubmit`을 batch 100건·동시 4건(`withMaxInFlight(4)`)으로 실행한다. `completionAttempts <= 10`인 publication만 대상이다 |
| `purgeArchive` | 매일 03:00 KST(`cron = "0 0 3 * * *"`) | `CompletedEventPublications.deletePublicationsOlderThan(Duration.ofDays(30))`으로 30일이 지난 완료 publication을 지운다 |

**10회를 초과해 계속 실패하는 publication은 자동 재제출 대상에서 제외되고 `ERROR` 레벨 구조화
로그**(`eventPublicationId`, `completionAttempts`, `event` 포함)로 남는다. 이 로그가 수동 개입이
필요하다는 신호다. 알림 채널에서 이 로그 패턴을 감시하고, 발견하면 수동 재처리 절차로 넘어간다.

### 수동 재처리 절차

1. `EventPublicationMaintenance.isRetryable`이 남긴 `ERROR` 로그 또는 `EVENT_PUBLICATION` /
   `EVENT_PUBLICATION_ARCHIVE` 테이블에서 `COMPLETION_ATTEMPTS > 10`인 행을 찾는다.
2. `event_type`과 `serialized_event`로 어떤 `OrderStarted`/`OrderTerminated`가 실패했는지, 어떤
   `orderId`/`holdKey`에 해당하는지 확인한다. `serialized_event`는 root V9로 넓혀
   (H2 `VARCHAR(4000)`, Oracle `VARCHAR2(4000 CHAR)`) 현실적인 주문 이벤트를 자르지 않고 담는다.
   V9 이전에 저장된 행은 옛 `VARCHAR(255)`에서 잘렸을 수 있다 — 이 경우 `orderId`만으로도 booking
   테이블에서 실제 주문/좌석 상태를 다시 조회할 수 있다(리스너 자체도 payload를 신뢰하지 않고
   재조회한다).
3. 근본 원인을 판단한다: listener 예외(코드 결함), 외부 의존성 장애(Redis 연결 등), 또는
   `serialized_event` 크기 초과(V9 이전 스키마에서 다중 좌석 주문이면 발생했다. 배경은
   [ADR 0003 §5](adr/0003-spring-modulith-application-module-boundaries.md#5-spring-modulith-이벤트와-jpa-event-publication-registry))로
   나눈다.
4. 원인이 해소됐다면 해당 publication의 `completion_attempts`를 초기화하거나 애플리케이션의
   `FailedEventPublications` API를 관리 스크립트/actuator 경로로 다시 호출해 재제출 대상에
   포함시킨다. 이 저장소는 아직 이 재처리를 자동화하는 전용 endpoint를 두지 않았다 — DB 직접
   조작 또는 임시 운영 스크립트로 수행하고, 좌석/주문의 최종 상태와 어긋나지 않는지 반드시
   확인한 뒤 반영한다.
5. 원인이 해소되지 않았다면(예: payload 크기 초과가 반복되는 구조적 문제) 재제출을 강행하지
   않고 별도 결정을 먼저 내린다.

## TTL 폭주와 보정

Redis hold meta key가 만료되면 `RedisKeyExpirationListener`가 등록된 핸들러 목록에 위임하고,
`booking.hold.persistence.HoldKeyExpirationHandler`가 키를 해석해
`ExpireOrderUseCase.expireByHoldKey`를 호출한다.

- `redisExpirationSubscriptionExecutor`: Redis 구독 전용 worker 1~2개
- `redisExpirationTaskExecutor`: 만료 handler worker 2개, queue 256개, 공유 permit 2개
- queue가 가득 차면 Redis 수신 스레드도 같은 permit을 얻은 뒤 처리해 유입 속도를 늦춘다.
- `OrderExpirationTrigger`(`com.ticket.booking.order.usecase`) →
  `ExpirePendingOrdersUseCase`: `worker.order-expiration.fixed-delay`(기본 5분)마다 만료 주문을
  보정한다. **id 커서로 순회한다** — 커서는 조회한 페이지의 마지막 id이고 처리 성공 여부와
  무관하게 앞으로만 가므로, 앞의 주문이 계속 실패해도 뒤의 정상 만료 대상이 같은 순회에서
  처리된다. 실패 항목은 지우지도 처리 완료로 치지도 않고 PENDING으로 남아 다음 순회에서 다시
  시도되며, 그 건수는 `Output.failedCount`와 경고 로그로 드러난다.
- `Order.expire(now)`는 **만료 시각이 지나기 전에는 만료시키지 않는다.** 아직 유효한 주문이 만료
  경로로 들어오면 그대로 EXPIRED가 되던 문제를 막는다. "지금 만료 처리 대상인가"는
  `Order.isExpirable(now)`가 답한다.

`@Scheduled` 트리거(`OrderExpirationTrigger`)는 `booking`이 소유한다. `worker.enabled=false`면
이 트리거 자체가 등록되지 않는다.

**주문 커밋 후 이벤트 리스너(`BookingEventListeners`)에는 이름이 붙은 전용 executor가 없다.**
상세(어떤 executor를 쓰는지, 관측 지표)는 [operations.md의 Core 용량 관측](operations.md#core-용량-관측)를 본다.

## 주요 코드

- 예매 시작: `booking.order.usecase.StartBookingUseCase`(정책·입장·회원 확인부터 Redis
  선점, 주문 생성, 실패 시 보상까지의 workflow를 조율한다. 검증 순서가 이 클래스에서 그대로 읽힌다)
- 좌석 판매 가능 확인: `booking.order.usecase.BookingAvailabilityChecker`(짧은 읽기 트랜잭션)
- 주문 DB 생성: `booking.order.usecase.PendingOrderCreator`(조립·저장·이력·이벤트가 한 트랜잭션)
- 선점 이력 조립: `booking.order.usecase.OrderHoldHistoryRecorder`. `HoldHistory`와 저장 계약은
  `booking.hold.domain`이 그대로 소유한다
- 예매 정책 조회: `booking.salespolicy.domain.PerformanceSalesPolicy`(booking local
  aggregate) / 표시 snapshot 조회: `show.api.PerformanceSaleCatalogApi`
- 판매 좌석과 가격 원본: `booking.seat.domain.PerformanceSeat`
  (`unitPrice`, `performanceGradeId`, `@Version`)
- 주문 종료: `booking.order.usecase.OrderTerminationService`
- 상태 전이 규칙: `booking.order.domain.Order`(confirm, expire, cancel),
  `OrderState`(PENDING/CONFIRMED/EXPIRED/CANCELED)
- 공개 이벤트: `booking.OrderStarted`, `booking.OrderTerminated`
- 커밋 후 리스너: `booking.event.BookingEventListeners`,
  `booking.order.usecase.OrderHoldSnapshotReader`(리스너가 쓸 DB 값을 짧은 읽기 트랜잭션에서 완성)
- hold 생성/해제 후속 처리: `booking.event.HoldCreationCoordinator`,
  `booking.event.HoldReleaseCoordinator`, `booking.event.HoldReleaseProgressRecorder`
- 좌석 선택 조율과 발행: `booking.selection.usecase.SeatSelectionCoordinator`
- 예매 정책 조회: `booking.salespolicy.usecase.PerformanceSaleFinder`
- 대기열 입장 검증: `booking.admission.AdmissionGuard`(실제 token 검증은 `AdmissionVerifier`에 위임)
- 취소 쓰기 트랜잭션: `booking.order.usecase.CancelOrderTransactionService`
- hold 만료 키 처리: `booking.hold.persistence.HoldKeyExpirationHandler`
- 만료 보정: `booking.order.usecase.ExpirePendingOrdersUseCase`
- background 트리거: `booking.order.usecase.OrderExpirationTrigger`
- Redis TTL 진입 제한: `booking.redis.RedisExpirationListenerConfig`
- event publication 운영: `shared.config.EventPublicationMaintenance`
- 분산락 포트: `booking.concurrency.LockManager`(잠글 대상은 `LockKey`/`LockScope` — 업무
  의미만 담고 key 문자열은 담지 않는다, 획득 방식은 `LockOptions` — 대기 시간·임대 시간·실패 로그
  수준), 구현: `booking.concurrency.redis.RedissonLockManager`(key 형식은
  `RedissonLockKeyFormatter`). 적용 예: 동일 회원/공연 조합의 중복 주문 시작 방지
  (`LockScope.ORDER_START`), 동일 좌석 동시 점유 방지(`LockScope.SEAT`)

## 운영 확인

- hikaricp_connections_pending이 지속적으로 0인지 확인한다.
- redisExpirationSubscriptionExecutor, redisExpirationTaskExecutor의 active/queued 값을 본다.
- `EventPublicationMaintenance.isRetryable`이 남기는 `ERROR` 로그(최대 재시도 초과)를 alert로
  감시한다.
- `EVENT_PUBLICATION`/`EVENT_PUBLICATION_ARCHIVE` 테이블에서 오래 남아 있는 미완료 publication이
  없는지 확인한다.
- 같은 조건의 연속 부하 테스트 전에는 이전 회차의 PENDING 주문, hold TTL, 미완료 event
  publication이 모두 정리됐는지 확인한다.
