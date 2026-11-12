# 다음 부하 테스트 시작 순서서 (Datadog·RDS 관측)

서버(EC2·RDS)는 평소 꺼 두고 측정할 때만 켠다. 이 문서는 두 부분이다.

- **한 번만**: 다음 테스트 때 서버를 켠 직후, 측정 전에 한 번 한다. 관측 설정(RDS 로그·Enhanced Monitoring, Agent의 postgres·nginx 수집,
  호스트 이름)을 바꾼다. 끝나면 이 절은 다시 하지 않는다.
- **매번**: 측정할 때마다 한다.

**적용 상태(2026-11-12)**: 1·2·3·5·6단계는 적용했다(1단계 5번 forwarder 트리거 포함). 남은 것은 4단계 하나다.

- 4단계(queue)는 보류했다. queue EC2에 루트 볼륨이 없어서다. queue는 [Core 수용량](../../docs/core-capacity.md) 9단계에서만 쓰므로, 그때 4단계의 복원 절차부터 한다.

서버를 새로 만들거나 설정을 잃었을 때는 이 절을 처음부터 다시 따른다.

측정 절차 자체(준비·예열·사다리·판정)는 [core-capacity 스킬의 production.md](../../.agents/skills/core-capacity/production.md)가 원본이다.
운영 서버 쓰기(AWS 콘솔, compose·nginx 수정, DB 계정)는 사용자가 한다. Claude는 명령을 준비하고 결과를 함께 읽는다.
비밀값은 이 문서·명령 기록·채팅에 남기지 않는다. `<…>`는 그 자리에서 채운다.

정한 이름(2026-11-12):

| 대상 | 이름 |
| --- | --- |
| Core 호스트 | `ticket-core-prod` (EC2 `i-09739eeb559db0d1d`) |
| queue 호스트 | `ticket-queue-prod` |
| 부하 발생기 | `loadgen-1` |
| 로컬 PC | `ticket-local` |
| RDS | `ticket-core-db` (PostgreSQL 18.3, db.t4g.micro, ap-northeast-2) |
| APM 서비스 | `ticket-be`(Core), `ticket-queue-api`, `ticket-queue-scheduler` |
| 대시보드 | `[loadtest] 부하 테스트 한 화면` |

## 한 번만 (다음 테스트 때)

순서대로 한다. 1(재부팅)이 끝나야 2·3에서 DB에 붙을 수 있다.

### 1. RDS 파라미터 그룹·Enhanced Monitoring·로그 내보내기

**누가**: 사용자(AWS 콘솔, 서울 리전). **걸리는 시간**: 10~15분(재부팅 포함).

1. RDS `ticket-core-db`를 **시작**한다. 상태가 `사용 가능`이 될 때까지 기다린다.
2. 파라미터 그룹 `ticket-core-pg18`(family postgres18)은 2026-11-12에 아래 값으로 만들어 두었다(Claude, 저장 후 값 확인함).
   `track_io_timing`·`track_activity_query_size`·`log_checkpoints`는 postgres18 기본값이 이미 아래 값이라 바꾸지 않았다.

   | 파라미터 | 값 | 이유 |
   | --- | --- | --- |
   | `log_min_duration_statement` | `100` | 100ms 넘는 SQL을 로그로 |
   | `log_lock_waits` | `1` | `deadlock_timeout`(1초) 넘게 락을 기다리면 로그 |
   | `log_temp_files` | `0` | 정렬·해시가 디스크로 넘치면 전부 로그 |
   | `log_autovacuum_min_duration` | `1000` | 1초 넘는 autovacuum 로그 |
   | `log_checkpoints` | `1` | checkpoint 시점·쓰기량 |
   | `track_io_timing` | `1` | 쿼리별 I/O 시간(Performance Insights·`pg_stat_statements`) |
   | `track_activity_query_size` | `4096` | 긴 SQL이 잘리지 않게 |
   | `shared_preload_libraries` | `pg_stat_statements,pg_tle,auto_explain` | 기본값 `pg_stat_statements,pg_tle`를 **지우지 않고** 더했다 |
   | `auto_explain.log_min_duration` | `200` | 200ms 넘는 SQL의 실행 계획 로그 |
   | `auto_explain.log_analyze` | `0`(기본) | 켜면 모든 쿼리에 실측 비용이 붙어 t4g.micro에서 측정을 흔든다 |

3. `수정`에서 한 번에 바꾼다.
   - DB 파라미터 그룹: `default.postgres18` → `ticket-core-pg18`
   - 모니터링: Enhanced Monitoring 켜기, 단위 **1초**, 역할은 `기본값`(콘솔이 `rds-monitoring-role`을 만든다)
   - 로그 내보내기: **PostgreSQL 로그** 체크
   - 수정 예약: **즉시 적용**
4. 상태가 `사용 가능`으로 돌아오면 **재부팅**한다. `shared_preload_libraries`는 재부팅해야 들어간다.
5. Enhanced Monitoring 지표를 Datadog으로 보낸다. RDS는 이 지표를 CloudWatch Logs의 `RDSOSMetrics` 로그 그룹에 쓴다.
   PostgreSQL 로그 그룹은 Datadog forwarder가 자동으로 구독하지만, 2026-11-12 확인 결과 `RDSOSMetrics`는 구독하지 않았다.
   AWS 콘솔 Lambda → `datadogintegration-forwarderstack-…-forwarder-…` → **트리거 추가** → CloudWatch Logs →
   로그 그룹 `RDSOSMetrics`, 필터 이름은 아무거나, 필터 패턴은 비운다.

**확인**

- 콘솔 `구성` 탭의 파라미터 그룹 옆이 `동기화됨`(재부팅 전엔 `재부팅 보류 중`).
- 2단계의 psql에서:

  ```sql
  SHOW shared_preload_libraries;          -- pg_stat_statements,pg_tle,auto_explain
  SHOW log_min_duration_statement;        -- 100ms
  SHOW auto_explain.log_min_duration;     -- 200ms
  SHOW track_io_timing;                   -- on
  ```

- CloudWatch Logs에 `/aws/rds/instance/ticket-core-db/postgresql` 로그 그룹이 생겼다.
- Datadog Logs `@aws.awslogs.logGroup:RDSOSMetrics`에 1초마다 한 건씩 들어온다(5번을 한 뒤 몇 분 안). 안 보이면 forwarder의
  트리거 목록에 `RDSOSMetrics`가 있는지 본다.
- 이 로그는 forwarder가 지표(`aws.rds.cpuutilization.total` 등)로 바꾸지 않는다. 바꾸려면 Datadog의 RDS Enhanced 전용 Lambda를
  따로 설치해야 해서, 대신 로그 측정값(measure) 8개를 만들어 대시보드가 로그에서 바로 그린다(2026-11-12):
  `@cpuUtilization.total|user|system|wait|steal`, `@loadAverageMinute.one`, `@tasks.running`, `@tasks.blocked`.
  Datadog 조직 설정이라 저장소에는 없다. 지워졌으면 Logs → 왼쪽 facet 목록 **+ Add** → Measure 탭에서 같은 경로로 다시 만든다.
  `steal`은 T 계열 CPU 크레딧이 바닥나 AWS가 CPU를 묶을 때 오른다.
- Datadog Logs `host:ticket-core-db source:postgresql`에 로그가 들어온다(로그 그룹 `/aws/rds/instance/ticket-core-db/postgresql`).

**주의**: 과부하 구간에서는 100ms 넘는 SQL이 쏟아져 로그 쓰기 자체가 DB를 바쁘게 할 수 있다. 로그 때문에 숫자가 흔들린다고
의심되면 `log_min_duration_statement`를 `1000`으로 올린다. 동적 파라미터라 재부팅이 필요 없다.

**되돌리기**: `수정`에서 파라미터 그룹을 `default.postgres18`로, Enhanced Monitoring 끄기, 로그 내보내기 해제 → 즉시 적용 → 재부팅.

### 2. RDS에 Datadog 읽기 계정

**누가**: 사용자(Core EC2 SSH). **걸리는 시간**: 5분.

Core EC2에서 master 계정으로 붙는다. 비밀번호는 psql이 묻는다.

```bash
sudo docker run --rm -it postgres:18 psql "host=<RDS 엔드포인트> port=5432 dbname=ticket_core_db user=postgres sslmode=require"
```

```sql
CREATE USER datadog;
\password datadog
GRANT pg_monitor TO datadog;
```

- `\password`는 화면에 비밀번호를 보이지 않고 묻는다. SQL에 비밀번호를 적지 않아 기록에 남지 않는다. 이 비밀번호는 3단계 `conf.yaml`에만 쓴다.
- 세 줄을 **한 줄씩** 입력한다. 한꺼번에 붙여 넣으면 `\password`가 다음 줄(`GRANT …`)을 비밀번호 답으로 먹어서,
  비밀번호도 권한도 안 들어간다(2026-11-12 실제로 겪음. `GRANT ROLE` 응답이 없으면 권한이 안 들어간 것이다).
- IntelliJ 같은 DB 도구로 SSH 터널을 거쳐 붙어도 된다. `\password`는 psql 전용이라, 그때는
  `CREATE USER datadog WITH PASSWORD '<비밀번호>';`로 만든다. 도구의 쿼리 이력에 비밀번호가 남으니 실행 뒤 지운다.
  도구가 수동 커밋(`Tx: Manual`)이면 커밋해야 다른 접속에서 보인다.
- DBM을 쓰지 않으므로 `datadog` 스키마와 explain 함수는 만들지 않는다.
- `pg_stat_statements` 확장은 Datadog 기본 수집에 필요 없다. 사람이 psql로 상위 SQL을 볼 때만 쓴다. 쓰려면
  `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;`(1단계 재부팅 뒤에만 된다).

**확인**

```bash
sudo docker run --rm -it postgres:18 psql "host=<RDS 엔드포인트> port=5432 dbname=ticket_core_db user=datadog sslmode=require" \
  -c "select numbackends, xact_commit from pg_stat_database where datname = 'ticket_core_db';"
```

한 줄이 나오면 된다.

**되돌리기**: master로 `DROP USER datadog;`

### 3. Core EC2: Agent 설정과 nginx 상태 페이지

**누가**: 사용자(Core EC2 SSH, `/home/ubuntu`). **걸리는 시간**: 20분. Core가 다시 만들어져 1~2분 끊긴다. 측정 전이라 괜찮다.

서버의 `docker-compose.yml`은 저장소 밖에 있고 배포가 덮어쓰지 않는다. 그래서 서버 파일을 직접 고친다.

**먼저 백업한다.**

```bash
cd /home/ubuntu
cp docker-compose.yml docker-compose.yml.bak-$(date +%Y%m%d)
cp nginx/nginx.conf nginx/nginx.conf.bak-$(date +%Y%m%d)
```

**3-1. postgres 수집 설정 파일**

```bash
mkdir -p /home/ubuntu/datadog/postgres.d
cat > /home/ubuntu/datadog/postgres.d/conf.yaml <<'EOF'
init_config:
instances:
  - host: <RDS 엔드포인트>
    port: 5432
    username: datadog
    password: <2단계 비밀번호>
    dbname: ticket_core_db
    ssl: require
    dbm: false
    tags:
      - env:prod
      - service:ticket-core-db
EOF
chmod 600 /home/ubuntu/datadog/postgres.d/conf.yaml
```

`<…>`는 편집기로 채운다(명령행에 비밀번호를 치면 shell 기록에 남는다).

**3-2. `docker-compose.yml`의 `datadog-agent`**

- `image`: `gcr.io/datadoghq/agent:latest` → `gcr.io/datadoghq/agent:7.81.1` (latest는 재기동 때마다 버전이 바뀌어 측정 조건이 흔들린다)
- `environment`에 더하거나 바꾼다.

  ```yaml
  - DD_HOSTNAME=ticket-core-prod
  - DD_PROCESS_AGENT_ENABLED=false   # 원래 true. Live Processes는 학생 팩 Pro에 없고 CPU만 쓴다
  ```

- `volumes`에 더한다. 이미 있는 `./datadog/docker.d:/etc/datadog-agent/conf.d/docker.d:ro`(빈 폴더)는 **그대로 둔다**.
  docker 수집이 15초마다 dockerd·containerd를 약 1코어씩 쓰게 해서 꺼 둔 것이다(2026-11-10).

  ```yaml
  - ./datadog/postgres.d:/etc/datadog-agent/conf.d/postgres.d:ro
  ```

**3-3. `docker-compose.yml`의 `app`(ticket-be)**

- `environment`에 더한다. Gatling이 보내는 실행 ID 헤더를 span 태그로 남겨 한 실행만 찾게 한다(로컬 `reset-local.ps1 -Datadog`과 같은 값).

  ```yaml
  - DD_TRACE_HEADER_TAGS=X-Load-Test-Run-Id:load_test.run_id
  ```

- label `com.datadoghq.ad.checks`의 openmetrics 인스턴스에 `"collect_histogram_buckets": false`를 더한다. bucket 지표는 쓰지 않고
  지표 수만 늘린다. 바꾼 뒤 모양:

  ```yaml
  com.datadoghq.ad.checks: '{"openmetrics":{"init_config":{},"instances":[{"openmetrics_endpoint":"http://%%host%%:8080/actuator/prometheus","namespace":"ticket","metrics":[".*"],"collect_histogram_buckets":false}]}}'
  ```

**3-4. nginx 상태 페이지**

`nginx/nginx.conf`의 `http { }` 안에 내부 전용 server를 더한다. compose `ports`에는 81을 **열지 않는다**. Agent만 Docker 네트워크로 닿는다.

```nginx
server {
    listen 81;
    location /nginx_status {
        stub_status;
        allow 172.16.0.0/12;   # Docker 네트워크
        allow 127.0.0.1;
        deny all;
    }
}
```

`docker-compose.yml`의 `nginx` 서비스에 label을 더한다.

```yaml
labels:
  com.datadoghq.ad.checks: '{"nginx":{"init_config":{},"instances":[{"nginx_status_url":"http://%%host%%:81/nginx_status/"}]}}'
  com.datadoghq.ad.logs: '[{"source":"nginx","service":"ticket-nginx"}]'
```

이미 적용된 `worker_rlimit_nofile 65535`, `worker_connections 8192`, `keepalive_requests 10000`, upstream `keepalive 64`는 건드리지 않는다.

**3-5. 적용**

먼저 문법을 검사한다. 이미지 변수를 넣지 않으면 `service "app" has neither an image nor a build context` 오류가 나는데,
파일 문제가 아니라 변수가 비어서다. 지금 떠 있는 컨테이너에서 이미지 이름을 가져와 넣는다.

```bash
cd /home/ubuntu
IMG="$(sudo docker inspect -f '{{.Config.Image}}' ticket-be)"
FE="$(sudo docker inspect -f '{{.Config.Image}}' ticket-fe)"
sudo DOCKER_IMAGE="$IMG" FRONTEND_DOCKER_IMAGE="$FE" DD_VERSION="${IMG##*:}" docker compose config --quiet && echo OK
```

`OK`가 나오면 같은 셸에서 적용한다.

```bash
sudo DOCKER_IMAGE="$IMG" FRONTEND_DOCKER_IMAGE="$FE" DD_VERSION="${IMG##*:}" docker compose up -d
until sudo docker exec ticket-be wget -q -O /dev/null http://localhost:8080/actuator/health; do sleep 5; done
sudo docker exec ticket-nginx nginx -t && sudo docker restart ticket-nginx
```

- `nginx -t`가 실패하면 재시작하지 않는다. 오류 줄을 고치거나 아래 "되돌리기"로 간다. `ticket-nginx`가 아예 안 떠 있으면
  `sudo docker logs --tail 50 ticket-nginx`로 오류를 본다.
- `DD_VERSION`은 배포 workflow와 같게 이미지 태그(commit)다. 빼면 span의 version이 비어 버린다.
- `app`이 새로 만들어진다. nginx는 upstream 주소를 기동 때 한 번만 읽으므로 Core가 UP이 된 뒤 nginx를 재시작한다
  ([배포 workflow](../../.github/workflows/deploy.yml)의 `restart_nginx_if_recreated`와 같은 이유).
- Core가 새로 떴으므로 측정 준비의 예열이 필요하다.

**확인**

```bash
sudo docker exec datadog-agent agent status | grep -A6 -E '^ +(postgres|nginx|openmetrics|docker) '
sudo docker exec datadog-agent agent hostname
```

- `postgres`·`nginx`·`openmetrics` 아래 `[OK]`, `docker` 체크는 **없음**.
- `agent hostname`이 `ticket-core-prod`.
- `wait for health` 중 `wget: can't connect … Connection refused`가 몇 줄 나오는 것은 Core가 뜨는 중이라는 뜻이다(2026-11-12 약 40초).
- `postgresql.*` 지표의 host 태그는 `ticket-core-prod`가 아니라 RDS 주소다(Datadog 기본 동작). 대시보드는 `env:prod`로 묶어 본다.
- docker 체크를 꺼서 `docker.*` 지표와 `container.restarts`는 없다. 컨테이너 수·재시작 알림과 대시보드는 `container.uptime`을 쓴다.
- Datadog Infrastructure 목록에 `ticket-core-prod`가 보인다. 옛 이름(`i-09739eeb559db0d1d`)은 2시간쯤 뒤 목록에서 빠진다.
  EC2 인스턴스 ID가 별칭으로 붙어 AWS 연동 지표도 같은 호스트에 모인다.
- 브라우저에서 `https://oneticket.site`가 열린다.

**되돌리기**

```bash
cd /home/ubuntu
cp docker-compose.yml.bak-<날짜> docker-compose.yml
cp nginx/nginx.conf.bak-<날짜> nginx/nginx.conf
# 위 3-5의 IMG·FE·compose up·health 대기·nginx 재시작을 다시 실행
```

### 4. queue EC2: 호스트 이름

**누가**: 사용자(queue EC2 SSH, `/home/ubuntu/ticket-queue`). **걸리는 시간**: 5분(볼륨 복원이 필요하면 +10분).

**먼저 볼륨 복원(2026-11-12 기준 필요)**: queue EC2 `i-089cd124293cecb83`(ticket-queue)는 루트 볼륨이 없어
`Instance does not have a volume attached at root (/dev/sda1)`로 시작되지 않는다. 남은 백업은 스냅샷
`snap-0dc9d409a27e4dce2`(2026-09-28, 원본 볼륨 `vol-03135cfb4f32cda19` 30GiB)뿐이다.

1. Core EC2의 **스토리지** 탭 볼륨 ID와 위 원본 볼륨 ID가 다른지 본다. 같으면 Core 디스크 백업이니 쓰지 않는다.
2. queue 인스턴스 **네트워킹** 탭에서 가용 영역을 본다.
3. 스냅샷 → 작업 → **스냅샷에서 볼륨 생성**: gp3, 30GiB(원본보다 작게는 안 된다), 2번의 가용 영역.
4. 볼륨 → 작업 → **볼륨 연결**: 인스턴스 `i-089cd124293cecb83`, 디바이스 `/dev/sda1` → 인스턴스 시작.
5. 9/28 이후 queue 변경은 queue 배포 workflow를 한 번 돌려 반영한다. 서버 `.env`·인증서는 스냅샷 시점 그대로다.
6. 측정이 끝나면 새 스냅샷을 찍고 볼륨을 지워 비용을 줄인다(gp3 30GiB는 꺼 둬도 매달 요금이 나간다).

queue의 compose는 저장소(`ticket-queue/deploy/docker-compose.yml`)가 원본이고 **배포할 때마다 서버 파일을 덮어쓴다**.
또 `datadog-agent`는 `.env`를 `env_file`로 읽지 않는다. 그래서 서버 `.env`에 `DD_HOSTNAME`만 넣어서는 Agent에 가지 않는다.

**이번 테스트용(다음 배포 때 사라진다)**: 서버의 `docker-compose.yml` `datadog-agent.environment`에 `DD_HOSTNAME: ticket-queue-prod`를 더하고 Agent만 다시 만든다.

```bash
cd /home/ubuntu/ticket-queue
cp docker-compose.yml docker-compose.yml.bak-$(date +%Y%m%d)
# 편집기로 datadog-agent.environment에 DD_HOSTNAME: ticket-queue-prod 추가
API="$(sudo docker inspect -f '{{.Config.Image}}' ticket-queue-api)"
SCH="$(sudo docker inspect -f '{{.Config.Image}}' ticket-queue-scheduler)"
VER="$(sudo docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' ticket-queue-api | sed -n 's/^DD_VERSION=//p')"
sudo env QUEUE_API_DOCKER_IMAGE="$API" QUEUE_SCHEDULER_DOCKER_IMAGE="$SCH" DD_VERSION="$VER" docker compose up -d datadog-agent
```

**확인**: `sudo docker ps --filter name=datadog-agent`가 `Up`, `sudo docker exec datadog-agent agent hostname`이 `ticket-queue-prod`,
`sudo docker exec datadog-agent agent status | grep -A4 redisdb`가 `[OK]`.

**되돌리기**: 백업으로 되돌리고 같은 `compose up -d datadog-agent`.

**제안(저장소 수정, 미적용)**: 오래 가게 하려면 `ticket-queue/deploy/docker-compose.yml`의 `datadog-agent`에 아래를 넣어 배포한다.
Core에서 15초 멈춤을 만든 docker 수집과 Pro에 없는 Live Processes가 queue에도 켜져 있다.

```yaml
    environment:
      DD_HOSTNAME: ${DD_HOSTNAME:-ticket-queue-prod}
      DD_PROCESS_AGENT_ENABLED: "false"          # 지금 "true"
    volumes:
      - ./datadog/docker.d:/etc/datadog-agent/conf.d/docker.d:ro   # 빈 폴더: docker 체크 끔
```

### 5. 부하 발생기 EC2: 가벼운 Agent

**누가**: 사용자(부하 발생기 `ticket-load-test` 중 측정에 쓰는 한 대, SSH). **걸리는 시간**: 10분.

호스트 지표(CPU·메모리·네트워크)만 보낸다. APM·로그·프로세스·docker 수집은 끈다. 부하 발생기가 막혔는지(INVALID)를 Core 지표와 같은 화면에서 보기 위해서다.

Docker가 있으면:

```bash
mkdir -p /home/ubuntu/datadog/docker.d      # 빈 폴더: docker 체크 끔
sudo docker run -d --name datadog-agent --restart unless-stopped \
  --pid host --cgroupns host --network host \
  -e DD_API_KEY=<키> -e DD_SITE=us5.datadoghq.com -e DD_HOSTNAME=loadgen-1 -e DD_ENV=prod \
  -e DD_APM_ENABLED=false -e DD_LOGS_ENABLED=false -e DD_PROCESS_AGENT_ENABLED=false \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  -v /proc/:/host/proc/:ro \
  -v /sys/fs/cgroup/:/host/sys/fs/cgroup:ro \
  -v /home/ubuntu/datadog/docker.d:/etc/datadog-agent/conf.d/docker.d:ro \
  gcr.io/datadoghq/agent:7.81.1
```

`--network host`는 컨테이너가 아니라 호스트의 네트워크 지표(`system.net.bytes_sent`)를 재기 위해서다. 명령행의 API 키는 shell 기록에 남으므로
끝나면 `history -d`로 지우거나, 키를 `--env-file`(chmod 600)로 넘긴다.

Docker가 없으면(2026-11-12 `loadgen-1`이 이 경우였다) 설치 스크립트로 패키지를 깔고, 설정 파일은 직접 쓴다.

```bash
 DD_API_KEY=<키> DD_SITE=us5.datadoghq.com DD_HOSTNAME=loadgen-1 DD_ENV=prod DD_AGENT_MAJOR_VERSION=7 DD_AGENT_MINOR_VERSION=81.1 \
  bash -c "$(curl -L https://install.datadoghq.com/scripts/install_script_agent7.sh)"
```

- 맨 앞 공백은 셸 기록에 키를 남기지 않으려는 것이다(Ubuntu 기본 `HISTCONTROL=ignoreboth`).
- 2026-11-12에는 스크립트가 `Setting up datadog-signing-keys` 다음에서 멈췄다. 오류 기록용 `sudo tee`가 다른 터미널(pts)에 붙어
  멈추는 것으로 보인다(추정). 3분 넘게 그대로면 다른 SSH 창에서 `dpkg -l datadog-agent datadog-signing-keys | tail -2`가 둘 다
  `ii`인지 보고, 그렇다면 설치 창에서 `Ctrl+C`로 끝낸다. 패키지는 깔렸고 설정 파일 쓰기와 시작만 남은 상태다.

설정 파일을 통째로 쓰고 켠다(스크립트가 끝까지 돈 경우에도 이 내용으로 덮어쓴다).

```bash
 sudo tee /etc/datadog-agent/datadog.yaml >/dev/null <<'EOF'
api_key: <키>
site: us5.datadoghq.com
hostname: loadgen-1
env: prod
apm_config:
  enabled: false
logs_enabled: false
process_config:
  process_collection:
    enabled: false
EOF
sudo chown dd-agent:dd-agent /etc/datadog-agent/datadog.yaml && sudo chmod 640 /etc/datadog-agent/datadog.yaml
sudo systemctl enable --now datadog-agent && sleep 20 && sudo datadog-agent hostname
```

부하 발생기는 측정에 쓰는 대수만큼만 설치한다. 늘리면 `loadgen-2`처럼 이름만 바꾸고, `[loadtest]` 대시보드와
`loadtest-loadgen-cpu-high` 알림의 `host:loadgen-1` 범위도 같이 넓힌다.

**확인**: `sudo docker exec datadog-agent agent status`(설치 스크립트면 `sudo datadog-agent status`)의 Hostname이 `loadgen-1`,
`docker` 체크 없음. Datadog Infrastructure에 `loadgen-1`.

**되돌리기**: `sudo docker rm -f datadog-agent` / 설치 스크립트면 `sudo apt-get remove -y datadog-agent`.

### 6. 무부하 확인

**누가**: Claude가 요청, 사용자가 **Core EC2**에서 실행(프롬프트 호스트가 부하 발생기가 아닌지 본다). **걸리는 시간**: 2분.

설정을 바꾼 뒤 부하 없이 1분 동안 15초 주기 CPU 튐이 사라졌는지 본다.

```bash
top -b -d 1 -n 60 > /tmp/idle-top.txt
grep '^%Cpu' /tmp/idle-top.txt | awk '{print NR": us="$2" sy="$4" id="$8}'
grep -E 'dockerd|containerd|agent' /tmp/idle-top.txt | sort -k9 -nr | head
```

- **정상**: 60줄 내내 `id`가 대체로 80 이상이고 15초 간격의 튐이 없다. `dockerd`·`containerd`가 상위에 거의 안 나온다.
- **이상**: 15초마다 `id`가 50 아래로 떨어지고 `dockerd`·`containerd`가 함께 오른다 → docker 체크가 다시 켜졌다. 3-2의 빈 `docker.d` 마운트를 확인한다.
- 튄 초에 어느 프로그램이 썼는지는 같은 파일로 바로 본다: `awk '/^top -/{f++} f && $1 ~ /^[0-9]+$/ && $9+0>=5 {print f": "$9"% "$12}' /tmp/idle-top.txt`
- 2026-11-12 기준값: `id` 86~99%, `dockerd`·`containerd` 없음. 15초마다 `agent` 9~10%(한 코어 기준, Agent 수집), 30초마다 `java` 5%.
  Agent가 Core 지표를 긁어 가는 순간에도 `java`는 5%를 넘지 않았다.

Datadog은 10~15초 단위라 이 확인은 `top`으로 한다.

## 매번 (측정할 때마다)

**측정 전**

1. AWS 콘솔에서 Core EC2, 부하 발생기 EC2, RDS를 켠다(RDS가 이미 켜져 있을 수 있다. 아래 "끝나면").
2. Datadog 대시보드 `[loadtest] 부하 테스트 한 화면`을 연다. 호스트 `ticket-core-prod`·`loadgen-1`과 RDS `ticket-core-db` 칸에 값이 들어오는지 본다.
3. RDS CPU 크레딧 `aws.rds.cpucredit_balance{dbinstanceidentifier:ticket-core-db}`를 본다. CloudWatch 지표라 10분쯤 늦게 들어온다.
   db.t4g.micro는 기준 CPU를 넘는 동안 크레딧을 쓴다. RDS의 T 계열은 기본 Unlimited라 잔고가 0이어도 느려지지 않고
   `aws.rds.cpusurplus_credits_charged`만큼 추가 요금이 붙는다. 잔고가 바닥이면 일지 "조건"에 적는다.
4. 이후는 [production.md](../../.agents/skills/core-capacity/production.md)의 "준비"(점검·예열)로 간다.

**측정 중·뒤**

- 1초 단위가 필요한 멈춤은 `top -b -d 1`(Core EC2)로 본다. Datadog은 10~15초 단위다.
- APM에서 한 실행만 찾기: `service:ticket-be env:prod @load_test.run_id:<runId>`. runId는 결과 폴더 `summary.json`의 `runId`다.
- RDS 로그(느린 SQL, `auto_explain` 계획, 락 대기): Datadog Logs `source:postgresql`, 또는 CloudWatch Logs
  `/aws/rds/instance/ticket-core-db/postgresql`.

**측정 뒤 7일 안**

- RDS Performance Insights(Database Insights 표준, 7일 보관)에서 측정 시간대의 대기 종류(wait event)와 상위 SQL을 캡처해 일지에 붙인다.
  7일이 지나면 사라진다.

**끝나면**

- Core EC2, 부하 발생기 EC2, RDS를 끈다.
- RDS는 꺼진 지 7일 뒤 AWS가 자동으로 켠다. Datadog의 `[infra]` 알림 상태를 보고, 켜져 있으면 다시 끈다.
