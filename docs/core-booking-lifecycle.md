# Core 예매 수명주기

이 문서는 주문 생성, 취소, 만료와 Redis hold 후처리의 실행 순서를 설명한다.
핵심 목적은 DB 트랜잭션과 외부 I/O의 경계를 한눈에 확인하는 것이다.

## 지켜야 할 원칙

- Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다.
- Redis TTL 이벤트가 한꺼번에 들어와도 DB로 진입하는 작업 수는 제한한다.
- 주문 상태 변경과 hold release outbox 적재는 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 리스너는 후처리를 직접 실행하지 않고 제한된 작업 큐에 제출만 한다.
- 즉시 후처리가 누락되거나 실패해도 보정 스케줄러가 outbox를 다시 처리한다.

## 주문 생성

~~~text
CreateOrderUseCase
  -> 요청/회차/좌석 검증
  -> Redis hold 생성                 (DB 트랜잭션 밖)
  -> CreatePendingOrderTxService
       -> PENDING 주문 저장          (짧은 DB 트랜잭션)
       -> hold history 저장
  -> DB 커밋 및 connection 반환
  -> HoldCreationPostCommitNotifier
       -> 제한된 background queue에 제출
       -> selection 강제 해제        (Redis)
       -> HELD 상태 발행             (WebSocket)
~~~

DB 저장이 실패하면 CreateOrderUseCase가 이미 만든 Redis hold를 보상 해제한다.
DB 커밋 뒤 후처리 제출이나 실행이 실패해도 커밋된 주문과 hold는 되돌리지 않는다.
hold가 실제 점유의 기준이며 selection은 UX 보조 상태이기 때문이다.

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
       -> hold 해제                  (Redis, DB transaction 없음)
       -> RELEASED 상태 발행         (WebSocket, DB transaction 없음)
       -> 완료 또는 재시도 기록      (짧은 write transaction)
~~~

같은 outbox를 즉시 작업자와 스케줄러가 동시에 집어도 outbox ID 분산락으로
외부 부수효과를 한 번에 하나만 실행한다. Redis 해제는 재시도 가능해야 하며,
실패한 outbox는 30초 뒤 다시 시도한다.

## TTL 폭주와 보정

Redis hold meta key가 만료되면 RedisKeyExpirationListener가
ExpireOrderUseCase.expireByHoldKey를 호출한다.

- redisExpirationTaskExecutor: worker 2개, queue 256개
- queue가 가득 차면 Redis 수신 스레드가 직접 처리해 유입 속도를 늦춘다.
- OrderExpirationScheduler: 5분마다 만료 주문을 100개씩 보정한다.
- HoldReleaseOutboxScheduler: 2분마다 미완료 outbox를 100개씩 보정한다.

두 스케줄러와 트랜잭션 이벤트 리스너는 core-infra에 위치한다.
core-domain은 상태 전이와 트랜잭션 단위만 소유한다.

## DB connection 관점

Hikari 최대 connection이 10개일 때 background 작업이 무제한으로 DB에
들어오면 요청 처리와 서로 connection을 빼앗는다. 현재 동시성 예산은 다음과 같다.

- Redis 만료 처리: 최대 2개 작업
- 주문 커밋 후 작업: 최대 2개 작업
- outbox 외부 I/O: DB connection 미점유

따라서 과거처럼 만료 handler가 바깥 connection을 잡은 채
REQUIRES_NEW로 두 번째 connection을 기다리는 순환 대기는 발생하지 않는다.

## 주요 코드

- 주문 생성: domain.order.command.create.CreateOrderUseCase
- 주문 DB 저장: domain.order.command.create.CreatePendingOrderTxService
- 주문 종료: domain.order.command.OrderTerminationService
- outbox 트랜잭션: domain.order.command.release.HoldReleaseOutboxTransactionService
- outbox 외부 처리: domain.order.command.release.HoldReleaseOutboxExecutor
- Redis TTL 진입 제한: infra.redis.RedisExpirationListenerConfig
- background queue와 트리거: infra.order

## 운영 확인

- hikaricp_connections_pending이 지속적으로 0인지 확인한다.
- redisExpirationTaskExecutor, bookingBackgroundTaskExecutor의 active/queued 값을 본다.
- background queue 포화 경고와 outbox 재시도 로그를 확인한다.
- 같은 조건의 연속 부하 테스트 전에는 이전 회차의 PENDING 주문, hold TTL,
  outbox backlog가 모두 정리됐는지 확인한다.
