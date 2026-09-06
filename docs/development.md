# 개발 기준

이 문서는 현재 코드 기준 개발 맥락을 정리한다. 모듈 경계와 상세 구조는
[architecture.md](architecture.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

**ADR 0005 반영 완료**: Grade/PerformanceGrade 가격 모델, ShowGrade/ShowSeat 폐기,
`Order.PAYMENT_FAILED` 제거, `payment` module 신설은 구현이 끝났다(ADR 0005의 `ticketing` module은
이후 booking으로 흡수됐다 — Ticket은 `booking.internal.domain.ticket`에 있다). 아래 내용은 그
결과를 반영한 현재 코드 기준이다. PG 연동·결제 승인/실패/콜백·자동 티켓 발급은 아직 별도 구현
대상이다([미구현 또는 후속 범위](#미구현-또는-후속-범위) 참고).

## 프로젝트 요약

Ticket은 공연/전시 티켓팅 백엔드다. 단일 Gradle Spring Boot 프로젝트이며 `booking`, `catalog`,
`member`, `payment`를 포함해 9개 Spring Modulith
Application Module로 나눈다(전체 목록과 DAG는 [architecture.md](architecture.md)가 원본). 현재
구현의 중심은 아래 흐름이다.

- 인증/회원(`member`): 이메일 회원가입, 로그인, JWT 갱신, OAuth2 로그인 URL 조회 및 토큰 교환
- 공연/전시 조회(`catalog`): 쇼, 장르, 회차별 Venue 배치·좌석·등급(Grade/PerformanceGrade) 조회,
  대기열 필요 여부 정책
- 좌석 선택(`booking`): Redis TTL 기반 임시 선택 상태와 WebSocket 전파
- 좌석 선점과 주문(`booking`): Redis 기반 hold, `PENDING` 주문 생성, 조회, 취소, 만료 처리.
  판매 좌석(`PerformanceSeat`)은 회차 단위로 편성되고 판매 오픈 시점 가격을 snapshot한다
- 입장 검증(`booking`의 admission 검증): `ticket-queue`가 발급한 admission token 검증
- 좋아요(`showlike`): catalog가 흡수했다. 상세는
  [architecture.md의 찜(showlike)은 catalog가 흡수한다](architecture.md#찜showlike은-catalog가-흡수한다)를 본다
- 결제 시도(`payment`), 발급 티켓(`booking`의 Ticket): 이번 범위는 entity/schema/repository까지다. PG
  연동, 결제 승인/실패/취소 API, 자동 티켓 발급, QR/입장/사용/양도는 없다
- 대기열: Ticket Server가 회차별 DIRECT/QUEUE를 결정하고, `ticket-queue` 별도 서비스가
  shard/local sequence와 public state 기반 대기 상태 및 admission token 발급을 담당

결제 승인/실패/콜백과 최종 판매 확정 흐름은 아직 별도 구현 대상이다.

## 주요 API 흐름

**엔드포인트 목록과 클래스 경로는 여기 적지 않는다.** 엔드포인트는 Swagger(`/api/api-docs`)가,
파일 위치는 코드가 원본이다. 여기에는 코드만 봐서는 알기 어려운 **정책과 순서**만 둔다.

### 인증

- API 인증은 JWT 기반 stateless 방식이다.
- OAuth2 인가 흐름은 별도 filter chain에서 처리한다. 전역 `SecurityFilterChain`은 `member`가
  제공한다.
- Refresh token과 OAuth2 1회용 코드는 Redis를 쓴다.
- 공개 GET API를 제외한 대부분의 API는 인증이 필요하다. 다른 모듈의 controller는
  `member.AuthenticatedMember`만 parameter로 받고 JWT나 member의 internal `Member`를 보지
  않는다.

### 쇼·회차·좌석 조회

- **좌석 상태는 DB 상태와 Redis 점유 상태를 합쳐 계산한다.** 합치는 규칙의 소유자는 booking
  모듈 한 곳이다.
- 잔여 좌석 수는 Redis `SELECTING`, `HOLDING` 상태를 반영한다.
- 좌석·등급·가격 조회의 기준 식별자는 `performanceId`/`performanceSeatId`/`performanceGradeId`다.
  `showId` 기준으로 등급·가격을 조회하는 API는 만들지 않는다 — 같은 Show라도 회차마다 편성과
  가격이 다를 수 있다(ADR 0005).
- performance 기준 API 3종(`booking.internal.web.PerformanceSeatQueryController`,
  `/api/v1/performances/{performanceId}/**`)은 각각 다른 것을 반환한다.
  - `GET .../seat-map`: 정적 Venue 배치·물리 Seat 좌표·PerformanceGrade 표시값·확정 가격
    (`GetPerformanceSeatMapUseCase`). catalog `PerformanceVenueLayoutCatalog`와 booking local
    `PerformanceSeat` 조회를 각각 한 번씩만 호출해 N+1 없이 고정된 query 수로 조합한다. Performance에
    판매 편성되지 않은 물리 Seat는 응답에 아예 나타나지 않는다.
  - `GET .../seats/status`: 동적 상태(`performanceSeatId` -> AVAILABLE/OCCUPIED). DB `RESERVED`와
    Redis `SELECTING`/`HOLDING`을 합친다(`GetSeatStatusUseCase`).
  - `GET .../seats/availability`: 등급별 잔여석(`GetSeatAvailabilityUseCase`). 그룹 key는 이름이
    같아도 바뀔 수 있는 `gradeName`이 아니라 `performanceGradeId`다.
  - **정적 seat-map에 있는데 상태 응답에 없는 좌석을 클라이언트가 AVAILABLE로 추정하게 하지 않는다.**
    새 좌석·등급 조회 API를 추가할 때도 이 원칙을 지킨다 — 상태 누락을 판매 가능으로 조용히
    치환하지 않는다.
  - 새 조회를 추가할 때 회차당 고정된 query 수(요청 회차 크기와 무관)를 유지하는지 확인한다.
    catalog 쪽 좌표·등급 표시값이 booking 쪽 판매 편성과 어긋나면(데이터 불일치) 예외를 던지지 않고
    조용히 그 좌석만 제외한다 — 어떤 오류로 다룰지는 조합 시점에 판정하지 않는다.
  - `booking.internal.web.ShowVenueLayoutController`(`/api/v1/shows/{showId}/venue-layout`)는 물리
    Venue 배치만 반환하는 별개의 레거시 show 기준 API다. 새 기능은 여기 추가하지 않고 performance
    기준 API 3종에 추가한다.

### 좌석 선택

- selection은 UX 보조 상태이고 **hold의 필수 선행 조건이 아니다.**
  다른 사용자가 selection 중이어도 hold는 성공할 수 있다. 이 결정은
  [ADR 0001](adr/0001-selection-and-hold-are-independent.md)이 원본이다.
- selection은 Redis TTL로 자동 만료되고, 만료 시 expired listener가 `DESELECTED`를 전파한다.

### 좌석 선점과 주문 시작

주문 생성은 `POST /api/v1/orders`가 canonical이다. hold 생성 엔드포인트는 deprecated이며
내부적으로 같은 `CreateOrderUseCase`(`booking`)로 위임한다.

실제 실행 순서(`CreateOrderValidator`/`CreateOrderUseCase` 기준):

1. 같은 회원·같은 회차의 주문 시작을 분산락으로 직렬화한다(`LockScope.ORDER_START`)
2. catalog `BookingPolicyLookup`으로 예매 정책·좌석 소속·가격 snapshot을 조회한다(booking DB
   트랜잭션 밖)
3. 정책상 대기열이 필요한 회차만 booking 안의 `AdmissionVerifier`로 token을 검증한다(밖)
4. member `MemberLookup`으로 회원이 active인지 확인한다(밖)
5. booking local read로 같은 회원의 `PENDING` 주문 중복과 좌석 판매 상태를 확인한다(짧은 read
   트랜잭션)
6. 좌석별 분산락(`LockScope.SEAT`) 안에서 Redis에 좌석 hold를 생성한다(밖)
7. booking DB 트랜잭션에서 `PENDING` 주문·`OrderSeat`·hold history를 저장하고 같은 트랜잭션
   안에서 `OrderStarted` 이벤트를 발행한다
8. 커밋 이후 `BookingEventListeners`(`@ApplicationModuleListener`)가 selection 해제와 HELD
   상태 전파를 실행한다
9. 201 Created와 `X-Order-Key` 헤더로 주문 식별자 반환

정책:

- 회차당 같은 회원은 `PENDING` 주문 1건만 허용한다.
- hold는 다중 좌석 all-or-nothing으로 생성한다.
- **주문 저장 트랜잭션 안에서는 다른 모듈 API도, Redis도, WebSocket도 호출하지 않는다.**
- DB 저장이 실패하면 이미 만든 Redis hold를 보상 해제한다. 커밋 이후 이벤트 리스너 처리가
  실패해도 이미 커밋된 주문과 hold는 되돌리지 않고 Event Publication Registry가 재시도한다.
- 대기열이 필요한 회차는 `X-Admission-Token` 검증을 먼저 통과해야 한다.

### 주문

- 취소와 만료는 `OrderTerminationService`(`booking`)의 공통 종료 절차를 쓴다.
- 종료 시 상태 전이·hold history 저장과 `OrderTerminated` 발행이 **같은 booking DB 트랜잭션**
  안에서 일어난다.
- 커밋 이후 처리(Redis hold 해제, WebSocket 발행)는 `BookingEventListeners`가 담당한다.

상세는 [core-booking-lifecycle.md](core-booking-lifecycle.md)를 본다.

### 대기열

대기열 런타임은 형제 저장소 `ticket-queue`가 담당한다. Core는 Queue Controller도, queue token
저장소도, 만료 핸들러도 갖지 않는다. 대신 catalog의 `PerformanceQueuePolicy`로 공연 상세 응답의
회차별 `entryType`을 계산하고, 클라이언트가 예매 버튼에서 DIRECT/QUEUE를 분기한다.

Queue Server hot path는 Core DB와 회차별 정책 snapshot을 조회하지 않는다. Queue Server는 모든
요청 회차에 애플리케이션 기본 입장 속도와 TTL을 적용하며, `join`에서 받은 `shardId`와
`localSeq`를 public `/state` 응답의 `serving[shardId]`와 비교해 입장 가능 여부를 판단한다.

Core(`booking`의 admission 검증)는 Queue가 발급한 admission token의 서명, issuer, audience, scope, 만료
시각과 `memberId`, `performanceId` 일치 여부를 검증한다. **주문 생성 후 Queue Server에 session
완료 요청을 보내지 않으며**, 입장 후 shopping session은 Queue Server의 TTL로 정리된다.

secret, issuer, audience는 두 저장소 설정이 일치해야 한다. 한쪽만 바꾸지 않는다.

## 핵심 도메인 모델

### 가격 원본과 snapshot 체인(catalog -> booking)

가격은 세 시점의 사실로 나뉘고, **뒤 단계는 앞 단계를 다시 조회하지 않는다.**

```text
PerformanceGrade.price(catalog)   운영자가 구성한 회차 등급 가격 — 판매 오픈 전에만 변경
  -> PerformanceSeat.unitPrice(booking)   판매 좌석 생성 시 snapshot — 판매 오픈 후 불변
    -> OrderSeat.unitPrice(booking)       주문 생성 시 snapshot — 생성 후 불변
```

- `Grade`(catalog)는 `VIP`/`R`/`S`/`A` 같은 코드·이름만 갖고 가격이 없다. 같은 Grade라도 회차마다
  가격이 다를 수 있어 `PerformanceGrade`(`Grade N:M Performance` 연결 entity, catalog)가 회차별
  가격·표시 순서를 갖는다 — **가격의 원본은 `PerformanceGrade.price`다.**
- `PerformanceSeat`(booking)는 판매 좌석 생성 시 `PerformanceGrade.price`를 `unitPrice`로 복사하고,
  이후 이 값은 불변이다. `PerformanceGrade` 가격을 나중에 바꿔도 이미 생성된 `PerformanceSeat`는
  바뀌지 않는다.
- `OrderSeat`(booking)는 주문 생성 시 `PerformanceSeat.unitPrice`를 복사한다. **주문 합계는 서버가
  `PerformanceSeat.unitPrice`만으로 계산하고, 클라이언트가 보낸 가격은 받지도 계산 근거로
  쓰지도 않는다.**
- 과거 주문 조회는 현재 `PerformanceGrade`/`PerformanceSeat` 가격을 다시 조회하지 않는다.
  `OrderSeat.unitPrice`가 그 시점의 계약을 이미 보존하므로, catalog 쪽 가격·표시 이름이 바뀌어도
  기존 주문 상세는 바뀌지 않아야 한다.
- 새 가격 관련 기능을 추가할 때 이 체인 중간을 건너뛰어 상위 단계(`PerformanceGrade.price`)를 직접
  참조하지 않는다 — 그 순간 스냅샷을 보존하는 이유 자체가 무너진다.

### Show/Performance/Grade/PerformanceGrade/PerformanceSeat(catalog + booking)

- `Show`는 `Performance`를 회차 단위로 갖는다(`Show 1 : 0..N Performance`).
- `Grade N:M Performance`는 `PerformanceGrade`가 연결한다. `Performance N:M Seat`는
  `PerformanceSeat`가 연결하고, 어느 `PerformanceGrade`에 속하는지는 `PerformanceSeat`가 갖는다.
- `ShowGrade`/`ShowSeat`(Show 단위 등급·좌석)는 폐기됐다. Show 상세의 공통 가격표가 필요하면
  `PerformanceGrade`에서 `minPrice`/`maxPrice`를 파생한다(`GetShowDetailUseCase.PriceSummary`).
  Show 전체 회차에 적용할 좌석 템플릿이 실제로 필요해지기 전에는 별도 개념을 미리 만들지 않는다.

### Order(`booking`)

- 내부 PK: `id`
- 외부 식별자: `orderKey`
- 주요 상태: `PENDING -> CONFIRMED`, `PENDING -> EXPIRED`, `PENDING -> CANCELED`

`PAYMENT_FAILED`는 Order 상태에서 제거됐다. Payment 실패는 Payment의 상태
(`READY`/`PROCESSING` -> `FAILED`)이고, Order는 만료 전까지 다시 결제를 시도할 수 있다(`Order 1 :
0..N Payment`, ADR 0005). 결제 재시도를 정말로 닫아야 하는 업무 사건이 생기면 그때 별도 Order
종료 상태를 추가한다 — 지금 미리 대체 상태를 만들지 않는다.

### Hold(`booking`)

- Redis 기반 임시 점유 상태
- DB에는 `HOLD_HISTORY` 이력 저장
- 주문 시작과 만료/취소 후처리에 직접 연결

### Selection(`booking`)

- Redis TTL 기반 UX 보조 상태
- 실제 점유 권리는 hold가 담당

### Payment(`payment`), Ticket(`booking`)

- `Payment`는 Order에 대한 한 번의 결제 시도다(`Order 1 : 0..N Payment`). `orderId`는
  cross-module scalar 컬럼이고 booking `Order` entity를 JPA로 참조하지 않는다.
- `Ticket`은 결제 성공으로 확정된 OrderSeat에 대해 발급되는 입장 권리다
  (`OrderSeat 1 : 0..1 Ticket`). booking 소유다(ADR 0005의 별도 `ticketing` module은 흡수됐다).
  `ownerMemberId`는 cross-module scalar, `orderSeatId`는 같은 module 안이지만 관례대로 scalar다.
- **Payment·Ticket 모두 이번 범위는 entity/schema/repository와 구조·중복 방지 테스트까지다.** controller,
  PG client, 결제 승인/실패/취소 API, callback/webhook, `OrderConfirmed` listener, 자동 티켓 발급,
  QR/입장/사용/양도는 만들지 않는다. 이 범위를 넘는 코드를 추가하려면 먼저 별도 설계·ADR 승인을
  받는다(ADR 0005 §3, "이 ADR이 결정하지 않는 것").

### Queue

- 대기열 상태는 `ticket-queue`가 관리한다.
- Core(`catalog`)는 예매 API 진입 시 회차 정책을 먼저 확인하고, 대기열이 필요한 회차에서만
  `booking`의 admission 검증이 `X-Admission-Token`의 서명, 만료, memberId와 performanceId 일치 여부를
  검증한다.
- Core의 Redis는 좌석 선택, hold(`booking`), refresh token, OAuth2 one-time auth code(`member`)
  용도로만 사용한다.

## 미구현 또는 후속 범위

- `payment`의 controller, PG client, 결제 승인/실패/취소 API, callback/webhook, 그리고 booking 안의
  Ticket 자동 발급(`OrderConfirmed` 후속 처리)
- `payment -> booking`(결제 정산) 공개 계약과 cross-module 의존 edge 자체 — entity-only 단계에서는
  payment가 다른 모듈을 참조하지 않는다
- 결제 성공 시 주문 확정과 최종 좌석 판매 확정(`payment`가 booking에 공개할 정산 계약 포함)
- Ticket 자동 발급 listener, QR, 입장, 사용, 취소, 환불, 양도
- `showlike` read 경로(`GetMyShowLikesUseCase` 등)의 모듈 이전 — 상세는
  [architecture.md](architecture.md#showlike-모듈의-경계--완결되지-않은-상태를-그대로-기록한다)를
  본다
- Event Publication Registry의 `serialized_event` 컬럼 크기(`VARCHAR(255)`) 리스크 — 상세는
  [ADR 0003](adr/0003-spring-modulith-application-module-boundaries.md#5-spring-modulith-이벤트와-jpa-event-publication-registry)을
  본다
- `com.ticket.core`/`storage`/`support` legacy 코드(오류 처리, `core.infra.seed`의 시드 러너
  등)의 모듈 이전
- member 전용인 `com.ticket.core.support.util.CookieUtils`(`refresh_token` 쿠키 이름과
  `/api/v1/auth` 경로를 하드코딩)를 `member.internal.web`으로 옮기는 작업 — 아직 legacy
  위치에 남아 있다
- Flyway 기반 운영 마이그레이션 스크립트 누적과 검증 환경 보강
- Redis key scan 기반 조회 구조 최적화
- 운영 관측성 대시보드와 알림 보강

## 작업 시작 전

1. 대상 모듈의 `internal.application`/`internal.domain`/`internal.infrastructure`/`internal.web`과
   기존 테스트를 읽는다.
2. 바꿀 API의 요청·응답·오류 계약과 Swagger 문서 인터페이스를 확인한다.
3. 트랜잭션 경계, Redis key와 TTL, 모듈을 넘는 참조가 scalar ID/공개 API로만 이뤄지는지
   추적한다.
4. 같은 흐름을 검증하는 테스트가 어디에 있는지 확인한다. 없으면 그 공백을 작업 범위에 포함한다.
5. 프로파일별 설정(`application-local.yml`, `-dev.yml`, `-prod.yml`)에 영향이 있는지 본다.

## 기능 개발 순서

1. **계약 확정** — endpoint, 인증 요구, request/response, 상태 코드, 오류를 먼저 정한다.
2. **domain** — 엔티티와 도메인 규칙, 필요한 port(`store`, publisher, client)를 소유 모듈의
   `internal.domain`에 정의한다.
3. **application** — use case와 트랜잭션 경계를 소유 모듈의 `internal.application`에 만든다.
   다른 모듈이 필요하면 그 모듈의 공개 API(interface + snapshot)만 호출한다.
4. **infrastructure** — 소유 모듈의 `internal.infrastructure`에서 port를 구현한다. Redis 명령,
   WebSocket 발행, 외부 HTTP를 여기에 둔다.
5. **presentation** — 소유 모듈의 `internal.web`에서 요청 검증, principal 추출, use case 호출,
   응답 매핑만 한다. 어떤 검증을 어느 계층이 소유하는지는 [validation.md](validation.md)가
   단일 기준이다.
6. **migration** — DB 구조 변경이 있으면 해당 모듈 소유 폴더(`db/migration/{module}`)에 새
   Flyway 버전 파일을 추가한다([operations.md](operations.md#db-마이그레이션)).
7. **테스트** — 도메인 규칙, use case, adapter 계약, controller 계약, 모듈 STANDALONE test를
   채운다([testing.md](testing.md)).
8. **검증** — 좁은 검증부터 실행한다([testing.md](testing.md#무엇을-돌릴지)).

사용하지 않는 빈 계층 파일은 만들지 않는다. 다만 Controller에 업무 규칙을 넣거나 use case를
건너뛰고 Controller가 repository를 직접 부르는 형태로 합치지 않는다.

## 계층 책임

모듈 경계와 계층별 책임은 [architecture.md](architecture.md)가 단일 출처다. 새 코드를 어디에
둘지는 **`/place-code` 스킬**이 원본이다. 이 문서에는 기능 맥락과 작업 규칙만 둔다.

## Redis 작업 규칙

- key 조립과 물리 TTL은 소유 모듈의 `internal.infrastructure` adapter가 소유한다.
  `internal.application`/`internal.domain`은 Redis 타입이나 key가 아니라 자신이 소유한 저장
  기술 중립 계약만 본다.
- 운영 Redis에서 `KEYS`를 사용하지 않는다. 필요한 조회는 인덱스(Sorted Set 등)로 만든다.
- key 형식이나 인덱스 구조를 바꾸면 기존 key가 남아 있는 상태의 전환 절차를 함께 설계한다.
  [operations.md의 좌석 선택 Redis 인덱스 전환](operations.md#좌석-선택-redis-인덱스-전환)이
  선례다.
- TTL, expiration listener, scheduler 보정 중 하나만 바꾸지 않는다. 세 경로는 같은 정합성을
  함께 지킨다.
- Core Redis의 용도는 seat selection, seat hold, refresh token, OAuth2 one-time auth code뿐이다.

## 분산락 작업 규칙

- 같은 회원·회차의 중복 주문 시작과 같은 좌석 동시 점유를 막는 데 사용한다.
- 락 범위 안에서 외부 I/O를 늘리지 않는다. 임계 구역은 짧게 유지한다.
- 락 키를 바꾸면 보호 대상이 그대로인지 테스트로 고정한다.

## payment 개발 규칙

`payment`는 ADR 0005로 신설된 **entity-only 모듈**이다. 이번 범위를 넘는 코드를 추가하지 않는다.
ADR 0005가 함께 신설했던 `ticketing`은 booking으로 흡수됐다 — `Ticket`도 아직 entity-only이며 아래
범위 규칙은 booking 안의 Ticket에도 그대로 적용한다.

- **범위는 entity/schema/repository와 구조·중복 방지 테스트까지다.** controller, PG client, 결제
  승인/실패/취소 API, callback/webhook, `OrderConfirmed` listener, 자동 티켓 발급, QR/입장/사용/
  양도를 이 모듈에 추가하지 않는다. 필요해지면 먼저 별도 설계·ADR 승인을 받는다.
- **cross-module 참조는 scalar ID만 쓴다.** `Payment.orderId`, `Ticket.ownerMemberId`는 booking/member
  entity를 JPA로 참조하지 않는 scalar 컬럼이다. cross-module
  물리 FK를 새로 만들지 않는다.
- **다른 업무 모듈을 import하지 않는다.** `ModularityTests.APPROVED_DEPENDENCY_DAG`에서 `payment`는
  `Set.of()`다(`shared`/`web`/`error` 포함 완전한 leaf) — booking의 `internal` 패키지나
  entity를 직접 참조하는 코드를 추가하면 그 테스트가 실패한다.
- **향후 PG 연동·티켓 발급은 별도 계획이다.** `payment -> booking`(정산 계약) edge는 그 공개 계약을
  실제로 구현하는 후속 단계에서만 추가한다.
  지금 빈 public interface나 가짜 이벤트 구독으로 미리 만들지 않는다(ADR 0005 §4).
- entity만 추가해도 운영 `ddl-auto=validate` 때문에 H2/Oracle 양쪽 Flyway migration이 반드시
  함께 있어야 한다 — "entity-only"는 controller/PG 연동을 만들지 않는다는 뜻이지 migration 없이
  Java 파일만 추가한다는 뜻이 아니다.

## 완료로 판정하지 않는 조건

- [architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙) 중 하나라도 어겼다.
- `com.ticket.ModularityTests`가 실패한다.
- 다른 모듈의 `internal` 패키지, Repository, JPA entity를 직접 import했다.
- 모듈을 넘는 `@ManyToOne`/`@OneToOne`/`@OneToMany`/`@ManyToMany` 연관관계나 DB FK가 생겼다.
- Controller가 다른 모듈의 repository나 Redis adapter를 직접 주입받는다.
- DB 트랜잭션 안에서 다른 모듈 API, Redis, 또는 WebSocket을 호출한다.
- 엔티티나 DB row를 응답으로 그대로 내보낸다.
- 이미 적용된 Flyway 파일을 수정했다.
- 오류를 빈 배열, `null`, 성공 응답으로 감춘다.
- 변경한 흐름에 대응하는 테스트가 없다.
- 같은 검증을 같은 목적으로 두 계층에서 중복 실행한다([validation.md](validation.md)).
- 관측 지표나 로그만 보고 정합성을 확인했다고 판단했다.
- `showlike`의 legacy read 경로 gap이나 event payload 크기 리스크를 해결된 것처럼 서술했다
  ([architecture.md](architecture.md#showlike-모듈의-경계--완결되지-않은-상태를-그대로-기록한다)).

## 개발 시 주의점

- Controller에는 비즈니스 규칙이나 직접 저장소 접근을 넣지 않는다.
- 요청 파라미터 제약은 `controller.docs` 인터페이스에만 선언한다. 구현체에 다시 붙이면 Jakarta
  상속 규칙 위반으로 method validation이 깨진다([validation.md](validation.md)).
- Redis, WebSocket, 외부 HTTP 구현은 소유 모듈의 `internal.infrastructure`에 둔다.
- domain/application 코드는 자신이 의미를 정의한 port 인터페이스에 의존한다.
- hold, order, performanceseat, queue 변경은 동시성, TTL, 이벤트 재시도, 테스트 공백을 먼저
  확인한다.
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
