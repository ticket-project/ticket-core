# Ticket Open Load Test Design

> 기준 변경: 이 문서는 과거 ticket-be 내장 queue/queueToken 설계 기록이다. 현재 실행 기준은 2026-05-24-separated-queue-server-design.md와 2026-05-24-separated-queue-server.md이다. 새 구조에서는 queueToken/X-Queue-Token 대신 queueSessionId/X-Queue-Session과 admissionToken/X-Admission-Token을 사용한다.


**목표**

- 예매 오픈 시점의 대량 유입을 로컬에서 재현한다.
- 대기열 처리량, 대기열 토큰 강제, 좌석 홀드 정합성을 함께 검증한다.
- 같은 테스트 자산을 이후 스테이징 환경에서도 재사용할 수 있게 한다.

**적용 범위**

- 포함: queue enter/status 부하, queue token gate 적용, hold/order 동시성 검증, Gatling Java DSL 로컬 부하 시뮬레이션, JUnit 정합성 테스트, 실행 문서
- 제외: 실제 결제, 운영 환경 직접 부하 테스트, 관리자 UI, 운영 모니터링 대시보드

## 1. 설계 원칙

- 운영 환경에는 직접 부하를 주지 않는다.
- 로컬 검증은 절대 처리량보다 정합성 오류 발견을 우선한다.
- HTTP 부하 특성과 도메인 정합성은 분리해서 검증한다.
- 대기열은 독립 기능이 아니라 좌석 조회와 홀드 생성 앞단의 진입 제어로 검증한다.

## 2. 접근 방법

권장 접근은 `Gatling Java DSL + JUnit` 병행이다.

- `Gatling Java DSL`
  - 실제 HTTP 요청으로 대기열 진입과 홀드 요청 부하를 만든다.
  - 응답 시간, 실패율, 처리량을 측정한다.
  - Java/Spring 개발자가 Gradle로 실행하고 유지보수하기 쉽게 한다.
- `JUnit`
  - 같은 좌석 동시 홀드 시 성공 주문이 1건만 생기는지 검증한다.
  - 대기열 동시 진입 시 `ADMITTED` 수가 정책 한도를 넘지 않는지 검증한다.
  - 대기열 토큰이 없거나 잘못되면 좌석/홀드 API 접근이 거부되는지 검증한다.

## 3. 대기열 토큰 강제

현재 `QueueAdmissionInterceptor`는 존재하지만 `WebConfig`에서 등록이 주석 처리되어 있다. 이번 작업에서는 이 게이트를 실제 요청 흐름에 포함한다.

적용 대상은 다음과 같다.

- 좌석 상태 조회 API
  - `GET /api/v1/performances/{performanceId}/seats/status`
- 좌석 잔여 수 조회 API
  - `GET /api/v1/performances/{performanceId}/seats/availability`
- 홀드/주문 시작 API
  - `POST /api/v1/performances/{performanceId}/holds`

구현 방향은 아래 기준을 따른다.

- `QueueAdmissionInterceptor`를 MVC interceptor로 등록한다.
- 모든 `/api/v1/performances/*/**` 요청을 무조건 막지 않고, `@RequireQueueAdmission`이 붙은 컨트롤러 메서드만 검사한다.
- 대기열 정책이 비활성화된 회차는 기존처럼 통과시킨다.
- 정책이 활성화된 회차는 `X-Queue-Token` 헤더를 요구한다.
- 토큰이 없으면 `QUEUE_TOKEN_REQUIRED`, 유효하지 않으면 `QUEUE_TOKEN_INVALID`를 반환한다.

## 4. 정합성 테스트

### 4.1 대기열 동시 진입

- 정책의 `maxActiveUsers`를 작게 둔다.
- 여러 회원이 같은 `performanceId`로 동시에 `enter`를 호출한다.
- 검증:
  - `ADMITTED` 수가 `maxActiveUsers`를 넘지 않는다.
  - 나머지는 `WAITING` 상태가 된다.
  - waiting position이 중복되거나 비정상적으로 비지 않는다.

### 4.2 대기열 게이트

- 토큰 없이 좌석 상태/잔여 수/홀드 API를 호출하면 거부된다.
- 잘못된 토큰으로 호출하면 거부된다.
- 정상 토큰으로 호출하면 다음 계층으로 진입한다.
- 대기열이 `FORCE_OFF` 또는 정책상 비활성화된 경우 토큰 없이 통과한다.

### 4.3 좌석 홀드 경합

- 같은 `performanceId`와 같은 `seatId`에 대해 여러 회원이 동시에 hold를 시도한다.
- 검증:
  - 성공 주문은 정확히 1건이다.
  - 실패 요청은 좌석 점유 또는 주문 시작 관련 예외로 종료된다.
  - Redis hold 상태와 DB 주문 상태가 서로 어긋나지 않는다.

## 5. Gatling 시나리오

Gatling 프로젝트는 루트 빌드와 분리된 `load-tests/gatling` 독립 Gradle 프로젝트로 둔다.

- `QueueEnterSimulation`
  - 여러 가상 사용자가 같은 회차의 대기열에 진입한다.
  - `ADMITTED/WAITING` 비율과 응답 시간을 측정한다.
- `HoldRaceSimulation`
  - 여러 가상 사용자가 같은 좌석 또는 제한된 좌석 풀을 대상으로 hold를 시도한다.
  - 성공률, 충돌 실패율, 응답 시간을 측정한다.
- `TicketOpenFlowSimulation`
  - enter 후 admitted 토큰을 받은 사용자만 좌석 조회와 hold를 시도한다.
  - 로컬에서는 짧은 시간, 낮은 VU 기준으로 반복 실행한다.

초기 로컬 기준은 보수적으로 잡는다.

- queue enter: 100~300 VU
- hold race: 50~100 VU
- duration: 30~60초

## 6. 실행 문서

문서는 `docs/load-test/ticket-open-local.md`에 둔다.

문서에는 아래 내용을 포함한다.

- Redis 실행 방법
- API 서버 실행 방법
- 테스트 데이터 전제
- Gatling Gradle 실행 방법
- JUnit 테스트 명령
- Gatling Simulation 실행 예시
- 성공 기준과 결과 해석 방법
- 스테이징에서 재사용할 때 바꿀 환경 변수

## 7. 성공 기준

- 전체 Gradle 테스트가 통과한다.
- 대기열 동시 진입 테스트에서 `ADMITTED` 수가 정책 한도를 넘지 않는다.
- 대기열 토큰 없는 좌석/홀드 접근이 거부된다.
- 정상 대기열 토큰이 있으면 보호 대상 API에 접근할 수 있다.
- 같은 좌석 동시 홀드에서 성공 주문은 1건만 생성된다.
- Gatling Simulation은 로컬 API URL과 테스트 토큰/회원 정보를 Gradle system property로 받아 실행할 수 있다.

## 8. 남은 리스크

- 로컬 H2와 운영 RDB의 락/트랜잭션 특성은 다르다. 로컬 결과는 정합성 기준선으로 보고, 절대 처리량은 스테이징에서 다시 확인해야 한다.
- 대량 HTTP 부하를 로컬 한 장비에서 발생시키면 클라이언트와 서버가 같은 자원을 공유한다. 응답 시간은 참고값으로만 해석한다.
- 대기열 토큰 강제는 기존 클라이언트 요청 흐름에 영향을 준다. 프론트는 좌석 조회와 홀드 전에 `X-Queue-Token`을 보내야 한다.
