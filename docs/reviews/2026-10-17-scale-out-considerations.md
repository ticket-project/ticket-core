# 2026-10-17 수평 확장(다중 인스턴스) 고려 사항

Core를 두 대 이상의 인스턴스로 운영할 때 풀어야 할 문제와 검토한 선택지를 정리한다. **아직 결정하지 않았다.** 결정은
나중에 ADR로 남기며, 이 문서는 그 입력이다. 리뷰 전체는 [전체 코드 리뷰](2026-10-17-codebase-review.md)에 있다.

## 먼저 판단할 것: 수평 확장이 병목을 푸는가

앱 인스턴스를 늘려도 DB와 Redis는 하나다. [Core 수용량](../core-capacity.md) 측정에서는 이미 Hikari pending이 포화 신호로
나타난다. 병목이 DB나 Redis라면 노드를 늘릴수록 연결과 쿼리만 N배가 된다. 그래서 부하 측정으로 앱 CPU·스레드, Hikari,
RDS CPU, Redis CPU 중 무엇이 먼저 포화되는지 확인하고, 조회 경로 개선(좌석맵 캐시, 찜 개수, show 인덱스)을 먼저 한다.

## 이미 수평 확장에 안전한 부분

- 분산 락(Redisson), hold, selection, 선택 한도 Lua
- 리프레시 토큰, OAuth 일회용 코드(Redis)
- API 인증(STATELESS JWT, 요청마다 DB·Redis를 보지 않음)
- Event Publication Registry: listener는 발행한 노드에서 실행되고, 재처리 중복은 Modulith의 조건부 UPDATE가 막는다.
- 점유 좌석 1초 스냅샷: 노드별 캐시라 Redis 조회가 N배가 될 뿐 정합성은 깨지지 않는다.

## 두 대가 되는 순간 깨지는 것

### 1. 좌석 이벤트가 다른 노드의 사용자에게 전달되지 않는다

`WebSocketConfig`의 Simple Broker는 구독 목록을 그 JVM 메모리에만 둔다. 좌석 이벤트는 종류마다 발행 노드가 다르다.

| 이벤트 | 발행 시점 | 발행 노드 |
| --- | --- | --- |
| SELECTED | 좌석 선택 요청 | 요청을 받은 노드 |
| DESELECTED | 직접 해제, 선택 TTL 만료 | 요청을 받은 노드, 또는 만료 이벤트를 받은 노드 |
| HELD | 주문 생성 커밋 뒤 listener | 주문을 커밋한 노드 |
| RELEASED | 주문 취소·만료 커밋 뒤 listener | 취소 요청 노드, 또는 만료를 처리한 노드 |

노드 A에서 발행한 이벤트는 A에 연결된 사용자만 받으므로, 2대면 각 이벤트를 대략 절반의 사용자가 놓친다. 함께 풀
문제는 다음과 같다.

- 순서: 지금은 같은 JVM에서 좌석 락 안에 바로 브로커로 넘겨 순서가 맞는다. 노드 사이에 단계를 끼우면 수신 쪽에서
  섞일 수 있다.
- 재동기화: 프런트엔드(`ticket-fe`의 `seatSocketClient.ts`, `useSeatSocket.ts`)는 `@stomp/stompjs`와 `sockjs-client`를
  쓰고, 재연결 때 구독만 다시 걸며 좌석 상태를 다시 읽지 않는다. 끊긴 동안의 이벤트는 복구되지 않는다.
- SockJS 폴백: WebSocket이 막힌 환경의 HTTP 폴백 전송은 같은 노드로 요청이 이어져야 하므로 LB에서 `/ws/**` sticky가 필요하다.

검토한 선택지:

| 기준 | Redis pub/sub 백플레인 | 외부 STOMP 브로커 relay | 폴링으로 전환 |
| --- | --- | --- | --- |
| 노드 간 전달 | 각 노드가 Redis 채널을 구독해 자기 Simple Broker로 넘김 | 브로커가 구독 관리 | 필요 없음 |
| 새 인프라 | 없음 | RabbitMQ 등(HA 없으면 새 단일 장애 지점) | 없음(micro-cache·CDN 권장) |
| 연결 수 | 그대로 | Spring relay는 클라이언트 세션마다 브로커 TCP 연결을 엶 | WebSocket 없음 |
| 전달 보장 | 최대 한 번, 재동기화 필요 | 브로커 수준 | 매 주기 전체 상태 |
| 반영 지연 | 병합 주기(수백 ms) | 같음 | 폴링 주기(1~2초) |
| 변경 범위 | 발행 포트 구현 교체, 수신기, FE 재동기화·시퀀스 | 브로커 운영 | FE 좌석 동기화 재작성 |

참고: Socket.IO Redis adapter, SignalR Redis backplane, Rails ActionCable, Django Channels가 같은 Redis 백플레인 구조를
기본 제공한다. Spring에는 Redis 어댑터가 없어 중계 코드를 직접 작성해야 하고, Spring 공식 권장은 외부 브로커 relay다.

현재 기울어 있는 안(미결정): Redis pub/sub 백플레인에 다음을 붙인다.

- 발행 포트(`SeatStatusEventPublisher`) 뒤에 구현해 나중에 다른 방식으로 교체할 수 있게 한다.
- 채널 이름에 처음부터 `{perf:N}` hash tag를 넣는다(예: `seat-events:{perf:7}`).
- 회차별 동적 구독: STOMP 구독·해제·연결 종료 이벤트로 회차별 로컬 구독자 수를 세어 0→1에서 구독, 1→0에서 해제한다.
- 회차별 변경분 병합(200~500ms), 회차별 시퀀스 번호, 재연결·빈 번호 발견 시 스냅샷 재조회.

이 구조는 리뷰 P0-4(락 안 동기 팬아웃)도 줄인다. 발행 노드는 락 안에서 PUBLISH 한 번만 하고, 구독자별 전송은 각 노드의
수신 스레드가 락 밖에서 한다.

### 2. 모든 노드가 모든 Redis 만료를 처리한다

`RedisExpirationListenerConfig`는 조건 없이 모든 노드에서 `__keyevent@*__:expired`를 구독한다. N대면 hold 만료 하나가 같은
주문에 `FOR UPDATE`를 N번 걸고, 선택 만료 알림이 N번 발행된다. 백플레인을 붙이면 N×N번 전달된다. 그래서 1번과 함께, 또는
먼저 풀어야 한다.

### 3. 스케줄 작업이 worker 노드마다 실행된다

`SchedulingConfig`는 `worker.enabled`로만 켜고 끈다. 만료 스위퍼, publication 재처리, 03시 purge, Modulith staleness 검사가
worker마다 돈다. 멱등이라 데이터는 깨지지 않지만 락 경합과 DB 부하가 N배가 된다.

2번과 3번의 선택지:

| 기준 | A. 역할 분리(worker 1대) | B. 동일 노드 + ShedLock + 리더 선출 | C. 동일 노드 + ShedLock + 만료 작업 큐 |
| --- | --- | --- | --- |
| 방식 | API 노드는 `worker.enabled=false`, worker 1대만 만료 리스너·스케줄러 실행. 이미지는 같고 환경 변수만 다름 | 스케줄은 ShedLock, 만료 리스너는 리더만 구독 | 만료 예정 시각을 점수로 한 ZSET에 기록, 노드마다 짧은 주기로 Lua로 만기 항목을 가져가 처리 |
| 구현 규모 | 작음 | 중간 | 큼 |
| 단일 장애 지점 | worker | 없음(리더 교체 공백) | 없음 |
| keyspace 알림 의존 | 그대로 | 그대로 | 없어짐 |
| 관리형·클러스터 Redis | parameter group 필요, 클러스터면 샤드마다 구독 | 같음 | 추가 설정 없음 |
| 기존 사례 | ticket-queue의 "scheduler는 1대" | 없음 | ticket-queue의 shard 분할 응용 가능 |

worker 분리 시 의존 관계: worker가 발행하는 RELEASED·선택 만료 알림을 API 노드의 사용자에게 보내려면 1번의 백플레인이
있어야 한다. worker 단일 장애의 영향은 리뷰 12번 조치(만료 시각이 지난 PENDING을 EXPIRED로 보이게, 주문 생성 락 안에서
즉시 만료)로 줄일 수 있다.

현재 기울어 있는 안(미결정): A로 시작하고, 관리형 클러스터 Redis 전환이나 worker 단일 장애가 문제가 될 때 C로 옮긴다.
B는 keyspace 알림 의존을 둔 채 복잡도만 늘린다.

### 4. OAuth 로그인이 노드를 넘나들면 실패한다

`OAuth2SecurityConfig`의 `IF_REQUIRED` 세션과 기본 `HttpSessionOAuth2AuthorizationRequestRepository`,
`OAuth2FrontendRedirectResolver`의 `getSession(true)` 때문에 authorize와 callback이 다른 노드로 가면 state 검증이 실패한다.
기본안: 인가 요청과 프런트엔드 주소를 Redis(state 키, 5~10분 TTL)나 서명 쿠키에 저장하고, OAuth 체인을 STATELESS로 바꾸며,
no-op `OAuth2AuthorizedClientRepository`를 등록한다. 리뷰 19번(세션 기반 힙 고갈)도 함께 풀린다.

## 노드 수만큼 커지는 공유 자원

- DB 연결: "Hikari 최대 × 노드 수 + worker + 관리 여유"가 RDS `max_connections` 안에 있어야 한다. 필요하면 RDS Proxy.
- Redis: 노드마다 Redisson 연결 풀·구독 연결과 Lettuce 연결이 생긴다. `maxclients`와 Redis CPU를 본다.
- 타임아웃: 리뷰 8번이 다중 노드에서 더 중요하다. 빠르게 실패해야 readiness와 LB가 느린 노드를 뺀다.
- rate limit: 노드 메모리가 아니라 nginx/LB나 Redis에 둔다.
- 시계: `Order.expire`와 hold 인덱스(`RSetCache`)가 앱 노드 시계를 쓴다. NTP를 맞추고 장기적으로 Redis `TIME`이나 허용
  오차를 쓴다.

## WebSocket 운영 특성

- 연결 수: Tomcat 기본 `server.tomcat.max-connections` 8,192(HTTP keep-alive 포함)가 노드당 상한이다. nginx는 WebSocket
  하나에 연결 두 개를 쓴다. 노드를 늘리면 연결이 나뉘어 상한도 늘어난다.
- 하트비트: nginx `proxy_read_timeout` 기본 60초. 지금은 SockJS 기본 25초 하트비트로 유지되고, 서버 STOMP 하트비트는 없다.
- 인증은 CONNECT 때 한 번뿐이라 토큰이 만료돼도 연결이 유지된다.
- 배포·장애 시 그 노드의 연결이 모두 끊기고 프런트엔드는 5초 뒤 재연결한다.

## Redis 자체의 확장

운영 Core Redis는 지금 같은 호스트의 컨테이너이고, 나중에 관리형(ElastiCache 등, 클러스터 모드 가능성 포함)으로 옮길 수
있다. 참고로 ticket-queue는 2026-07-04에 Azure Managed Redis 클러스터 모드로 전환했다가(`e3aba0d`), 2026-07-22 EC2 이전 뒤
단일 Redis 컨테이너(AOF 꺼짐)로 운영 중이다. 키는 128개 shard와 hash tag로 클러스터 전환에 대비해 두었다. Core와 queue의
Redis는 분리되어 있고, 이 분리는 유지한다.

Core는 대기열이 입장률을 제한하므로 Redis 부하에 상한이 있다. 지금까지 문제가 된 것은 명령 수가 아니라 목록 전체 읽기였고
(P-014), 캐시로 풀었다. 확장 형태별 순서는 다음으로 예상한다.

| 형태 | 해결하는 문제 | 예상 순서 |
| --- | --- | --- |
| 복제본 + 자동 failover | 가용성(지금은 Redis가 죽으면 예매 전체 중단) | 가장 먼저 |
| 용도별 분리(좌석 / 인증 토큰 / 알림 pub/sub) | 용도 간 간섭 | 다음 |
| 클러스터(샤딩) | 한 대의 CPU·메모리 한계 | 여러 인기 회차가 동시에 열려 포화될 때 |

Core 좌석 키는 모두 `{perf:N}`으로 묶여 있어 클러스터에서도 **회차 하나의 작업은 서버 한 대**로 간다. 인기 회차 하나의
부하까지 나누려면 키를 구역별로 쪼개야 하는데, 그러면 회원 한도를 Lua 한 번으로 원자적으로 지킬 수 없어 큰 재설계가
필요하다.

Redis 형태별 백플레인 동작:

| Redis 형태 | 백플레인 | 주의 |
| --- | --- | --- |
| 단일 | 일반 pub/sub | 없음 |
| 복제본 + failover | 일반 pub/sub, replica에서도 구독 가능 | failover 중 메시지 유실, 재동기화로 메움 |
| 용도별 분리 | 알림 전용 Redis면 데이터 Redis 형태와 무관 | 관리할 Redis 증가 |
| 클러스터 | 일반 PUBLISH는 모든 노드로 복사됨. sharded pub/sub(`SPUBLISH`/`SSUBSCRIBE`, Redis 7) 사용 | 패턴 구독 불가(회차별 동적 구독 필요), slot 이동·failover 시 재구독 |

관리형 클러스터로 가면 CONFIG가 막히고 keyspace 알림이 샤드마다 나오므로, 만료는 큐 방식(C)으로 옮기는 편이 맞다. Core
Redis는 좌석 hold를 담으므로 queue와 달리 AOF와 `noeviction`을 쓴다(리뷰 P0-5).

## 인프라 선택지

같은 호스트에 JVM을 여러 개 띄워도 CPU·메모리 총량은 같아 처리량은 거의 늘지 않는다. 이득은 무중단 배포와 프로세스 장애
격리다.

| 기준 | 같은 호스트 다중 컨테이너 | EC2 여러 대 + ALB | ECS/Fargate + ALB |
| --- | --- | --- | --- |
| 처리량 증가 | 거의 없음 | 있음 | 있음 |
| 무중단 배포 | 가능 | 가능 | 기본 제공 |
| 호스트 장애 | 전체 중단 | 견딤 | 견딤 |
| 추가 비용 | 없음(메모리 여유 필요) | 인스턴스와 ALB | 태스크와 ALB |
| Redis | 그대로 | 앱 호스트 밖으로 이전 | 같음 |
| 오픈 트래픽 | 해당 없음 | 판매 전 미리 대수 증가 | 자동 확장은 분 단위라 예약 확장 필요 |

현재 기울어 있는 안(미결정): 1단계로 같은 호스트에 컨테이너 2대를 띄워 무중단 배포와 다중 인스턴스 정합성을 검증하되,
설계는 호스트 분리를 전제한다(노드 로컬 상태 금지, 주소는 설정). 측정으로 앱 CPU 병목이 확인되면 Redis를 관리형으로 옮기고
EC2 여러 대와 ALB로 간다. 처리량이 급하면 그 전에 수직 확장이 가장 싸다.

배포에 필요한 것:

- LB: upstream 다중 서버 또는 ALB, WebSocket Upgrade 헤더, SockJS를 유지하면 `/ws/**` sticky
- 롤링 또는 blue/green: readiness가 UP이 된 뒤 다음 노드로, 내려갈 노드는 readiness DOWN 뒤 drain, nginx 재시작 대신 reload
- readiness(DB·Redis)와 liveness(의존성 없음) 분리
- migration: 구·신버전이 동시에 돌므로 expand/contract와 N-1 호환, migration은 배포 앞 단계에서 한 번
- 지표·로그에 인스턴스 식별자

## 검증 계획

- 두 애플리케이션 컨텍스트가 같은 Testcontainers DB·Redis를 쓰는 테스트: 노드 A의 좌석 이벤트가 노드 B 구독자에게 도착,
  만료 한 번 처리, 다른 노드의 OAuth callback 성공, 스케줄 작업 한 번 실행
- 로컬 compose에 core 두 대와 nginx를 띄운 부하 측정으로 처리량이 노드 수에 비례하는지, DB·Redis에서 먼저 막히는지 확인

## 남은 결정과 확인 필요 항목

결정할 것:

1. 좌석 이벤트 노드 간 전달 방식
2. 만료·스케줄 단일 실행 방식
3. 목표 인프라 형태와 단계
4. OAuth 상태 저장소(기본안: Redis)
5. SockJS 폴백 유지 여부

확인할 것:

- 운영 호스트 사양(힙 2개, Redis, nginx, Datadog 에이전트를 감당하는지)
- RDS `max_connections`와 등급
- 운영 Redis 설정(AOF, `maxmemory-policy`, `notify-keyspace-events`)
- 운영 nginx 설정(WebSocket 헤더, 타임아웃, FD 한도)
