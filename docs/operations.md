# 운영 절차

배포·DB 적용·Redis 전환·관측·장애 조사의 절차를 적는다. 프로파일 설정 값은 `application*.yml`, 배포 절차의 실제
내용은 `.github/workflows/deploy.yml`이 원본이라 여기 옮기지 않는다.

## 프로파일

- **local**: PostgreSQL과 Redis는 `docker-compose-local.yml`로 띄운다. 접속 정보가 기본값과 다르면
  `SPRING_DATASOURCE_*`로 지정한다. 스키마는 운영과 같은 PostgreSQL migration으로 만든다. 초기 데이터는 기동
  때 넣지 않으므로 처음 한 번 `seedLocal`을 실행한다.
- **prod**: AWS RDS PostgreSQL. 초기 데이터는 기동 시 넣지 않는다. 테이블 생성(배포/Flyway)과 데이터 적재(`seedProd`)는 별개
  작업이다.

## AWS RDS PostgreSQL

자동화 검증 컨테이너(Testcontainers)와 `docker-compose-local.yml`은 PostgreSQL 18(`postgres:18`)을 쓴다. 운영 RDS의 실제
버전은 저장소에서 확인할 수 없으므로 RDS 콘솔이나 `SELECT version()`으로 확인하고, 메이저 버전이 다르면 자동화 검증
버전을 맞춘다.

서버의 저장소 밖 compose와 환경변수에는 다음 값을 설정한다. 비밀번호는 별도 secret으로 주입한다.

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS endpoint>:5432/ticket?sslmode=verify-full&sslrootcert=/app/certs/global-bundle.pem
SPRING_DATASOURCE_USERNAME=<애플리케이션 계정>
SPRING_DATASOURCE_PASSWORD=<비밀번호>
```

AWS RDS CA bundle을 컨테이너의 위 경로에 읽기 전용으로 마운트한다. `verify-full`은 CA와 endpoint 이름을
함께 확인한다([AWS SSL 안내](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html)).
`seedProd`는 같은 JDBC URL을 사용하되 `sslrootcert`를 실행 PC에서 읽을 수 있는 인증서 경로로 바꾼다.
애플리케이션 실행 서버에서 RDS로 접속할 수 있게 VPC·보안 그룹·5432 접근과 DB 계정의 schema 생성·DDL 권한을
준비한다.

Oracle에서 RDS PostgreSQL로의 전환(데이터 이관 절차 포함)은 2026-10에 끝났다. 당시 절차는 git 이력에 있다.

## Admission token 검증

기존 클라이언트와 호환되는 초기 배포에서는 환경 변수 `ADMISSION_TOKEN_ENFORCEMENT_ENABLED=false`로 검증을
비활성화한다. Queue Server와 클라이언트의 admission token 전달이 모두 준비된 뒤에만 `true`로 전환한다.
비활성 상태에서도 회차 판매 정책으로 Queue 필요 여부는 판정하고, admission token 검증만 건너뛴다(`JwtAdmissionVerifier`).

admission token의 서명 secret, issuer, audience는 Core와 `ticket-queue` 두 저장소 설정이 일치해야 한다. 한쪽만
바꾸면 검증 실패가 부하 문제가 아니라 설정 불일치로 발생한다.

Core는 주문 생성·취소·만료 시 Queue Server에 session 완료 요청을 보내지 않는다. Queue 입장 후 shopping
session은 Queue Server의 TTL로 만료되므로, 운영 중에는 Queue의 entered marker 수와 TTL 만료 추이를
관측한다. 조기 반환이나 동시 active session 상한은 별도 프로토콜 설계 후 도입한다.

## Redis 운영과 전환

- 운영 Redis에서 `KEYS`를 사용하지 않는다. 필요한 조회는 인덱스(Sorted Set 등)로 만든다.
- key 형식·인덱스 구조를 바꾸면 기존 key가 남아 있는 상태의 전환 절차(신규 요청 차단 후 TTL 이상 대기,
  `SCAN`으로 옛 key가 0개인지 확인, 배포, 재개 — 무중단이면 백필 또는 호환 조회)를 함께 설계한다.
- TTL, expiration listener, scheduler 누락 복구 중 하나만 바꾸지 않는다 — 세 경로가 같은 정합성을 함께 지킨다.
- key·TTL·리스너 변경은 장애 복구와 scheduler 누락 복구 흐름까지 같이 검토한다.

## DB 마이그레이션 적용

운영 DB는 자동 DDL을 쓰지 않는다. 새 module 폴더를 처음 배포하거나 새 DB에 처음 적용하기 전에는 다음을 확인한다.

1. 운영 DB 백업 또는 복구 지점을 확보한다.
2. 애플리케이션 DB 계정이 `flyway_schema_history`(와 module별 `flyway_schema_history_{module}`) 테이블을 생성하고
   이후 DDL을 실행할 권한이 있는지 확인한다.
3. 기동 후 해당 이력 테이블에 version `0` baseline과 적용된 버전이 모두 `success`로 기록됐는지 확인한다
   (`SELECT version, success FROM flyway_schema_history_booking`).

배포 전 점검:

- booking V4(`uk_orders_pending_member_performance`)가 아직 적용되지 않은 DB라면 같은 회원·회차의 PENDING 주문이 둘 이상
  있는지 확인한다. 결과가 0건이어야 유일 인덱스가 만들어진다. 중복이 있으면 배포를 중단하고 주문·hold 상태를 확인해
  정리한다. migration에서 중복 행을 임의로 바꾸거나 삭제하지 않는다.

  ```sql
  SELECT member_id, performance_id, COUNT(*) FROM orders WHERE status = 'PENDING' GROUP BY member_id, performance_id HAVING COUNT(*) > 1;
  ```

- `SHOWS.venue_id`는 DB FK가 없어 존재하지 않는 Venue를 가리킬 수 있다. 단건 조회는 그런 row를 만나면 venue의
  not-found(404)를 내보내고 목록 조회만 표시값을 비우고 넘어간다. 배포 후에도 주기적으로 확인한다. 결과가 있으면
  애플리케이션 오류가 아니라 데이터 정합성 문제다 — 해당 Show의 `venue_id`를 바로잡거나 Venue 데이터를 복구한다.

  ```sql
  SELECT id FROM SHOWS WHERE venue_id NOT IN (SELECT id FROM VENUES);
  ```

PostgreSQL에서 migration이 실패한 뒤 재시도하기 전에는 실제 schema와 `flyway_schema_history`(module 소유라면
`flyway_schema_history_{module}`)를 함께 확인한다.

## 배포

`master` push는 `deploy.yml`이 CI(`ci.yml`)를 통과시킨 jar로 Docker 이미지를 만들어 배포한다. `workflow_dispatch`로도
시작할 수 있다. 서버에서 `docker compose up` 뒤 `ticket-be` 컨테이너의 `/actuator/health`가 5분 안에 UP이 되지 않으면
배포 직전에 돌던 이미지로 되돌리고 job을 실패시킨다. 되돌린 이미지도 UP이 되지 않으면 서버를 직접 확인한다.

서버의 `docker-compose.yml`은 저장소 밖에 있다. 컨테이너 이름(`ticket-be`), 컨테이너 안 포트(8080), `DOCKER_IMAGE` 변수는
workflow 주석에 적은 가정이다. 배포 script는 그 밖에도 같은 compose가 `FRONTEND_DOCKER_IMAGE`로 `ticket-fe`를,
`DD_VERSION`으로 Core 버전을 받는다고 보고, Core 컨테이너가 새로 만들어지면 `ticket-nginx`를 재시작한다. compose가 바뀌면
workflow도 함께 고친다.

## Core 용량 관측

Core는 `/actuator/prometheus`에서 용량 판정에 필요한 메트릭을 노출한다. 부하 테스트 중에는 다음을 같은 시간축으로 본다.

- `http_server_requests_seconds_bucket/count`: URI·method·status별 처리량과 p95/p99
- `hikaricp_connections_active/pending/max/min`: DB connection pool 사용량과 대기
- `tomcat_threads_busy_threads/current_threads/config_max_threads`: 요청 스레드 사용량과 상한
- `jvm_gc_pause_seconds`, `jvm_memory_used_bytes`, `process_cpu_usage`: JVM·CPU 포화 여부
- `executor_active_threads`, `executor_queued_tasks`: background worker 사용량과 적체. Redis 만료 처리 executor는
  `name` 태그로 구분한다.

**주문 커밋 후 이벤트 리스너(`BookingEventListeners`)에는 이름이 붙은 전용 executor가 없다.** 앱이 `ThreadPoolTaskExecutor`
빈(`RedisExpirationListenerConfig`)을 등록하므로 Spring Boot 기본 비동기 executor(`applicationTaskExecutor`)는 만들어지지
않는다(Boot는 `Executor` 빈이 없거나 `spring.task.execution.mode=force`일 때만 만든다). 측정 스레드 덤프에서 리스너는
이벤트마다 새 스레드를 만드는 `SimpleAsyncTaskExecutor-*` 스레드로 돌았고 동시 실행 수에 상한이 없었다(gatling-test
capacity-log P-015). 이 스레드는 요청과 같은 Hikari 풀에서 연결을 빌리므로 부하가 높으면 요청 스레드와 연결을 다툰다.
위 executor 태그에는 잡히지 않는다. 이 경로의 적체는 `EVENT_PUBLICATION` 테이블의 PENDING/PROCESSING 건수와
`EventPublicationMaintenance`가 남기는 재시도 초과 로그로 확인한다.

**로컬 캐시(Caffeine)는 값을 늦게 반영한다.** 캐시 이름·유지 시간·상한의 원본은 `CacheConfig`와 `CacheNames`다.

| 캐시 | 담는 값 | 유지 시간 | 상한 | 반영 지연 |
| --- | --- | --- | --- | --- |
| `salesPolicy` | booking 회차 판매 정책(`PerformanceSaleFinder`) | 쓴 뒤 1초 | 1,000개 | 판매 기간·대기열 정책 변경이 서버마다 최대 1초 늦다 |
| `performanceDisplay` | show 회차 표시값: 공연·회차, 공연장 이름, 회차 시작 시각, 등급(`PerformanceSaleHeaderReader`) | 쓴 뒤 10분 | 1,000개 | 주문 표시값과 잔여석 조회의 등급 정보에 최대 10분 옛 값이 남는다 |

두 캐시는 `recordStats()`를 켰지만 `/actuator/prometheus`에 `cache_*` 지표가 나오는지는 실행으로 확인하지 않았다.
운영 측정에서 판매 정책 캐시(`sync = true`)가 채워지기를 기다리는 주문 스레드가 연결을 쥐고, 캐시를 채우는 스레드는 연결을
기다려 Core가 멈춘 사례가 있다(gatling-test capacity-log P-022, 결정 대기). 캐시가 붙은 경로에서 Hikari pending이 길게
이어지면 이 경합을 먼저 의심한다.

모든 메트릭에는 `service`, `environment`, `version` 태그가 붙는다. 운영 task에는 `DD_SERVICE=ticket-core`,
`DD_ENV=prod`, `DD_VERSION=<배포버전>`을 동일하게 주입해야 task별 비교와 배포 전후 비교가 가능하다. 운영 서버의 실제
`DD_SERVICE` 값은 저장소에서 확인할 수 없고, core-capacity skill의 [운영 측정](../.agents/skills/core-capacity/production.md#진단)은
Datadog 서비스 이름을 `ticket-be`로 적고 있다. 아래 쿼리를 쓰기 전에 실제 `service` 태그를 확인한다.

```promql
histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{service="ticket-core"}[1m])))
sum(rate(http_server_requests_seconds_count{service="ticket-core",status=~"5.."}[1m]))
max(hikaricp_connections_pending{service="ticket-core"})
max(tomcat_threads_busy_threads{service="ticket-core"}) / max(tomcat_threads_config_max_threads{service="ticket-core"})
```

Hikari pending이 0보다 커지면 요청이 DB 연결을 빌리지 못하고 기다리는 상태다. pending이 0이어도 이미 빌린 연결이 DB lock에서
멈출 수 있으므로 DB wait를 별도로 확인한다. Redis 만료 처리 executor는 worker 2개에 큐 상한이 없어(`Integer.MAX_VALUE`) 포화
경고 없이 작업이 메모리에 쌓인다. queued 값이 줄지 않고 계속 늘면 이전 회차 작업이 현재 부하와 겹친 것이다. 이벤트 후속 처리가 누락되거나 실패해도
`EventPublicationMaintenance`가 주기적으로 재처리해 복구하지만, backlog가 해소되기 전에는 다음 부하를 넣지 않는다.

DB lock wait는 Actuator만으로 볼 수 없다. Datadog DBM 또는 `pg_monitor` 권한이 있는 별도 관측 계정에서 다음 정보를
수집한다. 다른 세션 조회 권한은 애플리케이션 계정에 추가하지 말고 관측 전용 계정에만 부여한다.

```sql
SELECT count(*) AS blocked_sessions FROM pg_stat_activity WHERE cardinality(pg_blocking_pids(pid)) > 0;

SELECT wait_event_type, wait_event, count(*) AS waiting_sessions, max(now() - query_start) AS oldest_query_age
FROM pg_stat_activity
WHERE state = 'active' AND wait_event_type IS NOT NULL
GROUP BY wait_event_type, wait_event
ORDER BY waiting_sessions DESC;
```

## 이벤트 publication 조사와 재처리

재시도 횟수 상한(`EventPublicationMaintenance`)을 넘긴 `ERROR` 로그와 `EVENT_PUBLICATION`/
`EVENT_PUBLICATION_ARCHIVE`의 `COMPLETION_ATTEMPTS`가 상한을 넘은 건을 감시한다. `event_type`·`serialized_event`의
`orderId`/`holdKey`를 주문·좌석 상태와 대조해 코드 오류와 Redis 장애를 조사한다. 과거 장애를 조사할 때는 당시 스키마의
publication 저장 실패를 먼저 확인한다. 기존 payload가 잘려 저장됐을 가능성은 별도 데이터 근거가 필요한 가설이다.

원인이 해소되기 전에는 재처리를 강행하지 않는다. 이 저장소에는 재처리 전용 endpoint가 없다. DB 직접 수정 또는 임시 운영
스크립트가 필요할 수 있으나 검증된 자동 복구 절차로 제공하지 않는다. 적용 전 대상 publication과 현재 주문·hold 상태,
백업·재시도·중복 처리 영향을 확인하고 운영 절차를 별도로 승인받는다.

## 인증·인가 변경 시

공개 API 노출 여부와 토큰 만료/재발급 흐름을 함께 확인한다.
