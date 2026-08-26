> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-07-30. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Gateway 제거 설계

기준일: 2026-06-17

> 결정 기록: Gateway 제거 당시의 설계입니다. 현재 실행 방법과 서비스 계약은 루트 `README.md`, `docs/development.md`, 형제 저장소 `../ticket-queue/README.md`를 기준으로 확인하세요.

> 2026-06-17 후속 결정: `Passport`와 `ticket-common`도 제거한다. core는 `MemberPrincipal`, queue는 `AuthenticatedMember`를 서비스 로컬 principal로 사용하며, access/admission token 발급·검증 코드는 각 서비스가 직접 소유한다. 아래 본문 중 `Passport`/`ticket-common` 유지 내용은 이 후속 결정으로 대체된다.

## 목표

`ticket-gateway` 애플리케이션을 제거하고, `ticket-core`와 `ticket-queue`가 각각 외부 요청을 직접 받을 수 있는 구조로 전환한다. 티켓 오픈 시 `/api/v1/queue/**` 트래픽이 gateway를 먼저 통과하지 않게 하여 queue 라인의 병목과 core 라인의 병목을 분리한다.

이 설계에서 말하는 gateway 제거는 Spring Cloud Gateway 기반 `ticket-gateway` 애플리케이션과 `/api/**` path 기반 애플리케이션 라우터를 제거한다는 뜻이다. TLS 종료, L4/L7 로드밸런서, CDN, WAF, 정적 파일 origin은 운영 인프라로 계속 사용할 수 있다. 다만 이 계층은 `/api/v1/queue/**`와 `/api/**`를 하나의 애플리케이션 gateway로 모아 분기하지 않는다.

## 비목표

- queue 알고리즘, Redis key 모델, admission scheduler 자체를 재설계하지 않는다.
- 결제 도메인이나 주문 확정 흐름을 새로 설계하지 않는다.
- queue public state의 CDN/static JSON 방향은 유지한다.
- 운영 도메인명은 예시로만 사용하며, 실제 DNS 이름은 배포 환경에서 정한다.

## 현재 구조의 문제

현재 gateway는 세 가지 책임을 가진다.

1. `/api/v1/queue/**`, `/ws/**`, `/api/**` 라우팅
2. 사용자 `Authorization` access token 검증
3. downstream용 `X-Internal-Auth` passport token 발급 및 클라이언트 위조 헤더 제거

이 구조는 보안 경계를 gateway에 집중시키는 장점이 있지만, 티켓 오픈 순간 queue `join`/`enter` 트래픽이 모두 gateway를 먼저 통과한다. `state` 조회는 CDN/static JSON으로 우회할 수 있어도, `join`과 `enter`는 write성 요청이므로 gateway와 queue server가 모두 스케일아웃 대상이 된다.

gateway를 제거하려면 라우팅만 없애서는 안 된다. core와 queue가 외부 access token을 직접 검증하고, `Passport`를 서비스 내부에서 복원하도록 인증 경계를 옮겨야 한다.

## 목표 아키텍처

```text
Frontend
  -> Core API endpoint
       -> ticket-core

  -> Queue API endpoint
       -> ticket-queue
       -> queue Redis

  -> Queue state endpoint
       -> CDN/static origin
       -> /queue-state/**

  -> WebSocket endpoint
       -> ticket-core websocket
```

예시 도메인:

```text
https://api.oneticket.site        -> ticket-core
https://queue.oneticket.site      -> ticket-queue
https://ws.oneticket.site         -> ticket-core websocket
https://static.oneticket.site     -> queue-state static JSON
```

frontend는 더 이상 단일 `NEXT_PUBLIC_API_BASE_URL`만 사용하지 않는다. core, queue, websocket, queue state base URL을 분리한다.

```text
NEXT_PUBLIC_CORE_API_BASE_URL
NEXT_PUBLIC_QUEUE_API_BASE_URL
NEXT_PUBLIC_WS_BASE_URL
NEXT_PUBLIC_QUEUE_STATE_BASE_URL
```

## 인증 설계

### Access token 검증

gateway가 수행하던 access token 검증을 각 서비스가 직접 수행한다.

- `ticket-core`: 기존 보호 API에서 `Authorization: Bearer <accessToken>`을 직접 검증한다.
- `ticket-queue`: `/api/v1/queue/performances/{performanceId}/join`에서 `Authorization: Bearer <accessToken>`을 직접 검증한다.
- `X-Internal-Auth`는 외부 HTTP 계약에서 제거한다.
- 컨트롤러가 쓰는 `Passport` 값 객체는 유지하되, 생성 주체를 gateway passport token 필터에서 access token 인증 필터로 바꾼다.

공통 모듈에는 access token을 검증해 `Passport`를 복원하는 web adapter를 둔다. 기존 `passport-web`의 argument resolver 패턴은 유지할 수 있다.

```text
Authorization: Bearer accessToken
  -> AccessTokenAuthenticationFilter
  -> JwtTokenVerifier
  -> Passport(memberId, role)
  -> SecurityContext
  -> PassportArgumentResolver
```

### JWT 키 방식

초기 전환은 현재 HS256 secret 공유로도 가능하지만, gateway 제거 후에는 queue도 외부 access token을 검증해야 하므로 운영 구조는 RS256 또는 동등한 공개키 검증 방식으로 전환한다.

권장 계약:

```text
ticket-core auth
  - private key로 access token 발급

ticket-core API / ticket-queue
  - public key로 access token 검증
```

이렇게 하면 queue가 access token을 검증해도 발급 권한은 갖지 않는다. HS256을 유지하면 core와 queue가 같은 서명 secret을 공유하므로 queue secret 유출 시 access token 위조 영향이 커진다.

## Queue API 계약

### Join

```http
POST /api/v1/queue/performances/{performanceId}/join
Authorization: Bearer <accessToken>
```

처리:

1. queue가 access token을 직접 검증한다.
2. `memberId`와 `role`을 `Passport`로 복원한다.
3. `performanceId + memberIdHash` 기준으로 Redis join 중복을 제어한다.
4. `queueToken`을 발급한다.

응답:

```json
{
  "performanceId": 1,
  "queueId": "queue-id",
  "seq": 152300,
  "status": "WAITING",
  "queueToken": "signed-queue-token"
}
```

### Public State

```http
GET /queue-state/performances/{performanceId}.json
```

대기 상태 조회는 queue API가 아니라 CDN/static JSON을 우선 사용한다. fallback/debug용 queue server `/api/v1/queue/performances/{performanceId}/state`는 유지할 수 있지만, 대량 트래픽 경로로 쓰지 않는다.

### Enter

```http
POST /api/v1/queue/performances/{performanceId}/enter
X-Queue-Token: <queueToken>
```

`enter`는 queueToken만으로 처리할 수 있다. 단, queueToken이 특정 회원에게 귀속되도록 claims를 보강한다.

응답:

```json
{
  "status": "ACTIVE",
  "admissionToken": "signed-admission-token",
  "expiresAtMillis": 1717000900000,
  "redirectUrl": "/booking/seat?performanceId=1"
}
```

## Token binding 보강

gateway 제거와 함께 queueToken/admissionToken의 회원 귀속을 명확히 한다.

현재 queueToken은 `performanceId`, `queueId`, `seq` 중심이고 admission token 검증은 core에서 performance 일치만 확인한다. 이 상태에서는 token 탈취 또는 전달 시 다른 회원이 admission token을 사용할 수 있다.

변경 계약:

- `QueueTokenClaims`에 `memberId` 또는 `memberIdHash`를 추가한다.
- queue `join`에서 access token의 `memberId`를 queueToken에 넣는다.
- queue `enter`에서 queueToken을 검증한 뒤 admissionToken에도 `memberId`를 넣는다.
- core의 `AdmissionTokenValidator`는 `performanceId`뿐 아니라 현재 `Passport.memberId()`와 admission token의 memberId도 비교한다.

권장 검증:

```text
AdmissionTokenValidator.validate(performanceId, memberId, admissionToken)
  -> issuer 검증
  -> audience 검증
  -> scope 검증
  -> expiration 검증
  -> performanceId 일치 검증
  -> memberId 일치 검증
```

이 변경 이후 queue admission token은 특정 공연과 특정 회원에 동시에 귀속된다.

## Core API 변경

core는 gateway passport token 대신 외부 access token을 직접 검증한다.

보호 API:

```http
Authorization: Bearer <accessToken>
```

대기열이 필요한 공연의 예매 API:

```http
Authorization: Bearer <accessToken>
X-Admission-Token: <admissionToken>
```

controller는 계속 `Passport`를 인자로 받을 수 있다. 다만 `Passport`는 `X-Internal-Auth`가 아니라 access token 필터에서 생성된다.

기존 refresh token, OAuth2, login, signup 흐름은 core 소유로 유지한다. queue는 refresh token을 처리하지 않는다.

## Frontend 변경

frontend API client를 목적별로 분리한다.

```text
coreApi
  - login
  - refresh
  - shows
  - performances
  - seats
  - orders
  - member

queueApi
  - queue join
  - queue enter
  - fallback queue state

queueStateClient
  - CDN/static JSON 조회

seatSocketClient
  - websocket endpoint 접속
```

access token refresh 정책:

1. core API 요청이 401이면 core refresh를 호출하고 재시도한다.
2. queue join 요청이 401이어도 core refresh를 호출하고 queue join을 재시도한다.
3. queue enter는 queueToken 기반 요청이므로 access token refresh 대상이 아니다.
4. admission token 만료/invalid는 queue enter를 재시도하는 것이 아니라 queue join부터 다시 시작한다.

## CORS와 쿠키

core와 queue가 서로 다른 origin으로 노출되면 CORS 정책도 분리된다.

- core는 frontend origin을 허용하고 refresh cookie를 처리한다.
- queue는 frontend origin을 허용하지만 refresh cookie를 처리하지 않는다.
- queue 요청에는 `Authorization`, `X-Queue-Token` 헤더를 허용한다.
- core 요청에는 `Authorization`, `X-Admission-Token` 헤더를 허용한다.
- exposed headers는 실제 응답 계약에 필요한 값만 둔다.

refresh token cookie는 core 도메인 기준으로 관리한다. queue는 refresh cookie에 의존하지 않는다.

## 스케일링 모델

gateway 제거 후 병목은 서비스별로 분리된다.

```text
join/enter
  -> queue endpoint
  -> ticket-queue replicas
  -> queue Redis

state
  -> CDN/static JSON

seat/order
  -> core endpoint
  -> ticket-core replicas
  -> core DB/Redis

websocket
  -> websocket endpoint
  -> ticket-core websocket capacity
```

티켓 오픈 순간 우선 확장 대상:

1. CDN/static queue state origin과 cache policy
2. `ticket-queue` replicas
3. queue Redis 처리량과 Lua script latency
4. `ticket-core` seat/order 처리량
5. websocket fanout 처리량

gateway replicas는 더 이상 확장 대상이 아니다.

## 마이그레이션 단계

### 1단계: 공통 access token web adapter 추가

`ticket-common`에 access token을 검증해 `Passport`를 복원하는 web adapter를 추가한다. 기존 `PassportArgumentResolver`를 재사용하고, `X-Internal-Auth` 전용 필터와 외부 access token 필터를 분리한다.

### 2단계: core 직접 JWT 검증 전환

core security filter chain에서 gateway internal auth 검증 대신 access token 검증 필터를 사용한다. public/protected path 정책은 기존 core security 설정을 기준으로 유지한다.

### 3단계: queue 직접 JWT 검증 전환

queue에 access token 검증 필터와 최소 security 설정을 추가한다.

- `POST /join`: authenticated
- `GET /state`: public fallback/debug
- `POST /enter`: queueToken 기반 처리
- actuator health/info: public 또는 운영 정책에 맞게 제한

### 4단계: token member binding 적용

queueToken과 admissionToken에 member binding을 추가한다. core admission 검증도 `memberId` 일치까지 확인하도록 바꾼다.

### 5단계: frontend endpoint 분리

frontend 환경변수와 API client를 core/queue/state/ws로 분리한다. queue join의 401 refresh 재시도는 core refresh를 사용한다.

### 6단계: 병행 배포

gateway 경로와 직접 경로를 짧은 기간 병행한다. 직접 경로에서 core, queue, frontend, load test가 통과하면 gateway 경로 트래픽을 중단한다.

### 7단계: gateway 제거

`ticket-gateway` 배포, 설정, 문서, 로컬 실행 절차, 부하 테스트 base URL에서 gateway 의존을 제거한다.

## 테스트 전략

### 단위 테스트

- access token 필터가 정상 token을 `Passport`로 복원한다.
- 만료/위조 token은 401로 처리한다.
- queueToken claims에 member binding이 포함된다.
- admissionToken 검증은 performanceId와 memberId mismatch를 거부한다.

### 계약 테스트

- core 보호 API는 `Authorization`만으로 인증된다.
- `X-Internal-Auth`만 보낸 요청은 인증되지 않는다.
- queue join은 access token이 없으면 401을 반환한다.
- queue enter는 queueToken이 없거나 위조되면 401/403 계열로 거부한다.
- queue state static JSON 경로는 Spring Boot API를 타지 않는다.

### 통합 테스트

- login -> queue join -> public state 확인 -> queue enter -> seat select/order 전체 흐름을 검증한다.
- admission token을 다른 회원 access token과 조합하면 core에서 거부한다.
- access token 만료 후 queue join 401 -> core refresh -> queue join 재시도가 동작한다.

### 부하 테스트

- queue join 단독 부하
- queue enter 단독 부하
- CDN/static state 조회 부하
- core seat/order capacity
- full ticket open flow

부하 테스트의 `baseUrl`은 더 이상 gateway 하나가 아니라 core/queue/state endpoint를 각각 받도록 조정한다.

## 운영 리스크와 대응

### JWT 키 배포

queue가 access token을 검증해야 하므로 public key 배포가 필요하다. HS256 공유 secret으로 시작할 수는 있지만 운영 전환 목표는 RS256 공개키 검증이다.

### CORS 복잡도 증가

단일 gateway origin에서 여러 API origin으로 분리되므로 CORS 설정이 늘어난다. core/queue 각각 허용 origin, 허용 header, credential 정책을 명확히 둔다.

### token 재사용 위험

gateway 제거 자체보다 token binding 누락이 더 큰 보안 리스크다. queueToken과 admissionToken을 회원에 귀속시키고 core에서 memberId까지 검증한다.

### 배포 순서 위험

frontend가 새 endpoint로 전환되기 전에 backend 직접 인증이 준비되어야 한다. 병행 배포 기간에는 gateway 경로와 직접 경로가 모두 통과해야 한다.

## 완료 기준

- `ticket-gateway` 없이 frontend 주요 흐름이 동작한다.
- core 보호 API가 외부 access token을 직접 검증한다.
- queue join이 외부 access token을 직접 검증한다.
- `X-Internal-Auth`는 외부 API 계약에서 제거된다.
- queueToken과 admissionToken은 회원에 귀속된다.
- queue state 조회는 CDN/static JSON 경로를 사용한다.
- gateway를 통하지 않는 full ticket open flow 부하 테스트가 통과한다.
