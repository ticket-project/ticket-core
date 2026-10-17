# 2026-10-17 전체 코드 리뷰

> **보안 주의:** 이 문서에는 아직 고치지 않은 보안 결함과 그 공격 시나리오가 들어 있다. 운영 서버가 내려가 있는
> 동안 기록 목적으로 공개 저장소에 올렸다. **운영을 다시 열기 전에 보안 항목을 고치고, 고치지 못한 항목의 상세는 이
> 문서에서 지운다.** 지울 대상은 아래 "보안 항목 목록"에 모아 두었다.

이 문서는 `master`(`c1f2bf04d`) 기준으로 `ticket-core` 전체(메인 코드, 테스트, migration, 설정, CI/배포)를 대상으로 한
리뷰 기록이다. 대규모 오픈 트래픽을 받는 운영 환경을 가정했다. 미해결 항목의 추적 원본은
[CONTRIBUTING](../../CONTRIBUTING.md)에 따라 Issue로 옮기며, 이 문서의 번호는 문서 안에서 서로 가리키기 위한 것이다.

다중 인스턴스 운영과 관련된 내용은 [수평 확장 고려 사항](2026-10-17-scale-out-considerations.md)에 따로 정리했다.

## 리뷰 방법과 한계

- 다섯 영역(주문·선점 동시성, 좌석·선택·입장 토큰, 인증·회원, 공연·공통 모듈, 빌드·운영·테스트)으로 나누어 읽었다.
- 영향이 큰 지적은 소스를 다시 읽어 확인했고, 라이브러리 동작이 걸린 지적은 Spring Modulith 2.1.1과 Redisson 4.7.0
  jar를 `javap`로 분해해 확인했다.
- 코드는 바꾸지 않았고 빌드·테스트도 실행하지 않았다. 실행 결과가 아니라 코드 읽기에 근거한 판단이다.
- 운영 Redis·nginx·환경 변수·서버 compose는 저장소 밖에 있어 확인하지 못했다(마지막 절 참고).

## 총평

트랜잭션과 외부 I/O의 분리, outbox(Event Publication Registry), 멱등 후속 처리, 모듈 경계 테스트는 수준이 높다.
약점은 코드가 "단일 인스턴스, 정상 상태의 Redis, 적당한 트래픽"을 암묵적으로 전제한다는 점이다.

1. 좌석 배타성을 Redis 하나만 보장하고 DB에는 최후 방어선이 없다.
2. WebSocket 브로커·OAuth 세션·만료 리스너·스케줄러가 단일 인스턴스를 전제한다.
3. DB·Redis·락 대기에 fail-fast 타임아웃이 없어 하위 의존성이 느려지면 Tomcat 스레드가 모두 묶인다.
4. 좌석 이벤트 팬아웃과 카탈로그 조회 경로에 역압·캐시·인덱스가 없다.
5. 보안 기본값이 fail-open이고 WebSocket 프레임 인가가 비어 있다.

## 보안 항목 목록

운영 재개 전에 고치거나, 고치지 못하면 이 문서에서 상세를 지울 항목이다.

| 번호 | 항목 |
| --- | --- |
| P0-1 | WebSocket 클라이언트 SEND로 위조 좌석 이벤트 방송 |
| P0-3 | 입장 토큰 검증 기본 꺼짐, 공개된 기본 비밀값 |
| 19 | OAuth authorize마다 HttpSession 생성(힙 고갈) |
| 20 | 리프레시 토큰 재사용 탐지·해시 저장 부재 |
| 21 | 로그아웃에 유효한 액세스 토큰 필요 |
| 22 | 일회용 로그인 코드가 브라우저에 묶이지 않음(로그인 CSRF) |
| 23 | 이메일 기준 소셜 계정 자동 연결 |
| 24 | 운영 `/actuator/prometheus`·Swagger 공개 |
| 26 | CI/CD 공급망(태그 고정 액션에 SSH 키 전달) |

## P0: 지금 고칠 것

### P0-1. 로그인한 사용자가 위조 좌석 이벤트를 관람자 전원에게 방송할 수 있다

- 위치: [WebSocketConfig.java:40](../../src/main/java/com/ticket/booking/websocket/WebSocketConfig.java),
  [WebSocketAuthInterceptor.java:42](../../src/main/java/com/ticket/booking/websocket/WebSocketAuthInterceptor.java),
  `ApiSecurityConfig`의 `/ws/**` permitAll
- 문제: `enableSimpleBroker("/topic")`만 있고 application destination prefix와 메시지 인가가 없다. 인터셉터는 CONNECT만
  검사한다. Simple Broker는 클라이언트 SEND의 목적지가 `/topic`이면 구독자 전원에게 팬아웃한다.
  `WebSocketAuthInterceptorTest`도 "CONNECT가 아닌 프레임은 그대로 통과한다"를 계약으로 고정한다.
- 시나리오: 공격자가 `/topic/performance/{id}/seats`로 SEND해 모든 좌석을 빈 좌석이나 점유 좌석으로 칠하면 관람자
  화면이 조작되고 E4001·E6003 재시도가 폭주한다. 큰 프레임 하나가 구독자 수만큼 증폭되는 DoS 수단이기도 하다.
- 조치: `/topic/**`로 가는 SEND를 거부하고, SUBSCRIBE 목적지를 좌석 토픽 패턴으로 제한하며, CONNECT로 인증되지 않은
  세션의 프레임을 거부한다. CONNECT 없이 SUBSCRIBE가 받아들여지는지도 테스트로 확인한다.

### P0-2. 재시도 상한을 넘긴 publication 4건이 이후 자동 재처리를 모두 막는다

- 위치: [EventPublicationMaintenance.java:34](../../src/main/java/com/ticket/shared/config/EventPublicationMaintenance.java)
- 근거(바이트코드 확인): Modulith 2.1.1의 `processFailedPublications`는 `min(batchSize, maxInFlight − RESUBMITTED 수)`건,
  지금 설정으로 최대 4건만 `publicationDate asc`로 읽고, `withFilter`는 읽은 다음 메모리에서 적용한다.
- 시나리오: Redis가 10여 분 불안정하면 가장 오래된 FAILED 4건이 11회 넘게 실패한다. 그 뒤로는 매 분 같은 4건을 읽어
  모두 걸러 내므로 새 실패 publication은 재처리되지 않는다. 취소 주문의 hold 조기 해제, 선택 정리, HELD/RELEASED
  발행이 사라지고 ERROR 로그만 매 분 쌓인다. 정체가 없어도 처리량이 분당 4건이다.
- 조치: 상한을 넘긴 건을 FAILED 집합에서 빼내고(별도 보관 테이블 등), `maxInFlight`를 전용 executor 크기에 맞추며,
  FAILED 건수 지표와 "poison 5건 + 정상 1건" 테스트를 추가한다.

### P0-3. 대기열 입장 검증이 기본으로 꺼져 있고 기본 비밀값이 공개되어 있다

- 위치: [application.yml](../../src/main/resources/application.yml)의 `security.admission.enforcement-enabled`(기본
  `false`)와 `security.admission.secret-key`(정확히 32바이트인 기본값)
- 문제: `application-prod.yml`은 두 값을 덮어쓰지 않는다. 기본 비밀값은 `JwtAdmissionVerifier`의 길이 검사를 통과한다.
- 시나리오: 환경 변수가 빠지면 대기열이 강제되지 않는다. 강제를 켜고 비밀값만 빠뜨리면 누구나 공개된 키로 입장 토큰을
  만들어 대기열을 건너뛴다.
- 조치: base 설정에서 기본 비밀값을 지우고 누락 시 기동을 실패시킨다. 강제 여부를 기동 로그에 남긴다. 장기적으로
  비대칭 서명(ES256/EdDSA)으로 옮긴다. ticket-queue와 공유하는 액세스 토큰 대칭키도 같은 방향으로 개선한다.

### P0-4. 좌석 클릭 하나가 좌석 락 안에서 관람자 전원에게 동기 팬아웃되고 출력 큐에 상한이 없다

- 위치: `WebSocketSeatStatusEventPublisher`의 `convertAndSend`, `SeatSelectionWriter`의 락 안 발행, `WebSocketConfig`
  (`setPreservePublishOrder(true)`, outbound 채널·transport 설정 없음)
- 문제: 브로커 채널이 동기라 요청 스레드가 락을 쥔 채 구독 세션마다 메시지를 넣는다. outbound 큐는 기본
  `Integer.MAX_VALUE`이고 전송 버퍼·시간·크기 제한이 없다.
- 시나리오: 초당 500명 입장, 사용자당 이벤트 4개, 관람자 5,000명이면 초당 약 1,000만 프레임이다. outbound 큐가 메모리를
  채우고 락 점유 시간이 늘어 선택이 E6003으로 실패한다. [Core 수용량](../core-capacity.md) 측정에는 WebSocket 구독자와
  좌석맵 조회가 빠져 있어 입장률을 과대평가한다.
- 조치: 회차별 변경분을 200~500ms 단위로 병합해 보내고, 락 밖에서 발행하되 순서는 시퀀스 번호로 표현한다. outbound와
  transport 한도를 설정하고 부하 시나리오에 STOMP 구독자를 추가한다.

### P0-5. 좌석 배타성의 최후 방어선이 DB에 없다(결제 연결 전 필수)

- 위치: [booking V1](../../src/main/resources/db/migration-vendor/postgresql/booking/V1__create_booking_schema.sql)의
  `uk_order_seats_order_perf_seat`, booking V2의 PENDING 인덱스(unique 아님), `Order.confirm()`
- 문제: 주문 좌석 유일성은 주문 단위뿐이다. 회원·회차당 PENDING 1건 규칙도 락으로만 지킨다. `PerformanceSeat`의
  `@Version`은 쓰는 곳이 없다.
- 시나리오: A의 좌석 키가 TTL로 사라진 뒤 만료 이벤트가 늦으면 A의 주문이 최대 5분 PENDING으로 남고 그사이 B가 같은
  좌석으로 PENDING 주문을 만든다. AOF 없는 재시작, 비동기 복제 failover, `volatile-*` eviction에서도 같다. 결제 승인을
  붙이면 이중 판매가 된다. `confirm()`은 만료 시각도 hold도 확인하지 않는다.
- 조치: `seat_reservations(performance_seat_id UNIQUE, …)`나 partial unique index를 `PendingOrderCreator` 트랜잭션에서
  기록하고, V2 인덱스를 `UNIQUE … WHERE status='PENDING'`으로 바꾼다. 결제 확정은 주문 행 락, `expires_at > now`,
  `PerformanceSeat` 버전 전이로 설계한다. 운영 Redis는 `noeviction`과 AOF를 쓴다. ADR로 결정한다.

## P1: 다음 대규모 판매 전에 해결할 것

### 동시성과 Redis

6. **멀티락 해제 실패 시 남은 좌석 락이 영구히 남는다.** `RedissonDistributedLock.unlockQuietly`. Redisson 4.7.0의
   `RedissonMultiLock.unlock()`은 `locks.forEach(Lock::unlock)`이라 첫 예외에서 나머지를 건너뛰고, leaseTime 없는 락은
   watchdog이 계속 갱신한다. 그 좌석은 모든 작업이 E6003으로 실패하고 P0-2의 poison publication을 만든다. `tryLock`
   실패 시에도 `finally`에서 `unlock`을 부르고, 소유권 상실을 DEBUG로만 남긴다. 조치: 락별 개별 해제, 획득 여부
   플래그, 소유권 상실 WARN과 지표.
7. **만료 처리가 불안정한 keyspace 알림에 기댄다.** `RedisExpirationListenerConfig`. 기동 시 `CONFIG SET`을 쓰며 관리형
   Redis는 막고 실패는 WARN뿐이다. Redis 재시작·failover 뒤 설정이 사라져도 모른다. `AKE` 같은 기존 값을 `Ex`로
   덮어쓴다. 모든 노드가 모든 만료를 처리하고, worker 2개에 무제한 큐다. 선택 만료에는 복구 경로가 없다. 조치: 서버
   설정으로 옮기고 지표로 노출, 정확성은 ZSET 지연 큐나 `SKIP LOCKED` 스위퍼로 보장.
8. **fail-fast 타임아웃이 없다.** Hikari는 `pool-name`만 설정(기본 10개, 대기 30초), `statement_timeout`·`lock_timeout`
   없음, `PESSIMISTIC_WRITE`에 대기 힌트 없음, Redisson 기본값, 락 기본 대기 5초(`LockOptions`). 주문 생성 한 건이
   최악의 경우 약 70초를 기다린다. 조치: 커넥션 대기 2~3초, 누수 탐지, DB 문장·락 타임아웃, Redisson 0.5~1초와 재시도
   1회, 주문 생성 락 `waitTime=0`, 풀 고갈은 503과 `Retry-After`.
9. **좌석 클릭 경로가 정합성 이득 없이 락으로 직렬화된다.** `SeatSelectionWriter`의 500ms 대기. 선택 Lua는 이미
   원자적이라 락은 발행 순서용이다. 인기 좌석에서는 대부분 E4001이 아니라 E6003을 받는다. 조치: 락 전에 선택 키를
   확인해 즉시 실패, 장기적으로 Lua가 시퀀스를 올려 락 없이 순서를 표현.
10. **`HoldStore.release`가 GET과 DEL로 나뉘어 락에만 정합성을 기댄다.** `RedissonHoldStore`. 저장도 좌석마다 여러
    왕복이다. 조치: 저장과 compare-and-delete를 각각 Lua 한 번으로 묶고, 앱 시계를 쓰는 `RSetCache`도 정리.
11. **보상이 best-effort이고 커밋 결과가 불분명한 경우를 잘못 처리한다.** `CreateOrderUseCase`. 커밋은 됐는데 예외가
    오면 hold만 풀려 hold 없는 PENDING이 남는다. 조치: 보상 전 `holdKey`로 주문 재확인, hold 인덱스와 PENDING 대조 작업.

### 주문·선택 API

12. **`POST /orders`가 멱등하지 않다.** 응답을 잃으면 재시도는 데이터 없는 E5004를 받고 "내 PENDING 주문" 조회도 없다.
    만료 이벤트를 놓친 주문은 최대 5분 재주문을 막고 상태 API는 `remainingSeconds=0`인 PENDING을 준다. 조치:
    `Idempotency-Key` 또는 E5004에 기존 `orderKey` 포함, 만료 시각이 지난 PENDING을 EXPIRED로 보이게 처리.
13. **주문 좌석 수 상한이 없다.** `CreateOrderRequest`에 `@Size`가 없고 `max_hold_seat_count`는 NULL을 허용한다.
    `OrderCreated.performanceSeatIds`가 `serialized_event VARCHAR(4000)`을 넘으면 좌석을 다 잡은 뒤 500으로 롤백된다.
14. **같은 회원의 선택이 멱등하지 않고 사재기를 막지 못한다.** `RedissonSeatSelectionStore`의 Lua는 키가 있으면 소유자와
    무관하게 거절한다. 선택 한도가 hold 좌석을 세지 않는다.

### 조회 경로

15. **좌석맵·가용성·좌석 상태 API가 무겁고 대부분 공개이며 캐시가 없다.** `GET /api/v1/performances/**` permitAll. 좌석맵은
    요청마다 1.5만 좌석 규모 엔티티를 두 번 하이드레이션하고 수 MB JSON을 매번 gzip한다. `@Cacheable`·ETag·
    `Cache-Control`이 없고 Spring Security 기본 `no-store`가 CDN을 막는다.
16. **점유 좌석 1초 스냅샷이 장애 시 요청을 줄 세운다.** `GetOccupiedSeatsUseCase`의 `ConcurrentHashMap.compute` 안에서
    I/O를 한다. 버전이 없어 늦은 스냅샷이 새 이벤트를 덮는다. 판매 정책은 요청마다 DB에서 읽는다.
17. **찜 개수 때문에 공개 공연 상세마다 `likes` 전체를 스캔한다.** 인덱스는 `member_id`로 시작하는데 count는
    `(like_type, target_id)`로 거른다. show 스키마에는 보조 인덱스가 없다.
18. **판매 표시 기간이 비어 있는 공연 하나가 상세와 목록 전체를 500으로 만든다.** 두 컬럼이 모두 NULL이면 Hibernate가
    `@Embedded DisplaySaleWindow`를 `null`로 읽고 `Show`의 getter가 NPE를 낸다. nullable `performances.start_time`, NULL
    `start_date` 페이지 경계도 같은 계열이다.

### 인증·보안

19. **OAuth authorize마다 HttpSession이 생겨 힙을 고갈시킬 수 있다.** `OAuth2SecurityConfig`의 `IF_REQUIRED`,
    `OAuth2FrontendRedirectResolver`의 `getSession(true)`. 세션 30분 유지, 개수 제한·rate limit 없음. 기본
    `InMemoryOAuth2AuthorizedClientService`가 제공자 토큰을 축출 없이 쌓는다.
20. **리프레시 토큰 회전 순서 때문에 피크에 사용자가 로그아웃된다.** `RefreshAuthTokenUseCase`는 GETDEL 뒤 DB 확인을
    한다. 재사용 탐지·절대 수명·전체 폐기가 없고 Redis 키에 원문 토큰을 쓴다.
21. **로그아웃에 유효한 액세스 토큰이 필요하다.** 만료 뒤 로그아웃하면 401이고 리프레시 쿠키·Redis 토큰이 남는다.
22. **일회용 로그인 코드가 시작 브라우저에 묶이지 않는다(로그인 CSRF).** 피해자를 공격자 계정으로 로그인시켜 예매를
    공격자 계정에 쌓게 할 수 있다.
23. **이메일이 같으면 다른 소셜 계정을 확인 없이 연결한다.** `SocialAccountProvisioningService`. `loadUser` 예외가 실패
    핸들러를 건너뛰는 문제도 있다.
24. **운영 `/actuator/prometheus`와 Swagger가 permitAll이다.** management 포트도 앱 포트와 같다.

### 운영·배포

25. **배포가 곧 전체 중단이고 master push마다 자동으로 일어난다.** 단일 컨테이너 재생성과 nginx 재시작으로 모든
    WebSocket이 끊기고, 승인 게이트·판매 중 동결이 없다.
26. **CI/CD 공급망 위험이 운영 root로 이어진다.** 태그 고정 SSH 액션에 `SSH_KEY`를 넘기고 fingerprint 검증이 없으며,
    서버에 push 권한 Docker Hub 자격 증명을 둔다.
27. **JVM·컨테이너 설정이 비어 있다.** 힙 기본 25%, `ExitOnOutOfMemoryError` 없음, root 실행, 에이전트 `ADD`에 checksum 없음.

## P2: 수평 확장 전과 운영 품질

| 번호 | 지적 | 조치 |
| --- | --- | --- |
| 28 | Simple Broker·만료 리스너·스케줄러가 노드마다 따로 동작 | [수평 확장 고려 사항](2026-10-17-scale-out-considerations.md) |
| 29 | 스케줄러 스레드 1개를 재처리·스위퍼·purge·staleness가 공유 | 풀 크기 4 이상, purge 분할 |
| 30 | 만료 스위퍼에 맞는 인덱스 없음, status가 바인드 파라미터라 partial index 미사용, 5분 주기 | `orders(expires_at, id) WHERE status='PENDING'`, 상수 status, 주기 단축 |
| 31 | listener executor 기본값(8스레드, 무제한 큐), staleness 5분이 대기 건을 FAILED로 표시, 종료 시 유실 | 전용 bounded executor |
| 32 | JPA 감사 시각은 JVM 시간대, 업무 시각은 Asia/Seoul Clock, Dockerfile에 TZ 없음 | Clock 기반 `DateTimeProvider` |
| 33 | 일시적 DB/Redis 오류가 500과 ERROR 스택, E6003은 409 | 503/429와 `Retry-After`. E6003 상태는 gatling-test 계약이라 함께 변경 |
| 34 | 기동 시 앱 계정으로 migration, 일반 `CREATE INDEX`, expand/contract 규칙 없음 | 별도 단계, `CONCURRENTLY`, N-1 호환 검사 |
| 35 | 업무 지표 없음, 로그 패턴에 날짜 없음, `%X{traceId}`가 비어 있음 | Micrometer 지표, 구조화 로그 |
| 36 | 만료 토큰마다 WARN, 연결마다 INFO, 재처리 제외 건마다 매 분 ERROR | DEBUG와 카운터, 샘플링 |
| 37 | 운영을 부하 대상으로 쓰고 합성 데이터를 적재, 공유 `.claude/settings.json`에 `run-step.ps1 -Prod` 허용 규칙 | staging 측정, 허용 규칙 제거 검토 |
| 38 | 경쟁 테스트 참가자 2명, 201이 아니면 모두 정상 거절로 셈 | 16명 이상, 오류 코드·사후 상태 단언, 장애 주입, STOMP 테스트 |

## P3: 낮은 위험과 세부 사항

- 다른 노드 시계가 늦으면 `Order.expire`가 예외를 던져 만료 이벤트를 잃는다.
- 취소가 `OrderCreated` 후처리보다 먼저 끝나면 선택 정리를 건너뛴다.
- `performance_seats.state` nullable, `Ticket`·`Payment`·`Order`에 `@Version` 없음, `Payment.approve()`가 READY에서도 허용.
- 목록 쿼리가 항상 join과 GROUP BY, 카드 목록이 `info` TEXT까지 읽음, 배너 정렬에 tie-breaker 없음.
- unlike 더블 탭 500, like 더블 탭 409(문서는 멱등 성공).
- 서명 없는 커서 조작 시 500, 400 응답에 내부 변환 메시지 노출.
- `AUTO` 대기열 모드에서 `preopen_queue_starts_at`이 NULL이면 대기열이 조용히 꺼짐.
- `required=false` 파라미터 등 null을 돌려주는데 `@Nullable`이 없는 곳.
- 운영 `use_sql_comments: true`, IDENTITY 키로 insert batching 불가, `in_clause_parameter_padding` 미설정.
- 108MB 정적 이미지가 boot jar 안에서 서빙됨.
- `forward-headers-strategy: framework`의 헤더 신뢰, 운영에서도 살아 있는 localhost 프런트엔드 분기.
- JWT에 `aud`·`typ`·`jti` 없음, `Bearer` 대소문자 구분, `toLowerCase()`에 `Locale.ROOT` 없음.

## 문서와 코드 불일치

- [예매 수명주기](../core-booking-lifecycle.md)는 `PerformanceSeat`에 `reserve()`/`release()`가 있다고 하지만 없다.
- 같은 문서의 원칙 절은 정책·회원·입장 검증을 "booking DB 트랜잭션 밖"에서 끝낸다고 하지만 주문 생성 절과 코드는 읽기
  트랜잭션 안에서 처리한다.
- "상한을 넘긴 건은 자동 처리에서 빠진다"는 실제로 다른 건의 재처리까지 막는다(P0-2).
- [운영](../operations.md)의 "queue 상한·포화 경고"는 존재하지 않는다.
- `BookingEventListeners` 주석은 `__root` V10을 가리키지만 V1만 있다.
- `OrderCreated`의 `eventId`는 멱등성에 쓴다고 적혀 있지만 읽는 곳이 없다.
- `OrderController`의 "만료된 주문은 EXPIRED"는 스위퍼 전까지 사실이 아니다.
- `AdmissionTokenProperties`의 "빈 값이면 기동 실패"는 yml 기본값 때문에 무력화된다.
- `MemberAccountApi`의 E2000 → 409는 OAuth 필터 경로에서 handler에 도달하지 않는다.
- 전체 해제는 좌석 락 없이 동작해 "모든 선택 변경은 락으로 직렬화된다"는 설명과 다르다(현재 기능상 안전).

## 잘된 점

- DB 트랜잭션과 외부 I/O의 분리(OSIV off, listener `NOT_SUPPORTED`, 짧은 스냅샷 읽기 트랜잭션).
- outbox, listener id 고정 계약 테스트, `holdKey` 비교와 락 안 재확인으로 만든 멱등 후속 처리.
- Redis `TIME`만 시계로 쓰고 모든 키를 `KEYS`로 선언하며 같은 hash tag에 둔 선택 Lua.
- 정렬된 락 순서, `unitPrice`만으로 계산하는 금액, `(orderKey, memberId)` 조회로 막은 IDOR.
- 알고리즘 허용 목록과 상수 시간 MAC 비교를 쓰는 토큰 검증.
- Modulith·ArchUnit·NullAway·Spotless 게이트, 운영 migration을 그대로 쓰는 Testcontainers 테스트, 커서 2단계 페이지네이션.

## 확인하지 못한 사항

- 운영 Redis의 `maxmemory-policy`, AOF, 토폴로지, `notify-keyspace-events`
- 운영 환경 변수(`ADMISSION_TOKEN_*`, `JWT_SECRET` 엔트로피, Hikari 크기, TZ)
- nginx의 `/actuator/*` 차단, forwarded 헤더 처리, rate limit
- 운영 compose의 `stop_grace_period`, 메모리 제한, `JAVA_TOOL_OPTIONS`
- 실제 데이터 규모의 `EXPLAIN (ANALYZE, BUFFERS)`

## 권장 순서

1. 즉시: P0-1, P0-2, P0-3, 6, 13, 17, 18
2. 다음 대규모 판매 전: 8, 9, P0-4, 7, 15, 16, 20, 19
3. 결제 연결 전: P0-5를 ADR로 결정하고 구현
4. 수평 확장 전: [수평 확장 고려 사항](2026-10-17-scale-out-considerations.md)
