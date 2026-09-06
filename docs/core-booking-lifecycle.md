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

이 문서가 설명하는 코드는 대부분 `booking` Application Module 소유다(`com.ticket.booking.internal.**`).
커밋 후 처리를 관리하는 `EventPublicationMaintenance`만 전역 배선 module
(`com.ticket.config.internal`)에 있다.

## 지켜야 할 원칙

- Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다.
- Redis TTL 이벤트가 한꺼번에 들어와도 DB로 진입하는 작업 수는 제한한다.
- 주문 저장 또는 상태 변경과 그에 대응하는 이벤트 발행은 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 담당하고, 실패는 catch-and-log로 삼키지 않고
  throw해 Event Publication Registry가 FAILED로 기록하고 재시도하게 한다.
- 다른 모듈 API 호출(catalog 정책 조회, member 회원 확인, admission token 검증)은 booking DB
  트랜잭션 밖에서 끝낸다.
- 주문 금액은 오직 `PerformanceSeat.unitPrice`로만 계산한다(ADR 0005). 클라이언트가 보낸 가격도,
  catalog가 다시 계산한 가격도 금액 계산 근거로 쓰지 않는다.
- Order/OrderSeat에 남긴 표시 snapshot(show/performance/venue 이름, 등급 코드·이름, 좌석 라벨,
  가격)은 생성 이후 다시 조회하지 않는다. catalog 쪽 표시값이나 가격이 나중에 바뀌어도 이미 만든
  주문 상세는 바뀌지 않는다.

## 주문 생성

~~~text
CreateOrderUseCase
  -> LockScope.ORDER_START 락(같은 회원·회차 직렬화)
  -> CreateOrderValidator
       -> catalog BookingPolicyLookup: 예매 정책(오픈 여부, hold 상한, 대기열 필요 여부) (DB 트랜잭션 밖)
       -> admission AdmissionVerifier: 대기열 필요 회차만 token 검증 (밖)
       -> member MemberLookup: active member 확인 (밖)
       -> booking local read: pending 주문 중복, 좌석 판매 상태 (짧은 read 트랜잭션)
       -> catalog PerformanceSaleCatalog: 요청 좌석의 표시 snapshot(등급 코드/이름, 좌석 라벨,
          show/venue 이름) 조회 (밖) — 가격 자체는 이 snapshot이 아니라 아래 PerformanceSeat에서 온다
  -> LockScope.SEAT 락 안에서 Redis에 좌석 hold 생성 (밖)
  -> CreatePendingOrderTransactionService
       -> OrderCreator: 주문 금액 = Σ PerformanceSeat.unitPrice (ADR 0005, 다른 값을 계산에 섞지 않는다)
       -> PENDING 주문·OrderSeat·hold history 저장     (booking DB 트랜잭션)
            Order에는 show/performance/venue 표시 snapshot을, OrderSeat에는 등급·좌석 라벨·unitPrice
            snapshot을 함께 저장한다 — 둘 다 생성 후 불변이다.
       -> 같은 트랜잭션 안에서 OrderStarted 이벤트 발행
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderStarted)             (@ApplicationModuleListener, 커밋 후)
       -> orderId로 order/orderSeat 재조회(payload를 신뢰하지 않는다)
       -> HoldCreationTaskProcessor
            -> 주문 회원 소유 selection만 해제 (Redis)
            -> HELD 상태 발행             (WebSocket)
~~~

DB 저장이 실패하면 `CreateOrderUseCase`가 이미 만든 Redis hold를 보상 해제한다(같은 스레드에서
`LockScope.SEAT` 락을 다시 잡고 해제).

이 시점의 동시성 방어는 여전히 `LockScope.SEAT` 분산락과 `PendingOrderLocalValidator`의 좌석 판매
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

## 결제 시도와 Order 상태

`Order`의 상태 전이는 `PENDING -> CONFIRMED`, `PENDING -> EXPIRED`, `PENDING -> CANCELED` 세 가지뿐이다
(`booking.internal.domain.order.model.OrderState`). 과거 있었던 `PAYMENT_FAILED`는 ADR 0005로
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
이후 공통 절차는 `OrderTerminationService`가 담당한다.

~~~text
CancelOrderUseCase / ExpireOrderUseCase
  -> PENDING 주문 row lock
  -> OrderTerminationService
       -> 주문 좌석 검증
       -> CANCELED 또는 EXPIRED 전이
       -> hold history 저장
       -> 같은 트랜잭션 안에서 OrderTerminated 이벤트 발행
  -> DB 커밋 및 connection 반환
  -> BookingEventListeners.on(OrderTerminated)          (@ApplicationModuleListener, 커밋 후)
       -> orderId로 order/orderSeat 재조회
       -> HoldReleaseProgressRecorder로 이미 Redis 해제가 끝난 event인지 확인
       -> HoldReleaseTaskProcessor
            -> (아직이면) 좌석별 현재 holdKey를 확인하고 일치하는 hold만 해제 (Redis)
            -> Redis 해제 완료를 eventId 기준으로 기록      (HoldReleaseProgressRecorder)
            -> 현재 hold/selection이 없는 좌석만 RELEASED 발행 (WebSocket)
~~~

같은 `OrderTerminated` publication이 재시도로 다시 전달돼도 `HoldReleaseProgressRecorder`가
`eventId` 단위로 Redis 해제 완료 여부를 기억하므로 Redis 해제 자체는 반복되지 않는다. 다만
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

`EventPublicationMaintenance`(`com.ticket.config.internal`)가 두 가지 주기 작업을 한다.

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
   `orderId`/`holdKey`에 해당하는지 확인한다. `serialized_event`는 `VARCHAR(255)`라서 원본
   payload가 잘려 있을 수 있다 — 이 경우 `orderId`만으로도 booking 테이블에서 실제 주문/좌석
   상태를 다시 조회할 수 있다(리스너 자체도 payload를 신뢰하지 않고 재조회한다).
3. 근본 원인을 판단한다: listener 예외(코드 결함), 외부 의존성 장애(Redis 연결 등), 또는
   `serialized_event` 크기 초과([architecture.md의 이벤트와 후속 처리](architecture.md#이벤트와-후속-처리)
   참고, 다중 좌석 주문에서 발생 가능)로 나눈다.
4. 원인이 해소됐다면 해당 publication의 `completion_attempts`를 초기화하거나 애플리케이션의
   `FailedEventPublications` API를 관리 스크립트/actuator 경로로 다시 호출해 재제출 대상에
   포함시킨다. 이 저장소는 아직 이 재처리를 자동화하는 전용 endpoint를 두지 않았다 — DB 직접
   조작 또는 임시 운영 스크립트로 수행하고, 좌석/주문의 최종 상태와 어긋나지 않는지 반드시
   확인한 뒤 반영한다.
5. 원인이 해소되지 않았다면(예: payload 크기 초과가 반복되는 구조적 문제) 재제출을 강행하지
   않고 별도 결정을 먼저 내린다.

## TTL 폭주와 보정

Redis hold meta key가 만료되면 `RedisKeyExpirationListener`가 `ExpireOrderUseCase.expireByHoldKey`를
호출한다.

- `redisExpirationSubscriptionExecutor`: Redis 구독 전용 worker 1~2개
- `redisExpirationTaskExecutor`: 만료 handler worker 2개, queue 256개, 공유 permit 2개
- queue가 가득 차면 Redis 수신 스레드도 같은 permit을 얻은 뒤 처리해 유입 속도를 늦춘다.
- `OrderExpirationTrigger`(`com.ticket.booking.internal.infrastructure.worker`) →
  `ExpirePendingOrdersUseCase`: `worker.order-expiration.fixed-delay`(기본 5분)마다 만료 주문을
  보정한다.

`@Scheduled` 트리거(`OrderExpirationTrigger`)는 `booking`이 소유한다. `worker.enabled=false`면
이 트리거 자체가 등록되지 않는다.

**주문 커밋 후 이벤트 리스너(`BookingEventListeners`)에는 과거 outbox worker 같은 명시적
동시성 상한이 설정돼 있지 않다.** `@ApplicationModuleListener`는 기본적으로 비동기 실행되며,
전용 `ThreadPoolTaskExecutor`를 따로 구성하지 않았으므로 Spring Boot의 기본 비동기 task
executor를 쓴다. Redis 만료 처리(`redisExpirationTaskExecutor`)처럼 명시적으로 2개로 제한된
경로와 달리, 이벤트 리스너 동시 실행 수는 운영 중 관측(아래 참고)으로 확인해야 한다. 이 상한이
필요하다고 판단되면 전용 executor 도입을 별도로 결정한다.

## 주요 코드

- 주문 생성: `booking.internal.application.order.command.CreateOrderUseCase`,
  `CreateOrderValidator`
- 주문 DB 저장: `booking.internal.application.order.command.CreatePendingOrderTransactionService`,
  `OrderCreator`(금액 계산과 snapshot 조립)
- 예매 정책 조회: `catalog.BookingPolicyLookup` / 표시 snapshot 조회: `catalog.PerformanceSaleCatalog`
- 판매 좌석과 가격 원본: `booking.internal.domain.performanceseat.model.PerformanceSeat`
  (`unitPrice`, `performanceGradeId`, `@Version`)
- 주문 종료: `booking.internal.application.order.command.OrderTerminationService`
- 상태 전이 규칙: `booking.internal.domain.order.model.Order`(confirm, expire, cancel),
  `OrderState`(PENDING/CONFIRMED/EXPIRED/CANCELED)
- 공개 이벤트: `booking.OrderStarted`, `booking.OrderTerminated`
- 커밋 후 리스너: `booking.internal.application.BookingEventListeners`
- hold 생성/해제 후속 처리: `booking.internal.application.order.command.HoldCreationTaskProcessor`,
  `HoldReleaseTaskProcessor`, `booking.internal.application.event.HoldReleaseProgressRecorder`
- 만료 보정: `booking.internal.application.order.command.ExpirePendingOrdersUseCase`
- background 트리거: `booking.internal.infrastructure.worker.OrderExpirationTrigger`
- Redis TTL 진입 제한: `booking.internal.infrastructure.redis.RedisExpirationListenerConfig`
- event publication 운영: `config.internal.EventPublicationMaintenance`

## 운영 확인

- hikaricp_connections_pending이 지속적으로 0인지 확인한다.
- redisExpirationSubscriptionExecutor, redisExpirationTaskExecutor의 active/queued 값을 본다.
- `EventPublicationMaintenance.isRetryable`이 남기는 `ERROR` 로그(최대 재시도 초과)를 alert로
  감시한다.
- `EVENT_PUBLICATION`/`EVENT_PUBLICATION_ARCHIVE` 테이블에서 오래 남아 있는 미완료 publication이
  없는지 확인한다.
- 같은 조건의 연속 부하 테스트 전에는 이전 회차의 PENDING 주문, hold TTL, 미완료 event
  publication이 모두 정리됐는지 확인한다.
