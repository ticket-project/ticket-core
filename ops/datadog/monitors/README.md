# Datadog 알림(모니터) 정의

이 폴더의 JSON 하나가 알림 하나다. Datadog 화면 **Monitors > New Monitor > Import from JSON**에 파일 내용을 붙여 넣어 만든다.
옛 알림 원본은 `../backup-2026-11-12/monitors/<id>.json`에 있다.

공통 설정:

- 서버(EC2·RDS)는 평소 꺼 두므로 모든 알림이 `notify_no_data: false`다. 꺼져 있는 동안 울리지 않는다.
- 받는 곳(@핸들)은 아직 없다. 메시지에는 뜻과 먼저 볼 곳만 적었다.
- 태그는 `service:…`, `env:prod`, `managed-by:ops-datadog`.
- Core 호스트 범위는 `host:ticket-core-prod`다. `DD_HOSTNAME`을 적용하기 전에는 옛 이름 `i-09739eeb559db0d1d`로 들어오므로 이 범위에 데이터가 없다.
- AWS(CloudWatch) 지표는 10~15분 늦게 들어오므로 `evaluation_delay: 900`을 둔다.

## 옛 알림 처리

| 옛 id | 옛 이름 | 처리 | 새 파일 |
| --- | --- | --- | --- |
| 20935492 | [ticket-queue] Redis cluster state degraded | 삭제(Azure 클러스터 Redis 시절, 지금은 단일 서버) | - |
| 20935493 | [ticket-queue] Redis cluster slots failing | 삭제(같은 이유) | - |
| 20935494 | [ticket-queue] Redis ping latency high | 삭제(같은 이유) | - |
| 20935495 | [ticket-queue] Redis memory fragmentation high | 삭제(같은 이유) | - |
| 20935496 | [ticket-queue] Redis keys evicted | 삭제(같은 이유) | - |
| 20935497 | [ticket-queue] Redis rejected connections | 삭제(같은 이유). 단일 서버용으로 새로 만듦 | `queue-redis-rejected-connections.json` |
| 21661952 | [ticket-core][DB] Autonomous DB CPU high | 삭제(Oracle `oci.*`, RDS로 이전) | - |
| 21661953 | [ticket-core][DB] Session utilization high | 삭제(같은 이유) | - |
| 21661954 | [ticket-core][DB] Storage utilization high | 삭제(같은 이유) | - |
| 21661955 | [ticket-core][DB] Blocking sessions detected | 삭제(같은 이유) | - |
| 21661956 | [ticket-core][DB] Queued statements detected | 삭제(같은 이유) | - |
| 21661957 | [ticket-core][DB] Failed connections detected | 삭제(같은 이유) | - |
| 21661958 | [ticket-core][DB] Server-dropped connections detected | 삭제(같은 이유) | - |
| 21661960 | [ticket-core][DB Path] Hikari pending connections | 고침: 한 번 0 초과 → 1분 내내 0 초과 | `core-hikari-pending.json` |
| 21661961 | [ticket-core][DB Path] Hikari connection timeouts | 고침: 이름·태그만 | `core-hikari-timeout.json` |
| 21661962 | [ticket-core][DB Path] PostgreSQL APM latency high | 고침: 기준 0.25초 → 0.1초(경고 0.05초). 평소 1~6ms | `core-postgres-apm-latency.json` |
| 21661963 | [ticket-core] Telemetry missing | 고침: no-data 방식은 서버를 끄면 울리므로, 호스트가 켜진 상태에서 운영 컨테이너 6개 중 일부가 안 떠 있는지로 바꿈 | `core-containers-missing.json` |
| 21661964 | [ticket-core] HTTP 5xx burst | 고침: 이름·태그만 | `core-http-5xx-burst.json` |
| 21662062 | [ticket-core] Container OOM detected | 고침: ticket-be만 → Core 호스트 컨테이너별. `oom_events`는 gauge라 `as_count` 대신 `max` | `core-container-oom.json` |
| 21662064 | [ticket-core] Error logs burst | 고침: 이름·태그만 | `core-error-logs-burst.json` |
| 21662239 | [ticket-core] Datadog Agent check degraded | 고침: 호스트 이름, no-data 끔, 깨진 메시지 다시 씀 | `core-agent-check-degraded.json` |
| 21662361 | [ticket-core][Host] Disk utilization high | 고침: 호스트 이름, no-data 끔 | `core-host-disk.json` |
| 21662364 | [ticket-core][Host] CPU saturation | 고침: 호스트 이름, no-data 끔 | `core-host-cpu.json` |
| 21662365 | [ticket-core][Host] Memory low | 고침: 호스트 이름, no-data 끔 | `core-host-memory.json` |
| 21662366 | [ticket-core][Host] Normalized load high | 고침: 호스트 이름, no-data 끔 | `core-host-load.json` |
| 21662367 | [ticket-core] Container restart loop | 고침: `container.restarts`는 누적 gauge라 `sum.as_count` → 10분 증가분(`change`), 컨테이너별 | `core-container-restart-loop.json` |

## 새 알림

| 파일 | 이름 | 목적 |
| --- | --- | --- |
| `db-rds-cpu-credit-low.json` | [db] RDS CPU 크레딧 부족 | t4g.micro 크레딧이 바닥나 CPU가 기준선으로 묶이기 전에 안다 |
| `db-rds-free-storage-low.json` | [db] RDS 디스크 여유 부족 | 남은 디스크 2GiB 아래(경고 4GiB) |
| `db-rds-connections-high.json` | [db] RDS 연결 수 많음 | 정상 상한(Hikari 10 × 4대 = 40)을 넘는 연결(경고 50, 위험 70) |
| `db-rds-read-latency-high.json` | [db] RDS 읽기 지연 높음 | 디스크 읽기 평균 20ms 초과(경고 10ms) |
| `infra-rds-running.json` | [infra] RDS 켜짐 | 꺼진 지 7일 뒤 AWS 자동 시작을 알아챈다 |
| `infra-core-ec2-running.json` | [infra] Core EC2 켜짐 | 테스트 뒤 끄는 걸 잊은 경우를 알아챈다 |
| `queue-api-5xx.json` | [queue] API 5xx 급증 | 대기열 API 5분 5xx 10건 초과 |
| `queue-scheduler-stalled.json` | [queue] 대기 회차가 있는데 입장 0 | 대기 회차가 3분 내내 있는데 입장 허용이 0. scheduler가 죽어 지표가 끊기면 못 잡는다 |
| `queue-redis-memory-high.json` | [queue] Redis 메모리 높음 | maxmemory 없는 단일 Redis가 호스트 메모리 절반 초과 |
| `queue-redis-rejected-connections.json` | [queue] Redis 연결 거부 | 누적값 `redis.net.rejected`의 5분 증가분 |
| `loadtest-loadgen-cpu-high.json` | [loadtest] 부하 발생기 CPU 90% 이상 | 측정 숫자가 발생기 한계인지 가린다 |

## 아직 데이터가 안 들어오는 지표(공식 이름으로 씀)

최근 30일 Datadog에서 찾지 못했다. 서버를 켜고 수집이 시작되면 이름을 다시 확인한다.

- `aws.rds.cpucredit_balance`, `aws.rds.free_storage_space`, `aws.rds.database_connections`, `aws.rds.read_latency`, `aws.rds.cpuutilization`: 같은 기간 `aws.rds.total_storage_space`(태그 `dbinstanceidentifier:ticket-core-db`)만 들어왔다.
- `ticket_queue_api.http_server_requests_seconds.count`, `ticket_queue_scheduler.queue_scheduler_waiting_performances`, `ticket_queue_scheduler.queue_scheduler_admitted.count`: queue 서버 지표가 30일 안에 없다. OpenMetrics가 `_total` 카운터를 `.count`로 바꾸는 규칙(Core의 `ticket.logback_events.count`와 같음)으로 적었다.
- `host:ticket-core-prod`, `host:ticket-queue-prod`, `host:loadgen-1`: `DD_HOSTNAME` 적용 전이라 아직 없다.
