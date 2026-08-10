# 부하 테스트 기준

이 문서는 부하 테스트 문서의 진입점이다.

현재 Gatling 소스와 실행 기준은 형제 저장소 `../gatling-test`에 있다. 이 저장소의 `load-tests/gatling`은 이전 API 계약을 사용하는 보관본이므로 새 부하 테스트에 사용하지 않는다.

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

## 주의점

- 운영 환경에 직접 부하를 주지 않는다.
- 로컬 검증은 절대 처리량보다 정합성 오류 발견을 우선한다.
- 테스트 데이터의 `performanceId`, `seatIds`, 회원 토큰은 실제 seed 데이터와 맞아야 한다.
