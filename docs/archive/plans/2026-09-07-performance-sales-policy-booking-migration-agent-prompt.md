# Performance 판매 정책을 Booking BC로 이관하는 에이전트 실행 프롬프트

아래 코드 블록 전체를 새 에이전트에게 한 번에 전달한다. 이 문서는 구현 전 실행 지시이며, 구현이
끝나면 실제 코드·테스트·현재 아키텍처 문서가 최종 기준이다.

```text
당신은 C:\Users\mn040\IdeaProjects\ticket-workspace에서 작업한다. 목표는 ticket 백엔드의
Performance에 섞인 예매·선점·대기열 진입 정책을 Booking BC로 완전히 이관하는 것이다. 분석이나
계획만 제시하고 멈추지 말고, 코드·DB migration·seed·fixture·테스트·현재 문서를 모두 수정하고
검증이 통과할 때까지 작업하라. 사용자의 기존 미커밋 변경은 보존하고, 커밋·push·PR은 만들지 마라.

작업 시작 시 다음을 반드시 UTF-8로 전부 읽고 따른다.

- ticket/AGENTS.md
- ticket/CONTEXT.md
- ticket/docs/architecture.md
- ticket/docs/development.md
- ticket/docs/validation.md
- ticket/docs/testing.md
- ticket/docs/operations.md의 DB migration 부분
- ticket/docs/adr/0003-spring-modulith-application-module-boundaries.md
- ticket/docs/adr/0006-bounded-context-module-boundaries.md의 Performance 책임 혼재 부분
- ticket/.claude/skills/place-code/SKILL.md
- ticket/.claude/skills/verify/SKILL.md

범위와 고정 결정

1. ticket 백엔드만 수정한다. ticket-fe, ticket-queue, gatling-test는 수정하지 않는다. FE 변경은
   ticket/docs/development.md의 "미구현 또는 후속 범위"에 이미 기록돼 있으므로 그대로 유지한다.
2. Show BC의 Performance에는 회차 정체성과 일정만 남긴다: id, show, performanceNo, startTime,
   endTime. orderOpenTime, orderCloseTime, maxCanHoldCount, holdTime, queuePolicy 및 그 정책 메서드는
   제거한다.
3. 예매 접수 기간, Hold 제한, 대기열 진입 정책의 원본과 판단은 Booking BC가 소유한다.
4. 모듈 의존 방향은 기존처럼 booking -> show를 유지한다. show -> booking 의존을 추가하지 않는다.
   Show domain/application/infrastructure가 booking 공개 API나 내부 타입을 참조하게 만들지 않는다.
5. cross-module JPA 연관관계와 DB FK를 만들지 않는다. 새 정책은 performanceId scalar를 식별자로
   사용한다.
6. 기존 정책 의미를 보존한다. 이번 작업에서 FORCE_ON/FORCE_OFF/AUTO 의미, 예매 경계 시각,
   E-code, Hold/Order 만료 계산을 제품 정책 차원에서 바꾸지 않는다.
7. 사용하지 않는 범용 계층, 미래 관리자 CRUD, Performance 생성 이벤트, 별도 composition module을
   미리 만들지 않는다.

목표 도메인 모델

Booking BC에 PerformanceSalesPolicy aggregate를 만든다. 현재 복잡도에서는 aggregate 하나와
의미 있는 값 객체로 구성한다.

- PerformanceSalesPolicy
  - performanceId: scalar aggregate ID
  - OrderAcceptanceWindow orderAcceptanceWindow
  - HoldPolicy holdPolicy
  - BookingEntryPolicy bookingEntryPolicy
  - 낙관적 변경 충돌을 방어할 version
- OrderAcceptanceWindow
  - opensAt, closesAt
  - 둘 다 필수이고 opensAt < closesAt
  - now < opensAt: BEFORE_OPEN
  - opensAt <= now <= closesAt: OPEN
  - closesAt < now: CLOSED
  - 현재 코드처럼 마감 시각과 정확히 같은 순간은 접수 가능하다
- HoldPolicy
  - maxSeatCount: null이면 무제한, 값이 있으면 기존 규칙대로 2 이상
  - holdDuration: 도메인에서는 Duration으로 표현하고 0보다 커야 한다
  - DB 컬럼은 초 단위로 저장해 단위를 명시한다
- BookingEntryPolicy
  - 기존 queueMode, queueLevel, preopenQueueStartAt, waitingRoomMessage, reason을 의미 손실 없이
    옮긴다
  - 기존 queue policy row가 없으면 대기열을 요구하지 않는다
  - 현재 QueueActivation의 판정 경계와 FORCE_ON 동작을 그대로 보존한다. 구조 이관 중 새로운
    대기열 정책을 도입하지 않는다

값 객체는 JPA @Embeddable을 사용해 같은 정책 테이블에 저장하는 방식을 우선한다. Duration을 직접
매핑해 DB 표현이 불명확해지면 holdDurationSeconds 같은 명시적 영속 필드와 Duration 반환 메서드를
사용한다. 도메인 객체에는 Integer holdTime처럼 단위를 숨기는 이름을 남기지 않는다.

시간 의미를 반드시 구분한다.

- Performance.startTime/endTime은 실제 공연 시간이다.
- OrderAcceptanceWindow는 새로운 주문·좌석 선택을 시작할 수 있는 접수 기간이다.
- Hold와 Order의 expiresAt은 이미 시작된 개별 주문의 완료 기한이다.
- 접수 종료 직전에 생성된 Order/Hold의 expiresAt을 closesAt으로 잘라내지 않는다.
- 정책 변경은 기존 Order/Hold의 expiresAt을 다시 계산하지 않고 이후 생성 건부터 적용한다.

권장 패키지 구조는 다음과 같다. 저장소 관례에 맞게 이름을 조금 조정할 수 있지만 책임은 유지하라.

com.ticket.booking.domain.performancepolicy.model
  PerformanceSalesPolicy
  OrderAcceptanceWindow
  HoldPolicy
  BookingEntryPolicy
  QueueMode
  QueueLevel
  OrderAcceptanceStatus 또는 이에 해당하는 내부 enum

com.ticket.booking.domain.performancepolicy.repository
  PerformanceSalesPolicyRepository

com.ticket.booking.infrastructure.performancepolicy
  PerformanceSalesPolicyRepositoryAdapter
  SpringDataPerformanceSalesPolicyJpaRepository

예매 흐름 변경

- show.BookingPolicyLookup, show.BookingPolicySnapshot과 그 show 구현을 제거한다.
- show의 PerformanceBookingPolicySnapshot, BookingPolicyValidator, QueueActivation,
  PerformanceQueuePolicy와 관련 repository query/fetch join을 제거한다.
- Booking의 CreateOrderValidator, SelectSeatUseCase, GetSeatStatusUseCase,
  GetSeatAvailabilityUseCase가 Booking local PerformanceSalesPolicy를 조회하도록 바꾼다.
- CreateOrderValidator와 SelectSeatUseCase/GetSeatStatusUseCase는 기존과 동일하게 접수 기간을 검사하고,
  정책 판정상 대기열이 필요할 때만 AdmissionVerifier를 호출한다.
- GetSeatAvailabilityUseCase는 현재처럼 정책 존재 확인은 하되, 기존에 없던 접수 기간 차단을 새로
  추가하지 않는다.
- Hold 생성 duration과 좌석 수 한도는 새 HoldPolicy에서 얻는다.
- 정책 조회 실패는 "회차는 있을 수 있지만 Booking 판매 정책이 구성되지 않음"을 뜻한다. 기존
  공통 NotFoundException을 사용하거나 저장소 오류 계약 관례에 맞는 Booking 오류를 선택하되,
  기존 E-code를 임의로 재번호하지 않는다. 새 E-code가 꼭 필요하지 않으면 만들지 않는다.
- 다른 모듈 API 호출은 Booking DB transaction 밖에서 수행한다는 기존 규칙을 유지한다. 정책
  조회가 local DB라는 이유로 show/member 호출까지 하나의 긴 transaction에 넣지 않는다.

Booking 진입 조회 API

FE는 수정하지 않지만 백엔드의 Booking 소유 API는 구현한다.

GET /api/v1/booking/performances/{performanceId}/entry

인증 없이 회차 판매 진입 상태를 조회하게 하고 최소한 다음 의미를 응답한다.

- performanceId
- acceptanceStatus: BEFORE_OPEN, OPEN, CLOSED
- entryType: DIRECT 또는 QUEUE. 접수 불가 상태에서는 null 또는 UNAVAILABLE 중 하나를 계약 테스트와
  Swagger 문서에서 일관되게 선택한다. 가능하면 명시적인 UNAVAILABLE을 사용한다.
- opensAt, closesAt
- maxSeatCount
- holdDurationSeconds

redirectUrl과 queueEnterUrl은 반환하지 않는다. FE 라우트와 ticket-queue HTTP 경로를 도메인 또는
응답에 하드코딩하지 않는다. entryType은 업무 의미만 전달한다. 진입 조회는 안내용이므로 실제
좌석 선택·상태·주문 API도 실행 시점에 정책을 다시 검사한다.

Show API 정리

- Show 상세 회차 응답에서 orderOpenTime, orderCloseTime, entryType, queueRequired, redirectUrl,
  queueEnterUrl을 제거한다. Show 상세에는 performanceId, performanceNo, startTime, endTime만 남긴다.
- /api/v1/performances/{id}/summary 응답과 read model에서 maxCanHoldCount를 제거한다. title, region,
  startTime은 유지한다.
- QuerydslShowDetailReadRepository가 Performance entity와 queuePolicy를 fetch join하거나
  BookingEntryResolver를 호출하지 않게 한다.
- Show 단위 saleStartDate/saleEndDate와 BookingStatus는 TD-12의 별도 문제이므로 이번 작업에서
  재설계하지 않는다.
- backend controller/Swagger 계약 테스트를 새 JSON 계약에 맞춘다.
- ticket-fe 타입은 stale 상태가 되더라도 이번 작업에서 고치지 않는다. 이 사실은 이미 후속 문서에
  기록돼 있다.

DB migration

최종 정책 원본 테이블은 Booking 소유 BOOKING_PERFORMANCE_SALES_POLICIES로 만든다. 최소 컬럼은
다음 의미를 가져야 한다.

- performance_id PK, cross-module FK 없음
- order_opens_at, order_closes_at
- max_hold_seat_count nullable
- hold_duration_seconds not null
- queue_mode, queue_level, preopen_queue_starts_at, waiting_room_message, queue_policy_reason nullable
- version 및 현재 BookingAuditedEntity 매핑에 필요한 감사 컬럼

이미 적용된 migration은 절대 수정하지 말고 다음 booking 버전을 추가한다. 현재 booking 최신 버전이
V5이므로 작업 시작 시 재확인한 후 보통 H2/Oracle booking V6를 만든다. H2와 Oracle 문법 차이는
각 vendor 폴더 파일로 나눈다.

이 migration은 ownership handoff를 한 번에 완결한다.

1. 새 Booking 정책 테이블을 멱등하게 생성한다.
2. 기존 PERFORMANCES의 order_open_time/order_close_time/max_can_hold_count/hold_time과
   PERFORMANCE_QUEUE_POLICIES를 performance_id로 합쳐 backfill한다.
3. order_open_time/order_close_time이 모두 null인 회차는 "정책 미구성"이므로 정책 row를 만들지
   않는다. 한쪽만 null이거나 opensAt >= closesAt처럼 해석할 수 없는 데이터는 조용히 보정하거나
   누락하지 말고 migration을 실패시켜 원본 데이터를 먼저 확인하게 한다.
4. 기존 hold_time이 null이라면 현재 도메인 기본값 600초를 적용할지, 데이터 오류로 실패할지 기존
   운영/seed 데이터를 확인해 명시적으로 결정하고 migration 테스트로 고정한다. 임의의 무음 손실을
   만들지 않는다.
5. backfill row 수와 원본의 "구성된 정책" row 수가 같은지 검증한다.
6. backfill 검증 후 PERFORMANCE_QUEUE_POLICIES와 PERFORMANCES의 정책 컬럼 네 개를 제거한다.

소유권 이전 migration이 과거 Show/공통 소유 테이블을 읽고 제거하는 예외라는 이유를 SQL 주석과
architecture/operations 문서에 기록한다. show와 booking의 독립 migration 실행 순서에 기대는 두
파일로 create/backfill과 drop을 나누지 말고, 한 booking ownership-handoff migration 안에서
원자적인 순서를 유지한다. H2와 Oracle 모두 기존 구조가 이미 일부 정리된 경우를 고려해 저장소의
기존 방어적 migration 관례를 따른다.

Seed와 fixture

- SeedDataLoader와 LoadTestFixtureSeeder가 더 이상 PERFORMANCES 정책 컬럼이나
  PERFORMANCE_QUEUE_POLICIES에 쓰지 않게 한다.
- Performance row와 BOOKING_PERFORMANCE_SALES_POLICIES row를 각각 적재한다.
- booking E2E SQL fixture 및 테스트가 직접 PERFORMANCES 정책 컬럼을 넣는 모든 곳을 찾아 새
  정책 테이블 insert로 바꾼다.
- src/main, src/test, src/test/resources 전체를 rg로 검색해 제거된 컬럼/타입 참조가 남지 않게 한다.

테스트 요구사항

구현을 그대로 복사한 가치 없는 테스트 대신 다음 의미를 고정한다.

- OrderAcceptanceWindow: 시작 전/시작과 동일/기간 중/마감과 동일/마감 후, 잘못된 기간
- HoldPolicy: null 무제한, 최소 경계 2, 2 미만 거절, 양수 duration
- BookingEntryPolicy/진입 판정: 기존 QueueActivation의 null/FORCE_OFF/FORCE_ON/AUTO와 시간 경계
- PerformanceSalesPolicy: 접수 검사, 좌석 수 제한, Hold duration 제공
- CreateOrderValidator/UseCase: local 정책 사용, 대기열 필요 시 admission 검증, 새 duration으로
  기존 expiresAt 의미 유지
- SelectSeat/GetSeatStatus/GetSeatAvailability: show BookingPolicyLookup 제거 후 기존 행위 보존
- 새 Booking entry controller 계약과 Swagger 문서 계약
- Show 상세와 Performance summary에서 이전된 필드가 제거됨
- repository/JPA mapping과 backfill 결과, cross-module FK 부재, 기존 정책 컬럼/table 제거
- H2 migration과 가능한 경우 Oracle migration
- BookingModuleTests에서 제거된 show BookingPolicyLookup mock 삭제, ShowModuleTests와 구조 테스트 갱신
- PerformanceTest는 일정 책임만 검증하고 기존 정책 테스트는 Booking 쪽으로 이동 또는 대체

문서 갱신

구현이 끝난 실제 상태에 맞게 최소한 다음을 갱신한다.

- CONTEXT.md: Performance는 일정만, PerformanceSalesPolicy는 Booking 소유라고 정의
- docs/architecture.md: 모듈 소유권, 공개 계약, DAG 설명, migration ownership handoff
- docs/development.md: Queue 설명과 미구현 목록. Backend 이관 항목은 완료 처리하고 FE 후속 항목은
  그대로 유지
- docs/adr/0006-bounded-context-module-boundaries.md: "결정하지 않는 것"이었던 A2가 후속 결정으로
  채택·구현됐음을 날짜와 함께 기록
- docs/technical-debt.md: TD-11을 해결됨으로 바꾸고 FE 후속은 development.md를 가리킴
- docs/testing.md, docs/operations.md: 새 정책/migration 검증과 실제 파일명 반영
- 각 module package-info.java의 책임 및 공개 계약 설명
- 오래된 주석과 Javadoc에서 show가 예매 정책을 소유한다고 한 표현 제거

검증과 완료 조건

먼저 좁게 실패를 고치고, 새 변경이 생길 때만 필요한 범위를 다시 실행한다. 최소 검증은 다음이다.

1. .\gradlew.bat compileJava
2. 새 domain 정책 단위 테스트와 관련 booking/show use case·controller 계약 테스트
3. .\gradlew.bat test --tests "com.ticket.ModularityTests"
4. .\gradlew.bat test --tests "com.ticket.booking.*" --tests "com.ticket.show.*"
5. 관련 migration slice 테스트와 OracleMigrationCompatibilityTest. Docker가 없으면 정확히 미검증으로
   보고하고 H2 검증으로 대체했다고 표현하지 않는다
6. .\gradlew.bat test --tests "com.ticket.*.*ModuleTests"
7. 주문·Hold 흐름이 바뀌므로 Docker가 가능하면 .\gradlew.bat test --tests "com.ticket.bootstrap.*"
8. git diff --check와 rg로 제거 대상 참조/오래된 문서 문구 확인

완료라고 보고하려면 다음이 모두 성립해야 한다.

- Performance Java 모델에는 회차 일정 책임만 남는다.
- Booking이 정책 데이터와 모든 판정을 소유한다.
- show.BookingPolicyLookup 및 Show의 예매/대기열 정책 타입이 제거된다.
- show -> booking 의존과 cross-module FK/JPA 관계가 없다.
- 기존 설정 데이터가 새 테이블에 손실 없이 이관되고 구 schema가 정리된다.
- Backend entry API와 변경된 Show API 계약이 테스트로 고정된다.
- ticket-fe에는 변경이 없다.
- 관련 테스트가 통과하고, 실행하지 못한 Docker/Oracle 범위만 구체적으로 보고한다.

작업 도중 현재 저장소가 이 프롬프트의 예상과 다르면 최신 코드와 AGENTS.md를 우선하되 위의 업무
경계와 사용자 고정 결정을 유지하는 가장 작은 변경을 선택하라. 막히지 않은 부분을 모두 끝내기 전에
질문하거나 중간 계획만 반환하지 마라. 최종 보고에는 변경 이유, 핵심 파일, 테스트별 통과/실패,
미검증 범위, ticket-fe 무변경을 포함하라.
```
