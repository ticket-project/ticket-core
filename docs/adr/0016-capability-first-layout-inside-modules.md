# ADR 0016: 모듈 안은 역할로 두되, 큰 모듈은 업무를 먼저 드러낸다

## 상태

채택됨 (2026-09-17)

> 2026-09-19 갱신: 이 ADR이 `application`을 `usecase`/`query`/`port` 셋으로 나눈 것 중 `query`는
> [ADR 0017](0017-query-implementations-live-in-persistence.md)과 그 후속 정리로 없어졌다 — 조회
> 구현은 `persistence`, 읽기 모델과 조회 타입은 `usecase`가 갖는다. 본문이 예로 든
> `show.persistence.querydsl.QuerydslShowListQueryAdapter`도 없다. `member`의 역할 폴더는 여섯이
> 아니라 일곱이다(`api`·`domain`·`endpoint`·`exception`·`password`·`persistence`·`usecase`).

[ADR 0013](0013-layer-first-package-layout-and-security-owns-authentication.md)의 "모듈 → 계층"
배치를 이 부분에 한해 대체한다. 그 ADR의 나머지 결정(**`security`만 기능으로 나눈다**)은 그대로
유효하다. Application Module 경계([ADR 0003](0003-spring-modulith-application-module-boundaries.md),
[ADR 0006](0006-bounded-context-module-boundaries.md))와 공개 계약 위치
([ADR 0014](0014-module-public-contracts-live-in-api-packages.md))도 바뀌지 않는다.

## 배경

ADR 0013은 업무 모듈을 `<module>.<layer>`로 폈다. 모듈이 작을 때는 잘 맞았지만, `booking`에서
대가가 커졌다.

주문 하나를 이해하려면 `booking.domain.order` · `booking.application` ·
`booking.application.usecase` · `booking.application.port` · `booking.application.query` ·
`booking.infrastructure.persistence` · `booking.infrastructure.querydsl` · `booking.endpoint`를
오가야 했다. 여덟 폴더 중 어느 것도 "주문"이라고 말하지 않는다. 계층은 각 파일이 *무엇인지*는
말하지만, 개발자가 실제로 여는 단위인 *무슨 업무인지*는 말하지 않는다.

반대로 작은 모듈에서는 계층 이름 자체가 군더더기였다. `member.application.usecase`는 세 단어를
써서 "use case"를 말하고, `member.application.port`는 파일 하나를 담고 있었다.

## 결정

**배치 기준은 하나다 — 작은 모듈은 역할을 바로 보여주고, 큰 모듈은 업무를 먼저 보여준다.**

1. **중간 계층 이름을 없애고 역할을 모듈 바로 아래로 올린다.** `application` → `usecase`(조립),
   `query`(조회 계약과 읽기 모델), `port`(그 밖의 출력 계약). `infrastructure` → `persistence`.
   `member.usecase.GetCurrentMemberUseCase`, `show.persistence.querydsl.QuerydslShowListQueryAdapter`.

2. **`booking`만 모듈 바로 아래를 capability로 둔다.** `order`·`hold`·`selection`·`seat`·
   `salespolicy`·`ticket`·`admission`이다. 역할 폴더는 그 아래에, **실제 파일이 있을 때만** 만든다.

3. **폴더 깊이는 코드 규모와 책임 복잡도에 비례한다.** `booking.admission`은 파일 일곱이 서로만
   부르므로 평평하고, `booking.order`는 크므로 역할로 나눈다. 일반적인 최대는 모듈 → capability →
   역할이다.

4. **여러 capability를 조율하는 코드는 이름이 아니라 결과로 배치한다.** 판단 기준은 "어떤 상태를
   저장하는가"가 아니라 **"어떤 workflow의 결과를 책임지는가"**다. `StartBookingUseCase`는 정책·입장·
   회원·좌석·선점을 조율하지만 결과가 주문이라 `booking.order.usecase`이고,
   `HoldCreationCoordinator`/`HoldReleaseCoordinator`는 이름과 달리 `booking.event`다.

5. **`Repository`(계약)와 `persistence`(기술)를 구분한다.** Aggregate 저장·복원 계약은 `domain`이
   소유하고 `persistence`에는 구현만 둔다. `XXXPort`/`XXXAdapter`도 규모와 무관하게 다른 package다.

## 하지 않은 것

- **Application Module을 늘리지 않았다.** `booking.order` 같은 하위 package는 Spring Modulith
  module이 아니다. `@ApplicationModule`도 `@NamedInterface`도 붙이지 않았고, 최상위 여덟 module과
  의존 DAG(`com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG`)는 완전히 그대로다.
- **`security`와 `shared`는 건드리지 않았다.** `security`의 기능 축 배치는 이미 이 ADR의 철학과
  맞고, `shared`는 기술 모듈이라 업무 축이 없다.
- **`booking.OrderStarted`/`OrderTerminated`를 옮기지 않았다.** FQCN이
  `EVENT_PUBLICATION.event_type`에 저장된 값이다. 같은 이유로 `BookingEventListeners`의 listener id
  문자열도 옛 `booking.application` 경로를 유지하며, `BookingEventListenerIdContractTest`가 고정한다.
- **예외를 capability로 내리지 않았다.** `<module>.exception` 하나를 유지한다 —
  `com.ticket.shared.exception.ExceptionHandlerScopeTest`가 "handler는 자기 module의 exception
  package 안 타입만 잡는다"를 강제하고 있어, 나누면 그 보호가 약해진다. 필요해지면 그때 규칙과 함께
  옮긴다.
- **로직을 바꾸지 않았다.** package 이동·import 수정·package-info·architecture test·Javadoc 경로가
  전부다. 트랜잭션·락·보상·Redis semantics·HTTP 계약·DB schema·이벤트 payload는 그대로다.

## 결과

**얻은 것.** `booking`에서 "주문은 어디 있나"에 package tree만 보고 답할 수 있다. 작은 모듈은
역할 목록이 짧아져 `member`는 여섯, `payment`는 둘이다. 함께 바뀌는 코드가 같은 폴더에 있다.

**잃은 것.** 모듈마다 모양이 달라 "어느 모듈이든 같은 경로"라는 단순함이 사라졌다. 그 대가로
architecture rule을 고정 경로가 아니라 **역할 이름 패턴**으로 다시 썼다
(`com.ticket..usecase..`가 `com.ticket.<module>.application..`을 대신한다). 새 capability가 생겨도
규칙 목록을 고칠 일이 없다는 점에서 이전보다 덜 깨진다.

**규칙의 보호 범위는 줄지 않았다.** cross-module 규칙은 "계층 목록 밖은 허용"에서 "공개 named
interface 밖은 금지"로 뒤집어 오히려 넓어졌다. `booking.ticket`은 파일이 다섯뿐이라 평평하게 둘
수도 있었지만, `domain`/`persistence`로 나눠야 `DomainIsolationTest`의 `..domain..` 패턴과 계층
방향 규칙이 계속 걸리므로 나눈 쪽을 골랐다.

**남은 것.** `booking` 바로 아래에는 여러 capability가 함께 쓰는 것만 남았다 — `domain`(요청 좌석
값), `exception`(module error code와 handler), `event`, `concurrency`, `redis`, `websocket`이다.
capability 하나가 소유할 수 없는 것들이라 더 내리지 않는다.

> 2026-09-19 갱신: 이 문단이 원래 예로 들던 `BookingAuditedEntity`는
> [ADR 0018](0018-audit-base-entity-lives-in-shared.md)이 `shared.jpa.AuditedEntity` 하나로 합쳐
> 더 이상 `booking.domain`에 없다.
>
> 2026-09-21 갱신: 마지막까지 남아 있던 `RequestedSeatIds`도 유일한 사용처인
> `booking.order.usecase`로 내려갔다 — `booking.domain` 직속은 이제 없다. 이 문단의 "`domain`(요청
> 좀석 값)"은 유효하지 않다.

`booking.admission`은 계약과 구현이 같은 package에 있어 방향 규칙 대상이 아니다. 파일이 늘어
목록만으로 무엇이 무엇인지 알 수 없어지면 그때 나눈다.
