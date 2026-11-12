# Datadog 정의

Datadog(us5, GitHub 학생 팩 Pro)에 올린 대시보드·알림의 원본이다. 화면에서 바꾸면 여기 JSON도 같이 바꾼다.

| 폴더·파일 | 내용 |
| --- | --- |
| `dashboards/` | 대시보드 4개. Datadog 대시보드 화면 `Configure > Import dashboard JSON`으로 넣는다 |
| `monitors/` | 알림 24개와 옛 알림 처리표(`monitors/README.md`). `Monitors > New Monitor > Import from JSON`으로 넣는다 |
| `next-start-runbook.md` | 다음 부하 테스트 때 서버를 켠 직후 한 번 하는 관측 설정과, 측정마다 하는 확인 |
| `backup-2026-11-12/` | 정리 전 대시보드 4개·알림 26개(SDK 응답 그대로, 필드가 camelCase). 되살릴 때는 Datadog API 형식(snake_case)으로 바꿔야 한다 |

## 2026-11-12 정리에서 정한 것

- 서비스 이름: Core `ticket-be`, queue `ticket-queue-api`·`ticket-queue-scheduler`. env는 `prod`, `local`.
- 호스트 이름(`DD_HOSTNAME`): `ticket-core-prod`, `ticket-queue-prod`, `loadgen-1`, `ticket-local`. 운영 서버 적용은 순서서대로 다음 테스트 때 한다.
- 알림은 받는 곳(@핸들) 없이 상태만 본다. 서버를 평소 꺼 두므로 `notify_no_data`는 끈다.
- 무료 범위만 쓴다: 인프라·APM 호스트 10, 로그 수집 월 500GB, 컨테이너 50, Lambda 10, API 테스트 1만. DBM·프로파일러·Live Processes는 쓰지 않는다.
  DB 부하는 RDS Performance Insights(7일 무료 보관)와 PostgreSQL 로그(`auto_explain`)로 본다.
- AWS 연동 지표 수집은 EC2, EBS, RDS, Lambda, CloudWatch Logs, ALB, ElastiCache, Billing 8개만 켰다. 새 서비스 자동 켜기는 껐다.
- 로그 색인 `main`에 nginx 2xx·3xx 제외 규칙을 넣었다(수집은 하되 색인하지 않음).
- RDS 파라미터 그룹 `ticket-core-pg18`을 만들어 같은 날 연결·재부팅했다(순서서 1단계). 서버 쪽 적용 상태는 순서서 맨 위에 적는다.
- Core 호스트는 docker 체크를 꺼 두므로(15초 멈춤 원인) `docker.*`·`container.restarts` 지표가 없다. 컨테이너 수·재시작은 `container.uptime`으로 본다.
- RDS 1초 지표(Enhanced Monitoring)는 지표가 아니라 로그(`@aws.awslogs.logGroup:RDSOSMetrics`)로 들어오고, 대시보드는 로그 측정값으로 그린다(순서서 1단계 확인).

## 비용·성능에 영향을 주는 설정

켜 둔 시간만큼 쌓이는 것들이다. 서버를 끄면 멈춘다.

| 설정 | 켜져 있는 동안 | 줄이는 법 |
| --- | --- | --- |
| RDS Enhanced Monitoring 1초 | CloudWatch Logs `RDSOSMetrics`에 시간당 약 17MB, Datadog 로그 색인 시간당 약 3,600건, forwarder Lambda 시간당 약 3,600회 | 측정이 없을 때는 RDS를 끈다. 오래 켜 둘 일이면 단위를 60초로 바꾼다 |
| RDS PostgreSQL 로그 내보내기(`log_min_duration_statement` 100ms) | 평소엔 거의 없다. 과부하에서는 느린 SQL마다 한 줄이라 RDS 디스크·CPU와 CloudWatch Logs·Datadog 색인이 함께 는다 | 로그가 숫자를 흔든다고 의심되면 `1000`으로 올린다(재부팅 없음) |
| CloudWatch Logs 보관 | `/aws/rds/instance/ticket-core-db/postgresql`의 기본 보관 기간은 "만료 없음"이다 | 콘솔에서 보관 기간을 7일로 둔다 |
| Datadog Agent(Core) | 무부하 기준 약 0.03코어(postgres·nginx·openmetrics 수집) | docker·프로세스 수집은 이미 껐다 |
