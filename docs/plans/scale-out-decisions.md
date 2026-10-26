# 스케일 아웃 결정 목록

작성 2026-10-25. Core를 여러 인스턴스로 늘리기 전에 정해야 할 것을 모은 작업 문서다. 2026-10-17 코드 리뷰의
수평 확장 논의에서 남은 결정과 구현도 [남은 작업](../todo.md)에서 옮겨 왔다. 정한 항목은 ADR이나
[운영](../operations.md)·[예매 수명주기](../core-booking-lifecycle.md)로 옮기고 여기서는 상태만 바꾼다.

상태는 세 가지다.

- `미정`: 아직 논의하지 않았다.
- `추천 있음`: 추천안은 있지만 확정하지 않았다.
- `정함`: 확정했다. 옮긴 문서를 함께 적는다.

## 한눈에 보기

| ID | 정할 것 | 이게 정해져야 하는 일 | 상태 | 추천 |
| --- | --- | --- | --- | --- |
| D0-1 | 스케일 아웃의 목적과 목표 입장률 | 아래 전부 | 미정 | — |
| D0-2 | 60 u/s 멈춤(P-022) 수정 방식 | 수용량 측정 | 미정 | — |
| D0-3 | 서버 한 대를 먼저 정리할지 | D1 전체 | 미정 | 먼저 정리 |
| D1-1 | Redis를 어디에 둘지 | D2 전체 | 미정 | Core 호스트 밖의 단일 노드 |
| D1-2 | 요청을 어떻게 나눌지(LB) | 2대 운영 | 미정 | 지금 nginx에 upstream 추가 |
| D1-3 | 어떻게 배포할지 | 2대 운영 | 미정 | 한 대씩 교체, readiness·liveness 분리 |
| D1-4 | Core Redis 영속성 | 2대 운영 | 미정 | — |
| D1-5 | Redis 확장 순서 | D1-1 이후 | 미정 | 복제본 → 용도별 분리 → 클러스터 |
| D2-1 | 좌석 알림을 모든 인스턴스에 나누는 방법 | 2대 운영 | 추천 있음 | Redis pub/sub relay |
| D2-2 | Redis 만료 알림 중복 처리 | 2대 운영 | 정함 | 좌석 선택은 keyspace 알림 + claim, 주문 만료는 Redis Sorted Set 장부. 세부 결정 남음 |
| D2-3 | OAuth2 로그인 세션 | 2대 운영 | 미정 | Spring Session Redis(확인 필요) |
| D2-4 | SockJS 연결 | 2대 운영 | 미정 | FE가 순수 WebSocket으로 연결 |
| D2-5 | FE 재연결 때 좌석 다시 조회 | — | 추천 있음 | 한다 |
| D3-1 | 스케줄러 중복 실행 | — | 미정 | worker를 한 대에만 켬 |
| D3-2 | DB 연결 풀 크기 | 2대 운영 | 미정 | 대당 10 유지 |
| D3-3 | 수용량 다시 재기 | Queue 설정 | 미정 | 2대 구성으로 03-3 사다리 |
| D4-1 | 롤링 배포 중 호환 규칙 | 첫 롤링 배포 | 미정 | expand/contract |
| D4-2 | 관측과 시계 | — | 미정 | 인스턴스 태그 확인 |

정하는 순서는 맨 아래 "진행 순서"에 있다.

## 지금 상태(2026-10-25)

- 운영은 EC2 한 대(메모리 1.9GB)에 `ticket-be`, `ticket-redis`, `ticket-fe`, `ticket-nginx`, `datadog-agent`,
  `ticket-certbot` 컨테이너가 함께 떠 있다. Core JVM에는 `-Xmx`가 없다.
- DB는 RDS PostgreSQL이고 `max_connections`는 79다. Core 연결 풀은 Hikari 기본값 10, Tomcat 스레드는 200이다.
- 같은 날 운영 사다리에서 50 u/s는 PASS, 60 u/s는 FAIL이었다. 60 u/s 실패는 CPU 부족이 아니라 판매 정책 캐시와 연결
  풀이 서로 기다린 교착(P-022)이다. 50 u/s에서 호스트 CPU는 평균 0.94였다(gatling-test capacity-log
  2026-10-25 운영 사다리).

## D0. 전제

### D0-1. 스케일 아웃의 목적과 목표 입장률

- **정할 것**: 늘리는 목적이 처리량인지, 가용성(한 대가 죽어도 서비스 유지)인지, 둘 다인지. 처리량이 목적이면 몇
  users/sec가 필요한지.
- **왜**: 목표가 없으면 몇 대가 필요한지, 서버 한 대 정리(D0-3)로 충분한지 판단할 수 없다. 가용성이 목적이면 처리량이
  충분해도 2대가 필요하다.

### D0-2. 60 u/s 멈춤(P-022) 수정 방식

- **정할 것**: gatling-test capacity-log 2026-10-25의 해결 선택지 A~E 중 무엇으로 고칠지.
  - A. 판매 정책 캐시 조회를 트랜잭션 밖으로 옮긴다.
  - B. `@Cacheable`의 `sync = true`를 뺀다.
  - C. 미리 새로 고치는 캐시(`refreshAfterWrite`)로 바꾼다.
  - D. 캐시 유지 시간을 늘린다.
  - E. Hikari 연결 대기 시간 초과를 줄인다(보조 수단).
- **왜**: 인스턴스를 늘려도 이 교착은 인스턴스마다 그대로 생긴다. 대당 부하가 줄어 늦게 날 뿐이다. 그래서 스케일
  아웃과 별개로 먼저 고친다.

### D0-3. 서버 한 대를 먼저 정리할지

- **정할 것**: 인스턴스를 늘리기 전에 다음을 먼저 할지.
  - FE·Redis·Datadog Agent를 Core 호스트에서 분리한다.
  - 인스턴스 사양을 올린다.
  - JVM `-Xmx`를 정한다.
- **왜**: 호스트 CPU 0.94는 Core만이 아니라 함께 뜬 컨테이너 전체의 사용량이다. 분리나 사양 상향은 코드 변경 없이
  한계를 올릴 수 있다.
- **추천**: 먼저 정리하고 다시 잰다. 목표(D0-1)에 못 미칠 때만 D1 이후로 간다. 가용성이 목적이면 정리와 함께 D1로 간다.

## D1. 인프라

### D1-1. Redis를 어디에 둘지

- **정할 것**: 모든 Core 인스턴스가 함께 쓸 Redis의 위치와 종류.
- **왜**: 선택·선점·락·토큰·좌석 알림 relay가 모두 Redis에 있다. 지금 Redis는 Core와 같은 호스트에 있어서, 다른
  호스트의 Core가 붙으면 그 호스트가 장애 지점이 되고 CPU도 나눠 쓴다.
- **선택지**:
  - 지금 호스트의 Redis에 다른 Core가 네트워크로 붙는다. 가장 쉽지만 위 문제가 그대로다.
  - 별도 EC2에 Redis를 둔다. 지금과 같은 설정을 쓸 수 있다.
  - ElastiCache 단일 노드를 쓴다. 기동 시 `CONFIG SET notify-keyspace-events`가 막히므로 파라미터 그룹에서 설정해야
    한다(`RedisExpirationListenerConfig`).
  - Redis Cluster는 쓰지 않는다. 만료 알림을 노드마다 따로 구독해야 하고, hold 메타 키(`hold:key:*`)에 hash tag가
    없어 hold Lua 스크립트가 CROSSSLOT 오류를 낸다.
- **추천**: Core 호스트 밖의 단일 노드(별도 EC2 또는 ElastiCache). 그 다음 확장 순서는 D1-5에서 정한다.

### D1-2. 요청을 어떻게 나눌지(LB)

- **정할 것**: 지금 nginx에 upstream을 여러 개 두는지, ALB 같은 관리형 LB로 바꾸는지. Core를 같은 호스트에 여러 개
  띄우는지, 호스트를 늘리는지.
- **왜**: 같은 호스트에 Core 컨테이너를 2개 띄우면 CPU가 늘지 않는다. 처리량이 목적이면 호스트가 늘어야 한다.
- **선택지**:
  - 같은 호스트에 Core 컨테이너 2대: 비용이 들지 않고 무중단 배포와 2대 정합성(D2)을 검증할 수 있다. 처리량은 거의
    늘지 않는다.
  - EC2 여러 대와 ALB.
  - ECS.
  - 처리량이 급하면 이 셋보다 수직 확장(D0-3)이 먼저다.
- **함께 볼 것**: 상태 확인은 `/actuator/health/readiness`를 쓴다(probe는 이미 켜져 있다). nginx의
  `worker_connections`·`limit_req`는 노드마다 따로 적용된다.
- **추천**: 지금 nginx에 upstream을 추가하는 것이 가장 작은 변경이다. 가용성까지 원하면 nginx도 이중화가 필요하므로
  ALB를 검토한다.

### D1-3. 어떻게 배포할지

- **정할 것**: 여러 인스턴스를 어떤 순서로 바꿀지.
- **왜**: [배포 workflow](../../.github/workflows/deploy.yml)는 컨테이너 이름 `ticket-be` 하나, 서버 한 대, 배포 뒤
  nginx 재시작을 전제로 한다.
- **추천**: 한 대씩 LB에서 빼고, 바꾸고, readiness가 UP이면 다시 넣는다. graceful shutdown 대기 시간과 WebSocket
  재연결이 한꺼번에 몰리는 것을 함께 본다.
- **readiness·liveness 분리**: 지금 workflow는 `/actuator/health` 하나로 배포 성공을 판단한다. LB에 넣을지는
  `/actuator/health/readiness`, 컨테이너를 다시 띄울지는 `/actuator/health/liveness`로 나눌지 정한다.

### D1-4. Core Redis 영속성

- **정할 것**: Core Redis에 AOF를 켤지, 메모리가 찼을 때 `noeviction`을 쓸지.
- **왜**: Core Redis는 좌석 hold를 담는다. 재시작으로 데이터를 잃거나 메모리 부족으로 키가 밀려나면, 주문은 PENDING인데
  좌석은 빈 것으로 보인다. 좌석 단위 DB 방어선이 아직 없어서([남은 작업](../todo.md)의 "좌석 단위 DB 방어선") 이 손실은
  겹친 PENDING 주문으로 이어질 수 있다.
- **함께 볼 것**: ticket-queue는 부하 테스트 격리 때문에 AOF를 껐다. Core는 저장하는 데이터가 달라서 그 설정을 따라가지
  않는다.

### D1-5. Redis 확장 순서

- **정할 것**: Core 호스트 밖으로 옮긴 뒤(D1-1) Redis를 어떤 순서로 키울지.
- **예상 순서**: 복제본과 자동 failover → 용도별 분리(좌석, 인증 토큰, 알림) → 클러스터.
- **함께 볼 것**: 좌석 키는 모두 `{perf:N}` hash tag라서, 클러스터에서도 회차 하나의 부하는 서버 한 대로 간다. 인기 회차
  하나의 한계는 클러스터로 올라가지 않는다. 클러스터로 옮길 때는 주문 만료 장부를 전역 하나에서
  회차별로 바꾸고 회차 목록 설계를 함께 적용한다. 좌석 선택 만료는 keyspace 알림을 쓰므로 노드마다 구독해야 한다(D2-2).

## D2. 늘리자마자 기능이 깨지는 것(P0)

### D2-1. 좌석 알림을 모든 인스턴스에 나누는 방법

- **문제**: `WebSocketConfig`의 simple broker는 같은 서버에 붙은 사용자에게만 보낸다. 서버 A가 보낸 `SELECTED`·`HELD`·
  `RELEASED`를 서버 B의 사용자는 받지 못한다.
- **선택지**: Redis pub/sub relay, 외부 STOMP 브로커(RabbitMQ 등), Redis Streams.
- **추천**: Redis pub/sub relay. 좌석 락 안에서 Redis에 동기로 발행하고, 모든 인스턴스가 단일 스레드로 받아 자기
  사용자에게 보낸다. 발행 순서 보장과 WebSocket 계약이 그대로 유지된다. 상세는
  [ADR 0022](../adr/0022-seat-status-relay-and-single-expiration-claim.md) 결정 1.
- **세부 결정**:
  - 채널 단위: ADR 0022는 채널 하나를 모든 인스턴스가 구독한다. 논의했던 다른 안은 `{perf:N}` hash tag 채널과 회차별
    동적 구독이다. 관리형 클러스터 Redis로 가면 sharded pub/sub(패턴 구독 불가)으로 바꿔야 하므로, 그 전환 조건을
    ADR에 적을지 정한다.
  - relay 큐: ADR 0022의 큐 상한·버림 정책·경보 기준을 빼고 단일 스레드 executor만 두는 단순안으로 줄일지.
  - 발행 위치: 좌석 락 안의 Java 대신 상태를 바꾸는 Lua 스크립트 안에서 `PUBLISH`하면 락 없이도 순서가 지켜진다.
    [좌석 분산 락 다시 설계하기](seat-lock-redesign.md)에서 검토 중이다(2026-10-26).

### D2-2. Redis 만료 알림 중복 처리

- **문제**: Redis는 키를 지울 때 "이 키 지웠어"라고 구독자 모두에게 방송한다. 인스턴스가 N대면 모두가 듣고 모두가
  처리한다.
  - 주문 선점 만료: 주문을 `EXPIRED`로 바꾼다. 결과는 한 번만 반영되지만 DB 연결과 lock 대기를 N배로 쓴다.
  - 좌석 선택 만료: "좌석 비었어요"(`DESELECTED`)를 보낸다. DB 조회와 좌석 락 경합이 N배이고, D2-1을 하면 같은
    알림이 N번 나간다.
- **선택지**: 먼저 손든 인스턴스만 처리(claim), 리더 한 대만 구독, 만료를 처리하는 worker 1대 분리(ticket-queue와 같은
  방식), Redis 지연 큐(만료 예정 Sorted Set), 외부 메시지 큐의 예약 배달, 중복 허용.
- **결정(2026-10-26)**: 만료 종류마다 다르게 처리한다. 같은 날 claim → 둘 다 Sorted Set 장부 → 지금 안으로 바꿨다.
  - **좌석 선택 만료: 지금처럼 Redis TTL + keyspace 만료 알림 → WebSocket `DESELECTED`.** 좌석을 고를 수 있는지는
    선택 키 TTL이 정하고, 만료 처리가 하는 일은 다른 사용자 화면에 "좌석 비었어요"를 알리는 것뿐이다. 알림이 늦거나
    빠져도 다음 조회에서 맞춰진다. 인스턴스가 N대일 때의 중복은 claim으로 막는다(아래 "좌석 선택 만료 알림의 중복").
  - **주문 선점(hold) 만료: Redis Sorted Set 장부(지연 큐).** hold를 저장할 때 "이 시각에 이 주문을 만료"를 장부에
    등록하고, Worker가 주기적으로 시각이 지난 작업을 꺼내 꺼내기에 성공한 한 대만 처리한다(`ExpireOrderUseCase`). hold
    메타 키(`hold:key:*`)의 keyspace 만료 알림은 쓰지 않는다.
  - 주문 만료를 장부로 고른 이유: 화면 알림(`RELEASED`)만이 아니라 주문을 `EXPIRED`로 바꾸는 일이다. 늦으면 그 회원은
    같은 회차에 다시 주문하지 못한다(E5004). 회원·회차당 PENDING 하나(`uk_orders_pending_member_performance`)를 지키는
    주문 전 검사(`BookingPreflightReader`)가 만료 시각이 아니라 상태만 보기 때문이다. keyspace 알림은 구독이 끊긴 동안의
    알림이 사라지고, 키가 실제로 지워질 때 나가서 TTL 시각보다 늦을 수 있으며, 클러스터에서는 노드마다 따로 구독해야
    한다([Redis 문서](https://redis.io/docs/latest/develop/pubsub/keyspace-notifications/)). 장부는 항목이 남아 있어
    서버가 꺼져 있던 동안의 만료도 처리한다. 지연 작업 구조를 공개한 Netflix dyno-queues, Sidekiq, Airbnb Dynein도 실행
    시각을 저장해 두고 주기적으로 꺼낸다.
  - 감수하는 것: 주문 만료는 확인 주기만큼 늦다. 꺼낸 뒤 처리하기 전에 서버가 죽으면 그 작업은 잃고, 5분 주기
    스케줄러(`OrderExpirationTrigger`)가 DB를 보고 복구한다. 좌석 선택 만료는 keyspace 알림의 누락·지연을 그대로 안고 FE
    재조회(D2-5)로 메운다. 좌석 선택 때문에 keyspace 알림과 기동 때의 `CONFIG SET notify-keyspace-events`
    (`RedisExpirationListenerConfig`)가 남으므로, D1-1의 관리형 Redis 파라미터 그룹 설정과 D1-5의 노드별 구독 문제도
    남는다.
  - 고르지 않은 안: 둘 다 장부로 옮기는 안은 좌석 선택 Lua에도 등록을 넣어야 하는데, 좌석 선택 만료는 화면 알림뿐이라
    얻는 것이 작다. 둘 다 claim(keyspace 알림 + `SET NX`)으로 두는 안은 주문 만료가 알림 누락·지연에 기댄다. 주문 만료만
    DB 청소 주기를 줄이는 안은 설정값 하나로 되지만 몇 초마다 DB를 조회한다.
  - 처리 구조(2026-10-26 정함): hold를 저장하는 Lua가 장부에 작업을 등록하고, booking 안의 Expiration Worker가 장부를
    확인해 주문 만료 처리기에 넘긴다. 등록은 hold Lua 안에서 상태 변경과 함께 한다. 따로 등록하면 "hold는 됐는데
    장부에는 없음" 틈이 생긴다. 좌석 선택 만료는 지금의 `RedisKeyExpirationListener`·`SeatSelectionExpirationHandler`가
    그대로 맡는다.
  - 만들지 않는 것: 별도 서비스, 아무 모듈이나 부르는 범용 예약 API, 재시도·실패 보관함·우선순위. Redisson의 지연 큐는
    쓸 수 없다. `RDelayedQueue`는 deprecated이고 `RReliableQueue`는 PRO 전용이다(4.7.0).
- **세부 결정**(장부 키·회차 목록·확인 서버는 주문 만료 장부, 마지막 항목은 좌석 선택):
  - 장부 키(2026-10-26 정함): 전역 장부 하나에 주문 만료 작업을 둔다. 키 이름은 구현할 때 정한다. Worker는 키 하나만
    확인하면 되고, Redis가 한 대인 지금은 hold Lua 안에서 그대로 등록할 수 있다. 같은 날 전역 → 회차별 → 전역 순으로
    바꿨다.
    - 회차별 장부(`{perf:N}` hash tag)는 지금은 고르지 않았다. 클러스터에서도 Lua 안에서 등록할 수 있지만, Worker가
      작업이 있는 회차 목록을 따로 관리해야 한다. Redis가 한 대인 지금은 얻는 것이 없다.
    - **회차별로 바꾸는 조건**: Redis를 클러스터 모드로 옮길 때(D1-5) 회차별 장부로 바꾸고 아래 "회차 목록" 설계를
      적용한다. 클러스터에서는 전역 장부가 회차 키와 다른 노드에 있을 수 있어 hold Lua 안에서 등록할 수 없다. 장부의
      작업은 hold 시간이 지나면 처리돼 빠지므로, 배포하는 동안 두 장부를 함께 읽어 옮긴다. hold 메타 키(`hold:key:*`)에
      hash tag가 없는 문제(D1-1)도 그때 함께 고친다.
    - 기존 hold 인덱스(`RSetCache`)는 쓰지 않는다. Redisson이 만료 항목을 지운다.
  - 회차 목록(2026-10-26 정함, 회차별 장부로 바꿀 때 적용): 전역 장부인 동안은 필요 없다. 회차별로 바꾸면 목록 키
    하나(Sorted Set)에 작업이 걸린 회차를 둔다. 값은 회차 id, 점수는 그 회차에 등록한 작업의 만료 시각이다. Worker는
    목록에 있는 회차의 장부만 확인한다.
    - 등록 순서: 목록에 회차를 먼저 적고(점수 = 이번 작업의 만료 시각), 그다음 hold Lua로 상태 변경과 장부 등록을 한다.
      목록 키는 회차 키와 hash slot이 달라 클러스터에서 같은 Lua에 넣을 수 없으므로 따로 보낸다. 대가는 hold 요청마다
      Redis 왕복 하나다. 목록 갱신이 실패하면 hold Lua를 실행하지 않고 요청을 실패로 끝낸다. hold Lua가 실패하면 목록에
      회차만 남는데, 점수가 지나면 아래 규칙으로 빠진다.
    - 빼기 규칙: Worker는 장부가 비었고 목록의 점수가 과거일 때만 회차를 뺀다. 점수 확인과 빼기는 Lua 하나로 한 번에
      한다. Worker가 장부가 빈 것을 본 직후 새 등록이 들어와도, 등록이 목록 점수를 먼저 미래로 올려 두므로 빼기가
      실패한다. Worker가 뺀 뒤에 들어온 등록은 목록에 회차를 다시 적는다.
    - 판매 중인 회차를 모두 확인하는 안은 고르지 않았다. 확인 비용이 판매 중인 회차 수에 비례하고(1만 회차, 서버 4대,
      1초 주기면 초당 4만 번), 판매가 끝난 회차의 남은 작업을 얼마나 더 볼지 정하기 어렵다(hold 시간에 상한이 없다).
      판매 기간 컬럼(`order_opens_at`, `order_closes_at`)에 인덱스도 없다.
  - 확인하는 서버와 주기(2026-10-26 정함): 모든 서버의 Worker가 1초마다 장부를 확인한다.
    - 꺼내기: Lua 하나로 시각이 지난 작업을 최대 100개 읽고 지운다. Redis는 Lua 하나를 실행하는 동안 다른 명령을 끼우지
      않으므로 여러 서버가 동시에 확인해도 한 작업은 한 서버만 가져간다. 꽉 채워 꺼냈으면 기다리지 않고 바로 다시
      꺼낸다(만료가 몰릴 때). `ZPOPMIN`은 시각이 안 된 작업도 꺼내므로 쓰지 않는다.
    - 시계: 점수는 주문의 `expiresAt`과 같은 값(`hold.expiresAt`, 앱 서버 시계)으로 넣고, 꺼낼 때도 앱 서버 시각과
      비교한다. `Order.expire`가 `expiresAt` 전이면 거부하므로, Redis 시계로 비교하면 Redis 시계가 앞선 만큼 일찍 꺼내
      처리에 실패할 수 있다. 서버끼리의 시계는 D4-2에서 맞춘다.
    - 처리 스레드는 서버당 2개다. 주문 만료 처리가 keyspace 리스너에서 Worker로 옮겨 가므로 DB 연결을 쓰는 양은 지금과
      비슷하다.
    - Worker는 `@Scheduled`가 아니라 자기 스레드로 돈다. `@Scheduled`는 `worker.enabled=true`인 서버에서만 켜진다
      (`SchedulingConfig`). 5분 DB 청소(`OrderExpirationTrigger`)는 그대로 worker에만 둔다(D3-1).
    - worker 서버만 확인하는 안은 고르지 않았다. API 서버의 DB 연결을 쓰지 않고 로그를 한 곳에서 볼 수 있지만, worker가
      죽거나 재배포되는 동안 만료 처리가 멈추고(작업은 장부에 남아 늦게 처리된다) 만료가 몰릴 때 한 대가 다 받는다.
      확인 비용은 서버당 1초에 Redis 명령 1번이라 서버 수가 늘어도 작다.
    - 늦어지는 것은 `RELEASED` 알림과 주문의 `EXPIRED` 반영이다. 좌석 점유 확인은 hold 키만 보고 그 키는 TTL로
      사라지므로, 다른 사용자가 좌석을 고를 수 있는 시점은 늦어지지 않는다. 같은 회원의 재주문은 `EXPIRED` 반영까지
      막힌다(E5004).
  - 좌석 선택 만료 알림의 중복(2026-10-26 정함): claim으로 한 대만 처리하고 `DESELECTED`는 relay로 보낸다.
    - 모든 인스턴스가 keyspace 알림을 받는다. 선택 키를 해석한 인스턴스는
      `SET expired-claim:<만료된 키> <인스턴스 id> NX PX 60000`을 보내고, 성공한 한 대만 `notifyReleasedIfFree`를
      실행한다. `DESELECTED`는 relay(D2-1)로 모든 인스턴스에 보내 각자 자기 접속자에게 전달한다. 그래서 D2-1의 relay가
      전제다.
    - 고르지 않은 안: 모든 인스턴스가 처리하고 relay 없이 자기 접속자에게만 보내는 안은 접속자마다 한 번씩 받지만, DB
      조회와 좌석 락이 N배라 사용자의 좌석 선택(`SELECT_LOCK`, 500ms 대기)과 같은 락을 N배로 다툰다. worker만 구독하는
      안은 worker가 내려가면 알림이 빠진다.
    - 알아 둘 것: 값(인스턴스 id)을 빼고 `SET <키> NX PX 60000`으로 보내면 Redis가 `NX`를 값으로 읽어 조건 없이
      덮어쓰므로 모두 성공한다. Redisson `RBucket.setIfAbsent(값, Duration)`을 쓰면 생기지 않는다. 표시 키도 만료될 때
      keyspace 알림이 나가 만료 알림 수가 는다. 처리기는 모르는 키라 버린다. 표시 키는 선택 키 해석에 성공한 것만
      만든다. 모든 키를 claim하면 표시 키의 만료가 다시 claim되는 고리가 생긴다.
  - 좌석 선택 claim의 위치와 TTL(2026-10-26 정함): 큐에서 꺼낸 뒤 `SeatSelectionExpirationHandler`가 키를 해석한
    직후에 claim한다. TTL은 60초다.
    - 지금의 수신 → 작업 큐(스레드 2개) → handler 구조가 그대로이고, 한가한 인스턴스가 먼저 꺼내 처리한다. 인스턴스가
      갑자기 죽으면 잃는 것은 처리 중이던 몇 개뿐이다(다른 인스턴스 큐의 같은 알림은 claim되지 않았으므로 처리된다).
    - 감수하는 것: 한 인스턴스의 큐가 60초 넘게 밀리면 표시 키가 사라진 뒤 꺼내 `DESELECTED`가 한 번 더 나간다(화면은
      같다). 4분 넘게 밀리면 늦게 잡은 표시 키가 같은 좌석의 다음 만료(최소 5분 뒤)를 막아 그 알림이 빠질 수 있다. 좌석
      선택 만료는 화면 알림뿐이라 다음 조회에서 맞춰진다.
    - TTL 60초: 인스턴스 사이의 알림 수신 차이(밀리초~초)를 덮고, 같은 좌석의 선택 키가 다시 만료되는 최소
      간격(`SELECT_TTL`, 5분)보다 충분히 짧다. 선택 TTL을 줄일 때 함께 줄이도록 두 값의 관계를 테스트로 고정한다.
    - 고르지 않은 안: 알림을 받자마자 비동기로 claim하고 성공한 것만 큐에 넣는 안(ADR 0022 원래 문구)은 밀려도 정확히
      한 번 처리하지만, 수신 구조를 바꿔야 하고 잡은 인스턴스가 밀려 있으면 그만큼 늦으며 그 인스턴스가 죽으면 잡아 둔
      큐를 모두 잃는다.
  - 아직 정하지 않은 것:
    - 꺼낸 뒤 처리 전에 죽은 주문 만료 작업: 안전망(5분 DB 청소)에 맡길지, "처리 중" 목록을 두고 오래 남은 작업을
      되돌릴지(Netflix dyno-queues의 unack 방식).
    - 기존 hold 만료 handler(`HoldKeyExpirationHandler`)를 걷어내는 시점. keyspace 리스너와 `CONFIG SET`은 좌석 선택
      때문에 남는다.
- **상세**: [ADR 0022](../adr/0022-seat-status-relay-and-single-expiration-claim.md) 결정 2는 claim 기준으로 쓰여 있다.
  위 세부 결정을 정한 뒤 다시 쓴다.

### D2-3. OAuth2 로그인 세션

- **문제**: 로그인 시작(`/api/v1/auth/oauth2/authorize/*`)과 콜백(`/api/v1/auth/oauth2/callback/*`) 사이에 OAuth2
  `state`와 프론트 주소를 HttpSession에 둔다(`OAuth2SecurityConfig`, `OAuth2FrontendRedirectResolver`). 두 요청이 다른
  인스턴스로 가면 `authorization_request_not_found`로 로그인이 실패한다.
- **선택지**:
  - Spring Session Data Redis로 세션을 Redis에 둔다. API 체인은 `STATELESS`라 세션을 만드는 곳이 OAuth2 체인뿐이다.
    의존성과 설정만 늘고 코드는 거의 바뀌지 않는다. Redisson 연결과 함께 쓸 수 있는지 확인이 필요하다.
  - Redis에 state 키를 짧은 TTL로 저장하는 `AuthorizationRequestRepository`를 직접 만들고 프론트 주소 저장도 바꾼다.
    2026-10-17 논의의 기본안이었다. 코드가 수십 줄 늘어난다.
  - state와 프론트 주소를 서명한 쿠키에 담는다. 서버 저장소가 필요 없다.
  - OAuth2 경로에만 LB sticky를 건다. 코드는 그대로지만 LB 설정에 기대게 된다.
- **추천**: Spring Session Data Redis(확인 필요).

### D2-4. SockJS 연결

- **문제**: FE는 `sockjs-client`로 `/ws`에 연결한다. WebSocket으로 연결되면 문제가 없지만, xhr-streaming이나
  polling으로 폴백하면 요청 여러 개가 같은 인스턴스로 가야 한다.
- **선택지**:
  - FE가 `/ws/websocket`으로 순수 WebSocket 연결을 한다. SockJS endpoint는 순수 WebSocket도 받으므로 서버는 그대로다.
  - `/ws/**`에 LB sticky를 건다.
  - 서버에서 SockJS를 걷어낸다(FE도 함께 바꿔야 한다).
- **추천**: FE 순수 WebSocket. 폴백이 꼭 필요한 사용자 환경이 있는지 먼저 확인한다.

### D2-5. FE 재연결 때 좌석 다시 조회

- **문제**: `useSeatSocket`은 연결되면 구독만 하고 좌석 상태를 다시 조회하지 않는다. 연결이 끊긴 동안의 알림과,
  D2-1·D2-2에서 드물게 사라지는 알림을 메울 길이 없다.
- **추천**: 연결·재연결 때 점유 좌석을 다시 조회한다. 스케일 아웃과 상관없이 지금도 필요하다. ticket-fe에서 진행한다.

## D3. 중복 실행과 자원(P1)

### D3-1. 스케줄러 중복 실행

- **문제**: `@Scheduled` 작업이 인스턴스마다 돈다.
  - `OrderExpirationTrigger`(5분): 같은 주문을 동시에 훑으며 row lock을 다툰다. 결과는 멱등이다.
  - `EventPublicationMaintenance`의 실패 이벤트 재처리(1분): 여러 인스턴스가 같은 publication을 다시 보내는지
    확인하지 못했다.
  - `EventPublicationMaintenance`의 archive 정리(매일 03시): 중복으로 지워도 무해하다.
- **선택지**: ShedLock(DB 테이블과 migration 추가), `worker.enabled=true`를 한 대에만 둠, 그대로 둠.
- **추천**: `worker.enabled=true`를 한 대에만 둔다. 코드 변경이 없다. 그 한 대가 내려가도 실시간 만료는 다른 인스턴스가
  D2-2로 처리하고, 안전망 청소만 잠시 멈춘다. 인스턴스가 많아지면 ShedLock을 다시 본다.

### D3-2. DB 연결 풀 크기

- **정할 것**: 인스턴스당 Hikari 풀 크기와 최대 인스턴스 수.
- **왜**: 전체 연결 수는 (풀 크기 × 인스턴스 수) + seed 작업 + 관측 계정이다. 이 합이 RDS `max_connections` 79 안에
  들어야 한다.
- **추천**: 대당 10을 유지한다. 그러면 5대까지 여유가 있다. 풀 크기는 수용량 측정 순서대로 한 번에 하나씩만 바꾼다.

### D3-3. 수용량 다시 재기

- **정할 것**: N대 구성에서 잴 조건과 Queue 입장률 R을 다시 계산하는 시점.
- **왜**: 인스턴스 수가 바뀌면 다시 재야 한다([Core 수용량](../core-capacity.md#다시-재야-할-때)). Redis는 모든
  인스턴스가 함께 쓰는 단일 스레드라 대수에 비례해 늘지 않을 수 있다.
- **추천**: D2 전체를 넣고 2대로 늘린 뒤 03-3 사다리로 잰다. R = C × 0.7은 그대로 쓴다.

## D4. 배포와 운영(P2)

### D4-1. 롤링 배포 중 호환 규칙

- **정할 것**: 옛 버전과 새 버전이 함께 도는 동안 지킬 규칙.
- **왜**: 한 대씩 바꾸면 잠시 두 버전이 함께 돈다.
  - Flyway migration은 옛 버전도 돌 수 있게 expand/contract로 나눈다.
  - `booking.OrderCreated`·`booking.OrderTerminated`처럼 DB에 FQCN이 저장되는 이벤트 이름은 바꾸지 않는다.
  - D2-1·D2-2는 옛 버전과 섞이면 알림이 빠지므로 인스턴스가 한 대일 때 배포를 끝낸다.

### D4-2. 관측과 시계

- **정할 것**: 인스턴스별로 지표를 나눠 볼 태그, 서버 시계 동기화.
- **왜**: 메트릭에는 `service`·`environment`·`version` 태그만 있어 인스턴스를 구분하려면 host나 container 태그가 있어야
  한다. 주문 `expiresAt`은 애플리케이션 서버 시계로 계산하므로 서버끼리 시계가 맞아야 한다.
- **추천**: Datadog이 붙이는 host·container 태그로 나눠 볼 수 있는지 확인하고, EC2 시간 동기화가 켜져 있는지 확인한다.

## 결정 뒤 구현할 것

- [ADR 0022](../adr/0022-seat-status-relay-and-single-expiration-claim.md)의 relay 구현(D2-1)과 주문 만료 Sorted Set
  장부 구현(D2-2)
- 프런트엔드 재연결 시 좌석 상태 재조회(D2-5). 지금 ticket-fe의 `useSeatSocket.ts`는 재구독만 한다.
- 배포 workflow의 순차 교체와 readiness·liveness 분리(D1-3)
- 2노드 검증 테스트와 로컬 2노드 부하 측정(D3-3 전에)

## 진행 순서

1. **D0-1~D0-3**: 목적을 정하고, P-022를 고치고, 서버 한 대를 정리한 뒤 다시 잰다. 목표를 넘으면 여기서 끝난다.
2. **D1-1, D1-4**: Redis를 Core 호스트 밖으로 옮기고 영속성을 정한다. 인스턴스가 한 대일 때 한다. D1-5는 그 뒤 필요할 때
   진행한다.
3. **D2-1 → D2-2 → D2-3**: 인스턴스가 한 대인 상태에서 하나씩 배포한다. 한 대라서 사용자 동작은 그대로다.
4. **D2-4, D2-5**: ticket-fe에서 진행한다.
5. **D3-1, D3-2, D4-1, D4-2**: 2대로 늘리기 전에 설정과 규칙을 정한다.
6. **D1-2, D1-3**: LB와 배포 workflow를 바꾸고 2대로 늘린다.
7. **D3-3**: 2대 구성으로 다시 재고 Queue 입장률을 바꾼다.

## 확인하지 못한 것

- 저장소 밖의 서버 `docker-compose.yml`과 nginx 설정을 직접 보지 못했다. 컨테이너 구성은 gatling-test capacity-log
  2026-10-25 기록을 따랐다. 운영 nginx의 WebSocket 헤더, 타임아웃, FD 한도와 운영 Redis 설정, RDS 등급은 아직
  확인할 것으로 남아 있다.
- Spring Session Data Redis가 Redisson 연결과 함께 동작하는지 확인하지 않았다(D2-3).
- Spring Modulith 2.1의 실패 이벤트 재처리가 여러 인스턴스에서 중복되지 않는지 확인하지 않았다(D3-1).
- SockJS 폴백이 필요한 사용자 환경이 있는지 확인하지 않았다(D2-4).
