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
| D2-2 | Redis 만료 알림 중복 처리 | 2대 운영 | 정함 | Redis Sorted Set 장부(지연 큐). 세부 결정 남음 |
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
  하나의 한계는 클러스터로 올라가지 않는다. 만료 작업 장부는 회차별(`{perf:N}`)이라 클러스터에서도 그대로 쓴다(D2-2).

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

### D2-2. Redis 만료 알림 중복 처리

- **문제**: Redis는 키를 지울 때 "이 키 지웠어"라고 구독자 모두에게 방송한다. 인스턴스가 N대면 모두가 듣고 모두가
  처리한다.
  - 주문 선점 만료: 주문을 `EXPIRED`로 바꾼다. 결과는 한 번만 반영되지만 DB 연결과 lock 대기를 N배로 쓴다.
  - 좌석 선택 만료: "좌석 비었어요"(`DESELECTED`)를 보낸다. DB 조회와 좌석 락 경합이 N배이고, D2-1을 하면 같은
    알림이 N번 나간다.
- **선택지**: 먼저 손든 인스턴스만 처리(claim), 리더 한 대만 구독, 만료를 처리하는 worker 1대 분리(ticket-queue와 같은
  방식), Redis 지연 큐(만료 예정 Sorted Set), 외부 메시지 큐의 예약 배달, 중복 허용.
- **결정(2026-10-26)**: Redis Sorted Set 장부(지연 큐). 같은 날 claim으로 정했다가 바꿨다.
  - 좌석 선택 만료와 주문 선점(hold) 만료 모두 만료 시각을 점수로 Sorted Set에 적는다. 서버는 주기적으로 시각이 지난
    항목을 꺼내고, 꺼내기(`ZREM` 등)에 성공한 한 대만 처리한다. keyspace 만료 알림은 쓰지 않는다.
  - 고른 이유: keyspace 알림은 구독이 끊긴 동안의 알림이 사라지고, 키가 실제로 지워질 때 나가서 TTL 시각보다 늦을 수
    있으며, 클러스터에서는 노드마다 따로 구독해야 한다
    ([Redis 문서](https://redis.io/docs/latest/develop/pubsub/keyspace-notifications/)). 장부는 항목이 남아 있어 서버가
    꺼져 있던 동안의 만료도 처리하고, 서버 대수와 Redis 형태에 기대지 않는다. 지연 작업 구조를 공개한 Netflix
    dyno-queues, Sidekiq, Airbnb Dynein도 실행 시각을 저장해 두고 주기적으로 꺼낸다.
  - 감수하는 것: 확인 주기만큼 늦다. 꺼낸 뒤 처리하기 전에 서버가 죽으면 그 항목은 잃는다. 주문은 5분 주기
    스케줄러(`OrderExpirationTrigger`)가 DB를 보고 복구하고, 좌석 선택 알림은 FE 재조회(D2-5)로 메운다.
  - 함께 바뀌는 것: 기동 때의 `CONFIG SET notify-keyspace-events`(`RedisExpirationListenerConfig`)가 필요 없어져서
    D1-1의 관리형 Redis 파라미터 그룹 설정과 D1-5의 노드별 구독 문제가 없어진다.
  - 고르지 않은 안: claim(keyspace 알림 + `SET NX`)은 코드 변경이 가장 작지만 알림 누락·지연과 클러스터 구독 문제가
    남는다. 주문 만료만 DB 청소 주기를 줄여 처리하는 안은 설정값 하나로 되지만 좌석 선택과 방식이 갈리고 몇 초마다 DB를
    조회한다. worker 한 대만 구독하는 안은 그 한 대가 내려가면 선택 만료 알림을 되살릴 경로가 없다.
  - 처리 구조(2026-10-26 정함): 각 기능이 "이 시각에 이 작업"을 장부에 미리 등록하고, booking 안의 공통 만료 작업
    처리기(Expiration Worker) 하나가 주기적으로 장부를 확인해 시각이 지난 작업을 꺼내 종류별 처리기(좌석 선택 해제,
    주문 만료)에 넘긴다. 지금의 `RedisKeyExpirationListener`·`RedisKeyExpirationHandler` 구조에서 입력만 방송에서
    장부로 바뀐다. 등록은 각 기능의 Lua 안에서 상태 변경과 함께 한다. 따로 등록하면 "선택은 됐는데 장부에는 없음" 틈이
    생기고, 만료 시각도 같은 Redis 시계로 계산해야 처리기가 아직 남은 선택을 보고 알림을 건너뛰지 않는다.
  - 만들지 않는 것: 별도 서비스, 아무 모듈이나 부르는 범용 예약 API, 재시도·실패 보관함·우선순위. 쓰는 곳이 booking 두
    곳뿐이다. Redisson의 지연 큐는 쓸 수 없다. `RDelayedQueue`는 deprecated이고 `RReliableQueue`는 PRO 전용이다(4.7.0).
- **세부 결정**:
  - 장부 키(2026-10-26 정함): 회차별 장부(`{perf:N}` hash tag)에 그 회차의 좌석 선택·hold 작업을 같이 둔다. 키 이름은
    구현할 때 정한다. 같은 날 전역 장부 하나로 정했다가 바꿨다.
    - 선택·hold Lua가 이미 같은 hash tag의 회차 키를 다루므로, Redis를 클러스터 모드로 옮겨도 Lua 안에서 그대로 등록할
      수 있다. hold Lua의 hold 메타 키(`hold:key:*`)에 hash tag가 없는 문제(D1-1)는 그와 별개로 클러스터 전에 고친다.
    - 전역 장부 하나는 고르지 않았다. Worker가 키 하나만 확인하면 되지만, 클러스터에서는 회차 키와 다른 노드에 있을 수
      있어 Lua 안에서 등록할 수 없고 그때 다시 바꿔야 한다.
    - 대가로 Worker가 작업이 있는 회차 목록을 알아야 한다(아래 "회차 목록").
    - 기존 인덱스는 쓰지 않는다. 좌석 선택 표시용 인덱스 `seat:select:index:{perf:N}`는 선택 Lua가 만료 항목을
      `zremrangebyscore`로 먼저 지우고 인덱스 키 전체가 마지막 만료 시각에 사라진다(`pexpireat`). hold
      인덱스(`RSetCache`)는 Redisson이 만료 항목을 지우고 점수를 앱 서버 시계로 넣는다.
  - 회차 목록(2026-10-26 정함): 목록 키 하나(Sorted Set)에 작업이 걸린 회차를 둔다. 값은 회차 id, 점수는 그 회차에
    등록한 작업의 만료 시각이다. Worker는 목록에 있는 회차의 장부만 확인한다.
    - 등록 순서: 목록에 회차를 먼저 적고(점수 = 이번 작업의 만료 시각), 그다음 회차 Lua로 상태 변경과 장부 등록을 한다.
      목록 키는 회차 키와 hash slot이 달라 클러스터에서 같은 Lua에 넣을 수 없으므로 처음부터 따로 보낸다. 대가는 좌석
      선택·hold 요청마다 Redis 왕복 하나다. 목록 갱신이 실패하면 회차 Lua를 실행하지 않고 요청을 실패로 끝낸다. 회차
      Lua가 실패하면 목록에 회차만 남는데, 점수가 지나면 아래 규칙으로 빠진다.
    - 빼기 규칙: Worker는 장부가 비었고 목록의 점수가 과거일 때만 회차를 뺀다. 점수 확인과 빼기는 Lua 하나로 한 번에
      한다. Worker가 장부가 빈 것을 본 직후 새 등록이 들어와도, 등록이 목록 점수를 먼저 미래로 올려 두므로 빼기가
      실패한다. Worker가 뺀 뒤에 들어온 등록은 목록에 회차를 다시 적는다.
    - 판매 중인 회차를 모두 확인하는 안은 고르지 않았다. 확인 비용이 판매 중인 회차 수에 비례하고(1만 회차, 서버 4대,
      1초 주기면 초당 4만 번), 판매가 끝난 회차의 남은 작업을 얼마나 더 볼지 정하기 어렵다(hold 시간에 상한이 없다).
      판매 기간 컬럼(`order_opens_at`, `order_closes_at`)에 인덱스도 없다.
  - 아직 정하지 않은 것:
    - 확인 주기와 확인하는 서버: 모든 서버가 확인하고 꺼내기에 성공한 쪽만 처리할지, worker만 확인할지(D3-1).
    - 꺼낸 뒤 처리 전에 죽은 항목: 안전망에 맡길지, "처리 중" 목록을 두고 오래 남은 항목을 되돌릴지(Netflix
      dyno-queues의 unack 방식).
    - 기존 keyspace 리스너와 `CONFIG SET` 기동 코드를 걷어내는 시점.
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

- [ADR 0022](../adr/0022-seat-status-relay-and-single-expiration-claim.md)의 relay 구현(D2-1)과 Redis Sorted Set 만료
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
