# 오류 계약을 모듈별로 소유하고 API에서 공통 처리한다

## Status

채택됨. 2026-09-02에 한 번 전역 카탈로그로 되돌려졌다가, Spring Modulith 이동 이후 모듈 소유로
다시 확정됐다. **2026-09-10, [ADR 0010](0010-exceptions-do-not-own-http-status.md)이 "예외가
HTTP 상태까지 스스로 갖는다"는 아래 서술을 수정했다** — 모듈이 자기 오류를 소유한다는 원칙
자체는 그대로다. `TicketException`은 이제 errorCode·message·data만 갖고, HTTP 상태는 각
module handler가 정한다.

## Current Decision

- 업무 오류는 소유 모듈이 갖는다 — `<Module>ErrorCode` enum과 예외 클래스, 그리고
  `exception.handler`의 handler(`@Order(HIGHEST_PRECEDENCE)`). 정확한 패키지 위치(capability
  아래인지 `<module>.support` 아래인지)는 [ADR 0010](0010-exceptions-do-not-own-http-status.md)
  참고.
- 어느 모듈의 것도 아닌 오류(E400·E404·E500)와 base 타입 `TicketException`, `ErrorCode`
  interface, 전역 `GlobalExceptionHandler`(`@Order(LOWEST_PRECEDENCE)`)는 `com.ticket.error`가
  소유한다.
- **`ProblemDetail`은 채택하지 않았다.** `ApiResponse`(`{result, data, error{code, message,
  data}}`) envelope와 E-code·HTTP 상태를 그대로 쓴다. envelope(`ApiResponse`/`ErrorMessage`/
  `ResultType`/`SliceResponse`)는 `com.ticket.web`이 소유한다(ADR 0003 §10). `error -> web`
  단방향만 있고 반대로 `web`이 오류 타입을 알면 순환이 된다 — `ApiResponse`는 완성된
  code·message·data만 받고 오류 타입을 모른다. **HTTP 상태는 `ApiResponse`가 아니라 각
  handler가 결정해 `ResponseEntity`에 싣는다** — 예외 자신은 상태를 모른다(ADR 0010).
- 강제 장치는 `com.ticket.error.ErrorCodeUniquenessTest`(E-code 전역 유일성)와
  `ExceptionHandlerScopeTest`(모듈 handler가 자기 오류만 잡는지)다.

## 검토한 대안

- **`ProblemDetail` 채택**: 표준 오류 포맷이라는 장점이 있었지만, `ticket-fe`가 `res.data`로
  봉투를 벗기는 계약과 `gatling-test`가 `E4001`/`E6000`/`E6003` 같은 E-code를 하드코딩하는 외부
  계약을 둘 다 깨뜨린다. 두 소비자를 함께 바꿀 이유가 없어 기각했다.
- **오류별 API 매퍼(도메인·API 표현 완전 분리)**: 표현 계층 분리는 가장 강하지만, 기능 오류를
  추가할 때마다 오류 정의와 API 매핑을 함께 수정해야 한다. 이 프로젝트는 오류를 한 번 정의해
  호출부에서 바로 쓰고 API 매핑 누락 없이 동일한 응답을 만드는 개발 경험을 우선해 기각했다.
- **전역 카탈로그 하나로 통합**: 2026-09-02에 실제로 한 번 이 방향으로 되돌린 적이 있다(당시
  구조는 전역 `ErrorType`/`CoreException`/`ApiControllerAdvice`). Spring Modulith 이동으로 기능이
  실제 module 단위로 옮겨진 뒤 재검토해 모듈 소유로 다시 확정했다. 이 되돌림의 흔적은 지금
  코드베이스에 없다 — `CoreException`, `ApiControllerAdvice` 모두 존재하지 않는다.

## Consequences

- 오류를 추가할 때 API 매퍼나 `GlobalExceptionHandler`를 수정하지 않는다. 모듈이 자기
  `<Module>ErrorCode`와 예외 클래스, 얇은 handler만 추가하면 된다.
- 모듈 handler가 자기 오류만 잡는지는 `ExceptionHandlerScopeTest`가, E-code 전역 유일성은
  `ErrorCodeUniquenessTest`가 막는다.
- `com.ticket.error`는 공통 오류(E400/E404/E500)와 두 base 타입(`TicketException`, `ErrorCode`)만
  소유하고 업무 오류를 소유하지 않는다.
