# ADR 0014: 모듈 공개 계약은 api 패키지가 갖고, controller 패키지는 endpoint로 부른다

## 상태

채택됨 (2026-09-15)

[ADR 0003](0003-spring-modulith-application-module-boundaries.md)의 "모듈 root = cross-module
공개 계약"과 [ADR 0013](0013-layer-first-package-layout-and-security-owns-authentication.md)의
계층 이름 `web`을 이 부분에 한해 대체한다. 두 ADR의 나머지 결정(Application Module 경계, 모듈 →
계층 배치, 업무별 폴더는 `domain` 아래에만, 인증 조립은 security 소유)은 그대로 유효하다.
[ADR 0006](0006-bounded-context-module-boundaries.md)이 `Region`을 "module root의 유일한 예외"로
적은 문장은 "`venue.api`의 유일한 enum"으로 읽는다 — 예외의 내용은 같고 자리만 옮겼다.

## 배경

### 1. 공개면이 규칙으로만 있고 이름으로 없었다

"모듈 root에 있는 것이 공개 계약"은 ADR 0003이 정한 규칙이었다. 규칙 자체는 잘 지켜졌지만, 어떤
타입이 공개면인지 알려면 그 타입이 root에 있는지를 눈으로 확인해야 했다. import 문
`com.ticket.member.MemberLookup`은 그것이 공개 계약인지 우연히 root에 있는 구현인지 말해 주지
않는다. 실제로 `SharedModulePurityTest`는 shared에 대해서만 "root에 bean을 두지 않는다"를 강제하고
있었고, 나머지 모듈에는 같은 보호가 없었다.

### 2. `allowedDependencies`가 모듈 전체를 열 수밖에 없었다

공개 계약이 root에 있으니 `booking`은 `"show"`라고 선언할 수밖에 없었다. Spring Modulith는
CLOSED 모듈의 하위 패키지를 이미 막아 주므로 안전 자체는 확보돼 있었지만, **선언이 실제 표면을
말해 주지 않았다.** `"shared :: *"` 와일드카드는 더 나빴다 — shared에 새 named interface가
추가되면 모든 모듈이 그것을 조용히 쓸 수 있게 된다.

### 3. `web`이 한 저장소에서 세 가지 뜻으로 쓰였다

업무 모듈의 controller 패키지(`booking.web`), 모든 응답을 감싸는 공통 봉투 계약(`shared.web`,
`@NamedInterface("web")`), 그리고 Spring의 `org.springframework.web`이 모두 `web`이었다. 문서에서
"web 계층"이라고 쓸 때마다 어느 쪽인지 문맥으로 풀어야 했다.

## 결정

### A. cross-module 공개 계약은 `<module>.api`에 두고 `@NamedInterface("api")`로 선언한다

`like`/`member`/`security`/`shared`/`show`/`venue`가 api 패키지를 갖는다. 이 여섯 모듈의 root에는
`package-info.java`만 남는다.

**호출용 행위 계약에만 `Api` 접미사를 붙인다.**

| 성격 | 이름 | 예 |
| --- | --- | --- |
| 다른 모듈이 호출하는 행위 계약 | `XxxApi` | `MemberLookupApi`, `VenueLookupApi` |
| 오가는 데이터 | 도메인 이름 그대로 | `PerformanceSaleSnapshot`, `MemberProfile` |
| 이벤트 | 발생한 사실의 이름 | `OrderStarted` |
| enum·value object | 그대로 | `Region`, `LikeType`, `RawPassword` |

기존 이름이 이미 더 명확하면 붙이지 않는다. `AccessTokenAuthenticator`는 `-Authenticator`가 이미
"이걸 불러 인증한다"를 말하고, `AuditorPrincipal`은 호출하는 계약이 아니라 **구현하는** 계약(SPI)
이라 `Api`가 방향을 거꾸로 읽히게 만든다.

### B. `allowedDependencies`를 named interface 단위로 좁힌다

```java
// 전
allowedDependencies = {"show", "member", "security", "shared :: *"}
// 후
allowedDependencies = {"show :: api", "member :: api", "security :: api",
                       "shared :: api", "shared :: web", "shared :: exception"}
```

`shared :: *` 와일드카드를 걷어내 실제로 여는 표면 셋을 적는다. `security -> member`만 `api`
하나로 좁히지 못한다 — 전역 HTTP security가 E1000/E1001 응답을 만들려면 `member :: exception`이
계속 필요하다. 오류 계약은 공개 계약과 성격이 달라 억지로 합치지 않는다.

### C. 공개면이 늘어나는 것을 스냅샷으로 고정한다

`com.ticket.ArchitectureRulesTest.공개된_named_interface는_승인된_목록과_일치한다()`가 현재
공개된 아홉 개를 고정한다. 새 `@NamedInterface`가 PR에서 조용히 추가되면 실패한다.

### D. 업무 모듈의 controller 패키지는 `endpoint`로 부른다

`booking`/`like`/`member`/`show`의 `web` → `endpoint`. `shared.web`과 `security.http`는 그대로
둔다 — 전자는 controller가 아니라 응답 형식 계약이고 이름이 `@NamedInterface`의 일부이며, 후자는
`@RestController`가 하나도 없는 HTTP 보안 adapter다(security의 실제 endpoint는 `security.auth`에
있다).

### E. 필요 없는 곳에는 api 패키지를 만들지 않는다

`payment`는 다른 모듈에 공개할 계약이 없는 entity-only 단계라(ADR 0005) api 패키지를 만들지
않는다. 빈 공개면은 없는 것보다 나쁘다 — "여기 뭔가 공개돼 있다"고 읽히기 때문이다.

`booking`은 root에 `OrderStarted`/`OrderTerminated`만 남긴다. **두 FQCN이 DB에 저장된 값이기
때문이다** — Modulith event publication registry의 `EVENT_PUBLICATION.event_type` 컬럼이 이벤트
class의 FQCN을 그대로 담는다. 패키지를 옮기면 배포 시점에 아직 완료되지 않은 publication row가
지금 코드에 없는 class를 가리키게 되어 영원히 재처리되지 않는다. 같은 성격의 사고를 listener id
에서 이미 한 번 겪었고 그 회귀는 `BookingEventListenerIdContractTest`가 고정하고 있다.

## 검토한 대안

### 대안 1: root를 유지하고 `@NamedInterface`만 root에 붙인다

Spring Modulith는 root 패키지에도 named interface를 선언할 수 있다. 이동 비용이 0이고
`allowedDependencies`도 좁힐 수 있다.

**버린 이유**: root는 "이름이 없는 자리"다. `com.ticket.member`에 계약과 (언젠가의) 구현이 함께
들어올 때 그것을 막는 것은 규칙뿐이고, 규칙은 사람이 지킨다. `api`라는 이름이 붙으면
`ArchitectureRulesTest`가 "`..api..`에는 `@Service`/`@Entity`를 두지 않는다"를 패키지 이름만으로
강제할 수 있다 — 이 강제력이 이동 비용보다 크다고 봤다.

### 대안 2: `Type.OPEN` 모듈로 두고 하위 패키지를 자유롭게 참조한다

**버린 이유**: 방향이 반대다. 이번 작업은 공개면을 좁히는 것이고 OPEN은 넓히는 것이다. OPEN이 되면
cross-module 순환 검증도 함께 약해진다.

### 대안 3: 모든 public type에 기계적으로 `Api`를 붙인다

**버린 이유**: `PerformanceSaleSnapshotApi`, `RegionApi`는 읽는 사람에게 아무것도 주지 않는다.
접미사가 의미를 가지려면 "붙은 것"과 "안 붙은 것"이 갈려야 한다 — 전부 붙이면 접미사가 곧 잡음이다.

## 결과

- 공개면이 import 문에서 바로 읽힌다: `com.ticket.member.api.MemberLookupApi`.
- 공개면 오염을 패키지 이름으로 막을 수 있다(`ArchitectureRulesTest`의 api 규칙 둘).
- 공개면이 늘어나면 스냅샷 테스트가 PR에서 잡는다.
- 치른 비용: 242개 파일의 import 변경, 9개 타입 rename. REST URL·HTTP status·응답 JSON·error
  code·DB schema·이벤트 payload는 바뀌지 않았다.
- `ModularityTests.APPROVED_DEPENDENCY_DAG`는 바뀌지 않았다. 모듈 단위 edge가 그대로라는 뜻이고,
  이 리팩터가 결합을 늘리지 않았다는 증거다.
