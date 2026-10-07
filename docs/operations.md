# 운영 절차

배포·DB 적용·Redis 전환·관측·장애 조사의 절차를 적는다. 프로파일 설정 값은 `application*.yml`, 배포 절차의 실제
내용은 `.github/workflows/deploy.yml`이 원본이라 여기 옮기지 않는다.

## 프로파일

- **local**: Docker PostgreSQL. `docker compose -f compose.local.yml up -d`로 DB와 Redis를 실행한다.
  스키마는 운영과 같은 PostgreSQL migration으로 만들며 데이터는 볼륨에 보존한다. 초기 데이터는 기동
  때 넣지 않으므로 처음 한 번 `seedLocal`을 실행한다. 기존 H2 파일은 자동 변환하거나 삭제하지 않는다.
- **prod**: AWS RDS PostgreSQL. 초기 데이터는 기동 시 넣지 않는다. 테이블 생성(배포/Flyway)과 데이터 적재(`seedProd`)는 별개
  작업이다.

## AWS RDS PostgreSQL 전환

로컬과 검증 컨테이너는 PostgreSQL 18을 사용한다. RDS에서도 같은 메이저 버전을 선택하고, 실제 리전에서
지원하는 마이너 버전은 생성 전에 확인한다. 기존 운영 Oracle의 데이터는 이 코드 변경으로 자동 이관되지 않는다.

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
Oracle Wallet과 `TNS_ADMIN`은 PostgreSQL 실행에 사용하지 않는다. 애플리케이션 실행 서버에서 RDS로
접속할 수 있게 VPC·보안 그룹·5432 접근과 DB 계정의 schema 생성·DDL 권한을 준비한다.

전환 순서:

1. 기존 Oracle의 백업과 데이터 이관 범위를 확정하고 새 RDS의 빈 애플리케이션 schema를 준비한다.
2. 새 코드로 PostgreSQL migration을 먼저 적용한다. PostgreSQL V1은 현재 스키마를 바로 만들므로
   Oracle의 Flyway 이력 테이블이나 과거 DDL을 가져오지 않는다.
3. 기존 업무 데이터를 옮길 경우 쓰기와 주문/hold 만료·이벤트 재처리를 중지하고 최종 데이터를 동기화한다.
   명시적 ID, UUID, 날짜와 시각, 긴 설명 문자열, 미완료 publication과 archive를 변환·검증한다.
   저장된 이벤트 클래스의 옛 `com.ticket.booking.OrderStarted` 값은 `com.ticket.booking.OrderCreated`로 변환한다.
4. 이관한 identity 컬럼마다 시퀀스를 기존 최대 ID 이상으로 맞춘다. `seedLocal`/`seedProd`는 성공 시
   이 작업을 자동 수행하지만 외부 데이터 이관 도구에는 자동 적용되지 않는다. seed는 이관 도구가 아니다.
5. 행 수·중복·참조·금액·날짜·미완료 이벤트를 대조하고 로그인, 공연 조회, 선점, 주문 생성/취소/만료,
   publication 처리와 `/actuator/health`를 확인한 뒤 트래픽을 연다. Redis에 남은 hold·대기열 상태도 DB와 맞춘다.

DB 전환은 일반 이미지 배포와 별도로 계획한다. 배포 workflow의 이전 이미지 복원만으로 DB 연결·데이터가
Oracle로 돌아가지는 않는다. PostgreSQL에서 새 쓰기가 발생한 뒤에는 이전 DB와 데이터가 달라지므로,
복귀 시 데이터 조정과 서버 환경변수 복원까지 포함해야 한다.

## Admission token 검증

기존 클라이언트와 호환되는 초기 배포에서는 환경 변수 `ADMISSION_TOKEN_ENFORCEMENT_ENABLED=false`로 검증을
비활성화한다. Queue Server와 클라이언트의 admission token 전달이 모두 준비된 뒤에만 `true`로 전환한다.
비활성 상태에서는 회차의 Queue 정책과 admission token을 조회하거나 검증하지 않는다.

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

- `PERFORMANCE_SEATS`에 `(performance_id, seat_id)` 중복이 있는지 확인한다. 결과가 0건이어야 유니크 인덱스
  migration이 적용된다. 중복이 있으면 배포를 중단하고 `ORDER_SEATS.performance_seat_id` 등 참조 데이터를 확인해 대표 행을
  결정한 뒤 정리한다. migration에서 중복 행을 임의 삭제하지 않는다.

  ```sql
  SELECT performance_id, seat_id, COUNT(*) FROM performance_seats GROUP BY performance_id, seat_id HAVING COUNT(*) > 1
  ```

- `SHOWS.venue_id`는 DB FK가 없어 존재하지 않는 Venue를 가리킬 수 있다. 단건 조회는 그런 row를 만나면 venue의
  not-found(404)를 내보내고 목록 조회만 표시값을 비우고 넘어간다. 배포 후에도 주기적으로 확인한다. 결과가 있으면
  애플리케이션 오류가 아니라 데이터 정합성 문제다 — 해당 Show의 `venue_id`를 바로잡거나 Venue 데이터를 복구한다.

  ```sql
  SELECT id FROM SHOWS WHERE venue_id NOT IN (SELECT id FROM VENUES);
  ```

- `EVENT_PUBLICATION`/`EVENT_PUBLICATION_ARCHIVE`의 `serialized_event` 컬럼 정의(PostgreSQL `__root` V1의 VARCHAR(4000))를
  환경별 Flyway 이력과 실제 컬럼으로 확인한다. 저장소에 파일이 있다는 사실만으로 운영 적용을 단정하지 않는다. V9 이전
  크기에서는 `OrderTerminated` publication 저장이 실패할 수 있으므로 다중 좌석 주문 트래픽을 늘리기 전에 확인한다.
- 과거 Oracle/H2의 `__root` V10은 저장된 publication의 `event_type`을 `com.ticket.booking.OrderStarted`에서 `OrderCreated`로 옮겼다. 이관 뒤
  `OrderStarted`를 쓰던 이전 이미지로 되돌리면 그 행의 클래스를 읽지 못한다. 되돌려야 하면 미완료 `OrderCreated` 행이 없는지
  먼저 확인하거나 `event_type`을 옛 이름으로 되돌린다.

PostgreSQL에서 migration이 실패한 뒤 재시도하기 전에는 실제 schema와 `flyway_schema_history`(module 소유라면
`flyway_schema_history_{module}`)를 함께 확인한다.

## 배포

`master` push는 `deploy.yml`이 CI(`ci.yml`)를 통과시킨 jar로 Docker 이미지를 만들어 배포한다. `workflow_dispatch`로도
시작할 수 있다. 서버에서 `docker compose up` 뒤 `ticket-be` 컨테이너의 `/actuator/health`가 5분 안에 UP이 되지 않으면
배포 직전에 돌던 이미지로 되돌리고 job을 실패시킨다. 되돌린 이미지도 UP이 되지 않으면 서버를 직접 확인한다.

서버의 `docker-compose.yml`은 저장소 밖에 있다. 컨테이너 이름, 컨테이너 안 포트, `DOCKER_IMAGE` 변수는 workflow 주석에
적은 가정이므로, compose가 바뀌면 workflow도 함께 고친다.

## Core 용량 관측

Core는 `/actuator/prometheus`에서 용량 판정에 필요한 메트릭을 노출한다. 부하 테스트 중에는 다음을 같은 시간축으로 본다.

- `http_server_requests_seconds_bucket/count`: URI·method·status별 처리량과 p95/p99
- `hikaricp_connections_active/pending/max/min`: DB connection pool 사용량과 대기
- `tomcat_threads_busy_threads/current_threads/config_max_threads`: 요청 스레드 사용량과 상한
- `jvm_gc_pause_seconds`, `jvm_memory_used_bytes`, `process_cpu_usage`: JVM·CPU 포화 여부
- `executor_active_threads`, `executor_queued_tasks`: background worker 사용량과 적체. Redis 만료 처리 executor는
  `name` 태그로 구분한다.

**주문 커밋 후 이벤트 리스너(`BookingEventListeners`)에는 이름이 붙은 전용 executor가 없다.** Spring Boot 기본 비동기
executor(`applicationTaskExecutor`)를 쓰므로 위 executor 태그에 잡히지 않는다. 이 경로의 적체는 `EVENT_PUBLICATION`
테이블의 PENDING/PROCESSING 건수와 `EventPublicationMaintenance`가 남기는 재시도 초과 로그로 확인한다.

모든 메트릭에는 `service`, `environment`, `version` 태그가 붙는다. 운영 task에는 `DD_SERVICE=ticket-core`,
`DD_ENV=prod`, `DD_VERSION=<배포버전>`을 동일하게 주입해야 task별 비교와 배포 전후 비교가 가능하다.

```promql
histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{service="ticket-core"}[1m])))
sum(rate(http_server_requests_seconds_count{service="ticket-core",status=~"5.."}[1m]))
max(hikaricp_connections_pending{service="ticket-core"})
max(tomcat_threads_busy_threads{service="ticket-core"}) / max(tomcat_threads_config_max_threads{service="ticket-core"})
```

Hikari pending이 0보다 커지면 요청이 DB 연결을 빌리지 못하고 기다리는 상태다. pending이 0이어도 이미 빌린 연결이 DB lock에서
멈출 수 있으므로 DB wait를 별도로 확인한다. Redis 만료 처리 executor의 queued 값이 상한에 오래 머물거나 queue 포화
경고가 반복되면 이전 회차 작업이 현재 부하와 겹친 것이다. 이벤트 후속 처리가 누락되거나 실패해도
`EventPublicationMaintenance`가 주기적으로 재처리해 복구하지만, backlog가 해소되기 전에는 다음 부하를 넣지 않는다.

Oracle lock wait는 Actuator만으로 볼 수 없다. Oracle exporter·Datadog DBM 또는 DBA 권한이 있는 별도 관측 계정에서 다음
정보를 수집한다. `v$session` 조회 권한은 애플리케이션 계정에 추가하지 말고 관측 전용 계정에만 부여한다.

```sql
SELECT COUNT(*) AS blocked_sessions FROM v$session WHERE blocking_session IS NOT NULL;

SELECT event, COUNT(*) AS waiting_sessions, MAX(seconds_in_wait) AS max_seconds_in_wait
FROM v$session
WHERE state = 'WAITING' AND wait_class <> 'Idle'
GROUP BY event
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
