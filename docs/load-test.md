# 부하 테스트 기준

이 문서는 부하 테스트 문서의 진입점이다.

현재 Gatling 소스와 실행 기준은 형제 저장소 `../gatling-test`에 있다.

## 목적

로컬 환경에서 예매 오픈 순간의 핵심 위험을 재현한다.

- 대기열 진입 처리량
- 대기열 토큰 발급과 상태 조회 흐름
- 티켓 서버 단독 좌석/주문 처리량
- 같은 좌석 order/hold 경합

운영 환경에는 직접 부하 테스트를 실행하지 않는다. 운영과 가까운 처리량은 별도 스테이징 환경에서 같은 시나리오를 재사용해 확인한다.

## 상세 문서

현재 상세 실행 절차는 아래 문서에 있다.

- [load-test/ticket-open-local.md](load-test/ticket-open-local.md)

## 기본 실행 흐름

1. Redis와 필요한 로컬 관측성 도구를 실행한다.
2. API 서버를 local 프로파일로 실행한다.
3. 테스트용 회원 JWT 또는 seed 회원 로그인을 준비한다.
4. Gatling Simulation 또는 관련 JUnit 정합성 테스트를 실행한다.
5. 실패율, 응답 시간, 대기열 admitted/waiting 비율, ticket server safe TPS, order 성공/거부 건수를 확인한다.

## 관련 명령

```powershell
.\gradlew.bat :core:core-api:bootRun
.\gradlew.bat :core:core-api:test --tests "com.ticket.core.config.seed.SeedDataLoaderTest"
cd ..\gatling-test
.\gradlew.bat -p load-tests/gatling gatlingClasses
```

## 티켓 서버 용량 산정 순서

대기열 스케줄러 방출량은 Queue Server 처리량이 아니라 Ticket Server가 안정적으로 처리하는 입장 사용자 수를 기준으로 잡는다.

1. `CoreAdmissionCapacitySimulation`을 단계별로 실행해 Ticket Server 단독 처리량을 측정한다.
2. 실패율, p95/p99, DB connection pool, Redis latency, JVM CPU/GC를 함께 본다.
3. 안정 구간의 `admitted users/sec` 또는 요청 TPS에 0.6~0.7 안전계수를 적용한다.
4. Queue Scheduler의 `app.queue.advance-batch-size`와 `app.queue.advance-interval-ms`를 조정해 단위 시간당 입장량을 이 값보다 낮게 설정한다.

현재 Queue Scheduler는 `app.queue.advance-interval-ms` 주기로 대기 중인 공연을 순회하고, 공연마다 최대 `app.queue.advance-batch-size`만큼 shard별 closed slot의 `servingSeq`를 전진시킨다. Queue API의 `app.queue.shopping-session-ttl`은 입장 후 entered marker와 admission token의 유효 시간이다.

## 실행 전 전제

- Ticket Server는 `8080`, Queue Server는 `8090`에서 실행한다. Gateway는 실행하지 않는다.
- **Ticket Redis와 Queue Redis를 분리한다.** 같은 Redis를 쓰면 측정이 서로 오염된다.
- 두 서버가 같은 access token 검증 secret과 같은 admission token secret을 사용해야 한다.
  한쪽만 다르면 부하 실패가 아니라 인증 실패로 끝난다.
- 회차의 `entryType`이 DIRECT인지 QUEUE인지 확인한다. 측정하려는 경로와 회차 정책이 어긋나면
  admission token 검증 구간을 통과하지 않는다.

## 시나리오와 확인 항목

| 시나리오 | 측정 대상 | 함께 볼 것 |
| --- | --- | --- |
| `QueueEnterSimulation` | Queue join과 enter 처리량 | 두 요청의 실패율, enter p95·p99, Queue Server 예외 로그 |
| `CoreAdmissionCapacitySimulation` | Core 단독 좌석·주문 처리량 | seat status·select seat·create order 실패율, p95·p99, 500 응답, DB connection pool, Redis latency |
| `TicketOpenEndToEndSimulation` | 예매 오픈 전체 흐름 | 위 두 항목 전부와 state polling 횟수 |
| `SeatContentionSimulation` | 같은 좌석 hold·order 경합 | 성공한 hold·order 수가 좌석 수를 넘지 않는지 |

`TicketOpenEndToEndSimulation`은 `coreBaseUrl`과 `queueBaseUrl`을 분리해서 넘긴다. 하나를 생략하면
그 값이 `baseUrl`로 대체돼 엉뚱한 서버를 때린다. 정확한 옵션 이름과 명령은
[load-test/ticket-open-local.md](load-test/ticket-open-local.md)를 그대로 사용하고 외워서 조립하지 않는다.

## 결과를 판정할 때

- **CPU에 여유가 있다는 것만으로 여유가 있다고 판정하지 않는다.** 과거 실행에서는 CPU보다
  nginx 파일 디스크립터와 DB connection pool이 먼저 포화됐다. 병목은 애플리케이션 밖에도 있다.
- `hikaricp_connections_pending`이 0보다 커지면 요청이 DB 연결을 기다리는 상태다. 다만 pending이 0이어도
  이미 빌린 연결이 DB lock에서 멈출 수 있으므로 DB wait를 따로 확인한다.
- `executor_queued_tasks`가 256에 오래 머물거나 queue 포화 경고가 반복되면 이전 회차 작업이 현재 부하와
  겹친 상태다. outbox scheduler가 보정하지만 **backlog가 해소되기 전에 다음 부하를 넣지 않는다.**
- 관측할 지표와 PromQL은 [operations.md의 Core 용량 관측](operations.md#core-용량-관측)을 따른다.
- 완료 기준은 [load-test/ticket-open-local.md](load-test/ticket-open-local.md)의 목록을 그대로 사용한다.
  특히 QUEUE 회차에서 admission token 없이 보호 API가 거부되는지, DIRECT 회차에서는 진입하는지 함께 확인한다.

## 연속 실행 격리

**회차 ID만 바꾸는 것으로는 격리되지 않는다.** 다음 실행 전에 이전 실행의 잔재를 같은 시간축으로 확인한다.

- 이전 실행의 PENDING 주문이 만료됐는가
- Redis hold TTL이 끝났는가
- `ORDER_HOLD_CREATION_OUTBOX`와 `ORDER_HOLD_RELEASE_OUTBOX`의 PENDING·FAILED 건이 정리됐는가
- Queue의 entered marker가 TTL로 만료됐는가

정리되지 않은 상태에서 다음 부하를 넣으면 실패의 원인이 이번 실행인지 이전 실행인지 구분할 수 없다.

## 결과 파일을 다룰 때

Gatling HTML 리포트는 `../gatling-test`의 `load-tests/gatling/build/reports/gatling`에 생성된다.
분산 실행 결과는 git으로 추적되지 않고 경로가 매우 길다.

- Windows 경로 길이 제한(260자)에 걸리기 쉽다. 일반 파일 탐색기나 셸 삭제·이동으로 다루면
  일부만 처리되고 조용히 실패할 수 있다.
- **결과 디렉터리를 지우거나 옮기기 전에 `tar`로 백업한다.** 대량 파일 조작은 `tar`, `find`, python처럼
  긴 경로를 다룰 수 있는 도구로 한다.
- 결과가 git 추적 대상이 아니므로 지우면 복구 경로가 없다. 사용자 확인 없이 정리하지 않는다.

## 주의점

- 운영 환경에 직접 부하를 주지 않는다.
- 로컬 검증은 절대 처리량보다 정합성 오류 발견을 우선한다.
- 테스트 데이터의 `performanceId`, `seatIds`, 회원 토큰은 실제 seed 데이터와 맞아야 한다.
