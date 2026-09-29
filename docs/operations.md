# 운영과 실행 기준

이 문서는 프로파일, DB 마이그레이션, 배포, 관측 기준을 정리한다. 기본 로컬 실행은 [README.md](../README.md)를 본다. 결정 배경은
[ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md)과
[ADR 0005](adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md)다. 검증 선택과 명령은 [testing.md](testing.md#변경별-검증)를 본다.

## 기본 환경

- JDK 25
- Gradle wrapper
- Redis 7
- H2(local/dev)
- Oracle(prod)

## 로컬 실행

기본 기동·seed·접속 확인은 [README.md](../README.md), seed 상세 옵션은 [seed/README.md](../seed/README.md)를 따른다.

## 프로파일

### local

- H2 file DB
- Redis
- `ddl-auto: validate`
- Flyway: enabled. 스키마는 운영과 같은 migration으로 만든다(빈 DB면 `__root` V1부터)
- 서버를 재시작해도 데이터가 남는다. 처음부터 다시 만들려면 서버를 끄고 `~/ticket-local*.db` 파일을 지운다
- 초기 데이터: **기동 시 자동으로 넣지 않는다.** 기동이 끝난 뒤 `.\gradlew.bat seedLocal`을
  따로 실행한다 — [seed/README.md](../seed/README.md)

관련 설정:

- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`

### dev

- local과 같은 H2 file DB 사용
- Redis
- `ddl-auto: validate`
- Flyway: enabled, module-aware(`spring.modulith.runtime.flyway-enabled: true`)
- 초기 데이터: 넣지 않는다(`seedLocal`은 local 프로파일 설정을 읽는 로컬 전용 명령이다)
- local과 설정이 사실상 같다. 남아 있는 이유는 옛 local DB(ddl-auto로 만든 스키마)를 baseline하던 용도다

관련 설정:

- `src/main/resources/application-dev.yml`

### prod

- Oracle driver 사용
- `ddl-auto: validate`
- Flyway: enabled, module-aware
- 기존 운영 스키마는 최초 도입 시 Flyway baseline으로 등록
- 초기 데이터: 기동 시 넣지 않는다. **테이블 생성(배포/Flyway)과 데이터 적재(`seedProd`)는 별개
  작업이다.** `seedProd`는 이미 준비된 테이블에 데이터를 넣기만 하고, 테이블을 만들거나 지우거나
  초기화하지 않으며 기존의 불완전한 데이터를 자동으로 고치지도 않는다 —
  [seed/README.md](../seed/README.md)
- `seedProd`는 `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` /
  `SPRING_DATASOURCE_PASSWORD`를 쓴다(prod 프로파일이 쓰는 것과 같은 변수). Wallet을 쓰면
  `TNS_ADMIN`도 서버와 같은 값이어야 한다. 하나라도 없으면 적재를 시작하기 전에 실패한다 —
  로컬 DB로 대체하지 않는다.

관련 설정:

- `src/main/resources/application-prod.yml`

### Admission token 검증

기존 클라이언트와 호환되는 초기 배포에서는 아래 환경 변수로 admission token 검증을 비활성화한다.

```text
ADMISSION_TOKEN_ENFORCEMENT_ENABLED=false
```

Queue Server와 클라이언트의 admission token 전달이 모두 준비된 뒤에만 `true`로 전환한다. 비활성 상태에서는 회차의 Queue 정책과 admission token을 조회하거나 검증하지 않는다.

admission token의 서명 secret, issuer, audience는 Core와 `ticket-queue` 두 저장소 설정이 일치해야
한다. 한쪽만 바꾸면 검증 실패가 부하 문제가 아니라 설정 불일치로 발생한다.

### Queue shopping session 만료

현재 Core는 주문 생성·취소·만료 시 Queue Server에 session 완료 요청을 보내지 않는다. Queue 입장 후 shopping session은 Queue Server의 TTL로 만료된다. 따라서 운영 시에는 Queue의 entered marker 수와 TTL 만료 추이를 관측해야 하며, 조기 반환이나 동시 active session 상한은 별도 프로토콜 설계 후 도입한다.

## Redis 운영과 전환

- 운영 Redis에서 `KEYS`를 사용하지 않는다. 필요한 조회는 인덱스(Sorted Set 등)로 만든다.
- key 형식·인덱스 구조를 바꾸면 기존 key가 남아 있는 상태의 전환 절차를 함께 설계한다.
- TTL, expiration listener, scheduler 보정 중 하나만 바꾸지 않는다 — 세 경로가 같은 정합성을
  함께 지킨다.

key·TTL 구현의 계층 소유권은 [architecture.md](architecture.md#저장소와-동시성),
주문 시작 락과 좌석 락의 수명은 [예매 수명주기](core-booking-lifecycle.md#주문-생성예매-시작)를 본다.

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
V 번호와 독립적이다. 현재 독립 migration 이력을 가진 모듈은 `show`, `venue`, `like`, `booking`,
`payment`다(ADR 0005, ADR 0006, ADR 0008). `payment`는 이번 entity-only 단계 첫 schema라
`V1__create_payments.sql`부터 시작하고, `show`/`booking`은 기존 이력 위에 이어서 버전을 매긴다.
`venue`/`like`는 ADR 0006의 BC 재편으로 `catalog`(→`show`)에서 분리된 신설 module 이름이라,
그 이름으로는 이력이 없어 각자 V1부터 새로 시작한다 — 그래서 옮겨온 migration은 멱등화가
필요하다(ADR 0006 "Flyway 이력 재시작과 멱등화 예외" 참고). `like`는 ADR 0006 시점에는 `favorite`로
신설됐다가 ADR 0008로 다시 `favorite` → `like` 개명을 거쳤다 — 개명 자체도 module 식별자가
바뀌는 사건이라 `flyway_schema_history_favorite`를 버리고 `flyway_schema_history_like`로 한 번 더
처음부터 시작한다(V1·V2는 그래서 내용 변경 없이 폴더만 옮겼다). `TICKETS`는 원래 `ticketing` module의
V1이었으나 ticketing이 booking으로 흡수되며 booking V5(`V5__create_tickets.sql`)로 옮겼다 —
`flyway_schema_history_ticketing`이 이미 있는 로컬 H2 파일 DB는 초기화가 필요하다. 공통 SQL은 `db/migration/{module}`, DB별 문법 차이가 있는
SQL은 `db/migration-vendor/{h2,oracle}/{module}`에 같은 버전으로 각각 둔다 — 모듈에 DB별
차이만 있고 공통 SQL이 없으면(현재 `show`, `venue`, `like`, `payment`) `db/migration/{module}`
폴더 자체를 만들지 않는다. `db/migration`에는 현재 `__root`와 `booking`만 있다.

공통 migration은 `db/migration/__root`(또는 `{module}`)에 두고, Oracle과 H2의 문법이 다른
migration은 `db/migration-vendor/oracle`, `db/migration-vendor/h2`에 같은 버전으로 각각 둔다.
`application-dev.yml`은 H2 경로를, `application-prod.yml`은 Oracle 경로를 명시해 현재 DB에
맞는 migration만 선택한다.

운영 DB는 이미 테이블이 존재한다는 전제로 도입했다. 이력 테이블이 아직 없을 때의 baseline은
설정이 아니라 Spring Modulith가 정한다. `SpringModulithFlywayMigrationStrategy`는 `__root`와
module마다 Flyway를 새로 만들면서 `baselineOnMigrate=true`, `baselineVersion=0`을 강제한다. 그래서
이력 테이블이 없는 폴더는 version `0` baseline을 만든 뒤 가장 낮은 버전(`V1`)부터 적용된다.
`spring.flyway.baseline-on-migrate`/`baseline-version`은 이 동작을 바꾸지 못하므로
두지 않는다(`FlywayConfigurationTest`가 고정한다). 새 module 폴더를 처음 배포하거나 새 DB에 처음
적용하기 전에는 다음을 확인한다.

1. 운영 DB 백업 또는 복구 지점을 확보한다.
2. 애플리케이션 DB 계정이 `flyway_schema_history`(와 module별
   `flyway_schema_history_{module}`) 테이블을 생성하고 이후 DDL을 실행할 권한이 있는지
   확인한다.
3. 기동 후 해당 이력 테이블에 version `0` baseline과 적용된 버전이 모두 `success`로 기록됐는지
   확인한다. Oracle에서 이력 테이블과 컬럼은 소문자로 만들어지므로 따옴표로 감싸 조회한다
   (`SELECT "version", "success" FROM "flyway_schema_history_booking"`).

`__root` V1(`V1__create_pre_flyway_baseline_schema.sql`)은 Flyway 도입 전 스키마를 빈 DB에 다시 만든다.
운영 `flyway_schema_history`에는 Modulith 전환 전에 기록된 version `1` BASELINE이 있어서 운영은 이 파일을
건너뛴다. 로컬과 테스트처럼 빈 DB만 V1을 실행한다. V1은 **도입 당시 모양**이라 지금 entity와 다르다 —
뒤 migration이 그 모양을 전제로 컬럼·제약을 더한다. 그래서 V1을 지금 entity에 맞춰 고치지 않는다.
빈 DB에서 전체 migration이 entity와 맞는지는 `MigrationChainSchemaTest`(H2)와
`OracleMigrationChainSchemaTest`(Oracle, Docker 필요)가 확인한다. 옛 스키마를 직접 만드는 migration
테스트는 `ModulithFlywayTestSupport.migrate`가 운영과 같은 version `1` BASELINE을 먼저 남긴다.

**DB 스키마의 원본은 `db/migration/`이다**(방언별 SQL은 같은 규칙의 `db/migration-vendor/{h2,oracle}/`,
[ADR 0020](adr/0020-db-schema-source-of-truth-is-migration.md)). 테이블·컬럼·인덱스·유니크 제약·FK는
migration에만 적고, entity의 `@Table`에는 테이블 이름만 둔다. Hibernate `validate`는 인덱스·유니크 제약을
보지 않아서, entity에 적어도 운영에는 만들어지지 않고 어긋나도 드러나지 않는다. Spring context를 띄우는
H2 테스트도 `@MigratedSchema`로 같은 migration을 적용해 스키마를 만든다. 이후 테이블 구조 변경은 새 파일로만
추가한다. `__root`에 남는 변경(어떤 module에도 속하지 않는 순수 기술 테이블)과 module 소유
변경(module의 aggregate/schema 경계 안)을 먼저 구분한 뒤 폴더를 고른다.

```text
db/migration/booking/V2__...sql          # booking이 소유하는 schema 변경
db/migration/__root/V9__...sql           # 어떤 module에도 속하지 않는 변경(드물어야 한다)
```

이미 운영에 적용된 migration 파일은 수정하지 않는다. 변경이 더 필요하면 다음 버전 파일을 새로
만든다.

**예외(module 개명·분리로 이력이 재시작될 때만)**: `catalog` → `show` 개명, `venue`/`favorite`
신설, `favorite` → `like` 개명(ADR 0008)처럼 module 식별자 자체가 바뀌면 그 폴더는 새
`flyway_schema_history_{module}` 이력으로
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
outbox 테이블은 이 시점에 별도 booking migration으로 제거됐다). 기존 V3(`add_performance_seat_unique_index`)~V4(`add_order_seat_order_index`)는 그대로 `__root`
이력에 남아 있다. 다만 V4가 만든 `idx_order_seats_order_id`는 booking V7이 지운다 — PK·유니크가 아닌 보조
인덱스는 당분간 두지 않는다(booking V7, payment V2, show V11이 옛 보조 인덱스를 지웠다). 필요해지면 새
migration으로 다시 만든다.

배포 전에는 `PERFORMANCE_SEATS`에 `(performance_id, seat_id)` 중복이 있는지 확인한다 —
`SELECT performance_id, seat_id, COUNT(*) FROM performance_seats GROUP BY performance_id, seat_id
HAVING COUNT(*) > 1`의 결과가 0건이어야 한다(배포 전후로 `USER_IND_COLUMNS`에서
`PERFORMANCE_SEATS`/`ORDER_SEATS`의 인덱스 컬럼과 순서도 같이 본다).
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

### 정책 소유권 이관(booking V6, ADR 0006 "Performance의 책임 혼재" A2)

`booking` V6(`V6__create_booking_performance_sales_policies.sql`)는 예매 접수 기간·Hold 한도·
대기열 진입 정책의 원본과 판단을 Show/`__root`에서 Booking BC로 이관한다. `BOOKING_PERFORMANCE_SALES_POLICIES`를
멱등하게 만든 뒤, 옛 `__root` V2 소유 `PERFORMANCE_QUEUE_POLICIES`와 pre-Flyway baseline인
`PERFORMANCES`의 정책 컬럼 4개(`order_open_time`/`order_close_time`/`max_can_hold_count`/`hold_time`)를
backfill하고 제거한다. **다른 module 소유 schema를 만지는 것은 이 저장소의 일반 규칙상 금지지만,
이 migration은 소유권 자체가 이관되는 일회성 예외다**(ADR 0006 참고) — booking과 show의 독립
migration 실행 순서에 기대지 않도록 create -> backfill -> drop을 한 파일 안에서 원자적으로
수행한다. backfill 규칙: 접수 기간(`order_open_time`/`order_close_time`)이 둘 다 null인 회차는
정책 미구성으로 보아 row를 만들지 않고, 한쪽만 null이거나 시작이 마감보다 늦은 데이터는 대상
컬럼의 `NOT NULL`/`CHECK` 제약 위반으로 migration 자체를 실패시켜 원본 데이터를 먼저 확인하게
한다. `hold_time`이 null이면 `Performance.holdTime`의 기존 Java 기본값과 같은 600초를 적용한다
(`BookingPerformanceSalesPolicyMigrationTest`가 이 결정을 고정한다). 이 migration도 정책 컬럼이
아예 없는 검증 환경(`BookingModuleSlicingSchemaTest`, `OracleMigrationCompatibilityTest` 등)에서는
no-op이 되도록 컬럼 존재 여부를 먼저 확인한다.

Oracle DDL은 실행 시 암묵적으로 커밋된다. 인덱스처럼 실패 후 재시도가 필요한 변경은 여러
migration으로 분리하고, 각 migration은 같은 목적의 기존 인덱스가 있으면 건너뛴다. 실패 후
재시도하기 전에는 `USER_IND_COLUMNS`와 `flyway_schema_history`(module 소유라면
`flyway_schema_history_{module}`)를 함께 확인한다.

### dangling venue_id 점검 (ADR 0006)

`show` V8이 `SHOWS.venue_id`의 옛 cross-module FK를 제거하므로, DB 수준에서는 더 이상 존재하지
않는 Venue를 가리키는 `venue_id`를 막지 않는다. `Show.venueId`는 필수값이라 단건 조회는 그런 row를
만나면 venue의 not-found(404)를 그대로 내보낸다 — 목록 조회만 `getSummaries`가 해당 show의 표시값을
비우고 넘어간다. 배포 후 다음 쿼리로 실제로 그런 row가 생기지 않았는지 주기적으로 확인한다.

```sql
SELECT id FROM SHOWS WHERE venue_id NOT IN (SELECT id FROM VENUES);
```

결과가 있으면 애플리케이션 오류가 아니라 데이터 정합성 문제다 — 해당 Show의 `venue_id`를 바로잡거나
Venue 데이터를 복구한다.

local·dev 프로파일은 H2 file DB(`~/ticket-local`)의 스키마를 Flyway로 만들고 Hibernate `validate`로
확인한다. 빈 DB면 `__root` V1부터 모든 migration을 적용하고, 이미 있으면 새 파일만 적용한다. 초기
데이터는 기동에 포함되지 않으므로 처음 한 번 `seedLocal`을 실행한다([seed/README.md](../seed/README.md)).

H2의 Oracle 호환 모드는 `DATE`를 `TIMESTAMP(0)`으로 저장한다. 기본 `H2Dialect`는 이것을 `LocalDate`
매핑과 다르다고 판정하므로 local·dev는 `H2OracleModeDialect`로 두 타입을 같게 본다.

`ddl-auto: create` 시절에 만든 로컬 H2 파일에는 Flyway 이력이 없다. 그 파일로 기동하면 Modulith가
version `0` baseline을 만든 뒤 V1이 이미 있는 테이블과 부딪혀 실패한다. 서버를 끄고
`~/ticket-local*.db` 파일을 지운 뒤 다시 기동하고 `seedLocal`을 실행한다.

`__root` V1을 고친 뒤(2026-09-29 보조 인덱스 제거)에는 그 전에 V1을 실행한 로컬 H2 파일이 Flyway checksum
검증에서 실패한다. 같은 방법으로 파일을 지우고 다시 만든다.

## 배포 workflow

GitHub Actions CI(`ci.yml`)는 root project 하나만 있는 단일 Gradle build로 전체 테스트를 통과한
뒤 bootJar를 만든다. `seed/` 소스 집합의 `seedTest`도 명시해서 돌리고,
`verifySeedNotInBootJar`는 만들어진 jar에 시드 산출물이 섞이지 않았는지 확인한다.

```bash
./gradlew clean spotlessCheck compileJava architectureTest test seedTest bootJar verifySeedNotInBootJar
```

`.github/workflows/deploy.yml`은 `master` push에서 위 CI(`ci.yml`)를 호출해 통과한 jar를 받아
Docker 이미지를 빌드하고 배포한다. `bootstrap/build/libs` 경로는 더 이상 없다 — 산출물은 루트
`build/libs/*.jar`다.

서버에서 `docker compose up` 뒤 `ticket-be` 컨테이너 안의 `/actuator/health`가 5분 안에 UP이 되는지
확인한다. UP이 되지 않으면 배포 직전에 돌던 이미지로 되돌리고 job을 실패시킨다. nginx는 Core
컨테이너가 새로 만들어졌을 때만 재시작한다. 서버의 `docker-compose.yml`은 저장소 밖에 있으므로
컨테이너 이름(`ticket-be`), 컨테이너 안 포트(8080), `DOCKER_IMAGE` 변수는 workflow 주석에 적은
가정이다. compose가 바뀌면 workflow도 함께 고친다.

관련 파일:

- `.github/workflows/ci.yml`(구조 테스트를 먼저 돌린 뒤 전체 테스트 + `bash scripts/check-docs.sh`)
- `.github/workflows/deploy.yml`
- `Dockerfile`(`build/libs/*.jar`를 `app.jar`로 복사. plain `jar` task는 꺼서 bootJar 하나만 남는다)
- `.dockerignore`(build context에 bootJar만 보낸다)

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
[core-booking-lifecycle.md](core-booking-lifecycle.md#이벤트-재시도와-보정)를 본다.

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

Redis 만료 구독용 `redisExpirationSubscriptionExecutor`는 worker 1~2개, handler용
`redisExpirationTaskExecutor`는 worker 2개·queue 256개·공유 permit 2개로 설정돼 있다.
queue가 가득 차면 수신 스레드도 같은 permit으로 진입한다. queued 값이 256에 오래
머물거나 queue 포화 경고가 반복되면 이전 회차 작업이 현재 부하와 겹친 것이다.
`OrderExpirationTrigger`는 `worker.order-expiration.fixed-delay`(기본 5분)마다 보정을 시작하며
`worker.enabled=false`면 등록되지 않는다. 이벤트 리스너
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

## 이벤트 publication 조사와 재처리

`application.yml`은 publication 완료 기록을 archive하고 재시작 시 미완료 이벤트를 일괄 재발행하지 않는다. staleness 확인은 1분 간격이며 published 5분, processing·resubmitted 10분이 기준이다. `EventPublicationMaintenance`는 실패 건을 1분마다 batch 100건·동시 4건으로 재제출하고 `completionAttempts <= 10`만 자동 대상으로 삼는다. 완료 archive는 매일 03:00 KST에 30일 이전 기록을 정리한다. 실제 값 변경 시 설정과 구현을 함께 확인한다.

재시도 상한을 넘긴 `ERROR` 로그와 `EVENT_PUBLICATION`/`EVENT_PUBLICATION_ARCHIVE`의
`COMPLETION_ATTEMPTS > 10`을 감시한다. `event_type`·`serialized_event`의 `orderId`/`holdKey`를
주문·좌석 상태와 대조해 코드 오류와 Redis 장애를 조사한다.

저장소의 root V8은 두 테이블의 `serialized_event`를 H2 `VARCHAR(255)`, Oracle
`VARCHAR2(255 CHAR)`로 정의했다. 이 크기에서는 실제 `OrderTerminated` 직렬화 결과의 publication
**저장이 실패할 수 있음**을 `EventPublicationSerializedEventLengthTest`가 확인한다. root V9 migration은
각각 H2 `VARCHAR(4000)`, Oracle `VARCHAR2(4000 CHAR)`로 확장한다. 환경별 적용 여부는 Flyway 이력과
실제 컬럼 정의를 확인해야 하며, 저장소에 V9 파일이 있다는 사실만으로 운영 적용을 단정하지 않는다.
과거 장애를 조사할 때는 당시 스키마와 publication 저장 실패를 먼저 확인한다. 기존 payload가
잘려 저장됐을 가능성은 별도 데이터 근거가 필요한 조사 가설이다.

원인이 해소되기 전에는 재제출을 강행하지 않는다. 이 저장소에는 재처리 전용 endpoint가 없다. DB 직접 수정 또는 임시 운영 스크립트가 필요할 수 있으나, 검증된 자동 복구 절차로 제공하지 않는다. 적용 전 대상 publication과 현재 주문·hold 상태, 백업·재시도·중복 처리 영향을 확인하고 운영 절차를 별도로 승인받는다. 업무 처리·멱등성 범위는 [예매 수명주기](core-booking-lifecycle.md#이벤트-재시도와-보정)를 본다.

## 부하 결과 관측

부하 목적·격리·판정은 [testing.md](testing.md#core-부하-검증), Gatling 옵션과 리포트 원본은 형제 [gatling-test README](https://github.com/ticket-project/gatling-test/blob/main/README.md)를 따른다. 로컬 workspace에서는 ticket-core와 나란히 둔 `../gatling-test`에서 실행한다. 개별 측정은 Issue/PR과 원본 리포트에 남긴다. 부하 잔재와 비추적 결과 파일은 사용자 확인 없이 지우거나 옮기지 않는다.

## 운영 반영 시 주의점

- 운영 DB는 자동 DDL을 사용하지 않는다.
- DB 컬럼명, enum, 상태 모델 변경은 마이그레이션 계획을 함께 작성하고, 어느 module 소유
  migration 폴더에 둘지 먼저 정한다.
- Redis key, TTL, expiration listener 변경은 장애 복구와 scheduler 보정 흐름까지 같이 검토한다.
- 인증/인가 변경은 공개 API 노출 여부와 토큰 만료/재발급 흐름을 함께 확인한다.
- 다중 좌석 주문 트래픽을 늘리기 전에는 환경별 publication 컬럼과 V9 적용 여부를
  [이벤트 publication 조사](#이벤트-publication-조사와-재처리)의 기준으로 확인한다.
