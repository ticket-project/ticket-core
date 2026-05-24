# 예매 오픈 로컬 부하 테스트

기준일: 2026-05-24

이 문서는 분리된 대기열 구조 기준이다. 사용자는 Gateway 하나만 바라보고, Gateway가 `/api/v1/queue/**`는 Queue Server로, `/api/**`와 `/ws/**`는 Ticket Server로 라우팅한다.

## 목적

로컬 환경에서 예매 오픈 순간의 핵심 위험을 재현한다.

- Ticket Server의 `booking-entry` DIRECT/QUEUE 판단
- Queue Server의 enter/status polling 처리량
- ACTIVE 이후 admission token 기반 Ticket Server 예매 API 진입
- Queue Server를 우회한 Ticket Server 단독 좌석/주문 처리량
- 같은 좌석 hold/order 경합

운영 환경에는 직접 부하 테스트를 실행하지 않는다. 운영과 가까운 처리량은 별도 스테이징 환경에서 같은 시나리오를 재사용해 확인한다.

## 전제

- Ticket Server가 `8080`에서 실행 중이다.
- Queue Server가 `8090`에서 실행 중이다.
- Gateway가 `8000`에서 실행 중이다.
- Frontend 없이 API 기준으로 테스트할 때도 `baseUrl`은 Gateway인 `http://localhost:8000`을 사용한다.
- Ticket Server Redis와 Queue Redis는 분리한다. 로컬에서는 포트를 다르게 띄우거나 환경 변수로 명시한다.
- Queue Server와 Ticket Server는 같은 access token 검증 secret과 admission token secret을 공유한다.
- `performanceId`, `seatIds`는 로컬 seed 데이터 기준으로 실제 존재하는 값을 사용한다.

## 로컬 실행

Ticket Redis 예시:

```powershell
docker run --name ticket-redis -p 6379:6379 -d redis:7
```

Queue Redis 예시:

```powershell
docker run --name ticket-queue-redis -p 6380:6379 -d redis:7
```

Ticket Server:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket
$env:SPRING_PROFILES_ACTIVE="local"
$env:ADMISSION_TOKEN_SECRET_KEY="same-admission-secret-32bytes-minimum"
.\gradlew.bat :core:core-api:bootRun
```

Queue Server:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-queue
$env:QUEUE_REDIS_PORT="6380"
$env:JWT_SECRET_KEY="same-access-token-secret-used-by-ticket"
$env:ADMISSION_TOKEN_SECRET_KEY="same-admission-secret-32bytes-minimum"
.\gradlew.bat bootRun
```

Gateway:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-gateway
$env:GATEWAY_PORT="8000"
$env:TICKET_API_URI="http://localhost:8080"
$env:TICKET_API_WS_URI="http://localhost:8080"
$env:TICKET_QUEUE_URI="http://localhost:8090"
.\gradlew.bat bootRun
```

## 테스트 토큰 준비

로컬 seed가 켜져 있으면 `SeedDataLoader`가 테스트 회원을 생성한다.

```text
email: loadtest1@test.com ~ loadtest100@test.com
password: password1234
```

Gatling Simulation은 기본적으로 시작 전에 `users` 수만큼 seed 회원으로 로그인해서 access token을 준비한다. 이 로그인 요청은 성능 측정 대상에서 제외한다.

직접 토큰을 쓰는 경우:

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketOpenFlowSimulation `
  -DbaseUrl=http://localhost:8000 `
  -DaccessTokenMode=tokens `
  -DaccessTokens=jwt1,jwt2
```

## 현재 API 흐름

```text
1. POST /api/v1/auth/login
   -> accessToken 발급

2. GET /api/v1/performances/{performanceId}/booking-entry
   -> DIRECT 또는 QUEUE

3-A. DIRECT
   -> Ticket Server 좌석/hold/order API 호출
   -> Authorization 헤더만 필요

3-B. QUEUE
   -> POST /api/v1/queue/performances/{performanceId}/enter
      Authorization: Bearer {accessToken}
   -> queueSessionId 저장
   -> GET /api/v1/queue/performances/{performanceId}/status
      X-Queue-Session: {queueSessionId}
   -> ACTIVE 응답에서 admissionToken, redirectUrl 수신
   -> Ticket Server 좌석/hold/order API 호출
      Authorization + X-Admission-Token 전달
```

이 구조에서는 `queueToken`과 `X-Queue-Token`을 사용하지 않는다.

## Gatling 실행

Gatling은 루트 Gradle wrapper로 `load-tests/gatling` 독립 프로젝트를 실행한다.

공통 옵션:

- `-DbaseUrl`: Gateway 주소, 기본값은 `http://localhost:8000`을 권장한다.
- `-DperformanceId`: 회차 ID, 기본값 `1`
- `-DseatIds`: 홀드할 좌석 ID 목록, 기본값 `1`
- `-DaccessTokenMode=tokens`: `accessTokens` 값을 사용한다.
- `-DaccessTokens`: 쉼표로 구분한 JWT 목록. `accessTokenMode=tokens`일 때 필수다.
- `-DadmissionTokenMode=synthetic`: Gatling이 access token의 `sub`와 `performanceId`로 admission token을 생성한다.
- `-DadmissionTokenMode=tokens`: `admissionTokens` 값을 사용한다.
- `-DadmissionTokens`: 쉼표로 구분한 admission token 목록. `admissionTokenMode=tokens`일 때 필수다.
- `-DadmissionTokenIssuer`: admission token issuer, 기본값 `ticket-queue`
- `-DadmissionTokenAudience`: admission token audience, 기본값 `ticket-api`
- `-DadmissionTokenSecret`: Ticket Server의 `ADMISSION_TOKEN_SECRET_KEY`와 같은 값
- `-DadmissionTokenTtlSeconds`: 합성 admission token TTL, 기본값 `300`
- `-Dusers`: 가상 사용자 수, 기본값 `10`
- `-DdurationSeconds`: 사용자를 투입할 시간, 기본값 `10`
- `-DstatusPolls`: Queue status polling 최대 횟수
- `-DstatusPollPauseSeconds`: Queue status polling 사이 대기 시간

### 1. Queue enter 부하

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.QueueEnterSimulation `
  -DbaseUrl=http://localhost:8000 `
  -DperformanceId=1 `
  -Dusers=10 `
  -DdurationSeconds=10
```

확인할 것:

- `enter queue` 실패율
- 응답 시간 p95, p99
- Queue Server 로그의 예외 여부

### 2. 티켓 서버 단독 용량 측정

대기열 스케줄러 방출량 산정의 기준 테스트다. Queue Server를 거치지 않고 Ticket Server에 `Authorization`과 `X-Admission-Token`을 직접 붙여 보호 API 처리량을 측정한다.

전제:

- Ticket Server가 `http://localhost:8080`에서 실행 중이어야 한다.
- `ADMISSION_TOKEN_SECRET_KEY`는 Gatling의 `-DadmissionTokenSecret`과 같아야 한다.
- 주문 생성까지 측정하려면 `loadtestN@test.com` seed 회원이 DB에 존재해야 한다.
- `seatIds`는 가능한 한 사용자 수 이상으로 충분히 준비한다. 좌석 수가 부족하면 409/422가 늘어나 처리량 해석이 왜곡된다.

Smoke:

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketServerCapacitySimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DseatIds=1,2,3,4,5 `
  -Dusers=5 `
  -DdurationSeconds=10 `
  -DaccessTokenMode=login `
  -DadmissionTokenMode=synthetic `
  -DadmissionTokenSecret=same-admission-secret-32bytes-minimum
```

단계 부하:

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketServerCapacitySimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DseatIds=1,2,3,4,5,6,7,8,9,10 `
  -DinjectionMode=constant-users-per-sec `
  -DusersPerSecond=10 `
  -DdurationSeconds=60 `
  -DaccessTokenMode=login `
  -DadmissionTokenMode=synthetic `
  -DadmissionTokenSecret=same-admission-secret-32bytes-minimum
```

확인할 것:

- `seat status`, `select seat`, `create order` 요청의 실패율
- p95/p99 응답 시간
- `500` 응답 여부
- DB connection pool active/pending
- Ticket Redis latency와 CPU
- JVM CPU, heap, GC pause

안정 TPS는 실패율과 p99가 급격히 나빠지기 직전 단계가 아니라, 그보다 한 단계 낮은 구간을 기준으로 잡는다. 운영 방출 기준은 보통 실측 안정값의 60~70%로 둔다.

### 3. 예매 오픈 흐름

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketOpenFlowSimulation `
  -DbaseUrl=http://localhost:8000 `
  -DperformanceId=1 `
  -DseatIds=1 `
  -Dusers=10 `
  -DdurationSeconds=10 `
  -DstatusPolls=3 `
  -DstatusPollPauseSeconds=1
```

흐름:

1. 로그인한다.
2. Queue Server `enter`를 호출한다.
3. 즉시 ACTIVE가 아니면 `queueSessionId`를 `X-Queue-Session`으로 보내 status polling을 수행한다.
4. ACTIVE면 `admissionToken`을 저장하고 Ticket Server 좌석 상태 조회와 주문 생성을 시도한다.
5. 같은 좌석이면 성공은 제한된 좌석 수만큼만 나와야 하고, 나머지는 `400`, `409`, `422` 계열로 거부되어야 한다.

### 4. Admission Token 직접 입력

Queue Server에서 admission token을 직접 확보해 쓰려면 `admissionTokenMode=tokens`를 사용한다. access token과 admission token은 같은 회원, 같은 `performanceId`여야 한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.HoldRaceSimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DseatIds=1 `
  -DaccessTokenMode=tokens `
  -DaccessTokens=$accessTokenList `
  -DadmissionTokenMode=tokens `
  -DadmissionTokens=$admissionTokenList `
  -Dusers=10 `
  -DdurationSeconds=10
```

## 추천 증가 순서

```text
users=5, durationSeconds=10
users=10, durationSeconds=10
users=30, durationSeconds=20
users=50, durationSeconds=30
```

티켓 서버 용량 측정은 아래처럼 `constant-users-per-sec`를 우선 사용한다.

```text
usersPerSecond=5, durationSeconds=60
usersPerSecond=10, durationSeconds=60
usersPerSecond=20, durationSeconds=60
usersPerSecond=30, durationSeconds=60
```

`constant-users-per-sec`에서 실제 가상 사용자 수는 `usersPerSecond * durationSeconds`다. 자동 로그인 모드라면 이 수만큼 seed 회원이 필요하다. 예를 들어 `10/sec * 60초`는 600명의 `loadtest` 회원이 필요하다.

로컬에서 `100+` 사용자를 넣으면 서버와 부하 발생기가 같은 PC 자원을 공유하므로 응답 시간은 참고값으로만 본다.

## 성공 기준

- queue enter/status 요청의 실패율이 기준치를 넘지 않는다.
- Ticket Server 단독 용량 테스트에서 `seat status`, `select seat`, `create order`의 실패율과 p99가 기준치를 넘지 않는다.
- QUEUE 회차에서 admission token 없이 Ticket Server 보호 API를 호출하면 거부된다.
- QUEUE 회차에서 정상 access token과 admission token이 있으면 보호 API에 진입한다.
- DIRECT 회차에서 admission token 없이 보호 API에 진입한다.
- 같은 좌석에 다수 사용자가 몰릴 때 성공한 hold/order는 제한된 좌석 수를 넘지 않는다.
- Gatling 리포트에서 `500` 응답이 나오면 실패로 본다.

## 대기열 스케줄러 방출량 계산

티켓 서버 용량 측정 후 아래처럼 계산한다.

```text
ticket_safe_admit_per_sec = 안정 구간 입장 사용자 수/sec
queue_admit_per_sec = floor(ticket_safe_admit_per_sec * 0.6 ~ 0.7)
queue_admit_per_tick = floor(queue_admit_per_sec * advanceIntervalSeconds)
max_active_users = queue_admit_per_sec * 사용자가 좌석/주문 화면에 머무는 평균 초
```

현재 Queue Server 스케줄러는 `maxActiveUsers`까지 active를 채우는 방식이다. 첫 tick에서 과도하게 승격될 수 있으므로 운영 기준으로는 `queue_admit_per_tick`에 해당하는 batch 제한을 추가한 뒤 E2E 부하 테스트로 확인한다.

## 리포트 위치

```text
load-tests/gatling/build/reports/gatling
```

## 스테이징 재사용

스테이징에서는 Simulation은 그대로 쓰고 아래 값만 바꾼다.

- `baseUrl`
- `performanceId`
- `seatIds`
- `accessTokens`
- `users`
- `durationSeconds`

실제 운영 URL은 사용하지 않는다.
