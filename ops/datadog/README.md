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
- RDS 파라미터 그룹 `ticket-core-pg18`을 만들어 두었다. 연결·재부팅은 순서서 1단계.
