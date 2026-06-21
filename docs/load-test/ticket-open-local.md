# 예매 오픈 로컬 부하 테스트

## 목적

로컬 환경에서 예매 오픈 순간의 핵심 위험을 Gatling Java DSL로 재현한다.

- 대기열 진입 처리량
- 대기열 토큰 발급과 상태 조회 흐름
- 같은 좌석 홀드 경합

운영 환경에는 직접 부하 테스트를 실행하지 않는다. 운영과 가까운 처리량은 별도 스테이징 환경에서 같은 Gatling Simulation을 재사용해 확인한다.

## 전제

- Redis가 실행 중이어야 한다.
- API 서버가 로컬 프로파일로 실행 중이어야 한다.
- 테스트용 회원 JWT가 준비되어야 한다.
- `performanceId`와 `seatIds`는 로컬 seed 데이터 기준으로 실제 존재하는 값을 사용해야 한다.
- 여러 사용자를 흉내 내려면 `accessTokens`에 서로 다른 회원의 JWT를 쉼표로 넣는다.

## 실행 순서

Redis와 Datadog Agent 실행:

`docker-compose.yml`은 로컬 전용 파일로 Git에서 무시한다. `datadog-agent.environment.DD_API_KEY`에 로컬에서 사용할 Datadog API key 값을 직접 넣는다.

```yaml
services:
  redis:
    image: redis:7
    ports:
      - "6379:6379"

  datadog-agent:
    environment:
      DD_API_KEY: "새로_발급한_Datadog_API_Key"
      DD_SITE: "us5.datadoghq.com"
      DD_APM_ENABLED: "true"
      DD_APM_NON_LOCAL_TRAFFIC: "true"
      DD_DOGSTATSD_NON_LOCAL_TRAFFIC: "true"
    ports:
      - "8126:8126"
      - "8125:8125/udp"
```

그 다음 Redis와 Datadog Agent를 함께 실행한다.

```powershell
docker compose up -d
```

IntelliJ에서 `CoreApiApplication` 실행 설정의 VM options에는 아래 값을 추가한다.

```text
-javaagent:C:\tmp\datadog\dd-java-agent.jar
-Ddd.service=ticket-be
-Ddd.env=local
-Ddd.version=local
-Ddd.trace.agent.url=http://localhost:8126
-Ddd.runtime.metrics.enabled=true
-Ddd.jmxfetch.statsd.host=localhost
-Ddd.jmxfetch.statsd.port=8125
-Ddd.logs.injection=true
-Ddd.trace.sample.rate=1
```

`JVM Metrics` 대시보드는 `jvm.*` 메트릭을 조회한다. trace는 `8126/tcp`로 들어가지만 JVM 메트릭은 Java tracer의 JMXFetch가 DogStatsD `8125/udp`로 보낸다. 따라서 Datadog Agent 컨테이너는 `8125/udp`를 열고, 애플리케이션 JVM은 DogStatsD host/port를 현재 실행 환경에 맞게 지정해야 한다.

애플리케이션을 호스트 Windows에서 실행하면 위처럼 `localhost:8125`를 사용한다. 애플리케이션도 Docker Compose 내부 컨테이너로 실행하면 `localhost`가 앱 컨테이너 자신을 가리키므로 `-Ddd.jmxfetch.statsd.host=datadog-agent`처럼 같은 compose 네트워크의 Agent 서비스명을 사용한다.

`dd-java-agent.jar`가 없으면 먼저 다운로드한다.

```powershell
New-Item -ItemType Directory -Force C:\tmp\datadog | Out-Null
curl.exe -L -o C:\tmp\datadog\dd-java-agent.jar https://dtdg.co/latest-java-tracer
```

API 실행:

```powershell
.\gradlew.bat :core:core-api:bootRun
```

정합성 테스트:

```powershell
.\gradlew.bat :core:core-api:test --tests "com.ticket.core.config.seed.SeedDataLoaderTest"
.\gradlew.bat :core:core-api:test --tests "com.ticket.core.api.controller.HoldControllerContractTest" --tests "com.ticket.core.api.controller.PerformanceControllerContractTest"
```

전체 테스트:

```powershell
.\gradlew.bat test
```

Gatling 프로젝트 컴파일/단위 테스트:

```powershell
cd C:\Users\mn040\IdeaProjects\ticket-workspace\ticket-gatling-load-tests
.\gradlew.bat -p load-tests/gatling test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 테스트 토큰 준비

로컬 seed가 켜져 있으면 `SeedDataLoader`가 아래 테스트 회원을 자동 생성한다.

```text
email: loadtest1@test.com ~ loadtest100@test.com
password: password1234
```

이미 API 서버가 켜져 있었다면 재시작해야 seed 회원이 생성된다.

Gatling Simulation은 기본적으로 시작 전에 `users` 수만큼 seed 회원으로 로그인해서 access token을 메모리에 준비한다. 이 로그인 요청은 Gatling DSL 요청이 아니므로 HTML 리포트의 성능 측정에는 포함되지 않는다.

예를 들어 `-Dusers=10`이면 `loadtest1@test.com`부터 `loadtest10@test.com`까지 로그인한 뒤 각 가상 사용자에게 서로 다른 토큰을 배정한다.

필요하면 seed 회원 수와 비밀번호는 로컬 설정에서 바꿀 수 있다.

```yaml
app:
  seed:
    load-test-members:
      enabled: true
      count: 100
      password: password1234
```

토큰 자동 발급 관련 옵션:

- `-DloginEmailPrefix`: 테스트 회원 이메일 prefix, 기본값 `loadtest`
- `-DloginEmailDomain`: 테스트 회원 이메일 domain, 기본값 `test.com`
- `-DloginPassword`: 테스트 회원 비밀번호, 기본값 `password1234`
- `-DloginStartIndex`: 첫 테스트 회원 번호, 기본값 `1`

이미 발급한 토큰을 직접 쓰고 싶으면 `-DaccessTokens=jwt1,jwt2`를 넘길 수 있다. 이 값이 있으면 자동 로그인은 실행하지 않는다.

직접 발급한 토큰을 쓰려면 실수 방지를 위해 토큰 모드를 명시한다.

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketOpenFlowSimulation `
  -DaccessTokenMode=tokens `
  -DaccessTokens=jwt1,jwt2
```

기본 모드에서는 `accessTokens` system property가 있어도 무시하고 seed 회원으로 다시 로그인한다.

## Gatling 실행

Gatling은 별도 설치하지 않는다. sibling 저장소 `ticket-gatling-load-tests`의 Gradle wrapper로 `load-tests/gatling` 독립 프로젝트를 실행한다.

공통 옵션:

- `-DbaseUrl`: API 주소, 기본값 `http://localhost:8080`
- `-DperformanceId`: 회차 ID, 기본값 `1`
- `-DseatIds`: 홀드할 좌석 ID 목록, 기본값 `1`
- `-DaccessTokenMode=tokens`: `accessTokens` 값을 사용한다.
- `-DaccessTokens`: 쉼표로 구분한 JWT 목록. `accessTokenMode=tokens`일 때 필수다.
- `-DqueueTokens`: 쉼표로 구분한 대기열 토큰 목록. 직접 토큰을 주입하는 시나리오에서 필수다.
- `-Dusers`: 가상 사용자 수, 기본값 `10`
- `-DdurationSeconds`: 사용자를 투입할 시간, 기본값 `10`

### 1. 대기열 진입 부하

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.QueueEnterSimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -Dusers=10 `
  -DdurationSeconds=10
```

확인할 것:

- `enter queue` 요청의 실패율
- 응답 시간 p95, p99
- 서버 로그의 예외 여부

### 2. 대기열 토큰 확보

홀드 경합 테스트는 `X-Queue-Token`이 필요하다. 아래처럼 토큰을 먼저 모은다.

```powershell
$baseUrl = "http://localhost:8080"
$queueTokens = @()

1..10 | ForEach-Object {
  $email = "loadtest$_@test.com"
  $password = "password1234"

  $login = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/v1/auth/login" `
    -ContentType "application/json" `
    -Body (@{
      email = $email
      password = $password
    } | ConvertTo-Json)

  $res = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/api/v1/queue/performances/1/enter" `
    -Headers @{ Authorization = "Bearer $($login.data.accessToken)" }

  if ($res.data.status -eq "ADMITTED") {
    $queueTokens += $res.data.queueToken
  }
}

$queueTokenList = $queueTokens -join ","
$queueTokenList
```

`$queueTokenList`가 비어 있으면 아직 모두 대기 중인 상태다. 이 경우 `users`를 낮추거나 잠시 뒤 status API로 토큰 발급 여부를 확인한다.

### 3. 홀드 경합 부하

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.HoldRaceSimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DseatIds=1 `
  -DqueueTokens=$queueTokenList `
  -Dusers=10 `
  -DdurationSeconds=10
```

같은 좌석이면 모두 성공하는 것이 정상 결과가 아니다. 성공은 제한된 좌석 수만큼만 나와야 하고, 나머지는 `400` 또는 `409` 계열로 거부되어야 한다. `500`이 나오면 서버 오류로 본다.

### 4. 예매 오픈 흐름

```powershell
.\gradlew.bat -p load-tests/gatling gatlingRun `
  --simulation com.ticket.loadtest.simulation.TicketOpenFlowSimulation `
  -DbaseUrl=http://localhost:8080 `
  -DperformanceId=1 `
  -DseatIds=1 `
  -Dusers=10 `
  -DdurationSeconds=10 `
  -DstatusPolls=3 `
  -DstatusPollPauseSeconds=1
```

흐름:

1. 대기열 진입
2. 즉시 입장하면 `queueToken` 저장
3. 대기 상태면 제한 횟수만 status polling
4. 토큰이 있으면 좌석 상태 조회
5. 토큰이 있으면 홀드 생성 시도

## 추천 증가 순서

처음부터 큰 값을 쓰지 않는다.

```text
users=5, durationSeconds=10
users=10, durationSeconds=10
users=30, durationSeconds=20
users=50, durationSeconds=30
```

로컬에서 `100+` 사용자를 넣으면 서버와 부하 발생기가 같은 PC 자원을 공유하므로 응답 시간은 참고값으로만 본다.

## 성공 기준

- 대기열 진입 요청의 실패율이 기준치를 넘지 않아야 한다.
- 정상 토큰이 있으면 좌석 상태 조회와 홀드 생성 흐름에 진입한다.
- 대기열 게이트 강제 여부는 별도 대기열 로직 변경에서 검증한다.
- 같은 좌석에 다수 사용자가 몰릴 때 성공한 hold/order는 제한된 좌석 수를 넘지 않아야 한다.
- Gatling 리포트에서 `500` 응답이 나오면 실패로 본다.

## 리포트 위치

Gatling 실행 후 HTML 리포트는 아래에 생성된다.

```text
load-tests/gatling/build/reports/gatling
```

## 스테이징 재사용

스테이징에서는 Simulation은 그대로 쓰고 아래 값만 바꾼다.

- `baseUrl`
- `performanceId`
- `seatIds`
- `accessTokens`
- `queueTokens`
- `users`
- `durationSeconds`

실제 운영 URL은 사용하지 않는다.
