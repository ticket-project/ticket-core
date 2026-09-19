# ADR 0015: null 계약을 JSpecify로 명시하고 NullAway로 강제한다

## 상태

채택됨 (2026-09-15)

> 2026-09-19 갱신: `TicketException`의 직접 하위 타입은 일곱이 아니라 여덟이다(흩어진 package가
> 다섯인 것은 그대로). sealed와 exhaustive switch 결정 자체는 그대로다.

[ADR 0002](0002-module-owned-error-contracts.md)와
[ADR 0010](0010-exceptions-do-not-own-http-status.md)의 오류 설계를 강화한다(대체하지 않는다).
"module이 자기 오류를 소유한다"와 "예외는 HTTP 상태를 모른다"는 그대로이고, 그 두 규칙이 지금까지
문서와 리뷰로만 지켜지던 부분을 컴파일러가 잡게 만든다.

## 배경

### 1. null 계약이 코드가 아니라 기억에 있었다

`TicketException.getData()`는 null을 돌려줄 수 있다. `ApiResponse.getData()`도, `getError()`도,
`SliceResponse.nextCursor`도, `CursorPage.nextPosition`도 그렇다. 이 사실은 어디에도 타입으로 적혀
있지 않았고, 일부는 Javadoc에만 있었다. 저장소 전체에 nullability annotation이 **하나도 없었다.**

Querydsl `Tuple.get()`, `Map.get()`, `HttpServletRequest.getHeader()`처럼 "없을 수 있는 것"을
돌려주는 API를 호출하는 자리가 100곳 가까이 있었는데, 그중 어디가 실제로 없을 수 있고 어디가
스키마상 불가능한지는 코드를 따라가야만 알 수 있었다.

### 2. 새 예외를 추가하고 HTTP 매핑을 빠뜨리면 장애로만 알 수 있었다

`BookingExceptionHandler.statusOf`는 15개 하위 타입을 pattern switch로 매핑하고 `default`에서
`IllegalStateException`을 던졌다. 16번째 예외를 추가하면서 매핑을 빼먹으면 컴파일은 통과하고, 그
예외가 실제로 발생한 요청에서만 드러난다.

## 결정

### A. first-party production package는 `@NullMarked`가 기본이다

`src/main/java/com/ticket/**`의 모든 package가 `package-info.java`에 `@NullMarked`를 선언한다.
`@NullMarked`는 하위 package로 전파되지 않으므로 package마다 명시해야 한다 — 이 사실이 곧 "새
package를 만들면서 빼먹기 쉽다"는 뜻이라, `ArchitectureRulesTest`가 소스 트리를 읽어 확인한다.
빠진 package는 조용히 검사에서 제외될 뿐 아무 경고도 내지 않기 때문이다.

### B. nullable한 것에만 `@Nullable`을 붙인다

`org.jspecify.annotations.Nullable`을 쓴다. 판단 기준은 "NullAway가 조용해지는가"가 아니라 "이
값이 실제로 없을 수 있는가"다. 없을 수 없다고 증명되면 `Objects.requireNonNull`로 그 근거를 한 줄
주석과 함께 남긴다 — 특히 Querydsl `Tuple.get()`처럼 API는 nullable이지만 스키마가 NOT NULL을
보장하는 자리가 그렇다.

이미 `Optional<T>`로 표현된 계약은 바꾸지 않는다. Repository와 Query Port가 일관되게 `Optional`을
쓰고 있고, 그것이 의미상 맞는 곳이다.

### C. `@NullUnmarked`와 suppression을 escape hatch로 쓰지 않는다

프레임워크가 채우는 필드만 최소 범위로 뺀다. 지금 예외는 다섯이고 전부 build.gradle에 이유가
적혀 있다.

| 제외 | 이유 |
| --- | --- |
| `@Id` / `@GeneratedValue` | persistence provider가 flush 시점에 채운다 |
| `@Version` | Hibernate가 채우고 관리한다 |
| `@Autowired` / `@Value` | Spring 주입 |
| `@DefaultValue` | `@ConfigurationProperties` 바인딩 |
| `@Generated` (class) | Querydsl Q-type 등 도구 산출물 |

**클래스 단위로 entity 전체를 빼지 않는다.** 그렇게 하면 같은 entity의 진짜 nullable 컬럼
(`deletedAt`, `canceledAt`, `approvedAt` 등)도 함께 검사에서 빠진다. 필드 단위로 빼야 초기화 시점
문제만 없애고 컬럼의 null 의미는 계속 검사받는다.

### D. NullAway를 ERROR로 빌드에 연결한다

Error Prone 위에 NullAway를 얹고 `onlyNullMarked = true`, `jspecifyMode = true`로 돌린다.
`onlyNullMarked`라서 `@NullMarked`를 붙인 만큼만 검사 범위가 늘어난다. `jspecifyMode`는 generic
타입 인자까지 본다 — `ApiResponse<T extends @Nullable Object>`와
`CursorPage<T, P>(..., @Nullable P nextPosition)`이 그 덕에 정확히 표현된다.

**Error Prone의 기본 rule 전체는 끈다.** 목적은 null 계약이지 Error Prone 전면 도입이 아니다.
무관한 지적 수백 개가 빌드를 막으면 이 작업 자체가 진행되지 않는다. 오탐이 적은 네 개
(`MissingOverride`, `EqualsHashCode`, `ReturnValueIgnored`, `UnusedVariable`)만 경고로 남긴다.

검사 대상은 `src/main/java`다. test와 seed에는 `@NullMarked`가 없어 검사해도 아무 일이 없고
컴파일만 느려진다.

### E. module별 업무 예외 계층을 sealed로 닫는다

`BookingException`(15) / `MemberException`(3) / `ShowException`(1) / `LikeException`(1) /
`AdmissionTokenException`(2)를 sealed로 만들고 handler switch의 `default`를 지운다. 이제 새 예외를
추가하면서 HTTP 매핑을 빠뜨리면 **컴파일이 실패한다.**

`TicketException`은 sealed로 만들지 않는다. 직접 하위 타입 일곱이 다섯 package에 흩어져 있고, 이
프로젝트는 JPMS named module이 아니라 permits 대상이 같은 package에 있어야 한다. 억지로 맞추려면
module별 오류 소유권(ADR 0002)을 깨야 하고, 루트에서 exhaustive가 필요하지도 않다 —
`GlobalExceptionHandler`는 switch가 아니라 Spring의 타입별 dispatch를 쓴다.

## 검토한 대안

### 대안 1: Spring의 `org.springframework.lang.Nullable`을 쓴다

**버린 이유**: Spring Framework 7 자신이 JSpecify로 옮겼고, Spring Boot BOM이 `jspecify` 버전을
관리한다. 프레임워크가 쓰는 것과 같은 annotation을 쓰면 Spring API의 nullability를 NullAway가 그대로
읽는다 — 실제로 `MessageListener.onMessage`와 `HandlerMethodArgumentResolver.resolveArgument`의
`@Nullable` 파라미터를 이 덕에 잡았다.

### 대안 2: annotation만 붙이고 도구는 나중에 붙인다

**버린 이유**: 강제되지 않는 표기는 시간이 지나면 코드와 어긋난다. 그러면 지금 문제(계약이 기억에
있다)가 "계약이 틀린 표기에 있다"로 바뀔 뿐이고, 틀린 표기는 없는 것보다 나쁘다.

### 대안 3: 엔티티 전체를 `ExcludedClassAnnotations`로 뺀다

도입이 훨씬 빠르다 — "initializer does not guarantee field initialized" 17건이 한 줄로 사라진다.

**버린 이유**: 그 17건 중 상당수가 진짜 nullable 컬럼이다. `Member.deletedAt`(탈퇴 전에는 없다),
`Order.canceledAt`, `Payment.failureMessage` 같은 것들인데, 엔티티를 통째로 빼면 이 값들의 null
의미가 영영 타입에 나타나지 않는다. 결제 도입처럼 이 값들을 읽는 코드가 늘어날 자리에서 가장 필요한
정보를 미리 버리는 셈이다.

## 결과

- production 전 package가 `@NullMarked`이고, 새 package가 정책 없이 생기면 테스트가 잡는다.
- NullAway 오류 0. 초기 검출은 100건이었고 전부 표기 또는 `requireNonNull`로 해소했다 —
  `@NullUnmarked`와 suppression은 하나도 쓰지 않았다.
- 예외 매핑 누락이 runtime이 아니라 compile time에 실패한다.
- 치른 비용: `package-info.java` 58개 신설, 12개 갱신. 공개 계약 타입 일부의 시그니처에
  `@Nullable`이 붙었다(컴파일 타임 계약 변경이며 wire 계약은 그대로다).
