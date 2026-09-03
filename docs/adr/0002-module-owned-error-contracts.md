# 오류 계약을 모듈별로 소유하고 API에서 공통 처리한다

## 상태: 모듈 소유로 재채택됨(되돌림 이후 다시 결정)

아래 "상태(2026-09-02)" 문단이 기록한 되돌림은 그 문단 자체가 예고한 대로 **Spring Modulith
이동이 끝난 뒤 다시 결정됐고, 결론은 모듈 소유다.** 지금 구조는 이렇다.

- 업무 오류는 소유 모듈의 `<module>.internal.exception`에 있다 — `<Module>ErrorCode` enum과 예외
  클래스, 그리고 `internal.exception.handler`의 얇은 handler(`@Order(HIGHEST_PRECEDENCE)`).
- 어느 모듈의 것도 아닌 오류(E400·E404·E500)와 base 타입 `TicketException`, `ErrorCode`
  interface, 전역 `GlobalExceptionHandler`(`@Order(LOWEST_PRECEDENCE)`)는 `com.ticket.error`다.
- **`ProblemDetail`은 채택하지 않았다.** 되돌림 때 결정한 대로 `ApiResponse`
  (`{result, data, error{code, message, data}}`) envelope와 E-code·HTTP 상태를 그대로 쓴다.
  `ticket-fe`가 `res.data`로 봉투를 벗기고 `gatling-test`가 E-code를 하드코딩하므로 외부 계약은
  한 글자도 바뀌지 않았다.
- 봉투는 `com.ticket.shared`에 있다. `error -> shared` 단방향을 지키려고 `ApiResponse`는 오류
  타입을 모르고, 오류를 던지던 `shared.RequiredInput`은 제거해 각 `UseCase.Input`으로 인라인했다.

첫 시도(`40b43274`)와 다른 점은 **계층별이 아니라 모듈별로 나눴다**는 것과 `ProblemDetail`을
쓰지 않는다는 것이다. 강제 장치는 `com.ticket.error.ErrorCodeUniquenessTest`(E-code 전역 유일성)와
`ExceptionHandlerScopeTest`(모듈 handler가 자기 오류만 잡는지)다.

아래 두 문단은 과거 기록이며 조용히 고치지 않는다.

## 상태(2026-09-02): 구현 이후 되돌림으로 supersede됨

이 ADR의 결정은 `40b43274`로 구현되어 `support:error`/`core-domain.error`/`core-app.error`/
`core-api.error`의 모듈별 `ErrorCode`/`ErrorType`과 `ProblemDetail` 기반 `shared.BusinessProblem`/
`BusinessException`/`web.GlobalProblemDetailHandler`가 만들어졌었다. 이후 사용자가 방향을 바꿔
**`ProblemDetail`을 쓰지 않고, 모듈별로 나눈 오류 카탈로그도 되돌려 전역 하나로 다시 합치기로
결정했다.** 이 결정에 따라 오류 처리는 `support:error` 도입 이전의 전역 `ErrorCode`/`ErrorType`/
`CoreException`/`ApiControllerAdvice`/`ApiResponse.error` envelope 구조로 되돌렸다. 새 위치는
`com.ticket.core.support.exception`(전역 `ErrorCode`, `ErrorType`, `CoreException`,
`AuthException`, `NotFoundException`, `ErrorMessage`)과 `com.ticket.core.support`의
`ApiControllerAdvice`다.

이 되돌림 이후에도 유효한 것:

- 기존 공개 E-CODE, HTTP 상태, 클라이언트 노출 메시지는 그대로 유지했다.
- `CoreException`이 완성된 오류 정의 하나(`ErrorType`)를 들고 다니고, 전역 handler 하나가 이를
  처리하는 구조 자체는 유지한다. 달라진 것은 그 정의가 모듈별 셋(`DomainErrorType` 등)이 아니라
  다시 전역 하나(`ErrorType`)라는 점이다.
- `AuthException`/`NotFoundException`이 `CoreException`을 상속해 handler를 하나로 유지하는 것도
  이 ADR이 정한 대로 유지한다.

**이번 되돌림에서는 업무 모듈별 오류 카탈로그로 재설계하지 않는다.** 전역 `ErrorType`은 Spring
Modulith가 기능별 module로 코드를 옮기기 전까지의 임시 과도기 구조다. 향후 실제 기능이 module로
이동할 때, 그 module이 자신의 오류를 다시 소유할지(이 ADR의 방향)와 그 경계를 어떻게 그을지는
별도로 다시 결정한다. 아래 본문은 처음 승인됐던 결정 기록으로 남기며 조용히 고치지 않는다.

---

현재 `support:error`의 전역 `ErrorType`은 인증, 회원, 공연, 좌석, 주문, 홀드, 대기열의
오류를 모두 소유한다. 각 오류는 `HttpStatus`, `ErrorCode`, 공개 메시지를 한 번에 제공하므로
호출부는 `new CoreException(ErrorType.HOLD_BUSY)`만 작성하면 된다는 장점이 있다. 반면 다음
두 문제가 있다.

- `ErrorType`이 Spring의 `HttpStatus`를 가져 `support:error`와 이를 의존하는 모든 모듈에
  `spring-web`이 전파된다.
- 모든 비즈니스 오류가 `support:error`에 모여 공통 모듈이 각 모듈의 업무 용어를 소유한다.

오류를 도메인 표현과 API 표현으로 완전히 분리하고 API에서 오류별 매퍼를 작성하는 방법도
있다. 이 방법은 표현 계층 분리는 가장 강하지만, 기능 오류를 추가할 때마다 오류 정의와 API
매핑을 함께 수정해야 한다. 이 프로젝트는 기능 개발 중 오류를 한 번 정의한 뒤 호출부에서 바로
사용하고, API 매핑 누락 없이 동일한 응답을 만드는 개발 경험을 우선한다.

## 결정

오류의 공통 형식과 예외 전달 타입만 `support:error`가 소유한다. 실제 E-CODE와 ErrorType은
기능별 패키지가 아니라 현재 강제된 컴파일 경계인 `core-domain`, `core-app`, `core-api`가 각각
하나씩 소유한다.

```text
support:error
├─ ErrorCode
├─ ErrorDefinition
├─ ErrorStatus
└─ CoreException

core-domain
└─ com.ticket.core.domain.error
   ├─ DomainErrorCode
   └─ DomainErrorType

core-app
└─ com.ticket.core.app.error
   ├─ ApplicationErrorCode
   └─ ApplicationErrorType

core-api
└─ com.ticket.core.api.error
   ├─ ApiErrorCode
   ├─ ApiErrorType
   ├─ ApiErrorResponse
   └─ GlobalExceptionHandler
```

`core-infra`는 클라이언트 공개 오류 카탈로그를 기본적으로 소유하지 않는다. Redis, DB, 외부
HTTP 호출 실패는 기술 예외로 전파한다. 기술 실패를 `HOLD_BUSY`처럼 안정적인 유스케이스
실패로 공개해야 할 때만 `core-app`이 소유한 오류로 의미를 바꾼다. 예상하지 못한 기술 실패는
API의 공통 500 응답으로 처리한다.

## 선택한 트레이드오프

각 ErrorType은 E-CODE, 공개 메시지와 함께 `ErrorStatus`를 가진다. 따라서 도메인 오류 정의가
외부 오류 상태를 일부 알고 있다. 대신 Spring의 `HttpStatus`, `ResponseEntity`, 응답 DTO와 JSON
형식은 알지 않는다.

이 선택으로 다음을 얻는다.

- 오류를 추가할 때 API 매퍼를 수정하지 않는다.
- `support:error`와 도메인·애플리케이션 모듈에서 `spring-web`을 제거한다.
- E-CODE, 상태, 메시지의 누락을 ErrorType 생성자가 컴파일 시점에 막는다.
- API는 구체적인 `DomainErrorType`이나 `ApplicationErrorType`을 참조하지 않는다.

도메인이 HTTP 의미를 전혀 몰라야 한다는 요구가 생기면 오류별 API 매핑을 도입해야 한다. 두
방식을 동시에 만족시킬 수는 없으며, 현재 결정은 매핑 없는 일회성 오류 정의를 우선한다.

## `support:error`의 최종 계약

`support:error`에는 실제 `ORDER_NOT_PENDING`, `SEAT_ALREADY_HOLD` 같은 오류값을 두지 않는다.

```java
public interface ErrorCode {

    String getCode();

    String getDescription();
}
```

`description`은 백엔드 개발자가 E-CODE의 의미를 찾기 위한 짧은 내부 설명이다. 클라이언트에
전달하는 문구는 ErrorType의 `message`이며, 클라이언트는 메시지가 아니라 `code`로 분기한다.

```java
public interface ErrorDefinition {

    ErrorStatus getStatus();

    ErrorCode getErrorCode();

    String getMessage();
}
```

```java
public enum ErrorStatus {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    UNPROCESSABLE_ENTITY(422),
    INTERNAL_SERVER_ERROR(500),
    SERVICE_UNAVAILABLE(503);

    private final int value;

    ErrorStatus(final int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
```

`ErrorStatus`는 순수 Java 타입이다. `support:error/build.gradle`에서 `spring-web` 의존성을
제거한다.

```java
public class CoreException extends RuntimeException {

    private final ErrorDefinition errorType;
    private final Object data;

    public CoreException(final ErrorDefinition errorType) {
        this(errorType, null);
    }

    public CoreException(final ErrorDefinition errorType, final Object data) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = data;
    }

    public ErrorDefinition getErrorType() {
        return errorType;
    }

    public Object getData() {
        return data;
    }
}
```

기존 호환을 위해 `data`는 1차 마이그레이션에서 유지한다. 다만 새 코드에서 도메인 엔티티나
임의 객체를 넣지 않는다. 검증 상세가 필요하면 API가 소유한 필드 오류 DTO처럼 외부 공개가
안전한 값만 사용한다. 후속 단계에서 `Object data`를 타입이 명확한 오류 상세 모델로 교체할 수
있다.

`AuthException`과 `NotFoundException`은 상태 코드를 기준으로 예외 타입을 나누던 구조다.
상태가 `ErrorDefinition`에 들어가므로 최종적으로 `CoreException`으로 통합한다. 점진적
마이그레이션이 필요하면 두 클래스가 `CoreException`을 상속하게 한 후 호출부를 옮기고 삭제한다.

## 모듈별 ErrorCode와 ErrorType

### `core-domain`

`DomainErrorCode`와 `DomainErrorType`은 도메인 객체와 도메인 정책이 직접 판단하는 실패를
소유한다.

```java
public enum DomainErrorCode implements ErrorCode {
    // Performance
    E3001("지난 회차"),
    E3002("예매 시작 전"),

    // Performance Seat
    E4001("이미 선택된 좌석"),
    E4002("좌석 선택 해제 권한 없음"),

    // Order
    E5002("결제 대기 주문만 처리 가능"),
    E5005("홀드가 만료된 주문"),

    // Hold
    E6000("이미 선점된 좌석"),
    E6001("선점 가능 수량 초과");

    private final String description;

    // ErrorCode 구현
}
```

```java
public enum DomainErrorType implements ErrorDefinition {
    PERFORMANCE_IS_PAST(
            ErrorStatus.BAD_REQUEST,
            DomainErrorCode.E3001,
            "과거 공연은 예매할 수 없습니다."
    ),
    NOT_YET_RESERVE_TIME(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E3002,
            "아직 예매가 시작되지 않았습니다."
    ),
    ORDER_NOT_PENDING(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E5002,
            "결제 대기 주문만 처리할 수 있습니다."
    ),
    SEAT_ALREADY_HOLD(
            ErrorStatus.CONFLICT,
            DomainErrorCode.E6000,
            "좌석이 이미 선점되었습니다."
    );

    private final ErrorStatus status;
    private final DomainErrorCode errorCode;
    private final String message;

    // ErrorDefinition 구현
}
```

사용처는 오류 정의만 선택한다.

```java
throw new CoreException(DomainErrorType.ORDER_NOT_PENDING);
```

### `core-app`

`ApplicationErrorCode`와 `ApplicationErrorType`은 유스케이스 실행 과정에서 판단하는 실패를
소유한다.

- 조회 결과 없음
- 유스케이스 입력 조합 오류
- 멱등성 충돌
- 낙관적 락 충돌을 안정적인 외부 실패로 번역한 경우
- 외부 시스템 실패를 유스케이스 의미로 번역한 경우

```java
public enum ApplicationErrorType implements ErrorDefinition {
    ORDER_NOT_FOUND(
            ErrorStatus.NOT_FOUND,
            ApplicationErrorCode.ORDER_NOT_FOUND,
            "주문을 찾을 수 없습니다."
    ),
    SHOW_INVALID_CURSOR(
            ErrorStatus.BAD_REQUEST,
            ApplicationErrorCode.SHOW_INVALID_CURSOR,
            "커서 형식이 올바르지 않습니다."
    ),
    HOLD_BUSY(
            ErrorStatus.CONFLICT,
            ApplicationErrorCode.HOLD_BUSY,
            "좌석 선점 처리 중입니다. 잠시 후 다시 시도해주세요."
    );
}
```

분산 락을 획득하지 못했다는 사실은 도메인 규칙이 아니라 유스케이스 실행 충돌이므로 현재
`HOLD_BUSY`는 `ApplicationErrorType` 후보로 분류한다. 구체적인 이동은 분산 락 애노테이션과
AOP의 계층 이동 작업과 함께 수행한다.

### `core-api`

`ApiErrorCode`와 `ApiErrorType`은 기능 오류를 다시 매핑하기 위한 타입이 아니다. Spring MVC와
Spring Security가 `ErrorDefinition` 없이 발생시킨 실패를 프로젝트의 오류 응답으로 표현하기
위한 작은 고정 카탈로그다.

```java
public enum ApiErrorType implements ErrorDefinition {
    INVALID_REQUEST(
            ErrorStatus.BAD_REQUEST,
            ApiErrorCode.E400,
            "요청이 올바르지 않습니다."
    ),
    AUTHENTICATION_REQUIRED(
            ErrorStatus.UNAUTHORIZED,
            ApiErrorCode.E1000,
            "로그인이 필요합니다."
    ),
    ACCESS_DENIED(
            ErrorStatus.FORBIDDEN,
            ApiErrorCode.E1001,
            "권한이 없습니다."
    ),
    API_NOT_FOUND(
            ErrorStatus.NOT_FOUND,
            ApiErrorCode.E404,
            "요청한 API를 찾을 수 없습니다."
    ),
    INTERNAL_SERVER_ERROR(
            ErrorStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCode.E500,
            "일시적인 오류가 발생했습니다."
    );
}
```

현재 `NOT_FOUND_DATA`는 데이터 조회 실패와 API 경로 실패를 동시에 표현한다. 마이그레이션 시
`NoHandlerFoundException`은 `ApiErrorType.API_NOT_FOUND`로 바꾸고, 실제 데이터 조회 실패는
`DomainErrorType` 또는 `ApplicationErrorType`의 구체 오류로 옮긴다.

## API 처리 규칙

`ApiResponse.error`와 오류 응답 DTO는 구체 enum인 기존 `ErrorType`이 아니라
`ErrorDefinition`을 받는다.

```java
public static <S> ApiResponse<S> error(
        final ErrorDefinition errorType,
        final Object data
) {
    return new ApiResponse<>(
            ResultType.ERROR,
            null,
            new ApiErrorResponse(
                    errorType.getErrorCode().getCode(),
                    errorType.getMessage(),
                    data
            )
    );
}
```

`ErrorMessage`는 HTTP 응답 DTO이므로 최종적으로 `support:error`가 아니라 `core-api`가 소유한다.

`GlobalExceptionHandler`는 `CoreException`을 한 번만 처리한다.

```java
@ExceptionHandler(CoreException.class)
public ResponseEntity<ApiResponse<Object>> handleCoreException(
        final CoreException exception
) {
    final ErrorDefinition error = exception.getErrorType();
    return ResponseEntity
            .status(error.getStatus().value())
            .body(ApiResponse.error(error, exception.getData()));
}
```

기능 오류를 추가할 때 이 Handler는 수정하지 않는다.

Spring 예외에는 프로젝트의 `ErrorDefinition`이 없으므로 다음 고정 변환만 API에 둔다.

```text
MethodArgumentNotValidException  -> ApiErrorType.INVALID_REQUEST
HttpMessageNotReadableException  -> ApiErrorType.INVALID_REQUEST
NoHandlerFoundException          -> ApiErrorType.API_NOT_FOUND
인증 EntryPoint                  -> ApiErrorType.AUTHENTICATION_REQUIRED
인가 AccessDeniedHandler         -> ApiErrorType.ACCESS_DENIED
그 밖의 예상하지 못한 Exception -> ApiErrorType.INTERNAL_SERVER_ERROR
```

이 변환은 기능 오류별 매핑이 아니라 프레임워크 경계에서 한 번 설정하는 고정 변환이다.

`GlobalExceptionHandler`는 현재 계층 검사 사각지대인 `com.ticket.core.support`에서
`com.ticket.core.api.error`로 옮긴다. API 응답 DTO도 같은 API 소유 패키지에 둔다.

## `AuthenticatedMember` 중복 검사 제거

`ShowLikeController.requireMemberId()`는 제거한다.

현재 `AuthenticatedMemberArgumentResolver`는 파라미터 타입이 `AuthenticatedMember`이면 항상
SecurityContext를 검사한다. 인증 객체가 없거나 인증되지 않았거나 principal이
`AuthenticatedMember`가 아니면 예외를 던지고, 성공한 경우에만 `AuthenticatedMember`를
반환한다.

`AuthenticatedMember` record 생성자도 다음 불변식을 보장한다.

```text
memberId != null
memberId > 0
role != null
role is not blank
```

따라서 컨트롤러 메서드에 주입된 `AuthenticatedMember`에 대해 다시 `member == null` 또는
`member.memberId() == null`을 검사하는 것은 동일 계약의 중복 검증이다. 다른 컨트롤러들은 이미
다음처럼 직접 사용한다.

```java
member.memberId()
```

`ShowLikeController`도 다음 형태로 맞춘다.

```java
@PostMapping("/shows/{showId}")
public ApiResponse<AddShowLikeUseCase.Output> likeShow(
        final AuthenticatedMember member,
        @PathVariable final Long showId
) {
    final AddShowLikeUseCase.Input input =
            new AddShowLikeUseCase.Input(member.memberId(), showId);
    return ApiResponse.success(addShowLikeUseCase.execute(input));
}
```

같은 변경을 `unlikeShow`, `getLikeStatus`에 적용하고 다음을 삭제한다.

```text
ShowLikeController.requireMemberId
ShowLikeController의 AuthException import
ShowLikeController의 기존 ErrorType import
```

현재 검색 결과 MVC 컨트롤러에서 `AuthenticatedMember`를 수동 null 검사하는 곳은
`ShowLikeController.requireMemberId()`뿐이다.

다음 검사는 제거 대상이 아니다.

- `AuthenticatedMemberArgumentResolver`: MVC 파라미터 주입 경계 자체다.
- `AuditorAwareImpl`: MVC ArgumentResolver를 거치지 않고 SecurityContext를 직접 읽는다.
- `AccessTokenAuthenticationFilter`: 인증 객체를 만드는 필터 경계다.
- `WebSocketAuthInterceptor`: MVC와 다른 WebSocket 인증 경계다.

`AuthenticatedMemberArgumentResolver`의 현재 `ResponseStatusException`은 공통 오류 응답 형식을
우회할 수 있다. Resolver는 인증 실패 시 `new CoreException(ApiErrorType.AUTHENTICATION_REQUIRED)`을
던지거나, `ResponseStatusException` 전용 Handler가 동일한 `ApiErrorType`으로 변환하도록 한다.
전자의 흐름이 기존 공통 예외 처리와 더 일관적이다.

## 오류를 추가하는 개발 절차

도메인 오류를 추가하는 경우는 다음 세 단계다.

1. `DomainErrorCode`에 E-CODE와 내부 설명을 추가한다.
2. `DomainErrorType`에 상태, E-CODE, 공개 메시지를 추가한다.
3. 필요한 위치에서 `new CoreException(DomainErrorType.XXX)`를 던진다.

애플리케이션 오류와 API 오류도 각각 같은 모듈의 두 enum을 사용한다. 다음 작업은 하지 않는다.

```text
GlobalExceptionHandler 수정
오류별 HttpStatus 매퍼 추가
오류별 메시지 매퍼 추가
support:error에 업무 오류 추가
```

E-CODE를 별도 enum으로 관리하기로 했으므로 한 오류를 추가할 때 ErrorCode와 ErrorType 두 곳은
수정한다. 이 두 선언은 동일 소유 모듈 안에 있고, ErrorType이 ErrorCode를 직접 참조해 컴파일
시점에 연결된다. 피하려는 중복은 API 경계의 두 번째 오류 계약 작성이다.

## 마이그레이션 순서

1. `support:error`에 `ErrorCode`, `ErrorDefinition`, `ErrorStatus` 계약을 만든다.
2. `CoreException`이 구체 `ErrorType` 대신 `ErrorDefinition`을 보관하게 변경한다.
3. `core-domain`에 `DomainErrorCode`, `DomainErrorType`을 만들고 도메인 발생 오류를 옮긴다.
4. `core-app`에 `ApplicationErrorCode`, `ApplicationErrorType`을 만들고 유스케이스 오류를 옮긴다.
5. `core-api`에 `ApiErrorCode`, `ApiErrorType`을 만들고 MVC·Security 오류를 옮긴다.
6. `ApiResponse.error`와 오류 응답 DTO가 `ErrorDefinition`을 받도록 변경한다.
7. `ApiControllerAdvice`를 `com.ticket.core.api.error.GlobalExceptionHandler`로 이동하고
   `CoreException` 단일 Handler로 통합한다.
8. Security EntryPoint와 AccessDeniedHandler가 `ApiErrorType`을 사용하게 변경한다.
9. `AuthenticatedMemberArgumentResolver`의 인증 실패를 공통 오류 응답 흐름으로 통합한다.
10. `ShowLikeController.requireMemberId()`와 관련 import를 삭제한다.
11. 기존 `AuthException`, `NotFoundException`, 전역 `ErrorType`, 전역 `ErrorCode`,
    `support:error`의 응답 DTO를 제거한다.
12. `support:error/build.gradle`에서 `spring-web`을 제거한다.

마이그레이션 중 공개된 E-CODE는 별도 승인 없이 변경하거나 재사용하지 않는다. 기존 E-CODE가
너무 포괄적이어서 분리가 필요하면 클라이언트 호환성 결정을 먼저 기록한다.

## 검증 조건

구현 완료 조건은 다음과 같다.

- `support:error`의 production dependency에 `spring-web`이 없다.
- `support:error`에 회원, 공연, 주문, 홀드 같은 실제 업무 오류 상수가 없다.
- `core-domain`과 `core-app`이 `org.springframework.http..`, `org.springframework.web..`을
  참조하지 않는다.
- 모든 `CoreException`이 `ErrorDefinition` 하나로 API에서 처리된다.
- 도메인 또는 애플리케이션 오류 추가 시 API 코드를 수정하지 않는다.
- `DomainErrorCode`, `ApplicationErrorCode`, `ApiErrorCode` 전체에서 외부 코드 문자열이
  중복되지 않는다. 의도적인 공용 코드가 필요하면 소유자를 하나로 정한다.
- 모든 ErrorType은 status, errorCode, message가 null 또는 blank가 아니다.
- 4xx `CoreException`은 정의된 상태·E-CODE·메시지로 응답한다.
- 예상하지 못한 예외는 상세 메시지를 노출하지 않고 API 공통 500으로 응답한다.
- 잘못된 JSON, Bean Validation 실패, 미인증, 인가 실패가 각각 정의된 `ApiErrorType`으로
  응답한다.
- `ShowLikeController`에 `requireMemberId()`와 수동 인증 검사가 없다.
- `AuthenticatedMemberArgumentResolver`의 성공 경로는 유효한 member만 반환하고 실패 경로는
  통일된 401 응답을 만든다.
- 기존 계층 ArchUnit 테스트와 모듈 테스트가 통과한다.

오류 코드 계약 테스트는 최소한 다음을 자동 검증한다.

```text
E-CODE 유일성
E-CODE/description/message 비어 있지 않음
status 비어 있지 않음
동일 모듈 ErrorType이 해당 모듈 ErrorCode만 참조함
```

## 구현에서 확정한 사항

설계 당시 열려 있던 지점을 마이그레이션에서 다음과 같이 정했다.

- **공용 E-CODE는 support:error가 소유한다.** `E400`, `E404`, `E500`, `E1000`, `E1001`은 도메인,
  애플리케이션, API 세 곳 모두에서 발생한다. 모듈마다 새 코드를 발급하면 이미 공개된 코드가
  바뀌므로 `CommonErrorCode` 하나를 두고 세 카탈로그가 함께 참조한다. 업무 오류 코드는 여전히
  각 모듈이 소유한다. 계약 테스트는 "자기 모듈 코드 또는 공용 코드"만 허용한다.
- **`ApiErrorCode`는 만들지 않았다.** 현재 API가 고정 변환하는 다섯 오류가 모두 공용 코드를 쓰므로
  빈 enum이 된다. API만의 코드가 필요해지면 그때 `com.ticket.core.api.error`에 추가한다.
- **`HOLD_BUSY`는 `DomainErrorType`에 둔다.** `@DistributedLock`의 `errorType` 속성은 애노테이션
  속성이라 구체 enum 타입이 필요하고, 애노테이션은 `core-domain`에 있다. 분산 락 애노테이션과
  AOP의 계층 이동 작업에서 `ApplicationErrorType`으로 옮긴다.
- **주문 상태·소유권 오류(`E5002`, `E5003`)는 도메인 어휘로 본다.** 던지는 곳은 `core-app`이지만
  규칙 자체는 주문 도메인이 소유하므로 `DomainErrorType`에 둔다.
- **공개된 E-CODE와 상태는 바꾸지 않았다.** 메시지는 API 공통 500만 "알 수 없는 에러입니다."에서
  "일시적인 오류가 발생했습니다."로 바뀐다. 한 번도 던지지 않던 상수(`E3000`, `E5000`, `E5001`,
  `E5005`, `E6002`, `E6004`, `E2001`)는 옮기지 않고 지웠다.
- **계약 테스트는 `core-api`에 둔다.** 세 카탈로그를 모두 클래스패스에 두는 모듈이 core-api뿐이다.
  `ErrorCatalogContractTest`가 값 누락과 코드 중복, 코드 소유를 검사하고, `CoreLayerArchitectureTest`가
  spring-web 참조 금지와 API의 구체 카탈로그 참조 금지를 검사한다.

## 결과

최종 오류 전파 흐름은 다음과 같다.

```text
core-domain
  DomainErrorType ─┐
                   │
core-app           ├─> CoreException(ErrorDefinition)
  ApplicationErrorType ┘              │
                                      ▼
core-api                    GlobalExceptionHandler
  ApiErrorType ────────────────────────┤
                                      ▼
                         HTTP status + E-CODE + message
```

기능 오류는 발생 원인을 판단하는 모듈이 소유하고, 호출부에서 완성된 ErrorType을 선택한다.
API는 구체 오류를 다시 해석하지 않고 공통 계약을 그대로 직렬화한다.
