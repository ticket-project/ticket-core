# Core 예매 수명주기

이 문서는 주문 생성, 취소, 만료와 Redis hold 후처리의 실행 순서를 설명한다.
핵심 목적은 DB 트랜잭션과 외부 I/O의 경계를 한눈에 확인하는 것이다.

## 지켜야 할 원칙

- Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다.
- Redis TTL 이벤트가 한꺼번에 들어와도 DB로 진입하는 작업 수는 제한한다.
- 주문 저장 또는 상태 변경과 그에 대응하는 hold outbox 적재는 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 트리거는 후처리를 직접 실행하지 않고 제한된 작업 큐에 제출만 한다.
- 즉시 후처리가 누락되거나 실패해도 보정 스케줄러가 outbox를 다시 처리한다.

## 주문 생성

~~~text
CreateOrderUseCase
  -> 요청/회차/좌석 검증
  -> Redis hold 생성                 (DB 트랜잭션 밖)
  -> CreatePendingOrderTxService
       -> PENDING 주문 저장          (짧은 DB 트랜잭션)
       -> hold history 저장
       -> hold creation outbox 저장
  -> DB 커밋 및 connection 반환
  -> HoldCreationPostCommitNotifier
       -> 제한된 background queue에 outbox ID 제출
  -> HoldCreationOutboxExecutor
       -> outbox 조회                (짧은 read transaction)
       -> 같은 좌석의 hold 분산락 획득
       -> 기록된 holdKey가 현재 hold인지 확인
       -> 주문 회원 소유 selection만 해제 (Redis)
       -> HELD 상태 발행             (WebSocket)
       -> 완료 또는 재시도 기록      (짧은 write transaction)
~~~

DB 저장이 실패하면 CreateOrderUseCase가 이미 만든 Redis hold를 보상 해제한다.
DB 커밋 뒤 후처리 제출이나 실행이 실패해도 커밋된 주문과 hold는 되돌리지 않는다.
대신 주문과 같은 트랜잭션에 저장된 creation outbox를 스케줄러가 다시 처리한다.
따라서 메모리 queue가 가득 차거나 프로세스가 재시작되어도 작업 입력은 DB에 남는다.
hold가 실제 점유의 기준이며 selection은 UX 보조 상태이다.
주문 생성 후처리와 hold 해제는 같은 좌석 잠금을 사용한다. 해제가 먼저 끝났다면
이전 hold의 후처리는 아무 작업도 하지 않고, 생성 후처리가 먼저라면 HELD 뒤에
RELEASED가 발행된다. WebSocket은 세션별 발행 순서를 보존한다.

주문 시작, 주문 상세, 주문 상태 응답의 시간 계약은 동일하다. `expiresAt`은 서버의 절대
만료 시각이고, `remainingSeconds`는 응답을 만드는 서버 시각부터 `expiresAt`까지 남은
완전한 초다. PENDING이 아니거나 이미 만료 경계를 지났으면 0이다. 클라이언트는 로컬
시계로 `expiresAt - now`를 다시 계산하지 않고 `remainingSeconds`로 카운트다운을 시작한 뒤,
상태 조회 응답으로 주기적으로 보정한다.

## 주문 취소와 만료

취소는 소유권과 현재 상태를 검증하고, 만료는 orderId 또는 holdKey로
PENDING 주문을 잠근다. 이후 공통 절차는 OrderTerminationService가 담당한다.

~~~text
CancelOrderUseCase / ExpireOrderUseCase
  -> PENDING 주문 row lock
  -> OrderTerminationService
       -> 주문 좌석 검증
       -> CANCELED 또는 EXPIRED 전이
       -> hold history 저장
       -> hold release outbox 저장
  -> DB 커밋 및 connection 반환
  -> HoldReleaseAfterCommitListener
       -> 제한된 background queue에 outbox ID 제출
  -> HoldReleaseOutboxExecutor
       -> outbox 조회                (짧은 read transaction)
       -> 좌석별 현재 holdKey 확인
       -> 일치하는 hold만 해제        (Redis, DB transaction 없음)
       -> hold 해제 완료 단계 기록    (짧은 write transaction)
       -> 현재 hold/selection이 없는 좌석만 RELEASED 발행 (WebSocket, DB transaction 없음)
       -> 완료 또는 재시도 기록      (짧은 write transaction)
~~~

같은 outbox를 즉시 작업자와 스케줄러가 동시에 집어도 outbox ID 분산락으로
외부 부수효과를 한 번에 하나만 실행한다. 다만 이 락은 **동시 실행**을 직렬화할 뿐,
첫 실행의 부수효과 뒤 완료 기록이 실패해서 나중에 순차 재실행되는 것까지 막지는 않는다.
그래서 생성 후처리는 현재 holdKey를 다시 확인한다. 해제 후처리는 Redis 해제 성공 단계를
WebSocket 발행 전에 outbox에 기록한다. 발행이 실패한 재시도에서는 Redis 해제를 반복하지 않고,
현재 hold와 selection이 모두 없는 좌석만 RELEASED로 다시 발행한다. 새 hold나 selection이 생긴
좌석에는 오래된 해제 알림을 보내지 않는다.

완료 기록이 실패하면 같은 RELEASED가 다시 발행될 수 있다. 이 이벤트는 좌석을 특정 상태로 맞추는
멱등 상태 알림으로 취급하며 전달 보장은 at-least-once이다. 중복보다 누락을 피하되, 매 발행 직전
현재 상태 검증으로 더 최신 상태를 덮어쓰지 않는 것이 기준이다.

실패 시 `nextAttemptAt`은 현재 시각의 30초 뒤로 기록된다. 이는 정확히 30초 뒤 실행된다는 뜻이
아니라 **그 시각부터 재시도 대상이 된다**는 뜻이다. 정상 경로는 커밋 직후 작업자가 즉시 실행하고,
그 실행을 놓친 작업은 2분 주기의 scheduler가 다음 조회에서 처리한다.
스케줄러가 한 페이지에서 처리 시작 실패를 만나면 그 실행은 다음 페이지 재조회 없이
끝낸다. 따라서 첫 100건이 그대로 남은 상황에서 같은 페이지를 무한 반복하지 않는다.

## TTL 폭주와 보정

Redis hold meta key가 만료되면 RedisKeyExpirationListener가
ExpireOrderUseCase.expireByHoldKey를 호출한다.

- redisExpirationSubscriptionExecutor: Redis 구독 전용 worker 1~2개
- redisExpirationTaskExecutor: 만료 handler worker 2개, queue 256개, 공유 permit 2개
- queue가 가득 차면 Redis 수신 스레드도 같은 permit을 얻은 뒤 처리해 유입 속도를 늦춘다.
- OrderExpirationTrigger(bootstrap) -> ExpirePendingOrdersUseCase: 5분마다 만료 주문을 100개씩 보정한다.
- HoldOutboxRelayTrigger(bootstrap) -> HoldCreationOutboxRelay: 2분마다 미완료 생성 후처리 outbox를 100개씩 보정한다.
- HoldOutboxRelayTrigger(bootstrap) -> HoldReleaseOutboxRelay: 2분마다 미완료 outbox를 100개씩 보정한다.

보정 주기는 bootstrap의 `worker.*.fixed-delay` 설정이고, `worker.enabled=false`면 트리거 자체가
등록되지 않는다.

@Scheduled 트리거는 bootstrap에 있고, 커밋 후 리스너와 outbox relay는 core-infra에 있다.
core-domain은 엔티티의 상태 전이 규칙만 소유한다. core-app은 트랜잭션 단위와 업무 후처리를 소유하고,
후속 처리 이벤트는 IntegrationEventPublisher 포트로 발행한다. outbox는 그 포트의 구현 방식이다.

## DB connection 관점

Hikari 최대 connection이 10개일 때 background 작업이 무제한으로 DB에
들어오면 요청 처리와 서로 connection을 빼앗는다. 현재 동시성 예산은 다음과 같다.

- Redis 만료 처리: 최대 2개 작업
- 주문 커밋 후 작업: 최대 2개 작업
- outbox 외부 I/O: DB connection 미점유

따라서 과거처럼 만료 handler가 바깥 connection을 잡은 채
REQUIRES_NEW로 두 번째 connection을 기다리는 순환 대기는 발생하지 않는다.

## 주요 코드

- 주문 생성: app.order.command.CreateOrderUseCase
- 주문 DB 저장: app.order.command.CreatePendingOrderTxService
- 주문 종료: app.order.command.OrderTerminationService
- 상태 전이 규칙: domain.order.model.Order (confirm, expire, cancel)
- 후속 처리 발행 포트: app.event.IntegrationEventPublisher
- outbox 엔티티와 발행 구현: infra.order.outbox (HoldCreationOutbox, HoldReleaseOutbox,
  OutboxIntegrationEventPublisher)
- 생성 outbox 실행: infra.order.outbox.create (HoldCreationOutboxExecutor, HoldCreationOutboxRelay)
- 해제 outbox 실행: infra.order.outbox.release (HoldReleaseOutboxExecutor, HoldReleaseOutboxRelay)
- 해제 업무 처리: app.order.command.HoldReleaseTaskProcessor
- 만료 보정: app.order.command.ExpirePendingOrdersUseCase
- background 트리거: bootstrap.worker
- Redis TTL 진입 제한: infra.redis.RedisExpirationListenerConfig
- background queue와 트리거: infra.order

## 운영 확인

- hikaricp_connections_pending이 지속적으로 0인지 확인한다.
- redisExpirationSubscriptionExecutor, redisExpirationTaskExecutor,
  bookingBackgroundTaskExecutor의 active/queued 값을 본다.
- background queue 포화 경고와 outbox 재시도 로그를 확인한다.
- 같은 조건의 연속 부하 테스트 전에는 이전 회차의 PENDING 주문, hold TTL,
  outbox backlog가 모두 정리됐는지 확인한다.
