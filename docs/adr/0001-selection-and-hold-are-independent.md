# Selection과 Hold를 독립으로 둔다

> **구현 참조 갱신(2026-09-02):** 아래 본문의 업무 결정 자체는 바뀌지 않았다. 다만 예시로 든
> 클래스는 Spring Modulith 모듈 전환으로 이동·대체됐다 —
> `CreateOrderValidator`/`HoldSeatAvailabilityValidator`는
> `com.ticket.booking.internal.domain.hold.command.HoldSeatAvailabilityValidator`로,
> `AsyncHoldCreationPostCommitNotifier`는 booking이 발행하는 `OrderStarted`/`OrderTerminated`
> 이벤트와 `BookingEventListeners`로 대체됐다. 상세는
> [ADR 0003의 "ADR 0001과의 관계"](0003-spring-modulith-application-module-boundaries.md#adr-0001과의-관계)를
> 본다.

좌석에는 Redis 기반 점유가 두 종류 있다. **Selection**은 회원이 좌석을 살펴보는 동안의 임시
표시이고, **Hold**는 진행 중인 Order가 좌석을 붙잡은 상태다. 주문 생성은 Hold와 판매 상태만
검증하고 Selection은 보지 않는다(`CreateOrderValidator` → `HoldSeatAvailabilityValidator`).
즉 Selection이 살아 있어도 다른 회원이 같은 좌석으로 주문할 수 있고, Selection 없이 바로
주문할 수도 있다.

Selection을 예약으로 승격시키는 안도 있었다. 화면을 열어둔 사용자가 좌석을 확실히 잡게 되어
UX는 좋아지지만, 결제로 넘어가지 않고 떠난 사용자가 좌석을 오래 묶고 잠금 경로가 둘로 늘어난다.
판매 정합성은 Hold와 DB가 지키고 Selection은 화면 협조 장치로 남기는 쪽을 골랐다.

## Consequences

- 회원 A가 좌석을 선택해 둔 상태에서 회원 B가 같은 좌석으로 주문하면 **B가 성공한다.**
  A는 화면에서 찜한 좌석을 잃는다. 이것은 결함이 아니라 위 결정의 결과다.
- 주문 생성 후 Selection을 정리하는 것(`AsyncHoldCreationPostCommitNotifier`)은 잠금을
  Hold로 넘기는 것이 아니라 남은 표시를 치우는 것이다. 그래서 커밋 직후 둘이 함께 존재하는
  짧은 창이 있어도 정합성 문제가 되지 않는다.
- `HoldSeatAvailabilityValidator`에 Selection 검사가 없는 것은 누락이 아니다. 추가하면 위
  결정을 뒤집는 것이므로 이 문서를 먼저 갱신한다.
