# 개발 기준

이 문서는 현재 코드 기준 개발 맥락을 정리한다. 상세 구조는 [architecture.md](architecture.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 요약

Ticket은 공연/전시 티켓팅 백엔드다. 현재 구현의 중심은 아래 흐름이다.

- 인증/회원: 이메일 회원가입, 로그인, JWT 갱신, OAuth2 로그인 URL 조회 및 토큰 교환
- 공연/전시 조회: 쇼, 장르, 메타 코드, 회차/좌석 레이아웃 조회
- 좌석 선택: Redis TTL 기반 임시 선택 상태와 WebSocket 전파
- 좌석 선점: Redis 기반 hold와 주문 시작 흐름
- 주문: `PENDING` 주문 생성, 조회, 취소, 만료 처리
- 대기열: Ticket Server가 회차별 DIRECT/QUEUE를 결정하고, `ticket-queue` 별도 서비스가 Redis Cluster 기반 waiting/active/session 상태와 admission token 발급을 담당

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
6. `201 Created`와 `X-Order-Key` 헤더로 주문 식별자 반환

정책:

- 회차당 같은 회원은 `PENDING` 주문 1건만 허용한다.
- hold는 다중 좌석 all-or-nothing으로 생성한다.
- hold는 Redis TTL 만료와 주문 만료 흐름에 연결된다.
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

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/order`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release`

### 대기열

대기열 런타임은 `ticket-queue` 독립 서비스가 담당한다. `ticket-be`는 Queue Controller, queue token 저장소, queue token 만료 핸들러를 갖지 않는다. 대신 `PerformanceQueuePolicy`로 공연 상세 응답의 회차별 `entryType`을 계산하고, 클라이언트가 예매 버튼 클릭 시 DIRECT/QUEUE를 분기한다. Queue Server hot path는 Core DB를 조회하지 않으며, 내부 API로 받은 회차별 정책 snapshot을 Queue Redis에서 읽는다.

주요 개념:

- queue session
- waiting/active 상태
- adaptive polling
- admission token
- redirectUrl
- show detail entryType

주요 위치:

- `C:\Users\mn040\IdeaProjects\ticket-queue`
- `C:\Users\mn040\IdeaProjects\ticket-gateway`
- `core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenValidator.java`
- `support/security/src/main/java/com/ticket/support/security/admission`

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
- `ticket-be`는 예매 API 진입 시 회차 정책을 먼저 확인하고, 대기열이 필요한 회차에서만 `X-Admission-Token`의 서명, 만료, performanceId 일치 여부를 검증한다.
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
