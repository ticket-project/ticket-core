> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-24. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Package Restructure Design

## 목표
- `order` 패키지를 역할 기준이 아니라 기능 기준으로 재구성한다.
- `finder`, `application`, `command/usecase`, `query/usecase`처럼 섞여 있는 구조를 `create`, `cancel`, `expire`, `query`, `shared` 중심으로 정리한다.
- 동작은 유지하고 import/패키지 경계만 명확히 만든다.

## 대상 구조
- `com.ticket.core.domain.order.create`
  - `CreateOrderUseCase`
  - `CreateOrderValidator`
  - `HoldAllocator`
  - `HoldAllocation`
  - `RequestedSeatIds`
  - `OrderCreator`
  - `OrderKeyGenerator`
- `com.ticket.core.domain.order.cancel`
  - `CancelOrderUseCase`
  - `OrderCanceler`
- `com.ticket.core.domain.order.expire`
  - `ExpireOrderUseCase`
  - `OrderExpirer`
  - `OrderExpirationScheduler`
- `com.ticket.core.domain.order.query`
  - `GetOrderDetailUseCase`
  - `OrderDetailResponseMapper`
- `com.ticket.core.domain.order.shared`
  - `OrderTerminationContext`
  - `OrderTerminationContextLoader`
  - `OrderTerminationResult`

## 공통 객체 처리
- `OrderFinder`, `OrderSeatFinder`는 제거한다.
- 기능별 패키지에서 필요한 조회는 리포지토리를 직접 사용하거나 해당 기능 내부 로더로 흡수한다.
- `model`, `repository`, `event`만 루트 공통으로 유지한다.

## 테스트 전략
- 패키지 이동에 맞춰 테스트 패키지도 같은 기능 기준으로 이동한다.
- `finder` 테스트는 제거하고, 필요한 조회 규칙은 각 기능 테스트로 흡수한다.
- `core-api` 모듈 테스트로 회귀를 확인한다.
