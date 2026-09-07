# 운영과 실행 기준

이 문서는 로컬 실행, 프로파일, DB 마이그레이션, 배포, 관측 기준을 정리한다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)과
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md), 검증
명령은 `/verify` 스킬이 원본이다.

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

애플리케이션 실행:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

**단일 Gradle Spring Boot 프로젝트다.** `@SpringBootApplication`(`@Modulith`)과 `application*.yml`이
루트 `src/main/resources`에 있으므로 `:core:core-api:bootRun` 같은 subproject 실행 명령은 없다.
`application.yml`에 기본 프로파일이 없어 프로파일을 지정하지 않으면 datasource 설정이 비어
기동에 실패한다.

Swagger:

- `/api/swagger-ui.html`
- `/api/api-docs`

## 검증

무엇을 돌릴지 고르는 표, 구조 테스트 명령, 통합 테스트 조건, 결과 보고 규칙은
**`/verify` 스킬**이 원본이다. 여기 옮겨 적지 않는다.

## 프로파일

### local

- H2 file DB
- Redis
- `ddl-auto: create`
- Flyway: disabled
- seed data: enabled

관련 설정:

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`

### dev

- local과 같은 H2 file DB 사용
- Redis
- `ddl-auto: validate`
- Flyway: enabled, module-aware(`spring.modulith.runtime.flyway-enabled: true`)
- seed data: disabled
- local 프로파일이 생성한 H2 DB를 대상으로 Flyway baseline/migration을 검증

관련 설정:

- `src/main/resources/application-dev.yml`

### prod

- Oracle driver 사용
- `ddl-auto: validate`
- Flyway: enabled, module-aware
- 기존 운영 스키마는 최초 도입 시 Flyway baseline으로 등록

관련 설정:

- `src/main/resources/application-prod.yml`

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

Flyway는 루트 `src/main/resources/db`에서 소스를 읽는다. `bootstrap/src/main/resources/db`
경로는 더 이상 없다.

```text
src/main/resources/db/migration/__root                       # 전환 이전 공통 이력(V2~V8), 내용 변경 없음
src/main/resources/db/migration-vendor/h2/__root              # 전환 이전 H2 이력
src/main/resources/db/migration-vendor/oracle/__root          # 전환 이전 Oracle 이력
src/main/resources/db/migration/{module}                      # module 소유 신규 공통 migration(현재 booking)
src/main/resources/db/migration-vendor/h2/{module}             # module 소유 신규 H2 migration
src/main/resources/db/migration-vendor/oracle/{module}         # module 소유 신규 Oracle migration
```

`spring.modulith.runtime.flyway-enabled: true`(모든 프로파일 공통, `application.yml`)로 각
모듈이 독립된 `flyway_schema_history_{module}` 이력 테이블을 갖는다. 전환 이전부터 있던 공통
이력은 `__root` 이력(기존 `flyway_schema_history`에 대응)으로 그대로 유지되고, 버전 번호도
바꾸지 않았다. 모듈이 소유하는 새 schema 변경(cross-module FK 제거, scalar column 전환,
신규 module의 첫 schema 등)은 module별 폴더에 **1부터 새로 버전을 매겨** 추가한다 — `__root`의
V 번호와 독립적이다. 현재 독립 migration 이력을 가진 모듈은 `show`, `venue`, `favorite`, `booking`,
`payment`다(ADR 0005, ADR 0006). `payment`는 이번 entity-only 단계 첫 schema라
`V1__create_payments.sql`부터 시작하고, `show`/`booking`은 기존 이력 위에 이어서 버전을 매긴다.
`venue`/`favorite`는 ADR 0006의 BC 재편으로 `catalog`(→`show`)에서 분리된 신설 module 이름이라,
그 이름으로는 이력이 없어 각자 V1부터 새로 시작한다 — 그래서 옮겨온 migration은 멱등화가
필요하다(ADR 0006 "Flyway 이력 재시작과 멱등화 예외" 참고). `TICKETS`는 원래 `ticketing` module의
V1이었으나 ticketing이 booking으로 흡수되며 booking V5(`V5__create_tickets.sql`)로 옮겼다 —
`flyway_schema_history_ticketing`이 이미 있는 로컬 H2 파일 DB는 초기화가 필요하다. 공통 SQL은 `db/migration/{module}`, DB별 문법 차이가 있는
SQL은 `db/migration-vendor/{h2,oracle}/{module}`에 같은 버전으로 각각 둔다 — 모듈에 DB별
차이만 있고 공통 SQL이 없으면(현재 `show`, `venue`, `favorite`, `payment`) `db/migration/{module}`
폴더 자체를 만들지 않는다. `db/migration`에는 현재 `__root`와 `booking`만 있다.

공통 migration은 `db/migration/__root`(또는 `{module}`)에 두고, Oracle과 H2의 문법이 다른
migration은 `db/migration-vendor/oracle`, `db/migration-vendor/h2`에 같은 버전으로 각각 둔다.
`application-dev.yml`은 H2 경로를, `application-prod.yml`은 Oracle 경로를 명시해 현재 DB에
맞는 migration만 선택한다.

운영 DB는 이미 테이블이 존재한다는 전제로 도입한다. 최초 반영 전에는 다음 순서를 지킨다.

1. 운영 DB 백업 또는 복구 지점을 확보한다.
2. 애플리케이션 DB 계정이 `flyway_schema_history`(와 module별
   `flyway_schema_history_{module}`) 테이블을 생성하고 이후 DDL을 실행할 권한이 있는지
   확인한다.
3. 최초 도입 배포에서만 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 설정한다.
4. 애플리케이션 기동 후 `flyway_schema_history`에 version `1` baseline 기록이 생성됐는지 확인한다.
5. baseline 확인 후에는 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`로 되돌리거나 환경 변수를 제거한다.

기존 운영 스키마를 다시 만드는 `V1__...sql`은 추가하지 않는다. 이후 테이블 구조 변경은 새 파일로만
추가한다. `__root`에 남는 변경(어떤 module에도 속하지 않는 순수 기술 테이블)과 module 소유
변경(module의 aggregate/schema 경계 안)을 먼저 구분한 뒤 폴더를 고른다.

```text
db/migration/booking/V2__...sql          # booking이 소유하는 schema 변경
db/migration/__root/V9__...sql           # 어떤 module에도 속하지 않는 변경(드물어야 한다)
```

이미 운영에 적용된 migration 파일은 수정하지 않는다. 변경이 더 필요하면 다음 버전 파일을 새로
만든다.

**예외(module 개명·분리로 이력이 재시작될 때만)**: `catalog` → `show` 개명, `venue`/`favorite`
신설처럼 module 식별자 자체가 바뀌면 그 폴더는 새 `flyway_schema_history_{module}` 이력으로
처음부터 다시 실행된다 — 이미 적용됐던 내용이라도 이 새 이력 기준으로는 "아직 적용 전"이다. 이
경우에 한해 **새 이력으로 옮겨가는 파일에** 존재 확인 가드(멱등화)를 추가하는 것을 허용한다.
이미 적용이 끝나 그대로 남는 이력의 파일(예: 그대로 유지되는 `__root`, 이름이 바뀌지 않은
`booking`)은 이 예외 대상이 아니며 여전히 수정하지 않는다. 상세 배경은
[ADR 0006](adr/0006-bounded-context-module-boundaries.md#flyway-이력-재시작과-멱등화-예외)을
본다.

### 완료된 module 경계 정리

`db/migration-vendor/{h2,oracle}/booking/V1__drop_performance_seat_cross_module_fk.sql`이
`PerformanceSeat`의 cross-module FK(과거 Performance/Seat 테이블 참조)를 제거했고,
`db/migration-vendor/{h2,oracle}/__root/V8__create_event_publication_registry.sql`이 Spring Modulith
2.1.1의 `EVENT_PUBLICATION`/`EVENT_PUBLICATION_ARCHIVE` registry table을 만들었다(과거 custom
outbox 테이블은 이 시점에 별도 booking migration으로 제거됐다). 기존 V3(`add_performance_seat_unique_index`)~V4(`add_order_seat_order_index`)의 조회 인덱스는 그대로 `__root`
이력에 남아 있다.

배포 전에는 `docs/database/core-api-query-indexes.sql`의 중복 조회 결과가 0건인지 확인한다.
중복이 있으면 배포를 중단하고, `ORDER_SEATS.performance_seat_id` 등 참조 데이터를 확인해
대표 행을 결정한 뒤 정리한다. migration에서 중복 행을 임의 삭제하지 않는다.

ADR 0005의 가격 재설계는 `show`(옛 `catalog`, V4~V7)와 `booking`(V3~V4) migration으로 이미
반영됐다. `show` V4가 `GRADES`/`PERFORMANCE_GRADES`를 만들고, V5~V6가 기존 `SHOW_GRADES`/`SHOW_SEATS`
데이터를 각각 `PERFORMANCE_GRADES`, `PERFORMANCE_SEATS.performance_grade_id`/`unit_price`로
backfill하며, V7이 이관이 끝난 `SHOW_GRADES`/`SHOW_SEATS`를 drop한다(expand -> migrate ->
contract). ADR 0006의 BC 재편으로 `show` V8이 옛 `@ManyToOne Venue` 매핑이 남겼을 수 있는
`SHOWS.venue_id` FK를 제거한다(존재 여부 동적 확인, 없으면 no-op) — Venue/Seat 자체는 `venue`
module로 분리됐다(`venue` V1, 옛 `catalog`/`show` V3). `booking` V3는 `PERFORMANCE_SEATS`에 같은
컬럼을 NOT NULL로 조이고, V4는 `ORDERS`/`ORDER_SEATS`에 주문 시점 snapshot 컬럼을 추가하면서
`payment_failed_at`을 제거한다(`Order.PAYMENT_FAILED` 상태 폐기). `payment`(`PAYMENTS`)는 이번
entity-only 단계의 신규 schema라 `V1__create_payments.sql`로 시작하고, `TICKETS`는 booking
V5(`V5__create_tickets.sql`)가 만든다. 위 migration들은 `SHOW_GRADES`/`SHOW_SEATS`/`ORDERS`/`ORDER_SEATS` 등 pre-Flyway
baseline table이 존재하지 않는 검증 환경(`OracleMigrationCompatibilityTest` 등)에서는 no-op이
되도록 존재 여부를 먼저 확인한다.

Oracle DDL은 실행 시 암묵적으로 커밋된다. 인덱스처럼 실패 후 재시도가 필요한 변경은 여러
migration으로 분리하고, 각 migration은 같은 목적의 기존 인덱스가 있으면 건너뛴다. 실패 후
재시도하기 전에는 `USER_IND_COLUMNS`와 `flyway_schema_history`(module 소유라면
`flyway_schema_history_{module}`)를 함께 확인한다.

### dangling venue_id 점검 (ADR 0006)

`show` V8이 `SHOWS.venue_id`의 옛 cross-module FK를 제거하므로, DB 수준에서는 더 이상 존재하지
않는 Venue를 가리키는 `venue_id`를 막지 않는다. `Show.venueId`는 그런 경우 애플리케이션 쪽에서
"venue 없는 show"와 같은 결과(표시값 null, 좌석 빈 목록)로 통일해 처리하지만, 배포 후 다음
쿼리로 실제로 그런 row가 생기지 않았는지 주기적으로 확인한다.

```sql
SELECT id FROM SHOWS WHERE venue_id IS NOT NULL AND venue_id NOT IN (SELECT id FROM VENUES);
```

결과가 있으면 애플리케이션 오류가 아니라 데이터 정합성 문제다 — 해당 Show의 `venue_id`를 바로잡거나
Venue 데이터를 복구한다.

local 프로파일은 H2 file DB(`~/ticket-local`)를 Hibernate `ddl-auto:create`와 seed loader로
초기화한다. dev 프로파일은 같은 H2 file DB를 사용하되 Hibernate 자동 DDL과 seed loader를 끄고
Flyway만 활성화한다. 기존 local DB를 dev에서 처음 Flyway에 편입할 때만
`SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`를 지정해 version `1` baseline을 만들고, 평소에는
기본값(`false`)을 유지한다. 이후 변경은 `__root`의 `V2__...sql`부터, module 소유 변경은 해당
module 폴더의 `V1__...sql`부터 검증한다.

주의: 현재 운영 baseline 방식은 기존 스키마를 다시 만드는 `V1__...sql`을 `__root`에 두지
않는다. 따라서 완전히 빈 dev DB에서 Flyway만으로 애플리케이션을 띄우려면 먼저 local 프로파일로
H2 DB를 생성하거나, 별도 스키마 생성 migration 전략을 정해야 한다.

## 배포 workflow

GitHub Actions CI(`ci.yml`)는 root project 하나만 있는 단일 Gradle build로 전체 테스트를 통과한
뒤 bootJar를 만든다.

```bash
./gradlew clean test bootJar
```

`.github/workflows/deploy.yml`은 `master` push에서 위 CI(`ci.yml`)를 호출해 통과한 jar를 받아
Docker 이미지를 빌드하고 배포한다. `bootstrap/build/libs` 경로는 더 이상 없다 — 산출물은 루트
`build/libs/*.jar`다.

관련 파일:

- `.github/workflows/ci.yml`
- `.github/workflows/deploy.yml`
- `Dockerfile`(`build/libs/*.jar`를 `app.jar`로 복사)

## Core 용량 관측

Core는 `/actuator/prometheus`에서 용량 판정에 필요한 애플리케이션 메트릭을 노출한다. 부하 테스트 중에는 다음을 같은 시간축으로 본다.

- `http_server_requests_seconds_bucket/count`: URI·method·status별 처리량과 p95/p99
- `hikaricp_connections_active/pending/max/min`: DB connection pool 사용량과 대기
- `tomcat_threads_busy_threads/current_threads/config_max_threads`: 요청 스레드 사용량과 상한
- `jvm_gc_pause_seconds`, `jvm_memory_used_bytes`, `process_cpu_usage`: JVM·CPU 포화 여부
- executor_active_threads, executor_queued_tasks: background worker 사용량과 적체
- executor 메트릭의 name 태그: redisExpirationSubscriptionExecutor, redisExpirationTaskExecutor

**주문 커밋 후 이벤트 리스너(`BookingEventListeners`)에는 이름이 붙은 전용 executor가 없다.**
Redis 만료 처리와 달리 `applicationTaskExecutor`(Spring Boot 기본 비동기 executor)를 쓰므로
위 executor 태그 목록에 잡히지 않는다. 이 경로의 적체는 `EVENT_PUBLICATION` 테이블의 PENDING/
PROCESSING 건수와 `EventPublicationMaintenance`가 남기는 재시도 초과 로그로 확인한다. 상세는
[core-booking-lifecycle.md](core-booking-lifecycle.md#운영-확인)를 본다.

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

redisExpirationTaskExecutor는 TTL 만료 DB 진입을 최대 2개로 제한한다. queued 값이 256에 오래
머물거나 queue 포화 경고가 반복되면 이전 회차 작업이 현재 부하와 겹친 것이다. 이벤트 리스너
처리는 명시적 concurrency 상한이 없으므로(위 참고), 즉시 처리가 누락되거나 실패해도 1분 주기의
`EventPublicationMaintenance.resubmitFailed`(batch 100·동시 4)가 보정하지만, backlog가
해소되기 전에는 다음 부하를 넣지 않는다.

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
`EVENT_PUBLICATION`의 PENDING/FAILED 건이 정리됐는지 같은 시간축으로 확인한다.

```powershell
cd ..\gatling-test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 운영 반영 시 주의점

- 운영 DB는 자동 DDL을 사용하지 않는다.
- DB 컬럼명, enum, 상태 모델 변경은 마이그레이션 계획을 함께 작성하고, 어느 module 소유
  migration 폴더에 둘지 먼저 정한다.
- Redis key, TTL, expiration listener 변경은 장애 복구와 scheduler 보정 흐름까지 같이 검토한다.
- 인증/인가 변경은 공개 API 노출 여부와 토큰 만료/재발급 흐름을 함께 확인한다.
- 다중 좌석 주문 트래픽을 늘리기 전에는 `EVENT_PUBLICATION.serialized_event`
  (`VARCHAR(255)`) 크기 리스크를 먼저 검토한다 — 상세는
  [ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md#5-spring-modulith-이벤트와-jpa-event-publication-registry)을
  본다.
