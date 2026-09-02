# Core 예매 수명주기

이 문서는 주문 생성, 취소, 만료와 Redis hold 후처리의 실행 순서를 설명한다. 핵심 목적은 DB
트랜잭션과 외부 I/O의 경계, 그리고 Spring Modulith 이벤트가 어떻게 이어지는지 한눈에 확인하는
것이다. 모듈 경계 결정 배경은 [ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)을
본다.

이 문서가 설명하는 코드는 모두 `booking` Application Module 소유다(`com.ticket.booking.internal.**`).
커밋 후 처리를 관리하는 `EventPublicationMaintenance`만 전역 설정 패키지
(`com.ticket.bootstrap.config`)에 있다.

## 지켜야 할 원칙

- Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다.
- Redis TTL 이벤트가 한꺼번에 들어와도 DB로 진입하는 작업 수는 제한한다.
- 주문 저장 또는 상태 변경과 그에 대응하는 이벤트 발행은 같은 DB 트랜잭션에서 처리한다.
- 커밋 후 처리는 `@ApplicationModuleListener`가 담당하고, 실패는 catch-and-log로 삼키지 않고
  throw해 Event Publication Registry가 FAILED로 기록하고 재시도하게 한다.
- 다른 모듈 API 호출(catalog 정책 조회, identity 회원 확인, admission token 검증)은 booking DB
  트랜잭션 밖에서 끝낸다.

## 주문 생성

~~~text
CreateOrderUseCase
  -> LockScope.ORDER_START 락(같은 회원·회차 직렬화)
  -> CreateOrderValidator
       -> catalog BookingPolicyLookup: 예매 정책·좌석 소속·가격 snapshot (DB 트랜잭션 밖)
       -> admission AdmissionVerifier: 대기열 필요 회차만 token 검증 (밖)
       -> identity MemberLookup: active member 확인 (밖)
       -> booking local read: pending 주문 중복, 좌석 판매 상태 (짧은 read 트랜잭션)
  -> LockScope.SEAT 락 안에서 Redis에 좌석 hold 생성 (밖)
  -> CreatePendingOrderTransactionService
       -> PENDING 주문·OrderSeat·hold history 저장     (booking DB 트랜잭션)
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

`EventPublicationMaintenance`(`com.ticket.bootstrap.config`)가 두 가지 주기 작업을 한다.

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
- `OrderExpirationTrigger`(`com.ticket.bootstrap.worker`, 아직 이동하지 않은 legacy 위치) →
  `ExpirePendingOrdersUseCase`: `worker.order-expiration.fixed-delay`(기본 5분)마다 만료 주문을
  보정한다.

`@Scheduled` 트리거(`OrderExpirationTrigger`)는 아직 `com.ticket.bootstrap`(legacy)에 있고,
booking 모듈로 옮기는 것은 이후 정리 작업의 범위다. `worker.enabled=false`면 이 트리거 자체가
등록되지 않는다.

**주문 커밋 후 이벤트 리스너(`BookingEventListeners`)에는 과거 outbox worker 같은 명시적
동시성 상한이 설정돼 있지 않다.** `@ApplicationModuleListener`는 기본적으로 비동기 실행되며,
전용 `ThreadPoolTaskExecutor`를 따로 구성하지 않았으므로 Spring Boot의 기본 비동기 task
executor를 쓴다. Redis 만료 처리(`redisExpirationTaskExecutor`)처럼 명시적으로 2개로 제한된
경로와 달리, 이벤트 리스너 동시 실행 수는 운영 중 관측(아래 참고)으로 확인해야 한다. 이 상한이
필요하다고 판단되면 전용 executor 도입을 별도로 결정한다.

## 주요 코드

- 주문 생성: `booking.internal.application.order.command.CreateOrderUseCase`,
  `CreateOrderValidator`
- 주문 DB 저장: `booking.internal.application.order.command.CreatePendingOrderTransactionService`
- 주문 종료: `booking.internal.application.order.command.OrderTerminationService`
- 상태 전이 규칙: `booking.internal.domain.order.model.Order`(confirm, expire, cancel)
- 공개 이벤트: `booking.OrderStarted`, `booking.OrderTerminated`
- 커밋 후 리스너: `booking.internal.application.BookingEventListeners`
- hold 생성/해제 후속 처리: `booking.internal.application.order.command.HoldCreationTaskProcessor`,
  `HoldReleaseTaskProcessor`, `booking.internal.application.event.HoldReleaseProgressRecorder`
- 만료 보정: `booking.internal.application.order.command.ExpirePendingOrdersUseCase`
- background 트리거(legacy): `bootstrap.worker.OrderExpirationTrigger`
- Redis TTL 진입 제한: `booking.internal.infrastructure.redis.RedisExpirationListenerConfig`
- event publication 운영: `bootstrap.config.EventPublicationMaintenance`

## 운영 확인

- hikaricp_connections_pending이 지속적으로 0인지 확인한다.
- redisExpirationSubscriptionExecutor, redisExpirationTaskExecutor의 active/queued 값을 본다.
- `EventPublicationMaintenance.isRetryable`이 남기는 `ERROR` 로그(최대 재시도 초과)를 alert로
  감시한다.
- `EVENT_PUBLICATION`/`EVENT_PUBLICATION_ARCHIVE` 테이블에서 오래 남아 있는 미완료 publication이
  없는지 확인한다.
- 같은 조건의 연속 부하 테스트 전에는 이전 회차의 PENDING 주문, hold TTL, 미완료 event
  publication이 모두 정리됐는지 확인한다.
