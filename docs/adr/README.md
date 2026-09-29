# ADR (Architecture Decision Record)

## 언제 ADR을 쓰는가

세 조건을 모두 만족할 때만 쓴다.

1. 되돌리기 어렵다 — 스키마, 모듈 경계, 외부 계약처럼 바꾸는 비용이 크다.
2. 나중에 누군가 "왜 이렇게 했는가"를 물을 만하다.
3. 실제로 여러 대안을 검토했다 — 유일한 선택지였다면 ADR이 아니라 그냥 구현이다.

## 번호와 상태

순차 번호를 쓴다. 새 ADR을 쓰기 전에 아래 표에서 가장 큰 번호를 찾아 그 다음 번호를 쓴다. 0004는 발행된 기록이
없다. 각 ADR은 `## 상태` 아래 첫 줄에 상태를 하나만 적고, 표는 그 줄을 옮긴 것이다.

- `제안`: 아직 승인되지 않았다.
- `채택`: 승인됐다. 구현 완료를 뜻하지 않는다.
- `일부 대체 → NNNN`: 결정 일부를 그 ADR이 대체했다. 나머지는 유효하다.
- `대체됨 → NNNN` 또는 `대체됨 → architecture.md (현재 구조)`: 결정이 대체됐다. 패키지 배치 ADR은 현재 구조가
  `docs/architecture.md`에 있어 당시 판단의 기록으로만 남는다.

본문의 과거 타입 이름과 당시 판단은 기록으로 남기고 고치지 않는다. 결정이 바뀌면 본문을 다시 쓰지 않고 상태 줄과
표를 고친다.

## 색인

| 번호 | 제목 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [0001](0001-selection-and-hold-are-independent.md) | Selection과 Hold를 독립으로 둔다 | 일부 대체 → 0021 | 2026-08-25 |
| [0002](0002-module-owned-error-contracts.md) | 오류 계약을 모듈별로 소유하고 API에서 공통 처리한다 | 일부 대체 → 0010 | 2026-08-26 |
| [0003](0003-spring-modulith-application-module-boundaries.md) | Spring Modulith Application Module 경계로 전환한다 | 일부 대체 → 0005, 0006, 0011, 0013, 0014 | 2026-09-02 |
| [0005](0005-performance-grade-price-ownership-and-payment-ticketing-modules.md) | 가격은 PerformanceGrade가 원본이고, Payment/Ticketing은 entity-only 모듈로 시작한다 | 일부 대체 → 0006 | 2026-09-04 |
| [0006](0006-bounded-context-module-boundaries.md) | Application Module을 Bounded Context 단위로 재편한다 | 일부 대체 → 0008, 0012 | 2026-09-07 |
| [0007](0007-show-sale-fields-are-display-only.md) | Show의 판매 필드는 표시 전용이고, 판단은 Booking이 한다 | 채택 | 2026-09-09 |
| [0008](0008-like-target-generalization.md) | favorite module을 like로 개명하고 찜 대상을 LikeType으로 일반화한다 | 일부 대체 → 0009 | 2026-09-09 |
| [0009](0009-like-owns-write-and-status-usecases.md) | 찜하기·찜 해제·찜 상태 조회는 like가 소유하고, "내 찜 목록"만 show에 남는다 | 채택 | 2026-09-09 |
| [0010](0010-exceptions-do-not-own-http-status.md) | 업무 예외는 HTTP 상태를 모른다 — 웹 계층이 상태를 정한다 | 일부 대체 → 0011 | 2026-09-10 |
| [0011](0011-shared-technical-package-layout.md) | 공통 기술 코드를 shared 하위 패키지로 통합한다 | 대체됨 → architecture.md (현재 구조) | 2026-09-10 |
| [0012](0012-separate-global-http-security-from-member.md) | 전역 HTTP security를 member에서 분리한다 | 일부 대체 → 0013 | 2026-09-13 |
| [0013](0013-layer-first-package-layout-and-security-owns-authentication.md) | 업무 모듈은 계층형으로 두고, 인증 조립은 security가 소유한다 | 대체됨 → architecture.md (현재 구조) | 2026-09-14 |
| [0014](0014-module-public-contracts-live-in-api-packages.md) | 모듈 공개 계약은 api 패키지가 갖고, controller 패키지는 endpoint로 부른다 | 대체됨 → architecture.md (현재 구조) | 2026-09-15 |
| [0015](0015-null-contracts-are-explicit-and-enforced.md) | null 계약을 JSpecify로 명시하고 NullAway로 강제한다 | 채택 | 2026-09-15 |
| [0016](0016-capability-first-layout-inside-modules.md) | 모듈 안은 역할로 두되, 큰 모듈은 업무를 먼저 드러낸다 | 대체됨 → architecture.md (현재 구조) | 2026-09-17 |
| [0017](0017-query-implementations-live-in-persistence.md) | 조회 구현은 `persistence`의 조회 Repository가 갖는다 | 대체됨 → architecture.md (현재 구조) | 2026-09-18 |
| [0018](0018-audit-base-entity-lives-in-shared.md) | 감사 기반 entity는 `shared.jpa` 하나가 갖는다 | 채택 | 2026-09-19 |
| [0019](0019-querydsl-only-query-repositories.md) | 이름이 조회 기술을 말한다 — `*QuerydslRepository`와 3단 구조 | 대체됨 → architecture.md (현재 구조) | 2026-09-21 |
| [0020](0020-db-schema-source-of-truth-is-migration.md) | DB 스키마의 원본은 migration이다 — entity `@Table`에는 테이블 이름만 둔다 | 채택 | 2026-09-29 |
| [0021](0021-order-requires-own-selection.md) | 주문은 본인이 선택 중인 좌석으로만 시작한다 | 채택 | 2026-09-29 |
