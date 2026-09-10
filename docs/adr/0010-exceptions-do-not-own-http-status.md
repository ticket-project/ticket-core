# 업무 예외는 HTTP 상태를 모른다 — 웹 계층이 상태를 정한다

## 상태(2026-09-10): 채택·구현됨. ADR 0002를 수정(모듈 소유 원칙 자체는 유지, "예외가 완성된
HTTP 응답 계약을 스스로 갖는다"는 서술만 대체)

> 2026-09-10 갱신: 패키지 배치와 `support` 관련 서술은
> [ADR 0011](0011-shared-technical-package-layout.md)으로 대체한다. 모듈이 자기 오류를
> 소유하고 웹 계층이 HTTP 상태를 정한다는 원칙은 유지한다.

## 배경

ADR 0002가 확정한 모듈 소유 오류 계약에서 `TicketException`은 `HttpStatus`/`ErrorCode`/
`message`/`data`를 전부 스스로 들고 있었다. 각 module handler(`BookingExceptionHandler` 등)는
`exception.getStatus()`를 그대로 `ResponseEntity.status(...)`에 옮겨 담기만 했다 — handler에
로직이랄 게 없었다.

이 형태의 문제: 업무 코드(예외 생성자)가 HTTP 표현(상태 코드)까지 결정한다. 예외는
`org.springframework.http.HttpStatus`를 몰라야 순수한 업무 실패로 재사용할 수 있는데,
`extends TicketException`이 강제로 그 의존을 물려준다. `RestAuthenticationEntryPoint`/
`RestAccessDeniedHandler`처럼 handler가 아니라 직접 응답을 쓰는 경로도 결국
`exception.getStatus()`를 읽어야 해서 같은 결합이 반복됐다.

## 결정

1. **`TicketException`은 `errorCode`·`message`·`data`만 갖는다.** `HttpStatus` 필드·생성자
   인수·접근자를 없앤다. 업무 코드는 "무엇이 실패했는가"(errorCode)와 "그 실패를 좁히는
   사실"(data)만 전달한다.
2. **HTTP 상태는 그 오류를 처리하는 쪽이 정한다.** 각 module handler(`BookingExceptionHandler`,
   `MemberExceptionHandler`, `ShowExceptionHandler`, `LikeExceptionHandler`)가 구체 타입을 보고
   상태를 고른다 — 여러 타입이 있으면 Java 21 패턴 매칭 switch로, 타입이 하나뿐이면 상수로
   충분하다. 공통 오류 셋(`InvalidRequestException`/`NotFoundException`/`InternalErrorException`)
   의 상태는 `GlobalExceptionHandler`가 고정한다. Security filter chain에서 나 handler를 거치지
   않는 두 경로(`RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`)는 각자 알고 있는 상태
   상수(401/403)를 직접 쓴다 — `MemberExceptionHandler`의 판단과 같은 값이다.
3. **E-code 값·공개 메시지·`error.data`는 바꾸지 않는다.** 외부에 노출되는 것은 그대로다.
   `com.ticket.error.ExceptionHandlerScopeTest`(module handler가 자기 module 오류만 잡는지)와
   `com.ticket.error.ErrorCodeUniquenessTest`(E-code 전역 유일성)는 수정 없이 그대로 강제한다 —
   전자는 handler 패키지에서 module 접두어를 동적으로 유도하므로 handler가 어디로 옮겨가도
   유효하다.
4. **모듈 handler를 새 오류 타입마다 고쳐야 한다는 비용이 생긴다.** ADR 0002의 "예외를 추가할
   때 handler를 수정하지 않는다"는 문장은 더 이상 맞지 않는다 — 새 concrete 타입을 추가하면
   그 module handler의 switch에 분기를 하나 더한다. 이 비용을 감수하는 이유는 3번(외부 계약
   불변)과 아래 "검토한 대안"의 트레이드오프 때문이다.
5. **예외 클래스는 통합하지 않는다.** 20개 concrete 타입(booking 15, member 3, show 1, like 1)
   전부 유지한다. 다들 (고정 코드, 고정 메시지) 조합만 다르고 `data`/`reason` 같은 추가 필드가
   없는 것은 사실이지만, 각각 서로 다른 업무 실패를 가리키는 이름 있는 타입이고 그 이름 자체가
   호출부에서 무엇이 잘못됐는지 드러낸다(`throw new SeatAlreadySelectedException()`이
   `throw new BookingException(BookingErrorCode.E4001)`보다 호출부에서 더 많은 것을 말해 준다).
   이름 없는 파라미터화된 하나의 클래스로 합치면 타입 안전성(오타난 문자열 대신 컴파일 타임
   클래스 참조)과 IDE 탐색성을 잃는다. 어느 것도 별도 재시도/롤백 판단이나 별도 catch 대상이
   아니었으므로(아래 "확인한 경로" 참고) 합칠 근거는 "형태가 같다"뿐이었고, 그 근거만으로
   합치지 않기로 했다.

## 확인한 경로 (합치거나 없애지 않은 이유)

- **`GetOrderStatusUseCase`의 `NotFoundException` 처리**: `memberLookup.requireActive`가 던지는
  공통 `NotFoundException`을 잡아 `OrderNotOwnedException`으로 바꿔 던진다 — 호출부가 타입으로
  분기하는 실제 사례라 `NotFoundException`을 없애거나 다른 것과 합칠 수 없다.
- **`WebSocketAuthInterceptor`의 `TicketException` 처리**: STOMP 인증 실패를 base 타입
  `TicketException` 하나로 넓게 잡아 `MessageDeliveryException`으로 바꾼다. base 타입을 캐치
  대상으로 실제로 쓰는 사례라 `TicketException`을 없앨 수 없다.
- **`AdmissionTokenException`의 `reason`/전용 handler**: `reason`은 응답에 노출되지 않는
  진단 전용 필드이자 `AdmissionExceptionHandler`의 로그에만 쓰인다 — 다른 예외가 갖지 않는
  고유한 사실 정보라 유지한다. 별도 handler를 두는 이유(검증 실패 사유를 admission 어휘로
  로깅)도 그대로다.
- **트랜잭션 rollback/retry 설정의 예외 타입 지정**: `noRollbackFor`/`rollbackFor`/`@Retryable`
  가 특정 예외 타입을 지정하는 곳이 있는지 전체 검색했다 — 없다. 이 프로젝트는 기본
  `RuntimeException` rollback 정책만 쓰고, 이번 변경이 그 정책에 영향을 주지 않는다.

## 검토한 대안

- **예외가 상태까지 계속 들고, `getStatus()`만 `@Deprecated`로 남긴다**: 점진적 이전처럼
  보이지만 결국 Spring HTTP 의존을 exception 레이어에 그대로 남긴다 — 이번 요청의 목표(업무
  예외에서 HTTP 의존 제거) 자체를 달성하지 못해 기각했다.
- **전역 `Map<ErrorCode, HttpStatus>` 레지스트리**: module 경계를 넘는 단일 카탈로그가 생겨
  "각 module이 자기 오류의 HTTP 매핑을 소유한다"는 원칙과 충돌한다. 새 오류 코드를 추가할 때
  두 곳(module `<Module>ErrorCode` enum과 전역 레지스트리)을 동시에 고쳐야 해 오히려 결합이
  늘어난다. `ExceptionHandlerScopeTest`가 강제하는 "handler가 자기 module만 잡는다"는 경계와도
  방향이 맞지 않아 기각했다.
- **`ProblemDetail`/`ErrorResponseException` 도입**: 이번 범위에서 명시적으로 제외됐다(사용자
  요청). ADR 0002가 이미 같은 이유(외부 계약 파손)로 기각한 방향이라 재검토하지 않았다.

## 영향

- `com.ticket.error.TicketException`이 `HttpStatus`/`getStatus()`를 잃는다. `error`,
  `booking`/`member`/`show`/`like`의 base 예외(4개)와 concrete 예외(20개) 생성자 시그니처가
  한 인수씩 짧아진다.
- 각 module handler(`BookingExceptionHandler` 등)가 구체 타입 → HTTP 상태 switch(또는 타입이
  하나뿐이면 상수)를 갖는다.
- `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`가 상태를 `HttpStatus.UNAUTHORIZED`/
  `FORBIDDEN` 상수로 직접 쓴다.
- **패키지도 함께 정리됐다**(capability 이전과 별개 결정): 각 module의 concrete 예외는 실제로
  그 예외를 던지는 capability로, 여러 capability가 던지는 예외와 module 전체를 다루는
  base·handler는 `<module>.support.exception[.handler]`로(Like는 capability가 하나뿐이라
  `like.preference.exception`), admission은 자기 완결 capability라
  `booking.admission.exception`으로 옮겼다. `exception` 패키지 자체를 capability로 나누지
  않는다는 원칙(ADR 0002·`docs/architecture.md`)은 그대로다 — 다만 그 패키지가 이제
  `<module>` 바로 아래가 아니라 `<module>.<capability>` 또는 `<module>.support` 아래에 있다.
- 외부에 노출되는 값(URL·HTTP 상태·E-code·공개 메시지·`error.data`의 형태)은 전부 그대로다 —
  `BookingExceptionHandlerTest`/`AdmissionExceptionHandlerTest`/`MemberExceptionHandlerTest`/
  `ShowExceptionHandlerTest`/`LikeExceptionHandlerTest`/`GlobalExceptionHandlerTest`가 이
  변경 전후로 값이 같음을 고정한다.
