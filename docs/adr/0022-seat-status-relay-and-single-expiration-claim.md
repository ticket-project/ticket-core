# ADR 0022: 좌석 상태 알림은 Redis pub/sub으로 모든 인스턴스에 나누고, Redis 키 만료는 한 인스턴스만 처리한다

## 상태

제안

## 배경

Core를 여러 인스턴스로 늘리려면 인스턴스 메모리 안에서만 맞는 두 동작을 고쳐야 한다.

**좌석 상태 알림이 발행한 인스턴스 밖으로 나가지 않는다.** `WebSocketConfig`는 `enableSimpleBroker("/topic")`를
쓴다. simple broker는 같은 JVM에 붙은 STOMP 세션에만 보낸다. 그래서 인스턴스 A가 발행한 `SELECTED`·`HELD`·`RELEASED`는
B에 붙은 사용자에게 가지 않는다. B의 사용자는 남이 잡은 좌석을 빈 좌석으로 보고 눌렀다가 `E4001`이나 `E6000`을 받는다.

**Redis 키 만료 알림을 모든 인스턴스가 처리한다.** `RedisExpirationListenerConfig`는 `__keyevent@*__:expired`를
pub/sub으로 구독한다. pub/sub은 구독자 모두에게 보내므로 인스턴스가 N대면 만료 하나를 N번 처리한다.

- hold 메타 키 만료: `ExpireOrderUseCase.expireByHoldKey`가 N번 돈다. PENDING 주문 row lock 덕분에 결과는 한 번만
  반영되지만, DB 연결과 row lock 대기를 N배로 쓴다.
- 선택 키 만료: `SeatSelectionWriter.notifyReleasedIfFree`가 N번 돈다. 좌석마다 DB 조회, 좌석 락 획득, 점유 확인이
  N배가 되고, 만료가 몰리면 실제 사용자의 좌석 선택(`SELECT_LOCK`, 500ms 대기)과 같은 락을 다툰다.

두 문제는 따로 풀 수 없다. 지금 선택 만료의 `DESELECTED`가 모든 인스턴스의 사용자에게 가는 것은 모든 인스턴스가
만료를 처리하기 때문이다. 만료를 한 곳에서만 처리하면 `DESELECTED`가 그 인스턴스의 사용자에게만 간다. 반대로 알림을
모든 인스턴스로 나누면서 만료를 N번 처리하면 같은 `DESELECTED`가 N번 나간다.

바꾸지 않을 기존 성질은 다음과 같다.

- 서버는 좌석 상태 알림을 상태가 바뀐 순서대로 발행한다. 상태 변경과 발행을 같은 좌석 락 안에서 하기 때문이다
  ([예매 수명주기](../core-booking-lifecycle.md#좌석-선택과-해제-알림-순서)). 클라이언트 수신 순서는 보장하지 않으며,
  알림은 "좌석을 이 상태로 맞추라"는 멱등 알림이다.
- WebSocket destination `/topic/performance/{performanceId}/seats`와 payload(`SeatStatusEvent`)는 FE와 gatling-test가
  쓰는 공개 계약이다.
- hold 만료는 TTL 알림과 만료 누락 복구 scheduler(`OrderExpirationTrigger`)가 함께 지킨다. 한쪽만 바꾸지 않는다
  ([운영](../operations.md#redis-운영과-전환)).

## 결정

### 1. 좌석 상태 알림: Redis pub/sub relay와 인스턴스별 simple broker

```text
상태를 바꾼 인스턴스                         Redis                 모든 인스턴스(발행한 인스턴스 포함)
좌석 락 안: 상태 변경 → PUBLISH(동기) ──▶ seat-status channel ──▶ 구독 연결 1개 → 단일 스레드 dispatch
            → 락 해제                                              → SimpMessagingTemplate.convertAndSend
                                                                    → 로컬 simple broker → 자기 STOMP 세션
```

- `SeatStatusEventPublisher` 구현이 로컬 broker 대신 Redis channel 하나에 `PUBLISH`한다. `SeatStatusEvent`는 지금처럼
  발행 시각을 담아 만들고 JSON으로 보낸다. 발행한 인스턴스도 로컬 broker로 직접 보내지 않고 자기 구독으로 받아 보낸다.
  전달 경로가 하나뿐이라 중복도, 경로 사이의 순서 차이도 생기지 않는다.
- 모든 인스턴스는 그 channel을 구독하고, 받은 메시지를 `SeatStatusEvent`로 되살려 지금과 같은 destination으로
  `convertAndSend`한다. 같은 message converter를 거치므로 클라이언트가 받는 JSON은 지금과 같다.
- channel은 회차별로 나누지 않는다. 구독자가 없는 회차의 메시지는 simple broker가 버린다. 회차별 channel은 구독을
  켜고 끄는 관리가 필요하고, 지금 메시지 양에서는 얻는 것이 없다.
- 구독은 만료 리스너와 다른 `RedisMessageListenerContainer`에 둔다. 컨테이너 하나는 dispatch executor 하나를 쓰는데,
  만료 처리는 worker 2개로 병렬 처리하고 relay는 순서를 지키려고 단일 스레드로 처리해야 하기 때문이다.
- 구현 위치는 `booking.websocket`이다. 좌석 상태 알림을 실어 나르는 전송 기술이고, port와 공개 계약은 그대로다.

**순서 보장.** 지금의 보장(서버 발행 순서가 상태 변화 순서와 같다)을 인스턴스 사이로 넓힌다.

1. `PUBLISH`를 좌석 락 안에서 동기로 보내고 응답을 받은 뒤 락을 푼다. 그래서 다음 락 보유자의 `PUBLISH`는 Redis에
   그 뒤에 도착한다.
2. Redis는 명령을 하나씩 처리하고, 처리한 순서대로 각 구독 연결에 메시지를 쓴다.
3. 인스턴스마다 구독 연결과 dispatch 스레드가 하나라서 받은 순서대로 로컬 broker에 넣는다. 로컬 broker는
   `setPreservePublishOrder(true)`로 세션별 순서를 지킨다.

비동기 발행(`publishAsync`)이나 여러 스레드의 dispatch는 이 순서를 깨므로 쓰지 않는다.

**전달 보장.** Redis pub/sub은 at-most-once다. 구독 연결이 끊긴 동안의 메시지와 relay 큐가 넘쳐 버린 메시지는 다시
오지 않는다. 이것은 WebSocket이 끊긴 동안 클라이언트가 알림을 놓치는 것과 같은 종류의 손실이므로 같은 방법으로
메운다. 클라이언트는 연결하거나 다시 연결할 때 점유 좌석을 다시 조회한다.

- relay dispatch executor의 큐에는 상한을 둔다. 넘치면 메시지를 버리고 경고 로그와 메트릭을 남긴다. 호출 스레드에서
  실행하는 방식(`CallerRunsPolicy`)은 Redis 수신 스레드를 막고 순서도 깨므로 쓰지 않는다. **2026-10-26 변경**: 상한
  없이 단일 스레드만 두기로 했다([스케일 아웃 결정 목록](../plans/scale-out-decisions.md#d2-1-좌석-알림을-모든-인스턴스에-나누는-방법)
  D2-1).

**발행 실패.** 지금처럼 호출자에게 던진다. `HeldSeatPublisher`·`HoldReleaser`의 실패는 Event Publication Registry가
다시 시도하며 처리는 멱등이다. 좌석 선택·해제 요청은 지금처럼 실패 응답을 받는다.

### 2. Redis 키 만료: 처리권을 먼저 잡은 인스턴스 하나만 처리

**2026-10-26 변경**: 주문 선점(hold) 만료는 Redis 만료 알림 없이 5초 DB 청소 + ShedLock으로 바꾸고, 좌석 선택 만료는 keyspace
만료 알림을 유지하기로 했다([스케일 아웃 결정 목록](../plans/scale-out-decisions.md#d2-2-redis-만료-알림-중복-처리)
D2-2). 좌석 선택 만료는 아래 claim 설계로 한 대만 처리하고 `DESELECTED`를 결정 1의 relay로 보낸다. 아래의 hold
메타 키 claim(`ExpireOrderUseCase`)은 DB 청소로 바뀌어 채택하지 않는다. claim 위치는 아래 "claim은 worker 큐에 넣기
전에 한다"가 아니라 큐에서 꺼낸 뒤 handler가 키를 해석한 직후로 정했고, TTL은 60초다(D2-2). 이 절과 "배포 순서"·"검증
계획"은 D2-2의 나머지 세부 결정을 정한 뒤 다시 쓴다.

모든 인스턴스는 지금처럼 만료 알림을 받는다. 처리하기 전에 Redis `SET <claim key> <instance id> NX PX <claim TTL>`로
처리권(claim)을 잡고, 잡은 인스턴스만 handler를 실행한다.

```text
expired 알림(모든 인스턴스) → handler가 키를 해석(I/O 없음) → 아는 키만 claim
  → SET expired-claim:<만료된 키> NX PX 60000
       성공 → 처리 worker 큐에 넣는다(hold: ExpireOrderUseCase, 선택: notifyReleasedIfFree)
       실패 → 버린다(다른 인스턴스가 처리한다)
```

- **아는 키만 claim한다.** 만료 알림은 선택 키와 hold 메타 키뿐 아니라 hold 좌석 키, 인덱스, 락, 리프레시 토큰 등
  TTL이 있는 모든 키에서 온다. `RedisKeyExpirationHandler`를 키 해석과 처리로 나누고, 해석이 맞을 때만 claim한다.
- **claim은 worker 큐에 넣기 전에 한다.** 지금은 listener container가 worker 2개짜리 무제한 큐에 작업을 넣고, 큐에서
  꺼낸 뒤에 `onMessage`가 돈다. 그 뒤에 claim하면 큐가 밀린 인스턴스가 claim TTL이 지난 뒤 같은 만료를 다시 처리할
  수 있다. 알림을 받으면 바로 비동기로 claim을 보내고, 성공한 것만 worker 큐에 넣는다. 수신 스레드에서는 블로킹
  호출을 하지 않는다.
- **claim TTL은 같은 이름의 키가 다시 만료될 수 있는 최소 간격보다 짧아야 한다.** 그렇지 않으면 이전 claim이 다음
  만료의 처리를 막는다.
  - hold 메타 키(`hold:key:HOLD-<uuid>`)는 hold마다 이름이 달라서 다시 만료되지 않는다.
  - 선택 키(`seat:select:{perf:N}:<seatId>`)는 같은 좌석을 다시 선택하면 이름이 같다. 다시 만료되려면 선택 TTL
    (`SeatSelectionService.SELECT_TTL`, 5분)이 지나야 한다.
  - 그래서 claim TTL은 60초로 둔다. 인스턴스 사이의 알림 수신 차이(밀리초에서 초 단위)를 넉넉히 덮고 5분보다 충분히
    짧다. 선택 TTL을 줄일 때 claim TTL도 함께 줄이도록 두 값의 관계를 테스트로 고정한다.
- **claim한 인스턴스가 처리하기 전에 죽으면** hold 만료는 `OrderExpirationTrigger`(5분 주기)가 복구한다. 선택 만료
  알림은 사라진다. 지금 단일 인스턴스가 죽을 때와 같은 손실이며, 클라이언트의 재조회로 메운다.
- 비용은 만료 하나당 `SET NX` 명령 N개다. 지금 구조를 그대로 N대로 늘리면 만료 하나당 DB 조회, 락 획득·해제, 점유
  확인을 N번 하므로 그보다 작다.
- 만료 처리의 DB 동시 진입 상한은 인스턴스당 worker 2개로 그대로이며, 전체로는 2N이다. 인스턴스마다 자기가 claim한
  작업만 큐에 넣으므로 큐 적체도 약 1/N로 줄어든다.

### 배포 순서

relay 없이 claim만 넣으면 선택 만료의 `DESELECTED`가 claim한 인스턴스의 사용자에게만 간다. 그래서 다음 순서로 배포한다.

1. 인스턴스 하나인 상태에서 relay를 배포한다. 인스턴스가 하나라 동작은 같다. 좌석 선택·주문 경로의 지연(락 안의
   `PUBLISH` 왕복)과 relay 큐를 본다.
2. 인스턴스 하나인 상태에서 claim을 배포한다.
3. `/ws/**`의 SockJS 처리(아래 "결과")를 정한 뒤 인스턴스를 2대로 늘린다.

인스턴스가 2대 이상일 때 1·2를 롤링 배포하면, 로컬로 발행하는 옛 버전과 relay로 발행하는 새 버전이 섞여 일부 알림이
빠진다. 그래서 1·2는 인스턴스가 하나일 때 끝낸다. 되돌릴 때도 인스턴스를 하나로 줄인 뒤 되돌린다.

## 검토한 대안

### 좌석 상태 알림

| 대안 | 판단 |
| --- | --- |
| 외부 STOMP broker relay(RabbitMQ STOMP plugin, ActiveMQ Artemis) | Spring이 지원하는 방식(`enableStompBrokerRelay`)이고 구독 관리를 broker가 맡는다. 하지만 운영할 인프라가 하나 늘고, relay가 클라이언트 STOMP 세션마다 broker TCP 연결을 연다(인기 회차 동시 접속 수만큼). 이미 쓰는 Redis로 충분하므로 미룬다. 영속 메시지나 사용자별 큐가 필요해지면 다시 본다 |
| Redis Streams relay | 인스턴스의 구독이 끊겼다 붙어도 이어 읽을 수 있다. 하지만 클라이언트가 끊긴 동안의 손실은 그대로라 재조회가 어차피 필요하고, consumer 위치와 trim 관리가 늘어난다 |
| 회차 단위 sticky routing(회차를 한 인스턴스에 고정) | 인기 회차 하나에 사용자가 몰리는 서비스라 부하가 나뉘지 않는다 |
| Spring Modulith event externalization | 대상이 Kafka·AMQP·JMS·SQS·SNS이고, 좌석 선택 알림은 Modulith 이벤트도 아니다 |

### Redis 키 만료

| 대안 | 판단 |
| --- | --- |
| 리더 인스턴스 하나만 구독(Redisson 락으로 리더 선출) | 만료마다 드는 추가 명령이 없다. 하지만 리더가 바뀌는 동안의 만료 알림은 저장되지 않아 사라지고, 만료 폭주를 한 인스턴스의 worker 2개가 모두 받는다. 리더가 바뀔 때 구독을 켜고 끄는 코드도 필요하다 |
| 인스턴스 번호로 키를 나눔(hash % N) | 인스턴스 목록과 번호를 관리해야 하고, 대수가 바뀌는 순간 처리가 빠지거나 겹친다 |
| keyspace 알림 대신 만료 시각 Sorted Set을 주기적으로 읽고 `ZREM`으로 claim | 처리 대상이 Redis에 남으므로 장애 중에도 잃지 않고, 관리형 Redis의 `CONFIG SET` 제한과 Cluster의 노드별 구독 문제도 없어진다. 하지만 TTL·리스너·scheduler 세 경로를 함께 다시 설계해야 하고 polling 주기만큼 지연이 생긴다. 관리형 Redis나 Cluster로 옮길 때 다시 본다 |
| 중복 처리를 그대로 둠 | hold는 row lock으로, 선택 알림은 멱등성으로 결과가 맞는다. 하지만 비용이 N배이고, relay와 함께 쓰면 같은 `DESELECTED`가 N번 나간다 |

## 결과

- WebSocket destination과 payload는 바뀌지 않는다.
- 좌석 락 안에서 좌석마다 Redis `PUBLISH` 왕복이 하나 늘어난다. `HeldSeatPublisher`·`HoldReleaser`는 주문 좌석 수만큼
  늘어난다. 측정에서 문제가 되면 한 연결의 batch로 묶는다(같은 연결이라 순서가 유지된다). 좌석 선택·주문 경로가
  바뀌므로 [Core 수용량](../core-capacity.md#다시-재야-할-때)을 다시 잰다.
- Redis에 relay channel(이름은 구현할 때 정한다)과 claim 키 `expired-claim:*`(TTL 60초)가 생긴다. 기존 키 형식은
  바뀌지 않으므로 키 전환 절차는 필요 없다.
- FE는 연결하거나 다시 연결할 때 점유 좌석을 다시 조회해야 한다. 지금 `useSeatSocket`은 연결되면 구독만 하고 다시
  조회하지 않는다. 지금도 WebSocket이 끊긴 동안의 알림은 사라지므로 relay와 상관없이 필요한 변경이다. ticket-fe에서
  따로 진행한다.
- FE는 `sockjs-client`로 연결한다. WebSocket 전송이면 연결이 하나라 인스턴스가 여러 대여도 문제없다. 하지만
  xhr-streaming이나 polling으로 폴백하면 요청 여러 개가 같은 인스턴스로 가야 한다. 인스턴스를 늘리기 전에 `/ws/**`에
  sticky를 걸지, FE에서 SockJS를 걷어낼지 정한다.
- 운영 관측 항목이 늘어난다. relay executor 적체(`executor_queued_tasks`)와 버린 메시지 수, claim 성공·실패 수, Redis
  구독 연결의 재연결 횟수를 남긴다.
- 채택하면 [예매 수명주기](../core-booking-lifecycle.md)의 "좌석 선택과 해제 알림 순서"·"TTL 폭주와 누락 복구" 절과
  [운영](../operations.md)의 Redis·용량 관측 절을 함께 고친다.
- Redis Cluster로 옮기면 일반 `PUBLISH`는 노드 사이의 순서를 보장하지 않는다. 그때는 회차 hash tag를 쓰는 sharded
  pub/sub(`SPUBLISH`)을 다시 검토한다. hold 메타 키(`hold:key:*`)에 hash tag가 없어서 hold Lua 스크립트도 Cluster에서
  CROSSSLOT 오류가 나지만, 이 결정의 범위 밖이다.

## 검증 계획

- relay 단위 테스트: 발행이 channel에 `SeatStatusEvent` JSON을 보내는지, 수신이 지금과 같은 destination과 payload로
  `convertAndSend`하는지 확인한다.
- 두 컨텍스트 통합 테스트(Testcontainers Redis): 컨텍스트 A의 좌석 선택이 컨텍스트 B에 붙은 STOMP 클라이언트에
  도착하는지, 두 컨텍스트가 번갈아 같은 좌석을 선택·해제할 때 수신 순서가 상태 변화 순서와 같은지 확인한다.
- claim 테스트: 두 리스너가 같은 만료 키를 받으면 handler가 한 번만 도는지, 모르는 키는 claim하지 않는지, claim TTL이
  `SELECT_TTL`보다 짧은지 확인한다.
- 회귀: `SeatSelectionWriterTest`, `HeldSeatPublisherTest`, `HoldReleaserTest`, `WebSocketSeatStatusEventPublisherTest`와
  booking 예매 E2E를 돌린다.
- 운영: 2대 구성에서 gatling-test 03-3으로 수용량을 다시 잰다.

## 정하지 않은 것

- 결정 1(Redis pub/sub relay)은 2026-10-26에 정했다
  ([스케일 아웃 결정 목록](../plans/scale-out-decisions.md#d2-1-좌석-알림을-모든-인스턴스에-나누는-방법) D2-1). 채널은
  하나, relay 큐는 상한 없는 단일 스레드로 정했고, 발행 위치는 거기서 정한다. 결정 2는 같은 날 좌석 선택 만료를 keyspace
  알림 + claim + relay로 정했고([D2-2](../plans/scale-out-decisions.md#d2-2-redis-만료-알림-중복-처리)), 나머지 세부
  결정은 거기서 정한다. 두 결정의 세부를 정한 뒤 본문을 다시 쓰고 상태를 바꾼다.
- SockJS 폴백 처리: LB sticky(nginx `ip_hash` 등)와 FE의 SockJS 제거 중 무엇을 고를지.
