# 예매 오픈 로컬 부하 테스트

기준일: 2026-06-19

이 문서는 Gateway 제거 후 구조 기준이다. 부하 테스트는 목적에 따라 Ticket Server와 Queue Server를 직접 호출한다. Queue 흐름은 `join -> public state polling -> enter -> admission token -> Ticket Server 보호 API` 순서다.

## 목적

로컬 환경에서 예매 오픈 시간의 핵심 위험을 재현한다.

- Ticket Server의 admission token 기반 좌석/주문 처리량
- Queue Server의 join/enter 처리량
- Queue public state polling과 admitted sequence 기준 입장
- 같은 좌석 hold/order 경합

운영 환경에 직접 부하를 주지 않는다. 운영과 가까운 처리량은 별도 스테이징 환경에서 같은 시나리오로 확인한다.

## 전제

- Ticket Server가 `8080`에서 실행 중이다.
- Queue Server가 `8090`에서 실행 중이다.
- Gateway는 실행하지 않는다.
- Ticket Server Redis와 Queue Redis는 분리한다.
- Ticket Server와 Queue Server는 같은 access token 검증 secret을 사용한다.
- Ticket Server와 Queue Server는 같은 admission token secret을 사용한다.
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
$env:JWT_SECRET="same-access-token-secret-32bytes-minimum"
$env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
$env:JWT_REFRESH_TOKEN_EXPIRATION_SECONDS="1209600"
$env:ADMISSION_TOKEN_SECRET_KEY="same-admission-secret-32bytes-minimum"
.\gradlew.bat :core:core-api:bootRun
```

Queue Server:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-queue
$env:SPRING_DATA_REDIS_PORT="6380"
$env:JWT_SECRET="same-access-token-secret-32bytes-minimum"
$env:JWT_ISSUER="ticket"
$env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
$env:ADMISSION_TOKEN_SECRET_KEY="same-admission-secret-32bytes-minimum"
$env:QUEUE_TOKEN_SECRET="same-queue-token-secret-32bytes-minimum"
.\gradlew.bat bootRun
```

## API 흐름

```text
1. POST /api/v1/auth/login
   -> accessToken 발급

2. Queue 회차
   POST http://localhost:8090/api/v1/queue/performances/{performanceId}/join
   Authorization: Bearer {accessToken}
   -> seq, queueToken 저장

3. Queue public state polling
   GET http://localhost:8090/api/v1/queue/performances/{performanceId}/state
   -> admittedUntilSeq >= seq 이면 enter 가능

4. Queue enter
   POST http://localhost:8090/api/v1/queue/performances/{performanceId}/enter
   X-Queue-Token: {queueToken}
   -> admissionToken 수신

5. Ticket Server 보호 API
   Authorization: Bearer {accessToken}
   X-Admission-Token: {admissionToken}
```

`X-Queue-Session`과 `/status` polling API는 사용하지 않는다.

## Gatling 실행

Gatling은 sibling 저장소 `ticket-gatling-load-tests`의 `load-tests/gatling` 독립 프로젝트를 실행한다.

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-gatling-load-tests
```

공통 옵션:

- `-DbaseUrl`: 단일 서버만 호출하는 기존 시나리오용 기본 주소. 기본값 `http://localhost:8080`
- `-DcoreBaseUrl`: Ticket Server 주소. 지정하지 않으면 `baseUrl`을 사용한다.
- `-DqueueBaseUrl`: Queue Server 주소. 지정하지 않으면 `baseUrl`을 사용한다.
- `-DperformanceId`: 회차 ID, 기본값 `1`
- `-DseatIds`: 홀드할 좌석 ID 목록, 기본값 `1`
- `-DaccessTokenMode=login`: seed 회원으로 로그인해 access token을 받는다.
- `-DaccessTokenMode=tokens`: `accessTokens` 값을 사용한다.
- `-DaccessTokenMode=synthetic-jwt`: `jwtSecret`으로 synthetic access token을 만든다.
- `-DaccessTokens`: 쉼표로 구분한 JWT 목록. `accessTokenMode=tokens`일 때 필수다.
- `-DjwtSecret`: synthetic access token 서명 secret. Queue Server/Ticket Server의 `JWT_SECRET`과 같아야 한다.
- `-DadmissionTokenMode=synthetic`: Gatling이 access token의 memberId와 `performanceId`로 admission token을 만든다.
- `-DadmissionTokenMode=tokens`: `admissionTokens` 값을 사용한다.
- `-DadmissionTokens`: 쉼표로 구분한 admission token 목록. `admissionTokenMode=tokens`일 때 필수다.
- `-DadmissionTokenIssuer`: admission token issuer, 기본값 `ticket-queue`
- `-DadmissionTokenAudience`: admission token audience, 기본값 `ticket-api`
- `-DadmissionTokenSecret`: Ticket Server의 `ADMISSION_TOKEN_SECRET_KEY`와 같은 값
- `-DadmissionTokenTtlSeconds`: synthetic admission token TTL, 기본값 `300`
- `-Dusers`: 가상 사용자 수, 기본값 `10`
- `-DdurationSeconds`: 사용자를 투입할 시간, 기본값 `10`
- `-DstatusPolls`: Queue public state polling 최대 횟수
- `-DstatusPollPauseSeconds`: Queue public state polling 사이 대기 시간

### 1. Queue enter 부하

`QueueEnterSimulation`은 각 사용자별로 `join`을 먼저 호출해 `queueToken`을 확보한 뒤 `enter`를 호출한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.QueueEnterSimulation `
  -DqueueBaseUrl=http://localhost:8090 `
  -DperformanceId=1 `
  -Dusers=10 `
  -DdurationSeconds=10 `
  -DaccessTokenMode=synthetic-jwt `
  -DjwtSecret=same-access-token-secret-32bytes-minimum
```

확인할 것:

- `queue join`, `queue enter` 실패율
- `queue enter` 응답 시간 p95, p99
- Queue Server 예외 로그 여부

### 2. Ticket Server 단독 용량

Queue Server를 거치지 않고 Ticket Server에 `Authorization`과 `X-Admission-Token`을 직접 붙여 보호 API 처리량을 측정한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketServerCapacitySimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DseatIds=1,2,3,4,5 `
  -Dusers=5 `
  -DdurationSeconds=10 `
  -DaccessTokenMode=synthetic-jwt `
  -DjwtSecret=same-access-token-secret-32bytes-minimum `
  -DadmissionTokenMode=synthetic `
  -DadmissionTokenSecret=same-admission-secret-32bytes-minimum
```

확인할 것:

- `seat status`, `select seat`, `create order` 실패율
- p95/p99 응답 시간
- `500` 응답 여부
- DB connection pool active/pending
- Ticket Redis latency와 CPU

### 3. 예매 오픈 전체 흐름

`TicketOpenFlowSimulation`은 Queue Server에서 `join` 후 public state를 polling하고, admitted sequence에 도달하면 `X-Queue-Token`으로 `enter`를 호출한다. `admissionToken`을 받은 사용자만 Ticket Server 좌석 상태 조회와 주문 생성을 시도한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketOpenFlowSimulation `
  -DcoreBaseUrl=http://localhost:8080 `
  -DqueueBaseUrl=http://localhost:8090 `
  -DperformanceId=1 `
  -DseatIds=1 `
  -Dusers=10 `
  -DdurationSeconds=10 `
  -DstatusPolls=3 `
  -DstatusPollPauseSeconds=1 `
  -DaccessTokenMode=synthetic-jwt `
  -DjwtSecret=same-access-token-secret-32bytes-minimum
```

주의: 이 시나리오는 `coreBaseUrl`과 `queueBaseUrl`을 분리해 사용한다. 둘 중 하나를 생략하면 해당 값은 `baseUrl`로 대체된다.

### 4. Admission Token 직접 입력

Ticket Server 단독 테스트에 Queue Server가 발급한 admission token을 직접 넣으려면 `admissionTokenMode=tokens`를 사용한다. admission token은 요청 대상과 같은 `performanceId`여야 하며, subject는 access token의 회원과 일치해야 한다.

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

## 완료 기준

- Queue 시나리오에서 `X-Queue-Session`과 `/status` polling을 사용하지 않는다.
- Queue join/enter 요청의 실패율이 기준치를 넘지 않는다.
- Ticket Server 단독 용량 테스트에서 `seat status`, `select seat`, `create order`의 실패율과 p99가 기준치를 넘지 않는다.
- QUEUE 회차에서 admission token 없이 Ticket Server 보호 API를 호출하면 거부된다.
- QUEUE 회차에서 정상 access token과 같은 memberId에 바인딩된 admission token이 있으면 보호 API에 진입한다.
- DIRECT 회차에서 admission token 없이 보호 API에 진입한다.
- 같은 좌석에 다수 사용자가 몰릴 때 성공한 hold/order는 제한된 좌석 수를 넘지 않는다.

## 리포트 위치

Gatling 실행 후 아래 경로에 HTML 리포트가 생성된다.

```text
load-tests/gatling/build/reports/gatling
```
