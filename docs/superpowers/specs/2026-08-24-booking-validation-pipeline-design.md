# 예매 검증 단계 통합 설계

## 목표

예매 플로우 네 엔드포인트에서 **비용이 드는 검증을 한 클래스·한 트랜잭션에 모으고 비용 순서로 정렬한다.**

"검증을 전부 앞으로 옮긴다"가 아니다. 실제로 일어나는 일은 셋이다.

- **모이기** — 좌석 소속·가용 검증이 `HoldAllocator`에서 검증기로 와서 DB 검증들이 한 트랜잭션에 모인다
- **재배치** — 무료 → 정책 조회 1회 → DB → Redis 순서로 정렬한다
- **admission은 뒤로 간다** — 정책 조회를 1회로 만들려면 정책 뒤에 와야 한다

hold 점유 검증(T3)은 Redis 원자성 때문에 움직일 수 없고, 요청 형식(`@Valid`)과 좌석 정규화(T0)는
이미 가장 앞이라 옮길 이유가 없다. 그래서 검증 위치는 4곳으로 유지되고, **DB와 Redis를 만지는 검증의
위치가 4곳에서 2곳으로** 줄어든다.

부수 효과로 요청당 DB 커넥션 획득이 줄고, 회차 정책 조회가 1회가 되며, admission 검증을 빠뜨릴 수 없게 된다.

실제 처리량이 얼마나 늘어날지는 주장하지 않는다. 계측이 제거된 상태(`735dd00b`)이므로 이 설계는
**왕복 횟수와 검증 위치**만 근거로 삼는다.

## 현재 구조의 문제

### 검증이 4개 계층에 흩어져 있다

`POST /api/v1/orders` 기준으로 검증 9개가 이렇게 퍼져 있다.

| # | 검증 | 위치 | 필요한 것 |
| --- | --- | --- | --- |
| 1 | 요청 형식(`performanceId` 양수, `seatIds` 비어있음·null·양수) | `@Valid` (Spring) | 요청 |
| 2 | admission 토큰 | **컨트롤러** | 정책 + 토큰 |
| 3 | 좌석 ID 중복·정렬 | `RequestedSeatIds.from` | 요청 |
| 4 | 회원 존재 | `CreateOrderValidator` | DB |
| 5 | 오픈/마감 | `CreateOrderValidator` → `findValidById` | 정책 |
| 6 | 좌석 수 한도 | `CreateOrderValidator` | 정책 |
| 7 | PENDING 중복 | `CreateOrderValidator` | DB |
| 8 | 좌석 소속·가용 | **`HoldAllocator`** → `HoldSeatAvailabilityValidator` | DB |
| 9 | hold 점유 | `HoldManager.ensureSeatsNotHeld` | Redis |

### 비용 순서가 어긋나 있다

```
현재  #2(정책조회+HMAC) → #1·#3(무료) → #4(DB) → #5·#6(정책) → #7·#8(DB) → #9(Redis)
       ^^^^^^^^^^^^^^^^^^^ 가장 비싼 것이 맨 앞      ^^^^^^ DB가 메모리 판정보다 앞
```

구체적 손해는 셋이다.

- 좌석 ID가 잘못된 요청이 `#2`에서 정책 조회 + HMAC 검증을 치른 뒤 `#3`에서 거부된다
- 마감된 회차 요청이 `#4`에서 회원 쿼리를 먼저 쓴다. `#5`에서 어차피 거부될 요청이다
- `#8`이 `HoldAllocator` 안에 있어 `#7`과 떨어져 **커넥션을 따로 빌린다**

### 정책을 두 번 조회한다

컨트롤러의 `admissionTokenValidator.validate`가 `findById`로 정책을 읽고, 유스케이스가
`findValidById`로 또 읽는다. `ADMISSION_TOKEN_ENFORCEMENT_ENABLED`가 `false`인 지금은 앞의 조회가
일어나지 않아 증상이 없지만, **켜는 순간 요청당 쿼리 2회 / 커넥션 획득 2회가 된다.**

### admission 검증을 빠뜨릴 수 있다

`CreateOrderUseCase`를 `OrderController`와 `HoldController`가 함께 쓴다. 각 컨트롤러가 admission 호출을
기억해야 하고, 하나를 빠뜨리면 대기열을 우회하는 구멍이 생긴다. 컴파일러도 테스트도 잡지 못한다.

이 저장소에는 **같은 유형의 사고가 이미 있다.** 회원 존재 검사가 주문 생성·취소·전체해제에는 있고
좌석 선택에는 없다(→ [#212](https://github.com/ticket-project/ticket-core/issues/212)).

### `HoldAllocator`가 DB와 Redis를 한 메서드에 섞는다

```java
public HoldAllocation allocate(...) {
    final List<PerformanceSeat> seats =
            holdSeatAvailabilityValidator.validate(performanceId, requestedSeatIds);   // DB
    final HoldSnapshot snapshot =
            holdManager.createHold(memberId, performanceId, requestedSeatIds, holdDuration, now);  // Redis
    return new HoldAllocation(snapshot, seats);
}
```

이 메서드를 트랜잭션으로 감싸면 Redis 호출이 트랜잭션 안으로 들어간다.
`docs/core-booking-lifecycle.md`의 "Redis 또는 WebSocket 호출 중에는 DB connection을 점유하지 않는다"를
어기게 된다. 즉 **현재 구조에서는 커넥션 통합이 불가능하다.**

## 설계

### 비용 계층

검증을 정리하는 축은 계층이 아니라 **비용**이다.

| 계층 | 검증 | 비용 |
| --- | --- | --- |
| **T0** | 요청 형식, 좌석 정규화 | 0 (메모리) |
| **T1** | 오픈/마감, 좌석 수 한도, admission | **정책 조회 1회**로 전부 |
| **T2** | PENDING 중복, 좌석 소속·가용 | DB 쿼리 (같은 트랜잭션) |
| **T3** | hold 점유 + 생성 | Redis (원자적) |

T3은 앞으로 당길 수 없다. Redis 원자성이 필요하고 `holdStore.save`와 한 덩어리여야 한다.

### 실행 형태

```java
// CreateOrderUseCase.execute
public Output execute(final Input input) {
    final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());   // T0
    final LocalDateTime now = LocalDateTime.now(clock);

    final ValidatedOrderRequest validated =                                            // T1 + T2
            validator.validate(input, requestedSeatIds, now);

    final Duration holdDuration = Duration.ofSeconds(validated.policy().holdTime());
    final HoldAllocation allocation = holdAllocator.allocate(                          // T3
            input.memberId(),
            input.performanceId(),
            requestedSeatIds,
            validated.performanceSeats(),
            holdDuration,
            now
    );
    // ... 이하 기존과 동일
}
```

```java
// CreateOrderValidator — 검증이 한 곳에 모이고 비용 순서로 정렬된다
@Transactional(readOnly = true)
public ValidatedOrderRequest validate(
        final CreateOrderUseCase.Input input,
        final RequestedSeatIds requestedSeatIds,
        final LocalDateTime now
) {
    // T1 — 정책 1회 조회로 셋을 판정
    final PerformanceBookingPolicyView policy =
            performanceBookingPolicyFinder.findById(input.performanceId());
    BookingPolicyValidator.ensureBookingOpen(policy, now);
    BookingPolicyValidator.ensureWithinHoldLimit(policy, requestedSeatIds.size());
    ensureAdmitted(policy, input.memberId(), input.admissionToken(), now);

    // T2 — 같은 트랜잭션, 커넥션 1개
    ensureNoPendingOrder(input.memberId(), input.performanceId());
    final List<PerformanceSeat> performanceSeats =
            holdSeatAvailabilityValidator.validate(input.performanceId(), requestedSeatIds);

    return new ValidatedOrderRequest(policy, performanceSeats);
}

private void ensureAdmitted(
        final PerformanceBookingPolicyView policy,
        final Long memberId,
        final String admissionToken,
        final LocalDateTime now
) {
    if (!BookingPolicyValidator.requiresQueue(policy, now)) {
        return;
    }
    admissionGuard.ensureAdmitted(policy.performanceId(), memberId, admissionToken);
}
```

### T1 내부 순서

**오픈/마감 → 좌석 수 한도 → admission** 으로 둔다.

정책 조회 후에는 셋 다 메모리 판정이라 비용 차이가 없다. 이 순서를 고른 이유는
`requiresQueueAt`이 마감된 회차에 대해 이미 `false`를 반환하기 때문이다. 마감 판정이 먼저 오면
"마감된 회차인데 admission 토큰을 요구한다"는 어색한 응답이 나오지 않는다.

`EXCEED_HOLD_LIMIT`이 `maxCanHoldCount`를 노출하지만 이 값은 `GET /performances/{id}/summary`
응답에도 들어 있어 admission 이전에 알려주는 것이 문제되지 않는다.

### `AdmissionGuard` port

`core-domain`에는 jjwt 의존이 없다(`core-domain/build.gradle` 확인). 토큰 검증은 대기열 서버와의
기술 계약이므로 도메인이 알 필요도 없다. **도메인은 "입장이 보장됐는가"만 묻고, 검증 방법은 어댑터가 안다.**

```java
// core-domain/domain/queue/AdmissionGuard.java
package com.ticket.core.domain.queue;

public interface AdmissionGuard {

    void ensureAdmitted(Long performanceId, Long memberId, String admissionToken);
}
```

설계 선택 셋을 명시한다.

- **정책을 인자로 받지 않는다.** "대기열이 필요한가"는 호출 전에 `BookingPolicyValidator.requiresQueue(policy, now)`가 이미 판단한다
- **`Clock`을 받지 않는다.** 시각 판정이 도메인에 남는다
- **반환값이 없다.** 실패는 예외다

`domain/queue/`에 두는 이유는 그 패키지가 지금 `QueueMode`·`QueueLevel` 두 enum만 갖고 있고,
admission이 곧 대기열 개념이기 때문이다.

### 어댑터 — 새 클래스를 만들지 않는다

port를 구현할 곳이 이미 있다. `AdmissionTokenService`가 토큰 검증을 담당하고 있고,
`AdmissionClaims` 반환값은 **테스트 외에 아무도 쓰지 않는다**(`verifyFor` 호출자인
`AdmissionTokenValidator`가 반환값을 버린다). 그래서 이 클래스가 port를 직접 구현한다.

```java
// core-api/config/admission/AdmissionTokenService.java
public class AdmissionTokenService implements AdmissionGuard {

    private final AdmissionTokenProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;
    private final boolean enforcementEnabled;          // AdmissionTokenValidator에서 이동

    @Override
    public void ensureAdmitted(final Long performanceId, final Long memberId, final String admissionToken) {
        if (!enforcementEnabled) {
            return;
        }
        if (admissionToken == null || admissionToken.isBlank()) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED);
        }
        try {
            verifyFor(admissionToken, memberId, performanceId);
        } catch (final AdmissionTokenExpiredException exception) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_EXPIRED);
        } catch (final AdmissionTokenException exception) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_INVALID);
        }
    }

    // verify / verifyFor 는 package-private으로 내린다. 외부 사용처가 테스트뿐이다
}
```

`CoreException`·`ErrorType`은 `core-domain/support/exception`에 있고 `core-api`가 이미
`ApiControllerAdvice`와 `AdmissionTokenValidator`에서 쓰고 있으므로 새 의존이 아니다.

`AdmissionTokenValidator`는 삭제한다. 그 클래스가 하던 세 가지 일이 이렇게 갈린다.

| 하던 일 | 옮겨갈 곳 |
| --- | --- |
| enforcement 토글 확인 | `AdmissionTokenService` |
| 정책 조회 + `requiresQueueAt` 판정 | 유스케이스 (T1) |
| 토큰 검증 + 예외 번역 | `AdmissionTokenService` |

**정책 조회 의존이 admission 쪽에서 완전히 사라진다.** 부수 효과로 `f6aea4a0`에서 고친
`Clock.systemDefaultZone()` 시간대 버그가 구조적으로 재발할 수 없게 된다. `AdmissionTokenService`가
갖는 `Clock`은 JWT `exp`/`iat` 비교용이고 시간대에 영향받지 않는 instant 비교다.
시간대에 의존하는 `LocalDateTime.now(clock)` 판정은 도메인(T1)으로 옮겨간다.

신규 파일은 **`AdmissionGuard` 인터페이스 하나뿐이다.**

### 어댑터를 core-api에 두는 이유

기존 도메인 port 5개는 전부 core-infra 구현이다(`HoldStore`, `SeatSelectionStore`,
`SeatStatusEventPublisher`, `HoldCreationPostCommitNotifier`, `RefreshTokenStore`).
core-api 구현은 새 패턴이다.

그래도 core-api에 두는 이유는 `AGENTS.md`의 모듈 기술이 **security를 core-api에 배정**하고 있고,
jjwt 의존과 `@ConfigurationProperties`가 이미 `core-api/config/admission`에 있기 때문이다.
port 구현 위치는 명문 규칙이 아니라 지금까지 전부 infra 관심사였던 결과다.

대안은 admission 패키지를 core-infra로 옮기고 jjwt를 추가하는 것인데, 그러면 access token을 다루는
`JwtTokenService`는 core-api에 남아 **JWT 처리가 두 모듈로 갈라진다.** 그쪽이 더 나쁘다.

### `PerformanceBookingPolicyFinder` 분해

정책 조회가 T1로 옮겨오면서 조회와 판정을 나눈다.

```java
// 현재 — 조회와 판정이 붙어 있다
public PerformanceBookingPolicyView findValidById(final Long performanceId, final LocalDateTime now) {
    final PerformanceBookingPolicyView policy = findById(performanceId);
    if (policy.orderOpenTime() == null || now.isBefore(policy.orderOpenTime())) {
        throw new CoreException(ErrorType.NOT_YET_RESERVE_TIME);
    }
    if (policy.orderCloseTime() == null || now.isAfter(policy.orderCloseTime())) {
        throw new CoreException(ErrorType.PERFORMANCE_IS_PAST);
    }
    return policy;
}
```

판정은 `BookingPolicyValidator`가 소유한다. 호출자가 셋(주문 생성·좌석 상태 조회·좌석 선택)이므로
술어만 내리고 예외를 호출자가 던지면 같은 6줄이 세 번 복제된다.

```java
// performance/query/BookingPolicyValidator
public static void ensureBookingOpen(final PerformanceBookingPolicyView policy, final LocalDateTime now) {
    if (policy.orderOpenTime() == null || now.isBefore(policy.orderOpenTime())) {
        throw new CoreException(ErrorType.NOT_YET_RESERVE_TIME);
    }
    if (policy.orderCloseTime() == null || now.isAfter(policy.orderCloseTime())) {
        throw new CoreException(ErrorType.PERFORMANCE_IS_PAST);
    }
}
```

```java
// 호출부
final PerformanceBookingPolicyView policy = performanceBookingPolicyFinder.findById(performanceId);
BookingPolicyValidator.ensureBookingOpen(policy, now);
```

`findValidById`는 호출처가 없어지므로 삭제한다.

초안은 이 판정을 `PerformanceBookingPolicyView`의 메서드로 두었다. 구현 후 되돌렸다 —
아래 "후속 결정"을 보라.

### `HoldAllocator`

`HoldSeatAvailabilityValidator` 의존을 버리고 좌석 목록을 인자로 받는다. **Redis hold 할당만 담당하게 되어
DB 구간과 Redis 구간이 호출 순서로 드러난다.**

```java
public HoldAllocation allocate(
        final Long memberId,
        final Long performanceId,
        final RequestedSeatIds requestedSeatIds,
        final List<PerformanceSeat> performanceSeats,
        final Duration holdDuration,
        final LocalDateTime now
) {
    final HoldSnapshot snapshot =
            holdManager.createHold(memberId, performanceId, requestedSeatIds, holdDuration, now);
    return new HoldAllocation(snapshot, performanceSeats);
}
```

### 새 타입

```java
// core-domain/domain/order/command/create/ValidatedOrderRequest.java
public record ValidatedOrderRequest(
        PerformanceBookingPolicyView policy,
        List<PerformanceSeat> performanceSeats
) {
}
```

같은 패키지의 `HoldAllocation`, `PendingOrderCreationResult` 관례를 따른다.

## 엔드포인트별 적용

네 엔드포인트에 같은 형태를 적용한다.

### `POST /api/v1/orders`, `POST /api/v1/performances/{id}/holds`

같은 유스케이스를 쓴다. `Input`에 `admissionToken`을 추가하고 컨트롤러는 헤더 값을 그대로 넘긴다.

```java
// OrderController — admissionTokenValidator 의존이 사라진다
final CreateOrderUseCase.Output output = createOrderUseCase.execute(new CreateOrderUseCase.Input(
        request.getPerformanceId(),
        request.getSeatIds(),
        memberPrincipal.getMemberId(),
        admissionToken
));
```

### `GET /api/v1/performances/{id}/seats/status`

`GetSeatStatusUseCase`는 이미 정책을 검증 목적으로만 조회한다(`8ddce94d`에서 반환값 사용을 제거했다).
`Input`에 토큰을 추가하고 T1에서 `ensureBookingOpen` + `ensureAdmitted`를 수행한다. T2는 좌석 상태
조회 하나다.

### `POST /api/v1/performances/{id}/seats/{seatId}/select`

여기만 추가 작업이 있다. 현재 `findForSelection`이 정책과 좌석 state를 **한 쿼리로 섞어 읽는다.**

```java
// SeatSelectionAvailabilityQueryRepository
.select(Projections.constructor(SeatSelectionAvailabilityView.class,
        performance.orderOpenTime,
        performance.orderCloseTime,
        performanceSeat.id,
        performanceSeat.state))
```

`SeatSelectionAvailabilityView`에 queue 정책 필드(`queueMode`, `preopenQueueStartAt`)가 없어
`requiresQueueAt`을 호출할 수 없다. 그래서 **T1(정책)과 T2(좌석 state)로 분리한다.**

- T1 — `PerformanceBookingPolicyView`로 오픈/마감과 admission을 판정
- T2 — `(performance_id, seat_id)` 단건으로 좌석 state 조회.
  `UK_PERFORMANCE_SEATS_PERFORMANCE_SEAT` 유니크 인덱스를 타므로 현재의 2테이블 조인보다 싸다

이 분리는 좌석 state가 결제 도입 후 런타임에 변하므로 **판정 경로에서 캐시할 수 없다**는 제약과도 맞물린다.
정책(캐시 가능)과 좌석 state(비캐시)를 갈라두면 이후 캐시 전략을 붙일 때 같은 일을 두 번 하지 않는다.

### 순수 좌석 해제 경로

`DELETE .../seats/{seatId}/select`와 `DELETE .../seats/select`는 admission 검증 대상이 아니다
(현재도 `SeatSelectionController`의 전체 해제 경로에는 `validate` 호출이 없다). 이 설계에서 바꾸지 않는다.

## 얻는 것

`POST /api/v1/orders` 기준이다.

| | 현재 | 변경 후 |
| --- | --- | --- |
| 정책 조회 (enforcement on) | 2회 | **1회** |
| 정책 조회 (enforcement off) | 1회 | **1회** |
| 전면 DB 커넥션 획득 | 4회 | **1회** |
| 요청 전체 커넥션 획득 | 7회 | **4회** |
| 검증 코드 위치 | 4개 계층 | **1개 클래스 + T0/T3** |
| 잘못된 좌석 ID 요청 | 정책 조회 + HMAC 후 거부 | **즉시 거부** |
| 마감 회차 요청 | 회원 쿼리 후 거부 | **정책 판정에서 즉시 거부** |
| admission 누락 | 컨트롤러가 기억해야 함 | **`Input` 필드로 컴파일 강제** |
| 컨트롤러 의존 | `AdmissionTokenValidator` | **없음** |

요청 전체가 4회인 이유는 남는 셋을 합칠 수 없기 때문이다.

- **주문 저장 트랜잭션** — 쓰기다. 읽기와 합치면 커넥션 점유가 Redis hold 생성 구간까지 늘어난다
- **outbox load** / **markCompleted** — 이 둘 사이에 Redis 해제와 WebSocket 발행이 있다.
  합치면 외부 I/O 중 커넥션을 점유한다. 의도적으로 분리된 구조다

## 지키는 규약

- `execute`에 `@Transactional`을 붙이지 않는다. `CreateOrderUseCaseTest`의
  `execute는_DB_트랜잭션을_직접_시작하지_않는다`가 이미 강제한다
- `validate`의 트랜잭션은 Redis 호출 전에 닫힌다. `holdManager.createHold`는 `validate` 반환 후다
- `@DistributedLock(start-order)`는 `execute` 바깥이므로 트랜잭션이 락 획득을 감싸지 않는다
- **HMAC 검증이 트랜잭션 안에서 일어난다.** 규약은 "Redis 또는 WebSocket 호출 중 커넥션 미점유"이고
  HMAC-SHA256은 외부 I/O가 아니라 순수 CPU다(짧은 토큰에 수십 μs, `secretKey`는 생성자에서 미리 계산).
  커넥션 점유 시간에 유의미한 영향이 없다

## detached 엔티티

`PerformanceSeat`은 T2 트랜잭션 종료 후 detached가 된다. 소비처는 `OrderCreator`와
`HoldHistoryRecorder` 둘이고 `getId()`, `getSeat().getId()`, `getPrice()`만 쓴다. `getSeat()`는 LAZY
프록시지만 **id 접근은 초기화를 유발하지 않는다.**

**지금도 완전히 같은 상태다.** 현재도 트랜잭션 밖에서 조회하므로 즉시 detached다. 이 변경이 새로운
위험을 만들지 않는다.

프로젝션으로 바꾸면 엔티티 전체 로드(`BaseEntity` 감사 4컬럼 포함)와 프록시 의존이 사라지지만
소비처 4곳과 테스트가 함께 움직인다. **이번 범위에서 하지 않는다.**

## 커밋 분할

네 엔드포인트를 한 PR에서 다루되 커밋을 잘게 나눈다. 각 커밋은 그 시점에 빌드와 테스트가 통과해야 한다.

| # | 커밋 | 범위 |
| --- | --- | --- |
| 1 | `refactor(performance): 회차 정책 조회와 예매 가능 판정을 분리` | `ensureBookingOpenAt` 추가, `findValidById` 삭제, 호출처를 `findById` + 판정으로 교체 |
| 2 | `refactor(queue): AdmissionGuard port 도입` | port 선언, `AdmissionTokenService`가 구현하고 enforcement 흡수. `AdmissionTokenValidator`는 port로 위임. 컨트롤러 무변경 |
| 3 | `refactor(order): 주문 생성 검증을 검증기로 통합` | `ValidatedOrderRequest`, `CreateOrderValidator` 통합, `HoldAllocator` 시그니처, `Input`에 토큰. `OrderController`·`HoldController` |
| 4 | `refactor(performanceseat): 좌석 상태 조회 검증을 유스케이스로 통합` | `GetSeatStatusUseCase`, `PerformanceController` |
| 5 | `refactor(performanceseat): 좌석 선택 검증을 정책과 좌석 상태로 분리` | `findForSelection` 분해, `SelectSeatUseCase`, `SeatSelectionController` |
| 6 | `refactor(admission): AdmissionTokenValidator 제거` | 호출처가 없어진 클래스와 테스트 삭제 |

| 7 | `refactor(performance): 예매 정책 판정을 검증기로 분리` | `BookingPolicyValidator`·`QueueActivation` 신설. 호출부 무변경 |
| 8 | `refactor(performance): 정책 뷰에서 판정을 걷어내고 검증기로 호출을 옮긴다` | 뷰가 순수 데이터가 된다. 호출부 셋 + `PerformanceQueuePolicy` 위임 |
| 9 | `refactor(performance): 예매 판정이 떠나간 뒤 남은 죽은 코드를 제거한다` | 호출자를 잃은 엔티티·조회기 메서드 삭제 |

1~2는 기존 동작을 바꾸지 않는 준비 작업이고, 3~5가 실제 통합이며, 6은 정리다.
7~9는 구현 중 나온 후속 결정이다 (아래).

## 테스트

### 수정

| 테스트 | 변경 |
| --- | --- |
| `AdmissionTokenValidatorTest` | 커밋 2에서 토큰 검증 케이스를 `AdmissionTokenServiceTest`로 이동. 커밋 6에서 나머지와 함께 삭제 |
| `CreateOrderValidatorTest` | `HoldSeatAvailabilityValidator`·`AdmissionGuard` mock 추가, 반환값 단정 변경 |
| `HoldAllocatorTest` | 시그니처 변경, `HoldSeatAvailabilityValidator` mock 제거 |
| `CreateOrderUseCaseTest` | stub 반환 타입·시그니처 변경 |
| `GetSeatStatusUseCaseTest` | `Input`에 토큰, admission 경로 |
| `SelectSeatUseCaseTest`, `SeatSelectionAvailabilityValidatorTest` | 정책·좌석 분리 |
| `PerformanceBookingPolicyFinderTest` | `findValidById` 제거 반영 |
| `OrderControllerContractTest`, `HoldControllerContractTest`, `PerformanceControllerContractTest`, `SeatSelectionControllerContractTest` | `AdmissionTokenValidator` mock 제거 → **단순해진다** |

### 추가

- `CreateOrderValidator.validate`에 `@Transactional(readOnly = true)`가 붙어 있는지 리플렉션 검증.
  `CreateOrderUseCaseTest`가 이미 쓰는 저장소 관례를 따른다
- T1에서 실패하면 T2 조회를 하지 않는지 `verifyNoInteractions`로 고정. 비용 순서 정렬이 코드로 남는다
- admission이 필요한 회차에서 토큰 없이 호출하면 거부되는지 (유스케이스 레벨)

### 검증 명령

```bash
./gradlew :core:core-domain:test
./gradlew :core:core-api:test
./gradlew test :core:core-infra:integrationTest :core:core-api:bootJar
```

Redis key·TTL·Lua를 바꾸지 않으므로 `integrationTest`는 필수가 아니지만, CI와 같은 전체 검증을 마지막에 한 번 돌린다.

## 하지 않는 것

| 항목 | 이유 |
| --- | --- |
| 회원 존재 검사 제거 | [#212](https://github.com/ticket-project/ticket-core/issues/212)로 분리. 보안 포스처 결정이 필요하다. **이 설계와 독립이다** — 검사가 남아도 같은 트랜잭션에 참여하므로 커넥션 획득은 1회다 |
| `PerformanceSeat` → 프로젝션 전환 | 소비처 4곳 + 테스트. 이번 목적은 트랜잭션 경계다 |
| `@Valid`와 `RequestedSeatIds` 중복 정리 | `RequestedSeatIds`는 도메인 타입이고 컨트롤러 없이도 호출될 수 있다. 방어를 남긴다 |
| 정책·요약 캐시 복구 | 이 설계와 직교한다. 통합은 요청 **내** 중복을, 캐시는 요청 **간** 반복을 없앤다 |
| 락 `waitTime`·`leaseTime` 조정 | 동작이 바뀌는 변경이라 부하 측정 근거가 필요하다 |
| Redisson `timeout`·`retryAttempts` 명시 | 설정 항목으로 분리. 명령 1회 최악 지연이 약 23초다 |
| hold 저장 Lua 통합 | 별 설계. `RedissonHoldStoreTest` 재작성과 meta key hash tag 변경을 포함한다 |
| `IDENTITY` → `SEQUENCE`, CLOB 제거 | 스키마 변경. 결제 도메인의 `PAYMENTS` id 전략과 합의가 필요하다 |
| 후면 worker 수 조정 | Hikari pool 설정이 선행이다 |

## 후속 결정 — 판정을 검증기로 분리

초안은 오픈·마감 판정을 `PerformanceBookingPolicyView`의 메서드로 두었다. 구현을 마친 뒤 되돌렸다.

**이유.** `query/model` 아래 `...View` 17개 중 메서드를 가진 것이 이 하나뿐이었다. 나머지 16개는
전부 메서드가 없다. 조회 전용 타입에 비즈니스 로직을 두지 않는다는 규약을 세우고 그에 맞췄다.

`isOverCount`와 `requiresQueueAt`은 초안 이전부터 이 뷰에 있었다. 초안이 예외를 만든 것이 아니라
넓혔다.

**옮긴 자리.**

| 규칙 | 어디로 | 왜 |
| --- | --- | --- |
| 오픈·마감, 좌석 수 한도 | `performance/query/BookingPolicyValidator` | 정책 뷰만 쓰는 판정. 뷰를 인자로 받아 호출부를 짧게 유지한다 |
| 대기열 필요 여부 | `performance/QueueActivation` | 프로젝션 경로와 엔티티 경로가 같이 부른다. 어느 타입에도 매이지 않게 값만 받는다 |

`BookingPolicyValidator.requiresQueue`가 `QueueActivation`을 감싸므로 호출부는 검증기 하나만 안다.

**대기열 규칙의 중복.** `PerformanceQueuePolicy.requiresQueueAt`과 뷰의 `requiresQueueAt`이 문자
단위로 같았다. 전자는 `ShowDetailQueryRepository` → `BookingEntryResolver`(`GET /shows/{id}`)가,
후자는 예매 3경로가 쓴다. 둘 다 살아 있었으므로 대기열 정책이 바뀔 때 한쪽만 고칠 위험이 있었다.
이제 양쪽이 `QueueActivation`에 위임한다.

**정적 유틸을 쓴 근거.** `order/OrderRemainingTime`이 같은 문제를 같은 형태로 이미 풀었다 —
`HoldAllocation`(커맨드)과 `OrderDetailRow`·`OrderStatusView`(프로젝션) 셋이 한 규칙을 공유한다.
`performanceseat/support/SeatRedisKey`, `performance/query/BookingEntryResolver`도 같은 형태다.

### 딸려 나온 죽은 코드

커밋 3에서 주문 생성 검증을 엔티티 경로에서 프로젝션 경로로 옮기면서 떠나온 쪽이 호출자를 잃었다.
남겨두면 같은 규칙의 두 번째 사본이 된다. 프로덕션 호출자가 0인 것들을 제거했다.

- `Performance.isBookingOpen`, `Performance.isOverCount`
- `PerformanceFinder.findOpenPerformance`, `findValidPerformanceById`, `validatePerformance`
- `Performance`의 대기열 위임 게터 5개
- `PerformanceQueuePolicyRepository` — 주입받는 곳이 없었다

`Performance.updateQueuePolicy`는 남겼다. `PerformanceQueuePolicy.create`가 역방향 링크를 세팅하지
않아 이것 말고는 대기열 정책을 붙일 방법이 없고, 살아있는 경로인 `GET /shows/{id}`의 테스트 픽스처가
여기에 의존한다.

### 커버리지 이동

지운 메서드들이 지키던 규칙을 먼저 옮긴 뒤 지웠다. 옮기면서 초안에 없던 경계를 채웠다.

- `now`가 `orderOpenTime`·`orderCloseTime`과 정확히 같은 경우
- 요청 좌석 수가 한도와 같은 경우, 한도가 `null`인 경우
- 대기열 `preopenQueueStartAt`·`orderCloseTime`과 `now`가 정확히 같은 경우

`PerformanceBookingPolicyViewTest`(146줄)를 `BookingPolicyValidatorTest`(13케이스)와
`QueueActivationTest`(10케이스)로 대체했다.

## 남은 리스크

- **`GetSeatStatusUseCase`의 admission 순서가 바뀐다.** 현재는 컨트롤러에서 먼저 거부하는데, 변경 후에는
  정책 조회 뒤에 거부한다. 토큰 없는 요청이 정책 조회 1회를 더 치른다. 메모리 판정 두 개가 앞에 붙는
  것뿐이고, 캐시를 복구하면 이 비용도 사라진다
- **`validate` 시그니처가 `Input`을 받는다.** 유스케이스 입력 타입에 검증기가 의존하게 된다.
  파라미터를 개별로 풀면 5개가 되어 가독성이 떨어지므로 `Input`을 받는 쪽을 택했다
- **커밋 4~6이 컨트롤러를 건드린다.** 계약 테스트 4개가 함께 움직이므로 리뷰 단위가 커진다.
  커밋을 엔드포인트별로 나눈 이유다
- **효과가 측정되지 않는다.** 커넥션 획득 감소는 구조적으로 확실하지만, 실제 지연 개선은 pool 경합
  정도에 달려 있다. 계측 복구 전에는 수치를 주장하지 않는다
