# Performance Queue Design

> 기준 변경: 이 문서는 과거 ticket-be 내장 queue/queueToken 설계 기록이다. 현재 실행 기준은 2026-05-24-separated-queue-server-design.md와 2026-05-24-separated-queue-server.md이다. 새 구조에서는 queueToken/X-Queue-Token 대신 queueSessionId/X-Queue-Session과 admissionToken/X-Admission-Token을 사용한다.


**목표**

- 회차 좌석 페이지 진입 전단에 `performance` 단위 대기열을 도입한다.
- 기존 `selection -> hold -> order` 흐름을 유지하면서 인기 회차의 초과 유입을 제어한다.
- 운영자가 회차별로 대기열 사용 여부와 허용 인원을 조정할 수 있게 한다.

**적용 범위**

- 포함: 회차 진입 대기열, 입장 토큰 검증, 운영 정책 저장, Redis 실시간 상태 관리
- 제외: 결제 연계, 관리자 UI, 장기 이력 적재, Kafka/외부 큐 연동

## 1. 아키텍처 원칙

대기열은 `좌석 페이지 진입권`만 관리한다.

- 좌석 점유와 주문 생성은 기존 `hold/order` 도메인이 담당한다.
- 대기열은 회차별 과도한 동시 진입을 제어하는 앞단 게이트로만 동작한다.
- 실시간 상태는 Redis, 운영 정책은 RDB 로 분리한다.

## 2. 정책

- 대기열 단위: `performance`
- 진입 시점: 회차 선택 후 좌석 조회 페이지 이동 시점
- 기본 정책: 모든 회차 대기열 활성
- 운영자 override: 회차별 `AUTO`, `FORCE_ON`, `FORCE_OFF`
- 순서 정책: `FIFO`
- 재접속 정책: 새로고침/재접속 시 다시 대기
- 동시 입장 기본값: `300`
- 입장 토큰 TTL 기본값: `600초`

## 3. 저장 전략

### 3.1 RDB

회차별 운영 설정만 영속 저장한다.

- 테이블: `performance_queue_policy`
- 주요 컬럼:
  - `performance_id`
  - `queue_mode`
  - `queue_level`
  - `max_active_users`
  - `entry_token_ttl_seconds`
  - `preopen_queue_start_at`
  - `waiting_room_message`
  - `reason`

전역 기본값은 애플리케이션 설정으로 유지하고, 테이블은 override 역할만 맡긴다.

### 3.2 Redis

실시간 큐 상태와 입장권은 Redis 에 둔다.

- `queue:performance:{performanceId}:waiting`
  - `ZSET`
  - score: 순번
  - member: `queueEntryId`
- `queue:entry:{queueEntryId}`
  - `HASH`
  - 대기 엔트리 상세
- `queue:performance:{performanceId}:active`
  - `SET`
  - member: `queueToken`
- `queue:token:{queueToken}`
  - `HASH + TTL`
  - 입장권 상세
- `queue:performance:{performanceId}:seq`
  - `INCR`
  - 순번 발급

## 4. 요청 흐름

### 4.1 진입

1. 사용자가 회차를 선택한다.
2. `POST /api/v1/queue/performances/{performanceId}/enter` 호출
3. 정책을 계산한다.
4. active 수가 허용 인원 미만이면 즉시 토큰을 발급한다.
5. 아니면 waiting 큐에 넣고 대기번호를 반환한다.

### 4.2 대기 상태 조회

- `GET /api/v1/queue/performances/{performanceId}/status`
- 현재 상태, 순번, 추정 대기시간 또는 입장 토큰을 반환한다.

### 4.3 좌석 페이지 접근

- `GET /api/v1/performances/{performanceId}/seats`
- 헤더 `X-Queue-Token` 을 요구한다.
- 토큰이 유효하지 않으면 접근을 거부한다.

## 5. 승격과 만료

- 입장 토큰 만료 또는 사용자의 명시적 이탈 시 active 집합에서 제거한다.
- 그 직후 waiting 큐의 가장 앞 사용자를 승격한다.
- 승격은 분산 락으로 보호해 중복 입장을 막는다.
- Redis key expiration listener 와 보조 스케줄러를 같이 둬서 누락을 보정한다.

## 6. API

- `POST /api/v1/queue/performances/{performanceId}/enter`
- `GET /api/v1/queue/performances/{performanceId}/status`
- `GET /api/v1/queue/performances/{performanceId}/stream`
- `POST /api/v1/queue/performances/{performanceId}/leave`
- `GET /api/v1/admin/queue/performances/{performanceId}/policy`
- `PUT /api/v1/admin/queue/performances/{performanceId}/policy`

## 7. 테스트 전략

- `QueueEntryUseCase`
  - 빈 자리 있으면 즉시 입장
  - 빈 자리 없으면 대기열 등록
  - `FORCE_OFF` 면 큐 없이 통과
- `QueueTokenGatekeeper`
  - 토큰 없을 때 거부
  - 만료 토큰 거부
  - 회차 불일치 거부
- `QueueAdvanceProcessor`
  - active 회수 후 다음 순번 승격
  - 중복 승격 방지

## 8. 남은 리스크

- Redis 만료 이벤트 누락 시 즉시 승격이 지연될 수 있다.
- `estimatedWaitSeconds` 는 운영 데이터가 없으면 부정확할 수 있다.
- 운영자 UI 가 없으면 초기에는 API 또는 DB 직접 수정으로 정책을 바꿔야 한다.
