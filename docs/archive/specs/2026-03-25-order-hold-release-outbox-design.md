> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-25. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Hold Release Outbox Design

## 목표

주문 취소 또는 만료 직후 좌석이 거의 즉시 다시 보이도록 하면서, Redis hold 해제나 좌석 상태 publish가 실패해도 최종적으로는 반드시 재시도되게 만든다.

## 현재 구조

- 주문 종료 트랜잭션 안에서 주문 상태만 변경한다.
- 같은 트랜잭션 안에서 `HoldReleaseOutbox` row를 저장한다.
- 실제 hold 해제와 좌석 상태 publish는 scheduler가 나중에 처리한다.

이 구조는 최종적 일관성은 확보하지만, 좌석이 바로 풀려야 하는 티켓팅 UX에는 반응 속도가 느리다.

## 설계

### 1. outbox는 유지한다

- 주문 종료 트랜잭션 안에서 `HoldReleaseOutbox`를 저장한다.
- outbox row는 재시도 기준 데이터이자, 즉시 처리의 입력 데이터가 된다.

### 2. 커밋 후 즉시 처리한다

- `HoldReleaseOutbox`가 저장되면 application event를 발행한다.
- `@TransactionalEventListener(phase = AFTER_COMMIT)`가 방금 저장한 outbox id를 받아 즉시 처리한다.
- 즉시 처리는 별도 트랜잭션에서 `HoldReleaseOutboxProcessor`를 호출한다.

### 3. 실패하면 scheduler가 재시도한다

- 즉시 처리 실패 시 원 주문 종료 트랜잭션은 롤백하지 않는다.
- `HoldReleaseOutboxProcessor`가 `nextAttemptAt`, `retryCount`, `lastError`를 갱신한다.
- `HoldReleaseOutboxScheduler`는 미완료 row를 재시도한다.

## 경계

- 주문 취소/만료 유스케이스는 여전히 주문 상태 변경과 outbox 저장까지만 책임진다.
- hold 해제와 좌석 상태 publish는 outbox 처리 책임으로 둔다.
- 즉시 처리와 배치 재시도는 같은 processor를 사용해서 로직을 한 군데 유지한다.

## 기대 효과

- 정상 경로에서는 커밋 직후 좌석이 거의 즉시 풀린다.
- Redis나 publish 실패 시에도 outbox row가 남아서 재시도된다.
- 주문 종료 트랜잭션이 외부 시스템 실패에 직접 흔들리지 않는다.
