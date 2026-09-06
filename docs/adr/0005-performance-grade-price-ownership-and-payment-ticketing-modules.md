# 가격은 PerformanceGrade가 원본이고, Payment/Ticketing은 entity-only 모듈로 시작한다

## 상태(2026-09-04): 채택·구현됨. ADR 0003을 module set/DAG 범위에서 부분적으로 supersede한다.

이 ADR은 `docs/superpowers/specs/2026-09-04-ticket-domain-module-redesign.md`(설계 원본, 이하
"재설계 스펙")와 `docs/superpowers/plans/2026-09-04-ticket-domain-module-redesign.md`(실행 계획)가
이미 확정한 결정을 도메인 문서 체계(ADR/CONTEXT.md)에 반영한다. 이 ADR 자체가 새로 논의를 여는
것이 아니라, 이미 승인된 설계를 실행 가능한 결정 기록으로 옮기는 것이다.

**Phase 2~5(Task 3~13, 실행 계획 참고)가 이 결정을 반영해 실제 entity/schema/module을 바꿨다.**
`payment`/`ticketing` module, `Grade`/`PerformanceGrade`/`PerformanceSeat.unitPrice`/
`Order.PAYMENT_FAILED` 제거가 모두 코드에 반영됐고, `com.ticket.ModularityTests`가 12개 module과
새 DAG를 고정한다. 이 ADR은 "무엇을 왜 바꿨는가"를 codify하고, architecture/development/testing/
operations 문서가 이 결정에 맞춰 갱신될 때 참조할 근거로 남는다.

## 배경

현재 모델은 세 가지 문제를 갖는다(재설계 스펙의 "왜 현재 모델을 바꿔야 하는가" 참고).

1. `Seat`에 Venue 연관관계가 없다. seed는 모든 Show/Performance와 모든 Seat를 `CROSS JOIN`해
   서로 다른 홀의 좌석이 같은 좌석 집합을 공유하는 것처럼 만든다(`CurrentSeatVenueShowGradeSchemaTest`가
   지금 상태를 schema로 고정했다).
2. `ShowGrade`(Show 단위 등급 가격)와 `PerformanceSeat.price`가 동시에 존재하지만 갱신 규칙이
   코드로 보장되지 않는다. `ShowGradePerformanceSeatPriceMismatchQueryTest`가 실제로 한쪽만 바뀌면
   조용히 어긋나는 것을 재현했다.
3. `Order.PAYMENT_FAILED`는 Payment가 "결제 시도"(`Order 1 : 0..N Payment`)라는 사실과 맞지 않는다.
   Payment 한 번 실패는 Order를 끝내지 않아야 하는데 지금 상태 모델은 그것을 표현하지 못한다.

## 결정

### 1. 가격은 세 시점의 사실로 나눈다 — PerformanceGrade가 원본이다

```text
PerformanceGrade.price   운영자가 구성한 회차 등급 가격 (판매 오픈 전에만 변경 가능)
  -> PerformanceSeat.unitPrice   판매 좌석 생성 시 snapshot (판매 오픈 후 불변)
    -> OrderSeat.unitPrice       주문 생성 시 snapshot (생성 후 불변)
```

`ShowGrade`(Show 단위)는 제거하고 `PerformanceGrade`(회차 단위, `Grade N:M Performance`의 연결
entity)가 그 책임(회차에서 쓸 Grade 선택, 회차별 가격, 회차별 표시 순서)을 모두 대체한다. `Grade`
자체는 `VIP`/`R`/`S`/`A` 같은 재사용 가능한 코드·이름만 갖고 가격을 갖지 않는다 — 가격은 회차마다
달라질 수 있기 때문이다.

과거 주문 조회는 현재 `PerformanceGrade` 가격을 다시 조회하지 않는다. `OrderSeat.unitPrice`가
이미 그 시점의 계약을 보존하므로, catalog 쪽 가격이나 표시 이름이 바뀌어도 기존 주문 상세는
바뀌지 않아야 한다. 클라이언트가 보낸 가격은 받지도, 계산 근거로 쓰지도 않는다 — 주문 합계는
서버가 `PerformanceSeat.unitPrice`만으로 계산한다.

### 2. `ShowGrade`/`ShowSeat`는 폐기하고 `PerformanceGrade`/`PerformanceSeat`로 완전히 대체한다

Show 상세의 공통 가격표가 필요하면 `ShowGrade`를 다시 두지 않고 `PerformanceGrade`에서
`minPrice`/`maxPrice` 같은 조회 전용 값을 파생한다. Show 전체 회차에 적용할 좌석 템플릿이 실제로
필요해지면 그때 `ShowSeatTemplate` 같은 별도 개념을 추가한다 — 지금은 요구가 없으므로 기존
`ShowSeat`를 이름만 바꿔 남기지 않는다.

제거는 Phase 3 Task 8에서 새 읽기·쓰기 경로가 모두 전환된 뒤 마지막에 한다(Phase 순서 자체가
실행 원칙 4). 기존 Flyway migration은 수정하지 않고, expand -> migrate -> contract 순서의 신규
migration으로 `SHOW_GRADES`/`SHOW_SEATS`를 이관 후 제거한다.

### 3. `payment`, `ticketing`을 닫힌 Application Module로 신설한다 — 이번 범위는 entity-only다

`booking`(좌석 판매와 주문)은 그대로 유지하고, 외부 PG 연동/결제 재시도 수명주기(`payment`)와
발급 티켓 수명주기(`ticketing`)만 분리한다(재설계 스펙이 검토한 방안 A/B/C 중 방안 C). `booking`에
다 두면(방안 A) PG 장애와 티켓 발급 재시도 정책이 booking 내부로 퍼지고, entity 단위로 전부
분리하면(방안 B) 좌석 판매 확정의 원자성이 여러 모듈 호출과 이벤트를 거쳐야 해 지금 규모에서
복잡도가 과도하다.

**이번 구현은 Payment/Ticket을 entity/schema/repository와 구조·중복 방지 테스트까지만 만든다.**
PG client, 결제 승인/실패/취소 API, callback/webhook, `OrderConfirmed` listener, 자동 티켓 발급,
QR/입장/사용/양도는 이번 범위가 아니다(계획 문서의 "이번 계획에서 제외하는 후속 작업" 참고).

### 4. entity-only 단계의 module DAG — ADR 0003을 이 범위에서 supersede

ADR 0003 §3이 승인한 DAG는 `payment`/`ticketing`을 몰랐다. 이 ADR은 module set에 그 둘을
추가하고, 이번 entity-only 단계의 목표 DAG를 다음과 같이 확정한다.

```text
payment   -> 없음
ticketing -> 없음
booking   -> catalog, member, admission
catalog   -> member
member  -> 없음
admission -> 없음
metadata  -> catalog, booking, member 그리고 필요 시 payment/ticketing의 공개 metadata
```

$1
**2026-09-06 갱신(ticketing)**: 결정 3이 신설한 `ticketing` module은 booking으로 흡수됐다. entity-only 상태에서
module 하나를 더 유지할 이유가 없고, 발급 트리거인 `OrderConfirmed`가 booking 안의 사건이라 같은 module에
두는 편이 단순하다는 판단이다. `Ticket`은 `booking.domain.ticket`, `TICKETS` migration은 booking V5로
옮겼다. 위 DAG의 `ticketing` 행과 §3의 방안 C 서술 중 ticketing 부분은 그 이전 기록이다. payment 분리는 그대로다.

`payment`와 `ticketing`은 다른 업무 모듈을 import하지 않는다. 미래 의존 edge(`payment -> booking`,
`ticketing -> booking`)는 실제 공개 계약(결제 정산, `OrderConfirmed` 구독)을 구현하는 후속
단계에서만 추가한다 — 지금 빈 public contract나 가짜 호출로 미리 만들지 않는다. 그 시점의 목표
DAG는 재설계 스펙의 "권장 의존 DAG"에 이미 기록돼 있다.

`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`와 각 module의 `package-info.java`가 실제
구현 시점에 이 DAG를 반영한다 — 지금은 설계만 승인한다.

### 5. `Order.PAYMENT_FAILED`는 제거한다

```text
PENDING -> CONFIRMED
PENDING -> EXPIRED
PENDING -> CANCELED
```

Payment 실패는 Payment의 상태(`READY/PROCESSING -> FAILED`)이고, Order는 만료 전까지 다시 결제를
시도할 수 있다. 결제 재시도를 정말로 닫아야 하는 업무 사건이 생기면 그때 별도 Order 종료 상태를
추가한다 — 지금 미리 대체 상태를 만들지 않는다.

## 관계와 선택성 요약

재설계 스펙의 표를 그대로 승인한다.

| 관계 | 카디널리티 |
| --- | --- |
| Venue - Seat | `1 : 0..N` |
| Grade - Performance (`PerformanceGrade`) | `N : M` |
| Performance - Seat (`PerformanceSeat`) | `N : M` |
| PerformanceGrade - PerformanceSeat | `1 : 0..N` |
| Order - Payment | `1 : 0..N` |
| OrderSeat - Ticket | `1 : 0..1` |

## 데이터 전환

기존 migration checksum은 바꾸지 않는다. `SeedDataLoader`/`kopis-curated.sql`이 모든 Show/
Performance와 모든 Seat를 `CROSS JOIN`하는 synthetic 데이터라는 사실을
`CurrentSeatVenueShowGradeSchemaTest`와 seed SQL 확인으로 검증했다 — 하나의 물리 Seat가 서로 다른
112개 Venue의 Show에 동시에 쓰이므로, 이 데이터에서 "실제" Seat-Venue 소속을 추론할 근거가 없다.

**따라서 backfill 전략이 아니라 재생성 전략을 채택한다.** Phase 2 Task 3에서 seed를 Venue별로
결정적으로 좌석을 새로 생성하는 방식으로 바꾸고, 기존 synthetic `Seat`/`ShowSeat`/`PerformanceSeat`
row를 실제 Venue에 임의로 짜맞추려 하지 않는다. 운영 데이터가 이 저장소 seed와 다른 실제 원본을
가진 경우, 그 backfill은 이 ADR의 범위가 아니라 별도로 결정한다.

## 이 ADR이 결정하지 않는 것

- PG 연동, 결제 승인/실패/취소 API, callback/webhook의 구체적 프로토콜
- Ticket 자동 발급, QR, 입장, 사용, 취소, 환불, 양도
- `payment -> booking`, `ticketing -> booking`의 실제 공개 계약 형태(재설계 스펙의 "미래 모듈별
  공개 계약 초안"은 방향만 보존한 설계이며 이번에 확정하지 않는다)
- 결제 승인과 Hold 만료가 경쟁하는 상황의 최종 정책(승인 시각 기준, row lock, PG 취소/환불 보정)

이 항목들은 별도 설계와 승인을 거쳐 각자의 ADR/스펙으로 결정한다.

## ADR 0003과의 관계

ADR 0003의 module 경계 원칙(패키지 기반 닫힌 module, `<module>`, 공개 계약은 작은
interface + 불변 record snapshot, cross-module JPA 금지, Spring Modulith 이벤트, module-aware
Flyway)은 그대로 유효하다 — 이 ADR은 그 메커니즘을 바꾸지 않는다.

**이 ADR이 supersede하는 것은 §3(승인된 의존 DAG)의 module set과 DAG 값뿐이다.** ADR 0003이
기록한 시점의 DAG는 `payment`/`ticketing`을 몰랐고, 이 ADR의 4번 결정이 그 자리를 채운다. §1(단일
Gradle 프로젝트), §2(패키지 기반 닫힌 모듈), §4(scalar ID와 공개 API), §5(Modulith 이벤트), §6~10(각
공유/전역 모듈의 존재 이유), §11(showlike 흡수)은 이 ADR과 무관하며 계속 유효하다.

## ADR 0001과의 관계

ADR 0001(Selection과 Hold는 독립이다)의 업무 결정은 이 재설계로 바뀌지 않는다. `PerformanceSeat`가
가격·등급 필드를 추가로 갖게 되지만 Selection/Hold가 `PerformanceSeat` 판매 상태를 다루는 방식
자체는 그대로다.
