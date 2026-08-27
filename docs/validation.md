# 검증 책임 기준

이 문서는 **어떤 검증을 어느 계층이 소유하는지**의 단일 기준이다. 모듈 경계는
[architecture.md](architecture.md), 오류 계약은 [ADR 0002](adr/0002-module-owned-error-contracts.md),
테스트 배치는 [testing.md](testing.md)를 함께 본다.

판단 기준 한 문장 — **"이 검증이 사라지면 무엇이 먼저 깨지는가."** HTTP 응답 품질만 나빠지면 API,
다른 adapter에서 호출해도 흐름이 깨지면 app, 어떤 호출 경로에서도 업무가 틀리면 domain,
기술 경계에서만 성립하면 infra다.

## 책임표

| 계층 | 소유하는 검증 | 실패 표현 |
| --- | --- | --- |
| `core-api` | JSON·HTTP 요청 형식, 필수 body field, blank/null, ID 양수 여부, path/query/header 형식, page size의 구조적 범위 | Bean Validation → `ApiErrorType.INVALID_REQUEST` (400 / `E400`) |
| `core-app` | HTTP가 아닌 adapter에서도 지켜야 하는 `UseCase.Input` 계약, 여러 입력의 조합, 날짜 from/to 범위, page size·cursor의 유스케이스 조건, 데이터 존재 여부, 요청 권한, 중복·멱등성·선행 작업, 여러 domain·port를 엮는 실행 선행조건 | `ApplicationErrorType` (`INVALID_INPUT`, `DATA_NOT_FOUND`, `ACCESS_DENIED` 등) |
| `core-domain` | 어떤 호출 경로에서도 깨지면 안 되는 업무 불변식, 값 객체 유효성, 상태 전이, 예매 가능 시간, 좌석 소유권과 선점 한도, 주문 상태 규칙, 공개 가능 여부 | `DomainErrorType` |
| `core-infra` | Redis·JWT·외부 API payload decode, 외부 응답 유효성, DB constraint와 기술 예외 번역, 설정값과 기술 형식 | 기술 예외를 app/domain이 이해할 실패로 번역 |

## core-api 규칙

**Bean Validation은 core-api만 쓴다.** `core-app`과 `core-domain`의 `build.gradle`에
`jakarta.validation`을 추가하지 않는다.

**파라미터 제약은 `controller.docs` 인터페이스에만 선언한다.** Controller는 binding 애노테이션
(`@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestHeader`)만 갖는다.

```java
// controller/docs/ShowLikeControllerDocs.java — 제약을 선언하는 유일한 곳
ApiResponse<AddShowLikeUseCase.Output> likeShow(
        @Parameter(hidden = true) AuthenticatedMember member,
        @Parameter(description = "공연 ID", example = "1", required = true) @Positive Long showId
);

// controller/ShowLikeController.java — binding만
@Override
@PostMapping("/shows/{showId}")
public ApiResponse<AddShowLikeUseCase.Output> likeShow(
        final AuthenticatedMember member,
        @PathVariable final Long showId
) { ... }
```

이유는 취향이 아니다. Jakarta Bean Validation은 상위 타입 메서드의 파라미터 제약을 구현체가
다시 선언하면 `ConstraintDeclarationException`(HV000151)을 던지고, 그 순간 해당 Controller의
method validation 전체가 500으로 무너진다. Controller가 문서 인터페이스를 구현하므로 선언 위치는
한 곳뿐이다. `@Valid` cascade 표시도 같은 규칙을 받는다.

`ControllerParameterConstraintTest`가 이 규칙을 고정한다.

**`@Validated`를 Controller에 붙이지 않는다.** 붙이면 AOP 프록시 경로가 켜져 같은 상속 규칙
위반을 되살린다. 문서 인터페이스에 제약이 있으면 Spring MVC의 기본 method validation이 적용한다.

실패 변환은 `GlobalExceptionHandler`의 고정 변환 두 개가 담당한다.

| 예외 | 응답 |
| --- | --- |
| `MethodArgumentNotValidException` (요청 body) | 400 `E400`, `error.data`에 `field: message` |
| `HandlerMethodValidationException` (path·query·header) | 400 `E400`, `error.data`에 `parameter: message` |

## core-app 규칙

**필수 component는 record compact constructor 한곳에서 판정한다.** 같은 검증을 생성자와
`execute()`에서 반복하지 않는다. 공통 판정은
`com.ticket.core.app.support.validation.RequiredInput`을 쓴다.

```java
public record Input(String orderKey, Long memberId) {
    public Input {
        RequiredInput.notBlank(orderKey, "orderKey");
        RequiredInput.positiveId(memberId, "memberId");
    }
}
```

- 단순 입력 실패는 기존 계약대로 `ApplicationErrorType.INVALID_INPUT`이다. 새 공개 오류 코드나
  HTTP status를 만들지 않는다.
- **`execute(null)`은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다.** `INVALID_INPUT`으로
  감싸지 않고 `NullPointerException`으로 드러낸다.
- domain 값 객체를 즉시 만들어 검증하는 값은 app에서 같은 규칙을 다시 구현하지 않는다.
  예: `CreateOrderUseCase.Input`은 `seatIds`를 판정하지 않고 `RequestedSeatIds`에 맡긴다.
- optional 검색 조건을 required로 바꾸지 않는다. 예: `GetGenresByCategoryUseCase`의
  `categoryCode`는 없으면 전체 조회다.
- 서버가 만든 설정·컨텍스트 값은 사용자 입력이 아니다. `GetSocialLoginUrlsUseCase.Input.baseUrl`은
  설정 오류로 `IllegalStateException`을 던진다.
- page size 상한은 제품 결정이 있는 유스케이스만 갖고, 그 상한은 해당 유스케이스가 소유한다
  (`GetMyShowLikesUseCase.MAX_SIZE`, `GetShowsUseCase.MAX_SIZE` = 100).

## 중복을 허용하는 기준과 금지하는 기준

**허용**: 두 계층의 목적이 다를 때. 형태가 같아도 지우지 않는다.

| 위치 | 검사 | 목적 |
| --- | --- | --- |
| `CreateOrderRequest.seatIds`의 `@NotEmpty` | 빈 배열 금지 | 친절한 HTTP 400 응답 |
| `RequestedSeatIds.validateEmpty` | 빈 목록 금지 | 어떤 경로에서도 깨지면 안 되는 업무 불변식 |

API 제약이 없어도 도메인이 막고, 도메인만 있으면 500이 아니라 400을 주기 어렵다. 둘 다 남긴다.

**금지**: 같은 목적의 같은 규칙을 같은 계층 안에서 두 번 실행하는 것.

```java
// 금지 — Input 생성자가 이미 판정한 것을 execute가 다시 본다
public Output execute(final Input input) {
    validateInput(input);              // showId null 검사
    ...
}
```

**금지**: 업무 정책 값을 API나 app으로 복사하는 것. 최대 선점 좌석 수는
`PerformanceBookingPolicyView.maxCanHoldCount`와 `BookingPolicyValidator`가 소유한다.
API 요청 DTO나 `UseCase.Input`에 같은 상한을 두지 않는다.

**금지**: 같은 값 변환 규칙을 여러 곳에 두는 것. `region` 문자열 → `Region` 변환은
`ShowParam.parseRegion` 한곳이 소유하고 `ShowSearchCriteria`와
`SaleOpeningSoonSearchParam`이 그것을 쓴다.

## 테스트로 고정하는 곳

| 검증 | 테스트 위치 |
| --- | --- |
| 요청 DTO Bean Validation | `core-api/src/test`의 `controller/request/*Test` |
| Controller invalid request 계약(400과 `E400`) | `core-api/src/test`의 `*ContractTest` |
| 파라미터 제약 선언 위치 | `core-api/src/test`의 `ControllerParameterConstraintTest` |
| `UseCase.Input` 계약 | `core-app/src/test` — API를 거치지 않고 Input을 직접 생성한다 |
| 업무 불변식과 값 객체 | `core-domain/src/test` |

## 남은 제품 정책 결정

아래는 코드에 흔적이 있지만 제품 결정이 없어 **이번에 손대지 않은** 것이다. 결정 없이 되살리거나
바꾸지 않는다.

- `RawPassword`의 주석 처리된 복잡도 정책(길이·영문·숫자·특수문자). 되살리면 기존 회원의 로그인과
  가입 흐름에 영향이 있다.
- `Email`의 `null` → 빈 문자열 변환. OAuth2 흐름과 기존 데이터를 조사하기 전에는 바꾸지 않는다.
- `size`의 최대값. `GetShowsUseCase`(API 문서에 최대 100)와 `GetMyShowLikesUseCase`(`MAX_SIZE` 100)
  외에는 근거가 없어 양수 조건만 적용했다. 검색과 판매 오픈 예정 목록의 상한은 결정이 필요하다.
- `GetLatestShowsUseCase`와 `GetSaleStartApproachingShowsUseCase`의 `category`. Swagger는
  `required = true`지만 조회 구현은 필터로 다룬다. 필수 여부 결정이 필요하다.
- path·query 값의 **타입** 불일치(`/api/v1/shows/abc`)는 현재 500이다. Bean Validation 이전 단계인
  binding 실패라 이번 범위 밖이며, 400으로 바꾸려면 공개 status 변경 결정이 필요하다.
