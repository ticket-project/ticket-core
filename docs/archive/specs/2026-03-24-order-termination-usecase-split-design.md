> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-24. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Termination UseCase Split Design

## 목표
- `TerminateOrderUseCase`를 `CancelOrderUseCase`와 `ExpireOrderUseCase`로 분리한다.
- `OrderTerminationDomainService`, `OrderLifecycleDomainService` 같은 큰 서비스 객체를 제거하고 책임별 컴포넌트로 나눈다.
- 기존 취소/만료 동작과 이벤트 발행 계약은 유지한다.

## 설계
- `CancelOrderUseCase`
  - 회원 검증
  - 취소 대상 주문 조회
  - `OrderCanceler` 호출
  - `OrderCancelledEvent` 발행
- `ExpireOrderUseCase`
  - `orderId` 또는 `holdKey`로 `PENDING` 주문 조회
  - 주문이 없으면 조용히 종료
  - `OrderExpirer` 호출
  - 결과가 있을 때만 `OrderExpiredEvent` 발행

## 구현 컴포넌트
- `OrderTerminationContextLoader`
  - 주문 좌석 조회
  - `seatIds` 추출
- `OrderCanceler`
  - 주문 취소 상태 전이
  - 주문 좌석 취소 상태 전이
  - hold history canceled 기록
  - 이벤트용 결과 반환
- `OrderExpirer`
  - pending 여부 확인
  - 주문 만료 상태 전이
  - 주문 좌석 만료 상태 전이
  - hold history expired 기록
  - 이벤트용 결과 반환

## 변경 범위
- 주문 종료 유스케이스
- 주문 만료 스케줄러
- Redis 만료 리스너
- 주문 컨트롤러
- 종료 관련 테스트

## 테스트 전략
- 기존 `TerminateOrderUseCaseTest`를 취소/만료 테스트로 분리한다.
- 새 구현 컴포넌트마다 독립 테스트를 추가한다.
- 스케줄러와 리스너는 새 유스케이스를 바라보도록 테스트를 갱신한다.
