# 남은 작업 (2026-10-25 주석·문서 조사, 2026-10-17 코드 리뷰)

2026-10-25 주석·문서 전수 조사에서 찾았지만 문서·주석 수정만으로 끝나지 않아 남긴 항목이다. 코드·설정·테스트를
바꾸거나 정책을 정해야 한다. 미해결 항목의 추적 원본은 [CONTRIBUTING](../CONTRIBUTING.md#이슈와-라벨)대로 GitHub
Issues이므로, 착수하거나 Issue로 옮긴 항목은 여기서 지운다. 각 항목의 줄 번호는 조사 시점(`67d197fae`) 기준이다.
작업 대화에서 나왔지만 하지 않은 일도 사용자가 승인하면 아래 해당 절에 같은 형식으로 더한다.

2026-10-17 전체 코드 리뷰에서 남은 항목도 함께 둔다. 수평 확장 논의에서 남은 결정과 구현은
[스케일 아웃 결정 목록](plans/scale-out-decisions.md)에 모았다. 2026-10-25에 `6fe601ec1` 기준으로 코드에 그대로
남아 있는지 grep으로 다시 확인했고, 동작을 실행해 확인하지는 않았다. 리뷰 원문은 로컬 브랜치
`docs/codebase-review-scale-out`의 `docs/reviews/`에 있다. 괄호 안의 번호(P0-1 등)는 그 리뷰 문서의 번호다.

## 2026-10-17 코드 리뷰에서 남은 수정

### 운영 재개 전 필수(보안)

- **WebSocket 프레임 인가(P0-1).** `WebSocketAuthInterceptor`는 CONNECT만 검사하고, `WebSocketConfig`는
  `enableSimpleBroker("/topic")`만 둔다. 클라이언트 SEND가 `/topic/**`로 가면 Simple Broker가 구독자 전원에게 방송한다.
  후보: `/topic/**` SEND 거부, SUBSCRIBE 목적지를 좌석 토픽으로 제한, CONNECT 없는 세션의 프레임 거부.
  `WebSocketAuthInterceptorTest`의 "CONNECT가 아닌 프레임은 그대로 통과한다"도 바꾼다.
- **입장 토큰 fail-open(P0-3).** `application.yml`의 `security.admission.enforcement-enabled` 기본값이 `false`이고,
  `secret-key`에 공개된 32바이트 기본값이 있다. 후보: base 설정에서 기본 비밀값 제거(누락 시 기동 실패), 강제 여부를
  기동 로그에 남김, 장기적으로 비대칭 서명.
- **OAuth 세션.** `OAuth2SecurityConfig`가 `IF_REQUIRED` 세션과 기본 `HttpSessionOAuth2AuthorizationRequestRepository`를
  쓰고 `OAuth2FrontendRedirectResolver`가 `getSession(true)`를 부른다. authorize 요청만으로 세션이 무제한 쌓인다. 기본
  `InMemoryOAuth2AuthorizedClientService`도 제공자 토큰을 축출 없이 쌓는다. 후보: Redis나 서명 쿠키 저장소, OAuth 체인
  STATELESS, no-op `OAuth2AuthorizedClientRepository`, nginx rate limit. 다중 인스턴스 전환의 전제이기도 하다.
- **로그아웃.** `/api/v1/auth/logout`이 permitAll이 아니어서 액세스 토큰이 만료된 뒤 로그아웃하면 401이 나고 리프레시
  쿠키와 Redis 토큰이 남는다. 후보: 리프레시 쿠키만으로 로그아웃 허용, 쿠키는 항상 삭제, 멱등 200.
- **로그인 CSRF.** 일회용 로그인 코드가 로그인을 시작한 브라우저에 묶이지 않는다. 후보: authorize 시점의 nonce 쿠키나
  PKCE 방식 challenge를 교환 때 검증.
- **소셜 계정 자동 연결.** `SocialAccountProvisioningService`가 같은 이메일의 다른 제공자 계정을 확인 없이 연결한다.
  `loadUser` 예외가 `OAuth2AuthenticationException`으로 감싸지지 않아 실패 핸들러를 건너뛰는 문제도 있다.
- **운영 노출.** `/actuator/prometheus`와 Swagger가 permitAll이고 management 포트가 앱 포트와 같다. nginx 차단 여부는
  확인하지 못했다.
- **CI/CD.** `deploy.yml`이 태그로만 고정한 `appleboy/ssh-action`에 `SSH_KEY`를 넘기고 호스트 fingerprint를 검증하지
  않는다. 서버에는 push 권한이 있는 Docker Hub 자격 증명으로 로그인한다. 후보: 액션 커밋 SHA 고정, fingerprint, 읽기
  전용 토큰, digest 배포.

### 바로 고칠 결함

- **이벤트 재처리 정체(P0-2).** `EventPublicationMaintenance`가 `withMaxInFlight(4)`와 `withFilter(this::isRetryable)`를
  쓴다. Modulith 2.1.1은 최대 4건을 `publicationDate asc`로 읽은 뒤 필터를 메모리에서 적용하므로, 재시도 상한을 넘긴
  오래된 FAILED 4건이 생기면 이후 실패 publication은 재처리되지 않는다(바이트코드로 확인). 후보: 상한 초과 건을 FAILED
  집합에서 빼기, `maxInFlight` 조정, "poison 5건 + 정상 1건" 테스트.
- **멀티락 해제(6).** `RedissonDistributedLock`은 멀티락에 `lock.unlock()`을 한 번 부른다. Redisson 4.7.0
  `RedissonMultiLock.unlock()`은 `locks.forEach(Lock::unlock)`이라 첫 예외에서 나머지가 남고 watchdog이 계속 갱신한다.
  `tryLock` 실패 때도 `finally`에서 해제를 부르고, 소유권 상실을 DEBUG로만 남긴다.
- **판매 표시 기간 NULL(18).** `Show`의 `@Embedded DisplaySaleWindow`는 두 컬럼이 모두 NULL이면 `null`로 읽히고,
  `saleDisplayStatusAt` 등 getter가 NPE를 내 공연 상세와 목록 페이지가 500이 된다.
- **주문 좌석 수 상한(13).** `CreateOrderRequest.seatIds`에 `@Size`가 없고 `max_hold_seat_count`는 NULL을 허용한다.
  좌석이 많으면 `OrderCreated`가 `serialized_event VARCHAR(4000)`을 넘어 좌석을 다 잡은 뒤 500으로 롤백된다.
- **찜 개수 인덱스(17).** `LikeRepository.countByLikeTypeAndTargetId`는 `(like_type, target_id)`로 거르지만 인덱스는
  `member_id`로 시작한다. show 스키마에도 `performances.show_id` 등 보조 인덱스가 없다.
- **좌석 선택 멱등성(14).** `RedissonSeatSelectionStore`의 선택 Lua는 키가 있으면 소유자와 무관하게 거절해, 같은 회원의
  재시도가 자기 좌석에 E4001을 받는다. 선택 한도가 hold 좌석을 세지 않는다.

### 다음 대규모 판매 전

- **fail-fast 타임아웃(8).** `application*.yml`에 Hikari 크기·대기 시간, `statement_timeout`·`lock_timeout`, Redisson
  타임아웃 설정이 없다. 락 기본 대기는 `LockOptions`의 5초다. 후보: 커넥션 대기 2~3초, DB 문장·락 타임아웃, Redisson
  0.5~1초, 풀 고갈은 503과 `Retry-After`.
- **좌석 클릭 락(9).** `SeatSelectionWriter`의 `SELECT_LOCK`은 500ms를 기다린다. 인기 좌석에서는 E4001이 아니라 E6003이
  난다. 후보: 락 전에 선택 키를 확인해 즉시 실패.
- **좌석 이벤트 팬아웃(P0-4).** 좌석 락 안에서 구독자 전원에게 동기로 보내고, outbound 큐·transport 한도가 없다. 후보:
  회차별 변경분 병합(200~500ms), 시퀀스 번호, outbound·transport 한도, 부하 시나리오에 STOMP 구독자 추가.
- **keyspace 알림 설정(7).** `RedisExpirationListenerConfig`가 기동 시 `CONFIG SET`을 쓰고 실패는 WARN뿐이며, 만료
  worker 큐는 `Integer.MAX_VALUE`다. 후보: Redis 서버 설정으로 옮기고 지표로 노출, 큐 상한.
- **좌석맵·가용성 캐시(15).** 공개 GET인 좌석맵·가용성이 요청마다 좌석 전체를 읽고, `Cache-Control`·ETag가 없다.
- **리프레시 토큰 회전(20).** `RefreshAuthTokenUseCase`가 토큰을 먼저 지운 뒤 DB로 회원을 확인한다. DB가 포화되면 토큰만
  사라져 로그아웃된다. 재사용 탐지와 해시 저장도 없다.
- **JVM·컨테이너(27).** `Dockerfile`은 `java -jar app.jar`뿐이다. 힙 비율, `ExitOnOutOfMemoryError`, 비root 사용자,
  에이전트 `ADD`의 checksum이 없다.
- **배포 중단(25).** 배포가 단일 컨테이너를 재생성하고 nginx를 재시작해 모든 WebSocket이 끊긴다.

### 수평 확장 구현(ADR 0022 채택 뒤)

[스케일 아웃 결정 목록](plans/scale-out-decisions.md#결정-뒤-구현할-것)으로 옮겼다.

## 코드·설정·테스트 수정

### `architectureTest`가 `ArchitectureRulesTest`의 규칙을 돌리지 않는다

- 사실: `src/test/java/com/ticket/ArchitectureRulesTest.java`에 JUnit `@Tag("architecture")`만 있고 ArchUnit
  `@ArchTag`가 없다. ArchUnit JUnit 엔진은 `@ArchTag`만 읽으므로 `architectureTest`(`includeTags 'architecture'`)에서는
  `@Test` 6개만 돌고 `@ArchTest` 규칙 12개(계층 방향, api 순수성 등)는 돌지 않는다. 2026-10-25에 실행해 6건만 도는 것을
  확인했다. 태그 필터가 없는 전체 `test`와 CI에서는 돈다.
- 영향: AGENTS.md·skill·`build.gradle` 주석이 안내하는 대로 `architectureTest`만 돌리면 계층 규칙이 검증됐다고 믿게 된다.
- 후보: `@ArchTag("architecture")`를 함께 붙이고 `architectureTest` 실행 건수가 늘었는지 확인한다.

### 로컬 compose의 Core가 default 프로파일로 떠서 Flyway가 꺼진다

- 사실: `docker-compose-local.yml`의 `core` 서비스, `Dockerfile`, `application.yml`, `../local-env/ticket-core.env`
  어디에도 `SPRING_PROFILES_ACTIVE`가 없다. default 프로파일은 `spring.flyway.enabled: false`다. 실제 기동으로는
  확인하지 않았다.
- 영향: 새 migration(예: booking V4)이 적용되지 않은 채로 부하를 잴 수 있다. gatling-test `reset-local.ps1`의
  "Core가 기동하며 Flyway로 스키마를 만든다"도 성립하지 않을 수 있다.
- 후보: compose `core` 환경에 `SPRING_PROFILES_ACTIVE: local`을 넣고 기동 로그에서 Flyway 적용을 확인한다.

### 주문 생성이 모든 무결성 위반을 E5004로 바꾼다

- 사실: `CreateOrderUseCase`는 같은 회원·회차 PENDING 유일 인덱스 위반을 E5004(409)로 바꾸려고
  `DataIntegrityViolationException`을 통째로 잡는다.
- 영향: 다른 NOT NULL·길이 위반도 "진행 중인 주문" 409로 응답해 원인을 가린다. 발생 가능성은 낮다.
- 후보: 제약 이름(`uk_orders_pending_member_performance`)으로 걸러 그 경우만 E5004로 바꾼다.

### 회차 시작 시각의 null 계약이 세 곳에서 다르다

- 사실: 스키마에서 `performances.show_id`·`start_time`은 nullable이다. `ShowQuerydslRepository`의 `required()`는
  "NOT NULL 컬럼"이라며 null이면 예외를 던지고, 공개 snapshot(`PerformanceSummarySnapshot`, `PerformanceSaleSnapshot`)은
  시작 시각을 `@Nullable`로 선언한다.
- 후보: 스키마에 NOT NULL을 걸지(migration·운영 데이터 확인), `@Nullable`을 지울지, 단언을 지울지 하나로 정한다.

### 회차 표시값 10분 캐시 사이에 등급이 추가되면 주문이 500이 된다

- 사실: `PerformanceSaleHeaderReader`는 등급 표시값까지 회차별 10분 캐시한다. 그 사이 등급이 추가되면 등급별 잔여석
  응답에서 빠지고, 그 등급 좌석의 주문은 `PendingOrderCreator`가 등급 표시값을 찾지 못해 `IllegalStateException`(500)이 된다.
- 영향: 지금은 등급을 바꾸는 관리 기능이 없어 낮다. 관리 기능을 만들 때 캐시 무효화를 함께 정한다.

### 테스트가 이름이나 주석이 말하는 것을 검증하지 않는다

- `src/test/java/com/ticket/member/persistence/MemberSocialAccountPersistenceTest.java`: orphanRemoval·CascadeType.REMOVE가
  붙으면 실패한다고 주석에 적었지만, 탈퇴는 soft delete라 두 설정이 붙어도 통과한다. 실제 방어는
  `MemberSocialAccountMappingTest`다. 주석을 고치거나 실제로 잡는 사례를 만든다.
- `SeatSelectionWriterTest`의 이름 일부("락 내부", "DB검증 후", "락 획득 시점")는 assertion이 직접 확인하지 않는다.

### 쓰이지 않는 테스트 대역과 메서드

- `LikeModuleTests`·`ShowModuleTests`의 `MemberLookupApi` mock은 쓰는 bean이 없다. booking·like·venue 모듈 테스트의
  `JPAQueryFactory` mock도 해당 모듈 production 코드가 쓰지 않는다.
- `RecordingDistributedLock`의 `failWith()`·`acquisitions()`, `PerformanceRepository.findPerformanceGrades`는 production·테스트
  호출처가 없거나 테스트만 쓴다.

### 이슈 템플릿이 꺼진 Discussions로 안내한다

- 사실: `.github/ISSUE_TEMPLATE/config.yml`이 Discussions 링크를 두지만 저장소는 `has_discussions: false`다.
- 후보: 링크를 지우거나 Discussions를 켠다.

## 정해야 할 것

### 측정 사다리 상한과 확정 규칙

[Core 수용량](core-capacity.md#측정-순서) 5단계는 50 users/sec까지 오르고 확정 규칙이 없다.
core-capacity skill(`.agents/skills/core-capacity/SKILL.md`)은 100 users/sec까지 오르고 "C를 새 회차로 두 번 더 재서
세 번 모두 PASS면 확정"한다. 2026-10-25 운영 측정은 60 users/sec까지 쟀다. 원본(core-capacity.md)을 어느 쪽으로 정할지
결정한 뒤 두 문서를 맞춘다.

### 운영 `DD_SERVICE` 값

[운영](operations.md#core-용량-관측)은 `ticket-core`, core-capacity skill의 운영 측정은 `ticket-be`로 적는다. 운영 서버
compose의 실제 값을 확인해 한 값으로 맞춘다. 로컬 캐시의 `cache_*` 지표가 `/actuator/prometheus`에 나오는지도 이때
확인한다.

### 주석의 `ponytail:` 표식

`GetOccupiedSeatsUseCase`, `GetSaleOpeningSoonShowsPageUseCase`, `RedissonHoldStore`에 미뤄 둔 작업 표식이 있지만 표기
규칙이 문서에 없다. Issue로 옮길지, 규칙을 문서화할지 정한다.

### 수평 확장

[스케일 아웃 결정 목록](plans/scale-out-decisions.md)으로 옮겼다.

### 좌석 선택 만료·주문 만료(2026-10-26 중단)

2026-10-26 논의를 여기서 멈췄다. 결정 근거와 고르지 않은 안은 [스케일 아웃 결정 목록](plans/scale-out-decisions.md)
D2-1·D2-2에 있다. 아래 "계획"은 사용자가 준 구현 계획(저장소에 없음)이고, 번호는 그 계획의 절 번호다.

정한 것:

- **주문 만료(구현·커밋, `97ad1464`~`ec488786`).** Redis 만료 알림을 쓰지 않는다. `OrderExpirationTrigger`가
  5초마다(`worker.order-expiration.fixed-delay`) 돌고 ShedLock(JDBC, DB 시각 기준, `lockAtMostFor` 1분·`lockAtLeastFor`
  4초)으로 한 인스턴스만 실행한다. 만료 시각이 지난 PENDING을 부분 인덱스 `idx_orders_pending_expires_at`(booking V5)로
  100건씩 id 커서로 끝까지 읽고, 주문마다 따로 행 락 → `EXPIRED` → 커밋한다. 실패한 주문은 건너뛰고 다음 실행에서 다시
  잡는다. Redis hold는 `OrderTerminated` 이벤트로 지운다. 좌석 hold 키는 주문과 같은 hold 시간으로 TTL이 걸려 만료
  시각에 스스로 풀린다.
- **좌석 알림 relay(D2-1).** Redis pub/sub relay로 모든 인스턴스에 나눈다. 채널 하나, 받는 쪽은 상한 없는 단일 스레드다.
  FE가 좌석 상태를 화면 진입 때 한 번만 읽고 그 뒤로는 WebSocket으로만 바꾸기 때문이다(FE 수정 불가). 구현했다가
  되돌렸다(`96036b8e` → `0ef9b371`).
- **좌석 선택 만료.** 지금처럼 Redis TTL + keyspace 만료 알림 → `DESELECTED`다. 선택권은 TTL이 풀고, 알림은 화면용이다.
- **keyspace 만료 리스너 수신 확인.** 운영과 같은 조합(Redisson 연결, 구독 executor 따로)으로 실제 Redis에서 handler까지
  오는 것을 확인했다. 테스트 `src/test/java/com/ticket/booking/redis/RedisKeyExpirationNotificationIntegrationTest.java`는
  커밋하지 않았다. 되돌린 relay 테스트가 메시지를 못 받은 원인은 task executor 스레드 1개에 구독 executor를 따로 주지
  않은 테스트 설정으로 추정한다(Redisson 구독이 그 스레드를 계속 쥔다).

정할 것:

1. **좌석 선택 만료 알림의 중복(D2-2).** 결정 목록에는 "claim(`SET NX`)으로 한 대만 처리하고 relay로 보낸다"가 적혀
   있지만 구현은 되돌렸다. 계획 2.3은 "모든 서버가 같은 만료 알림을 받아 대표 서버·분산 락 없이 각자 자기 접속자에게만
   보낸다"이다. Claude 추천은 계획대로 claim 없이 로컬로 보내고, `SeatSelectionWriter.notifyReleasedIfFree`의 좌석
   락(`NOTIFY_LOCK`)을 빼는 것이다. 감수: 상태 확인과 전송 사이에 다른 사용자가 고르면 `DESELECTED`가 `SELECTED`보다
   늦게 도착해 화면이 다음 알림이나 새로고침까지 틀린다(판매는 서버가 막는다). 인스턴스가 N대면 Redis 상태 조회도
   N배다. 이렇게 정하면 relay에 "relay로 보내기"와 "자기 접속자에게만 보내기" 두 발행 경로가 생기고, 결정 목록 D2-2
   세부 결정을 고친다. 되돌린 claim 구현은 "비어 있으면 발행"을 Lua 하나(`publishIfFree`)로 해 이 틈이 없었다.
2. **relay 발행 위치(D2-1 세부).** 결정 목록은 "상태를 바꾸는 Lua 안에서 `PUBLISH`"로 정했지만, 좌석 락을 걷어낼지는
   [좌석 분산 락 다시 설계하기](plans/seat-lock-redesign.md)와 함께 정하기로 했다. 되돌린 구현(`96036b8e`)은 상태를
   바꾸는 스크립트와 따로 발행 Lua를 두고, 둘 사이의 순서는 좌석 락으로 지켰다. 다시 구현할 때 그대로 되살릴지 정한다.
3. **만료됐지만 아직 PENDING인 주문과 유일 인덱스.** 계획 3.1은 "만료 시각이 지난 PENDING은 무효, `EXPIRED` 전환은
   백그라운드 작업만"이다. 그런데 `BookingPreflightReader.ensureNoPendingOrder`(상태만 본다)와 유일 인덱스
   `uk_orders_pending_member_performance`가 만료 처리 전(최대 약 5초) 같은 회원·회차의 새 주문을 E5004로 막는다.
   PostgreSQL 부분 인덱스 조건에는 지금 시각을 넣을 수 없다. 좌석은 hold 키 TTL로 제때 풀려 다른 사용자에게는 영향이
   없다.
   - A(Claude 추천): 그대로 두고 3.1의 예외로 문서에 적는다. 코드 0줄. 만료 처리가 밀리면 막히는 시간도 늘어난다.
   - B: 주문 생성 때 내 만료된 PENDING을 그 자리에서 `EXPIRED`로 바꾸고 진행한다. 3.1을 어기고, 읽기 전용 사전 확인
     트랜잭션 구조를 바꿔야 한다(약 30줄과 테스트, 추정).
   - 인덱스를 빼는 안은 2026-10-19에 걷어낸 회원·회차 분산 락이 다시 필요해 뺐다.
4. **주문 만료 실행당 상한(계획 3.7).** 지금은 한 번 실행에서 상한 없이 끝까지 처리한다. 상한을 둘지, 값을 얼마로 할지
   부하 테스트로 정한다. 상한을 두면 커서가 실행마다 처음부터 시작하므로, 앞쪽 주문이 계속 실패할 때 뒤쪽 주문이
   밀리지 않는지 함께 본다.
5. **중복 실행 방지 수단(계획 3.4).** 계획은 Redis 분산 락, 지금 구현은 ShedLock JDBC다. Claude 추천은 유지다. DB 시각
   기준이라 서버 시계 차이에 덜 민감하고, 실행 결과도 DB에 쓴다. Redis로 바꿔야 하면 ShedLock Redis provider로 바꾼다.
6. **계획 6절의 값.** 스케줄러 주기(지금 5초), 실행당 상한, 락 유지 시간(지금 1분·4초), 좌석 전체 재동기화 주기(FE
   수정 불가라 D2-5에서 보류), Redis 클러스터 대응(D1-5), 모니터링 임계값. 지금 값은 모두 설정값이고 부하 테스트로
   정한다.

정한 뒤 할 것:

- relay를 `96036b8e` 바탕으로 다시 구현한다. 실패했던 `SeatStatusRelayIntegrationTest`는 구독 executor를 따로 준다.
- 정할 것 1에 따라 좌석 선택 만료 처리를 바꾼다.
- 계획 4절의 시나리오를 검토하고 테스트한다. 다중 인스턴스 검증을 포함한다.
  - 좌석 선택 만료: 만료 직후 재선택, 만료 알림보다 선택 알림이 먼저 도착, 모든 서버 수신, 알림 유실·지연, WebSocket
    재접속, 주문 생성 때 선택 키 삭제, 선택 키 삭제와 hold 생성 사이 장애, 클러스터 노드 장애로 구독 끊김.
  - 주문 만료: 만료 시각이 지나도 PENDING, 실행 중 서버 장애, 락 만료로 중복 실행, 같은 주문 동시 UPDATE, 수만 건
    적체, 특정 주문 반복 실패, hold가 먼저 만료되고 DB 반영 지연, hold 만료와 다른 사용자 선택 동시, 재시작 뒤 복구.
- booking V5 인덱스의 운영 적용 방식을 확인한다. 일반 `CREATE INDEX`는 만드는 동안 `orders` 쓰기를 막는다. 운영
  `orders` 크기를 보고 그대로 둘지, 트랜잭션 밖 `CONCURRENTLY`(Flyway 설정 필요)로 할지 정한다.
- [ADR 0022](adr/0022-seat-status-relay-and-single-expiration-claim.md)는 아직 "제안"이고 본문이 claim 기준이다. 정할 것
  1·2를 정한 뒤 다시 쓰고 상태를 바꾼다.
- 결정 목록 D0-2(60 u/s 멈춤 수정)는 "미정"으로 적혀 있지만 B(`@Cacheable`의 `sync = true` 제거)로 고쳐
  `origin/master`에 들어갔다(`43fe8873`). 이 브랜치(`docs/sync-comments-and-docs`)에는 그 커밋이 없다. 결정 목록 상태를
  고친다.

### 좌석 단위 DB 방어선(리뷰 P0-5)

booking V4는 회원·회차당 PENDING 1건만 DB로 막는다. 같은 좌석이 두 PENDING 주문에 들어가는 것은 Redis hold에만
의존한다. Redis 데이터 손실이나 만료 지연이 있으면 겹친 PENDING이 생기고, 결제 확정을 붙이면 이중 판매가 된다.
`Order.confirm()`은 만료 시각도 hold도 확인하지 않는다. 결제를 연결하기 전에 좌석 단위 예약 테이블이나 partial unique
index 중 무엇으로 막을지 ADR로 정한다.

### 리뷰 문서와 로컬 브랜치 처리

- 리뷰 원문(로컬 `docs/codebase-review-scale-out` 브랜치)은 보안 결함 상세를 담고 있고, "운영 재개 전에 고치지 못한 보안
  상세를 지운다"는 조건으로 올렸었다. 지금 원격에는 이 브랜치가 없다. master에 넣을지, 보안 상세를 뺀 판본으로 올릴지,
  로컬에만 둘지 정한다.
- 리뷰 항목과 이 문서의 항목을 GitHub Issue로 옮길지 정한다.

## 다른 저장소

- gatling-test의 capacity 스크립트 README(`../gatling-test/scripts/capacity/README.md`)는 03 시나리오를 다섯 요청으로, 조회 응답에 공연 요약·주문 조회를 넣어
  적고 있다. Core 쪽 [Core 수용량](core-capacity.md#왜-userssec인가)은 세 요청이다.
