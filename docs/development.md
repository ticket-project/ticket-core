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
- hold creation/release outbox 기반 후처리 보강

- 취소와 만료는 OrderTerminationService의 공통 종료 절차를 사용한다.
- 주문 생성 시 PENDING 주문, hold history, hold creation outbox를 같은 DB 트랜잭션에서 기록한다.
- 주문 종료 시 상태 전이, hold history, hold release outbox를 같은 DB 트랜잭션에서 기록한다.
- outbox의 Redis/WebSocket 처리는 DB 트랜잭션 밖에서 실행한다.
- Redis 만료 listener와 주문 background worker는 각각 동시 실행 수를 제한한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/order`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release`
- core/core-infra/src/main/java/com/ticket/core/infra/order
- docs/core-booking-lifecycle.md

### 대기열

대기열 런타임은 `ticket-queue` 독립 서비스가 담당한다. Core는 Queue Controller, queue token 저장소, queue token 만료 핸들러를 갖지 않는다. 대신 `PerformanceQueuePolicy`로 공연 상세 응답의 회차별 `entryType`을 계산하고, 클라이언트가 예매 버튼 클릭 시 DIRECT/QUEUE를 분기한다.

Queue Server hot path는 Core DB와 회차별 정책 snapshot을 조회하지 않는다. Queue Server는 모든 요청 회차에 애플리케이션 기본 입장 속도와 TTL을 적용하며, `join`에서 받은 `shardId`와 `localSeq`를 public `/state` 응답의 `serving[shardId]`와 비교해 입장 가능 여부를 판단한다.
Core는 Queue가 발급한 admission token의 서명, issuer, audience, scope, 만료 시각과 `memberId`, `performanceId` 일치 여부를 검증한다. 주문 생성 후 Queue Server에 session 완료 요청을 보내지는 않으며, 입장 후 shopping session은 Queue Server의 TTL로 정리된다.

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
- `core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenService.java`
- `core/core-api/src/main/java/com/ticket/core/config/admission/TicketAdmissionTokenProperties.java`

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
- Core는 예매 API 진입 시 회차 정책을 먼저 확인하고, 대기열이 필요한 회차에서만 `X-Admission-Token`의 서명, 만료, memberId와 performanceId 일치 여부를 검증한다.
- Core의 Redis는 좌석 선택, hold, refresh token, OAuth2 one-time auth code 용도로만 사용한다.

## 미구현 또는 후속 범위

- 결제 도메인, controller, callback, PG 연동
- 결제 성공 시 주문 확정과 최종 좌석 판매 확정
- Flyway 기반 운영 마이그레이션 스크립트 누적과 검증 환경 보강
- Redis key scan 기반 조회 구조 최적화
- 운영 관측성 대시보드와 알림 보강

## 작업 시작 전

1. 대상 도메인의 `command`, `query`, `model`, `repository`, `store`와 기존 테스트를 읽는다.
2. 바꿀 API의 요청·응답·오류 계약과 Swagger 문서 인터페이스를 확인한다.
3. 트랜잭션 경계, Redis key와 TTL, 만료 후처리 경로를 추적한다.
4. 같은 흐름을 검증하는 테스트가 어디에 있는지 확인한다. 없으면 그 공백을 작업 범위에 포함한다.
5. 프로파일별 설정(`application-local.yml`, `-dev.yml`, `-prod.yml`)에 영향이 있는지 본다.

## 기능 개발 순서

1. **계약 확정** — endpoint, 인증 요구, request/response, 상태 코드, 오류를 먼저 정한다.
2. **domain** — 엔티티와 도메인 규칙, 필요한 port(`store`, publisher, client)를 `core-domain`에 정의한다.
2-1. **application** — use case와 트랜잭션 경계를 `core-app`에 만든다. 도메인 규칙을 순서대로 엮는다.
3. **infrastructure** — `core-infra`에서 port를 구현한다. Redis 명령, WebSocket 발행, 외부 HTTP를 여기에 둔다.
4. **presentation** — `core-api`에서 요청 검증, principal 추출, use case 호출, 응답 매핑만 한다.
   어떤 검증을 어느 계층이 소유하는지는 [validation.md](validation.md)가 단일 기준이다.
5. **migration** — DB 구조 변경이 있으면 Flyway 새 버전 파일을 추가한다([operations.md](operations.md#db-마이그레이션)).
6. **테스트** — 도메인 규칙, use case, adapter 계약, controller 계약을 채운다([testing.md](testing.md)).
7. **검증** — 좁은 검증부터 실행한다([testing.md](testing.md#무엇을-돌릴지)).

사용하지 않는 빈 계층 파일은 만들지 않는다. 다만 Controller에 업무 규칙을 넣거나 use case를 건너뛰고
Controller가 repository를 직접 부르는 형태로 합치지 않는다.

## 계층 책임

모듈 경계와 계층별 책임은 [architecture.md](architecture.md)가 단일 출처다. 새 코드를 어디에
둘지는 `AGENTS.md`의 판단표를, 상세 근거는 [architecture.md의 코드 위치 결정표](architecture.md#코드-위치-결정표)를
본다. 이 문서에는 기능 맥락과 작업 규칙만 둔다.

## Redis 작업 규칙

- key 조립과 물리 TTL은 `core-infra`의 adapter가 소유한다. domain/app은 Redis 타입이나 key가 아니라
  자신이 소유한 저장 기술 중립 계약만 본다.
- 운영 Redis에서 `KEYS`를 사용하지 않는다. 필요한 조회는 인덱스(Sorted Set 등)로 만든다.
- key 형식이나 인덱스 구조를 바꾸면 기존 key가 남아 있는 상태의 전환 절차를 함께 설계한다.
  [operations.md의 좌석 선택 Redis 인덱스 전환](operations.md#좌석-선택-redis-인덱스-전환)이 선례다.
- TTL, expiration listener, scheduler 보정 중 하나만 바꾸지 않는다. 세 경로는 같은 정합성을 함께 지킨다.
- Core Redis의 용도는 seat selection, seat hold, refresh token, OAuth2 one-time auth code뿐이다.

## 분산락 작업 규칙

- 같은 회원·회차의 중복 주문 시작과 같은 좌석 동시 점유를 막는 데 사용한다.
- 락 범위 안에서 외부 I/O를 늘리지 않는다. 임계 구역은 짧게 유지한다.
- 락 키를 바꾸면 보호 대상이 그대로인지 테스트로 고정한다.

## 완료로 판정하지 않는 조건

- [architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙) 중 하나라도 어겼다.
- `core-domain`에 `@Scheduled`, `@TransactionalEventListener`, Redisson, Spring Data Redis,
  `org.springframework.messaging`, Swagger import가 들어갔다.
- Controller가 repository나 Redis adapter를 직접 주입받는다.
- DB 트랜잭션 안에서 Redis 또는 WebSocket을 호출한다.
- 엔티티나 DB row를 응답으로 그대로 내보낸다.
- 이미 적용된 Flyway 파일을 수정했다.
- 오류를 빈 배열, `null`, 성공 응답으로 감춘다.
- 변경한 흐름에 대응하는 테스트가 없다.
- 같은 검증을 같은 목적으로 두 계층에서 중복 실행한다([validation.md](validation.md)).
- 관측 지표나 로그만 보고 정합성을 확인했다고 판단했다.

## 개발 시 주의점

- Controller에는 비즈니스 규칙이나 직접 저장소 접근을 넣지 않는다.
- 요청 파라미터 제약은 `controller.docs` 인터페이스에만 선언한다. 구현체에 다시 붙이면 Jakarta
  상속 규칙 위반으로 method validation이 깨진다([validation.md](validation.md)).
- Redis, WebSocket, 외부 HTTP 구현은 `core-infra`에 둔다.
- domain/app 코드는 자신이 의미를 정의한 port 인터페이스에 의존한다.
- hold, order, performanceseat, queue 변경은 동시성, TTL, 만료 후처리, 테스트 공백을 먼저 확인한다.
- API 요청/응답을 바꾸면 하위 호환성과 Swagger 문서 영향을 함께 본다.

## 커밋과 PR

기본 흐름은 **작업 브랜치 → 작업 단위 커밋들 → PR → squash 또는 rebase 반영**이다. 기준 브랜치는
`master`(= `origin/HEAD`)이며 이 저장소는 선형 이력을 유지하므로 merge commit을 만들지 않는다.
메시지 형식은 Conventional Commits 기반 `<type>(<scope>): <한국어 설명>`이다.

지켜야 할 사실 두 가지만 여기 둔다.

- **커밋은 사용자가 명시적으로 요청할 때만 시작한다.** "진행해 / 좋아"는 커밋 트리거가 아니다.
- **`master` push는 곧 운영 배포다.** `.github/workflows/deploy.yml`이 붙어 있고 자동 롤백은 없다.

절차와 컨벤션의 원본은 **`/commit-pr` 스킬**이다. 커밋이나 PR 작업을 시작하면 그 스킬이 로드되며,
아래 파일을 직접 열어도 된다.

| 알아야 하는 것 | 열 파일 |
| --- | --- |
| 9단계 절차, 충돌 검증, 배포 파이프라인, 하지 않을 것 | `.claude/skills/commit-pr/references/procedure.md` |
| type 표, scope 목록, 설명 규칙, BREAKING CHANGE, PR 본문 | `.claude/skills/commit-pr/references/conventions.md` |

PR 본문 항목은 `.github/pull_request_template.md`가 강제한다.
