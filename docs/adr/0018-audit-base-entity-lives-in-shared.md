# ADR 0018: 감사 기반 entity는 `shared.jpa` 하나가 갖는다

## 상태

채택됨 (2026-09-19)

6개 업무 module이 각자 복제해 쓰던 `<Module>AuditedEntity`를 없애고
`com.ticket.shared.jpa.AuditedEntity` 하나로 합친다. [ADR 0016](0016-capability-first-layout-inside-modules.md)
§"남은 것"이 `BookingAuditedEntity`를 `booking.domain` 직속에 둔다고 적은 부분과,
`docs/architecture.md`가 "이를 이유로 모든 BC의 감사 기반 타입을 `shared`로 합치지 않는다"고 적은
부분을 이 결정이 대체한다. 나머지 module 경계 결정(ADR 0003·0006·0011·0014)은 그대로다.

## 배경

`booking`/`show`/`venue`/`member`/`like`/`payment`가 각각 38줄짜리 `@MappedSuperclass`를 갖고
있었다. 여섯 벌은 클래스 이름만 다르고 필드·애노테이션·동작이 완전히 같았다.

복제를 택했던 이유는 "module 간 JPA 상속 결합을 만들지 않는다"였다. 그 우려에는 실질이 있다 —
공유 base entity는 시간이 지나면 한 module만 필요한 필드(낙관적 락 `@Version`, 소프트 삭제 표시,
멀티테넌시 키)를 받아들이고, 그때마다 모든 테이블이 그 컬럼을 갖게 된다. 복제는 그 수렴 압력을
물리적으로 차단한다.

다만 지금 구조에서 그 우려가 실제 비용으로 바뀐 적은 없다. 여섯 벌은 한 번도 갈라진 적이 없고,
갈라져도 서로를 깨뜨리지 못한다. 한편 복제의 비용은 매번 발생한다 — 새 module을 만들 때마다
일곱 번째 복사본을 만들어야 하고, 감사 정책을 바꾸면 여섯 파일을 같이 고쳐야 하며, 신규 기여자는
"왜 같은 클래스가 여섯 개인가"를 매번 묻는다.

## 결정

`com.ticket.shared.jpa.AuditedEntity` 하나를 두고 entity 21개가 직접 상속한다.

**`shared`의 네 번째 named interface로 연다** (`shared :: jpa`). 기존 셋(`api`/`web`/`exception`)
어디에도 들어갈 수 없다.

- `shared.api`는 안 된다 — `ArchitectureRulesTest.api는_구현_기술을_모른다`가 `..api..`의
  `jakarta.persistence`·`org.springframework.data` 참조를 막는다. 감사 기반 타입은 그 둘이 본체다.
- `shared.persistence`도 안 된다 — `persistence`는 역할 이름이라
  `ArchitectureRulesTest.domain은_조립_저장_HTTP를_모른다`가 `..domain..` → `..persistence..`를
  막는다. entity는 `domain`에 살므로 상속 자체가 규칙 위반이 된다.

그래서 역할 이름 여섯(`domain`/`usecase`/`event`/`port`/`persistence`/`endpoint`) 밖의 이름을
쓴다. `jpa`는 그 package가 무엇을 담는지 그대로 말한다.

**`venue`와 `payment`가 처음으로 의존을 갖는다.** 둘은 `allowedDependencies = {}`인 완전한 leaf
였는데 이제 `{"shared :: jpa"}`가 된다. 업무 module 참조는 여전히 0이다.

**DB schema는 바뀌지 않는다.** `@MappedSuperclass`는 테이블을 갖지 않고 컬럼을 상속받는 쪽 테이블에
인라인한다. 컬럼 이름·타입·nullable이 그대로라 Flyway 마이그레이션이 필요 없다.

## 대안과 버린 이유

**복제를 유지한다.** 수렴 압력 차단이라는 실질적 이점이 있지만, 그 압력은 코드 배치가 아니라
리뷰가 막는 것이 맞다. `AuditedEntity`의 Javadoc이 "업무 의미를 가진 필드를 여기 추가하지 않는다"를
명시하고, 필드가 늘면 21개 테이블이 동시에 바뀌므로 PR에서 조용히 지나가기 어렵다.

**`shared.api`의 규칙을 완화한다.** `api는_구현_기술을_모른다`에서 `jakarta.persistence`를 뺄 수도
있었다. 기각했다 — 그 규칙은 `AuditedEntity` 하나가 아니라 다른 module이 읽는 계약면 전체를 지킨다.
예외 하나를 위해 전체를 여는 거래다.

**`shared.domain`에 둔다.** 역할 이름 충돌은 피하지만 `shared`에 업무 domain이 있다고 읽힌다.
`shared`는 업무 vocabulary를 담지 않는다는 것이 ADR 0011의 결정이다.

## 하지 않은 것

- **감사 컬럼 자체는 건드리지 않았다.** `createdBy`/`updatedBy`/`updatedAt`은 지금 읽는 코드가
  없지만 DB schema는 외부 계약이라 이 결정의 범위 밖이다.
- **`booking.domain`을 없애지 않았다.** `RequestedSeatIds`가 남아 있어 package는 그대로다.
- **`security`의 `allowedDependencies`는 그대로다.** entity가 없어 `shared :: jpa`가 필요 없다.
- **로직을 바꾸지 않았다.** 상속 대상 교체와 import 수정이 전부다. 감사 값을 채우는 주체
  (`JpaAuditingConfig` + `SecurityContextAuditorAware` + `AuditorPrincipal`)는 그대로다.

## 결과

main 파일 −5, 약 −190줄. 새 module을 만들 때 복사할 기반 클래스가 없다.

`venue`·`payment`가 leaf가 아니게 되면서 `ModularityTests.APPROVED_DEPENDENCY_DAG`의 두 항목이
`Set.of("shared")`로 바뀐다. `shared`가 leaf라는 성질(`shared는_업무_module을_모른다`)은 그대로다.

공개면이 셋에서 넷으로 늘었다. `ArchitectureRulesTest.EXPOSED_NAMED_INTERFACES` 스냅샷이 그
사실을 고정한다 — 다음에 `shared`에 무언가를 열려는 PR은 이 목록에서 먼저 걸린다.
