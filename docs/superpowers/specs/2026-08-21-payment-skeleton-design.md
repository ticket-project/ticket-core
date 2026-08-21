# Payment Skeleton Design

## 목표

결제 도메인의 뼈대를 만들어 **결제가 완료될 때 우리 데이터가 어떻게 변하는지**를 코드로 확정한다.
실제 카드사·PG 연동은 범위 밖이며, 승인 요청은 가짜 게이트웨이가 즉시 응답한다.

확정하려는 것은 다음 네 가지 전이다.

- `PAYMENTS` 행의 생성과 승인
- `ORDERS.status` `PENDING -> CONFIRMED`
- `PERFORMANCE_SEATS.state` `AVAILABLE -> RESERVED`
- 확정된 hold의 Redis 정리와 좌석 이벤트 억제

## 현재 구조

결제 확정 경로가 **전혀 없다.** 아래 요소가 모두 정의만 되어 있고 호출처가 없다.

- [Order.confirm()](../../../core/core-domain/src/main/java/com/ticket/core/domain/order/model/Order.java) — 호출처 없음
- [PerformanceSeat.reserve()](../../../core/core-domain/src/main/java/com/ticket/core/domain/performanceseat/model/PerformanceSeat.java) — 호출처 없음
- `HoldState.CONFIRMED`, `HoldHistoryEventType.CONFIRMED`, `HoldReleaseReason.PAYMENT_CONFIRMED` — 저장·조회하는 코드 없음
- `SeatStatusMessage.SeatAction.RESERVED` — 발행하는 코드 없음

그 결과 `PERFORMANCE_SEATS.state`는 시드 이후 항상 `AVAILABLE`이고,
[SeatMapQueryRepository.findSeatStatuses](../../../core/core-domain/src/main/java/com/ticket/core/domain/performanceseat/query/SeatMapQueryRepository.java)는
항상 전 좌석 `AVAILABLE`을 반환한다.
[HoldSeatAvailabilityValidator](../../../core/core-domain/src/main/java/com/ticket/core/domain/hold/command/HoldSeatAvailabilityValidator.java)의
`NOT_EXIST_AVAILABLE_SEAT` 검사도 성립하지 않는다.

취소·만료는 [OrderTerminationService](../../../core/core-domain/src/main/java/com/ticket/core/domain/order/command/OrderTerminationService.java)가
상태 전이와 hold 해제 outbox 적재를 한 트랜잭션에서 처리하고, 커밋 후
[HoldReleaseAfterCommitListener](../../../core/core-infra/src/main/java/com/ticket/core/infra/order/HoldReleaseAfterCommitListener.java)가
백그라운드 큐에 제출한다. 실제 Redis 해제와 WebSocket 발행은
[HoldReleaseTaskProcessor](../../../core/core-domain/src/main/java/com/ticket/core/domain/order/command/release/HoldReleaseTaskProcessor.java)가 수행한다.
이 processor는 **조건 없이 `RELEASED`를 발행한다.** outbox에 해제 이유가 남지 않기 때문이다.

프론트엔드는 `HELD`와 `RESERVED`를 모두 `OCCUPIED`로 취급하고
(`ticket-fe/src/features/booking/lib/seatStateCache.ts`), 클라이언트 측 hold TTL 타이머가 없다.
따라서 선점 중 좌석은 이미 점유로 보이며, **결제 확정 시 새로 발행할 좌석 이벤트는 없다.**
확정 경로가 지켜야 할 것은 `RELEASED`를 발행하지 않는 것이다.

## 설계

### 1. 결제 API는 2단계로 둔다

실제 PG(준비 -> 결제창 -> 승인)와 같은 모양을 유지해 이후 연동 시 스키마 변경을 줄인다.

```text
POST /api/v1/orders/{orderKey}/payments
  요청  { method, amount }
  검증  본인 주문 / PENDING / 미만료 / amount == order.totalAmount
  결과  PAYMENTS insert (READY), paymentKey 발급
  응답  { paymentKey, orderKey, amount, status, expiresAt }

POST /api/v1/payments/{paymentKey}/confirm
  요청  { amount }
  검증  본인 / READY / 주문 PENDING·미만료 / 금액 재확인
  결과  게이트웨이 승인 요청 후 확정 트랜잭션 또는 실패 기록
  응답  { paymentKey, status, approvedAt, orderKey, orderStatus }
```

멱등 규칙은 다음과 같다.

- 같은 주문에 준비 요청이 다시 오면 기존 `READY` 행을 반환한다.
- 이미 `APPROVED`인 결제에 `confirm`이 다시 오면 같은 응답을 반환한다.
- `FAILED` 결제에 `confirm`이 오면 409로 막는다. 재시도는 준비 요청부터 다시 하며 새 `PAYMENTS` 행이 생긴다.

### 2. PAYMENTS 테이블

```text
PAYMENTS
  id              PK
  payment_key     VARCHAR(40)  NOT NULL UNIQUE
  order_id        NUMBER       NOT NULL   -- IDX_PAYMENTS_ORDER_ID
  method          VARCHAR(20)  NOT NULL   -- CARD
  amount          NUMBER(19,2) NOT NULL
  status          VARCHAR(20)  NOT NULL   -- READY / APPROVED / FAILED
  pg_transaction_id VARCHAR(64)           -- 가짜 게이트웨이가 발급한 승인 식별자
  approved_at     TIMESTAMP
  failed_at       TIMESTAMP
  failure_code    VARCHAR(40)
  failure_message VARCHAR(200)
  created_at, created_by                  -- BaseEntity
```

상태는 `READY -> APPROVED` 또는 `READY -> FAILED`이고 둘 다 종단이다.
`order_id`는 `Order`가 `performanceId`를 원시 타입으로 두는 방식에 맞춰 `Long`으로 들고 연관관계를 걸지 않는다.

### 3. 승인 성공은 짧은 DB 트랜잭션 하나로 끝낸다

게이트웨이 호출은 트랜잭션 밖에서 먼저 하고, 결과를 받은 뒤 트랜잭션을 연다.

```text
[트랜잭션 밖] 게이트웨이 승인 요청

[DB 트랜잭션]
  PAYMENTS           row lock, READY 재확인 -> APPROVED (approved_at)
  ORDERS             row lock, PENDING·미만료 재확인 -> CONFIRMED (confirmed_at)
  ORDER_SEATS        조회로 seatId 확보
  PERFORMANCE_SEATS  전 좌석 AVAILABLE 확인 -> RESERVED
  HOLD_HISTORY       CONFIRMED + PAYMENT_CONFIRMED 기록 (좌석당 1행)
  해제 outbox         적재 (reason = PAYMENT_CONFIRMED)

[커밋 후] 기존 경로 재사용
  Redis hold 해제
  reason이 PAYMENT_CONFIRMED이므로 좌석 이벤트 발행 생략
  outbox 완료 기록
```

`PERFORMANCE_SEATS`가 이 시점부터 영구 점유의 진실 원천이 된다.
좌석 중 하나라도 `AVAILABLE`이 아니면 예외로 트랜잭션 전체를 되돌린다.
hold를 보유한 주문이라 발생할 수 없지만, 진실 원천이 바뀌는 첫 지점이므로 방어를 둔다.

### 4. 승인 거절은 결제만 종료한다

```text
PAYMENTS           READY -> FAILED (failure_code, failure_message)
ORDERS             PENDING 유지
PERFORMANCE_SEATS  변화 없음
Redis hold         유지
```

사용자는 주문 만료 전까지 준비 요청부터 다시 시도할 수 있다.
자동 정리는 기존 만료 경로(hold TTL -> `ExpireOrderUseCase`)가 그대로 담당하므로 실패 전용 정리 경로를 만들지 않는다.
`Order.failPayment()`는 이번 범위에서 사용하지 않는다.

### 5. 가짜 게이트웨이는 port/adapter로 둔다

- `core-domain`에 port를 둔다. 승인 요청 커맨드와 결과를 받는 인터페이스 하나다.
- `core-infra`에 어댑터를 둔다. 외부 호출 없이 즉시 승인하고 `pgTransactionId`는 UUID로 만든다.
- 거절을 재현할 수 있도록 설정값 하나로 전량 거절로 토글한다 (`ticket.payment.fake.approve: false`).
  로컬에서 `FAILED` 데이터를 확인하기 위한 수단이며 기본값은 승인이다.

`core-domain`은 HTTP 클라이언트 애노테이션에 의존할 수 없으므로 어댑터는 반드시 `core-infra`에 둔다.

### 6. 해제 outbox에 이유를 기록해 재사용한다

`ORDER_HOLD_RELEASE_OUTBOX`에 `reason` 컬럼을 추가한다.

- **NULL 허용**이므로 기존 행 백필이 필요 없다.
- processor는 `reason == PAYMENT_CONFIRMED`일 때만 좌석 이벤트 발행을 건너뛴다. `NULL`과 그 외 값은 현재 동작(`RELEASED` 발행)을 유지한다.
- 취소는 `USER_CANCELED`, 만료는 `ORDER_EXPIRED`를 기록한다. 지금은 이 구분이 어디에도 남지 않는다.

hold 해제·분산락·재시도·스케줄러 보정을 그대로 재사용하므로 새 outbox 테이블과 executor를 만들지 않는다.

## 코드 변경 범위

### 새로 만드는 것

- `core/core-domain/src/main/java/com/ticket/core/domain/payment/`
  - `model/Payment.java`, `model/PaymentStatus.java`, `model/PaymentMethod.java`
  - `repository/PaymentRepository.java`
  - `command/PreparePaymentUseCase.java`, `command/ConfirmPaymentUseCase.java`
  - `gateway/PaymentGatewayClient.java`, `gateway/PaymentApprovalCommand.java`, `gateway/PaymentApprovalResult.java`
  - `support/PaymentKeyGenerator.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/OrderConfirmationService.java`
  - 확정 트랜잭션의 상태 전이를 담당한다. `OrderTerminationService`와 같은 위치·같은 성격이다.
- `core/core-infra/src/main/java/com/ticket/core/infra/payment/FakePaymentGatewayClient.java`
- `core/core-api` — `controller/PaymentController.java`, `controller/docs/PaymentControllerDocs.java`,
  `controller/request/PreparePaymentRequest.java`, `controller/request/ConfirmPaymentRequest.java`
- Flyway — `V8__create_payments.sql`, `V9__add_reason_to_order_hold_release_outbox.sql` (h2/oracle 각각)

### 수정하는 것

- `HoldReleaseOutbox` — `reason` 필드 추가
- `HoldReleaseTask`, `HoldReleaseOutboxWriter`, `HoldReleaseOutboxTransactionService.load` — `reason` 전달
- `HoldReleaseTaskProcessor` — `PAYMENT_CONFIRMED`면 좌석 이벤트 발행 생략
- `OrderTerminationService` — 취소·만료에 각각 `USER_CANCELED`, `ORDER_EXPIRED` 전달
- `HoldHistory`, `HoldHistoryRecorder` — `confirmed` 팩토리와 `recordConfirmed` 추가
- `ErrorType` — 결제 관련 항목 추가
- `docs/core-booking-lifecycle.md` — 결제 확정 절 추가

### 건드리지 않는 것

- `SeatStatusPublisher` — 확정 시 발행할 이벤트가 없다
- `ExpireOrderUseCase` — PENDING만 조회하므로 확정 주문에 영향이 없다
- 좌석맵 조회 경로 — `PERFORMANCE_SEATS.state`를 읽는 기존 코드가 그대로 동작한다

## 검증

- `core-domain` 단위 테스트 — 승인 성공 시 네 전이와 outbox `reason`, 거절 시 주문 `PENDING` 유지,
  `confirm` 멱등 재호출, 만료 주문 거절, 금액 불일치, 좌석이 이미 `RESERVED`인 경우 롤백
- `HoldReleaseTaskProcessor` — `PAYMENT_CONFIRMED`면 `RELEASED`를 발행하지 않는다
- `core-api` — `PaymentControllerContractTest`
- 구조 테스트 — 패키지가 늘어나므로 ArchUnit·모듈 구조 테스트를 함께 돌린다
- `CoreQueryIndexMigrationTest` — 새 마이그레이션이 베이스라인 스키마에서 적용되는지 확인

## 범위 밖

- 실제 PG·카드사 연동, 웹훅, 서명 검증
- 결제 취소·환불, 확정된 좌석을 `AVAILABLE`로 되돌리는 역방향 전이
- 결제수단 확장(가상계좌, 간편결제)
- 부분 결제, 쿠폰, 포인트
