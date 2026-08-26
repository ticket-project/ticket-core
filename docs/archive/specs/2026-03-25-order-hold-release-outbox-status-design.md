> ⚠️ **완료·폐기된 기록이다. 현행 기준이 아니다.**
> 마지막 갱신 2026-03-25. 이후 코드와 규칙이 바뀌었을 수 있다.
> 현재 설계는 `docs/architecture.md`, 현재 규칙은 `AGENTS.md`를 본다.

# Order Hold Release Outbox Status Design

## 목표

`HoldReleaseOutbox`를 운영형 상태 모델로 바꿔서, 조회와 운영 해석을 `completedAt null 여부`가 아니라 명시적인 `status` 기준으로 읽을 수 있게 만든다.

## 상태 모델

- `PENDING`
  - 첫 적재 상태
  - 아직 성공하지 않았고 즉시 처리 또는 재시도 대상이 될 수 있다
- `FAILED`
  - 직전 실행이 실패한 상태
  - `nextAttemptAt` 이후 다시 시도 가능하다
- `COMPLETED`
  - hold 해제와 release publish가 끝난 상태
  - `completedAt`이 반드시 기록된다

## 전이 규칙

- 생성 시 `PENDING`
- 성공 시 `COMPLETED`
- 실패 시 `FAILED`
- `FAILED`는 재시도 후 성공하면 `COMPLETED`, 다시 실패하면 `FAILED` 유지

## 조회 기준

- 기존: `completedAt is null and nextAttemptAt <= now`
- 변경: `status in (PENDING, FAILED) and nextAttemptAt <= now`

## 경계

- `completedAt`은 완료 시각 기록용으로 유지한다
- `status`는 운영 가시성과 쿼리 명시성을 위해 추가한다
- `status`와 `completedAt`의 일관성은 엔티티 메서드가 보장한다
