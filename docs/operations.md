# 운영과 실행 기준

이 문서는 로컬 실행, 검증, 프로파일, 배포 기준을 정리한다.

## 기본 환경

- JDK 25
- Gradle wrapper
- Redis 7
- H2(local/dev)
- Oracle(prod)

## 로컬 실행

Redis 실행:

```bash
docker run --name ticket-redis -p 6379:6379 -d redis:7
```

API 실행:

```bash
./gradlew :core:core-api:bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat :core:core-api:bootRun
```

Swagger:

- `/api/swagger-ui.html`
- `/api/api-docs`

## 빠른 검증

컴파일 확인:

```bash
./gradlew :core:core-api:compileJava
```

도메인/애플리케이션 검증:

```bash
./gradlew :core:core-domain:test
./gradlew :core:core-app:test
```

계층 경계 검증:

```bash
./gradlew :core:core-api:test --tests "com.ticket.core.CoreLayerArchitectureTest"
```

배포 산출물 기준 검증:

```bash
./gradlew clean :core:core-api:bootJar -x test
```

Windows PowerShell:

```powershell
.\gradlew.bat :core:core-api:compileJava
.\gradlew.bat :core:core-domain:test
.\gradlew.bat :core:core-app:test
.\gradlew.bat clean :core:core-api:bootJar -x test
```

## 프로파일

### local

- H2 file DB
- Redis
- `ddl-auto: create`
- Flyway: disabled
- seed data: enabled

관련 설정:

- `core/core-api/src/main/resources/application.yml`
- `core/core-api/src/main/resources/application-local.yml`

### dev

- local과 같은 H2 file DB 사용
- Redis
- `ddl-auto: none`
- Flyway: enabled
- seed data: disabled
- local 프로파일이 생성한 H2 DB를 대상으로 Flyway baseline/migration을 검증

관련 설정:

- `core/core-api/src/main/resources/application-dev.yml`

### prod

- Oracle driver 사용
- `ddl-auto: none`
- Flyway: enabled
- 기존 운영 스키마는 최초 도입 시 Flyway baseline으로 등록

관련 설정:

- `core/core-api/src/main/resources/application-prod.yml`

### Admission token 검증

기존 클라이언트와 호환되는 초기 배포에서는 아래 환경 변수로 admission token 검증을 비활성화한다.

```text
ADMISSION_TOKEN_ENFORCEMENT_ENABLED=false
```

Queue Server와 클라이언트의 admission token 전달이 모두 준비된 뒤에만 `true`로 전환한다. 비활성 상태에서는 회차의 Queue 정책과 admission token을 조회하거나 검증하지 않는다.

### Queue shopping session 만료

현재 Core는 주문 생성·취소·만료 시 Queue Server에 session 완료 요청을 보내지 않는다. Queue 입장 후 shopping session은 Queue Server의 TTL로 만료된다. 따라서 운영 시에는 Queue의 entered marker 수와 TTL 만료 추이를 관측해야 하며, 조기 반환이나 동시 active session 상한은 별도 프로토콜 설계 후 도입한다.

## 좌석 선택 Redis 인덱스 전환

좌석 선택 조회는 기존 key scan 대신 공연별 Sorted Set 인덱스를 사용한다.
이전 버전이 만든 선택 키는 새 인덱스에 없으므로, Redis를 유지한 채 처음 배포할 때는
아래 순서로 전환한다.

1. 신규 좌석 선택 요청을 잠시 차단한다.
2. 좌석 선택 TTL인 5분 이상 기다린다.
3. Redis `SCAN`으로 `seat:select:{perf:*}:*` 형식의 기존 선택 키가 0개인지 확인한다.
4. 새 버전을 배포하고 좌석 상태 조회와 전체 선택 해제를 확인한다.
5. 신규 좌석 선택 요청을 다시 허용한다.

운영 Redis에서 전체 키를 한 번에 반환하는 `KEYS`는 사용하지 않는다.
좌석 선택을 중단할 수 없는 무중단 배포라면 배포 전에 기존 선택 키를 Sorted Set으로
백필하거나, 전환 기간에만 기존 scan 조회를 함께 사용하는 호환 코드가 필요하다.

## DB 마이그레이션

Flyway는 `core:core-api` 실행 모듈에서만 사용한다. 마이그레이션 파일 위치는 아래 경로다.

```text
core/core-api/src/main/resources/db/migration
core/core-api/src/main/resources/db/migration-vendor/h2
core/core-api/src/main/resources/db/migration-vendor/oracle
```

공통 migration은 `db/migration`에 두고, Oracle과 H2의 문법이 다른 migration은
`db/migration-vendor/oracle`, `db/migration-vendor/h2`에 같은 버전으로 각각 둔다.
`application-dev.yml`은 H2 경로를, `application-prod.yml`은 Oracle 경로를 명시해
현재 DB에 맞는 migration만 선택한다.

운영 DB는 이미 테이블이 존재한다는 전제로 도입한다. 최초 반영 전에는 다음 순서를 지킨다.

1. 운영 DB 백업 또는 복구 지점을 확보한다.
2. 애플리케이션 DB 계정이 `flyway_schema_history` 테이블을 생성하고 이후 DDL을 실행할 권한이 있는지 확인한다.
3. 최초 도입 배포에서만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 설정한다.
4. 애플리케이션 기동 후 `flyway_schema_history`에 version `1` baseline 기록이 생성됐는지 확인한다.
5. baseline 확인 후에는 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`로 되돌리거나 환경 변수를 제거한다.

기존 운영 스키마를 다시 만드는 `V1__...sql`은 추가하지 않는다. 이후 테이블 구조 변경은 새 파일로만 추가한다.

```text
V8__add_payment_tables.sql
V9__add_order_confirmed_at.sql
```

이미 운영에 적용된 migration 파일은 수정하지 않는다. 변경이 더 필요하면 다음 버전 파일을 새로 만든다.

### 조회 인덱스 적용

`V3__add_performance_seat_unique_index.sql`과 `V4__add_order_seat_order_index.sql`은
좌석 선택 검증과 주문 상세 조회에 필요한 인덱스를 적용한다.
`V5__create_order_hold_release_outbox.sql`과 `V6__create_order_hold_creation_outbox.sql`은
주문 커밋 후 작업을 유실 없이 재시도하기 위한 outbox 테이블과 due 조회 인덱스를 만든다.
`V7__add_hold_released_at_to_outbox.sql`은 Redis 해제 완료 단계를 저장해 WebSocket 발행만
안전하게 재시도할 수 있게 한다.

배포 전에는 `docs/database/core-api-query-indexes.sql`의 중복 조회 결과가 0건인지 확인한다.
중복이 있으면 배포를 중단하고, `ORDER_SEATS.performance_seat_id` 등 참조 데이터를 확인해
대표 행을 결정한 뒤 정리한다. migration에서 중복 행을 임의 삭제하지 않는다.

Oracle DDL은 실행 시 암묵적으로 커밋된다. 그래서 두 인덱스를 V3과 V4로 분리했고,
각 migration은 같은 목적의 기존 인덱스가 있으면 건너뛴다. 실패 후 재시도하기 전에는
`USER_IND_COLUMNS`와 `flyway_schema_history`를 함께 확인한다. 적용 후에도 같은 점검 SQL로
두 인덱스의 컬럼 순서를 확인한다.

local 프로파일은 H2 file DB(`~/ticket-local`)를 Hibernate `ddl-auto:create`와 seed loader로 초기화한다. dev 프로파일은 같은 H2 file DB를 사용하되 Hibernate 자동 DDL과 seed loader를 끄고 Flyway만 활성화한다. 기존 local DB를 dev에서 처음 Flyway에 편입할 때만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 지정해 version `1` baseline을 만들고, 평소에는 기본값(`false`)을 유지한다. 이후 변경은 `V2__...sql`부터 검증한다.

주의: 현재 운영 baseline 방식은 기존 스키마를 다시 만드는 `V1__...sql`을 두지 않는다. 따라서 완전히 빈 dev DB에서 Flyway만으로 애플리케이션을 띄우려면 먼저 local 프로파일로 H2 DB를 생성하거나, 별도 스키마 생성 migration 전략을 정해야 한다.

## 배포 workflow

GitHub Actions 배포 workflow는 전체 테스트와 infra 통합 테스트를 통과한 뒤 bootJar를 만든다.

```bash
./gradlew clean test :core:core-infra:integrationTest :core:core-api:bootJar
```

관련 파일:

- `.github/workflows/deploy.yml`

## Core 용량 관측

Core는 `/actuator/prometheus`에서 용량 판정에 필요한 애플리케이션 메트릭을 노출한다. 부하 테스트 중에는 다음을 같은 시간축으로 본다.

- `http_server_requests_seconds_bucket/count`: URI·method·status별 처리량과 p95/p99
- `hikaricp_connections_active/pending/max/min`: DB connection pool 사용량과 대기
- `tomcat_threads_busy_threads/current_threads/config_max_threads`: 요청 스레드 사용량과 상한
- `jvm_gc_pause_seconds`, `jvm_memory_used_bytes`, `process_cpu_usage`: JVM·CPU 포화 여부
- executor_active_threads, executor_queued_tasks: background worker 사용량과 적체
- executor 메트릭의 name 태그: redisExpirationSubscriptionExecutor,
  redisExpirationTaskExecutor, bookingBackgroundTaskExecutor

모든 메트릭에는 `service`, `environment`, `version` 태그가 붙는다. 운영 task에는 `DD_SERVICE=ticket-core`, `DD_ENV=prod`, `DD_VERSION=<배포버전>`을 동일하게 주입해야 task별 비교와 배포 전후 비교가 가능하다.

```promql
histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{service="ticket-core"}[1m])))
```

```promql
sum(rate(http_server_requests_seconds_count{service="ticket-core",status=~"5.."}[1m]))
max(hikaricp_connections_pending{service="ticket-core"})
(
  max(tomcat_threads_busy_threads{service="ticket-core"})
  /
  max(tomcat_threads_config_max_threads{service="ticket-core"})
)
```

Hikari pending이 0보다 커지면 애플리케이션 요청이 DB 연결을 빌리지 못하고 기다리는 상태다. 다만 pending이 0이어도 이미 빌린 연결이 DB lock에서 멈출 수 있으므로 DB wait를 별도로 확인해야 한다.

redisExpirationTaskExecutor는 TTL 만료 DB 진입을 최대 2개로 제한한다.
bookingBackgroundTaskExecutor는 주문 커밋 후 작업을 최대 2개로 제한한다.
queued 값이 256에 오래 머물거나 queue 포화 경고가 반복되면 이전 회차 작업이
현재 부하와 겹친 것이다. hold creation/release 즉시 작업은 누락되어도 2분 주기의
각 outbox scheduler가 보정하지만, backlog가 해소되기 전에는 다음 부하를 넣지 않는다.

Oracle lock wait는 Actuator만으로 볼 수 없다. Oracle exporter·Datadog DBM 또는 DBA 권한이 있는 별도 관측 계정에서 다음 정보를 수집한다.

```sql
SELECT COUNT(*) AS blocked_sessions FROM v$session WHERE blocking_session IS NOT NULL;

SELECT event, COUNT(*) AS waiting_sessions, MAX(seconds_in_wait) AS max_seconds_in_wait
FROM v$session
WHERE state = 'WAITING' AND wait_class <> 'Idle'
GROUP BY event
ORDER BY waiting_sessions DESC;
```

`v$session` 조회 권한은 애플리케이션 계정에 추가하지 말고 관측 전용 계정에만 부여한다.

## 부하 테스트

예매 오픈 부하 테스트는 [load-test.md](load-test.md)에서 시작한다.

상세 실행 문서와 실제 실행 프로젝트:

- `docs/load-test/ticket-open-local.md`
- 형제 저장소 `../gatling-test/README.md`
- 로컬 콘솔 `../gatling-test/console/README.md`

현재 Gatling 시나리오는 이 저장소가 아니라 형제 `gatling-test` 저장소에서 관리한다.

연속 부하 테스트는 회차 ID만 바꾸는 것으로 격리되지 않는다. 다음 실행 전에는
이전 실행의 PENDING 주문이 만료됐는지, Redis hold TTL이 끝났는지,
ORDER_HOLD_CREATION_OUTBOX와 ORDER_HOLD_RELEASE_OUTBOX의 PENDING/FAILED 건이
정리됐는지 같은 시간축으로 확인한다.

```powershell
cd ..\gatling-test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 운영 반영 시 주의점

- 운영 DB는 자동 DDL을 사용하지 않는다.
- DB 컬럼명, enum, 상태 모델 변경은 마이그레이션 계획을 함께 작성한다.
- Redis key, TTL, expiration listener 변경은 장애 복구와 scheduler 보정 흐름까지 같이 검토한다.
- 인증/인가 변경은 공개 API 노출 여부와 토큰 만료/재발급 흐름을 함께 확인한다.
