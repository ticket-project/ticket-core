# 운영 측정

대상은 운영 Core(`https://oneticket.site`, AWS EC2 한 대, 컨테이너 `ticket-be`)와 운영 PostgreSQL(AWS RDS)이다.
Queue 입장률 C는 여기서 정한다. 순서와 끝난 기준은 [core-capacity.md](../../../docs/core-capacity.md)의 "측정 순서" 1~10단계가 원본이다.

운영은 실제 서비스라서 로컬과 세 가지가 다르다.

- **측정을 시작할 때 대상과 부하를 보여 준다.** 아래 "승인".
- **운영 DB에 쓰는 일과 운영 서버 설정 변경은 사용자가 한다.** 데이터 적재·정리, nginx·compose 변경이 여기에 든다. Claude는 명령과
  SQL 초안을 준비해 보여 준다. 운영 호스트에서 읽기만 하는 준비(지표 터널, JWT secret, 회원 ID, 실행 전 점검)는
  `run-step.ps1 -Prod`가 한다(사용자 결정 2026-10-17). 스크립트는 비밀값을 메모리에서만 쓰고 화면·파일·명령행·Claude 출력에 남기지 않는다.
  JWT secret은 이 PC 프로세스 메모리로만 받고, DB 비밀번호는 운영 호스트 안에서 `ticket-be` 환경 변수 → psql 컨테이너로만 넘긴다.
- **원인은 Datadog으로 찾는다.** 운영 이미지에는 `jcmd`가 없다. SQL 로그(p6spy)는 `DECORATOR_DATASOURCE_ENABLED=true`로
  켤 수 있지만 운영 compose를 바꾸고 Core를 재시작해야 하므로 사용자가 정한다.

## 승인

운영에 실사용자가 없으므로 실행마다 승인을 받지 않는다(사용자 결정 2026-10-17). 측정을 시작할 때 아래를 한 블록으로
보여 주고, 사다리·재현·진단·좁히기는 이어서 진행한다. 실사용자가 생기면 실행마다 사용자의 "예"를 받는 방식으로 되돌린다.
실행 사이에 Core가 원래대로 돌아오지 않으면(아래 "실행 사이") 다음 실행을 하지 않고 보고한다.

```text
대상 URL / 시나리오 / 부하(users/sec × 초) / 예상 사용자 수 / 회차 ID / 부하 발생기 / 예상 끝 시각
```

## 실행 명령

Claude는 ticket 저장소 루트에서 아래 꼴 그대로 실행한다. `ticket/.claude/settings.json`의 허용 규칙이 이 앞부분과 맞아야 한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ..\gatling-test\scripts\capacity\run-step.ps1 -Prod -LoadHost ubuntu@<부하 발생기 IP> -UsersPerSecond <N> -Label <ladder|warmup|repro|diag> -PerformanceId <회차>
```

- 운영 Core·DB는 사양이 낮다. 부하 말고 점검으로 운영을 건드리는 일은 최소로 한다(사용자 결정 2026-10-19, capacity-log P-020).
- `-CheckOnly`는 준비와 점검만 하고 부하를 보내지 않는다(`-LoadHost` 없어도 됨). 측정을 시작할 때 한 번 돌려 다음 회차를 받는다.
- 사다리·재현·진단은 그 회차부터 1씩 올려 `-PerformanceId`로 넘긴다. 그러면 실행마다 운영 DB에 묻지 않는다.
  비우면 마지막 부하 주문의 회차 + 1을 운영 DB에 한 번 묻는다(주문을 PK 역순으로 읽다 첫 부하 주문에서 멈춘다).
- 자동 모드 권한 분류기는 Claude의 운영 호스트 SSH를 "Production Reads"로 막는다(2026-10-17). 허용 규칙이 있어도 막히면
  사용자가 그 세션을 기본(매번 확인) 모드로 바꾸고 실행마다 승인한다.

`-Prod`가 하는 일(`../gatling-test/scripts/capacity/prod-target.ps1`):

1. 운영 호스트(`ubuntu@oneticket.site`)에 SSH로 들어가 Core 이미지 태그(commit), 컨테이너 IP, `free -m`을 읽는다.
   `-CheckOnly`일 때만 DB 규모(통계 추정치), `max_connections`, 대형 회차 수, nginx·compose·`docker stats`를 더 읽는다.
   결과는 결과 폴더 `server-conditions.txt`에 남긴다.
2. 빈 로컬 포트에서 `ticket-be:8080`으로 SSH 터널을 열어 health·prometheus를 읽고, 지표를 다 읽으면 닫는다.
   전체 prometheus는 실행 전후 한 번씩만 받고, 1초 수집·회복 대기는 `includedNames`로 거른 지표 6개만 받는다.
3. `ticket-be`의 `JWT_SECRET`을 메모리로 받아 토큰을 만든다. 사용자 환경 변수는 필요 없다.
4. `../gatling-test/scripts/core-capacity/member-ids.txt`(git 미추적)가 없으면 운영 DB에서 부하 회원 ID를 받아 만든다.
5. 앞 실행의 부하가 빠질 때까지 기다린다(아래 "실행 사이").
6. 조건(commit, Core 기동 시간, DB 규모, 회차)을 `prod-conditions.json`에 남긴다.

키는 운영 호스트와 부하 발생기가 같은 `ticket-test-key-01.pem`이다. `-KeyPath`를 비우면 `~/.ssh/`부터 찾고, 권한을 좁힌 사본을 쓴다.

## 준비

측정을 시작할 때마다 한다. Core EC2와 부하 발생기 EC2는 평소 꺼 두고 측정할 때만 켠다(사용자 결정 2026-10-17).
항목마다 결과를 일지 "조건"에 적는다.

1. **EC2 시작.** 사용자가 AWS 콘솔(서울 ap-northeast-2)에서 Core EC2(`i-09739eeb559db0d1d`)와 부하 발생기 EC2(`ticket-load-test`)를
   켠다. 부하 발생기 퍼블릭 IP는 켤 때마다 바뀐다. `/core-capacity prod <IP>`처럼 인자로 받고, 없으면 사용자에게 묻는다.
   IP는 파일이나 스크립트 기본값에 두지 않고 그 세션에서만 쓴다. Core는 도메인으로 접속하므로 IP가 필요 없다.
2. **점검.** `-Prod -CheckOnly`를 돌린다. 출력과 `prod-conditions.json`·`server-conditions.txt`를 일지 "조건"에 옮긴다.
   - commit이 측정할 commit과 다르면 사용자가 배포한다(ticket에서 `gh run list --workflow deploy.yml --limit 5`).
     Datadog span의 `git.commit.sha`는 5월 commit `7b71a992`로 고정돼 있어 대조에 쓰지 않는다(2026-10-17 확인).
   - 회원이 모자라거나 다음 회차가 없으면(대형 회차를 다 썼으면) 사용자가 아래 "데이터"대로 적재한다.
3. **예열.** Core EC2를 새로 켜면 JVM이 차갑다(`coreUptimeSeconds`가 짧다). 첫 실행은 `-Label warmup -UsersPerSecond 5`로 하고
   판정에 쓰지 않는다. 같은 Core 기동 안에서는 한 번이면 된다.
4. **Smoke.** 처음 측정하거나 데이터·시나리오를 바꾼 뒤에는 1 users/sec × 5초를 먼저 돌린다(core-capacity.md 4단계).

**데이터**(core-capacity.md 3단계)는 운영 DB 쓰기라 사용자가 한다. [seed README](../../../seed/README.md)의 "운영 규모를 가정한 적재"
명령을 실행한다. 넣기 전에 RDS 남은 용량을, 넣은 뒤에는 통계 갱신(`ANALYZE orders, order_seats, members`)을 확인한다.
2026-10-17 기준 회원 100만, 주문 약 501만, 대형 회차 45개(그날 001~013 사용)다.

**앞단 상한**(core-capacity.md 2단계)은 점검이 남긴 `server-conditions.txt`와 run-step이 prometheus에서 읽는 Hikari·Tomcat 최대로 본다.
2026-10-17 확인: 호스트는 2 vCPU(물리 1코어)·메모리 1.9GB이고 Redis·nginx·FE·certbot·Datadog Agent가 함께 돈다.
Core JVM은 `-Xmx` 없이 기본 힙(약 478MiB), Hikari 10, Tomcat 200, Datadog 트레이서 켜짐. nginx는 `limit_req`·`limit_conn` 없음,
`worker_connections 1024`, gzip 켜짐. RDS `max_connections` 79.

## 다른 PC에서 준비

한 번만 한다.

1. `ticket`과 `gatling-test`를 같은 상위 폴더에 clone한다(`ticket-workspace/ticket`, `ticket-workspace/gatling-test`).
2. `ticket-test-key-01.pem`을 `~/.ssh/`에 둔다.
3. JDK 21 이상을 설치한다. 토큰·피더 생성은 이 PC의 Gradle이 한다.
4. Claude를 ticket 저장소에서 띄우고 `/core-capacity prod <부하 발생기 IP>`를 실행한다. 이후는 "준비" 1~4와 같다.

## 부하 발생기

운영에서는 Gatling을 이 PC가 아니라 EC2에서 돌린다(사용자 결정 2026-10-17). 처음 정한 이유는 네트워크였다. 좌석 상태 응답은
회차의 좌석 전체를 담아 15,000석이면 압축 없이 약 1MB였고, 초당 50명이면 내려받기만 약 400Mbps였다.
그 뒤 응답 압축(capacity-log P-010, 78KB)과 03의 점유 좌석 API(P-011)로 이 이유는 대부분 사라졌다. 그래도 EC2에서 돌리면
이 PC의 회선과 Windows 임시 포트(P-012)가 결과에 섞이지 않는다.

`-LoadHost`로 받은 EC2 한 대에서 Gatling만 SSH로 돌린다. 토큰·피더 생성, 회차 검사, 지표 수집, 판정은 이 PC에서 한다.
판정 규칙이 한 곳에 남는다. 토큰은 이 PC에서 만들어 파일로 보내므로 EC2에는 JWT secret이 가지 않는다. 실행이 끝나면 EC2의
토큰 폴더를 지운다. Java 21이 없으면 설치한다(디스크에 남는다). 한 대로 모자라면(부하 발생기 GC나 입장 흔들림으로 INVALID)
그때 노드를 늘린다. 부하 발생기는 `ticket-load-test` 이름의 c6i.xlarge(4 vCPU·7.6GB) 세 대이고 지금은 한 대만 쓴다(2026-10-17).

`run-distributed-booking.ps1`은 EC2 노드에 Gatling을 나눠 돌리지만 Core 쪽 숫자 판정과 INVALID 검사가 없어 수용량 측정에는 쓰지 않는다.

## 실행 사이

30초 실행이라 중간에 끊지 않는다. `-Prod`는 실행 전에 Core가 원래대로 돌아왔는지 5초 간격으로 최대 3분 기다린다.
health UP, Hikari pending 0, 바쁜 요청 스레드 5개 이하. 돌아오지 않으면 중단(2)으로 끝난다. 그러면 다음 실행을 하지 않고
보고한다. 5xx와 executor 적체는 [operations.md](../../../docs/operations.md)의 "Core 용량 관측"으로 따로 본다.

## 진단

`run-step.ps1` 결과(`summary.json`, `core-metrics.tsv`, prometheus 전후)는 로컬과 같게 본다. 그 밖에는 다음을 쓴다.

- **Datadog APM.** 서비스는 `ticket-be`, `env:prod`, 사이트는 us5다([operations.md](../../../docs/operations.md)는 `ticket-core`로 적고 있다.
  2026-10-02 확인). span은 표본이다. 요청별 시간 분포는 run-step의 Core 히스토그램으로 본다. span은 실행 시간대의 느린 요청을
  골라, 하위 span(DB 쿼리, Redis)이 어디서 길어졌는지 보는 데 쓴다. 로그는 Gatling이 보내는 `X-Load-Test-Run-Id`(runId)로 찾는다.
- **스레드 덤프.** 운영 이미지는 JRE(`eclipse-temurin:25-jre-alpine`)라 `jcmd`가 없다. 필요하면 사용자가 서버에서
  `docker exec ticket-be kill -3 1`을 막힌 동안 2~3번 실행한다. 덤프는 컨테이너 로그에 남는다. 읽는 법은 local.md와 같다.
  운영 요청 스레드 이름도 `http-nio-8080-exec-`다.
- **DB 대기.** [operations.md](../../../docs/operations.md)의 `pg_stat_activity` 쿼리를 관측 전용 계정으로 사용자가 실행한다.

## 정리

- 측정 중에는 데이터를 지우지 않는다. 실행마다 새 회차를 쓰므로 필요 없다.
- 측정이 끝나면 남는 것: 부하 회차의 주문, 배경 주문(`created_by = 'LOAD_TEST_BACKGROUND'`), 부하 회원. seed에는 지우는 기능이
  없다. 지울지와 SQL은 사용자와 정한다. Claude는 SQL 초안을 쓰고, 실행은 사용자가 한다.
- Redis의 선택(5분)과 선점(600초)은 저절로 풀린다. PENDING 주문은 만료 worker가 처리한다. Core EC2를 끄면 Redis도 비워진다.
- 측정이 끝나면 사용자가 Core EC2와 부하 발생기 EC2를 끈다.

## 정한 것 (2026-10-17)

- 측정 시간대: 측정하는 날 사용자가 정한다.
- Core EC2·부하 발생기 EC2: 평소 꺼 두고 측정할 때만 켠다.
- 운영 호스트 읽기 준비(터널, JWT secret, 회원 ID, 점검): `run-step.ps1 -Prod`가 한다.
- 원격 부하 발생기: `run-step.ps1`의 SSH 원격 실행. IP는 인자로 받는다.
- 측정을 끝낸 뒤 데이터: 남겨 둔다. 고친 뒤 같은 데이터 조건으로 다시 재기 위해서다. RDS 저장 용량은 계속 차지한다.
