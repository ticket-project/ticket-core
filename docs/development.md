# 개발 기준

이 문서는 현재 코드 기준 개발 맥락을 정리한다. 상세 구조는 [architecture.md](architecture.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 요약

Ticket은 공연/전시 티켓팅 백엔드다. 현재 구현의 중심은 아래 흐름이다.

- 인증/회원: 이메일 회원가입, 로그인, JWT 갱신, OAuth2 로그인 URL 조회 및 토큰 교환
- 공연/전시 조회: 쇼, 장르, 메타 코드, 회차/좌석 레이아웃 조회
- 좌석 선택: Redis TTL 기반 임시 선택 상태와 WebSocket 전파
- 좌석 선점: Redis 기반 hold와 주문 시작 흐름
- 주문: `PENDING` 주문 생성, 조회, 취소, 만료 처리
- 대기열: Ticket Server가 회차별 DIRECT/QUEUE를 결정하고, `ticket-queue` 별도 서비스가 shard/local sequence와 public state 기반 대기 상태 및 admission token 발급을 담당

결제 승인/실패/콜백과 최종 판매 확정 흐름은 아직 별도 구현 대상이다.

## 주요 API 흐름

### 인증

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/social/urls`
- `POST /api/v1/auth/oauth2/token`

구현 기준:

- API 인증은 JWT 기반 stateless 방식이다.
- OAuth2 인가 흐름은 별도 filter chain에서 처리한다.
- Refresh token과 OAuth2 1회용 코드는 Redis를 사용한다.
- 공개 GET API를 제외한 대부분 API는 인증이 필요하다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/AuthController.java`
- `core/core-api/src/main/java/com/ticket/core/config/security`
- `core/core-domain/src/main/java/com/ticket/core/domain/auth`
- `core/core-infra/src/main/java/com/ticket/core/infra/auth`

### 쇼/회차/좌석 조회

- 쇼 목록, 최신 쇼, 오픈 예정 쇼, 검색, 상세 조회
- 쇼 기준 좌석 정보와 공연장 레이아웃 조회
- 회차별 좌석 상태 조회
- 회차별 등급별 잔여 좌석 수 조회

구현 기준:

- 좌석 상태는 DB 상태와 Redis 점유 상태를 합쳐 계산한다.
- 잔여 좌석 수는 Redis `SELECTING`, `HOLDING` 상태를 반영한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/ShowController.java`
- `core/core-api/src/main/java/com/ticket/core/api/controller/PerformanceController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/show`
- `core/core-domain/src/main/java/com/ticket/core/domain/performance`
- `core/core-domain/src/main/java/com/ticket/core/domain/performanceseat`

### 좌석 선택

- `POST /api/v1/performances/{performanceId}/seats/{seatId}/select`
- `DELETE /api/v1/performances/{performanceId}/seats/{seatId}/select`
- `DELETE /api/v1/performances/{performanceId}/seats/select`

정책:

- selection은 UX 보조 상태다.
- hold의 필수 선행 조건이 아니다.
- 다른 사용자가 selection 중이어도 hold가 성공할 수 있다.
- selection은 Redis TTL로 자동 만료된다.
- 만료 시 Redis expired listener가 `DESELECTED` 이벤트를 전파한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/SeatSelectionController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/performanceseat/command`
- `core/core-infra/src/main/java/com/ticket/core/infra/performanceseat`

### 좌석 선점과 주문 시작

- `POST /api/v1/orders` (canonical)
- `POST /api/v1/performances/{performanceId}/holds` (`@Deprecated`, 내부적으로 동일한 `CreateOrderUseCase`로 위임)

현재 흐름:

1. 회차 유효성, 좌석 유효성, 수량 제한 검증
2. 같은 회원/같은 회차 `PENDING` 주문 여부 검증
3. Redis에 좌석 hold 생성
4. DB에 `PENDING` 주문 생성
5. hold history 기록
6. DB 커밋과 connection 반환
7. 제한된 background worker에서 selection 해제와 HELD 상태 전파
8. 201 Created와 X-Order-Key 헤더로 주문 식별자 반환

정책:

- 회차당 같은 회원은 `PENDING` 주문 1건만 허용한다.
- hold는 다중 좌석 all-or-nothing으로 생성한다.
- hold는 Redis TTL 만료와 주문 만료 흐름에 연결된다.
- 주문 저장 트랜잭션 안에서는 Redis 또는 WebSocket을 호출하지 않는다.
- 생성 후처리 실패는 이미 커밋된 주문과 hold를 되돌리지 않는다.
- 대기열이 필요한 회차는 `X-Admission-Token` 검증을 먼저 통과해야 한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- `core/core-api/src/main/java/com/ticket/core/api/controller/HoldController.java` (deprecated)
- `core/core-domain/src/main/java/com/ticket/core/domain/hold`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/create`
- `core/core-infra/src/main/java/com/ticket/core/infra/hold`

### 주문

- `GET /api/v1/orders/{orderKey}`
- `DELETE /api/v1/orders/{orderKey}`

현재 구현:

- 주문 상세 조회
- 사용자 취소
- 스케줄러 기반 만료
- Redis expired listener 기반 즉시 만료
- hold release outbox 기반 후처리 보강

- 취소와 만료는 OrderTerminationService의 공통 종료 절차를 사용한다.
- 주문 상태, hold history, hold release outbox는 같은 DB 트랜잭션에서 기록한다.
- outbox의 Redis/WebSocket 처리는 DB 트랜잭션 밖에서 실행한다.
- Redis 만료 listener와 주문 background worker는 각각 동시 실행 수를 제한한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/order`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release`
- core/core-infra/src/main/java/com/ticket/core/infra/order
- docs/core-booking-lifecycle.md

### 대기열

대기열 런타임은 `ticket-queue` 독립 서비스가 담당한다. `ticket-be`는 Queue Controller, queue token 저장소, queue token 만료 핸들러를 갖지 않는다. 대신 `PerformanceQueuePolicy`로 공연 상세 응답의 회차별 `entryType`을 계산하고, 클라이언트가 예매 버튼 클릭 시 DIRECT/QUEUE를 분기한다.

Queue Server hot path는 Core DB와 회차별 정책 snapshot을 조회하지 않는다. Queue Server는 모든 요청 회차에 애플리케이션 기본 입장 속도와 TTL을 적용하며, `join`에서 받은 `shardId`와 `localSeq`를 public `/state` 응답의 `serving[shardId]`와 비교해 입장 가능 여부를 판단한다.
Core의 admission 검증은 `memberId`, `performanceId`뿐 아니라 Queue가 넣은 `queueId` claim도 읽는다. QUEUE 흐름에서 주문 생성(호환용 hold endpoint 포함)이 성공하면 Queue Server의 내부 완료 API를 비동기로 호출해 active session을 조기 반환한다. 이 알림은 선택 기능이며 실패해도 주문을 되돌리지 않고 Queue의 shopping session TTL에 정리를 맡긴다.


주요 개념:

- 서명된 queue token (`X-Queue-Token`)
- shard/local sequence와 public state
- public state 기반 adaptive polling
- admission token
- admission token으로 보호 API 진입
- show detail entryType

주요 위치:

- 형제 저장소 `../ticket-queue`
- `core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenValidator.java`
- `core/core-api/src/main/java/com/ticket/core/config/admission/QueueSessionCompletionNotifier.java`
- `core/core-api/src/main/java/com/ticket/core/config/admission/TicketQueueCompletionProperties.java`
- 형제 저장소 `../ticket-queue`의 내부 session 완료 API
- `core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenService.java`

## 핵심 도메인 모델

### Order

- 내부 PK: `id`
- 외부 식별자: `orderKey`
- 주요 상태: `PENDING`, `CONFIRMED`, `EXPIRED`, `CANCELED`, `PAYMENT_FAILED`

주문 상태 모델은 결제 성공/실패를 수용할 수 있지만, 실제 결제 유스케이스는 아직 별도 구현 대상이다.

### Hold

- Redis 기반 임시 점유 상태
- DB에는 `HOLD_HISTORY` 이력 저장
- 주문 시작과 만료/취소 후처리에 직접 연결

### Selection

- Redis TTL 기반 UX 보조 상태
- 실제 점유 권리는 hold가 담당

### Queue

- 대기열 상태는 `ticket-queue`가 관리한다.
- `ticket-be`는 예매 API 진입 시 회차 정책을 먼저 확인하고, 대기열이 필요한 회차에서만 `X-Admission-Token`의 서명, 만료, memberId와 performanceId 일치 여부를 검증한다.
- `ticket-be`의 Redis는 좌석 선택, hold, refresh token, OAuth2 one-time auth code 용도로만 사용한다.

## 미구현 또는 후속 범위

- 결제 도메인, controller, callback, PG 연동
- 결제 성공 시 주문 확정과 최종 좌석 판매 확정
- Flyway 기반 운영 마이그레이션 스크립트 누적과 검증 환경 보강
- Redis key scan 기반 조회 구조 최적화
- 운영 관측성 대시보드와 알림 보강

## 개발 시 주의점

- Controller에는 비즈니스 규칙이나 직접 저장소 접근을 넣지 않는다.
- Redis, WebSocket, 외부 HTTP, AOP 구현은 `core-infra`에 둔다.
- 도메인/application 코드는 port 인터페이스에 의존한다.
- hold, order, performanceseat, queue 변경은 동시성, TTL, 만료 후처리, 테스트 공백을 먼저 확인한다.
- API 요청/응답을 바꾸면 하위 호환성과 Swagger 문서 영향을 함께 본다.

## 커밋과 PR 컨벤션

커밋 메시지와 PR 제목은 Conventional Commits를 기반으로 작성한다. 이 규칙은 사람과 AI 에이전트 모두에게 동일하게 적용한다.

### 기본 형식

```text
<type>(<scope>): <한국어 설명>
```

`scope`는 선택 사항이다.

```text
feat(order): 결제 대기 주문 생성
fix(performanceseat): 만료된 좌석 선택 상태 정리
refactor(order): 주문 생성 보상 흐름 분리
perf(core): 좌석 조회 병목 완화
test(order): 주문 생성 동시성 테스트 추가
docs: 커밋 및 PR 컨벤션 문서화
```

### type

`type`은 변경 파일의 종류가 아니라 변경 목적을 기준으로 선택한다.

| type | 사용 기준 |
| --- | --- |
| `feat` | 새로운 기능 또는 외부 동작 추가 |
| `fix` | 잘못된 동작이나 결함 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `perf` | 응답 시간, 쿼리, 락, 메모리 등 성능 개선 |
| `test` | 테스트만 추가하거나 수정 |
| `docs` | 문서만 변경 |
| `chore` | 제품 동작과 무관한 유지보수 작업 |
| `build` | 빌드 설정 또는 의존성 변경 |
| `ci` | CI 워크플로우 변경 |
| `security` | 인증, 권한 또는 보안 정책 강화 |
| `revert` | 기존 변경 되돌리기 |

예를 들어 주문 코드를 변경했더라도 목적에 따라 type이 달라진다.

```text
새로운 주문 API 추가              -> feat(order)
주문 실패 처리 오류 수정         -> fix(order)
동작을 유지하며 주문 클래스 분리 -> refactor(order)
DB 커넥션 점유 시간 단축         -> perf(order)
```

### scope

`scope`는 변경의 주된 책임 영역을 나타낸다. 다음 순서로 가장 작은 적절한 범위를 선택한다.

1. 하나의 도메인 변경이면 도메인 이름을 사용한다.
2. 여러 도메인에 걸친 모듈 변경이면 모듈 이름을 사용한다.
3. 여러 Core 도메인과 모듈에 걸친 변경이면 `core`를 사용한다.
4. 저장소 전체 작업으로 특정 범위를 정하기 어렵다면 scope를 생략한다.

권장 도메인 scope:

```text
auth, member, show, performance, performanceseat, hold, order, queue
```

권장 모듈·기술 scope:

```text
core, core-api, core-domain, core-infra, redis, logging, seed, tools, ci, review, codex
```

기존 scope로 표현할 수 있으면 새로운 scope를 임의로 만들지 않는다. 새로운 scope가 필요하면 실제 도메인, 모듈 또는 안정적인 하위 시스템 이름을 사용한다.

### 설명과 본문

- 설명은 한국어로 작성하고 마침표를 붙이지 않는다.
- 기술 고유명사와 제품명은 `Redis`, `WebSocket`, `Flyway`처럼 원문 표기를 허용한다.
- `수정`, `개선`, `작업`처럼 대상이 드러나지 않는 표현만 사용하지 않는다.
- `추가`, `수정`, `분리`, `축소`, `최적화`처럼 변경 결과가 드러나는 표현을 사용한다.
- 하나의 커밋에는 하나의 목적만 포함한다. 목적이 다르면 커밋을 분리한다.

코드만 보고 이유를 알기 어려운 변경은 빈 줄 다음에 한국어 본문을 추가한다.

```text
perf(order): 주문 생성 트랜잭션 범위 축소

Redis 좌석 선점 중 DB 커넥션을 점유하지 않도록
주문과 선점 이력 저장 구간만 트랜잭션으로 분리한다.
```

호환성을 깨는 변경은 type 또는 scope 뒤에 `!`를 붙이고 본문 하단에 `BREAKING CHANGE:`를 작성한다.

```text
feat(order)!: 주문 생성 응답 형식 변경

BREAKING CHANGE: 기존 holdKey 응답 필드를 orderKey로 대체한다.
```

### PR

- PR 제목도 커밋과 동일한 `<type>(<scope>): <한국어 설명>` 형식을 사용한다.
- PR의 type과 scope는 개별 파일이 아니라 PR 전체의 주된 목적과 영향 범위를 기준으로 선택한다.
- 성능 코드에 테스트와 문서가 함께 포함돼도 주된 목적이 성능 개선이면 `perf`를 사용한다.
- 서로 관계없는 기능, 버그 수정, 리팩터링이 섞이면 하나의 포괄적인 제목을 만들지 말고 PR을 분리한다.
- PR 본문은 `.github/pull_request_template.md`의 변경 목적, 주요 변경 내용, 영향 범위, 테스트와 롤백 항목을 작성한다.

### AI 에이전트 작업 규칙

- 기존 히스토리를 추측만으로 모방하지 말고 이 문서의 type, scope, 언어 규칙을 우선 적용한다.
- type은 변경 목적, scope는 주된 책임 영역을 기준으로 선택한다.
- 여러 커밋을 만들 때는 작업 책임별로 분리하고 각각의 메시지를 독립적으로 작성한다.
- 이미 푸시한 커밋 메시지를 변경하면 커밋 해시와 이후 이력이 바뀐다는 점을 설명하고 사용자 승인 후 진행한다.
- force push가 필요하면 원격이 예상한 상태일 때만 갱신하는 `--force-with-lease`를 사용한다.
