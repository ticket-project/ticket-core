# 운영 측정

대상은 운영 Core(`https://oneticket.site`, AWS EC2 한 대, 컨테이너 `ticket-be`)와 운영 Oracle(OCI Autonomous DB)이다.
Queue 입장률 C는 여기서 정한다. 순서와 끝난 기준은 [core-capacity.md](../../../docs/core-capacity.md)의 "측정 순서" 1~10단계가 원본이다.

운영은 실제 서비스라서 로컬과 세 가지가 다르다.

- **실행마다 승인을 받는다.** 아래 "승인".
- **운영 DB에 쓰는 일과 운영 서버 설정은 사용자가 한다.** 데이터 적재·정리, nginx·compose 확인이 여기에 든다. Claude는 명령과
  SQL 초안을 준비해 보여 준다. 운영 DB 접속 정보와 JWT secret은 Claude가 다루지 않는다.
- **원인은 Datadog으로 찾는다.** 운영 이미지에는 `jcmd`가 없고, SQL 로그를 켤 수 없다.

## 승인

실행할 때마다 아래를 한 블록으로 보여 주고 사용자의 "예"를 받는다. 승인은 그 실행 하나에만 쓴다.
사용자가 정한 측정 시간대 밖이면 실행하지 않는다.

```text
대상 URL / 시나리오 / 부하(users/sec × 초) / 예상 사용자 수 / 회차 ID / 부하 발생기 / 예상 끝 시각
```

## 준비

측정을 시작하는 날 한 번 한다. 항목마다 결과를 일지 "조건"에 적는다.

1. **운영 commit.** ticket에서 `gh run list --workflow deploy.yml --limit 5`로 마지막 배포 성공 commit을 보고, Datadog span의
   `git.commit.sha`와 맞춰 본다. 측정할 commit과 다르면 사용자가 배포한다. health가 UP이어야 한다.
   - 2026-10-02 확인: 마지막 성공은 9/28 `f3202085`(OSIV 끄기 전)다. 그 뒤 배포 세 번이 실패했다. Datadog 마지막 span은
     9/29 08:47 KST라 서버가 꺼져 있을 수 있다.
2. **앞단 상한**(core-capacity.md 2단계). 사용자에게 확인을 부탁할 것과 Claude가 읽을 것을 나눠 적는다.
   - Claude: Hikari 최대, Tomcat 최대 스레드(`run-step.ps1`이 prometheus에서 읽는다), Datadog 호스트의 CPU·메모리.
   - 사용자(서버에서): Core nginx의 `worker_rlimit_nofile`·`limit_req`, compose의 JVM 옵션(8월 기준 Xmx 478MiB),
     DB 등급과 동시 세션 상한(Always Free면 30, 미확인).
3. **prometheus 경로.** `run-step.ps1`은 `<CoreUrl>/actuator/prometheus`를 읽는다. 밖에서 열려 있으면 내부 지표가 공개된 것이라
   보안 문제로 보고한다. 막혀 있으면 부하 발생기 EC2에서 Core 사설 주소로 읽는 방법을 사용자와 정한다.
4. **데이터**(core-capacity.md 3단계). 사용자가 [seed README](../../../seed/README.md)의 "운영 규모를 가정한 적재" 명령을 실행한다.
   넣기 전에 DB 남은 용량을, 넣은 뒤에는 통계 수집(`DBMS_STATS`)을 확인한다. 측정 회원 ID는 사용자가
   `../gatling-test/scripts/core-capacity/member-ids.txt`(git 미추적)로 뽑는다.
5. **JWT secret.** 사용자가 Claude를 띄우기 전에 사용자 환경 변수 `LOADTEST_JWT_SECRET`을 설정한다. Claude는 값을 출력하거나
   명령줄·파일에 쓰지 않는다.
6. **부하 발생기**를 준비한다. 아래.
7. **Smoke.** 1 users/sec × 5초를 승인받아 돌린다(core-capacity.md 4단계). 좌석 상태 응답의 크기와 `Content-Encoding`도 본다.

## 부하 발생기

운영에서는 Gatling을 이 PC가 아니라 EC2에서 돌린다. 이유는 네트워크다. 좌석 상태 응답은 회차의 좌석 전체를 담는다.
15,000석이면 압축 없이 한 번에 약 1MB다(Core에 압축 설정이 없다. nginx 압축은 미확인). 초당 50명이면 내려받기만 약 400Mbps다.
회사 회선에서 돌리면 Core가 아니라 회선을 잰다.

지금 도구 상태:

- `scripts/capacity/run-step.ps1`은 자기가 도는 PC에서 Gatling을 실행한다(Windows, `gradlew.bat`). EC2(Ubuntu)에서는 그대로 못 돈다.
- `run-distributed-booking.ps1`은 EC2 노드(기본값에 세 대, SSH 키 `ticket-test-key-01.pem`)에 Gatling을 나눠 돌린다.
  Core 쪽 숫자 판정과 INVALID 검사는 없다.

그래서 운영 측정 첫날, `run-step.ps1`에 원격 부하 발생기를 붙인다. 준비·판정은 이 PC에서 하고 Gatling만 EC2 한 대에서 SSH로
돌린다. 판정 규칙이 한 곳에 남는다. 한 대로 모자라면(부하 발생기 GC나 입장 흔들림으로 INVALID) 그때 노드를 늘린다.
EC2가 켜져 있는지, Core와 같은 리전인지 먼저 확인한다.

## 실행 사이

30초 실행이라 중간에 끊지 않는다. 다음 실행 전에 Core가 원래대로 돌아왔는지 본다. health UP, Hikari pending 0, 5xx 없음,
executor 적체 없음([operations.md](../../../docs/operations.md)의 "Core 용량 관측"). 돌아오지 않으면 다음 실행을 하지 않고 보고한다.
운영 Core는 재기동하지 않으므로 예열은 배포 직후 첫 실행 한 번이다.

## 진단

`run-step.ps1` 결과(`summary.json`, `core-metrics.tsv`, prometheus 전후)는 로컬과 같게 본다. 그 밖에는 다음을 쓴다.

- **Datadog APM.** 서비스는 `ticket-be`, `env:prod`, 사이트는 us5다([operations.md](../../../docs/operations.md)는 `ticket-core`로 적고 있다.
  2026-10-02 확인). span은 표본이다. 요청별 시간 분포는 run-step의 Core 히스토그램으로 본다. span은 실행 시간대의 느린 요청을
  골라, 하위 span(DB 쿼리, Redis)이 어디서 길어졌는지 보는 데 쓴다. 로그는 Gatling이 보내는 `X-Load-Test-Run-Id`(runId)로 찾는다.
- **스레드 덤프.** 운영 이미지는 JRE(`eclipse-temurin:25-jre-alpine`)라 `jcmd`가 없다. 필요하면 사용자가 서버에서
  `docker exec ticket-be kill -3 1`을 막힌 동안 2~3번 실행한다. 덤프는 컨테이너 로그에 남는다. 읽는 법은 local.md와 같다.
  운영 요청 스레드 이름도 `http-nio-8080-exec-`다.
- **Oracle 대기.** [operations.md](../../../docs/operations.md)의 `v$session` 쿼리를 관측 전용 계정으로 사용자가 실행한다.

## 정리

- 측정 중에는 데이터를 지우지 않는다. 실행마다 새 회차를 쓰므로 필요 없다.
- 측정이 끝나면 남는 것: 부하 회차의 주문, 배경 주문(`created_by = 'LOAD_TEST_BACKGROUND'`), 부하 회원. seed에는 지우는 기능이
  없다. 지울지와 SQL은 사용자와 정한다. Claude는 SQL 초안을 쓰고, 실행은 사용자가 한다.
- Redis의 선택(5분)과 선점(600초)은 저절로 풀린다. PENDING 주문은 만료 worker가 처리한다.

## 아직 정하지 않은 것

운영 측정을 시작할 때 사용자와 정하고, 정한 내용은 이 파일에 반영한다.

- 측정 시간대
- prometheus를 읽는 경로(위 준비 3)
- `run-step.ps1` 원격 부하 발생기의 구현과 EC2 사양
- 측정을 끝낸 뒤 데이터를 지울지
