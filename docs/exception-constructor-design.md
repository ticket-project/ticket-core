# 예외 생성자에 실패 상황의 인수를 명시한다

상태: **구현 완료, 2026-09-10**. A~D 그룹과 외부 계약 표가 코드에 반영됐다 —
아래 생성자와 호출 예시는 현재 코드와 같다. 검증은 `./gradlew test` 전체 실행이 213개 클래스
715개 테스트 전부 통과(실패·오류 0, 스킵 0, Docker 기동 상태)로 끝났다. 현재 동작의 근거는
코드와 [ADR 0010](adr/0010-exceptions-do-not-own-http-status.md)이다.
[TD-17](technical-debt.md)의 카탈로그 구조 결정은 이 작업으로 해소되지 않고 보류 상태 그대로다.

이번 작업은 예외 객체를 만드는 인터페이스를 정리한다. 호출부가 이미 알고 있는 ID·상태·수량을
생성자에 전달하고, 예외는 그 사실을 타입이 있는 필드에 보관한다. 공개 응답의 코드·메시지·데이터는
현재 계약대로 유지한다. 구현 에이전트에게는 [작업 프롬프트](exception-constructor-agent-prompt.md)를 전달한다.

## 범위

- 구체 예외의 생성자, 실패 상황을 담는 필드와 getter, 해당 생성자를 사용하는 main/test 호출부를 수정한다.
- 문자열 상세 정보만 받는 예외는 공개 `Object data` 생성자를 `String detail` 생성자로 좁힌다.
- 기존 예외 클래스, 상속 계층, 오류 코드, HTTP 매핑, 예외 발생 조건과 발생 위치를 유지한다.
- `ErrorType` 재도입은 [TD-17](technical-debt.md)의 미결정 사항이다. 이 작업에서 카탈로그를 만들거나 예외 클래스를 통합하지 않는다.
- `IllegalArgumentException`/`IllegalStateException` 선택 기준 정리, `Order` 검증 책임 이동,
  Kakao 설정·장애 재분류, cause/로그 정책 변경은 후속 작업이다. 이번 구현에 섞지 않는다.
- 앞서 검토한 `withDetail` 같은 정적 생성 메서드 도입은 이번 범위에서 제외한다. 생성자 호출을 유지한다.

## 인수를 고르는 기준

1. 예외를 던지는 시점에 이미 존재하는 값만 받는다. private helper에 기존 인수를 전달하는 것은
   가능하지만 진단 정보를 채우기 위한 DB/Redis 조회나 외부 API 호출은 추가하지 않는다.
2. 엔티티나 snapshot 전체 대신 실패를 식별하는 scalar ID, enum, 수량을 받는다. 다른 module의
   내부 타입을 가져오지 않는다.
3. 필수 인수를 가진 예외는 그 생성자만 공개한다. 이전 무인수 생성자나 `Object` 생성자를 남겨
   `null`·가짜 ID로 새 계약을 우회하는 호환 경로를 만들지 않는다. 테스트도 실제 상황의 값을 전달한다.
4. 인수 전달을 필수로 만드는 것과 값 검증은 구분한다. 예외 생성자는 기존 업무 검증을 다시 수행하지
   않는다. ID는 호출부와 같은 `Long`으로 받아 진단 값의 unboxing 때문에 원래 업무 예외가 NPE로
   바뀌는 것을 피한다. nullable 상세 정보의 기존 동작도 유지한다.
5. 새 사실은 `private final` 필드와 getter로 보관한다. 이 getter는 진단·테스트에서 사용할 수 있는
   인터페이스다. 이번 단계에서 로그 형식이나 로그량을 바꾸지 않는다.

공통으로 확보되는 정보가 회차뿐인 예외는 회차 ID만 받는다. 예를 들어 `NoAvailableSeatException`은
단일 좌석 검증과 여러 좌석 검증이 함께 쓰므로 요청 좌석 전체를 "실패한 좌석 목록"이라고 저장하지
않는다. `SeatNotOwnedException`은 요청한 회원 ID만 받고 실제 점유 회원을 새로 조회하지 않는다.

## 목표 생성자

### A. 실패 상황을 받는 booking 예외

아래 표의 인수 이름을 필드 이름으로 사용한다. 이 그룹의 현재 main 호출은 모두 무인수이며
`error.data`는 `null`이다. **새 필드는 진단 정보이고 `super(..., data)`에는 계속 `null`을 전달한다.**

| 예외 | 공개 생성자 인수 | 값의 출처 / 이유 |
| --- | --- | --- |
| `PerformanceIsPastException` | `Long performanceId` | `PerformanceSalesPolicy`, `SeatSelectionCoordinator`가 가진 회차. 서로 다른 시각 판정 로직은 그대로 둔다 |
| `NotYetReserveTimeException` | `Long performanceId` | `PerformanceSalesPolicy`가 가진 회차 |
| `NoAvailableSeatException` | `Long performanceId` | `HoldSeatAvailabilityValidator`, `SeatSelectionAvailabilityValidator`의 검증 대상 회차 |
| `SeatMismatchInPerformanceException` | `Long performanceId` | 위 두 validator의 검증 대상 회차. 없는 좌석을 찾기 위한 추가 조회 없음 |
| `SeatAlreadySelectedException` | `Long performanceId, Long seatId` | `SeatSelectionService.select`가 이미 가진 경합 대상 |
| `SeatNotOwnedException` | `Long performanceId, Long seatId, Long memberId` | `SeatSelectionService`의 두 실패 경로. `memberId`는 해제를 요청한 회원 |
| `SeatVenueMismatchException` | `Long performanceId, Long seatId` | `EditPerformanceSeatsUseCase.toPerformanceSeat`의 편성 대상 |
| `PerformanceGradeMismatchException` | `Long performanceId, Long performanceGradeId` | 같은 메서드의 등급 배정 값. nullable 배정 값 때문에 예외 생성에서 unboxing하지 않는다 |
| `PerformanceSeatAlreadyEditionedException` | `Long performanceId` | `EditPerformanceSeatsUseCase.ensureNotAlreadyEditioned`의 회차 |
| `OrderNotPendingException` | `OrderState currentStatus` | `CancelOrderTransactionService`가 조회한 주문 상태 |
| `OrderNotOwnedException` | `String orderKey, Long memberId` | 취소·상세·상태 조회의 요청 값. 실제 주문 소유자 정보는 요구하지 않는다 |
| `PendingOrderAlreadyExistsException` | `Long memberId, Long performanceId` | `PendingOrderLocalValidator`가 기존 exists 조회에 사용하는 값 |
| `SeatAlreadyHoldException` | `Long performanceId, Long seatId` | `HoldManager`, `SeatSelectionCoordinator`, `SeatSelectionAvailabilityValidator`의 대상 |
| `ExceedHoldLimitException` | `long requestedSeatCount, int maxSeatCount` | `PerformanceSalesPolicy.ensureWithinHoldLimit`의 수량과 정책 한도 |

`GetOrderStatusUseCase.requireActiveMember`는 현재 `memberId`만 받는다. 요청의 `orderKey`를 함께
전달해 그 안의 `NotFoundException` → `OrderNotOwnedException` 변환에도 같은 사실을 넣는다.
catch 대상과 권한 판단 의미는 유지한다.

`maxSeatCount == null`은 무제한이다. 기존 `holdPolicy.exceeds(requestedSeatCount)`가 true일 때만
예외를 만들므로 이 지점의 한도는 non-null이다. 생성자는 이를 `int`로 받고, 한도 초과 여부는
계속 정책이 판단한다. 수량은 현재 입력과 같은 `long`을 유지해 축소 변환하지 않는다.

예를 들어 `OrderNotPendingException`의 목표 구현은 다음과 같다(import 생략).

```java
@Getter
public class OrderNotPendingException extends BookingException {

    private static final String MESSAGE = "결제 대기 주문만 처리할 수 있습니다.";

    private final OrderState currentStatus;

    public OrderNotPendingException(final OrderState currentStatus) {
        super(BookingErrorCode.E5002, MESSAGE, null);
        this.currentStatus = currentStatus;
    }
}
```

호출 위치는 현재 취소 서비스 안의 조건문 그대로다.

```java
if (order.getStatus() != OrderState.PENDING) {
    throw new OrderNotPendingException(order.getStatus());
}
```

`Order.validatePendingTransition`의 `IllegalStateException`과 서비스의 사전 검증은 이 단계에서
유지한다. 이번 문서가 이전에 논의한 검증 책임 이동까지 승인하는 것으로 해석하지 않는다.

### B. 원래 값을 받아 공개 상세 문구를 만드는 show 예외

`UnsupportedShowSortException(String sortValue)`는 정렬 원문을 받아 `sortValue` 필드에 보관한다.
호출부 `ShowSort.from`은 문장을 만들지 않고 원래 `apiValue`를 전달한다. 예외가 기존 문구를 구성한다.

```java
@Getter
public class UnsupportedShowSortException extends ShowException {

    private static final String MESSAGE = "지원하지 않는 정렬 조건입니다.";

    private final String sortValue;

    public UnsupportedShowSortException(final String sortValue) {
        super(ShowErrorCode.E7002, MESSAGE, "지원하지 않는 sort: " + sortValue);
        this.sortValue = sortValue;
    }
}
```

현재 실제 응답의 `error.data`는 `"지원하지 않는 sort: " + apiValue`다. 문자열의 대소문자·공백을
정규화하지 않는다. `ShowSort.from`의 null/blank → 기본 정렬 처리도 유지한다.

현재 `ShowExceptionHandlerTest`는 생성자에 `"UNKNOWN_SORT"`를 직접 넣고 같은 값을 기대한다.
이것은 생성자의 기존 범용 data 동작을 검사한 사례다. 새 계약은 원문을 받으므로 이 테스트를 실제
`ShowSort.from("UNKNOWN_SORT")` 경로와 연결해 **접두어가 정확히 한 번 붙는 기존 API 응답**을
고정한다. handler 테스트의 기존 문자열만 보고 실제 응답을 원문 하나로 축소하면 안 된다.

### C. 공개 상세 설명만 받는 예외

아래 타입은 현재 호출자가 자유 형식의 공개 상세 문구를 제공한다. 요청마다 별도의 필드 enum이나
전용 예외를 신설하는 대신 인수를 `String detail`로 좁힌다. 무인수 생성자는 상세 설명이 없는
현재 사용처를 위해 유지한다. `detail`은 `error.data`이며 공개 `MESSAGE`를 덮지 않는다.

| 예외 | 목표 공개 생성자 |
| --- | --- |
| `InvalidRequestException` | `()`, `(String detail)` |
| `NotFoundException` | `()`, `(String detail)` |
| `InternalErrorException` | `()`, `(String detail)` |
| `UnauthenticatedException` | `()`, `(String detail)` |
| `AuthorizationException` | `()`, `(String detail)` |
| `DuplicateEmailException` | `()`, `(String detail)` |
| `HoldBusyException` | `()`, `(String detail)` |

`new InvalidRequestException("performanceId는 필수입니다.")`는 계속 같은 모양으로 호출한다.
JavaDoc에 인수가 공개 상세 정보라는 사실을 명시하고, 고정 메시지와 상세 정보의 구분은 응답
계약 테스트로 확인한다. 문자열 생성자가 message처럼 보이는 사용성 한계는 남지만 이번 생성자
작업에서 별도의 factory 체계를 함께 도입하지 않는다.

`HoldBusyException`의 락 경합 상세 문구, 인증 entry point의 기본·expired·invalid 상세 문구는
현재 값 그대로다. 상세 문구가 고정 메시지와 같아 보이더라도 `error.data`를 없애지 않는다.
필수값·양수 검증 문구도 현재 리터럴을 유지한다.

### D. 이미 필요한 인수를 받는 예외와 기반 클래스

- `LikeAlreadyExistsException(long memberId, LikeType likeType, long targetId)`는 현재 생성자와
  공개 data 문구를 유지한다. 이번 단계에서 API의 문자열 data를 객체로 바꾸지 않는다.
- `AdmissionTokenException` 계열의 `reason`, `cause`, 무인수 required 예외는 유지한다.
  reason은 진단 전용이며 응답 data에 넣지 않는다.
- `TicketException`과 module별 abstract base의 protected `Object data` 인수는 응답 전달을
  위한 내부 공통 경로로 유지한다. 구체 예외의 공개 생성자를 좁히는 것이 이번 목표다.
- 예외는 계속 HTTP 타입을 모른다. `ErrorCode.description`과 구체 예외의 `MESSAGE` 위치도 유지한다.

## 외부 계약과 실패 동작

| 확인할 것 | 구현 후 조건 |
| --- | --- |
| 기존 HTTP 상태·오류 코드·공개 메시지 | 동일 |
| A 그룹의 공개 `error.data` | 계속 null. 실제 JSON의 null 생략 여부도 기존 직렬화 설정을 따른다 |
| B 그룹의 공개 `error.data` | 실제 호출 기준으로 동일한 접두어와 원문 |
| C·D 그룹의 공개 `error.data` | 기존 문자열/값 그대로 |
| 새로운 getter | 예외 자체를 JSON으로 직렬화하지 않고 기존 `ApiResponse.error(code, message, data)` 경로 사용 |
| 예외 발생 조건·순서·catch 대상 | 동일 |
| SQL/Redis/외부 호출 횟수, 락·트랜잭션·이벤트 | 동일 |

새 값은 예외를 검사할 때 읽을 수 있는 진단 정보다. getter가 생긴다는 이유로 핸들러에서 JSON
필드를 추가하거나 로그를 일괄 늘리는 것은 별도의 변경이다.

## 검증과 완료 조건

실행 범위 선택은 `.agents/skills/verify/SKILL.md`, 테스트 작성 관례는 [testing.md](testing.md)를 따른다.

1. 생성자를 바꾸기 전에 관련 handler/호출부 테스트로 현재 응답을 확인한다. 실제 호출부가
   문자열을 조립하는 B 그룹은 handler를 직접 호출하는 테스트와 구분해 기준을 잡는다.
2. A·B 그룹의 실제 실패 경로 테스트에서 예외 종류와 전달된 사실을 함께 확인한다. 두 위치에서
   같은 예외를 던지는 경우 각 경로를 확인한다. 단순 getter별 테스트는 만들지 않는다.
3. handler 응답 테스트에서 code·message·HTTP 상태와 **`getError().getData()`**를 검사한다.
   기존 `BookingExceptionHandlerTest`의 `getBody().getData()`는 바깥 봉투의 성공 data라서
   진단 정보가 오류 data에 새어 나오는 것을 검사하지 못한다.
4. 수량은 한도 이하·초과·무제한을, 주문은 기존 취소 거절을, 정렬은 잘못된 값과 null/blank
   기본값을 확인한다. 상태 전이·검증 순서를 바꾸는 테스트 변경으로 확대하지 않는다.
5. `ErrorCodeUniquenessTest`, `ExceptionHandlerScopeTest`, `InvalidRequestMessageContractTest`와
   영향받은 module의 handler/호출부 테스트를 실행한다. 기존 검사 대상을 줄여 통과시키지 않는다.
6. 바뀐 생성자를 참조하는 테스트는 fixture 인수를 실제 값으로 갱신하고 테스트 컴파일까지 확인한다.
   module 내부 enum 의존 등이 구조 규칙을 지키는지 `ModularityTests`·`DomainPurityTest`로 확인한다.
   통합 검증 필요 여부는 실제 변경 파일과 verify 기준으로 정한다.
7. 완료 시 A~D 그룹마다 변경·유지 결과를 설명하고 테스트 결과 및 미실행 범위를 보고한다.
   새 공개 `Object` 생성자나 가짜 값으로 이전 호출 형태를 유지하는 경로가 없어야 한다.

**실행 결과(2026-09-10)**: `./gradlew test` 전체 213개 클래스 715개 테스트 통과, 실패·오류 0,
스킵 0. Docker가 떠 있어 Testcontainers 기반 Redis 통합 테스트와 예매 E2E도 함께 돌았다.
`ErrorCodeUniquenessTest`·`ExceptionHandlerScopeTest`·`InvalidRequestMessageContractTest`와
`ModularityTests`·`DomainPurityTest`·`AggregateAssociationTest`가 그 안에 포함된다. 부하 테스트와
실제 배포 환경 확인은 이 작업의 범위가 아니라 실행하지 않았다.

## 검토한 대안

- **호출자가 문자열을 조립해 `Object data`에 전달**: 변경량은 작지만 예외에 필요한 사실이
  생성자에서 드러나지 않고 타입 검사를 활용할 수 없어 선택하지 않았다.
- **모든 예외에 ID·snapshot·원인·행위 enum을 일괄 추가**: 없는 값을 얻기 위해 조회가 늘거나
  호출 관계가 바뀔 수 있다. 현재 모든 발생 경로가 공유하는 최소 사실만 받는다.
- **문제의 실제 값을 공개 메시지에 삽입**: Iced-Latte의 일부 예외처럼 만들 수 있지만 현재
  고정 메시지 계약을 바꾼다. 이번에는 진단 필드와 공개 메시지를 구분한다.
- **생성자 정리와 예외 분류·검증 책임 이동을 한 번에 수행**: 응답 회귀의 원인을 구분하기
  어려워진다. 생성자를 먼저 바꾸고 실패 의미와 흐름은 후속 설계에서 다룬다.

참고한 패턴은 Iced-Latte의 [ShoppingCartNotFoundException](https://github.com/Sunagatov/Iced-Latte/blob/development/src/main/java/com/zufar/icedlatte/cart/exception/ShoppingCartNotFoundException.java)과
[InvalidItemProductQuantityException](https://github.com/Sunagatov/Iced-Latte/blob/development/src/main/java/com/zufar/icedlatte/cart/exception/InvalidItemProductQuantityException.java)의
구체 인수·필드다. 해당 프로젝트의 응답 포맷이나 동적 공개 메시지까지 채택하는 설계는 아니다.
