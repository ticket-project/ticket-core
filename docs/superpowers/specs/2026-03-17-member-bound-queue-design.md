# Member-Bound Queue Design

> 기준 변경: 이 문서는 과거 ticket-be 내장 queue/queueToken 설계 기록이다. 현재 실행 기준은 2026-05-24-separated-queue-server-design.md와 2026-05-24-separated-queue-server.md이다. 새 구조에서는 queueToken/X-Queue-Token 대신 queueSessionId/X-Queue-Session과 admissionToken/X-Admission-Token을 사용한다.


**목표**

- `QueueController` API를 인증 사용자 전용으로 전환한다.
- 대기열 엔트리를 `memberId`에 귀속시켜 사용자 소유권을 강제한다.
- 같은 회원이 같은 공연에 다시 `enter`하면 기존 엔트리를 포기하고 새 엔트리를 발급받도록 한다.

**적용 범위**

- 포함: queue 인증 강제, Redis 회원-공연 매핑, 재진입 시 기존 엔트리 정리, 소유권 검증
- 제외: 관리자 정책 UI, 대기열 복구 기능, 기기/브라우저 단위 세션 유지

## 1. 설계 원칙

- 대기열 권한 기준은 `queueEntryId`가 아니라 `memberId`다.
- `queueEntryId`는 개별 진입 시도를 식별하는 런타임 식별자다.
- 같은 회원이 같은 공연에서 다시 진입하면 기존 엔트리는 유지하지 않는다.
- 보안 검사는 가능한 한 컨트롤러 진입과 유스케이스 내부 양쪽에서 보강한다.

## 2. 인증 정책

- `POST /api/v1/queue/performances/{performanceId}/enter`
- `GET /api/v1/queue/performances/{performanceId}/status`
- `POST /api/v1/queue/performances/{performanceId}/leave`

위 세 API는 모두 인증 사용자만 호출 가능하게 변경한다.

`/api/v1/queue/**` 전체를 익명 허용하던 보안 설정은 제거하고, 기본 `authenticated()` 정책을 적용한다.

## 3. 데이터 모델 변경

### 3.1 Queue 유스케이스 입력

- `QueueEntryUseCase.Input`
  - `performanceId`
  - `memberId`
- `GetQueueStatusUseCase.Input`
  - `performanceId`
  - `memberId`
  - `queueEntryId`
- `LeaveQueueUseCase.Input`
  - `performanceId`
  - `memberId`
  - `queueEntryId`

### 3.2 Redis entry 저장값

기존 entry hash에 아래 필드를 추가한다.

- `memberId`

### 3.3 Redis 회원 매핑 키

같은 회원의 같은 공연 내 현재 엔트리를 찾기 위해 별도 키를 둔다.

- `queue:performance:{performanceId}:member:{memberId}`
  - `STRING`
  - value: 현재 `queueEntryId`
  - TTL: entry retention과 동일하게 관리

이 키는 재진입 정리, 소유권 검증, 현재 엔트리 추적에 사용한다.

## 4. 요청 흐름

### 4.1 enter

1. 인증된 `memberId`로 요청을 받는다.
2. `performanceId + memberId` 회원 매핑 키에서 기존 `queueEntryId`를 조회한다.
3. 기존 엔트리가 있으면 상태를 읽는다.
4. 기존 엔트리가 `WAITING`이면 대기열에서 제거하고 `LEFT` 처리한다.
5. 기존 엔트리가 `ADMITTED`이면 active set과 token key를 제거하고 `LEFT` 처리한다.
6. 기존 회원 매핑 키를 삭제한다.
7. 현재 정책 기준으로 즉시 입장 또는 waiting 등록을 다시 수행한다.
8. 새 엔트리의 `queueEntryId`를 회원 매핑 키에 저장한다.

결과적으로 새로고침, 재접속, 중복 클릭은 모두 기존 권리를 포기하고 새 번호를 받는다.

### 4.2 status

1. `queueEntryId`로 엔트리를 조회한다.
2. 엔트리가 없으면 `EXPIRED`를 반환한다.
3. 엔트리의 `performanceId`, `memberId`가 요청값과 일치하는지 확인한다.
4. 일치하지 않으면 명시적 예외를 반환한다.
5. 소유자가 맞으면 기존 로직대로 `WAITING`, `ADMITTED`, `EXPIRED`를 계산한다.

이 흐름으로 다른 사용자의 `queueEntryId`를 추측해 조회하는 시도를 차단한다.

### 4.3 leave

1. `queueEntryId`로 엔트리를 조회한다.
2. 엔트리가 없거나 소유자가 다르면 아무 동작 없이 종료하지 않고, 명시적 예외를 반환한다.
3. `WAITING`이면 waiting set에서 제거 후 `LEFT` 처리한다.
4. `ADMITTED`이면 active set과 token key 제거 후 `LEFT` 처리하고 `advance()`를 호출한다.
5. 회원 매핑 키도 함께 제거한다.

## 5. 권한/예외 정책

- 비인증 사용자의 queue API 호출: 기존 보안 예외, `401`
- 남의 엔트리 조회/이탈 시도: queue 전용 권한 예외 또는 기존 공통 권한 예외 재사용
- 이미 만료된 엔트리 status 조회: `EXPIRED`
- 이미 만료된 엔트리 leave 호출: 명시적 예외보다 idempotent 처리보다 보안 우선이므로 소유 검증 실패가 아니면 무시 가능

구현에서는 기존 에러 체계와 가장 잘 맞는 공통 예외를 우선 재사용한다.

## 6. 영향 범위

- `QueueController`는 `MemberPrincipal`을 받도록 시그니처가 바뀐다.
- `QueueRuntimeStore`, `RedisQueueRuntimeStore`, `QueueEntryRuntime`이 `memberId`를 다뤄야 한다.
- `QueueTokenGatekeeper`는 토큰 유효성 판단 자체는 그대로 두되, queue 발급 단계가 회원 귀속으로 바뀐다.
- 프론트는 queue API 호출 전에 로그인/JWT 확보가 필요하다.

## 7. 테스트 전략

- `QueueEntryUseCase`
  - 같은 회원 재진입 시 기존 waiting 엔트리가 정리되고 새 엔트리가 생성되는지
  - 같은 회원 재진입 시 기존 admitted 엔트리가 정리되고 필요 시 다음 대기자가 승격되는지
  - 빈 자리가 있으면 즉시 admitted 되는지
- `GetQueueStatusUseCase`
  - 본인 엔트리는 정상 조회되는지
  - 다른 회원 엔트리 조회는 예외가 발생하는지
- `LeaveQueueUseCase`
  - 본인 waiting/admitted 엔트리만 정상 이탈되는지
  - admitted 이탈 시 advance가 호출되는지
- 보안 테스트
  - queue API 비인증 요청이 `401`인지
  - 인증 요청은 정상 진입하는지

## 8. 남은 리스크

- 재진입 시 기존 admitted 엔트리를 제거하면 사용자는 스스로 입장권을 포기할 수 있다. 이는 의도된 정책이지만 UX 안내가 필요하다.
- 회원 매핑 키와 entry hash 간 정합성 문제가 생기면 정리 로직이 필요하다. 구현에서는 stale entry를 최대한 무시하고 새 진입을 허용하는 방향이 안전하다.
- `queueEntryId`를 쿼리 파라미터로 계속 받는 구조는 유지되므로, 보안은 반드시 `memberId` 소유권 검증에 의존해야 한다.
