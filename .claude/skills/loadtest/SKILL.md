---
name: loadtest
description: >
  ticket 저장소에서 부하 테스트를 준비·실행하고 결과를 판정한다. 예매 오픈 재현, Core 용량 산정,
  좌석 경합 검증, Gatling 결과 해석, 대기열 방출량(advance-batch-size) 조정 요청을 받았을 때 쓴다.
allowed-tools: Bash(rg:*) PowerShell(.\gradlew.bat:*)
---

# 부하 테스트

**운영 환경에 직접 부하를 주지 않는다.** 로컬 검증은 절대 처리량보다 정합성 오류 발견을 우선한다.
Gatling 소스와 실행 옵션의 원본은 형제 저장소 `../gatling-test`다.

실제 실행은 대상 URL, 사용자 수, 투입 시간, 테스트 전용 `performanceId`를 사용자가 승인한
경우에만 한다.

## 실행 전 전제

- Ticket Server는 `8080`, Queue Server는 `8090`. Gateway는 실행하지 않는다.
- **Ticket Redis와 Queue Redis를 분리한다.** 같은 Redis를 쓰면 측정이 서로 오염된다.
- 두 서버가 같은 access token 검증 secret과 같은 admission token secret을 써야 한다.
  한쪽만 다르면 부하 실패가 아니라 인증 실패로 끝난다.
- 회차의 `entryType`이 DIRECT인지 QUEUE인지 확인한다. 측정하려는 경로와 회차 정책이 어긋나면
  admission token 검증 구간을 통과하지 않는다.
- 테스트 데이터의 `performanceId`, `seatIds`, 회원 토큰이 실제 seed 데이터와 맞아야 한다.

## 기본 흐름

1. Redis와 로컬 관측성 도구를 실행한다.
2. API 서버를 local 프로파일로 실행한다.
3. 테스트용 회원 JWT 또는 seed 회원 로그인을 준비한다.
4. Simulation 또는 정합성 JUnit 테스트를 실행한다.
5. 실패율, 응답 시간, admitted/waiting 비율, safe TPS, order 성공·거부 건수를 확인한다.

**정확한 옵션 이름과 명령은 [ticket-open-local.md](../../../docs/load-test/ticket-open-local.md)를
그대로 쓰고 외워서 조립하지 않는다.**

## 시나리오

| 시나리오 | 측정 대상 | 함께 볼 것 |
| --- | --- | --- |
| `QueueEnterSimulation` | Queue join과 enter 처리량 | 두 요청의 실패율, enter p95·p99, Queue 예외 로그 |
| `CoreAdmissionCapacitySimulation` | Core 단독 좌석·주문 처리량 | seat status·select·create order 실패율, p95·p99, 500 응답, DB pool, Redis latency |
| `TicketOpenEndToEndSimulation` | 예매 오픈 전체 흐름 | 위 두 항목 전부와 state polling 횟수 |
| `SeatContentionSimulation` | 같은 좌석 hold·order 경합 | 성공한 hold·order 수가 좌석 수를 넘지 않는지 |

`TicketOpenEndToEndSimulation`은 `coreBaseUrl`과 `queueBaseUrl`을 분리해서 넘긴다. 하나를 생략하면
그 값이 `baseUrl`로 대체돼 **엉뚱한 서버를 때린다.**

## 용량 산정 순서

대기열 방출량은 Queue Server 처리량이 아니라 **Ticket Server가 안정적으로 처리하는 입장 사용자
수**를 기준으로 잡는다.

1. `CoreAdmissionCapacitySimulation`을 단계별로 실행해 Core 단독 처리량을 측정한다.
   경계 탐색은 5, 10, 15, 20, 30, 40, 50 users/sec를 각각 별도 실행한다.
2. 실패율, p95·p99, DB connection pool, Redis latency, JVM CPU·GC를 함께 본다.
3. 안정 구간의 `admitted users/sec`에 0.6~0.7 안전계수를 적용한다.
4. Queue의 `app.queue.advance-batch-size`와 `app.queue.advance-interval-ms`를 조정해 단위 시간당
   입장량을 이 값보다 낮게 만든다.

> ⚠️ **현재 `advance-batch-size` 기본값 500은 실측으로 검증된 값이 아니다.** 이 저장소와
> `../gatling-test` 어디에도 실측 결과가 기록돼 있지 않다. 500을 근거로 삼지 말고, 조정이
> 필요하면 1번부터 다시 측정한다. 측정했다면 결과를 `docs/adr/`에 남긴다.

## 결과를 판정할 때

- **CPU에 여유가 있다는 것만으로 여유가 있다고 판정하지 않는다.** 과거 실행에서는 CPU보다
  nginx 파일 디스크립터와 DB connection pool이 먼저 포화됐다. 병목은 애플리케이션 밖에도 있다.
- `hikaricp_connections_pending`이 0보다 커지면 요청이 DB 연결을 기다리는 상태다. pending이 0이어도
  이미 빌린 연결이 DB lock에서 멈출 수 있으므로 DB wait를 따로 확인한다.
- `executor_queued_tasks`가 256에 오래 머물거나 queue 포화 경고가 반복되면 이전 회차 작업이 현재
  부하와 겹친 상태다. **backlog가 해소되기 전에 다음 부하를 넣지 않는다.**
- 지표와 PromQL은 [operations.md의 Core 용량 관측](../../../docs/operations.md#core-용량-관측)을 따른다.
- 완료 기준은 [ticket-open-local.md](../../../docs/load-test/ticket-open-local.md)의 목록을 그대로 쓴다.

## 연속 실행 격리

**회차 ID만 바꾸는 것으로는 격리되지 않는다.** 다음 실행 전에 이전 실행의 잔재를 확인한다.

- 이전 실행의 PENDING 주문이 만료됐는가
- Redis hold TTL이 끝났는가
- `ORDER_HOLD_CREATION_OUTBOX`와 `ORDER_HOLD_RELEASE_OUTBOX`의 PENDING·FAILED 건이 정리됐는가
- Queue의 entered marker가 TTL로 만료됐는가

정리되지 않은 상태에서 다음 부하를 넣으면 실패 원인이 이번 실행인지 이전 실행인지 구분할 수 없다.

## 결과 파일을 다룰 때

Gatling HTML 리포트는 `../gatling-test/load-tests/gatling/build/reports/gatling`에, 분산 실행 결과는
`../gatling-test/distributed-results-join/`에 생긴다. **둘 다 git 추적 대상이 아니다.**

- Windows 경로 길이 제한(260자)에 걸리기 쉽다. 탐색기나 셸 삭제·이동으로 다루면 일부만 처리되고
  **조용히 실패한다.**
- **지우거나 옮기기 전에 `tar`로 백업한다.** 대량 파일 조작은 `tar`, `find`, python처럼 긴 경로를
  다룰 수 있는 도구로 한다.
- 추적 대상이 아니므로 지우면 복구 경로가 없다. **사용자 확인 없이 정리하지 않는다.**
