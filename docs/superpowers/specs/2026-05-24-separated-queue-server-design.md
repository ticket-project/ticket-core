# Separated Queue Server Design

**목표**

- 로그인 후 대기열 구조를 기준으로 대기열 서버와 티켓 서버를 분리한다.
- 대기열 서버는 대량 진입, 순번 조회, active member 승격, admission token 발급만 담당한다.
- 티켓 서버는 기존 Auth 로직과 예매 도메인을 유지하고, JWT와 admission token을 검증한 사용자만 좌석/홀드/주문 API에 진입시킨다.

**적용 범위**

- 포함: 대기열 서버 분리, JWT 검증 공통화, 대기열 Redis 모델, admission token 계약, 티켓 서버 admission gate, API Gateway routing, 프론트엔드 polling 흐름
- 제외: 익명 대기열, 결제 도메인, 관리자 대기열 정책 UI, MQ/worker 기반 주문 저장 비동기화

## 1. 확정된 방향

- 대기열은 로그인 후 진입한다.
- 대기열 단위는 1차 구현에서 `performanceId`로 둔다.
- 대기열 참가 식별자는 클라이언트 계약에서 `queueEntryId`를 제거하고 `performanceId + memberId`로 단순화한다.
- 대기 순서는 Redis `ZSET`으로 관리한다.
- 입장 가능 사용자는 Redis active member 저장소로 관리한다.
- 대기열 서버는 최초 `enter`에서만 accessToken을 검증하고, 이후 polling은 Redis에 저장된 queue session을 기준으로 처리한다.
- 티켓 서버는 매 요청마다 queue Redis를 조회하지 않고, 대기열 서버가 발급한 짧은 TTL의 서명형 admission token을 검증한다.
- 사용자의 신원 인증은 기존 Ticket 내부 Auth가 발급한 JWT를 기준으로 한다.
- 대기열 적용 여부는 Queue Server나 Queue Redis가 아니라 Ticket Server가 회차 정책(`Performance.queueMode`, `preopenQueueStartAt`)으로 판단한다.
- Queue Server는 자신에게 라우팅된 요청을 항상 대기열 대상으로 보고, waiting/active/session/admission runtime만 처리한다.

## 2. 전체 흐름

```text
1. FE -> Ticket Server
   POST /api/v1/auth/login

2. Ticket Server -> FE
   accessToken(JWT), refreshToken cookie

3. FE -> API Gateway -> Ticket Server
   GET /api/v1/performances/{performanceId}/booking-entry

4. Ticket Server
   Performance queue policy를 조회해 DIRECT 또는 QUEUE를 결정
   - DIRECT: redirectUrl=/booking/seat?performanceId={performanceId}
   - QUEUE: queueEnterUrl=/api/v1/queue/performances/{performanceId}/enter

5-A. DIRECT이면 FE -> Ticket Server
   좌석/예매 화면으로 바로 이동
   Authorization: Bearer {accessToken}
   admission token은 보내지 않음

5-B. QUEUE이면 FE -> API Gateway -> Queue Server
   POST /api/v1/queue/performances/{performanceId}/enter
   Authorization: Bearer {accessToken}

6. Queue Server
   JWT 검증 후 memberId 추출
   Redis waiting ZSET에 performanceId + memberId 등록
   queueSessionId 발급 후 Redis session에 memberId, performanceId 저장

7. Queue Server Scheduler
   주기적으로 waiting 앞쪽에서 N명을 꺼내 active member로 승격
   active member TTL 설정

8. FE -> API Gateway -> Queue Server
   GET /api/v1/queue/performances/{performanceId}/status
   X-Queue-Session: {queueSessionId}

9. Queue Server -> FE
   WAITING이면 position, estimatedWaitSeconds, pollAfterSeconds 반환
   ACTIVE이면 admissionToken, redirectUrl 반환

10. FE
    Queue Server polling 중지
    redirectUrl로 좌석/예매 화면 이동
    accessToken이 만료됐으면 Ticket Server refresh 호출

11. FE -> API Gateway -> Ticket Server
    좌석 조회 / 좌석 선택 / hold / order 요청
    Authorization: Bearer {accessToken}
    대기열이 필요한 회차에서만 X-Admission-Token: {admissionToken}

12. Ticket Server
    JWT 검증
    Performance queue policy 확인
    대기열이 필요한 회차면 admissionToken 서명, 만료, memberId, performanceId 검증
    대기열이 필요 없는 회차면 admissionToken 없이 예매 도메인 처리
```

## 3. 서버 책임

### 3.1 Ticket Server

Ticket Server는 현재 `core-api`의 역할을 유지한다.

- 회원가입, 로그인, refresh, logout
- OAuth2 토큰 교환
- accessToken 발급
- refresh token 저장과 회전
- 공연/회차/좌석 조회
- 좌석 선택, hold, 주문 시작
- admission token 검증
- 회차별 대기열 적용 여부 판단
- 예매 진입 판단 API `GET /api/v1/performances/{performanceId}/booking-entry` 제공

현재 JWT 발급/검증 로직은 `core-api`의 `JwtTokenService`에 있다. Queue Server도 동일 JWT를 검증해야 하므로 JWT 검증 코드는 공통 모듈로 분리한다.

### 3.2 Queue Server

Queue Server는 로그인 기능을 직접 가지지 않는다.

- Ticket Server가 발급한 JWT 검증. 단, 최초 `enter`에서만 수행한다.
- 대기열 등록
- queue session 기반 대기 상태 조회
- active member 승격 scheduler
- admission token 발급
- 대기열 Redis key 소유

Queue Server는 회원 DB나 주문 DB에 직접 접근하지 않는다. 또한 회차가 대기열 대상인지 여부를 판단하지 않는다. `performanceId`별 대기열 적용 정책은 Ticket Server가 소유하고, Queue Server는 자신에게 라우팅된 회차의 waiting/active/session/admission runtime만 처리한다. Queue Server 설정의 `maxActiveUsers`, `entryRetention`, `pollAfterSeconds`는 대기열 적용 여부가 아니라 런타임 처리량과 TTL 정책이다.

### 3.3 API Gateway

사용자는 Queue Server와 Ticket Server의 도메인을 직접 구분하지 않는다. 앞단에 Spring Cloud Gateway 같은 API Gateway를 두고 단일 외부 도메인을 제공한다.

예시 routing:

- `/api/v1/queue/**` -> Queue Server
- `/api/v1/auth/**` -> Ticket Server
- `/api/v1/performances/**` -> Ticket Server
- `/api/v1/orders/**` -> Ticket Server
- `/ws/**` -> Ticket Server 또는 WebSocket 전용 Gateway route

Gateway는 `Authorization`, `X-Queue-Session`, `X-Admission-Token` 헤더를 보존한다. 인증/인가의 최종 판단은 각 내부 서버가 수행하고, Gateway는 경로 라우팅과 공통 rate limit, CORS, TLS termination을 담당한다.

## 4. 모듈 구조

확정 구현은 기존 `ticket` 저장소 내부 모듈이 아니라 별도 폴더의 독립 Queue Server 프로젝트를 기준으로 한다. Queue Server는 현재 멀티모듈이 아니라 단일 Spring Boot/Gradle 프로젝트로 유지한다.

```text
C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-queue
├── src/main/java/com/ticket/queue
│   ├── controller      # enter/status/leave HTTP API
│   ├── service         # JWT 최초 검증, session 기반 status, admission 응답 조립
│   ├── scheduler       # waiting performance 주기적 active 승격
│   ├── domain          # use case, policy, model, port, lock annotation
│   ├── infra           # Redisson 저장소, Redis 설정, distributed lock AOP
│   └── config          # security, JWT/admission/redirect 설정
└── src/main/java/com/ticket/support/security
    ├── jwt             # access token 검증 유틸
    └── admission       # admission token 발급/검증 유틸
```

`support/security` 책임:

- JWT 설정 모델
- JWT 검증기
- 인증 사용자 claims 모델
- Bearer token 추출 유틸
- admission token 발급/검증 코드

Access token 발급 책임은 Ticket Server 내부 Auth에 남긴다. Queue Server는 access token을 발급하지 않고 검증만 수행한다. 공통 보안 코드는 1차 구현에서는 양쪽 프로젝트에 같은 계약으로 두고, 이후 별도 artifact로 배포할 수 있다.

```text
ticket repository
core:core-api
  -> support:security
  -> core:core-domain
  -> core:core-infra

ticket-queue repository
single Spring Boot project
  -> com.ticket.queue.*
  -> com.ticket.support.security.*
```

Queue 관련 런타임 코드는 `ticket-queue`로 분리한다. Queue Server는 Ticket Server의 회원/주문/좌석 DB repository를 직접 참조하지 않는다.

```text
1단계: support:security 추가, JWT/admission token 공통화
2단계: C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-queue 독립 프로젝트 생성
3단계: Queue Server를 단일 Spring Boot 프로젝트로 구성
4단계: core-api에서 queue controller 제거 또는 Gateway route에서 제외
5단계: core-api에는 회차 정책 기반 admission gate만 유지
```

## 5. JWT 정책

기존 Ticket Auth가 accessToken과 refreshToken을 계속 담당한다.

- accessToken: Queue Server와 Ticket Server가 모두 검증
- refreshToken: Ticket Server만 처리
- Queue Server: refresh endpoint를 제공하지 않음
- FE: accessToken은 Queue Server `enter`와 Ticket Server 요청의 `Authorization` 헤더에 사용
- FE: Queue Server `status`와 `leave`에는 accessToken 대신 `queueSessionId`를 사용
- FE: refreshToken은 기존처럼 Ticket Server refresh 흐름에서만 사용

현재 JWT는 HS256 shared secret 기반이다. 1차 구현에서는 두 서버가 같은 `security.jwt.secret-key`를 공유한다.

운영 보안성을 높이는 후속 단계에서는 RS256으로 전환한다.

- Ticket Server: private key로 accessToken 발급
- Queue Server: public key로 accessToken 검증
- Ticket Server: public key로 자체 검증

## 6. Admission Token 정책

Admission token은 JWT와 별개다.

```text
JWT = 사용자가 누구인지 인증
admissionToken = 사용자가 특정 performance 예매 구역에 입장해도 되는지 허가
```

Queue Server가 active member로 승격된 사용자에게 admission token을 발급한다.

토큰 payload:

- `iss`: `ticket-queue`
- `aud`: `ticket-api`
- `sub`: `memberId`
- `performanceId`
- `scope`: `ticket-admission`
- `iat`
- `exp`
- `jti`

TTL 기본값은 5분으로 둔다. 실제 값은 `app.queue.admission-token-ttl` 설정으로 관리한다. ACTIVE 응답 이후 accessToken refresh가 필요할 수 있으므로, admission token TTL은 refresh 왕복 시간을 충분히 감당해야 한다.

Ticket Server 검증 조건:

- admission token 서명이 유효하다.
- admission token이 만료되지 않았다.
- `scope`가 `ticket-admission`이다.
- `aud`가 `ticket-api`다.
- JWT의 `memberId`와 admission token의 `sub`가 같다.
- 요청 경로의 `performanceId`와 admission token의 `performanceId`가 같다.

1차 구현에서는 admission token을 상태 저장하지 않는다. 따라서 발급 후 강제 폐기는 TTL 만료에 의존한다. 강제 폐기가 필요하면 후속 단계에서 `jti` blacklist 또는 Redis 저장형 admission token으로 확장한다.

## 7. Redis 모델

대기열 Redis key는 queue prefix를 사용한다.

```text
queue:waiting:{performanceId}
queue:active:{performanceId}
queue:sequence:{performanceId}
queue:member:{performanceId}:{memberId}
queue:session:{queueSessionId}
```

운영 구성은 Queue Redis를 Redis Cluster로 둔다. queue polling은 read traffic이 크고, scheduler와 enter는 write traffic을 만든다. Redis Cluster를 쓰면 performance 단위 key가 특정 slot에 과도하게 몰리지 않는지 부하 테스트로 확인해야 한다. 원자적 다중 key 연산이 필요해지는 구간은 같은 hash tag를 쓰거나 Lua/락 범위를 재검토한다.

Ticket Server의 좌석 선택, hold, 주문 lock Redis는 1차 운영에서 단일 Redis로 둔다. 대기열 burst 부하가 좌석/hold latency에 영향을 주지 않도록 Queue Redis Cluster와 Ticket Redis를 물리적으로 분리한다.

### 7.1 waiting

```text
key: queue:waiting:{performanceId}
type: ZSET
member: memberId
score: sequence
```

score는 timestamp보다 단조 증가 sequence를 우선 사용한다. 같은 밀리초에 많은 사용자가 들어와도 순서가 안정적이기 때문이다.

### 7.2 active

```text
key: queue:active:{performanceId}
type: TTL 지원 set 또는 zset
member: memberId
ttl 또는 score: active 만료 시각
```

현재 기술 스택이 Redisson이므로 1차 구현은 `RSetCache<Long>` 또는 동등한 구조를 사용한다. 표준 Redis 호환성을 더 중시하면 active도 `ZSET(memberId, expiresAtEpochMillis)`로 두고 scheduler가 만료 사용자를 정리한다.

### 7.3 member state

```text
key: queue:member:{performanceId}:{memberId}
value: WAITING 또는 ACTIVE
ttl: queue retention
```

이 키는 enter idempotency와 상태 복구를 위해 사용한다. 필수 상태는 waiting/active 자료구조에서 계산하고, member state는 보조 캐시로 취급한다.

### 7.4 queue session

```text
key: queue:session:{queueSessionId}
type: HASH 또는 STRING JSON
fields:
  performanceId
  memberId
  createdAt
ttl: queue session ttl
```

`queueSessionId`는 Queue Server가 생성하는 충분히 긴 랜덤 값이다. 클라이언트가 `memberId`를 직접 보내지 않게 하기 위한 Redis lookup handle이며, JWT처럼 매 요청마다 서명 검증하는 토큰이 아니다.

Queue Server는 `enter`에서만 accessToken을 검증해 `memberId`를 추출한다. 이후 `status`와 `leave`는 `queueSessionId`로 Redis session을 조회하고, session에 저장된 `memberId`와 `performanceId`로 waiting/active 상태를 계산한다.

`queueSessionId`가 탈취되면 대기 상태 조회와 ACTIVE 상태의 admission token 수신 시도는 가능하다. 그러나 Ticket Server 예매 API는 JWT의 `memberId`와 admission token의 `sub`를 비교한다. 따라서 queue session만 탈취한 사용자는 Ticket Server 예매 API를 통과할 수 없다.

## 8. Queue Server API

### 8.1 Enter

```http
POST /queue/performances/{performanceId}/enter
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "status": "WAITING",
  "position": 1532,
  "estimatedWaitSeconds": 180,
  "pollAfterSeconds": 3,
  "queueSessionId": "opaque-random-id",
  "admissionToken": null
}
```

정책:

- 이미 active이면 `ACTIVE`와 새 admission token을 반환한다.
- 이미 waiting이면 기존 position을 반환한다.
- waiting/active에 없으면 새로 등록한다.
- 같은 회원이 같은 performance에 중복 등록되지 않는다.
- 응답에는 이후 polling에 사용할 `queueSessionId`를 포함한다.

### 8.2 Status

```http
GET /queue/performances/{performanceId}/status
X-Queue-Session: {queueSessionId}
```

응답:

```json
{
  "status": "ACTIVE",
  "position": null,
  "estimatedWaitSeconds": 0,
  "pollAfterSeconds": null,
  "admissionToken": "signed-token",
  "redirectUrl": "/performances/10/seats"
}
```

정책:

- active면 admission token과 Ticket Server 예매 화면으로 이동할 `redirectUrl`을 반환한다.
- waiting이면 `ZRANK` 기반 position을 반환한다.
- `queueSessionId`가 없거나 만료되면 `EXPIRED` 또는 `NONE` 계열 상태를 반환한다.
- session의 `performanceId`와 요청 path의 `performanceId`가 다르면 거부한다.
- 어느 곳에도 없으면 `EXPIRED` 또는 `NONE` 계열 상태를 반환한다.
- 프론트는 `pollAfterSeconds`보다 빠르게 polling하지 않는다.
- ACTIVE 응답 후 프론트는 polling을 중지하고 Queue Server를 다시 호출하지 않는다.

polling interval 정책:

- 입장이 멀수록 길게, 가까울수록 짧게 내려준다.
- 예시 기본값은 `position > 10_000: 30초`, `position > 1_000: 10초`, `position > 100: 5초`, `position <= 100: 2초`다.
- 이 값은 Queue Server 설정으로 관리하고, FE는 서버가 내려준 `pollAfterSeconds`를 따른다.

### 8.3 Leave

```http
POST /queue/performances/{performanceId}/leave
X-Queue-Session: {queueSessionId}
```

정책:

- waiting이면 waiting ZSET에서 제거한다.
- active이면 active set에서 제거한다.
- leave 후 scheduler가 다음 대기자를 승격할 수 있다.
- 클라이언트 종료는 신뢰할 수 없으므로 TTL 만료를 기본 정리 수단으로 둔다.

## 9. Queue Scheduler

Queue Scheduler는 Queue Server 내부에서만 실행한다.

주기:

- 기본 1초
- `app.queue.advance-interval`로 설정

처리:

```text
1. performanceId별 runtime capacity policy 조회
2. 현재 active 수 계산
3. maxActiveUsers까지 남은 슬롯 계산
4. waiting 앞쪽에서 남은 슬롯만큼 memberId 조회
5. waiting에서 제거
6. active에 TTL과 함께 저장
7. member state 갱신
```

동시 실행 방지:

- performanceId별 분산락을 사용한다.
- Queue Server가 여러 대 떠도 같은 performance의 승격은 한 번만 수행한다.

## 10. Ticket Server Admission Gate

Ticket Server는 보호 대상 API에서 회차 정책을 먼저 확인하고, 대기열이 필요한 회차에서만 admission token을 검증한다.

보호 대상 1차 기준:

- `GET /api/v1/performances/{performanceId}/seats/status`
- `GET /api/v1/performances/{performanceId}/seats/availability`
- `POST /api/v1/performances/{performanceId}/holds`
- 좌석 선택/해제 API

검증 흐름:

```text
1. Spring Security JWT filter가 accessToken 검증
2. AdmissionTokenValidator가 performanceId로 회차 정책 조회
3. 대기열이 필요 없는 회차면 X-Admission-Token 없이 통과
4. 대기열이 필요한 회차면 X-Admission-Token 추출
5. admission token 검증
6. JWT memberId와 admission token subject 비교
7. path performanceId와 admission token performanceId 비교
8. 통과 시 controller 진입
```

에러:

- admission token 없음: `ADMISSION_TOKEN_REQUIRED`
- admission token 만료: `ADMISSION_TOKEN_EXPIRED`
- admission token 위변조: `ADMISSION_TOKEN_INVALID`
- member/performance 불일치: `ADMISSION_TOKEN_MISMATCH`

기존 `QueueAdmissionInterceptor`는 제거한다. `ticket-be`는 예매 진입 컨트롤러에서 `AdmissionTokenValidator`를 명시적으로 호출해 `X-Admission-Token`을 검증한다.

## 11. Frontend 상태 머신

프론트엔드는 대기열과 티켓 API를 분리해서 호출한다.

```text
IDLE
-> LOGIN_REQUIRED
-> ENTERING_QUEUE
-> WAITING
-> ACTIVE
-> TICKETING
-> EXPIRED
```

### 11.1 예매 진입

1. 사용자가 예매 버튼을 누른다.
2. FE가 Ticket Server `GET /api/v1/performances/{performanceId}/booking-entry`를 호출한다.
3. 응답이 `DIRECT`이면 좌석 화면으로 이동한다. accessToken이 없으면 로그인 후 좌석 화면으로 복귀한다.
4. 응답이 `QUEUE`이면 대기열 화면으로 이동한다. accessToken이 없으면 로그인 후 대기열 화면으로 복귀한다.
5. 대기열 화면이 Queue Server `enter`를 호출한다.
6. Queue Server가 반환한 `queueSessionId`를 대기열 화면 상태로 저장한다.

### 11.2 대기 화면

- `WAITING`이면 position과 estimated wait를 표시한다.
- `pollAfterSeconds` 기준으로 status polling을 수행한다.
- status polling에는 accessToken을 보내지 않고 `queueSessionId`를 보낸다.
- 같은 화면에서 중복 polling timer가 생기지 않게 한다.
- 브라우저 새로고침 시 `queueSessionId`가 남아 있으면 status를 다시 호출한다.
- `queueSessionId`가 없거나 만료되면 enter부터 다시 시작한다.

### 11.3 입장 후

- `ACTIVE` 응답을 받으면 admissionToken을 저장한다.
- 응답의 `redirectUrl`로 좌석 화면에 이동한다.
- Ticket Server 요청마다 아래 헤더를 붙인다.

```http
Authorization: Bearer {accessToken}
X-Admission-Token: {admissionToken}
```

### 11.4 만료와 복구

- ACTIVE 응답 이후에는 Queue Server polling을 중지하고 Queue Server를 다시 호출하지 않는다.
- accessToken이 만료되면 Ticket Server refresh API로 accessToken을 갱신한 뒤 admissionToken과 함께 ticket 요청을 보낸다.
- admission token 만료 시각은 token 내부 `exp` claim에만 둔다. 프론트가 남은 시간을 표시해야 하면 token payload를 디코딩해 참고하되, 보안 판단은 Ticket Server 검증 결과만 신뢰한다.
- Ticket Server가 admission token 만료/invalid를 반환하면 기존 admissionToken은 버리고 대기열 enter부터 다시 시작한다.

## 12. 단계별 구현 계획

### 1단계: 공통 보안 기반

- `support:security` 모듈 추가
- JWT 검증 로직 공통화
- Ticket Server 기존 로그인/refresh 흐름 유지
- admission token 발급/검증 컴포넌트 추가

### 2단계: Queue Server 분리

- `C:\Users\mn040\IdeaProjects\ticket-queue` 독립 프로젝트 추가
- 단일 Spring Boot/Gradle 프로젝트로 controller/service/domain/infra/config 패키지 구성
- queue enter/status/leave controller 이동 또는 복제 후 기존 core-api endpoint 비활성화
- Queue Server 전용 security filter 구성
- Queue Server Redis adapter 구성
- ACTIVE status 응답에 `admissionToken`, `redirectUrl` 포함
- WAITING status 응답에 position 기반 `pollAfterSeconds` 포함

### 3단계: Gateway 구성

- Spring Cloud Gateway 모듈 또는 별도 gateway application 추가
- 외부 단일 도메인 기준 route 구성
- `/api/v1/queue/**`는 Queue Server로, 예매/인증 API는 Ticket Server로 전달
- admission 관련 헤더를 제거하거나 변조하지 않도록 route filter 검증

### 4단계: 대기열 모델 단순화

- 클라이언트 계약에서 `queueEntryId` 제거
- `performanceId + memberId` 기준 enter/status/leave로 변경
- waiting ZSET member를 `memberId`로 변경
- active member TTL 구조 도입

### 5단계: Ticket Server admission gate

- 기존 `QueueAdmissionInterceptor`는 제거하고 `AdmissionTokenValidator` 기반 검증 gate로 재구성
- 보호 대상 API에 annotation 적용
- admission token 에러 타입 추가
- 기존 queue token 기반 테스트를 admission token 기반으로 변경

### 6단계: Frontend 계약 반영

- 예매 버튼에서 Ticket Server `booking-entry` 호출 후 DIRECT/QUEUE 분기
- status polling 상태 머신 구현
- 서버가 내려준 `pollAfterSeconds` 기준 동적 polling scheduling
- admissionToken 저장과 Ticket Server API 헤더 자동 첨부
- ACTIVE 응답의 `redirectUrl`로 예매 화면 이동
- admission 만료/invalid 시 queue 복귀 처리

### 7단계: 부하와 정합성 검증

- Queue enter/status 부하 테스트
- Queue scheduler 승격 정합성 테스트
- Admission token 검증 contract test
- Ticket open flow Gatling 시나리오 갱신
- Queue Redis Cluster와 Ticket 단일 Redis 분리 구성 기준 부하 테스트

## 13. 테스트 전략

### 13.1 Queue Server 단위 테스트

- 이미 waiting 중인 회원이 enter하면 기존 position을 반환한다.
- active 회원이 enter/status를 호출하면 admissionToken을 반환한다.
- waiting position은 `ZRANK + 1`로 계산된다.
- scheduler는 `maxActiveUsers`를 넘겨 active로 승격하지 않는다.
- active TTL이 지나면 status가 expired/none으로 바뀐다.

### 13.2 Admission Token 테스트

- 정상 토큰은 검증된다.
- 만료 토큰은 거부된다.
- 다른 memberId 토큰은 거부된다.
- 다른 performanceId 토큰은 거부된다.
- 위변조 토큰은 거부된다.

### 13.3 Ticket Server 테스트

- admission token 없이 보호 API 호출 시 거부된다.
- JWT 없이 보호 API 호출 시 기존 인증 정책으로 거부된다.
- 정상 JWT와 admission token이 있으면 controller까지 진입한다.
- JWT memberId와 admission token subject가 다르면 거부된다.

### 13.4 Frontend 시나리오 테스트

- 비로그인 예매 클릭 시 로그인으로 이동한다.
- 로그인 후 queue enter가 호출된다.
- WAITING이면 polling이 시작된다.
- ACTIVE면 좌석 화면으로 이동하고 Ticket Server 요청에 admissionToken이 포함된다.
- admission 만료 응답을 받으면 Queue Server status를 다시 조회하지 않고 대기열 enter부터 다시 시작한다.

## 14. 남은 리스크

- 로그인 후 대기열이므로 로그인 API 폭주는 여전히 Ticket Server가 받는다. 사전 로그인 유도, 로그인 rate limit, refresh token 유지 전략이 필요하다.
- 1차 admission token은 무상태 서명형이므로 강제 폐기가 어렵다. TTL을 짧게 유지하고, hold/order 단계의 별도 TTL과 정합성 검증에 의존한다.
- Queue Server와 Ticket Server가 HS256 secret을 공유하면 secret 유출 시 영향 범위가 크다. 운영 단계에서는 RS256 전환을 검토한다.
- Queue policy를 Queue Server가 자체 설정으로 들고 있으면 Ticket Server의 performance 설정과 어긋날 수 있다. 이후 performance queue policy 조회 계약이 필요하다.
- Queue Redis Cluster와 Ticket 단일 Redis를 분리하므로 queue polling 부하가 ticket hold/selection Redis 성능에 직접 영향을 주지 않는다. 대신 Redis 운영 대상이 2개가 되므로 모니터링, 알람, 백업/장애 대응 runbook을 분리해야 한다.
