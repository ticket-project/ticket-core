# 예매 오픈 로컬 부하 테스트

기준일: 2026-07-30

이 문서는 Gateway 제거 후 구조 기준이다. 부하 테스트는 목적에 따라 Ticket Server와 Queue Server를 직접 호출한다. Queue 흐름은 `join -> public state polling -> enter -> admission token -> Ticket Server 보호 API` 순서다.

현재 Gatling 소스와 상세 옵션의 기준은 형제 저장소의 `../../../gatling-test/README.md`와
`../../../gatling-test/console/README.md`다.

## 목적

로컬 환경에서 예매 오픈 시간의 핵심 위험을 재현한다.

- Ticket Server의 admission token 기반 좌석/주문 처리량
- Queue Server의 join/enter 처리량
- Queue public state polling과 shard별 serving sequence 기준 입장
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
# ticket 저장소 루트에서 실행
$env:SPRING_PROFILES_ACTIVE="local"
$env:JWT_SECRET="same-access-token-secret-32bytes-minimum"
$env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
$env:JWT_REFRESH_TOKEN_EXPIRATION_SECONDS="1209600"
$env:ADMISSION_TOKEN_SECRET_KEY="same-admission-secret-32bytes-minimum"
$env:GOOGLE_CLIENT_ID="local-google-client-id"
$env:GOOGLE_CLIENT_SECRET="local-google-client-secret"
$env:KAKAO_CLIENT_ID="local-kakao-client-id"
$env:KAKAO_CLIENT_SECRET="local-kakao-client-secret"
$env:KAKAO_ADMIN_KEY="local-kakao-admin-key"
.\gradlew.bat :core:core-api:bootRun
```

Queue API:

```powershell
# ticket-queue 저장소 루트의 별도 터미널에서 실행
$env:REDIS_PORT="6380"
$env:JWT_SECRET="same-access-token-secret-32bytes-minimum"
$env:JWT_ISSUER="ticket"
$env:JWT_ACCESS_TOKEN_EXPIRATION_SECONDS="1800"
$env:ADMISSION_TOKEN_SECRET_KEY="same-admission-secret-32bytes-minimum"
$env:QUEUE_TOKEN_SECRET="same-queue-token-secret-32bytes-minimum"
.\gradlew.bat :queue-api:bootRun
```

Queue Scheduler:

```powershell
# ticket-queue 저장소 루트의 또 다른 터미널에서 실행
$env:REDIS_PORT="6380"
.\gradlew.bat :queue-scheduler:bootRun
```

## API 흐름

```text
1. POST /api/v1/auth/login
   -> accessToken 발급

2. Queue 회차
   POST http://localhost:8090/api/v1/queue/performances/{performanceId}/join
   Authorization: Bearer {accessToken}
   -> shardId, localSeq, queueToken 저장

3. Queue public state polling
   GET http://localhost:8090/api/v1/queue/performances/{performanceId}/state
   -> serving[shardId] >= localSeq 이면 enter 가능

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

Gatling은 형제 `gatling-test` 저장소의 Gradle wrapper와 `load-tests/gatling` 프로젝트를 사용한다.

아래 명령은 `gatling-test` 저장소 루트에서 실행한다. 실행 전 대상 URL, 사용자 수, 전용 `performanceId`, feeder를 확인한다.

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

Queue Server를 거치지 않고 Ticket Server에 `Authorization`과 `X-Admission-Token`을 직접 붙여 보호 API 처리량을 측정한다. 회원·좌석·토큰 조합은 현재 표준인 booking feeder CSV로 공급한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.CoreAdmissionCapacitySimulation `
  -DcoreBaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DbookingFeederFile=C:\path\booking-feeder.csv `
  -DbookingScenario=CORE_ADMISSION_CAPACITY `
  -DinjectionMode=constant-users-per-sec `
  -DusersPerSecond=10 `
  -DdurationSeconds=60 `
  -DresultFile=build\reports\core-capacity-local.csv
```

확인할 것:

- `seat status`, `select seat`, `create order` 실패율
- p95/p99 응답 시간
- `500` 응답 여부
- DB connection pool active/pending
- Ticket Redis latency와 CPU

### 3. 예매 오픈 전체 흐름

`TicketOpenEndToEndSimulation`은 Queue Server에서 `join` 후 public state를 polling하고, 자신의 shard에서 serving sequence에 도달하면 `X-Queue-Token`으로 `enter`를 호출한다. `admissionToken`을 받은 사용자만 Ticket Server 좌석 상태 조회와 주문 생성을 시도한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketOpenEndToEndSimulation `
  -DcoreBaseUrl=http://localhost:8080 `
  -DqueueBaseUrl=http://localhost:8090 `
  -DperformanceId=1 `
  -DbookingFeederFile=C:\path\booking-feeder.csv `
  -DbookingScenario=TICKET_OPEN_END_TO_END `
  -DinjectionMode=constant-users-per-sec `
  -DusersPerSecond=10 `
  -DdurationSeconds=60 `
  -DstatusPolls=3 `
  -DstatusPollPauseSeconds=1 `
  -DresultFile=build\reports\ticket-open-local.csv
```

주의: 이 시나리오는 `coreBaseUrl`과 `queueBaseUrl`을 분리해 사용한다. 둘 중 하나를 생략하면 해당 값은 `baseUrl`로 대체된다.

### 4. 좌석 경합

같은 좌석에 여러 회원을 집중시켜 hold/order 정합성을 확인한다. feeder의 여러 행에 같은 `seatId`를 넣고, 각 행에는 서로 다른 회원의 access token과 그 회원·공연에 바인딩된 admission token을 넣는다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.SeatContentionSimulation `
  -DcoreBaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DbookingFeederFile=C:\path\seat-contention-feeder.csv `
  -DbookingScenario=SEAT_CONTENTION `
  -DinjectionMode=at-once-users `
  -Dusers=10 `
  -DdurationSeconds=10 `
  -DresultFile=build\reports\seat-contention-local.csv
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
