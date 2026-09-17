# 코드 관례

이 문서는 현재 코드에서 반복되는 이름과 배치 규칙의 단일 기준이다. 새 접미사를 기계적으로
붙이지 않고, 같은 책임은 같은 어휘로 표현한다. 모듈 경계와 의존 방향은
[architecture.md](architecture.md)가 원본이다.

## 패키지와 역할

업무 코드의 배치 기준은 하나다 — **작은 모듈은 역할을 바로 보여주고, 큰 모듈은 업무를 먼저
보여준다.** 모든 모듈을 같은 모양으로 만들지 않는다.

작은/중간 모듈은 `com.ticket.<module>.<role>`이다 — `member.usecase.MemberAccountService`,
`show.persistence.ShowRepositoryAdapter`, `member.endpoint.MemberController`처럼 읽는다.
`booking`만 `com.ticket.booking.<capability>.<role>`이다 —
`booking.order.usecase.CancelOrderUseCase`, `booking.selection.persistence.RedissonSeatSelectionStore`.

역할 이름은 모든 모듈에서 같은 뜻이다. `api`는 다른 module에 공개하는 계약, `endpoint`는 HTTP
진입점, `usecase`는 요청 단위 조립과 트랜잭션 경계, `query`는 조회 계약과 읽기 모델, `port`는
조회가 아닌 출력 계약, `domain`은 상태와 업무 규칙, `persistence`는 DB·Redis 구현이다.

**역할 폴더는 템플릿이 아니다.** 실제 파일과 책임이 있을 때만 만든다 — `payment`에는 `domain`과
`persistence`뿐이고, `venue`에는 `endpoint`가 없다. 미래를 예상해 빈 폴더를 만들지 않는다.

**`Repository`는 계약이고 `persistence`는 기술이다.** Aggregate 저장·복원 계약은 `domain`이
소유한다(`member.domain.MemberRepository`, `booking.order.domain.OrderRepository`). `persistence`에는
그 계약을 만족시키는 구현만 둔다(`member.persistence.MemberRepositoryAdapter`). `XXXPort`는 계약,
`XXXAdapter`는 구현이며 규모가 작아도 둘을 다른 package에 둔다.

**폴더 깊이는 규모에 비례한다.** 일반적인 최대는 모듈 → capability → 역할이다
(`booking.order.persistence`). 기술 응집도가 높고 파일이 많을 때만 한 단계를 더
쓴다(`show.persistence.querydsl`, `booking.concurrency.redis`). 파일이 적고 서로만 부르는 묶음은
평평하게 둔다(`booking.admission` 일곱 파일, `member.password` 세 파일).

**모듈 안에 `common`·`util`·`helper`·`support`·`misc` 패키지를 만들지 않는다.** 갈 곳이 애매하면
그 타입의 소유 capability나 실제 역할을 먼저 판단한다. 여러 업무가 함께 쓰는 기반도 그 역할이
받는다 — 락 계약은 `booking.concurrency`, 도메인 기반 타입과 값은 `domain`
(`show.domain.ShowAuditedEntity`, `booking.domain.BookingAuditedEntity`)이다.

**`booking` 하위 capability는 Spring Modulith Application Module이 아니다.** `@ApplicationModule`도
`@NamedInterface`도 붙이지 않고 capability 사이에 module API를 만들지 않는다. 최상위 Application
Module은 여덟 개 그대로다.

**여러 capability를 조율하는 코드는 capability에 억지로 넣지 않는다.** 판단 기준은 "어떤 상태를
저장하는가"가 아니라 "어떤 workflow의 결과를 책임지는가"다. `StartBookingUseCase`는
`booking.usecase`, `HoldReleaseCoordinator`는 (이름과 달리) `booking.event`다.

**`security`만 역할 대신 기능으로 나눈다** — `auth`/`jwt`/`oauth`/`token`/`http`이고 각
폴더 안에 역할 폴더를 다시 만들지 않는다. 업무가 아니라 인증 기술이라 "무엇에 관한 코드인가"가 더
나은 탐색 단위이기 때문이다. `security.http`는 controller 패키지가 아니라 HTTP 보안 adapter라
`endpoint`로 바꾸지 않는다. `shared`도 공개 계약을 성격별로 `shared.api`/`shared.web`/
`shared.exception` 세 named interface에 나눠 두고, 실행 배선은 `shared.infrastructure`에 둔다.

**cross-module 공개 계약은 module root가 아니라 `<module>.api`에 둔다.** 다른 module이
호출하는 행위 계약에만 `Api` 접미사를 붙이고(`MemberLookupApi`, `VenueLookupApi`), record·snapshot·
event·enum 같은 데이터에는 붙이지 않는다. 배경은
[ADR 0014](adr/0014-module-public-contracts-live-in-api-packages.md)다.

배치의 단일 기준은 [architecture.md](architecture.md#module-structure)이고 배경은
[ADR 0016](adr/0016-capability-first-layout-inside-modules.md)이다.

## Application 계약

- Use case 클래스는 `<Verb><Target>UseCase`, 진입 메서드는 `execute(...)`를 기본으로 한다.
- `Request`는 `endpoint`의 HTTP 등 외부 adapter 입력에 쓴다.
- `Input`과 `Output`은 use case 호출 계약이다. 의미 있는 값 객체나 `void`를 억지로 감싸지 않는다.
- `Result`는 일반 출력 외에 cookie/token처럼 adapter가 별도로 소비할 값이 있을 때만 쓴다.
  `LoginUseCase.Result`가 그 예다.
- `Context`는 여러 application 단계 사이의 내부 처리 정보다. 외부 요청이 아닌 검증 결과에
  `Request`를 붙이지 않는다. 다만 한 use case 안에서만 오가는 값이면 `Context` 타입을 만들기 전에
  지역 변수로 충분한지 먼저 본다.
- `Row`는 persistence/query projection, `View`는 응답용 조합 모델, `Snapshot`은 특정 시점에 고정한
  상태다. `Criteria`는 검색·판정 조건, `Param`은 목록·커서 조회 실행 파라미터에 쓴다.

## Component와 메서드

- Aggregate 저장 계약은 `Repository`, 읽기 전용 projection 계약은 `QueryPort`다. **`Port`는
  application이 요구하는 계약이고 그 구현은 `Adapter`다** — 구현에는 기술을 드러내는 접두사와
  `Adapter` 접미사를 함께 쓴다(`QuerydslSeatStateQueryAdapter implements SeatStateQueryPort`,
  `ShowRepositoryAdapter implements ShowRepository`). 구현에 `Port`를 붙이면 파일 이름만으로
  계약과 구현을 구분할 수 없다.
- **조회 구현은 Spring Data method·`@Query`·Querydsl 중 그 조회를 가장 간단히 표현하는 것을**
  **고른다.** 동적 조건·복합 정렬·커서 페이징에는 Querydsl을 적극 쓰고, 단순 조회에까지 강제하지
  않는다. 판단 기준과 보존해야 할 query semantics는
  [readability-guidelines.md](readability-guidelines.md#조회-코드)가 원본이다.
- **역할 이름을 붙일 수 있다는 것은 별도 class를 만들 이유가 아니다.** 먼저 독립적인 책임이나
  실제 경계(트랜잭션·모듈·외부 시스템·분산락·aggregate 생명주기)가 있는지 확인한다. 한 곳에서만
  쓰는 검증·매핑·조립은 같은 class의 private method를 먼저 검토한다. 판단 기준의 원본은
  [readability-guidelines.md](readability-guidelines.md)다.
- **별도 class를 만들기로 결정한 뒤** 가장 구체적인 이름을 고른다. `Reader`/`Writer`는 읽기·쓰기
  한쪽 책임, `Validator`는 검증, `Registrar`는 등록, `Authenticator`는 자격 증명 확인을 뜻한다.
  이때 `Service`보다 이 이름들을 우선한다. `Manager`/`Processor`/`Coordinator`/`Preparer`/
  `Helper`/`Util`/`Handler`처럼 무엇이든 담을 수 있는 이름은 업무 어휘가 없을 때만 쓴다.
- `Coordinator`는 락·순서처럼 실행 조율이 본체인 것에 쓴다(`SeatSelectionCoordinator`). 상태
  변경과 그 결과 알림을 같은 경계 안에서 함께 책임진다는 뜻이다.
- `find...`는 없을 수 있는 조회, `findAll...`은 빈 컬렉션이 가능한 복수 조회, `exists...`는 존재
  여부, `require...`는 실패 가능한 필수 조건에 쓴다. boolean 판단은 `is`/`has`/`can`을 쓴다.
- 새 객체·리소스 생성은 `create`, 다른 표현에서 변환은 `from`, 단순 값 조립은 `of`, 반대 방향
  변환은 `to...`를 기본으로 한다. 기존 Repository의 absence 처리 정책은 application에 유지한다.

## 이름 세부 규칙

- 예외는 `[Subject][Condition]Exception` 형태로 읽히게 한다. 예: `SeatAlreadyHeldException`,
  `HoldLimitExceededException`. Java 클래스명 교정으로 오류 코드·상태·공개 메시지를 바꾸지 않는다.
- `@ConfigurationProperties` binding 타입은 `XxxProperties`, 검증을 끝낸 별도 설정 값은
  `XxxSettings`로 구분한다.
- 도메인 lifecycle은 `State`를 기본으로 하고, 외부 조회/응답에 단순화한 표현은 `Status`를 허용한다.
  JSON 필드나 DB 저장값에 영향을 주는 일괄 rename은 하지 않는다.
- acronym은 변수에서 `oauth2`, `jwt`, `url`, `uri`처럼 일반 camelCase로 쓴다.

## 커밋 본문

**변경 이력의 기본 기록은 커밋 본문이다.** 제목 다음 본문에 아래 내용을 남겨, 코드와 함께
당시의 판단과 검증 근거를 읽을 수 있게 한다. 별도 작업 일지 파일은 만들지 않는다.

- **배경·이유:** 해결하려던 문제와 이 방법을 선택한 이유. 실제로 검토한 중요한 대안이나
  감수한 비용이 있으면 함께 설명한다.
- **변경:** 무엇을 어떻게 바꿨는지, 책임·동작·계약의 변화를 중심으로 쓴다. 파일 목록이나
  diff를 그대로 반복하지 않는다.
- **검증:** 실제 실행한 검사와 결과, 실행하지 않은 범위와 이유를 쓴다. 과거 검증이나
  실행할 예정인 검사를 이번 결과로 기록하지 않는다.

작은 변경은 짧은 문장으로 충분하며 고정된 제목·양식을 강제하지 않는다. 중요한 결정은
[ADR 기준](adr/README.md)에 따라 별도로 남기고 본문에서 연결한다. 현재 구조·운영이 바뀌면
해당 원본 문서도 갱신한다. 기록을 위해 커밋을 자동 생성하지 않고, 커밋 전 인계는 대화에서
변경 이유·검증·남은 작업을 전달한다. 커밋 작성 권한은 `AGENTS.md`의 기존 규칙을 따른다.

## 테스트와 형식

- 개별 단위·구조 테스트는 `XxxTest`, HTTP/API 계약은 `XxxContractTest`, Spring Modulith slice는
  `XxxModuleTests`를 쓴다. 데이터 생성은 `XxxFixture`, test double은 `FakeXxx` 또는 호출을 기록하는
  `RecordingXxx`로 역할을 드러낸다.
- 여러 모듈의 JPA/Querydsl 테스트가 공유하는 기반 코드는 `com.ticket.testsupport.persistence`에 둔다.
- Java와 seed source는 Spotless가 4칸 들여쓰기, 120자 줄 길이, 명시적 import, import 순서,
  trailing whitespace와 EOF newline을 관리한다. `./gradlew spotlessApply`로 수정하고
  `./gradlew spotlessCheck`로 검사한다. wildcard import는 허용하지 않는다.
- **production package는 `package-info.java`에 `@NullMarked`를 선언한다.** `@NullMarked`는 하위
  package로 전파되지 않으므로 package를 새로 만들 때마다 필요하다. 실제로 없을 수 있는 값에만
  `@Nullable`을 붙이고, `@NullUnmarked`와 suppression은 쓰지 않는다. NullAway 오류는 CI 실패다.
  배경은 [ADR 0015](adr/0015-null-contracts-are-explicit-and-enforced.md)다.
