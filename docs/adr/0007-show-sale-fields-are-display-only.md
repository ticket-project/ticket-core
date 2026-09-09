# Show의 판매 필드는 표시 전용이고, 판단은 Booking이 한다

## 상태(2026-09-09): 채택·구현됨

## 배경

ADR 0006(A2)이 회차(Performance) 단위 예매 접수 기간·Hold 한도·대기열 정책을 Booking BC의
`PerformanceSalesPolicy`로 이관했다. 그 결과 실제 주문 접수 가능 여부는 전적으로 Booking이
판단하고, `show -> booking` 의존은 금지돼 있어 Show가 그 판단을 되물어볼 경로가 구조적으로
없다.

그런데 `Show` entity에는 `saleType`/`saleStartDate`/`saleEndDate`와 `getBookingStatus(now)`가
그대로 남아 있었다. 이름만 보면 판매 정책의 원본처럼 읽히지만 실제로는:

- 목록·검색·정렬·상세 조회에만 쓰인다.
- 쓰기 경로가 seed와 테스트뿐이다(`ShowRepository`에 저장 메서드조차 없다).
- Booking의 `PerformanceSalesPolicy`(`OrderAcceptanceWindow`)와 **정합성 검증 없이 완전히
  독립적인 데이터**다 — booking V6 backfill이 `PERFORMANCES.order_open_time`에서 왔고
  `SHOWS.sale_start_date`를 보지 않았으므로 처음부터 서로 다른 값이었다.

어떤 ADR도 이 Show 단위 필드의 소유권을 다루지 않았다. 유일한 추적 지점은
`docs/technical-debt.md`의 TD-12였고, 그마저도 "예매 가능 여부 판정 로직이 두 곳(실제로는 세
곳)에 중복 구현됐다"는 증상만 기록했지 "왜 Show가 이 값을 갖는가"는 다루지 않았다.

## 결정

**"실제 주문을 받을 수 있는가"는 Booking만 판단한다. "화면에 어떤 판매 상태를 보여줄 것인가"는
Show read model이 표현한다.** 이 둘은 이미 다른 데이터였으므로 구조는 바뀌지 않는다 — 이번
결정은 그 사실을 **이름으로 드러내는 것**이다.

1. `Show`의 판매 표시 필드는 `display` 어휘로 통일한다:
   `saleType` → `displaySaleType`, `saleStartDate`/`saleEndDate` →
   `displaySaleWindow`(`@Embeddable DisplaySaleWindow`)의 `startsAt`/`endsAt`,
   `getBookingStatus(now)` → `saleDisplayStatusAt(now)`(내부적으로
   `DisplaySaleWindow.statusAt(now)`에 위임), `BookingStatus` → `SaleDisplayStatus`.
2. 판정 규칙은 `DisplaySaleWindow.statusAt(now)` 한 곳에만 둔다(TD-12 해소). 이전에는 도메인
   (`Show.getBookingStatus`), Querydsl 술어(`BookingStatusPredicateFactory`), 하드코딩된 조건
   (`QuerydslShowConditionBuilder`)이 각자 구현했고, 그중 도메인과 Querydsl 술어의 **null
   처리가 서로 달랐다**(창이 null이면 도메인은 CLOSED로 보는데 Querydsl 필터는 세 조건 어디에도
   걸리지 않았다). 통합 후에는 세 CLOSED 조건에 null 창을 명시적으로 포함시켜 하나로 맞춘다.
3. `Show`의 표시 창과 Booking의 `OrderAcceptanceWindow`가 서로 어긋날 수 있다는 사실을 **버그가
   아니라 허용된 결과**로 명시한다. `/api/v1/shows/{id}`가 `ON_SALE`이라 응답해도 주문 API가
   `E3002`로 거절할 수 있다 — 표시는 표시고 판단은 Booking이다. 이 둘을 동기화하는 코드는
   추가하지 않는다.
4. 공개 API JSON 이름(`saleType`/`saleStartDate`/`saleEndDate`/`bookingStatus`)은 바꾸지 않는다.
   `ticket-fe`가 이미 이 이름을 쓰고 있어서다. 내부 컴포넌트는 `display` 어휘로 바꾸고
   `@JsonProperty`로 옛 이름을 고정한다. DB 컬럼도 Flyway로 rename한다(`sale_type` →
   `display_sale_type`, `sale_start_date`/`sale_end_date` → `display_sale_starts_at`/
   `display_sale_ends_at`).

## 결정하지 않는 것

- **가격 잠금 규칙의 소유자**(TD-15) — "판매 오픈 전에만 가격을 바꿀 수 있다"는 불변식의 판단
  근거(접수 시각·좌석 편성 여부)가 Booking BC에 있다는 문제는 이 ADR의 범위가 아니다. 가격
  변경 기능이 아직 없어 드러나지 않으므로 관리자 CRUD 착수 시점에 따로 결정한다.
- **판매 오픈 예정 목록의 상한**(PD-03), **`viewCount` 정렬 키 문제**(TD-13) — 무관한 별개
  제품 결정이다.
- Show와 Booking의 표시-판단 불일치를 사용자에게 어떻게 보여줄지(예: 상세 화면에서 "표시상
  판매중이지만 예매가 마감됐을 수 있음" 안내) — 프론트엔드 UX 결정이라 이 ADR이 다루지 않는다.

## 근거

- CONTEXT.md의 `PerformanceSalesPolicy` 정의가 이미 `_Avoid_: 이 정책을 Show가 갖는다는 서술`을
  명시했다 — 이 ADR은 그 경계를 Show 쪽 이름에도 반영하는 것뿐이다.
- `docs/architecture.md`의 View 규칙("조회 전용 `...View` 타입에 비즈니스 로직을 두지 않는다 —
  판정은 별도 validator/policy가 맡는다")에 따라 판정을 `DisplaySaleWindow`라는 값 객체로
  분리했다.

## 영향

- **행동 변화 1건**: `SaleDisplayStatus.CLOSED` 검색 필터에 표시 창이 null인 Show가 새로
  포함된다. seed·fixture 데이터는 두 값이 항상 채워져 있어 계약 테스트 응답 값은 바뀌지 않는다.
- `docs/technical-debt.md`의 TD-12를 해소 처리한다.
